package ba.sake.deder

import java.util.concurrent.ExecutorService
import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import scala.util.control.NonFatal
import scala.util.Using
import org.typelevel.jawn.ast.JValue
import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.api.trace.StatusCode
import ba.sake.tupson.toJson
import ba.sake.deder.config.{ConfigParser, DederProject}

class DederProjectState(
    tasksRegistry: TasksRegistry,
    maxInactiveSeconds: Int,
    tasksExecutorService: ExecutorService,
    onShutdown: () => Unit
) extends StrictLogging {

  private val maxInactiveDuration = Duration.ofSeconds(maxInactiveSeconds)

  @volatile private var shutdownStarted = false

  private val configParser = ConfigParser()
  private val configFile = DederGlobals.projectRootDir / "deder.pkl"

  // TODO atomicref?
  @volatile var current: Either[String, DederProjectStateData] = Left("Project state is uninitialized")
  // used for BSP
  @volatile var lastGood: Either[String, DederProjectStateData] = Left("Project state is uninitialized")

  private val lastRequestStartedAt = new java.util.concurrent.atomic.AtomicReference[Instant](null)

  // TODO concurrent?
  private var watchedTasks = Seq.empty[WatchedTaskData]

  reloadProject()

  scheduleInactiveShutdownChecker()

  def reloadProject(): Unit = current.synchronized {
    // TODO make sure no requests are running
    // because we need to make sure locks are not held while we refresh the state (new locks are instantiated)
    try {
      val newProjectConfig = configParser.parse(configFile)
      newProjectConfig match {
        case Left(errorMessage) =>
          current = Left(errorMessage)
        case Right(newConfig) =>
          val tasksResolver = TasksResolver(newConfig, tasksRegistry)
          val executionPlanner =
            ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)
          val goodProjectStateData =
            DederProjectStateData(newConfig, tasksRegistry, tasksResolver, executionPlanner)
          lastGood = Right(goodProjectStateData)
          current = Right(goodProjectStateData)
          triggerConfigWatchedTasks()
      }
    } catch {
      case NonFatal(e) =>
        val errorMessage = s"Error during project load: ${e.getMessage}"
        current = Left(errorMessage)
    }
  }

  def executeCLI(
      clientId: Int,
      moduleSelectors: Seq[String],
      taskName: String,
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean = false,
      json: Boolean = false,
      startWatch: Boolean = false,
      exitOnEnd: Boolean = true
  ): Unit = try {
    val state = (if useLastGood then lastGood else current) match
      case Left(err) => throw TaskEvaluationException(s"Project state is not available: ${err}")
      case Right(s)  => s

    val allModuleIds = state.tasksResolver.allModules.map(_.id)
    val selectedModuleIds =
      if moduleSelectors.isEmpty then allModuleIds else WildcardUtils.getMatches(allModuleIds, moduleSelectors)

    if selectedModuleIds.isEmpty then {
      serverNotificationsLogger.add(
        ServerNotification.logError(s"No modules found for selectors: ${moduleSelectors.mkString(", ")}")
      )
      serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
    } else {
      var success = true
      val relevantModuleAndTasks = selectedModuleIds.flatMap { moduleId =>
        state.executionPlanner.getTaskInstanceOpt(moduleId, taskName).map { taskInstance =>
          moduleId -> taskInstance
        }
      }
      if relevantModuleAndTasks.isEmpty then {
        success = false
        serverNotificationsLogger.add(
          ServerNotification.logError(s"No '${taskName}' tasks found for modules: ${selectedModuleIds.mkString(", ")}")
        )
      } else {
        val plural = if relevantModuleAndTasks.size > 1 then "s" else ""
        serverNotificationsLogger.add(
          ServerNotification.logInfo(
            s"Executing '${taskName}' task on module${plural}: ${relevantModuleAndTasks.map(_._1).mkString(", ")}"
          )
        )
      }
      val relevantModuleIds = relevantModuleAndTasks.map(_._1)
      val results = executeTasks(
        relevantModuleIds,
        taskName,
        args,
        watch = startWatch,
        serverNotificationsLogger,
        useLastGood = useLastGood
      )
      if json then {
        val jsonValues = results.map { res =>
          val moduleId = res.taskInstance.moduleId
          val taskInstance = res.taskInstance
          val taskRes = res.res
          val jsonRes = taskInstance.task.rw.write(taskRes.asInstanceOf[taskInstance.task.Res])
          (moduleId, jsonRes)
        }.toMap
        serverNotificationsLogger.add(ServerNotification.Output(jsonValues.toJson))
      }
      if startWatch then {
        relevantModuleAndTasks.foreach { case (moduleId, taskInstance) =>
          val affectingSourceFileTasks = state.executionPlanner.getAffectingSourceFileTasks(moduleId, taskName)
          val affectingConfigValueTasks = state.executionPlanner.getAffectingConfigValueTasks(moduleId, taskName)
          watchedTasks = watchedTasks.appended(
            WatchedTaskData(
              clientId,
              taskInstance,
              args,
              serverNotificationsLogger,
              useLastGood,
              json,
              affectingSourceFileTasks,
              affectingConfigValueTasks
            )
          )
          serverNotificationsLogger.add(
            ServerNotification.logInfo(s"⌚ Executing ${moduleId}.${taskName} in watch mode...")
          )
        }
      }
      if exitOnEnd then serverNotificationsLogger.add(ServerNotification.RequestFinished(success = success))
    }

  } catch {
    case NonFatal(e) =>
      serverNotificationsLogger.add(ServerNotification.logError(e.getMessage))
      serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
  }

  def executeTask[T](
      moduleId: String,
      task: Task[T, ?],
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger,
      watch: Boolean = false,
      useLastGood: Boolean = false
  ): (res: T, changed: Boolean) = {
    val span = OTEL.TRACER
      .spanBuilder(s"${moduleId}.${task.name}")
      .setAttribute("moduleId", moduleId)
      .setAttribute("taskName", task.name)
      .startSpan()
    try {
      Using.resource(span.makeCurrent()) { scope =>
        val res = executeTasks(Seq(moduleId), task.name, args, watch, serverNotificationsLogger, useLastGood).head
        (res.res.asInstanceOf[T], res.changed)
      }
    } catch {
      case e: Throwable =>
        span.recordException(e)
        span.setStatus(StatusCode.ERROR)
        throw e
    } finally span.end()
  }

  // execute a single task on many modules
  def executeTasks(
      moduleIds: Seq[String], // nonempty please :')
      taskName: String,
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean
  ): Seq[TaskExecResult] =
    try {
      lastRequestStartedAt.set(Instant.now())
      if shutdownStarted then throw TaskEvaluationException("Cannot execute tasks - server is shutting down")

      val state = (if useLastGood then lastGood else current) match
        case Left(err) => throw TaskEvaluationException(s"Project state is not available: ${err}")
        case Right(s)  => s

      val tasksExecStages = state.executionPlanner.getExecStages(moduleIds, taskName)
      val tasksExecutor =
        TasksExecutor(
          state.projectConfig,
          state.tasksResolver.modulesGraph,
          state.tasksResolver.taskInstancesGraph,
          tasksExecutorService
        )
      val allTaskInstances = tasksExecStages.flatten.sortBy(_.id) // essential!!
      allTaskInstances.foreach { taskInstance =>
        // TODO timed wait
        taskInstance.lock.lock()
      }
      try {
        tasksExecutor.execute(tasksExecStages, taskName, args, watch, serverNotificationsLogger)
      } finally {
        allTaskInstances.reverse.foreach { taskInstance =>
          taskInstance.lock.unlock()
        }
      }
    } catch {
      case NonFatal(e) =>
        // send notification about failure to client
        serverNotificationsLogger.add(ServerNotification.logError(e.getMessage))
        serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
        throw TaskEvaluationException(s"Error during execution of task '${taskName}': ${e.getMessage}", e)
    }

  private def scheduleInactiveShutdownChecker(): Unit = {
    val executor = Executors.newSingleThreadScheduledExecutor()
    executor.scheduleAtFixedRate(
      () => {
        try {
          val lastStarted = lastRequestStartedAt.get()
          if lastStarted != null then {
            val now = Instant.now()
            val inactiveDuration = Duration.between(lastStarted, now)
            if inactiveDuration.compareTo(maxInactiveDuration) > 0 then {
              logger.info(s"No requests in flight for ${inactiveDuration.toMinutes} minutes, shutting down server.")
              shutdown()
            }
          }
        } catch {
          case NonFatal(e) =>
            logger.error(s"Error during inactivity shutdown checker: ${e.getMessage}")
        }
      },
      1,
      1,
      TimeUnit.MINUTES
    )
  }

  def triggerFileWatchedTasks(changedPaths: Set[os.Path]): Unit = {
    watchedTasks.foreach { watchedTask =>
      logger.debug(
        s"Checking if watched task is affected: ${watchedTask.taskInstance} by ${watchedTask.affectingConfigValueTasks}"
      )
      // the watched task itself may be a config value task!
      val watchedTaskAsSourceTasks = watchedTask.taskInstance.task match {
        case _: SourceFilesTask => Set(watchedTask.taskInstance)
        case _: SourceFileTask  => Set(watchedTask.taskInstance)
        case _                  => Set.empty
      }
      val taskInstancesToCheck = watchedTaskAsSourceTasks ++ watchedTask.affectingSourceFileTasks
      val affected = taskInstancesToCheck.exists { taskInstance =>
        val sourceFiles = taskInstance.task match {
          case sourceFilesTask: SourceFilesTask =>
            executeTask(
              taskInstance.moduleId,
              sourceFilesTask,
              watchedTask.args,
              watchedTask.serverNotificationsLogger,
              watchedTask.useLastGood
            ).res.map(_.absPath)
          case sourceFileTask: SourceFileTask =>
            Seq(
              executeTask(
                taskInstance.moduleId,
                sourceFileTask,
                watchedTask.args,
                watchedTask.serverNotificationsLogger,
                watchedTask.useLastGood
              ).res.absPath
            )
          case _ => Seq.empty
        }
        changedPaths.zip(sourceFiles).exists { (changedPath, sourceFile) =>
          changedPath.startsWith(sourceFile)
        }
      }
      if affected then {
        executeTasks(
          Seq(watchedTask.taskInstance.moduleId),
          watchedTask.taskInstance.task.name,
          watchedTask.args,
          true, // tell client we are in watch mode
          watchedTask.serverNotificationsLogger,
          watchedTask.useLastGood
        )
        watchedTask.serverNotificationsLogger.add(
          ServerNotification.logInfo(s"⌚ Executing ${watchedTask.taskInstance.id} in watch mode...")
        )
      }
    }
  }

  def triggerConfigWatchedTasks(): Unit = {
    watchedTasks.foreach { watchedTask =>
      logger.debug(
        s"Checking if watched task is affected: ${watchedTask.taskInstance} by ${watchedTask.affectingConfigValueTasks}"
      )
      // the watched task itself may be a config value task!
      val watchedTaskAffected = watchedTask.taskInstance.task match {
        case _: ConfigValueTask[?] => Set(watchedTask.taskInstance)
        case _: SourceFilesTask    => Set(watchedTask.taskInstance) // source tasks usually depend on config values
        case _: SourceFileTask     => Set(watchedTask.taskInstance)
        case _                     => Set.empty
      }
      val taskInstancesToCheck = watchedTaskAffected ++ watchedTask.affectingConfigValueTasks
      val affected = taskInstancesToCheck.exists { taskInstance =>
        val (_, changed) = taskInstance.task match {
          case configValueTask: ConfigValueTask[?] =>
            executeTask(
              taskInstance.moduleId,
              configValueTask,
              watchedTask.args,
              watchedTask.serverNotificationsLogger,
              watchedTask.useLastGood
            )
          case _ => ((), true) // should not happen
        }
        changed
      }
      if affected then {
        logger.debug(s"Deps watched task ${watchedTask.taskInstance.id} have changed, re-executing...")
        executeCLI(
          watchedTask.clientId,
          Seq(watchedTask.taskInstance.moduleId),
          watchedTask.taskInstance.task.name,
          watchedTask.args,
          watchedTask.serverNotificationsLogger,
          watchedTask.useLastGood,
          watchedTask.json,
          startWatch = false,
          exitOnEnd = false
        )
        watchedTask.serverNotificationsLogger.add(
          ServerNotification.logInfo(s"⌚ Executing ${watchedTask.taskInstance.id} in watch mode...")
        )
      }
    }
  }

  def removeWatchedTasks(clientId: Int): Unit = {
    logger.debug(s"Removing watched tasks for client ${clientId}")
    watchedTasks = watchedTasks.filterNot(_.clientId == clientId)
  }

  def shutdown(): Unit = {
    shutdownStarted = true
    onShutdown()
  }
}

case class DederProjectStateData(
    projectConfig: DederProject,
    tasksRegistry: TasksRegistry,
    tasksResolver: TasksResolver,
    executionPlanner: ExecutionPlanner
)

case class WatchedTaskData(
    clientId: Int,
    taskInstance: TaskInstance,
    args: Seq[String],
    serverNotificationsLogger: ServerNotificationsLogger,
    useLastGood: Boolean,
    json: Boolean,
    affectingSourceFileTasks: Set[TaskInstance],
    affectingConfigValueTasks: Set[TaskInstance]
)
