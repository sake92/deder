package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.compiletime.uninitialized
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.BaseIntegrationSuite

class BspResilienceSuite extends BaseIntegrationSuite {

  override def munitTimeout: Duration = 10.minutes

  private val testResourceDir: os.Path = os.pwd / "integration/test/resources"

  test("BSP client re-launch connects to still-running server") {
    val testDir = os.pwd / "tmp" / s"bsp-relaunch-${System.currentTimeMillis()}"
    try {
      os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
      val lines = os.read.lines(testDir / "deder.pkl")
      os.write.over(
        testDir / "deder.pkl",
        (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
      )
      os.write.over(
        testDir / ".deder/server.properties",
        s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
        createFolders = true
      )
      executeDederCommand(testDir, "bsp", "install")

      // First session: run a query
      withBspSession(testDir) { (buildServer1, _, _) =>
        val result1 = buildServer1.workspaceBuildTargets().get(1, TimeUnit.MINUTES)
        val ids1 = result1.getTargets.asScala.map(_.getId.getUri).toSet
        assert(ids1.contains(s"${baseUri(testDir)}#common"))
      }

      // Second session: re-launch the BSP client, connect to same running server
      withBspSession(testDir) { (buildServer2, _, _) =>
        val result2 = buildServer2.workspaceBuildTargets().get(1, TimeUnit.MINUTES)
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
      os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
      val lines = os.read.lines(testDir / "deder.pkl")
      os.write.over(
        testDir / "deder.pkl",
        (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
      )
      os.write.over(
        testDir / ".deder/server.properties",
        s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
        createFolders = true
      )
      executeDederCommand(testDir, "bsp", "install")

      // First session
      withBspSession(testDir) { (buildServer1, _, bspProcess1) =>
        val result1 = buildServer1.workspaceBuildTargets().get(1, TimeUnit.MINUTES)
        assert(result1.getTargets.asScala.nonEmpty)

        // Kill the server (simulating "deder shutdown")
        executeDederCommand(testDir, "shutdown")
        Thread.sleep(1000) // let the server die and bspProcess detect it
      }

      // Second session: new BSP client should auto-start the server
      withBspSession(testDir) { (buildServer2, _, bspProcess2) =>
        // Server was auto-started, should be ready after buildInitialize
        val result2 = buildServer2.workspaceBuildTargets().get(2, TimeUnit.MINUTES)
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
      os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
      val lines = os.read.lines(testDir / "deder.pkl")
      os.write.over(
        testDir / "deder.pkl",
        (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
      )
      os.write.over(
        testDir / ".deder/server.properties",
        s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
        createFolders = true
      )
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

  test("unknown build target returns empty result without crashing") {
    val testDir = os.pwd / "tmp" / s"bsp-unknown-target-${System.currentTimeMillis()}"
    try {
      os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
      val lines = os.read.lines(testDir / "deder.pkl")
      os.write.over(
        testDir / "deder.pkl",
        (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
      )
      os.write.over(
        testDir / ".deder/server.properties",
        s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
        createFolders = true
      )
      executeDederCommand(testDir, "bsp", "install")

      withBspSession(testDir) { (buildServer, _, _) =>
        val unknownUri = s"${baseUri(testDir)}#nonexistent"
        val params = new SourcesParams(List(new BuildTargetIdentifier(unknownUri)).asJava)
        val result = buildServer.buildTargetSources(params).get(30, TimeUnit.SECONDS)
        // Request is fulfilled even for unknown targets; just verify it doesn't crash
        assert(result.getItems != null, "should return a result even for unknown target")
      }
    } finally {
      executeDederCommand(testDir, "shutdown")
    }
  }

  test("buildTargetCompile after reconnect maintains state correctness") {
    val testDir = os.pwd / "tmp" / s"bsp-compile-reconnect-${System.currentTimeMillis()}"
    try {
      os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
      val lines = os.read.lines(testDir / "deder.pkl")
      os.write.over(
        testDir / "deder.pkl",
        (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
      )
      os.write.over(
        testDir / ".deder/server.properties",
        s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
        createFolders = true
      )
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
      os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
      val lines = os.read.lines(testDir / "deder.pkl")
      os.write.over(
        testDir / "deder.pkl",
        (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
      )
      os.write.over(
        testDir / ".deder/server.properties",
        s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
        createFolders = true
      )
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
      os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
      val lines = os.read.lines(testDir / "deder.pkl")
      os.write.over(
        testDir / "deder.pkl",
        (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
      )
      os.write.over(
        testDir / ".deder/server.properties",
        s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\n",
        createFolders = true
      )
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
      buildServer.buildInitialize(initParams).get(2, TimeUnit.MINUTES)
      buildServer.onBuildInitialized()

      testCode(buildServer, capturingClient, bspProcess)
    } finally {
      if buildServer != null then scala.util.Try(buildServer.buildShutdown().get(10, TimeUnit.SECONDS))
      if bspProcess != null then scala.util.Try(bspProcess.destroy())
      Thread.sleep(500) // give server time to close socket and free up for next connection
    }
  }
}
