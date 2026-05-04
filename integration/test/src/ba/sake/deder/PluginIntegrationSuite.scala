package ba.sake.deder

import scala.concurrent.duration.*

class PluginIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 5.minute

  test("deder should publish hello-plugin to ./tmp/m2 and load its task in consumer") {
    withTestProject("sample-projects/hello-plugin") { pluginPath =>
      // Step 1: publish hello-plugin to the shared tmp-m2 repo
      // (DEDER_TMP_M2_REPO env var is set by run-it-tests.sh, read via Pkl's read("env:..."))
      val publishRes = executeDederCommand(pluginPath, "exec", "-m", "hello-plugin", "-t", "publishLocal")
      assert(
        publishRes.exitCode == 0,
        s"publishLocal failed: exit=${publishRes.exitCode}\nstderr=${publishRes.err.text()}\nstdout=${publishRes.out.text()}"
      )

      // Step 2: verify the artifact was published to the shared repo
      val localRepoDir = os.Path(sys.env("DEDER_TMP_M2_REPO"))
      val publishedDir = localRepoDir / "ba" / "sake" / "deder-hello-plugin_3" / "it-test-version"
      assert(os.exists(publishedDir), s"Published artifact directory not found at $publishedDir")
      assert(
        os.list(publishedDir).exists(_.last.endsWith(".jar")),
        s"No JAR found in $publishedDir"
      )

      // Step 3: run hello task in consumer project
      // (consumer resolves the plugin from DEDER_TMP_M2_REPO via Pkl's read("env:..."))
      withTestProject("sample-projects/hello-plugin-consumer") { consumerPath =>
        val res = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "hello")
        assert(
          res.exitCode == 0,
          s"hello task failed: exit=${res.exitCode}\nstderr=${res.err.text()}\nstdout=${res.out.text()}"
        )
        val out = res.out.text() + res.err.text()
        assert(out.contains("Hello from hello-plugin!"), s"Expected greeting in output, got: $out")
      }
    }
  }
}
