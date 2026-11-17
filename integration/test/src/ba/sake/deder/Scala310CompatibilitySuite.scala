package ba.sake.deder

class Scala310CompatibilitySuite extends BaseIntegrationSuite {

  test("Scala 3.10.1 project compiles and tests") {
    withTestProject("sample-projects/scala-310-compat") { projectPath =>
      val result = executeDederCommand(projectPath, "exec", "--no-color", "-t", "test", "-m", "app-test")
      val output = result.out.text() + result.err.text()

      assertEquals(result.exitCode, 0, output)
      assert(output.contains("1 passed"), output)
    }
  }
}
