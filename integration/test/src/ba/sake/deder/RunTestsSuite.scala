package ba.sake.deder

import scala.util.Properties

class RunTestsSuite extends munit.FunSuite {
  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  test("deder should run JUnit4 tests") {
    withTestProject(testResourceDir / "sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec -m junit4 -t test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 2"), s"Expected test output to contain 'Passed: 2', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run JUnit5 tests") {
    withTestProject(testResourceDir / "sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec -m junit5 -t test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 2"), s"Expected test output to contain 'Passed: 2', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Scalatest tests") {
    withTestProject(testResourceDir / "sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec -m scalatest -t test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Munit tests") {
    withTestProject(testResourceDir / "sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec -m munit -t test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Utest tests") {
    withTestProject(testResourceDir / "sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec -m utest -t test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Weaver tests") {
    withTestProject(testResourceDir / "sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec -m weaver -t test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 2"), s"Expected test output to contain 'Passed: 2', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }

  test("deder should run Ztest tests") {
    withTestProject(testResourceDir / "sample-projects/tests") { projectPath =>
      val res = executeDederCommand(projectPath, "exec -m ztest -t test")
      val outText = res.err.text()
      assert(outText.contains("Passed: 1"), s"Expected test output to contain 'Passed: 1', got: ${outText}")
      assertEquals(res.exitCode, 0, s"Expected exit code 0, got ${res.exitCode}")
    }
  }



  private def withTestProject(testProjectPath: os.Path)(testCode: os.Path => Unit): Unit = {
    // mill test runs in sandbox folder, so it is safe to create temp folders here
    val tempDir = os.pwd / testProjectPath.last / s"temp-${System.currentTimeMillis()}"
    println(s"Setting up test project in temporary directory: $tempDir")
    try {
      os.copy(testProjectPath, tempDir, createFolders = true, replaceExisting = true)
      os.write.over(tempDir / ".deder/server.properties", s"localPath=$dederServerPath\n", createFolders = true)
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      // os.remove.all(tempDir)
    }
  }

  private def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
  }
}
