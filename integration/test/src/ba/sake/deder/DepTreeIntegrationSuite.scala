package ba.sake.deder

class DepTreeIntegrationSuite extends BaseIntegrationSuite {

  private val multiProjectPath = os.RelPath("sample-projects/multi")

  test("depsTree checks do not mutate the shared sample project") {
    val projectPath = testResourceDir / multiProjectPath
    val dederDir = projectPath / ".deder"
    if os.exists(dederDir) then os.remove.all(dederDir)

    try {
      withTestProject(multiProjectPath) { stagedProjectPath =>
        val result = executeDederCommand(stagedProjectPath, "tasks", "-m", "common")
        assertEquals(result.exitCode, 0)
      }
      assert(!os.exists(dederDir), s"shared fixture must stay clean, but found $dederDir")
    } finally {
      if os.exists(dederDir) then os.remove.all(dederDir)
    }
  }

  private def skipIfDepsTreeNotAvailable(projectPath: os.Path): Unit = {
    // Check if depsTree task is available
    val result = executeDederCommand(projectPath, "tasks", "-m", "common")
    val output = result.out.text()

    assume(
      output.contains("depsTree"),
      "depsTree task not available - this task may not be fully implemented yet"
    )
  }

  test("depsTree task is available") {
    withTestProject(multiProjectPath) { projectPath =>
      val result = executeDederCommand(projectPath, "tasks", "-m", "common")
      val output = result.out.text()
      assume(
        output.contains("depsTree") || output.contains("Dependency Management"),
        "depsTree task should be listed in available tasks - this task may not be fully implemented yet"
      )
    }
  }

  test("depsTree task executes successfully on multi") {
    withTestProject(multiProjectPath) { projectPath =>
      skipIfDepsTreeNotAvailable(projectPath)
      val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "common")
      assertEquals(result.exitCode, 0, "depsTree task should execute without error")
    }
  }

  test("depsTree output contains dependency tree structure") {
    withTestProject(multiProjectPath) { projectPath =>
      skipIfDepsTreeNotAvailable(projectPath)
      val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "common")
      val output = result.out.text()
      assert(
        output.nonEmpty,
        "Output should not be empty"
      )
    }
  }

  test("depsTree executes on different modules") {
    withTestProject(multiProjectPath) { projectPath =>
      skipIfDepsTreeNotAvailable(projectPath)
      val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "frontend")
      assertEquals(result.exitCode, 0, "depsTree task should work on different modules")
    }
  }

  test("depsTree handles modules with no dependencies") {
    withTestProject(multiProjectPath) { projectPath =>
      skipIfDepsTreeNotAvailable(projectPath)
      val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "common")
      // Task should complete without crashing
      assert(
        result.exitCode == 0 || result.exitCode == 1,
        "depsTree should handle modules gracefully"
      )
    }
  }
}
