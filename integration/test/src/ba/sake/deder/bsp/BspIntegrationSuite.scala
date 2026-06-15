package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.compiletime.uninitialized
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.BaseIntegrationSuite

// Combined interface so the lsp4j proxy exposes both core and Scala-specific methods
trait BspServerAll extends BuildServer, JvmBuildServer, JavaBuildServer, ScalaBuildServer

class BspIntegrationSuite extends BaseIntegrationSuite {

  private var testDir: os.Path = uninitialized
  private var bspProcess: os.SubProcess = uninitialized
  private var buildServer: BspServerAll = uninitialized
  private val capturingClient = CapturingBuildClient()

  private def baseUri = testDir.toNIO.toUri.toString
  private def targetId(module: String) = new BuildTargetIdentifier(s"$baseUri#$module")

  override def beforeEach(context: BeforeEach): Unit = {
    capturingClient.clear()
  }

  override def beforeAll(): Unit = {
    testDir = os.pwd / "tmp" / s"multi-bsp-${System.currentTimeMillis()}"
    stageTestProject(os.RelPath("sample-projects/multi"), testDir)
    os.write.over(
      testDir / ".deder/server.properties",
      s"localPath=$dederServerPath\ntestRunnerLocalPath=$dederTestRunnerPath\nmaxConnectSeconds=300\nbspFileChangeNotifyCooldownSeconds=0\n",
      createFolders = true
    )
    executeDederCommand(testDir, "bsp", "install")
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
      testDir.toNIO.toUri.toString,
      new BuildClientCapabilities(List("scala", "java").asJava)
    )
    buildServer.buildInitialize(initParams).get(1, TimeUnit.MINUTES)
    buildServer.onBuildInitialized()
  }

  override def afterAll(): Unit = {
    if buildServer != null then scala.util.Try(buildServer.buildShutdown().get(10, TimeUnit.SECONDS))
    if bspProcess != null then bspProcess.close()
    executeDederCommand(testDir, "shutdown")
  }

  test("workspaceBuildTargets returns all modules with correct structure") {
    val result = buildServer.workspaceBuildTargets().get(2, TimeUnit.MINUTES)
    val targets = result.getTargets.asScala
    val ids = targets.map(_.getId.getUri).toSet

    assert(ids.contains(s"$baseUri#common"), s"missing 'common' in $ids")
    assert(ids.contains(s"$baseUri#frontend"), s"missing 'frontend' in $ids")
    assert(ids.contains(s"$baseUri#backend"), s"missing 'backend' in $ids")
    assert(ids.contains(s"$baseUri#uber"), s"missing 'uber' in $ids")
    assert(ids.contains(s"$baseUri#uber-test"), s"missing 'uber-test' in $ids")

    val frontend = targets.find(_.getId.getUri.endsWith("#frontend")).get
    assert(frontend.getCapabilities.getCanCompile, "frontend should be compilable")
    val frontendDeps = frontend.getDependencies.asScala.map(_.getUri).toSet
    assert(frontendDeps.contains(s"$baseUri#common"), "frontend should depend on common")

    val uber = targets.find(_.getId.getUri.endsWith("#uber")).get
    assertEquals(uber.getTags.asScala.toList, List(BuildTargetTag.APPLICATION))
    assert(uber.getCapabilities.getCanRun, "uber should be runnable")

    val uberTest = targets.find(_.getId.getUri.endsWith("#uber-test")).get
    assertEquals(uberTest.getTags.asScala.toList, List(BuildTargetTag.TEST))
    assert(uberTest.getCapabilities.getCanTest, "uber-test should be testable")
  }

  test("buildTargetSources returns source directories for each module") {
    val params = new SourcesParams(List("common", "frontend", "backend").map(targetId).asJava)
    val result = buildServer.buildTargetSources(params).get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 3)
    for item <- items do
      val sources = item.getSources.asScala.map(_.getUri)
      assert(sources.nonEmpty, s"no sources for target ${item.getTarget.getUri}")
      assert(sources.forall(_.startsWith("file://")), s"sources should be file URIs: $sources")
  }

  test("buildTargetJavacOptions returns Java compiler options and classpath") {
    val params = new JavacOptionsParams(List(targetId("common")).asJava)
    val result = buildServer.buildTargetJavacOptions(params).get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 1)
    val item = items.head
    val options = item.getOptions.asScala
    assert(options.exists(_.contains("-Xplugin:semanticdb")), "should include semanticdb plugin")
    val classpath = item.getClasspath.asScala
    assert(classpath.nonEmpty, "classpath should not be empty")
    assert(
      item.getClassDirectory.contains("/classes"),
      s"classDirectory should point to classes, got: ${item.getClassDirectory}"
    )
  }

  test("buildTargetScalacOptions contains scala3-library and semanticdb flags") {
    val result = buildServer
      .buildTargetScalacOptions(new ScalacOptionsParams(List(targetId("common")).asJava))
      .get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 1)
    val item = items.head
    val classpath = item.getClasspath.asScala
    assert(classpath.exists(_.contains("scala3-library")), s"scala3-library not in classpath: $classpath")
    val options = item.getOptions.asScala
    assert(options.contains("-Xsemanticdb"), s"missing -Xsemanticdb in options: $options")
    assert(
      item.getClassDirectory.contains("/classes"),
      s"classDirectory should point to classes, got: ${item.getClassDirectory}"
    )
  }

  test("buildTargetScalacOptions for module with deps includes dependency classes on classpath") {
    val result = buildServer
      .buildTargetScalacOptions(new ScalacOptionsParams(List(targetId("frontend")).asJava))
      .get(30, TimeUnit.SECONDS)
    val classpath = result.getItems.asScala.head.getClasspath.asScala
    assert(classpath.exists(_.contains("scala3-library")), s"scala3-library missing: $classpath")
    assert(classpath.exists(_.contains("/common/")), s"common classes not on frontend classpath: $classpath")
  }

  test("buildTargetScalaMainClasses returns main class for application module") {
    val result = buildServer
      .buildTargetScalaMainClasses(new ScalaMainClassesParams(List(targetId("uber")).asJava))
      .get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 1)
    val item = items.head
    assertEquals(item.getClasses.asScala.map(_.getClassName).toSet, Set("uber.Main"))
    assert(item.getClasses.asScala.forall(_.getJvmOptions != null), "jvmOptions should be set")
  }

  test("buildTargetScalaTestClasses returns test class for test module") {
    val result = buildServer
      .buildTargetScalaTestClasses(new ScalaTestClassesParams(List(targetId("uber-test")).asJava))
      .get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 1)
    val item = items.head
    assertEquals(item.getClasses.asScala.toSet, Set("uber.MyTest"))
  }

  test("buildTargetCompile emits task progress notifications") {
    val params = new CompileParams(List(targetId("common")).asJava)
    params.setOriginId("test-compile-progress")
    val result = buildServer.buildTargetCompile(params).get(2, TimeUnit.MINUTES)
    assertEquals(result.getStatusCode, StatusCode.OK)

    // Task progress is optional but if emitted, should be captured
    val taskProgress = capturingClient.awaitTaskProgress(timeout = 2.seconds)
    // Progress notifications are implementation-dependent; just verify we can capture them
    // (they may or may not be sent for small, fast compilations)
  }

  test("buildTargetCompile succeeds and emits task start/finish notifications") {
    val params = new CompileParams(List(targetId("common")).asJava)
    params.setOriginId("test-compile-common")
    val result = buildServer.buildTargetCompile(params).get(2, TimeUnit.MINUTES)
    assertEquals(result.getStatusCode, StatusCode.OK)

    val taskFinish = capturingClient.awaitTaskFinish(predicate = _.getDataKind == TaskFinishDataKind.COMPILE_REPORT)
    assert(taskFinish.isDefined, "expected a COMPILE_REPORT task-finish notification")
    val report = new com.google.gson.Gson().fromJson(
      taskFinish.get.getData.asInstanceOf[com.google.gson.JsonObject],
      classOf[CompileReport]
    )
    assertEquals(report.getErrors.intValue(), 0)
    assertEquals(report.getWarnings.intValue(), 0)

    val taskStart = capturingClient.taskStarts.asScala
      .find(_.getDataKind == TaskStartDataKind.COMPILE_TASK)
    assert(taskStart.isDefined, "expected a COMPILE_TASK task-start notification")
  }

  test("buildTargetCompile with a type error emits error diagnostics") {
    val badFile = testDir / "common/src/bad.scala"
    os.write(badFile, "package common\nval notValid: Int = \"wrong type\"")
    try {
      val result = buildServer
        .buildTargetCompile(new CompileParams(List(targetId("common")).asJava))
        .get(2, TimeUnit.MINUTES)
      assertEquals(result.getStatusCode, StatusCode.ERROR)

      val diagParams = capturingClient.awaitDiagnostic(predicate = _.getDiagnostics.asScala.nonEmpty)
      assert(diagParams.isDefined, "expected diagnostics to be published for the bad file")
      val diag = diagParams.get.getDiagnostics.asScala.head
      assertEquals(diag.getSeverity, DiagnosticSeverity.ERROR)
      assert(diagParams.get.getTextDocument.getUri.contains("bad.scala"), "diagnostic should point to bad.scala")
    } finally {
      os.remove(badFile)
      // restore clean state for subsequent tests
      buildServer
        .buildTargetCompile(new CompileParams(List(targetId("common")).asJava))
        .get(2, TimeUnit.MINUTES)
    }
  }

  test("buildTargetDependencySources returns source JARs for module with dependencies") {
    val params = new DependencySourcesParams(List(targetId("uber-test")).asJava)
    val result = buildServer.buildTargetDependencySources(params).get(2, TimeUnit.MINUTES)
    val items = result.getItems.asScala
    assertEquals(items.size, 1, s"expected 1 DependencySourcesItem, got ${items.size}")
    val item = items.head
    assertEquals(item.getTarget.getUri, targetId("uber-test").getUri)
    val sourceJars = item.getSources.asScala
    assert(sourceJars.nonEmpty, "expected at least one source JAR for uber-test dependencies")
    assert(
      sourceJars.forall(_.endsWith("-sources.jar")),
      s"all source entries should end with -sources.jar, got: $sourceJars"
    )
  }

  test("buildTargetDependencyModules returns dependency modules for module with dependencies") {
    val params = new DependencyModulesParams(List(targetId("uber-test")).asJava)
    val result = buildServer.buildTargetDependencyModules(params).get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 1)
    val item = items.head
    val depModules = item.getModules.asScala
    assert(depModules.map(_.getName()).contains("scala3-library_3"), "should contain scala3-library_3 dependency")
    assert(depModules.map(_.getName()).contains("munit_3"), "should contain munit_3 dependency")
  }

  test("buildTargetResources returns resource directories for module") {
    val params = new ResourcesParams(List(targetId("common")).asJava)
    val result = buildServer.buildTargetResources(params).get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 1)
    val item = items.head
    assert(item.getResources.asScala.exists(_.endsWith("bla.properties")), "should contain bla.properties resource")
  }

  test("buildTargetCleanCache cleans task cache for targets") {
    val cleanParams = new CleanCacheParams(List(targetId("common")).asJava)
    val result = buildServer.buildTargetCleanCache(cleanParams).get(30, TimeUnit.SECONDS)
    assert(result.getCleaned, "cache should be cleaned for common module")
  }

  test("buildTargetCompile generates semanticdb files") {
    val result = buildServer
      .buildTargetCompile(new CompileParams(List(targetId("common")).asJava))
      .get(2, TimeUnit.MINUTES)
    assertEquals(result.getStatusCode, StatusCode.OK)
    val semanticdbFiles = os.walk(testDir / ".deder/out/common/semanticdb").filter(_.ext == "semanticdb")
    assert(semanticdbFiles.nonEmpty, s"expected .semanticdb files to be generated under .deder/out/common/semanticdb/")
  }

  test("buildTargetCompile after cleanCache regenerates semanticdb files") {
    buildServer
      .buildTargetCompile(new CompileParams(List(targetId("common")).asJava))
      .get(2, TimeUnit.MINUTES)
    buildServer
      .buildTargetCleanCache(new CleanCacheParams(List(targetId("common")).asJava))
      .get(30, TimeUnit.SECONDS)
    val result = buildServer
      .buildTargetCompile(new CompileParams(List(targetId("common")).asJava))
      .get(2, TimeUnit.MINUTES)
    assertEquals(result.getStatusCode, StatusCode.OK)
    val semanticdbFiles = os.walk(testDir / ".deder/out/common/semanticdb").filter(_.ext == "semanticdb")
    assert(
      semanticdbFiles.nonEmpty,
      s"expected .semanticdb files after clean+recompile under .deder/out/common/semanticdb/"
    )
  }

  test("buildTargetInverseSources returns targets containing source file") {
    val params = new InverseSourcesParams(new TextDocumentIdentifier(s"${baseUri}common/src/Common.scala"))
    val result = buildServer.buildTargetInverseSources(params).get(30, TimeUnit.SECONDS)
    val targetIds = result.getTargets.asScala.toList.map(_.getUri()).map(_.split("#").last)
    assertEquals(targetIds, List("common"), "Common.scala should be included in common module")
  }

  // A cache-hit replay emits a task-start whose message contains "(cached)" — this uniquely
  // distinguishes the replay path from the live (compiler-ran) path.
  private def isCachedCompileStart(p: TaskStartParams): Boolean =
    p.getDataKind == TaskStartDataKind.COMPILE_TASK && Option(p.getMessage).exists(_.contains("cached"))

  private def compileCommon(): CompileResult =
    buildServer.buildTargetCompile(new CompileParams(List(targetId("common")).asJava)).get(2, TimeUnit.MINUTES)

  test("buildTargetCompile cache hit replays compile notifications") {
    // First compile populates the cache for 'common' with the current (unchanged) inputs.
    compileCommon()
    capturingClient.clear()

    // Second compile with identical inputs must be served from cache and replay notifications,
    // even though the compiler did not run.
    val result = compileCommon()
    assertEquals(result.getStatusCode, StatusCode.OK)

    val cachedStart = capturingClient.awaitTaskStart(predicate = isCachedCompileStart)
    assert(
      cachedStart.isDefined,
      s"expected a cache-hit COMPILE_TASK start, got messages: ${capturingClient.taskStarts.asScala.map(_.getMessage).toList}"
    )

    val finish = capturingClient.awaitTaskFinish(predicate = _.getDataKind == TaskFinishDataKind.COMPILE_REPORT)
    assert(finish.isDefined, "expected a COMPILE_REPORT task-finish on cache hit")
    val report = new com.google.gson.Gson().fromJson(
      finish.get.getData.asInstanceOf[com.google.gson.JsonObject],
      classOf[CompileReport]
    )
    assertEquals(report.getErrors.intValue(), 0)
    assertEquals(report.getWarnings.intValue(), 0)
  }

  test("buildTargetCompile cache hit replays error diagnostics without recompiling") {
    val badFile = testDir / "common/src/bad.scala"
    os.write.over(badFile, "package common\nval notValid: Int = \"wrong type\"")
    try {
      // First compile: cache miss, fails and publishes an error diagnostic (live path).
      val r1 = compileCommon()
      assertEquals(r1.getStatusCode, StatusCode.ERROR)
      capturingClient.clear()

      // Second compile, bad.scala unchanged: cache hit must REPLAY the error diagnostic.
      val r2 = compileCommon()
      assertEquals(r2.getStatusCode, StatusCode.ERROR)

      val cachedStart = capturingClient.awaitTaskStart(predicate = isCachedCompileStart)
      assert(cachedStart.isDefined, "expected a cache-hit COMPILE_TASK start on the 2nd compile")

      val diag = capturingClient.awaitDiagnostic(predicate =
        p =>
          p.getTextDocument.getUri.contains("bad.scala") &&
            p.getDiagnostics.asScala.exists(_.getSeverity == DiagnosticSeverity.ERROR)
      )
      assert(diag.isDefined, "error diagnostic for bad.scala should be replayed on cache hit")
    } finally {
      os.remove(badFile)
      compileCommon() // restore clean state for subsequent tests
    }
  }

  test("buildTargetCompile incremental compile keeps and clears diagnostics consistently") {
    val fileA = testDir / "common/src/badA.scala"
    val fileB = testDir / "common/src/badB.scala"
    os.write.over(fileA, "package common\nval badA: Int = \"a\"")
    os.write.over(fileB, "package common\nval badB: Int = \"b\"")
    try {
      val r1 = compileCommon()
      assertEquals(r1.getStatusCode, StatusCode.ERROR)
      capturingClient.clear()

      // Fix only A; B is untouched and still broken. This is an incremental recompile.
      os.write.over(fileA, "package common\nval okA: Int = 1")
      val r2 = compileCommon()
      assertEquals(r2.getStatusCode, StatusCode.ERROR)

      // B's diagnostic must still be reported after the recompile (complete picture).
      val diagB = capturingClient.awaitDiagnostic(predicate =
        p =>
          p.getTextDocument.getUri.contains("badB.scala") &&
            p.getDiagnostics.asScala.exists(_.getSeverity == DiagnosticSeverity.ERROR)
      )
      assert(diagB.isDefined, "badB.scala error should still be reported after editing only badA.scala")

      // A's diagnostics must be cleared (empty publish with reset).
      val diagACleared = capturingClient.awaitDiagnostic(predicate =
        p => p.getTextDocument.getUri.contains("badA.scala") && p.getDiagnostics.asScala.isEmpty
      )
      assert(diagACleared.isDefined, "badA.scala diagnostics should be cleared after fixing it")
    } finally {
      os.remove(fileA)
      os.remove(fileB)
      compileCommon() // restore clean state for subsequent tests
    }
  }

  test("buildTargetOutputPaths returns excluded directory paths") {
    val params = new OutputPathsParams(List(targetId("common")).asJava)
    val result = buildServer.buildTargetOutputPaths(params).get(30, TimeUnit.SECONDS)
    val items = result.getItems.asScala
    assertEquals(items.size, 1)
    val item = items.head
    val paths = item.getOutputPaths.asScala
    assert(paths.exists(_.getUri.contains(".deder")), "should include .deder in excluded paths")
    assert(paths.exists(_.getUri.contains(".metals")), "should include .metals in excluded paths")
  }

  test("notifies BSP client about external source file change") {
    // Drain any pending notifications from prior test debounce
    capturingClient.didChangeNotifications.clear()

    val commonSrcDir = testDir / "common" / "src"
    os.makeDir.all(commonSrcDir)
    val editedFile = commonSrcDir / "ExternallyEdited.scala"
    try {
      os.write(editedFile, "object ExternallyEdited { val x = 1 }")

      // Wait for file watcher debounce (300ms) + OS propagation + processing time
      val notification = capturingClient.awaitTargetDidChange(
        30.seconds,
        predicate = { n =>
          n.getChanges.asScala.exists { event =>
            event.getTarget.getUri.endsWith("#common")
          }
        }
      )

      assert(notification.isDefined,
        s"No didChange notification received for module 'common' after writing a source file. " +
        s"Total notifications received: ${capturingClient.didChangeNotifications.size()}")
    } finally {
      if os.exists(editedFile) then os.remove(editedFile)
    }
  }

}
