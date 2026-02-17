package ba.sake.deder

import scala.concurrent.duration.*
import scala.util.Properties
import ba.sake.tupson.*

class ScalaJsIntegrationSuite extends munit.FunSuite {

  // first compile can take a while
  override def munitTimeout = 1.minute
  
  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  // default command is compile
  // and the logs go to stderr!
  test("deder should compile multimodule project") {
    withTestProject(testResourceDir / "sample-projects/scalajs") { projectPath =>
      locally {
        val dederOutputJson = executeDederCommand(projectPath, "exec -m frontend -t compileClasspath --json").out.text()
        val dederOutput = dederOutputJson.parseJson[Map[String, List[String]]]
        val frontendCompileClasspath = dederOutput("frontend")
        assert(frontendCompileClasspath(0).endsWith("/.deder/out/frontend/classes"))
        assert(frontendCompileClasspath.exists(_.contains("scala3-library_3-3.7.1.jar")))
        assert(frontendCompileClasspath.exists(_.contains("scala-library-2.13.16.jar")))
      }
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on modules: frontend"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 5)
      }
      locally {
        os.write.append(projectPath / "common/src/Common.scala", "\n// some change to trigger recompilation\n")
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on modules: frontend"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 1)
      }
    }
  }

  test("deder should link scalajs") {
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
