package ba.sake.deder

import scala.concurrent.duration.*

class RunTestsSuite extends BaseIntegrationSuite {

  override def munitTimeout: Duration = 3.minutes

  test("deder should run JUnit4 tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "junit4", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 2"), s"Expected test output to contain 'Passed: 2', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run JUnit5 tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "junit5", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 2"), s"Expected test output to contain 'Passed: 2', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should generate JUnit XML reports for forked tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "junit4", "-t", "test")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
      assertReportContains(projectPath, "junit4", "test", "junit4test.JUnit4Test", "tests=\"2\"", "failures=\"0\"")
    }
  }

  test("deder should run Scalatest tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "scalatest", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Specs2 tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "specs2", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 2"), s"Expected test output to contain 'Passed: 2', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Munit tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "munit", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should generate JUnit XML reports for in-memory tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "munit", "-t", "testInMemory")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
      assertReportContains(projectPath, "munit", "testInMemory", "munittest.MunitSuite", "tests=\"1\"", "failures=\"0\"")
    }
  }

  test("deder should run Utest tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "utest", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Weaver tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "weaver", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 2"), s"Expected test output to contain 'Passed: 2', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Ztest tests") {
    withTestProject("sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "ztest", "-t", "test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  private def assertReportContains(
      projectPath: os.Path,
      moduleId: String,
      taskName: String,
      suiteName: String,
      expectedSnippets: String*
  ): Unit = {
    val reportPath = projectPath / ".deder" / "out" / moduleId / taskName / "reports" / "junit" / s"TEST-$suiteName.xml"
    assert(os.exists(reportPath), s"Expected report file at $reportPath")
    val xml = os.read(reportPath)
    expectedSnippets.foreach { snippet =>
      assert(xml.contains(snippet), s"Expected report $reportPath to contain '$snippet', got: $xml")
    }
  }

}
