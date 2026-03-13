package ba.sake.deder

import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.*

class ExecutionPlannerSuite extends munit.FunSuite {

  private val testProjectsDir = os.pwd / "server/test/resources/sample-projects"

  test("ExecutionPlanner builds correct execution plan") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi/deder.pkl")
    val projectConfig = parsedConfig.toOption.get
    val tasksRegistry = TasksRegistry(CoreTasks())
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
            "common.javaSemanticdbVersion",
            "common.javacAnnotationProcessorDeps",
            "common.javacOptions",
            "common.scalaVersion",
            "common.scalacOptions",
            "common.scalacPluginDeps",
            "common.semanticdb",
            "common.semanticdbEnabled",
            "common.sources"
          ),
          Set(
            "common.allClassesDirs",
            "common.mandatoryDependencies",
            "common.compilerDeps",
            "common.dependencies",
            "common.javacAnnotationProcessors",
            "common.scalaSemanticdbVersion"
          ),
          Set("common.allDependencies", "common.scalacPlugins"),
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
    val tasksRegistry = TasksRegistry(CoreTasks())
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

  test("ExecutionPlanner returns correct tasks for getAffectingConfigValueTasks") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi" / "deder.pkl")
    val projectConfig = parsedConfig.toOption.get
    val tasksRegistry = TasksRegistry(CoreTasks())
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
          "common.javaSemanticdbVersion",
          "common.scalacOptions",
          "common.scalacPluginDeps",
          "common.scalaVersion",
          "common.semanticdbEnabled",
          "backend.deps",
          "backend.javaHome",
          "backend.javaVersion",
          "backend.javacAnnotationProcessorDeps",
          "backend.javacOptions",
          "backend.javaSemanticdbVersion",
          "backend.scalacOptions",
          "backend.scalacPluginDeps",
          "backend.scalaVersion",
          "backend.semanticdbEnabled",
          "frontend.deps",
          "frontend.javaHome",
          "frontend.javaVersion",
          "frontend.javacAnnotationProcessorDeps",
          "frontend.javacOptions",
          "frontend.javaSemanticdbVersion",
          "frontend.scalacOptions",
          "frontend.scalacPluginDeps",
          "frontend.scalaVersion",
          "frontend.semanticdbEnabled",
          "uber.deps",
          "uber.javaHome",
          "uber.javaVersion",
          "uber.javacAnnotationProcessorDeps",
          "uber.javacOptions",
          "uber.javaSemanticdbVersion",
          "uber.scalacOptions",
          "uber.scalacPluginDeps",
          "uber.scalaVersion",
          "uber.semanticdbEnabled"
        )
      )
    }
  }

}
