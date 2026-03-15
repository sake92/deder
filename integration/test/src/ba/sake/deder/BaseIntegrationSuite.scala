package ba.sake.deder

import scala.util.Properties

trait BaseIntegrationSuite extends munit.FunSuite {

  val testResourceDir: os.Path = os.pwd / "integration/test/resources"

  val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  def withTestProject(testProjectPath: os.RelPath)(testCode: os.Path => Unit): Unit = {
    val tempDir = os.pwd / "tmp" / s"${testProjectPath.last}-${System.currentTimeMillis()}"
    try {
      os.copy(testResourceDir / testProjectPath, tempDir, createFolders = true, replaceExisting = true)
      // override the path to the DederProject.pkl, so we dont have to point to the stale one in the github pages...
      val originalLines = os.read.lines(tempDir / "deder.pkl")
      val tweakedLines = Seq(""" amends "../../config/DederProject.pkl" """) ++ originalLines.tail
      os.write.over(tempDir / "deder.pkl", tweakedLines.mkString("\n"), createFolders = true)
      os.write.over(tempDir / ".deder/server.properties", s"localPath=$dederServerPath\n", createFolders = true)
      System.setProperty("DEDER_PROJECT_ROOT_DIR", tempDir.toString)
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      // os.remove.all(tempDir)
    }
  }

  def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe, check = false)
  }
}
