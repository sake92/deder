package ba.sake.deder

import ba.sake.tupson.*

import scala.concurrent.duration.*
import scala.util.Properties

class ScalaNativeIntegrationSuite extends munit.FunSuite {

  // first compile can take a while
  override def munitTimeout = 1.minute
  
  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  // default command is compile
  // and the logs go to stderr!
  test("deder should compile scalanative cli project") {
    withTestProject(testResourceDir / "sample-projects/scalanative") { projectPath =>
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on module: cli"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 1)
      }
    }
  }

  test("deder should nativeLink cli project") {
    withTestProject(testResourceDir / "sample-projects/scalanative") { projectPath =>
      locally {
        executeDederCommand(projectPath, "exec -m cli -t nativeLink")
        val command = s"./.deder/out/cli/nativeLink/cli"
        val res = os.proc(command).call(cwd = projectPath, stderr = os.Pipe)
        val resText = res.out.text()
        assert(resText.contains("Hello from Scala Native!"))
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
      // os.remove.all(tempDir)
    }
  }

  private def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
  }
}
