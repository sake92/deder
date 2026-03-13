package ba.sake.deder

import scala.concurrent.duration.*
import scala.util.Properties

class ScalaNativeIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 2.minutes

  // default command is compile
  // and the logs go to stderr!
  test("deder should compile scalanative cli project") {
    withTestProject("sample-projects/scalanative") { projectPath =>
      locally {
        val dederOutput = executeDederCommand(projectPath, "exec").err.text()
        assert(dederOutput.contains("Executing 'compile' task on module: cli"))
        val compilingCount = dederOutput.linesIterator.count(_.matches(".*compiling .* source to .*"))
        assertEquals(compilingCount, 1)
      }
    }
  }

  test("deder should nativeLink cli project") {
    withTestProject("sample-projects/scalanative") { projectPath =>
      locally {
        executeDederCommand(projectPath, "exec -m cli -t nativeLink")
        val command = s"./.deder/out/cli/nativeLink/cli"
        val res = os.proc(command).call(cwd = projectPath, stderr = os.Pipe)
        val resText = res.out.text()
        assert(resText.contains("Hello from Scala Native!"))
      }
    }
  }

}
