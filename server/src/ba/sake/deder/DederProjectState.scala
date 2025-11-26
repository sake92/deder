package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.{ConfigParser, DederProject}
import ba.sake.deder.deps.DependencyResolver
import ba.sake.deder.zinc.ZincCompiler
import coursier.parse.DependencyParser

import java.util.concurrent.ExecutorService


class DederProjectState(tasksExecutorTP: ExecutorService) {

  def execute(moduleId: String, taskName: String, logCallback: ServerNotification => Unit)/*: TaskResult[?]*/ = {
    val configParser = ConfigParser()
    val configFile = DederGlobals.projectRootDir / "deder.pkl"
    val projectConfig = configParser.parse(configFile)
    val allModules = projectConfig.modules.asScala.toSeq
    // TODO check unique module ids

    val compilerBridgeJar = DependencyResolver.fetchOne(
      DependencyParser.dependency(s"org.scala-sbt:compiler-bridge_2.13:1.11.0", "2.13").toOption.get
    )
    val zincCompiler = ZincCompiler(compilerBridgeJar)
    val tasksRegistry = TasksRegistry(zincCompiler)

    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val executionPlanner = ExecutionPlanner(tasksResolver.tasksGraph, tasksResolver.tasksPerModule)
    val tasksExecSubgraph = executionPlanner.execSubgraph(moduleId, taskName)
    val tasksExecStages = executionPlanner.execStages(moduleId, taskName)
    val tasksExecutor = TasksExecutor(projectConfig, tasksResolver.tasksGraph, tasksExecutorTP)

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
    tasksExecutor.execute(tasksExecStages, logCallback)
  }
}
