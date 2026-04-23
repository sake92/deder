package ba.sake.deder

import scala.concurrent.duration.*

class CustomRepoIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 2.minute

  test("deder should resolve deps from a file:// custom repository") {
    withTestProject("sample-projects/custom-repo") { projectPath =>
      val localRepoUrl = (projectPath / "local-repo").toNIO.toUri.toString
      val pkl = os.read(projectPath / "deder.pkl")
      val rewritten = pkl.replace("__LOCAL_REPO_URL__", localRepoUrl)
      os.write.over(projectPath / "deder.pkl", rewritten)

      val res = executeDederCommand(projectPath, "exec -t run -m app")
      val out = res.out.text()
      assert(res.exitCode == 0, s"exit=${res.exitCode}, stderr=${res.err.text()}\nstdout=$out")
      assert(out.contains("hello, deder"), s"output did not contain greeting: $out")
    }
  }
}
