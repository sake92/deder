package ba.sake.deder

import scala.concurrent.duration.*

class CrossIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 1.minute

  // default command is compile
  // and the logs go to stderr!
  test("deder should compile cross project") {
    withTestProject("sample-projects/cross") { projectPath =>
      locally {
        val dederCompileRes = executeDederCommand(projectPath, "exec")
        assert(dederCompileRes.exitCode == 0, s"Deder compile failed with error: ${dederCompileRes.err.text()}")
        val dederCompileResOutput = dederCompileRes.err.text()
        val expectedModules = Seq(
          "common-js-2.12.21",
          "common-js-2.13.18",
          "common-js-3.7.4",
          "common-jvm-2.12.21",
          "common-jvm-2.13.18"
          /*"common-jvm-3.7.4",
          "common-jvm-test-2.12.21",
          "common-jvm-test-2.13.18",
          "common-jvm-test-3.7.4",
          "common-native-2.12.21",
          "common-native-2.13.18",
          "common-native-3.7.4"*/
        )
        expectedModules.foreach { moduleId =>
          assert(dederCompileResOutput.contains(moduleId), s"Expected module '$moduleId' was not compiled")
        }
        assert(dederCompileResOutput.contains("and 7 more"), s"Expected 'and 7 more' was not compiled")
      }
    }
  }

}
