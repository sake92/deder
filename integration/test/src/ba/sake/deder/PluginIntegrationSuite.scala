package ba.sake.deder

import scala.concurrent.duration.*

class PluginIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 5.minute

  test("deder should publish hello-plugin to ./tmp/m2 and load its task in consumer") {
    withTestProject("sample-projects/hello-plugin") { pluginPath =>
      // Step 1: inject Maven local repo URL (for deder-plugin-api resolution) and version
      val m2RepoUrl = (os.home / ".m2/repository").toNIO.toUri.toString
      val pkl = os.read(pluginPath / "deder.pkl")
      val rewritten = pkl
        .replace("__M2_REPO_URL__", m2RepoUrl)
        .replace("__PLUGIN_API_VERSION__", dederPluginApiVersion)
      os.write.over(pluginPath / "deder.pkl", rewritten)

      // Step 2: publish hello-plugin to ./tmp/m2 (relative, resolves to <projectPath>/tmp/m2)
      val publishRes = executeDederCommand(pluginPath, "exec", "-m", "hello-plugin", "-t", "publishLocal")
      assert(
        publishRes.exitCode == 0,
        s"publishLocal failed: exit=${publishRes.exitCode}\nstderr=${publishRes.err.text()}\nstdout=${publishRes.out.text()}"
      )

      // Step 3: verify the artifact was published to ./tmp/m2
      val localRepoDir = pluginPath / "tmp/m2"
      val publishedDir = localRepoDir / "ba" / "sake" / "deder-hello-plugin_3" / "0.1.0-SNAPSHOT"
      assert(os.exists(publishedDir), s"Published artifact directory not found at $publishedDir")
      assert(
        os.list(publishedDir).exists(_.last.endsWith(".jar")),
        s"No JAR found in $publishedDir"
      )

      // Step 4: run hello task in consumer project that resolves the plugin from ./tmp/m2
      withTestProject("sample-projects/hello-plugin-consumer") { consumerPath =>
        val localRepoUrl = localRepoDir.toNIO.toUri.toString
        val consumerPkl = os.read(consumerPath / "deder.pkl")
        val consumerRewritten = consumerPkl.replace("__PLUGIN_LOCAL_REPO_URL__", localRepoUrl)
        os.write.over(consumerPath / "deder.pkl", consumerRewritten)

        val res = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "hello")
        assert(
          res.exitCode == 0,
          s"hello task failed: exit=${res.exitCode}\nstderr=${res.err.text()}\nstdout=${res.out.text()}"
        )
        val out = res.out.text()
        assert(out.contains("Hello from hello-plugin!"), s"Expected greeting in output, got: $out")
      }
    }
  }
}
