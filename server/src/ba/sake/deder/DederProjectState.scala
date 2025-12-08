package ba.sake.deder

import java.util.concurrent.ExecutorService
import scala.util.control.NonFatal
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import dependency.ScalaParameters
import dependency.parser.DependencyParser
import dependency.api.ops.*

import ba.sake.deder.config.{ConfigParser, DederProject}
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.zinc.ZincCompiler

class DederProjectState(tasksExecutorService: ExecutorService) {

  // keep hot
  private val zincCompiler = locally {
    val compilerBridgeJar = DependencyResolver.fetchOne(
      DependencyParser
        .parse("org.scala-sbt::compiler-bridge:1.11.0")
        .toOption
        .get
        .applyParams(ScalaParameters("2.13.17"))
        .toCs
    )
    ZincCompiler(compilerBridgeJar)
  }
  private val tasksRegistry = TasksRegistry(zincCompiler)
  private val configParser = ConfigParser()
  private val configFile = DederGlobals.projectRootDir / "deder.pkl"

  @volatile var current: Either[String, DederProjectStateData] = Left("Project state is uninitialized")

  // used for BSP to keep last known good state
  @volatile var lastGood: Either[String, DederProjectStateData] = Left("Project state is uninitialized")

  refreshProjectState(err => println(s"Initial project state load error: ${err}"))

  def refreshProjectState(onError: String => Unit): Unit = current.synchronized {
    // TODO make sure no requests are running
    // because we need to make sure locks are not held while we refresh the state (new locks are instantiated)
    val newProjectConfig = configParser.parse(configFile)
    newProjectConfig match {
      case Left(errorMessage) =>
        onError(errorMessage)
        current = Left(errorMessage)
      case Right(newConfig) =>
        val tasksResolver = TasksResolver(newConfig, tasksRegistry)
        val executionPlanner = ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule)
        val goodProjectStateData =
          DederProjectStateData(newConfig, tasksRegistry, tasksResolver, executionPlanner)
        lastGood = Right(goodProjectStateData)
        current = Right(goodProjectStateData)
    }
  }

  def executeTask[T](
      moduleId: String,
      task: Task[T, ?],
      notificationCallback: ServerNotification => Unit,
      useLastGood: Boolean = false
  ): T = {
    val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
    executeOne(moduleId, task.name, serverNotificationsLogger, useLastGood).asInstanceOf[T]
  }

  // used mostly by CLI
  def executeAll(
      moduleSelectors: Seq[String],
      taskName: String,
      notificationCallback: ServerNotification => Unit,
      useLastGood: Boolean = false
  ): Unit = {
    val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
    val state = if useLastGood then lastGood else current
    // TODO deduplicate unnecessary work !
    val selectedModuleIds = state match {
      case Left(errorMessage) =>
        notificationCallback(
          ServerNotification.log(ServerNotification.LogLevel.ERROR, errorMessage)
        )
        Seq.empty
      case Right(dpsd) =>
        val allModuleIds = dpsd.tasksResolver.allModules.map(_.id)
        if moduleSelectors.isEmpty then allModuleIds
        else
          moduleSelectors.flatMap { selector =>
            // TODO improve wildcard selection, e.g mymodule*jvm
            allModuleIds.filter(_ == selector)
          }
    }
    println(s"Running ${taskName} on modules ${selectedModuleIds}")
    try {
      selectedModuleIds.foreach(moduleId => executeOne(moduleId, taskName, serverNotificationsLogger, useLastGood))
      serverNotificationsLogger.add(ServerNotification.RequestFinished(success = true))
    } catch {
      case e: TaskEvaluationException =>
        serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
    }
  }

  def executeOne(
      moduleId: String,
      taskName: String,
      serverNotificationsLogger: ServerNotificationsLogger,
      useLastGood: Boolean = false
  ): Any =
    try {
      refreshProjectState(errorMessage => serverNotificationsLogger.add(ServerNotification.logError(errorMessage, Some(moduleId))))
      val state = if useLastGood then lastGood else current
      state match {
        case Left(errorMessage) =>
          throw TaskEvaluationException(s"Project state is invalid during task execution: ${errorMessage}")

        case Right(DederProjectStateData(projectConfig, _, tasksResolver, executionPlanner)) =>
          val tasksExecSubgraph = executionPlanner.getExecSubgraph(moduleId, taskName)
          val tasksExecStages = executionPlanner.execStages(moduleId, taskName)
          val tasksExecutor =
            TasksExecutor(projectConfig, tasksResolver.modulesGraph, tasksResolver.tasksGraph, tasksExecutorService)
          val allTaskInstances = tasksExecStages.flatten.sortBy(_.id)
          allTaskInstances.foreach { taskInstance =>
            taskInstance.lock.lock()
          }
          try {
            serverNotificationsLogger.add(
              ServerNotification.logInfo(s"Executing ${moduleId}.${taskName}", Some(moduleId))
            )
            tasksExecutor.execute(tasksExecStages, serverNotificationsLogger)
          } finally {
            allTaskInstances.reverse.foreach { taskInstance =>
              taskInstance.lock.unlock()
            }
          }
      }

      /*
    println("Modules graph:")
    println(GraphUtils.generateDOT(tasksResolver.modulesGraph, v => v.id, v => Map("label" -> v.id)))
    println("Tasks graph:")
    println(GraphUtils.generateDOT(tasksResolver.tasksGraph, v => v.id, v => Map("label" -> v.id)))
    println("Planned exec subgraph:")
    println(GraphUtils.generateDOT(tasksExecSubgraph, v => v.id, v => Map("label" -> v.id)))
    println("Exec stages:")
    println(tasksExecStages.map(_.map(_.id)).mkString("\n"))

    println("#" * 50)
       */

    } catch {
      case NonFatal(e) =>
        serverNotificationsLogger.add(
          ServerNotification.logError(e.getMessage, Some(moduleId))
        )
        serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
        throw TaskEvaluationException(s"Error during task execution: ${e.getMessage}", e)
    }
}

case class DederProjectStateData(
    projectConfig: DederProject,
    tasksRegistry: TasksRegistry,
    tasksResolver: TasksResolver,
    executionPlanner: ExecutionPlanner
)
