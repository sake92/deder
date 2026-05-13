package ba.sake.deder.cli

class TabCompleterSuite extends munit.FunSuite {

  private val moduleIds = Seq("common", "frontend", "backend", "uber", "uber-test")
  private val taskIds = Seq(
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
    "semanticdbEnabled",
    "runClasspath",
    "allClassesDirs",
    "publishArtifacts",
    "finalMainClass",
    "scalacPluginDeps",
    "javacAnnotationProcessors",
    "classes",
    "allDependencies",
    "compilerDeps",
    "compilerJars",
    "compile",
    "run",
    "assembly",
    "javacOptions",
    "javacAnnotationProcessorDeps",
    "mainClass",
    "scalaVersion",
    "scalacOptions",
    "allJars",
    "moduleDepsPomSettings",
    "sources",
    "sourceFiles",
    "javadocJar",
    "jar",
    "compileClasspath",
    "javaHome",
    "dependencies",
    "compileOnlyDeps",
    "compileOnlyDependencies"
  )

  test("TabCompleter completes subcommands") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

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

    // --mermaid flag is available for modules, tasks, and plan subcommands
    assertEquals(
      completer.complete("deder modules --", 16).toSet,
      Set("--json", "--dot", "--mermaid")
    )
    assertEquals(
      completer.complete("deder tasks --", 14).toSet,
      Set("--module", "--json", "--dot", "--mermaid")
    )
    assertEquals(
      completer.complete("deder plan --", 13).toSet,
      Set("--module", "--task", "--json", "--dot", "--mermaid")
    )

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
        "semanticdbEnabled",
        "runClasspath",
        "allClassesDirs",
        "publishArtifacts",
        "finalMainClass",
        "scalacPluginDeps",
        "javacAnnotationProcessors",
        "classes",
        "allDependencies",
        "compilerDeps",
        "compilerJars",
        "compile",
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
        "sourceFiles",
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
      Set("compile", "compileOnlyDeps", "compileOnlyDependencies", "compilerDeps", "compilerJars", "compileClasspath")
    )
  }

}
