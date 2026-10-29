package ba.sake.deder

import java.util.jar.JarFile
import scala.concurrent.duration.*
import scala.util.Using

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
      s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\nmaxConnectSeconds=300\n",
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

  test("plugins are reloaded on every deder.pkl change") {
    val tempParent = os.pwd / "tmp" / s"full-reload-${System.currentTimeMillis()}"
    val pluginPath = stageSiblingProject(tempParent, "hello-plugin")
    val consumerPath = stageSiblingProject(tempParent, "hello-plugin-consumer")

    try {
      // Publish the plugin
      val publishRes = executeDederCommand(pluginPath, "exec", "-m", "hello-plugin", "-t", "publishLocal")
      assert(
        publishRes.exitCode == 0,
        s"publishLocal failed: exit=${publishRes.exitCode}\nstderr=${publishRes.err.text()}\nstdout=${publishRes.out.text()}"
      )

      // First run: plugin should load fresh and work
      val res1 = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "hello")
      assert(
        res1.exitCode == 0,
        s"first hello task failed: exit=${res1.exitCode}\nstderr=${res1.err.text()}\nstdout=${res1.out.text()}"
      )
      val out1 = res1.out.text() + res1.err.text()
      assert(out1.contains("Hello from typed config!"), s"Expected typed greeting, got: $out1")

      // Touch deder.pkl to trigger server reload (no actual content change)
      val dederPkl = consumerPath / "deder.pkl"
      os.write.append(dederPkl, "\n")
      Thread.sleep(2000) // give file watcher time to detect and reload

      // Second run: plugin should still work after full reload
      val res2 = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "hello")
      assert(
        res2.exitCode == 0,
        s"second hello task after reload failed: exit=${res2.exitCode}\nstderr=${res2.err.text()}\nstdout=${res2.out.text()}"
      )
      val out2 = res2.out.text() + res2.err.text()
      assert(out2.contains("Hello from typed config!"), s"Expected typed greeting after reload, got: $out2")
    } finally {
      executeDederCommand(pluginPath, "shutdown")
      executeDederCommand(consumerPath, "shutdown")
    }
  }

  test("plugin-generated resources are packaged and invalidate JARs and assemblies") {
    val tempParent = os.pwd / "tmp" / s"generated-resource-packaging-${System.currentTimeMillis()}"
    val pluginPath = stageSiblingProject(tempParent, "hello-plugin")
    val consumerPath = stageSiblingProject(tempParent, "hello-plugin-consumer")
    val expectedInitial = "hello from generated resource\n"
    val expectedUpdated = "updated generated resource\n"

    try {
      val publishRes = executeDederCommand(pluginPath, "exec", "-m", "hello-plugin", "-t", "publishLocal")
      assert(
        publishRes.exitCode == 0,
        s"publishLocal failed: exit=${publishRes.exitCode}\nstderr=${publishRes.err.text()}\nstdout=${publishRes.out.text()}"
      )

      def packagedResource(jar: os.Path): String =
        Using.resource(new JarFile(jar.toIO)) { jarFile =>
          val entry = jarFile.getJarEntry("plugin-generated-resource.txt")
          assert(entry != null, s"generated resource missing from $jar")
          Using.resource(jarFile.getInputStream(entry)) { stream =>
            String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
          }
        }

      def runPackaged(jar: os.Path): String =
        os.proc("java", "-jar", jar.toString).call(cwd = consumerPath, stderr = os.Pipe).out.text()

      val jarRes = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "jar")
      assert(jarRes.exitCode == 0, s"jar failed: ${jarRes.err.text()}\n${jarRes.out.text()}")
      val jarPath = consumerPath / ".deder/out/app/jar/app.jar"
      assertEquals(packagedResource(jarPath), expectedInitial)

      val assemblyRes = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "assembly")
      assert(assemblyRes.exitCode == 0, s"assembly failed: ${assemblyRes.err.text()}\n${assemblyRes.out.text()}")
      val assemblyPath = consumerPath / ".deder/out/app/assembly/out.jar"
      assertEquals(runPackaged(assemblyPath), expectedInitial)

      os.write.over(consumerPath / "generated-resource.txt", expectedUpdated)

      val updatedJarRes = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "jar")
      assert(updatedJarRes.exitCode == 0, s"updated jar failed: ${updatedJarRes.err.text()}\n${updatedJarRes.out.text()}")
      assertEquals(packagedResource(jarPath), expectedUpdated)

      val updatedAssemblyRes = executeDederCommand(consumerPath, "exec", "-m", "app", "-t", "assembly")
      assert(
        updatedAssemblyRes.exitCode == 0,
        s"updated assembly failed: ${updatedAssemblyRes.err.text()}\n${updatedAssemblyRes.out.text()}"
      )
      assertEquals(runPackaged(assemblyPath), expectedUpdated)
    } finally {
      executeDederCommand(pluginPath, "shutdown")
      executeDederCommand(consumerPath, "shutdown")
    }
  }
}
