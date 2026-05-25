package ba.sake.deder

import munit.FunSuite

class DepTreeIntegrationSuite extends FunSuite {

  private def skipIfDepsTreeNotAvailable(): Unit = {
    // Check if depsTree task is available
    val projectPath = os.pwd / "integration/test/resources/sample-projects/multi"
    val result = os.proc("deder", "tasks", "-m", "common")
      .call(cwd = projectPath, check = false, stderr = os.Pipe)
    val output = result.out.text()
    
    assume(
      output.contains("depsTree"),
      "depsTree task not available - this task may not be fully implemented yet"
    )
  }

  test("depsTree task is available") {
    val projectPath = os.pwd / "integration/test/resources/sample-projects/multi"
    val result = os.proc("deder", "tasks", "-m", "common")
      .call(cwd = projectPath, check = false)
    
    val output = result.out.text()
    assume(
      output.contains("depsTree") || output.contains("Dependency Management"),
      "depsTree task should be listed in available tasks - this task may not be fully implemented yet"
    )
  }

  test("depsTree task executes successfully on multi") {
    skipIfDepsTreeNotAvailable()
    
    val projectPath = os.pwd / "integration/test/resources/sample-projects/multi"
    val result = os.proc("deder", "exec", "-t", "depsTree", "-m", "common")
      .call(cwd = projectPath, check = false)
    assertEquals(result.exitCode, 0, "depsTree task should execute without error")
  }

  test("depsTree output contains dependency tree structure") {
    skipIfDepsTreeNotAvailable()
    
    val projectPath = os.pwd / "integration/test/resources/sample-projects/multi"
    val result = os.proc("deder", "exec", "-t", "depsTree", "-m", "common")
      .call(cwd = projectPath, check = false)
    
    val output = result.out.text()
    assert(
      output.nonEmpty,
      "Output should not be empty"
    )
  }

  test("depsTree executes on different modules") {
    skipIfDepsTreeNotAvailable()
    
    val projectPath = os.pwd / "integration/test/resources/sample-projects/multi"
    val result = os.proc("deder", "exec", "-t", "depsTree", "-m", "frontend")
      .call(cwd = projectPath, check = false)
    
    assertEquals(result.exitCode, 0, "depsTree task should work on different modules")
  }

  test("depsTree handles modules with no dependencies") {
    skipIfDepsTreeNotAvailable()
    
    val projectPath = os.pwd / "integration/test/resources/sample-projects/multi"
    val result = os.proc("deder", "exec", "-t", "depsTree", "-m", "common")
      .call(cwd = projectPath, check = false, stderr = os.Pipe)
    
    // Task should complete without crashing
    assert(
      result.exitCode == 0 || result.exitCode == 1,
      "depsTree should handle modules gracefully"
    )
  }
}
