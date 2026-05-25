package ba.sake.deder

import java.util.UUID
import scala.concurrent.duration.*
import scala.util.Properties

trait BaseIntegrationSuite extends munit.FunSuite {

  override def munitTimeout = 10.minute

  val testResourceDir: os.Path = os.pwd / "integration/test/resources"

  val dederClientPath: String = sys.env("DEDER_CLIENT_PATH")
  val dederServerPath: String = sys.env("DEDER_SERVER_PATH")
  val dederTestRunnerPath: String = sys.env("DEDER_TEST_RUNNER_PATH")
  val dederPluginApiVersion: String = sys.env.getOrElse("DEDER_PLUGIN_API_VERSION", "0.1.0-SNAPSHOT")

  private val dederConfigUrlPattern = raw"""https://sake92\.github\.io/deder/config/[^"/]+/([A-Za-z0-9]+\.pkl)""".r

  protected def rewriteConfigUrls(rootDir: os.Path): Unit = {
    val configDir = os.pwd / "config"
    for pklPath <- os.walk(rootDir) if pklPath.last == "deder.pkl" do
      val original = os.read(pklPath)
      val rewritten = dederConfigUrlPattern.replaceAllIn(
        original,
        m => {
          val fileName = m.group(1)
          val relativePath = pklPath.toNIO.getParent.relativize((configDir / fileName).toNIO).toString.replace('\\', '/')
          relativePath
        }
      )
      if rewritten != original then
        os.write.over(pklPath, rewritten)
  }

  protected def stageTestProject(testProjectPath: os.RelPath, tempDir: os.Path): Unit = {
    val sourceDir = testResourceDir / testProjectPath
    if os.exists(tempDir) then os.remove.all(tempDir)
    os.makeDir.all(tempDir)
    for entry <- os.list(sourceDir) if entry.last != ".deder" do
      os.copy(entry, tempDir / entry.last, createFolders = true, replaceExisting = true)
    rewriteConfigUrls(tempDir)
  }

  def withTestProject(
      testProjectPath: os.RelPath,
      serverProperties: Map[String, String] = Map.empty
  )(testCode: os.Path => Unit): Unit = {
    val tempDir = os.pwd / "tmp" / s"${testProjectPath.last}-${System.currentTimeMillis()}-${UUID.randomUUID().toString.take(8)}"
    try {
      stageTestProject(testProjectPath, tempDir)
      val allServerProperties = serverProperties ++ Map(
        "localPath" -> dederServerPath,
        "testRunnerLocalPath" -> dederTestRunnerPath,
        "maxConnectSeconds" -> "300"
      )
      val serverPropertiesContent = allServerProperties.map((k, v) => s"${k}=${v}").mkString("\n") + "\n"
      os.write.over(tempDir / ".deder/server.properties", serverPropertiesContent, createFolders = true)
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      // os.remove.all(tempDir)
    }
  }

  def executeDederCommand(projectPath: os.Path, command: String*): os.CommandResult = {
   val normalizedEnv = sys.env.get("DEDER_TMP_M2_REPO").toSeq.map { repoPath =>
     s"DEDER_TMP_M2_REPO=${os.Path(repoPath, os.pwd)}"
   }
   val cmd = Seq("env") ++ normalizedEnv ++ Seq("java", "-jar", dederClientPath) ++ command
   // println(s"Executing command: ${cmd.mkString(" ")} in $projectPath")
   os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe, check = false)
  }
}
