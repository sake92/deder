package ba.sake.deder

import scala.concurrent.duration.*
import scala.util.Properties

class CrossIntegrationSuite extends munit.FunSuite {

  // first compile can take a while
  override def munitTimeout = 1.minute
  
  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  // default command is compile
  // and the logs go to stderr!
  test("deder should compile cross project") {
    withTestProject(testResourceDir / "sample-projects/cross") { projectPath =>
      locally {
        val dederCompileRes = executeDederCommand(projectPath, "exec")
        assert(dederCompileRes.exitCode == 0, s"Deder compile failed with error: ${dederCompileRes.err.text()}")
        val dederCompileResOutput = dederCompileRes.err.text()
        val expectedModules = Seq(
          "common-js-2.12.21", "common-js-2.13.18", "common-js-3.7.4",
          "common-jvm-2.12.21", "common-jvm-2.13.18", "common-jvm-3.7.4", "common-jvm-test-2.12.21",
          "common-jvm-test-2.13.18", "common-jvm-test-3.7.4", "common-native-2.12.21", "common-native-2.13.18", "common-native-3.7.4"
        )
        expectedModules.foreach { moduleId =>
          assert(dederCompileResOutput.contains(moduleId), s"Expected module '$moduleId' was not compiled")
        }
      }
    }
  }

  /*
  test("deder should fastLinkJs scalajs project") {
    withTestProject(testResourceDir / "sample-projects/scalajs") { projectPath =>
      locally {
        executeDederCommand(projectPath, "exec -m frontend -t fastLinkJs")
        val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
        val command = s"node .deder/out/frontend/fastLinkJs/main.js"
        val cmd = shell ++ Seq(command)
        val res = os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
        val resText = res.out.text()
        assert(resText.contains("Hello, Scala.js!"))
      }
    }
  }*/

  private def withTestProject(testProjectPath: os.Path)(testCode: os.Path => Unit): Unit = {
    // mill test runs in sandbox folder, so it is safe to create temp folders here
    val tempDir = os.pwd / testProjectPath.last / s"temp-${System.currentTimeMillis()}"
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
