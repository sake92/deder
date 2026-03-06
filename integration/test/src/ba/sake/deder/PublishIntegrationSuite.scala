package ba.sake.deder

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Properties
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

class PublishIntegrationSuite extends munit.FunSuite {

  // first compile can take a while
  override def munitTimeout = 1.minute
  
  private val testResourceDir = os.Path(System.getenv("MILL_TEST_RESOURCE_DIR"))
  private val dederClientPath = System.getenv("DEDER_CLIENT_PATH")
  private val dederServerPath = System.getenv("DEDER_SERVER_PATH")

  // default command is compile
  // and the logs go to stderr!
  test("deder should make publishArtifacts") {
    withTestProject(testResourceDir / "sample-projects/publish") { projectPath =>
      executeDederCommand(projectPath, "exec -t publishArtifacts").out.text()
      locally {
        val publishArtifactsPath = projectPath / ".deder/out/mylibrary/publishArtifacts"
        val pomContent = os.read(publishArtifactsPath / "mylibrary_3-0.0.1-SNAPSHOT.pom")
        val reader = new MavenXpp3Reader()
        val pom = reader.read(new java.io.StringReader(pomContent))
        assertEquals(pom.getGroupId, "com.example")
        assertEquals(pom.getArtifactId, "mylibrary_3")
        assertEquals(pom.getVersion, "0.0.1-SNAPSHOT")
        val deps = pom.getDependencies.asScala
        assertEquals(deps.length, 2)
        assert(deps.exists(_.getArtifactId == "scala3-library_3"))
        assert(deps.exists(_.getArtifactId == "os-lib_3"))
      }
      locally {
        val publishArtifactsPath = projectPath / ".deder/out/myapp/publishArtifacts"
        val pomContent = os.read(publishArtifactsPath / "myapp_3-0.0.1-SNAPSHOT.pom")
        val reader = new MavenXpp3Reader()
        val pom = reader.read(new java.io.StringReader(pomContent))
        assertEquals(pom.getGroupId, "com.example")
        assertEquals(pom.getArtifactId, "myapp_3")
        assertEquals(pom.getVersion, "0.0.1-SNAPSHOT")
        val deps = pom.getDependencies.asScala
        assertEquals(deps.length, 2)
        assert(deps.exists(_.getArtifactId == "scala3-library_3"))
        assert(deps.exists(_.getArtifactId == "mylibrary_3"))
      }
    }
  }

  private def withTestProject(testProjectPath: os.Path)(testCode: os.Path => Unit): Unit = {
    // mill test runs in sandbox folder, so it is safe to create temp folders here
    val tempDir = os.pwd / testProjectPath.last / s"temp-${System.currentTimeMillis()}"
    println(s"Copying test project to temp directory: $tempDir")
    try {
      os.copy(testProjectPath, tempDir, createFolders = true, replaceExisting = true)
      os.write.over(tempDir / ".deder/server.properties", s"localPath=$dederServerPath\n", createFolders = true)
      testCode(tempDir)
    } finally {
      executeDederCommand(tempDir, "shutdown")
      // os.remove.all(tempDir)
    }
  }

  private def executeDederCommand(projectPath: os.Path, command: String): os.CommandResult = {
    val shell = if Properties.isWin then Seq("cmd.exe", "/C") else Seq("bash", "-c")
    val cmd = shell ++ Seq(s"$dederClientPath $command")
    os.proc(cmd).call(cwd = projectPath, stderr = os.Pipe)
  }
}
