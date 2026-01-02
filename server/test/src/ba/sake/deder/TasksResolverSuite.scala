package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.*

class TasksResolverSuite extends munit.FunSuite {

  test("TasksResolver builds correct modules and tasks graph") {
    val projectPath = os.resource / "sample-projects/multi"
    val projectConfigStr = os.read(projectPath / "deder.pkl")
    val configParser = ConfigParser()
    val parsedConfig = configParser.parse(projectConfigStr)
    assert(parsedConfig.isRight, parsedConfig.left.get)
    val projectConfig = parsedConfig.toOption.get
    val coreTasks = CoreTasks()
    val tasksRegistry = TasksRegistry(coreTasks)
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
    locally {
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
    }

    // test if tasks are assigned to modules correctly
    val taskInstancesPerModule = tasksResolver.taskInstancesPerModule
    locally {
      val commonModuleTaskInstances = taskInstancesPerModule(commonModule.id)
      assertEquals(
        commonModuleTaskInstances.map(_.task.name).toSet,
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
      val uberTestModuleTasks = taskInstancesPerModule(uberTestModule.id)
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

    // test tasks graph
    locally {
      val taskInstancesGraph = tasksResolver.taskInstancesGraph
      val expectedVerticesCount = taskInstancesPerModule.values.map(_.size).sum
      assertEquals(taskInstancesGraph.vertexSet.asScala.size, expectedVerticesCount)

      val taskInstanceEdgeIdsGraph = taskInstancesGraph
        .edgeSet()
        .asScala
        .map(e =>
          (
            taskInstancesGraph.getEdgeSource(e).id,
            taskInstancesGraph.getEdgeTarget(e).id
          )
        )
        .toSet
      val expectedEdges =
        scalaModuleTaskEdges("common") ++
          scalaModuleTaskEdges("frontend") ++
          scalaModuleTaskEdges("backend") ++
          scalaModuleTaskEdges("uber") ++
          scalaTestModuleTaskEdges("uber-test") ++
          transitiveScalaModuleTaskEdges("frontend", "common") ++
          transitiveScalaModuleTaskEdges("backend", "common") ++
          transitiveScalaModuleTaskEdges("uber", "frontend") ++
          transitiveScalaModuleTaskEdges("uber", "backend") ++
          transitiveScalaModuleTaskEdges("uber-test", "uber")
      assertEquals(expectedEdges, taskInstanceEdgeIdsGraph)
    }

  }

  private def scalaModuleTaskEdges(moduleId: String): Set[(String, String)] =
    baseScalaModuleTaskEdges(moduleId) ++ Set(
      (s"${moduleId}.mainClasses", s"${moduleId}.compile"),
      (s"${moduleId}.mainClasses", s"${moduleId}.classes"),
      (s"${moduleId}.finalMainClass", s"${moduleId}.mainClasses"),
      (s"${moduleId}.finalMainClass", s"${moduleId}.mainClass"),
      (s"${moduleId}.run", s"${moduleId}.runClasspath"),
      (s"${moduleId}.run", s"${moduleId}.finalMainClass")
    )

  private def scalaTestModuleTaskEdges(moduleId: String): Set[(String, String)] =
    baseScalaModuleTaskEdges(moduleId) ++ Set(
      (s"${moduleId}.testClasses", s"${moduleId}.compile"),
      (s"${moduleId}.testClasses", s"${moduleId}.runClasspath"),
      (s"${moduleId}.test", s"${moduleId}.compile"),
      (s"${moduleId}.test", s"${moduleId}.runClasspath")
    )

  private def baseScalaModuleTaskEdges(moduleId: String): Set[(String, String)] =
    Set(
      (s"${moduleId}.dependencies", s"${moduleId}.deps"),
      (s"${moduleId}.dependencies", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.allDependencies", s"${moduleId}.dependencies"),
      (s"${moduleId}.allClassesDirs", s"${moduleId}.classes"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.scalacOptions"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.allDependencies"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.allClassesDirs"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.semanticdbEnabled"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.javaSemanticdbVersion"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.javacAnnotationProcessorDeps"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.semanticdbEnabled"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.scalaSemanticdbVersion"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.scalacPluginDeps"),
      (s"${moduleId}.compile", s"${moduleId}.sources"),
      (s"${moduleId}.compile", s"${moduleId}.generatedSources"),
      (s"${moduleId}.compile", s"${moduleId}.javacOptions"),
      (s"${moduleId}.compile", s"${moduleId}.scalacOptions"),
      (s"${moduleId}.compile", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.compile", s"${moduleId}.compileClasspath"),
      (s"${moduleId}.compile", s"${moduleId}.classes"),
      (s"${moduleId}.compile", s"${moduleId}.scalacPlugins"),
      (s"${moduleId}.compile", s"${moduleId}.javacAnnotationProcessors"),
      (s"${moduleId}.compile", s"${moduleId}.semanticdbEnabled"),
      (s"${moduleId}.runClasspath", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.runClasspath", s"${moduleId}.allDependencies"),
      (s"${moduleId}.runClasspath", s"${moduleId}.compile")
    )

  private def transitiveScalaModuleTaskEdges(moduleId: String, dependencyModuleId: String): Set[(String, String)] =
    Set(
      (s"${moduleId}.allDependencies", s"${dependencyModuleId}.allDependencies"),
      (s"${moduleId}.allClassesDirs", s"${dependencyModuleId}.allClassesDirs"),
      (s"${moduleId}.compileClasspath", s"${dependencyModuleId}.compileClasspath"),
      (s"${moduleId}.compile", s"${dependencyModuleId}.compile"),
      (s"${moduleId}.runClasspath", s"${dependencyModuleId}.runClasspath")
    )
}
