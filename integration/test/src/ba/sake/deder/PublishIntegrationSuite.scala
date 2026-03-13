package ba.sake.deder

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

class PublishIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 2.minute

  // default command is compile
  // and the logs go to stderr!
  test("deder should make publishArtifacts") {
    withTestProject("sample-projects/publish") { projectPath =>
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
}
