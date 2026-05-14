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

  def cs(s: String): Int = s.length

  // ============================================================================
  // Subcommand completion
  // ============================================================================

  test("TabCompleter completes subcommands") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder ", cs("deder ")).toSet,
      Set("version", "clean", "complete", "modules", "tasks", "plan", "exec", "shutdown", "import", "bsp", "help")
    )

    assertEquals(completer.complete("deder c", cs("deder c")).toSet, Set("clean", "complete"))
  }

  // ============================================================================
  // exec command
  // ============================================================================

  test("exec command completes all flags") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder exec ", cs("deder exec ")).toSet,
      Set("-m", "--modules", "-t", "--task", "--json", "-w", "--watch", "-l", "--log-level")
    )

    assertEquals(
      completer.complete("deder exec --", cs("deder exec --")).toSet,
      Set("--modules", "--task", "--json", "--watch", "--log-level")
    )
  }

  // ============================================================================
  // modules command
  // ============================================================================

  test("modules command completes all flags") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder modules ", cs("deder modules ")).toSet,
      Set("-m", "--modules", "--depth-down", "--depth-up", "--json", "--dot", "--mermaid")
    )

    assertEquals(
      completer.complete("deder modules --", cs("deder modules --")).toSet,
      Set("--modules", "--depth-down", "--depth-up", "--json", "--dot", "--mermaid")
    )
  }

  test("modules command completes module values after -m") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder modules -m ", cs("deder modules -m ")).toSet,
      Set("common", "frontend", "backend", "uber", "uber-test")
    )

    assertEquals(
      completer.complete("deder modules -m f", cs("deder modules -m f")).toSet,
      Set("frontend")
    )

    assertEquals(
      completer.complete("deder modules --modules ", cs("deder modules --modules ")).toSet,
      Set("common", "frontend", "backend", "uber", "uber-test")
    )
  }

  // ============================================================================
  // tasks command
  // ============================================================================

  test("tasks command completes all flags") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder tasks ", cs("deder tasks ")).toSet,
      Set("-m", "--module", "--json", "--dot", "--mermaid")
    )

    assertEquals(
      completer.complete("deder tasks --", cs("deder tasks --")).toSet,
      Set("--module", "--json", "--dot", "--mermaid")
    )
  }

  // ============================================================================
  // plan command
  // ============================================================================

  test("plan command completes all flags") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder plan ", cs("deder plan ")).toSet,
      Set("-m", "--modules", "-t", "--task", "--json", "--dot", "--mermaid")
    )

    assertEquals(
      completer.complete("deder plan --", cs("deder plan --")).toSet,
      Set("--modules", "--task", "--json", "--dot", "--mermaid")
    )
  }

  test("plan command completes module and task values") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder plan -m ", cs("deder plan -m ")).toSet,
      Set("common", "frontend", "backend", "uber", "uber-test")
    )

    locally {
      val completions = completer.complete("deder plan -t ", cs("deder plan -t ")).toSet
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
      completer.complete("deder plan --modules frontend --task compile", cs("deder plan --modules frontend --task compile")).toSet,
      Set("compile", "compileOnlyDeps", "compileOnlyDependencies", "compilerDeps", "compilerJars", "compileClasspath")
    )
  }

  // ============================================================================
  // clean command
  // ============================================================================

  test("clean command completes all flags") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder clean ", cs("deder clean ")).toSet,
      Set("-m", "--modules", "-t", "--task")
    )

    assertEquals(
      completer.complete("deder clean -m ", cs("deder clean -m ")).toSet,
      Set("common", "frontend", "backend", "uber", "uber-test")
    )

    assertEquals(
      completer.complete("deder clean -t ", cs("deder clean -t ")).toSet,
      taskIds.toSet
    )
  }

  // ============================================================================
  // import command
  // ============================================================================

  test("import command completes flags and enum values") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder import ", cs("deder import ")).toSet,
      Set("--from")
    )

    assertEquals(
      completer.complete("deder import --from ", cs("deder import --from ")).toSet,
      Set("sbt")
    )

    assertEquals(
      completer.complete("deder import --from s", cs("deder import --from s")).toSet,
      Set("sbt")
    )
  }

  // ============================================================================
  // complete command
  // ============================================================================

  test("complete command completes flags") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder complete ", cs("deder complete ")).toSet,
      Set("-s", "--shell", "-c", "--command-line", "-p", "--cursor-pos", "-o", "--output")
    )

    assertEquals(
      completer.complete("deder complete --", cs("deder complete --")).toSet,
      Set("--shell", "--command-line", "--cursor-pos", "--output")
    )
  }

  test("complete command completes shell enum values") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder complete -s ", cs("deder complete -s ")).toSet,
      Set("bash", "zsh", "fish", "powershell")
    )

    assertEquals(
      completer.complete("deder complete --shell ", cs("deder complete --shell ")).toSet,
      Set("bash", "zsh", "fish", "powershell")
    )

    assertEquals(
      completer.complete("deder complete -s ba", cs("deder complete -s ba")).toSet,
      Set("bash")
    )
  }

  // ============================================================================
  // help command
  // ============================================================================

  test("help command completes flags and subcommand values") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)

    assertEquals(
      completer.complete("deder help ", cs("deder help ")).toSet,
      Set("-c", "--command")
    )

    // After -c / --command, complete with known subcommands
    assertEquals(
      completer.complete("deder help -c ", cs("deder help -c ")).toSet,
      Set("version", "clean", "complete", "modules", "tasks", "plan", "exec", "shutdown", "import", "bsp", "help")
    )

    assertEquals(
      completer.complete("deder help -c c", cs("deder help -c c")).toSet,
      Set("clean", "complete")
    )
  }

  // ============================================================================
  // shutdown command
  // ============================================================================

  test("shutdown command completes nothing") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)
    assertEquals(completer.complete("deder shutdown ", cs("deder shutdown ")).toSet, Set.empty)
  }

  // ============================================================================
  // version command
  // ============================================================================

  test("version command completes nothing") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)
    assertEquals(completer.complete("deder version ", cs("deder version ")).toSet, Set.empty)
  }

  // ============================================================================
  // bsp command
  // ============================================================================

  test("bsp command completes install") {
    val completer = new TabCompleter(moduleIds = moduleIds, taskIds = taskIds)
    assertEquals(completer.complete("deder bsp ", cs("deder bsp ")).toSet, Set("install"))
  }

}
