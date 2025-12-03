package ba.sake.deder

import java.util.concurrent.ExecutorService
import scala.util.control.NonFatal
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

  @volatile var projectStateData: Either[String, DederProjectStateData] = locally {
    val projectConfig = configParser.parse(configFile)
    projectConfig.map { config =>
      val tasksResolver = TasksResolver(config, tasksRegistry)
      val executionPlanner = ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule)
      DederProjectStateData(config, tasksResolver, executionPlanner)
    }
  }

  private def refreshProjectState(): Unit = projectStateData.synchronized {
    val newProjectConfig = configParser.parse(configFile)
    if (newProjectConfig != projectStateData.map(_._1)) {
      // TODO make sure no requests are running
      // because we need to make sure locks are not held while we refresh the state (new locks are instantiated)
      projectStateData = newProjectConfig.map { config =>
        val tasksResolver = TasksResolver(config, tasksRegistry)
        val executionPlanner = ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule)
        DederProjectStateData(config, tasksResolver, executionPlanner)
      }
    }
  }

  def execute(moduleId: String, taskName: String, notificationCallback: ServerNotification => Unit): Unit =
    val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
    try {
      refreshProjectState()
      projectStateData match {
        case Left(errorMessage) =>
          serverNotificationsLogger.add(
            ServerNotification.log(ServerNotification.Level.ERROR, errorMessage)
          )
          serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))

        case Right(DederProjectStateData(projectConfig, tasksResolver, executionPlanner)) =>
          val tasksExecSubgraph = executionPlanner.getExecSubgraph(moduleId, taskName)
          val tasksExecStages = executionPlanner.execStages(moduleId, taskName)
          val tasksExecutor =
            TasksExecutor(projectConfig, tasksResolver.modulesGraph, tasksResolver.tasksGraph, tasksExecutorService)
          val allTaskInstances = tasksExecStages.flatten.sortBy(_.id)
          allTaskInstances.foreach { taskInstance =>
            taskInstance.lock.lock()
          }
          try {
            tasksExecutor.execute(tasksExecStages, serverNotificationsLogger)
            serverNotificationsLogger.add(ServerNotification.RequestFinished(success = true))
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
        e.printStackTrace()
    }
}

case class DederProjectStateData(
    projectConfig: DederProject,
    tasksResolver: TasksResolver,
    executionPlanner: ExecutionPlanner
)