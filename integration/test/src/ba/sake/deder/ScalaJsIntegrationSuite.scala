package ba.sake.deder

import scala.concurrent.duration.*
import scala.util.Properties
import ba.sake.tupson.*

class ScalaJsIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 2.minute

  // default command is compile
  // and the logs go to stderr!
  test("deder should compile scalajs project") {
    withTestProject("sample-projects/scalajs") { projectPath =>
      locally {
        val dederOutputJson = executeDederCommand(projectPath, "exec", "-m", "frontend", "-t", "compileClasspath", "--json").out.text()
        val dederOutput = dederOutputJson.parseJson[Map[String, List[String]]]
        val frontendCompileClasspath = dederOutput("frontend")
        assert(frontendCompileClasspath(0).endsWith("/.deder/out/frontend/classes"))
        assert(frontendCompileClasspath.exists(_.contains("scala3-library_sjs1_3-3.7.1.jar")))
        assert(frontendCompileClasspath.exists(_.contains("scalajs-library_2.13-1.20.2.jar")))
        assert(frontendCompileClasspath.exists(_.contains("scala-library-2.13.17.jar")))
        assert(frontendCompileClasspath.exists(_.contains("scalajs-javalib-1.20.2.jar")))
        assert(frontendCompileClasspath.exists(_.contains("scalajs-scalalib_2.13")))
      }
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on modules: frontend, frontend-test"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 1)
      }
    }
  }

  test("deder should fastLinkJs scalajs project") {
    withTestProject("sample-projects/scalajs") { projectPath =>
      locally {
        executeDederCommand(projectPath, "exec", "-m", "frontend", "-t", "fastLinkJs")
        val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
        val command = s"node .deder/out/frontend/fastLinkJs/main.js"
        val cmd = shell ++ Seq(command)
        val res = os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
        val resText = res.out.text()
        assert(resText.contains("Hello, Scala.js!"))
      }
    }
  }

  test("deder should test scalajs project") {
    withTestProject("sample-projects/scalajs") { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-t", "test")
      val outText = res.err.text()
      assert(
        outText.contains("Tests: 1 passed, 1 failed, 1 skipped, 3 total"),
        s"Expected test output to contain 'Tests: 1 passed, 1 failed, 1 skipped, 3 total', got: ${outText}"
      )
      assertEquals(res.exitCode, 1, s"Expected exit code 1, got ${res.exitCode}")
    }
  }

}
