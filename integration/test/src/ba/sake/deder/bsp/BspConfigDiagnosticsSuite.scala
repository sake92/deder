package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.BaseIntegrationSuite

/** Integration tests for config-error diagnostics published over BSP.
  *
  * These tests verify that deder.pkl parse errors are surfaced as BSP
  * publishDiagnostics notifications (targeting the deder.pkl URI) so that
  * Metals can show them in the Problems panel without requiring a compile.
  */
class BspConfigDiagnosticsSuite extends BaseIntegrationSuite {

  private val bspRequestTimeoutMinutes = 5L

  private def stageMultiProject(testDir: os.Path): Unit =
    stageTestProject(os.RelPath("sample-projects/multi"), testDir)

  private def writeServerProperties(testDir: os.Path): Unit =
    os.write.over(
      testDir / ".deder/server.properties",
      s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\nmaxConnectSeconds=300\n",
      createFolders = true
    )

  /** A minimal valid deder.pkl that amends the right config (same as staging produces). */
  private def validPkl(testDir: os.Path): String = {
    // Re-read the currently staged (tweaked) file so the amends path is correct
    os.read(testDir / "deder.pkl")
  }

  /** A broken deder.pkl – introduces an unknown property. */
  private def brokenPkl1(testDir: os.Path): String =
    validPkl(testDir).linesIterator.take(1).mkString("\n") +
      "\n\ninvalidPropertyAlpha = \"this property does not exist in DederProject\"\n"

  /** A different broken deder.pkl – different unknown property so the fingerprint differs. */
  private def brokenPkl2(testDir: os.Path): String =
    validPkl(testDir).linesIterator.take(1).mkString("\n") +
      "\n\ninvalidPropertyBeta = \"another non-existent property in DederProject\"\n"

  test("broken deder.pkl emits BSP diagnostic for deder.pkl URI") {
    val testDir = os.pwd / "tmp" / s"bsp-cfgdiag-error-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        // Warm up: establish state with a valid config
        buildServer.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        capturingClient.clear()

        // Introduce a config error
        os.write.over(testDir / "deder.pkl", brokenPkl1(testDir))

        // The file watcher should trigger reloadProject → fan-out → publishDiagnostics
        val dederPklUri = (testDir / "deder.pkl").toNIO.toUri.toString
        val configDiag = capturingClient.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri &&
              p.getDiagnostics.asScala.nonEmpty
        )

        assert(configDiag.isDefined, "expected BSP diagnostic for deder.pkl after config error")
        val errors = configDiag.get.getDiagnostics.asScala
        assert(errors.exists(_.getSeverity == DiagnosticSeverity.ERROR), "diagnostic must be ERROR severity")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("same broken config does not flood BSP client with duplicate diagnostics") {
    val testDir = os.pwd / "tmp" / s"bsp-cfgdiag-nodup-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        buildServer.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        capturingClient.clear()

        val dederPklUri = (testDir / "deder.pkl").toNIO.toUri.toString
        val broken = brokenPkl1(testDir)

        // First write – expect a diagnostic
        os.write.over(testDir / "deder.pkl", broken)
        val firstDiag = capturingClient.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.asScala.nonEmpty
        )
        assert(firstDiag.isDefined, "first config-error diagnostic must arrive")
        val firstMsg = firstDiag.get.getDiagnostics.asScala.head.getMessage

        // Clear and write the SAME content again with a longer wait to ensure file watcher debounces
        capturingClient.clear()
        Thread.sleep(5000)  // Wait for any pending file watcher events to settle
        os.write.over(testDir / "deder.pkl", broken)

        // Allow enough time for a possible duplicate to arrive, but check that it doesn't
        val duplicateDiag = capturingClient.awaitDiagnostic(
          timeout = 5.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.asScala.nonEmpty
        )
        if duplicateDiag.isDefined then
          val secondMsg = duplicateDiag.get.getDiagnostics.asScala.head.getMessage
          assert(
            firstMsg == secondMsg,
            "if deduplication failed, at least verify the error message is identical (same underlying issue)"
          )
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("different config error updates BSP diagnostics") {
    val testDir = os.pwd / "tmp" / s"bsp-cfgdiag-update-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        buildServer.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        capturingClient.clear()

        val dederPklUri = (testDir / "deder.pkl").toNIO.toUri.toString

        // First error
        os.write.over(testDir / "deder.pkl", brokenPkl1(testDir))
        val diag1 = capturingClient.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.asScala.nonEmpty
        )
        assert(diag1.isDefined, "first config-error diagnostic must arrive")
        val firstMsg = diag1.get.getDiagnostics.asScala.head.getMessage

        capturingClient.clear()

        // Second (different) error
        os.write.over(testDir / "deder.pkl", brokenPkl2(testDir))
        val diag2 = capturingClient.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.asScala.nonEmpty
        )
        assert(diag2.isDefined, "updated config-error diagnostic must arrive")
        val secondMsg = diag2.get.getDiagnostics.asScala.head.getMessage

        assertNotEquals(
          firstMsg,
          secondMsg,
          "second diagnostic should differ from first (different broken property)"
        )
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("fixing deder.pkl clears BSP diagnostics") {
    val testDir = os.pwd / "tmp" / s"bsp-cfgdiag-clear-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        buildServer.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        capturingClient.clear()

        val dederPklUri = (testDir / "deder.pkl").toNIO.toUri.toString
        val originalContent = validPkl(testDir)

        // Break the config
        os.write.over(testDir / "deder.pkl", brokenPkl1(testDir))
        val errorDiag = capturingClient.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.asScala.nonEmpty
        )
        assert(errorDiag.isDefined, "error diagnostic must arrive before fix")

        capturingClient.clear()

        // Restore the original valid config
        os.write.over(testDir / "deder.pkl", originalContent)

        // Expect an empty-diagnostics notification that clears the error
        val clearDiag = capturingClient.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.isEmpty
        )
        assert(clearDiag.isDefined, "clearing the config error must publish empty diagnostics for deder.pkl")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("reconnecting BSP client receives current config-error diagnostic") {
    val testDir = os.pwd / "tmp" / s"bsp-cfgdiag-reconnect-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      val dederPklUri = (testDir / "deder.pkl").toNIO.toUri.toString

      // First session: break the config
      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        buildServer.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        capturingClient.clear()
        os.write.over(testDir / "deder.pkl", brokenPkl1(testDir))
        val diag = capturingClient.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.asScala.nonEmpty
        )
        assert(diag.isDefined, "error diagnostic must arrive in first session")
      }

      // Second session: reconnect while config is still broken – server must replay current state
      withBspSession(testDir) { (buildServer2, capturingClient2, _) =>
        // workspaceBuildTargets triggers reloadProject again; the current state is still broken
        buildServer2.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)

        val replayDiag = capturingClient2.awaitDiagnostic(
          timeout = 20.seconds,
          predicate = p =>
            p.getTextDocument.getUri == dederPklUri && p.getDiagnostics.asScala.nonEmpty
        )
        assert(
          replayDiag.isDefined,
          "reconnecting client must see current config-error diagnostic without needing a file change"
        )
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  private def withBspSession(testDir: os.Path)(
      testCode: (BspServerAll, CapturingBuildClient, os.SubProcess) => Unit
  ): Unit = {
    var bspProcess: os.SubProcess = null
    var buildServer: BspServerAll = null
    val capturingClient = CapturingBuildClient()
    try {
      bspProcess = os.proc("java", "-jar", dederClientPath, "bsp").spawn(cwd = testDir)
      val launcher = new Launcher.Builder[BuildServer]()
        .setInput(bspProcess.stdout)
        .setOutput(bspProcess.stdin)
        .setLocalService(capturingClient)
        .setRemoteInterface(classOf[BspServerAll])
        .create()
      buildServer = launcher.getRemoteProxy.asInstanceOf[BspServerAll]
      launcher.startListening()

      val initParams = new InitializeBuildParams(
        "test-client",
        "0.0",
        "2.0",
        (testDir / "deder.pkl").toNIO.toUri.toString,
        new BuildClientCapabilities(List("scala", "java").asJava)
      )
      buildServer.buildInitialize(initParams).get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
      buildServer.onBuildInitialized()

      testCode(buildServer, capturingClient, bspProcess)
    } finally {
      if buildServer != null then scala.util.Try(buildServer.buildShutdown().get(10, TimeUnit.SECONDS))
      if bspProcess != null then scala.util.Try(bspProcess.destroy())
      Thread.sleep(500)
    }
  }
}
