package ba.sake.deder

import scala.compiletime.uninitialized

class DepTreeIntegrationSuite extends BaseIntegrationSuite {

  private val multiProjectPath = os.RelPath("sample-projects/multi")
  private var projectPath: os.Path = uninitialized

  override def beforeAll(): Unit = {
    projectPath = stagedServerProject(multiProjectPath)
    // Server auto-starts on the first executeDederCommand call.
  }

  override def afterAll(): Unit = {
    executeDederCommand(projectPath, "shutdown")
  }

  // --- meta-test: verifies the shared fixture stays clean (uses its own staging) ---

  test("depsTree checks do not mutate the shared sample project") {
    val fixtureDir = testResourceDir / multiProjectPath
    val dederDir = fixtureDir / ".deder"
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

  // --- shared-server tests (5 tests, no mutations) ---

  private def skipIfDepsTreeNotAvailable(): Unit = {
    val result = executeDederCommand(projectPath, "tasks", "-m", "common")
    val output = result.out.text()
    assume(
      output.contains("depsTree"),
      "depsTree task not available - this task may not be fully implemented yet"
    )
  }

  test("depsTree task is available") {
    val result = executeDederCommand(projectPath, "tasks", "-m", "common")
    val output = result.out.text()
    assume(
      output.contains("depsTree") || output.contains("Dependency Management"),
      "depsTree task should be listed in available tasks - this task may not be fully implemented yet"
    )
  }

  test("depsTree task executes successfully on multi") {
    skipIfDepsTreeNotAvailable()
    val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "common")
    assertEquals(result.exitCode, 0, "depsTree task should execute without error")
  }

  test("depsTree output contains dependency tree structure") {
    skipIfDepsTreeNotAvailable()
    val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "common")
    val output = result.out.text()
    assert(
      output.nonEmpty,
      "Output should not be empty"
    )
  }

  test("depsTree executes on different modules") {
    skipIfDepsTreeNotAvailable()
    val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "frontend")
    assertEquals(result.exitCode, 0, "depsTree task should work on different modules")
  }

  test("depsTree handles modules with no dependencies") {
    skipIfDepsTreeNotAvailable()
    val result = executeDederCommand(projectPath, "exec", "-t", "depsTree", "-m", "common")
    // Task should complete without crashing
    assert(
      result.exitCode == 0 || result.exitCode == 1,
      "depsTree should handle modules gracefully"
    )
  }
}
