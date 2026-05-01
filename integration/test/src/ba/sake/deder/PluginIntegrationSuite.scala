package ba.sake.deder

import scala.concurrent.duration.*

class PluginIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 5.minute

  test("deder should publish hello-plugin to local folder and load its task in consumer") {
    val localRepoDir = os.temp.dir(prefix = "deder-plugin-repo")
    try {
      // Step 1: publish hello-plugin to the local temp folder
      withTestProject("sample-projects/hello-plugin") { pluginPath =>
        val m2RepoUrl = (os.home / ".m2/repository").toNIO.toUri.toString
        val pkl = os.read(pluginPath / "deder.pkl")
        val rewritten = pkl
          .replace("__M2_REPO_URL__", m2RepoUrl)
          .replace("__PLUGIN_API_VERSION__", dederPluginApiVersion)
          .replace("__PLUGIN_LOCAL_REPO__", localRepoDir.toString)
        os.write.over(pluginPath / "deder.pkl", rewritten)

        val res = executeDederCommand(pluginPath, "exec", "-m", "hello-plugin", "-t", "publishLocal")
        assert(
          res.exitCode == 0,
          s"publishLocal failed: exit=${res.exitCode}\nstderr=${res.err.text()}\nstdout=${res.out.text()}"
        )
        val publishedDir = localRepoDir / "ba" / "sake" / "deder-hello-plugin_3" / "0.1.0-SNAPSHOT"
        assert(os.exists(publishedDir), s"Published artifact directory not found at $publishedDir")
      }

      // Step 2: run hello task in consumer project that loads the plugin from the local folder
      withTestProject("sample-projects/hello-plugin-consumer") { consumerPath =>
        val localRepoUrl = localRepoDir.toNIO.toUri.toString
        val pkl = os.read(consumerPath / "deder.pkl")
        val rewritten = pkl.replace("__PLUGIN_LOCAL_REPO_URL__", localRepoUrl)
        os.write.over(consumerPath / "deder.pkl", rewritten)

        val res = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "hello")
        assert(
          res.exitCode == 0,
          s"hello task failed: exit=${res.exitCode}\nstderr=${res.err.text()}\nstdout=${res.out.text()}"
        )
        val out = res.out.text()
        assert(out.contains("Hello from hello-plugin!"), s"Expected greeting in output, got: $out")
      }
    } finally {
      os.remove.all(localRepoDir)
    }
  }
}
