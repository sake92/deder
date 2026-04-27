package ba.sake.deder

import scala.concurrent.duration.*

class RunTestsSuite extends BaseIntegrationSuite {

  override def munitTimeout: Duration = 3.minutes

  test("deder should run JUnit4 tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "junit4", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("2 passed"), s"Expected test output to contain '2 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run JUnit5 tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "junit5", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("2 passed"), s"Expected test output to contain '2 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Scalatest tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "scalatest", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("1 passed"), s"Expected test output to contain '1 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Specs2 tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "specs2", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("2 passed"), s"Expected test output to contain '2 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Munit tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "munit", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("1 passed"), s"Expected test output to contain '1 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Utest tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "utest", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("1 passed"), s"Expected test output to contain '1 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Weaver tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "weaver", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("2 passed"), s"Expected test output to contain '2 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Ztest tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "ztest", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("1 passed"), s"Expected test output to contain '1 passed', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

}
