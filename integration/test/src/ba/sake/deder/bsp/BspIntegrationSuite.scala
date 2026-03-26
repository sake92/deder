package ba.sake.deder.bsp

import java.util.concurrent.*
import scala.compiletime.uninitialized
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import ba.sake.deder.BaseIntegrationSuite

// Combined interface so the lsp4j proxy exposes both core and Scala-specific methods
trait BspServerAll extends BuildServer, ScalaBuildServer

class BspIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout: Duration = 5.minutes

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
    os.copy(testResourceDir / "sample-projects/multi", testDir, createFolders = true)
    val lines = os.read.lines(testDir / "deder.pkl")
    os.write.over(
      testDir / "deder.pkl",
      (Seq("""amends "../../config/DederProject.pkl"""") ++ lines.tail).mkString("\n")
    )
    os.write.over(testDir / ".deder/server.properties", s"localPath=$dederServerPath\n", createFolders = true)
    executeDederCommand(testDir, "bsp install")
    bspProcess = os.proc(dederClientPath, "bsp").spawn(cwd = testDir)

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
    buildServer.buildInitialize(initParams).get(30, TimeUnit.SECONDS)
    buildServer.onBuildInitialized()
  }

  override def afterAll(): Unit = {
    if buildServer != null then scala.util.Try(buildServer.buildShutdown().get(10, TimeUnit.SECONDS))
    if bspProcess != null then bspProcess.close()
    executeDederCommand(testDir, "shutdown")
  }

  test("workspaceBuildTargets returns all modules with correct structure") {
    val result = buildServer.workspaceBuildTargets().get(30, TimeUnit.SECONDS)
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
    assertEquals(uberTest.getTags.asScala.toList, List(BuildTargetTag.APPLICATION, BuildTargetTag.TEST))
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
    assert(item.getClassDirectory.nonEmpty, "classDirectory should be set")
  }

  test("buildTargetScalacOptions for module with deps includes dependency classes on classpath") {
    val result = buildServer
      .buildTargetScalacOptions(new ScalacOptionsParams(List(targetId("frontend")).asJava))
      .get(30, TimeUnit.SECONDS)
    val classpath = result.getItems.asScala.head.getClasspath.asScala
    assert(classpath.exists(_.contains("scala3-library")), s"scala3-library missing: $classpath")
    assert(classpath.exists(_.contains("/common/")), s"common classes not on frontend classpath: $classpath")
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
}

class CapturingBuildClient extends BuildClient {
  val taskStarts = new ConcurrentLinkedQueue[TaskStartParams]()
  val taskFinishes = new ConcurrentLinkedQueue[TaskFinishParams]()
  val diagnostics = new ConcurrentLinkedQueue[PublishDiagnosticsParams]()

  def clear(): Unit =
    taskStarts.clear()
    taskFinishes.clear()
    diagnostics.clear()

  def awaitTaskFinish(
      timeout: FiniteDuration = 15.seconds,
      predicate: TaskFinishParams => Boolean = _ => true
  ): Option[TaskFinishParams] =
    pollUntil(timeout)(taskFinishes.asScala.find(predicate))

  def awaitDiagnostic(
      timeout: FiniteDuration = 15.seconds,
      predicate: PublishDiagnosticsParams => Boolean = _ => true
  ): Option[PublishDiagnosticsParams] =
    pollUntil(timeout)(diagnostics.asScala.find(predicate))

  private def pollUntil[T](timeout: FiniteDuration)(check: => Option[T]): Option[T] =
    val deadline = System.currentTimeMillis() + timeout.toMillis
    while System.currentTimeMillis() < deadline do
      check match
        case s @ Some(_) => return s
        case None        => Thread.sleep(50)
    None

  override def onBuildTaskStart(p: TaskStartParams): Unit = taskStarts.add(p)
  override def onBuildTaskFinish(p: TaskFinishParams): Unit = taskFinishes.add(p)
  override def onBuildPublishDiagnostics(p: PublishDiagnosticsParams): Unit = diagnostics.add(p)
  override def onBuildShowMessage(p: ShowMessageParams): Unit = ()
  override def onBuildLogMessage(p: LogMessageParams): Unit = ()
  override def onBuildTargetDidChange(p: DidChangeBuildTarget): Unit = ()
  override def onBuildTaskProgress(p: TaskProgressParams): Unit = ()
  override def onRunPrintStdout(p: PrintParams): Unit = ()
  override def onRunPrintStderr(p: PrintParams): Unit = ()
}
