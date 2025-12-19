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
import ba.sake.tupson.toJson
import ba.sake.deder.config.{ConfigParser, DederProject}
import ba.sake.deder.deps.Dependency
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.zinc.ZincCompiler

class DederProjectState(tasksExecutorService: ExecutorService, onShutdown: () => Unit) {

  // TODO configurable
  private val maxInactiveDuration = Duration.ofMinutes(10)

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

  reloadProjectState()

  scheduleInactiveShutdownChecker()

  def reloadProjectState(): Unit = current.synchronized {
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
      }
    } catch {
      case NonFatal(e) =>
        val errorMessage = s"Error during project load: ${e.getMessage}"
        current = Left(errorMessage)
    }
  }

  // TODO execute as one action, not per module
  // merge plan graphs into one !
  def executeCLI(
      moduleSelectors: Seq[String],
      taskName: String,
      args: Seq[String],
      notificationCallback: ServerNotification => Unit,
      useLastGood: Boolean = false,
      json: Boolean = false,
      watch: Boolean = false
  ): Unit = try {
    val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
    val state = (if useLastGood then lastGood else current) match
      case Left(err) => throw TaskEvaluationException(s"Project state is not available: ${err}")
      case Right(s)  => s

    // TODO deduplicate unnecessary work !
    val allModuleIds = state.tasksResolver.allModules.map(_.id)
    val selectedModuleIds =
      if moduleSelectors.isEmpty then allModuleIds
      else
        moduleSelectors.flatMap { selector =>
          // TODO improve wildcard selection, e.g mymodule*jvm
          allModuleIds.filter(_ == selector)
        }

    println(s"Running ${taskName} on modules ${selectedModuleIds}")
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
          val taskRes = executeTask(moduleId, taskInstance.task, args, serverNotificationsLogger, useLastGood)
          if json then
            val jsonRes = taskInstance.task.rw.write(taskRes)
            jsonValues = jsonValues.updated(moduleId, jsonRes)
          if watch then {
            val affectingSourceFileTasks =
              state.executionPlanner.getAffectingSourceFileTasks(moduleId, taskName)
            val affectingConfigValueTasks =
              state.executionPlanner.getAffectingConfigValueTasks(moduleId, taskName)
            watchedTasks = watchedTasks.appended(
              WatchedTaskData(
                taskInstance,
                args,
                serverNotificationsLogger,
                useLastGood,
                affectingSourceFileTasks,
                affectingConfigValueTasks
              )
            )
            serverNotificationsLogger.add(
              ServerNotification.logInfo(s"Executing ${moduleId}.${taskName} in watch mode...")
            )
          }
        }
        if json then serverNotificationsLogger.add(ServerNotification.Output(jsonValues.toJson))
        if !watch then serverNotificationsLogger.add(ServerNotification.RequestFinished(success = true))
      } catch {
        case e: TaskNotFoundException =>
          serverNotificationsLogger.add(ServerNotification.logError(e.getMessage))
          serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
      }

  } catch {
    case NonFatal(e) =>
      notificationCallback(ServerNotification.logError(e.getMessage))
      notificationCallback(ServerNotification.RequestFinished(success = false))
  }

  def executeTask[T](
      moduleId: String,
      task: Task[T, ?],
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean = false
  ): T =
    executeOne(moduleId, task.name, args, serverNotificationsLogger, useLastGood).asInstanceOf[T]

  def executeOne(
      moduleId: String,
      taskName: String,
      args: Seq[String],
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean = false
  ): Any =
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
        serverNotificationsLogger.add(
          ServerNotification.logInfo(s"Executing ${moduleId}.${taskName}", Some(moduleId))
        )
        tasksExecutor.execute(tasksExecStages, args, serverNotificationsLogger)
      } finally {
        allTaskInstances.reverse.foreach { taskInstance =>
          taskInstance.lock.unlock()
        }
      }
    } catch {
      case NonFatal(e) =>
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
              println(s"No requests in flight for ${inactiveDuration.toMinutes} minutes, shutting down server.")
              shutdown()
            }
          }
        } catch {
          case NonFatal(e) =>
            println(s"Error in inactive shutdown checker: ${e.getMessage}")
        }
      },
      1,
      1,
      TimeUnit.MINUTES
    )
  }

  def triggerFileWatchedTasks(changedPaths: Set[os.Path]): Unit = {
    watchedTasks.foreach { watchedTask =>
      val affected = watchedTask.affectingSourceFileTasks.exists { taskInstance =>
        val sourceFiles = taskInstance.task match {
          case sourceFilesTask: SourceFilesTask =>
            executeTask(
              taskInstance.moduleId,
              sourceFilesTask,
              watchedTask.args,
              watchedTask.serverNotificationsLogger,
              watchedTask.useLastGood
            ).map(_.absPath)
          case sourceFileTask: SourceFileTask =>
            Seq(
              executeTask(
                taskInstance.moduleId,
                sourceFileTask,
                watchedTask.args,
                watchedTask.serverNotificationsLogger,
                watchedTask.useLastGood
              ).absPath
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
          watchedTask.serverNotificationsLogger,
          watchedTask.useLastGood
        )
        watchedTask.serverNotificationsLogger.add(
          ServerNotification.logInfo(s"Executing ${watchedTask.taskInstance.id} in watch mode...")
        )
      }
    }
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
    taskInstance: TaskInstance,
    args: Seq[String],
    serverNotificationsLogger: ServerNotificationsLogger,
    useLastGood: Boolean,
    affectingSourceFileTasks: Set[TaskInstance],
    affectingConfigValueTasks: Set[TaskInstance]
)
