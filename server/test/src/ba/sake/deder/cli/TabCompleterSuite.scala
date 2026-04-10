package ba.sake.deder.cli

import ba.sake.deder.config.ConfigParser
import ba.sake.deder.*
import ba.sake.deder.publish.PublishTasks

class TabCompleterSuite extends munit.FunSuite {

  private val testProjectsDir = os.pwd / "server/test/resources/sample-projects"

  test("TabCompleter completes subcommands") {
    val configParser = ConfigParser(writeJson = false)
    val parsedConfig = configParser.parse(testProjectsDir / "multi/deder.pkl")
    assert(parsedConfig.isRight, parsedConfig.left.get)
    val projectConfig = parsedConfig.toOption.get
    val coreTasks = CoreTasks()

    val publishTasks = PublishTasks(coreTasks)
    val tasksRegistry = TasksRegistry(coreTasks.all ++ publishTasks.all)
    val tasksResolver = TasksResolver(projectConfig, tasksRegistry)
    val completer = new TabCompleter(tasksResolver)

    assertEquals(
      completer.complete("deder ", 6).toSet,
      Set("version", "clean", "complete", "modules", "tasks", "plan", "exec", "shutdown", "import", "bsp", "help")
    )

    assertEquals(completer.complete("deder c", 7).toSet, Set("clean", "complete"))

    assertEquals(
      completer.complete("deder exec ", 11).toSet,
      Set("-m", "--module", "-t", "--task", "--json", "-w", "--watch")
    )
    assertEquals(completer.complete("deder exec --", 13).toSet, Set("--module", "--task", "--json", "--watch"))

    assertEquals(
      completer.complete("deder clean -m ", 15).toSet,
      Set("common", "frontend", "backend", "uber", "uber-test")
    )

    locally {
      val completions = completer.complete("deder plan -t ", 14).toSet
      val expected = Set(
        "generatedSources",
        "mainClasses",
        "publishLocal",
        "testClasses",
        "semanticdb",
        "runMain",
        "publish",
        "deps",
        "mandatoryDependencies",
        "jvmOptions",
        "resources",
        "scalacPlugins",
        "sourcesJar",
        "javaSemanticdbVersion",
        "pomSettings",
        "javaVersion",
        "test",
        "scalaSemanticdbVersion",
        "runClasspath",
        "allClassesDirs",
        "publishArtifacts",
        "finalMainClass",
        "scalacPluginDeps",
        "javacAnnotationProcessors",
        "classes",
        "allDependencies",
        "compilerDeps",
        "compile",
        "compileBsp",
        "run",
        "assembly",
        "javacOptions",
        "javacAnnotationProcessorDeps",
        "compile",
        "mainClass",
        "scalaVersion",
        "scalacOptions",
        "allJars",
        "moduleDepsPomSettings",
        "sources",
        "javadocJar",
        "jar",
        "compileClasspath",
        "javaHome",
        "dependencies"
      )
      assert(expected.subsetOf(completions))
    }
    assertEquals(
      completer.complete("deder plan -m frontend -t compile", 33).toSet,
      Set("compile", "compileBsp", "compilerDeps", "compileClasspath")
    )
  }

}
