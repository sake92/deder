package ba.sake.deder

import scala.jdk.CollectionConverters.*
import scala.util.Properties

class IntegrationSuite extends munit.FunSuite {

  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  test("deder should work with multimodule project") {
    withTestProject(testResourceDir / "sample-projects/multi") { projectPath =>
      locally {
        val versionOutput = executeDederCommand(projectPath, "version")
        assert(versionOutput.out.text().contains("Client version: 0.0.1"))
        assert(versionOutput.out.text().contains("Server version: 0.0.1"))
      }
    }
  }

  private def withTestProject(testProjectPath: os.Path)(testCode: os.Path => Unit): Unit = {
    // mill test runs in sandbox folder, so it is safe to create temp folders here
    val tempDir = os.pwd / testProjectPath.last / s"temp-${System.currentTimeMillis()}"
    try {
      os.copy(testProjectPath, tempDir, createFolders = true, replaceExisting = true)
      os.write.over(tempDir / ".deder/server.properties", s"localPath=$dederServerPath\n", createFolders = true)
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      os.remove.all(tempDir)
    }
  }

  private def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath)
  }
}
