package ba.sake.deder

import scala.concurrent.duration.*
import scala.util.Properties

trait BaseIntegrationSuite extends munit.FunSuite {

  override def munitTimeout = 2.minute

  val testResourceDir: os.Path = os.pwd / "integration/test/resources"

  val dederClientPath: String = sys.env("DEDER_CLIENT_PATH")
  val dederServerPath: String = sys.env("DEDER_SERVER_PATH")
  val dederTestRunnerPath: String = sys.env("DEDER_TEST_RUNNER_PATH")

  def withTestProject(
      testProjectPath: os.RelPath,
      serverProperties: Map[String, String] = Map.empty
  )(testCode: os.Path => Unit): Unit = {
    val tempDir = os.pwd / "tmp" / s"${testProjectPath.last}-${System.currentTimeMillis()}"
    try {
      os.copy(testResourceDir / testProjectPath, tempDir, createFolders = true, replaceExisting = true)
      // override the path to the DederProject.pkl, so we dont have to point to the stale one in the github pages...
      val originalLines = os.read.lines(tempDir / "deder.pkl")
      val tweakedLines = Seq(""" amends "../../config/DederProject.pkl" """) ++ originalLines.tail
      os.write.over(tempDir / "deder.pkl", tweakedLines.mkString("\n"), createFolders = true)
      val allServerProperties = serverProperties ++ Map(
        "localPath" -> dederServerPath,
        "testRunnerLocalPath" -> dederTestRunnerPath
      )
      val serverPropertiesContent = allServerProperties.map((k, v) => s"${k}=${v}").mkString("\n") + "\n"
      os.write.over(tempDir / ".deder/server.properties", serverPropertiesContent, createFolders = true)
      System.setProperty("DEDER_PROJECT_ROOT_DIR", tempDir.toString)
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
    println(s"Executing command: ${cmd.mkString(" ")} in $projectPath")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe, check = false)
  }
}
