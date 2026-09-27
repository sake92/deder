package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.compiletime.uninitialized
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import ba.sake.deder.BaseIntegrationSuite

class BspResilienceSuite extends BaseIntegrationSuite {

  private val bspRequestTimeoutMinutes = 5L

  private def stageMultiProject(testDir: os.Path): Unit =
    stageTestProject(os.RelPath("sample-projects/multi"), testDir)

  private def writeServerProperties(testDir: os.Path): Unit =
    os.write.over(
      testDir / ".deder/server.properties",
      s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\nmaxConnectSeconds=300\n",
      createFolders = true
    )

  test("BSP client re-launch connects to still-running server") {
    val testDir = os.pwd / "tmp" / s"bsp-relaunch-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      // First session: run a query
      withBspSession(testDir) { (buildServer1, _, _) =>
        val result1 = buildServer1.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        val ids1 = result1.getTargets.asScala.map(_.getId.getUri).toSet
        assert(ids1.contains(s"${baseUri(testDir)}#common"))
      }

      // Second session: re-launch the BSP client, connect to same running server
      withBspSession(testDir) { (buildServer2, _, _) =>
        val result2 = buildServer2.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        val ids2 = result2.getTargets.asScala.map(_.getId.getUri).toSet
        assert(ids2.contains(s"${baseUri(testDir)}#common"), "second session should also see common module")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("server restart: new BSP client auto-starts server") {
    val testDir = os.pwd / "tmp" / s"bsp-restart-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      // First session
      withBspSession(testDir) { (buildServer1, _, bspProcess1) =>
        val result1 = buildServer1.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        assert(result1.getTargets.asScala.nonEmpty)

        // Kill the server (simulating "deder shutdown")
        executeDederCommand(testDir, "shutdown")
        Thread.sleep(1000) // let the server die and bspProcess detect it
      }

      // Second session: new BSP client should auto-start the server
      withBspSession(testDir) { (buildServer2, _, bspProcess2) =>
        // Server was auto-started, should be ready after buildInitialize
        val result2 = buildServer2.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        val ids2 = result2.getTargets.asScala.map(_.getId.getUri).toSet
        assert(ids2.contains(s"${baseUri(testDir)}#common"), "server should have restarted and provided targets")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("concurrent buildTargetSources requests complete successfully") {
    val testDir = os.pwd / "tmp" / s"bsp-concurrent-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, _, bspProcess) =>
        val modules = List("common", "frontend", "backend", "uber")
        val futures = modules.map { module =>
          val params = new SourcesParams(List(targetId(testDir, module)).asJava)
          buildServer.buildTargetSources(params)
        }
        val results = futures.map(_.get(30, TimeUnit.SECONDS))
        results.foreach { result =>
          assert(result.getItems.asScala.nonEmpty, "each module should have sources")
        }
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("unknown build target request is rejected with BSP error but server stays alive") {
    val testDir = os.pwd / "tmp" / s"bsp-unknown-target-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, _, _) =>
        // Request for an unknown target must fail with a BSP error
        val unknownUri = s"${baseUri(testDir)}#nonexistent"
        val params = new SourcesParams(List(new BuildTargetIdentifier(unknownUri)).asJava)
        val ex = intercept[ExecutionException] {
          buildServer.buildTargetSources(params).get(30, TimeUnit.SECONDS)
        }
        val errorDetail = ex.getCause match {
          case ree: ResponseErrorException =>
            Option(ree.getResponseError.getData).map(_.toString).getOrElse(ree.getMessage)
          case other => other.getMessage
        }
        assert(
          errorDetail.contains("Unknown BSP target") && errorDetail.contains("nonexistent"),
          s"expected rejection for unknown target 'nonexistent', got error detail: $errorDetail"
        )

        // Server must remain usable after the rejected request
        val targetsResult = buildServer.workspaceBuildTargets().get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
        assert(
          targetsResult.getTargets.asScala.nonEmpty,
          "server should still be reachable and return targets after rejecting an unknown target"
        )
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("buildTargetCompile after reconnect maintains state correctness") {
    val testDir = os.pwd / "tmp" / s"bsp-compile-reconnect-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      // First session: compile common
      withBspSession(testDir) { (buildServer1, capturingClient1, _) =>
        val params = new CompileParams(List(targetId(testDir, "common")).asJava)
        params.setOriginId("compile-session1")
        val result = buildServer1.buildTargetCompile(params).get(2, TimeUnit.MINUTES)
        assertEquals(result.getStatusCode, StatusCode.OK)
        val taskFinish = capturingClient1.awaitTaskFinish()
        assert(taskFinish.isDefined, "should have compile task finish")
      }

      // Second session: compile again, verify fresh state
      withBspSession(testDir) { (buildServer2, capturingClient2, _) =>
        val params = new CompileParams(List(targetId(testDir, "common")).asJava)
        params.setOriginId("compile-session2")
        val result = buildServer2.buildTargetCompile(params).get(2, TimeUnit.MINUTES)
        assertEquals(result.getStatusCode, StatusCode.OK)
        val taskFinish = capturingClient2.awaitTaskFinish()
        assert(taskFinish.isDefined, "second session should also produce compile task finish")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("compile notifications arrive in order: start -> progress -> finish") {
    val testDir = os.pwd / "tmp" / s"bsp-notify-order-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        val params = new CompileParams(List(targetId(testDir, "common")).asJava)
        params.setOriginId("test-notify-order")
        capturingClient.clear()
        val result = buildServer.buildTargetCompile(params).get(2, TimeUnit.MINUTES)
        assertEquals(result.getStatusCode, StatusCode.OK)

        // Verify notification order: start before progress, progress before finish
        val taskStart = capturingClient.awaitTaskStart()
        assert(taskStart.isDefined, "should have compile start notification")
        val startTime = taskStart.get.getEventTime

        val taskProgress = capturingClient.awaitTaskProgress()
        // Progress may or may not be sent, but if present should be after start
        if taskProgress.isDefined then
          assert(taskProgress.get.getEventTime >= startTime, "progress should be after start")

        val taskFinish = capturingClient.awaitTaskFinish()
        assert(taskFinish.isDefined, "should have compile finish notification")
        val finishTime = taskFinish.get.getEventTime
        assert(finishTime >= startTime, "finish should be after start")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("failing compilation emits error diagnostics and finish with error status") {
    val testDir = os.pwd / "tmp" / s"bsp-compile-fail-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        // Write a compilation error
        val badFile = testDir / "common/src/bad.scala"
        os.write(badFile, "package common\nval notValid: Int = \"wrong type\"")

        capturingClient.clear()
        val params = new CompileParams(List(targetId(testDir, "common")).asJava)
        params.setOriginId("test-compile-fail")
        val result = buildServer.buildTargetCompile(params).get(2, TimeUnit.MINUTES)
        assertEquals(result.getStatusCode, StatusCode.ERROR, "compile should fail")

        // Verify we got start notification
        val taskStart = capturingClient.awaitTaskStart()
        assert(taskStart.isDefined, "should have compile start notification")

        // Verify we got error diagnostics
        val diag = capturingClient.awaitDiagnostic(predicate = _.getDiagnostics.asScala.nonEmpty)
        assert(diag.isDefined, "should have error diagnostics")
        val errors = diag.get.getDiagnostics.asScala
        assert(errors.exists(_.getSeverity == DiagnosticSeverity.ERROR), "should have error severity")

        // Verify we got finish notification with error
        val taskFinish = capturingClient.awaitTaskFinish()
        assert(taskFinish.isDefined, "should have compile finish notification")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("rapid successive buildTargetCompile requests both complete without hanging") {
    val testDir = os.pwd / "tmp" / s"bsp-rapid-compile-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        capturingClient.clear()

        val params1 = new CompileParams(List(targetId(testDir, "common")).asJava)
        params1.setOriginId("test-rapid-1")
        val params2 = new CompileParams(List(targetId(testDir, "common")).asJava)
        params2.setOriginId("test-rapid-2")

        val future1 = buildServer.buildTargetCompile(params1)
        val future2 = buildServer.buildTargetCompile(params2)

        val result1 = future1.get(2, TimeUnit.MINUTES)
        val result2 = future2.get(2, TimeUnit.MINUTES)

        assertEquals(result1.getStatusCode, StatusCode.OK)
        assertEquals(result2.getStatusCode, StatusCode.OK)

        // With debouncing, only one compilation runs for identical targets.
        // Both futures complete with the same result.
        val starts = capturingClient.awaitTaskStarts(1)
        assertEquals(starts.size, 1, "should have at least 1 task start notification")

        val finishes = capturingClient.awaitTaskFinishes(1)
        assertEquals(finishes.size, 1, "should have at least 1 task finish notification")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("rapid buildTargetCompile requests each get correct originId on result") {
    val testDir = os.pwd / "tmp" / s"bsp-originid-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        capturingClient.clear()

        val params1 = new CompileParams(List(targetId(testDir, "common")).asJava)
        params1.setOriginId("burst-request-1")
        val params2 = new CompileParams(List(targetId(testDir, "common")).asJava)
        params2.setOriginId("burst-request-2")
        val params3 = new CompileParams(List(targetId(testDir, "common")).asJava)
        params3.setOriginId("burst-request-3")

        // Fire all three requests as close together as possible
        val future1 = buildServer.buildTargetCompile(params1)
        val future2 = buildServer.buildTargetCompile(params2)
        val future3 = buildServer.buildTargetCompile(params3)

        val result1 = future1.get(2, TimeUnit.MINUTES)
        val result2 = future2.get(2, TimeUnit.MINUTES)
        val result3 = future3.get(2, TimeUnit.MINUTES)

        // All results must have their OWN originId
        assertEquals(result1.getOriginId, "burst-request-1")
        assertEquals(result2.getOriginId, "burst-request-2")
        assertEquals(result3.getOriginId, "burst-request-3")

        // All must complete successfully
        assertEquals(result1.getStatusCode, StatusCode.OK)
        assertEquals(result2.getStatusCode, StatusCode.OK)
        assertEquals(result3.getStatusCode, StatusCode.OK)

        // Only one compilation should run (one start + one finish notification)
        val starts = capturingClient.awaitTaskStarts(1)
        assertEquals(starts.size, 1)

        val finishes = capturingClient.awaitTaskFinishes(1)
        assertEquals(finishes.size, 1)
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("buildTargetCompile with overlapping target sets completes correctly") {
    val testDir = os.pwd / "tmp" / s"bsp-overlap-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        capturingClient.clear()

        // First request compiles common + frontend (frontend depends on common)
        val paramsBoth = new CompileParams(
          List(targetId(testDir, "common"), targetId(testDir, "frontend")).asJava
        )
        paramsBoth.setOriginId("overlap-both")

        // Second request compiles just common (subset of first)
        val paramsCommon = new CompileParams(List(targetId(testDir, "common")).asJava)
        paramsCommon.setOriginId("overlap-common")

        val futureBoth = buildServer.buildTargetCompile(paramsBoth)
        // Small delay to ensure the first future starts executing before second is submitted
        Thread.sleep(100)
        val futureCommon = buildServer.buildTargetCompile(paramsCommon)

        val resultBoth = futureBoth.get(3, TimeUnit.MINUTES)
        val resultCommon = futureCommon.get(3, TimeUnit.MINUTES)

        assertEquals(resultBoth.getOriginId, "overlap-both")
        assertEquals(resultCommon.getOriginId, "overlap-common")
        assertEquals(resultBoth.getStatusCode, StatusCode.OK)
        assertEquals(resultCommon.getStatusCode, StatusCode.OK)
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("simulated AI agent burst: many compiles, all originIds correct, server stays alive") {
    val testDir = os.pwd / "tmp" / s"bsp-aiburst-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        capturingClient.clear()

        val modules = Seq("common", "frontend", "backend", "uber")
        val requestCount = 10

        // Fire many rapid compile requests for various module combinations
        val futures = (1 to requestCount).map { i =>
          val targets = if i % 3 == 0 then modules.take(2)   // [common, frontend]
                        else if i % 3 == 1 then modules.take(1) // [common]
                        else modules.take(3)                    // [common, frontend, backend]
          val params = new CompileParams(targets.map(t => targetId(testDir, t)).asJava)
          params.setOriginId(s"aiburst-$i")
          buildServer.buildTargetCompile(params)
        }

        // All must complete without timeout
        val results = futures.map(_.get(5, TimeUnit.MINUTES))

        // Each result must have its own originId
        results.zipWithIndex.foreach { case (result, idx) =>
          assertEquals(result.getOriginId, s"aiburst-${idx + 1}",
            s"request ${idx + 1} originId mismatch")
          assertEquals(
            Seq(StatusCode.OK, StatusCode.CANCELLED).contains(result.getStatusCode), true,
            s"request ${idx + 1} status should be OK or CANCELLED, got ${result.getStatusCode}"
          )
        }

        // Server must still be usable after burst
        val targetsResult = buildServer.workspaceBuildTargets().get(1, TimeUnit.MINUTES)
        assert(targetsResult.getTargets.asScala.nonEmpty, "server should still serve targets after burst")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("compile finish notification is sent even when compilation fails with errors") {
    val testDir = os.pwd / "tmp" / s"bsp-finish-guarantee-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        val badFile = testDir / "common/src/bad.scala"
        os.write(badFile, "package common\nval notValid: Int = \"wrong type\"")
        capturingClient.clear()

        val params = new CompileParams(List(targetId(testDir, "common")).asJava)
        params.setOriginId("test-compile-error-guarantee")
        val result = buildServer.buildTargetCompile(params).get(2, TimeUnit.MINUTES)

        val taskStart = capturingClient.awaitTaskStart()
        assert(taskStart.isDefined, "should have compile start notification even on failure")

        val taskFinish = capturingClient.awaitTaskFinish()
        assert(taskFinish.isDefined, "should have compile finish notification even on failure")
        assertEquals(taskFinish.get.getStatus, StatusCode.ERROR)

        assertEquals(result.getStatusCode, StatusCode.ERROR)
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("manual kill during in-flight compile returns CANCELLED instead of hanging") {
    val testDir = os.pwd / "tmp" / s"bsp-kill-inflight-${System.currentTimeMillis()}"
    try {
      stageMultiProject(testDir)
      writeServerProperties(testDir)
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, capturingClient, _) =>
        capturingClient.clear()

        val params = new CompileParams(List(targetId(testDir, "common")).asJava)
        params.setOriginId("test-kill-inflight")
        val future = buildServer.buildTargetCompile(params)

        val started = capturingClient.awaitTaskStart(timeout = 30.seconds)
        assert(started.isDefined, "compile should start before manual kill")

        val pid = os.read(testDir / ".deder/server.lock").trim
        os.proc("kill", "-TERM", pid).call(cwd = testDir)

        val result = future.get(60, TimeUnit.SECONDS)
        assertEquals(result.getStatusCode, StatusCode.CANCELLED)
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  private def baseUri(testDir: os.Path) = testDir.toNIO.toUri.toString

  private def targetId(testDir: os.Path, module: String) =
    new BuildTargetIdentifier(s"${baseUri(testDir)}#$module")

  /** @param testCode
    *   receives (buildServer, capturingClient, bspProcess) tuple: the connected BuildServer, a CapturingBuildClient to
    *   observe server events, and the BSP server proxy process (in case the test wants to kill it directly).
    *
    * The BSP server proxy is destroyed and the server is shutdown after the test code finishes, so each test gets a
    * clean slate.
    */
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
        baseUri(testDir),
        new BuildClientCapabilities(List("scala", "java").asJava)
      )
      buildServer.buildInitialize(initParams).get(bspRequestTimeoutMinutes, TimeUnit.MINUTES)
      buildServer.onBuildInitialized()

      testCode(buildServer, capturingClient, bspProcess)
    } finally {
      if buildServer != null then scala.util.Try(buildServer.buildShutdown().get(10, TimeUnit.SECONDS))
      if bspProcess != null then scala.util.Try(bspProcess.destroy())
      Thread.sleep(500) // give server time to close socket and free up for next connection
    }
  }
}
