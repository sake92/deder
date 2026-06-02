package ba.sake.deder

import scala.concurrent.duration.*
import scala.util.Properties

trait BaseIntegrationSuite extends munit.FunSuite {

  override def munitTimeout = 10.minute

  val testResourceDir: os.Path = os.pwd / "integration/test/resources"

  val dederClientPath: String = sys.env("DEDER_CLIENT_PATH")
  val dederServerPath: String = sys.env("DEDER_SERVER_PATH")
  val dederTestRunnerPath: String = sys.env("DEDER_TEST_RUNNER_PATH")
  val dederPluginApiVersion: String = sys.env.getOrElse("DEDER_PLUGIN_API_VERSION", "0.1.0-SNAPSHOT")

  protected def stageTestProject(testProjectPath: os.RelPath, tempDir: os.Path): Unit = {
    val sourceDir = testResourceDir / testProjectPath
    os.makeDir.all(tempDir)
    for entry <- os.list(sourceDir) if entry.last != ".deder" do
      os.copy(entry, tempDir / entry.last, createFolders = true, replaceExisting = true)
    // override the path to the DederProject.pkl, so we dont have to point to the stale one in the github pages...
    val originalLines = os.read.lines(tempDir / "deder.pkl")
    val tweakedLines = Seq(""" amends "../../config/DederProject.pkl" """) ++ originalLines.tail
    os.write.over(tempDir / "deder.pkl", tweakedLines.mkString("\n"), createFolders = true)
  }

  /** Stages a test project and writes server.properties, returning the project path. The
    * caller is responsible for server lifecycle (shutdown in afterAll, etc.). Use this for
    * suites where many tests share one server to avoid per-test JVM startup overhead.
    *
    * @param dirSuffix appended to the temp directory name for uniqueness; defaults to
    *                  currentTimeMillis (sufficient for per-suite use). Per-test callers
    *                  should pass System.nanoTime() for higher-resolution uniqueness.
    */
  protected def stagedServerProject(
      testProjectPath: os.RelPath,
      extraProperties: Map[String, String] = Map.empty,
      dirSuffix: String = System.currentTimeMillis().toString
  ): os.Path = {
    val tempDir = os.pwd / "tmp" / s"${testProjectPath.last}-$dirSuffix"
    stageTestProject(testProjectPath, tempDir)
    val allServerProperties = extraProperties ++ Map(
      "localPath" -> dederServerPath,
      "testRunnerLocalPath" -> dederTestRunnerPath,
      "maxConnectSeconds" -> "300"
    )
    val serverPropertiesContent = allServerProperties.map((k, v) => s"$k=$v").mkString("\n") + "\n"
    os.write.over(tempDir / ".deder/server.properties", serverPropertiesContent, createFolders = true)
    tempDir
  }

  def withTestProject(
      testProjectPath: os.RelPath,
      serverProperties: Map[String, String] = Map.empty
  )(testCode: os.Path => Unit): Unit = {
    val tempDir = stagedServerProject(testProjectPath, serverProperties, dirSuffix = System.nanoTime().toString)
    try {
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      // os.remove.all(tempDir)
    }
  }

  def executeDederCommand(projectPath: os.Path, command: String*): os.CommandResult = {
    // val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    // val cmd = shell ++ Seq(s"$dederClientPath $command")
    val cmd = Seq("java", "-jar", dederClientPath) ++ command
   // println(s"Executing command: ${cmd.mkString(" ")} in $projectPath")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe, check = false)
  }
}
