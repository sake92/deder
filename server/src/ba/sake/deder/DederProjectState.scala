package ba.sake.deder

import java.util.concurrent.ExecutorService
import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import scala.util.control.NonFatal
import scala.util.Using
import org.typelevel.jawn.ast.{CanonicalRenderer, JValue}
import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.api.trace.StatusCode
import ba.sake.tupson.toJson
import ba.sake.deder.config.{ConfigParser, DederProject}

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class DederProjectState(
    tasksRegistry: TasksRegistry,
    maxInactiveSeconds: Int,
    tasksExecutorService: ExecutorService,
    onShutdown: () => Unit
) extends StrictLogging {

  private val maxInactiveDuration = Duration.ofSeconds(maxInactiveSeconds)

  @volatile private var shutdownStarted = false

  private val configParser = ConfigParser(writeJson = true)
  private val configFile = DederGlobals.projectRootDir / "deder.pkl"

  private val stateLock = new AnyRef
  private var current: Either[String, DederProjectStateData] = Left("Project state is uninitialized")
  // used for BSP
  private var lastGood: Either[String, DederProjectStateData] = Left("Project state is uninitialized")

  private val lastRequestStartedAt = new java.util.concurrent.atomic.AtomicReference[Instant](null)

  private val watchedTasksLock = new AnyRef
  private var watchedTasks = Seq.empty[WatchedTaskData]

  reloadProject()

  scheduleInactiveShutdownChecker()

  def readState(useLastGood: Boolean): Either[String, DederProjectStateData] =
    stateLock.synchronized {
      if useLastGood then lastGood match {
        case Left(_) => current // current has latest error message!
        case Right(value) => Right(value)
      }
      else current
    }

  private def readCurrentOrLastGood: Either[String, DederProjectStateData] =
    stateLock.synchronized { current.orElse(lastGood) }

  def reloadProject(): Unit = stateLock.synchronized {
    // TODO make sure no requests are running
    // because we need to make sure locks are not held while we refresh the state (new locks are instantiated)
    if !os.exists(configFile) || !os.isFile(configFile) then {
      val errorMessage =
        s"No deder.pkl found at '${configFile}'. Create a deder.pkl configuration file in your project root to get started."
      logger.warn(errorMessage)
      current = Left(errorMessage)
    } else
      try {
        val newProjectConfig = configParser.parse(configFile)
        newProjectConfig match {
          case Left(errorMessage) =>
            logger.warn(s"Failed to load project config: $errorMessage")
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
          logger.warn(errorMessage)
          current = Left(errorMessage)
      }
  }

  def executeCLI(
      clientId: Int,
      requestId: String,
      moduleSelectors: Seq[String],
      taskName: String,
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean = false,
      json: Boolean = false,
      startWatch: Boolean = false,
      exitOnEnd: Boolean = true
  ): Unit = try {
    val state = readState(useLastGood) match
      case Left(err) => throw TaskEvaluationException(s"Project state is not available: ${err}")
      case Right(s)  => s

    val allModuleIds = state.tasksResolver.allModules.map(_.id)
    val selectedModuleIds =
      if moduleSelectors.isEmpty then Right(allModuleIds)
      else WildcardUtils.getMatchesOrRecommendations(allModuleIds, moduleSelectors)

    selectedModuleIds match {
      case Left(recommendedModuleIds) =>
        val msg =
          if recommendedModuleIds.isEmpty then s"No modules found for selectors: ${moduleSelectors.mkString(", ")}"
          else s"No modules found, did you mean: ${recommendedModuleIds.mkString(", ")} ?"
        serverNotificationsLogger.add(ServerNotification.logError(msg))
        serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
      case Right(moduleIds) =>
        val relevantModuleAndTasks = state.executionPlanner.getTaskInstances(moduleIds, taskName) match {
          case Left(recommendations) =>
            val msg =
              if recommendations.isEmpty then s"No '${taskName}' tasks found"
              else s"No '${taskName}' tasks found, did you mean: ${recommendations.mkString(", ")} ?"
            serverNotificationsLogger.add(ServerNotification.logError(msg))
            serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
            Seq.empty
          case Right(values) =>
            val plural = if values.size > 1 then "s" else ""
            val modulesString = values.take(5).map(_._1).mkString(", ") + (if values.size > 5 then s", and ${values.size - 5} more" else "")
            serverNotificationsLogger.add(
              ServerNotification.logInfo(
                s"Executing '${taskName}' task on module${plural}: ${modulesString}"
              )
            )
            values
        }
        val relevantModuleIds = relevantModuleAndTasks.map(_._1)
        val isTaskSingleton = relevantModuleAndTasks.exists(_._2.task.singleton)
        if isTaskSingleton && relevantModuleIds.length > 1 then
          throw RuntimeException(s"Task '${taskName}' is singleton, cannot execute it on multiple modules at once")
        val results = executeTasks(
          requestId,
          relevantModuleIds,
          taskName,
          args,
          watch = startWatch,
          serverNotificationsLogger,
          useLastGood = useLastGood
        )
        // summarize across modules (only when >1 module)
        if results.nonEmpty then {
          val task = results.head.taskInstance.task
          val moduleResults = results.sortBy(_.taskInstance.moduleId).map(r => (r.taskInstance.module, r.res))
          task.summarizeUnsafe(moduleResults, serverNotificationsLogger)
        }
        if json then {
          val jsonValues = results.map { res =>
            val moduleId = res.taskInstance.moduleId
            val taskInstance = res.taskInstance
            val taskRes = res.res
            val jsonRes = taskInstance.task.rw.write(taskRes.asInstanceOf[taskInstance.task.Res])
            (moduleId, jsonRes)
          }.toMap
          val jsonRW = summon[ba.sake.tupson.JsonRW[Map[String, JValue]]]
          val jsonRes = CanonicalRenderer.render(jsonRW.write(jsonValues))
          serverNotificationsLogger.add(ServerNotification.Output(jsonRes))
        }
        if startWatch then {
          relevantModuleAndTasks.foreach { case (moduleId, taskInstance) =>
            val affectingSourceFileTasks = state.executionPlanner.getAffectingSourceFileTasks(moduleId, taskName)
            val affectingConfigValueTasks = state.executionPlanner.getAffectingConfigValueTasks(moduleId, taskName)
            watchedTasksLock.synchronized {
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
            }
            serverNotificationsLogger.add(
              ServerNotification.logInfo(s"⌚ Executing ${moduleId}.${taskName} in watch mode...", moduleId)
            )
          }
        }
        if exitOnEnd then {
          val allSuccessful = results.forall(r => r.taskInstance.task.isResultSuccessfulUnsafe(r.res))
          serverNotificationsLogger.add(ServerNotification.RequestFinished(success = allSuccessful))
        }
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
        val requestId = UUID.randomUUID().toString
        val res =
          executeTasks(requestId, Seq(moduleId), task.name, args, watch, serverNotificationsLogger, useLastGood).head
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
      requestId: String,
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

      val state = readState(useLastGood) match
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
      try {
        allTaskInstances.foreach { taskInstance =>
          // TODO timed wait
          taskInstance.lock.lock()
        }
        DederGlobals.cancellationTokens.put(requestId, new AtomicBoolean(false))
        tasksExecutor.execute(requestId, tasksExecStages, moduleIds, taskName, args, watch, serverNotificationsLogger)
      } finally {
        allTaskInstances.reverse.foreach { taskInstance =>
          taskInstance.lock.unlock()
        }
        DederGlobals.cancellationTokens.remove(requestId)
      }
    } catch {
      case NonFatal(e) =>
        // send notification about failure to client
        serverNotificationsLogger.add(ServerNotification.logError(e.getMessage))
        if !watch then serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
        throw TaskEvaluationException(s"Error during execution of task '${taskName}': ${e.getMessage}", e)
    }

  def cancelRequest(requestId: String): Unit = {
    val token = DederGlobals.cancellationTokens.get(requestId)
    logger.debug(s"Cancelling request ${requestId} with token ${token}")
    if token != null then token.set(true)
  }

  def cleanModules(moduleIds: Seq[String]): Boolean = {
    readCurrentOrLastGood match {
      case Left(err) =>
        logger.error(s"Cannot clean modules, project state is not available: ${err}")
        false
      case Right(state) =>
        val modulesTaskInstances = moduleIds
          .flatMap(
            state.tasksResolver.taskInstancesPerModule.get
          )
          .flatten
          .sortBy(_.id)
        try {
          modulesTaskInstances.foreach(_.lock.lock())
          DederCleaner.cleanModules(moduleIds)
        } finally {
          modulesTaskInstances.reverse.foreach(_.lock.unlock())
        }
    }
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
    val snapshot = watchedTasksLock.synchronized { watchedTasks }
    snapshot.foreach { watchedTask =>
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
        changedPaths.exists { changedPath =>
          sourceFiles.exists(changedPath.startsWith)
        }
      }
      if affected then {
        val requestId = UUID.randomUUID().toString
        executeTasks(
          requestId,
          Seq(watchedTask.taskInstance.moduleId),
          watchedTask.taskInstance.task.name,
          watchedTask.args,
          true, // tell client we are in watch mode
          watchedTask.serverNotificationsLogger,
          watchedTask.useLastGood
        )
        watchedTask.serverNotificationsLogger.add(
          ServerNotification.logInfo(s"⌚ Executing ${watchedTask.taskInstance.id} in watch mode...", watchedTask.taskInstance.moduleId)
        )
      }
    }
  }

  private def triggerConfigWatchedTasks(): Unit = {
    val snapshot = watchedTasksLock.synchronized { watchedTasks }
    snapshot.foreach { watchedTask =>
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
        logger.debug(
          s"Config value dependencies of watched task ${watchedTask.taskInstance.id} have changed, re-executing..."
        )
        val requestId = UUID.randomUUID().toString
        executeCLI(
          watchedTask.clientId,
          requestId,
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
          ServerNotification.logInfo(s"⌚ Executing ${watchedTask.taskInstance.id} in watch mode...", watchedTask.taskInstance.moduleId)
        )
      }
    }
  }

  def removeWatchedTasks(clientId: Int): Unit = {
    logger.debug(s"Removing watched tasks for client ${clientId}")
    watchedTasksLock.synchronized {
      watchedTasks = watchedTasks.filterNot(_.clientId == clientId)
    }
  }

  def getTabCompletions(commandLine: String, cursorPos: Int): Seq[String] =
    readCurrentOrLastGood match {
      case Left(_) => Seq.empty
      case Right(state) =>
        val tabCompleter = TabCompleter(state.tasksResolver)
        tabCompleter.complete(commandLine, cursorPos)
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
