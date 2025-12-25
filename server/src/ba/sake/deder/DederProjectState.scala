package ba.sake.deder

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import scala.util.control.NonFatal
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import org.typelevel.jawn.ast.JValue
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.toJson
import ba.sake.deder.config.{ConfigParser, DederProject}
import ba.sake.deder.deps.Dependency
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.zinc.ZincCompiler

class DederProjectState(maxInactiveSeconds: Int, tasksExecutorService: ExecutorService, onShutdown: () => Unit)
    extends StrictLogging {

  private val maxInactiveDuration = Duration.ofSeconds(maxInactiveSeconds)

  @volatile private var shutdownStarted = false

  private val tasksRegistry = TasksRegistry()
  private val configParser = ConfigParser()
  private val configFile = DederGlobals.projectRootDir / "deder.pkl"

  @volatile var current: Either[String, DederProjectStateData] = Left("Project state is uninitialized")
  // used for BSP
  @volatile var lastGood: Either[String, DederProjectStateData] = Left("Project state is uninitialized")

  val lastRequestStartedAt = new java.util.concurrent.atomic.AtomicReference[Instant](null)

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
          val executionPlanner = ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule)
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

    // TODO deduplicate unnecessary work !
    val allModuleIds = state.tasksResolver.allModules.map(_.id)
    val selectedModuleIds =
      if moduleSelectors.isEmpty then allModuleIds
      else
        moduleSelectors.flatMap { selector =>
          // TODO improve wildcard selection, e.g mymodule%jvm
          allModuleIds.filter(_ == selector)
        }

    serverNotificationsLogger.add(
      ServerNotification.logInfo(s"Executing ${taskName} on module(s): ${selectedModuleIds.mkString(", ")}")
    )
    if selectedModuleIds.isEmpty then {
      serverNotificationsLogger.add(
        ServerNotification.logError(s"No modules found for selectors: ${moduleSelectors.mkString(", ")}")
      )
      serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
    } else
      try {
        var jsonValues = Map.empty[String, JValue]
        selectedModuleIds.foreach { moduleId =>
          val taskInstance = state.executionPlanner.getTaskInstance(moduleId, taskName)
          val (taskRes, _) = executeTask(
            moduleId,
            taskInstance.task,
            args,
            serverNotificationsLogger,
            watch = startWatch,
            useLastGood = useLastGood
          )
          if json then
            val jsonRes = taskInstance.task.rw.write(taskRes)
            jsonValues = jsonValues.updated(moduleId, jsonRes)
          if startWatch then {
            val affectingSourceFileTasks =
              state.executionPlanner.getAffectingSourceFileTasks(moduleId, taskName)
            val affectingConfigValueTasks =
              state.executionPlanner.getAffectingConfigValueTasks(moduleId, taskName)
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
        if json then serverNotificationsLogger.add(ServerNotification.Output(jsonValues.toJson))
        if exitOnEnd then serverNotificationsLogger.add(ServerNotification.RequestFinished(success = true))
      } catch {
        case e: TaskNotFoundException =>
          serverNotificationsLogger.add(ServerNotification.logError(e.getMessage))
          serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
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
  ): (res: T, changed: Boolean) =
    val (resAny, changed) = executeOne(moduleId, task.name, args, watch, serverNotificationsLogger, useLastGood)
    (resAny.asInstanceOf[T], changed)

  def executeOne(
      moduleId: String,
      taskName: String,
      args: Seq[String],
      watch: Boolean,
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean = false
  ): (res: Any, changed: Boolean) =
    try {
      lastRequestStartedAt.set(Instant.now())
      if shutdownStarted then throw TaskEvaluationException("Cannot execute tasks - server is shutting down")

      val state = (if useLastGood then lastGood else current).toOption.getOrElse(
        throw TaskEvaluationException(s"Project state is not available (lastGood=${useLastGood})")
      )

      val tasksExecSubgraph = state.executionPlanner.getExecSubgraph(moduleId, taskName)
      val tasksExecStages = state.executionPlanner.execStages(moduleId, taskName)
      val tasksExecutor =
        TasksExecutor(
          state.projectConfig,
          state.tasksResolver.modulesGraph,
          state.tasksResolver.tasksGraph,
          tasksExecutorService
        )
      val allTaskInstances = tasksExecStages.flatten.sortBy(_.id) // essential!!
      allTaskInstances.foreach { taskInstance =>
        taskInstance.lock.lock()
      }
      try {
        tasksExecutor.execute(tasksExecStages, args, watch, serverNotificationsLogger)
      } finally {
        allTaskInstances.reverse.foreach { taskInstance =>
          taskInstance.lock.unlock()
        }
      }
    } catch {
      case NonFatal(e) =>
        // send notification about failure to client
        serverNotificationsLogger.add(ServerNotification.logError(e.getMessage, Some(moduleId)))
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
        executeOne(
          watchedTask.taskInstance.moduleId,
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
