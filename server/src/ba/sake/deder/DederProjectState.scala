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

  @volatile private var projectE: Either[String, (DederProject, TasksResolver, ExecutionPlanner)] = locally {
    val projectConfig = configParser.parse(configFile)
    projectConfig.map { config =>
      val tasksResolver = TasksResolver(config, tasksRegistry)
      (config, tasksResolver, ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule))
    }
  }

  private def refreshProjectState(): Unit = projectE.synchronized {
    val newProjectConfig = configParser.parse(configFile)
    if (newProjectConfig != projectE.map(_._1)) {
      projectE = newProjectConfig.map { config =>
        val tasksResolver = TasksResolver(config, tasksRegistry)
        (config, tasksResolver, ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule))
      }
    }
  }

  def execute(moduleId: String, taskName: String, logCallback: ServerNotification => Unit): Unit =
    val serverNotificationsLogger = ServerNotificationsLogger(logCallback)
    try {
      refreshProjectState()
      projectE match {
        case Left(errorMessage) =>
          serverNotificationsLogger.add(
            ServerNotification.message(ServerNotification.Level.ERROR, errorMessage)
          )
          serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))

        case Right((projectConfig, tasksResolver, executionPlanner)) =>
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
          ServerNotification.message(ServerNotification.Level.ERROR, e.getMessage, Some(moduleId))
        )
        serverNotificationsLogger.add(ServerNotification.RequestFinished(success = false))
        e.printStackTrace()
    }
}
