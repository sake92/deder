package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.*

class ExecutionPlannerSuite extends munit.FunSuite {

  test("ExecutionPlanner builds correct execution plan") {
    val projectPath = os.resource / "sample-projects/multi"
    val projectConfigStr = os.read(projectPath / "deder.pkl")
    val configParser = ConfigParser()
    val parsedConfig = configParser.parse(projectConfigStr)
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
      val stageTaskIds = stages.map(stage => stage.map(_.id).toSet).toSeq
      assertEquals(
        stageTaskIds,
        Seq(
          Set("common.classes", "common.deps", "common.scalaVersion", "common.scalacOptions"),
          Set("common.allClassesDirs", "common.dependencies"),
          Set("common.allDependencies"),
          Set("common.compileClasspath")
        )
      )
    }
    locally {
      val stages = executionPlanner.getExecStages(commonModule.id, "compile")
      val stageTaskIds = stages.map(stage => stage.map(_.id).toSet).toSeq
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
            "common.scalaSemanticdbVersion",
            "common.scalaVersion",
            "common.scalacOptions",
            "common.scalacPluginDeps",
            "common.semanticdbEnabled",
            "common.sources"
          ),
          Set(
            "common.allClassesDirs",
            "common.dependencies",
            "common.javacAnnotationProcessors",
            "common.scalacPlugins"
          ),
          Set("common.allDependencies"),
          Set("common.compileClasspath"),
          Set("common.compile")
        )
      )
    }
  }

  test("ExecutionPlanner returns correct tasks for getAffectingSourceFileTasks") {
    val projectPath = os.resource / "sample-projects/multi"
    val projectConfigStr = os.read(projectPath / "deder.pkl")
    val configParser = ConfigParser()
    val parsedConfig = configParser.parse(projectConfigStr)
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
    val projectPath = os.resource / "sample-projects/multi"
    val projectConfigStr = os.read(projectPath / "deder.pkl")
    val configParser = ConfigParser()
    val parsedConfig = configParser.parse(projectConfigStr)
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
          "common.scalaSemanticdbVersion",
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
          "backend.scalaSemanticdbVersion",
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
          "frontend.scalaSemanticdbVersion",
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
          "uber.scalaSemanticdbVersion",
          "uber.scalaVersion",
          "uber.semanticdbEnabled"
        )
      )
    }
  }

}
