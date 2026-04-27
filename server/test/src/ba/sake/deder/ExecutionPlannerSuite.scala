package ba.sake.deder

import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.*

class ExecutionPlannerSuite extends munit.FunSuite {

  private val testProjectsDir = os.pwd / "server/test/resources/sample-projects"

  val tasksRegistry = TasksRegistry(CoreTasks().all)

  test("ExecutionPlanner builds correct execution plan") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi/deder.pkl")
    val projectConfig = parsedConfig.toOption.get
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val executionPlanner = ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)
    val modules = tasksResolver.allModules.map(_.asInstanceOf[ScalaModule])
    val commonModule = modules.find(_.id == "common").get
    val frontendModule = modules.find(_.id == "frontend").get
    val backendModule = modules.find(_.id == "backend").get
    val uberModule = modules.find(_.id == "uber").get
    val uberTestModule = modules.find(_.id == "uber-test").get.asInstanceOf[ScalaTestModule]
    locally {
      val stages = executionPlanner.getExecStages(commonModule.id, "compileClasspath")
      val stageTaskIds = stages.map(stage => stage.map(_.id).toSet)
      assertEquals(
        stageTaskIds,
        Seq(
          Set("common.classes", "common.deps", "common.scalaVersion"),
          Set("common.allClassesDirs", "common.dependencies", "common.mandatoryDependencies"),
          Set("common.allDependencies"),
          Set("common.compileClasspath")
        ),
        "common compileClasspath stages mismatch"
      )
    }
    locally {
      val stages = executionPlanner.getExecStages(commonModule.id, "compile")
      val stageTaskIds = stages.map(stage => stage.map(_.id).toSet)
      assertEquals(
        stageTaskIds,
        Seq(
          Set(
            "common.classes",
            "common.deps",
            "common.javaHome",
            "common.javaVersion",
            "common.generatedSources",
            "common.javacAnnotationProcessorDeps",
            "common.javacOptions",
            "common.scalaVersion",
            "common.scalacOptions",
            "common.scalacPluginDeps",
            "common.sources",
            "common.javaSemanticdbVersion",
            "common.semanticdbEnabled",
            "common.semanticdb"
          ),
          Set(
            "common.allClassesDirs",
            "common.mandatoryDependencies",
            "common.compilerDeps",
            "common.dependencies",
            "common.javacAnnotationProcessors",
            "common.scalacPlugins",
            "common.sourceFiles",
            "common.scalaSemanticdbVersion"
          ),
          Set("common.allDependencies", "common.compilerJars"),
          Set("common.compileClasspath"),
          Set("common.compile")
        ),
        "common compile stages mismatch"
      )
    }
  }

  test("ExecutionPlanner returns correct tasks for getAffectingSourceFileTasks") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
    val projectConfig = parsedConfig.toOption.get
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val executionPlanner = ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)
    val modules = tasksResolver.allModules.map(_.asInstanceOf[ScalaModule])
    val uberModule = modules.find(_.id == "uber").get
    locally {
      val affectingSourceFileTasks = executionPlanner.getAffectingSourceFileTasks(uberModule.id, "compile")
      val taskIds = affectingSourceFileTasks.map(_.id).toSet
      assertEquals(
        taskIds,
        Set("common.sources", "frontend.sources", "backend.sources", "uber.sources")
      )
    }
  }

  test("getTaskInstances returns Left for non-existent module") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
    val projectConfig = parsedConfig.toOption.get
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val executionPlanner = ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)
    val result = executionPlanner.getTaskInstances(Seq("nonexistent"), "compile")
    assert(result.isLeft)
  }

  test("getTaskInstances returns Left with recommendations for non-existent task on existing module") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
    val projectConfig = parsedConfig.toOption.get
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val executionPlanner = ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)
    val result = executionPlanner.getTaskInstances(Seq("common"), "nonexistentTask")
    assert(result.isLeft)
    assert(result.left.getOrElse(Seq.empty).nonEmpty) // should have recommendations
  }

  test("ExecutionPlanner returns correct tasks for getAffectingConfigValueTasks") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
    val projectConfig = parsedConfig.toOption.get
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val executionPlanner = ExecutionPlanner(tasksResolver.taskInstancesGraph, tasksResolver.taskInstancesPerModule)
    val modules = tasksResolver.allModules.map(_.asInstanceOf[ScalaModule])
    val uberModule = modules.find(_.id == "uber").get
    locally {
      val affectingConfigValueTasks = executionPlanner.getAffectingConfigValueTasks(uberModule.id, "compile")
      val taskIds = affectingConfigValueTasks.map(_.id).toSet
      assertEquals(
        taskIds,
        Set(
          "common.deps",
          "common.javaHome",
          "common.javaVersion",
          "common.javacAnnotationProcessorDeps",
          "common.javacOptions",
          "common.scalacOptions",
          "common.scalacPluginDeps",
          "common.scalaVersion",
          "common.javaSemanticdbVersion",
          "common.semanticdbEnabled",
          "backend.deps",
          "backend.javaHome",
          "backend.javaVersion",
          "backend.javacAnnotationProcessorDeps",
          "backend.javacOptions",
          "backend.scalacOptions",
          "backend.scalacPluginDeps",
          "backend.scalaVersion",
          "backend.javaSemanticdbVersion",
          "backend.semanticdbEnabled",
          "frontend.deps",
          "frontend.javaHome",
          "frontend.javaVersion",
          "frontend.javacAnnotationProcessorDeps",
          "frontend.javacOptions",
          "frontend.scalacOptions",
          "frontend.scalacPluginDeps",
          "frontend.scalaVersion",
          "frontend.javaSemanticdbVersion",
          "frontend.semanticdbEnabled",
          "uber.deps",
          "uber.javaHome",
          "uber.javaVersion",
          "uber.javacAnnotationProcessorDeps",
          "uber.javacOptions",
          "uber.scalacOptions",
          "uber.scalacPluginDeps",
          "uber.scalaVersion",
          "uber.javaSemanticdbVersion",
          "uber.semanticdbEnabled"
        )
      )
    }
  }

}
