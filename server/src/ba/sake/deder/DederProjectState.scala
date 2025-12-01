package ba.sake.deder

import scala.util.control.NonFatal
import java.util.concurrent.ExecutorService
import scala.jdk.CollectionConverters.*
import dependency.ScalaParameters
import dependency.parser.DependencyParser
import dependency.api.ops.*

import ba.sake.deder.config.{ConfigParser, DederProject}
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.zinc.ZincCompiler
import ba.sake.deder.ServerNotification.RequestFinished

class DederProjectState(tasksExecutorService: ExecutorService) {

  private val compilerBridgeJar = DependencyResolver.fetchOne(
    DependencyParser
      .parse("org.scala-sbt::compiler-bridge:1.11.0")
      .toOption
      .get
      .applyParams(
        ScalaParameters("2.13.17")
      )
      .toCs
  )
  // keep hot
  private val zincCompiler = ZincCompiler(compilerBridgeJar)

  def execute(moduleId: String, taskName: String, logCallback: ServerNotification => Unit): Unit =
    val serverNotificationsLogger = ServerNotificationsLogger(logCallback)
    try {
      // TODO check unique module ids

      val configParser = ConfigParser(serverNotificationsLogger)
      val configFile = DederGlobals.projectRootDir / "deder.pkl"
      val projectConfig = configParser.parse(configFile)
      val tasksRegistry = TasksRegistry(zincCompiler)
      val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
      val executionPlanner = ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule)
      val tasksExecSubgraph = executionPlanner.getExecSubgraph(moduleId, taskName)
      val tasksExecStages = executionPlanner.execStages(moduleId, taskName)
      val tasksExecutor =
        TasksExecutor(projectConfig, tasksResolver.modulesGraph, tasksResolver.tasksGraph, tasksExecutorService)

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

      tasksExecutor.execute(tasksExecStages, serverNotificationsLogger)
    } catch {
      case NonFatal(_) =>
        serverNotificationsLogger.add(RequestFinished(success = false))
    }
}
