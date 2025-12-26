package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.*

class TasksResolverSuite extends munit.FunSuite {

  test("TasksResolver builds correct tasks graph") {
    val projectPath = os.resource / "sample-projects/multi"
    val projectConfigStr = os.read(projectPath / "deder.pkl")
    val configParser = ConfigParser()
    val parsedConfig = configParser.parse(projectConfigStr)
    assert(parsedConfig.isRight, parsedConfig.left.get)
    val projectConfig = parsedConfig.toOption.get
    val tasksRegistry = TasksRegistry()
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val modules = tasksResolver.allModules.map(_.asInstanceOf[ScalaModule])

    // test basic config
    assertEquals(modules.map(_.id).toSet, Set("common", "frontend", "backend", "uber", "uber-test"))
    assert(modules.map(_.scalaVersion).forall(_ == "3.7.1"))
    val commonModule = modules.find(_.id == "common").get
    val frontendModule = modules.find(_.id == "frontend").get
    val backendModule = modules.find(_.id == "backend").get
    val uberModule = modules.find(_.id == "uber").get
    val uberTestModule = modules.find(_.id == "uber-test").get.asInstanceOf[ScalaTestModule]
    assertEquals(commonModule.deps.asScala.toSeq, Seq("org.jsoup:jsoup:1.21.1"))
    val frontendModuleDeps: Seq[DederModule] = frontendModule.moduleDeps.asScala.toSeq
    assertEquals(frontendModuleDeps, Seq(commonModule))
    assertEquals(Option(frontendModule.mainClass), None)
    assertEquals(Option(uberModule.mainClass), Some("uber.Main"))
    assertEquals(uberTestModule.root, "uber/test")

    // test modules graph
    val modulesGraph = tasksResolver.modulesGraph
    assertEquals(modulesGraph.vertexSet.asScala.toSet, tasksResolver.allModules.toSet)
    val expectedEdges = Set(
      (frontendModule, commonModule),
      (backendModule, commonModule),
      (uberModule, frontendModule),
      (uberModule, backendModule),
      (uberTestModule, uberModule)
    )
    val moduleEdges = modulesGraph.edgeSet.asScala
      .map(e =>
        (
          modulesGraph.getEdgeSource(e).asInstanceOf[ScalaModule],
          modulesGraph.getEdgeTarget(e).asInstanceOf[ScalaModule]
        )
      )
      .toSet
    assertEquals(moduleEdges, expectedEdges)
    // println("Modules graph edges: " + modulesGraph.edgeSet.asScala.toSet)

    // test if tasks are assigned to modules correctly
    val tasksPerModule = tasksResolver.tasksPerModule
    val commonModuleTasks = tasksPerModule(commonModule.id)
    assertEquals(
      commonModuleTasks.map(_.task.name).toSet,
      Set(
        "sources",
        "generatedSources",
        "scalaVersion",
        "resources",
        "javacOptions",
        "scalacOptions",
        "semanticdbEnabled",
        "javaSemanticdbVersion",
        "scalaSemanticdbVersion",
        "javacAnnotationProcessorDeps",
        "scalacPluginDeps",
        "javacAnnotationProcessors",
        "scalacPlugins",
        "deps",
        "dependencies",
        "allDependencies",
        "classes",
        "allClassesDirs",
        "compileClasspath",
        "compile",
        "runClasspath",
        "mainClass",
        "mainClasses",
        "finalMainClass",
        "run"
      )
    )
    val uberTestModuleTasks = tasksPerModule(uberTestModule.id)
    assertEquals(
      uberTestModuleTasks.map(_.task.name).toSet,
      Set(
        "sources",
        "generatedSources",
        "scalaVersion",
        "resources",
        "javacOptions",
        "scalacOptions",
        "semanticdbEnabled",
        "javaSemanticdbVersion",
        "scalaSemanticdbVersion",
        "javacAnnotationProcessorDeps",
        "scalacPluginDeps",
        "javacAnnotationProcessors",
        "scalacPlugins",
        "deps",
        "dependencies",
        "allDependencies",
        "classes",
        "allClassesDirs",
        "compileClasspath",
        "compile",
        "runClasspath",
        "testClasses",
        "test"
      )
    )
  }
}
