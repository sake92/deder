package ba.sake.deder

import scala.util.Properties

trait BaseIntegrationSuite extends munit.FunSuite {
  
  val testResourceDir: os.Path = os.pwd / "integration/test/resources"
  
  val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  val dederServerPath = System.getenv("DEDER_SERVER_PATH")
  
  def withTestProject(testProjectPath: os.RelPath)(testCode: os.Path => Unit): Unit = {
    val tempDir = os.temp.dir(prefix = testProjectPath.last)
    try {
      os.copy(testResourceDir /testProjectPath, tempDir, createFolders = true, replaceExisting = true)
      os.write.over(tempDir / ".deder/server.properties", s"localPath=$dederServerPath\n", createFolders = true)
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      // os.remove.all(tempDir)
    }
  }

   def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
  }
}
