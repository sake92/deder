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

  private def assertReportContains(
      projectPath: os.Path,
      moduleId: String,
      taskName: String,
      suiteName: String,
      expectedSnippets: String*
  ): Unit = {
    val taskOutPath = projectPath / ".deder" / "out" / moduleId / taskName
    val reportPathOpt = os.walk(taskOutPath).find { path =>
      path.segments.toSeq.takeRight(3) == Seq("reports", "junit", s"TEST-$suiteName.xml")
    }
    assert(reportPathOpt.isDefined, s"Expected a report file under $taskOutPath")
    val reportPath = reportPathOpt.get
    val xml = os.read(reportPath)
    expectedSnippets.foreach { snippet =>
      assert(xml.contains(snippet), s"Expected report $reportPath to contain '$snippet', got: $xml")
    }
  }

}
