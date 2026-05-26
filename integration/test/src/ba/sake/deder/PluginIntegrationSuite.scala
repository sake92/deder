package ba.sake.deder

import scala.concurrent.duration.*

class PluginIntegrationSuite extends BaseIntegrationSuite {

  private def stageSiblingProject(parentDir: os.Path, projectName: String): os.Path = {
    val stagedPath = parentDir / projectName
    // Copy project files without rewriting deder.pkl.
    // Don't use stageTestProject because it rewrites the amends to a local path.
    // We need the original HTTP amends to match the HelloPluginModule.pkl import type.
    val sourceDir = testResourceDir / os.RelPath(s"sample-projects/$projectName")
    os.makeDir.all(stagedPath)
    for entry <- os.list(sourceDir) if entry.last != ".deder" do
      os.copy(entry, stagedPath / entry.last, createFolders = true, replaceExisting = true)

    os.write.over(
      stagedPath / ".deder/server.properties",
      s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
      createFolders = true
    )
    stagedPath
  }

  test("deder should publish hello-plugin to ./tmp/m2 and load its typed task in consumer") {
    val tempParent = os.pwd / "tmp" / s"hello-plugin-sync-${System.currentTimeMillis()}"
    val pluginPath = stageSiblingProject(tempParent, "hello-plugin")
    val consumerPath = stageSiblingProject(tempParent, "hello-plugin-consumer")

    try {
      val publishRes = executeDederCommand(pluginPath, "exec", "-m", "hello-plugin", "-t", "publishLocal")
      assert(
        publishRes.exitCode == 0,
        s"publishLocal failed: exit=${publishRes.exitCode}\nstderr=${publishRes.err.text()}\nstdout=${publishRes.out.text()}"
      )

      val localRepoDir = os.Path(sys.env("DEDER_TMP_M2_REPO"))
      val publishedDir = localRepoDir / "ba" / "sake" / "deder-hello-plugin_3" / "it-test-version"
      assert(os.exists(publishedDir), s"Published artifact directory not found at $publishedDir")
      assert(
        os.list(publishedDir).exists(_.last.endsWith(".jar")),
        s"No JAR found in $publishedDir"
      )

      val res = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "hello")
      assert(
        res.exitCode == 0,
        s"hello task failed: exit=${res.exitCode}\nstderr=${res.err.text()}\nstdout=${res.out.text()}"
      )
      val out = res.out.text() + res.err.text()
      assert(out.contains("Hello from typed config!"), s"Expected typed greeting in output, got: $out")
    } finally {
      executeDederCommand(pluginPath, "shutdown")
      executeDederCommand(consumerPath, "shutdown")
    }
  }
}
