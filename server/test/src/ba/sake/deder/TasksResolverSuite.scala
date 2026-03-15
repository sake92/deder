package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.*

class TasksResolverSuite extends munit.FunSuite {

  private val testProjectsDir = os.pwd / "server/test/resources/sample-projects"

  test("TasksResolver builds correct modules and tasks graph") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
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
    val baseTasks = Set(
      "javaHome",
      "javaVersion",
      "jvmOptions",
      "sources",
      "generatedSources",
      "scalaVersion",
      "resources",
      "javacOptions",
      "scalacOptions",
      "semanticdb", // directory..
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
      "mandatoryDependencies",
      "classes",
      "allClassesDirs",
      "compileClasspath",
      "compilerDeps",
      "compile",
      "runClasspath",
      "mainClass",
      "mainClasses",
      "finalMainClass",
      "manifest",
      "finalManifest",
      "jar",
      "allJars",
      "assembly",
      "run",
      "runMain",
      "sourcesJar",
      "javadocJar",
      "pomSettings",
      "moduleDepsPomSettings",
      "publishArtifacts",
      "publishLocal",
      "publish",
      "runMvnApp"
    )
    val taskInstancesPerModule = tasksResolver.taskInstancesPerModule
    locally {
      val commonModuleTaskInstances = taskInstancesPerModule(commonModule.id)
      assertEquals(
        commonModuleTaskInstances.map(_.task.name).toSet,
        baseTasks
      )
      val uberTestModuleTasks = taskInstancesPerModule(uberTestModule.id)
      assertEquals(
        uberTestModuleTasks.map(_.task.name).toSet,
        baseTasks ++ Set("testClasses", "test"),
        "uber-test module tasks mismatch"
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
      locally {
        val diff1 = expectedEdges.diff(taskInstanceEdgeIdsGraph)
        assert(
          diff1.isEmpty,
          s"Task instance graph edges mismatch. Nonexisting edges: ${diff1
              .take(10)}${if diff1.size > 10 then "..." else ""}"
        )
        val diff2 = taskInstanceEdgeIdsGraph.diff(expectedEdges)
        assert(
          diff2.isEmpty,
          s"Task instance graph edges mismatch. Missing edges: ${diff2.take(10)}${if diff2.size > 10 then "..." else ""}"
        )
        // assertEquals(taskInstanceEdgeIdsGraph, expectedEdges)
      }
    }
  }

  private def scalaModuleTaskEdges(moduleId: String): Set[(String, String)] =
    baseScalaModuleTaskEdges(moduleId) ++ Set(
      (s"${moduleId}.finalMainClass", s"${moduleId}.mainClasses"),
      (s"${moduleId}.finalMainClass", s"${moduleId}.mainClass"),
      (s"${moduleId}.mainClasses", s"${moduleId}.compile"),
      (s"${moduleId}.mainClasses", s"${moduleId}.classes"),
      (s"${moduleId}.run", s"${moduleId}.jvmOptions"),
      (s"${moduleId}.run", s"${moduleId}.runClasspath"),
      (s"${moduleId}.run", s"${moduleId}.mainClasses"),
      (s"${moduleId}.run", s"${moduleId}.finalMainClass"),
      (s"${moduleId}.runMain", s"${moduleId}.jvmOptions"),
      (s"${moduleId}.runMain", s"${moduleId}.runClasspath"),
      (s"${moduleId}.runMain", s"${moduleId}.mainClasses")
    )

  private def scalaTestModuleTaskEdges(moduleId: String): Set[(String, String)] =
    scalaModuleTaskEdges(moduleId) ++ Set(
      (s"${moduleId}.test", s"${moduleId}.classes"),
      (s"${moduleId}.test", s"${moduleId}.runClasspath"),
      (s"${moduleId}.testClasses", s"${moduleId}.classes"),
      (s"${moduleId}.testClasses", s"${moduleId}.runClasspath")
    )

  // TODO too rigid, necessary??
  private def baseScalaModuleTaskEdges(moduleId: String): Set[(String, String)] =
    Set(
      (s"${moduleId}.allDependencies", s"${moduleId}.dependencies"),
      (s"${moduleId}.allClassesDirs", s"${moduleId}.classes"),
      (s"${moduleId}.allJars", s"${moduleId}.jar"),
      (s"${moduleId}.assembly", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.assembly", s"${moduleId}.allDependencies"),
      (s"${moduleId}.assembly", s"${moduleId}.mandatoryDependencies"),
      (s"${moduleId}.assembly", s"${moduleId}.allJars"),
      (s"${moduleId}.assembly", s"${moduleId}.finalManifest"),
      (s"${moduleId}.assembly", s"${moduleId}.allJars"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.mandatoryDependencies"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.allDependencies"),
      (s"${moduleId}.compileClasspath", s"${moduleId}.allClassesDirs"),
      (s"${moduleId}.compilerDeps", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.compile", s"${moduleId}.sources"),
      (s"${moduleId}.compile", s"${moduleId}.generatedSources"),
      (s"${moduleId}.compile", s"${moduleId}.javaHome"),
      (s"${moduleId}.compile", s"${moduleId}.javaVersion"),
      (s"${moduleId}.compile", s"${moduleId}.javacOptions"),
      (s"${moduleId}.compile", s"${moduleId}.scalacOptions"),
      (s"${moduleId}.compile", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.compile", s"${moduleId}.compilerDeps"),
      (s"${moduleId}.compile", s"${moduleId}.compileClasspath"),
      (s"${moduleId}.compile", s"${moduleId}.classes"),
      (s"${moduleId}.compile", s"${moduleId}.scalacPlugins"),
      (s"${moduleId}.compile", s"${moduleId}.javacAnnotationProcessors"),
      (s"${moduleId}.compile", s"${moduleId}.semanticdbEnabled"),
      (s"${moduleId}.compile", s"${moduleId}.semanticdb"),
      (s"${moduleId}.dependencies", s"${moduleId}.deps"),
      (s"${moduleId}.dependencies", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.finalManifest", s"${moduleId}.manifest"),
      (s"${moduleId}.finalManifest", s"${moduleId}.finalMainClass"),
      (s"${moduleId}.finalManifest", s"${moduleId}.pomSettings"),
      (s"${moduleId}.jar", s"${moduleId}.compile"),
      (s"${moduleId}.jar", s"${moduleId}.finalManifest"),
      (s"${moduleId}.javadocJar", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.javadocJar", s"${moduleId}.classes"),
      (s"${moduleId}.javadocJar", s"${moduleId}.compilerDeps"),
      (s"${moduleId}.javadocJar", s"${moduleId}.compileClasspath"),
      (s"${moduleId}.javadocJar", s"${moduleId}.sources"),
      (s"${moduleId}.javadocJar", s"${moduleId}.compile"),
      (s"${moduleId}.javadocJar", s"${moduleId}.pomSettings"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.semanticdbEnabled"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.javaSemanticdbVersion"),
      (s"${moduleId}.javacAnnotationProcessors", s"${moduleId}.javacAnnotationProcessorDeps"),
      (s"${moduleId}.mandatoryDependencies", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.moduleDepsPomSettings", s"${moduleId}.pomSettings"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.pomSettings"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.mandatoryDependencies"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.dependencies"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.moduleDepsPomSettings"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.sourcesJar"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.javadocJar"),
      (s"${moduleId}.publishArtifacts", s"${moduleId}.jar"),
      (s"${moduleId}.publishLocal", s"${moduleId}.publishArtifacts"),
      (s"${moduleId}.publish", s"${moduleId}.publishArtifacts"),
      (s"${moduleId}.runMvnApp", s"${moduleId}.sources"),
      (s"${moduleId}.runMvnApp", s"${moduleId}.jvmOptions"),
      (s"${moduleId}.runMvnApp", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.runClasspath", s"${moduleId}.compile"),
      (s"${moduleId}.runClasspath", s"${moduleId}.compileClasspath"),
      (s"${moduleId}.runClasspath", s"${moduleId}.resources"),
      (s"${moduleId}.scalaSemanticdbVersion", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.scalaVersion"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.semanticdbEnabled"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.scalaSemanticdbVersion"),
      (s"${moduleId}.scalacPlugins", s"${moduleId}.scalacPluginDeps"),
      (s"${moduleId}.sourcesJar", s"${moduleId}.sources"),
      (s"${moduleId}.sourcesJar", s"${moduleId}.pomSettings")
    )

  private def transitiveScalaModuleTaskEdges(moduleId: String, dependencyModuleId: String): Set[(String, String)] =
    Set(
      (s"${moduleId}.allDependencies", s"${dependencyModuleId}.allDependencies"),
      (s"${moduleId}.allClassesDirs", s"${dependencyModuleId}.allClassesDirs"),
      (s"${moduleId}.compileClasspath", s"${dependencyModuleId}.compileClasspath"),
      (s"${moduleId}.compile", s"${dependencyModuleId}.compile"),
      (s"${moduleId}.runClasspath", s"${dependencyModuleId}.runClasspath"),
      (s"${moduleId}.allJars", s"${dependencyModuleId}.allJars"),
      (s"${moduleId}.moduleDepsPomSettings", s"${dependencyModuleId}.moduleDepsPomSettings")
    )
}
