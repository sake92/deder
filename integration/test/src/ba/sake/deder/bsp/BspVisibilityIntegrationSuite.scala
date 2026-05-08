package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j.*
import ba.sake.deder.BaseIntegrationSuite

class BspVisibilityIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout: Duration = 10.minutes

  private def targetId(baseUri: String, moduleId: String) = new BuildTargetIdentifier(s"$baseUri#$moduleId")

  private def withBspServer(
      testProjectPath: os.RelPath,
      tweakDeder: String => String = identity
  )(testCode: (os.Path, BspServerAll) => Unit): Unit =
    withTestProject(testProjectPath) { projectPath =>
      val capturingClient = CapturingBuildClient()
      val dederPklPath = projectPath / "deder.pkl"
      os.write.over(dederPklPath, tweakDeder(os.read(dederPklPath)))
      val installResult = executeDederCommand(projectPath, "bsp", "install")
      assertEquals(installResult.exitCode, 0, installResult.err.text())
      val bspProcess = os.proc("java", "-jar", dederClientPath, "bsp").spawn(cwd = projectPath)

      val launcher = new Launcher.Builder[BuildServer]()
        .setInput(bspProcess.stdout)
        .setOutput(bspProcess.stdin)
        .setLocalService(capturingClient)
        .setRemoteInterface(classOf[BspServerAll])
        .create()
      val buildServer = launcher.getRemoteProxy.asInstanceOf[BspServerAll]
      launcher.startListening()

      val initParams = new InitializeBuildParams(
        "test-client",
        "0.0",
        "2.0",
        projectPath.toNIO.toUri.toString,
        new BuildClientCapabilities(List("scala", "java").asJava)
      )

      try {
        buildServer.buildInitialize(initParams).get(1, TimeUnit.MINUTES)
        buildServer.onBuildInitialized()
        testCode(projectPath, buildServer)
      } finally {
        scala.util.Try(buildServer.buildShutdown().get(10, TimeUnit.SECONDS))
        bspProcess.close()
      }
    }

  test("workspaceBuildTargets exposes only latest cross-platform variants by default") {
    withBspServer("sample-projects/cross") { (projectPath, buildServer) =>
      val baseUri = projectPath.toNIO.toUri.toString
      val result = buildServer.workspaceBuildTargets().get(2, TimeUnit.MINUTES)
      val ids = result.getTargets.asScala.map(_.getId.getUri).toSet

      assertEquals(
        ids,
        Set(
          s"$baseUri#common-jvm-3.7.4",
          s"$baseUri#common-jvm-test-3.7.4",
          s"$baseUri#common-js-3.7.4",
          s"$baseUri#common-js-test-3.7.4",
          s"$baseUri#common-native-3.7.4",
          s"$baseUri#common-native-test-3.7.4"
        )
      )
    }
  }

  test("workspaceBuildTargets honors explicit bspVisible and rejects hidden targets") {
    withBspServer(
      "sample-projects/multi",
      _.replace(
        "local const common = (baseModule) {",
        """local const common = (baseModule) {
          |  bspVisible = false""".stripMargin
      )
    ) { (projectPath, buildServer) =>
      val baseUri = projectPath.toNIO.toUri.toString
      val result = buildServer.workspaceBuildTargets().get(2, TimeUnit.MINUTES)
      val targets = result.getTargets.asScala
      val ids = targets.map(_.getId.getUri).toSet

      assert(!ids.contains(s"$baseUri#common"), s"common should be hidden from BSP targets: $ids")

      val frontend = targets.find(_.getId.getUri.endsWith("#frontend")).get
      assertEquals(frontend.getDependencies.asScala.map(_.getUri).toSet, Set.empty)

      val scalacResult = buildServer
        .buildTargetScalacOptions(new ScalacOptionsParams(List(targetId(baseUri, "frontend")).asJava))
        .get(30, TimeUnit.SECONDS)
      val classpath = scalacResult.getItems.asScala.head.getClasspath.asScala
      assert(classpath.exists(_.contains("/common/")), s"frontend should still compile against hidden common: $classpath")

      intercept[ExecutionException] {
        buildServer
          .buildTargetSources(new SourcesParams(List(targetId(baseUri, "common")).asJava))
          .get(30, TimeUnit.SECONDS)
      }
    }
  }
}
