package ba.sake.deder

import java.util.jar.JarFile
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import org.apache.maven.model.io.xpp3.MavenXpp3Reader

class PublishIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 2.minute

  test("deder should make publishArtifacts") {
    withTestProject("sample-projects/publish") { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "lib1", "-t", "publishArtifacts").out.text()
      locally {
        val publishArtifactsPath = projectPath / ".deder/out/lib1/publishArtifacts/artifacts"
        val pomContent = os.read(publishArtifactsPath / "lib1_3-0.0.1-SNAPSHOT.pom")
        val reader = new MavenXpp3Reader()
        val pom = reader.read(new java.io.StringReader(pomContent))
        assertEquals(pom.getGroupId, "com.example")
        assertEquals(pom.getArtifactId, "lib1_3")
        assertEquals(pom.getVersion, "0.0.1-SNAPSHOT")
        val deps = pom.getDependencies.asScala
        assertEquals(deps.length, 2)
        assert(deps.exists(_.getArtifactId == "scala3-library_3"))
        assert(deps.exists(_.getArtifactId == "os-lib_3"))
      }
    }
  }

  test("publishArtifacts should contain exactly 4 files, no checksums") {
    withTestProject("sample-projects/publish") { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "lib1", "-t", "publishArtifacts").out.text()
      val artifactsDir = projectPath / ".deder/out/lib1/publishArtifacts/artifacts"
      val files = os.list(artifactsDir).map(_.last).sorted
      val expected = Seq(
        "lib1_3-0.0.1-SNAPSHOT.jar",
        "lib1_3-0.0.1-SNAPSHOT-javadoc.jar",
        "lib1_3-0.0.1-SNAPSHOT-sources.jar",
        "lib1_3-0.0.1-SNAPSHOT.pom"
      ).sorted
      assertEquals(files, expected, s"publishArtifacts must contain exactly 4 artifact files, no checksums, got: $files")
    }
  }

  test("deder POM should only contain direct module deps, not transitive") {
    withTestProject("sample-projects/publish") { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "lib3", "-t", "publishArtifacts").out.text()
      val publishArtifactsPath = projectPath / ".deder/out/lib3/publishArtifacts/artifacts"
      val pomContent = os.read(publishArtifactsPath / "lib3_3-0.0.1-SNAPSHOT.pom")
      val reader = new MavenXpp3Reader()
      val pom = reader.read(new java.io.StringReader(pomContent))
      assertEquals(pom.getGroupId, "com.example")
      assertEquals(pom.getArtifactId, "lib3_3")
      val deps = pom.getDependencies.asScala.map(_.getArtifactId).sorted
      println(s"lib3 POM dependencies: $deps")
      assertEquals(deps.length, 2) // only lib2_3 + scala3-library, not lib1_3 which is transitive
      assert(deps.contains("lib2_3"), s"Expected lib2_3 in deps, got: $deps")
      assert(!deps.contains("lib1_3"), s"Expected lib1_3 NOT in deps (transitive), got: $deps")
    }
  }

  test("deder POM should contain all direct module deps when multiple exist") {
    withTestProject("sample-projects/publish") { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "lib4", "-t", "publishArtifacts").out.text()
      val publishArtifactsPath = projectPath / ".deder/out/lib4/publishArtifacts/artifacts"
      val pomContent = os.read(publishArtifactsPath / "lib4_3-0.0.1-SNAPSHOT.pom")
      val reader = new MavenXpp3Reader()
      val pom = reader.read(new java.io.StringReader(pomContent))
      val deps = pom.getDependencies.asScala.map(_.getArtifactId).sorted
      println(s"lib4 POM dependencies: $deps")
      assertEquals(deps.length, 3) // 2 libs + scala3-library
      assert(deps.contains("lib1_3"), s"Expected lib1_3 in deps (direct dep), got: $deps")
      assert(deps.contains("lib2_3"), s"Expected lib2_3 in deps (direct dep), got: $deps")
    }
  }

  test("deder publishLocal should publish exactly 12 files to custom folder (relative path)") {
    withTestProject("sample-projects/publish") { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "lib5", "-t", "publishLocal").out.text()
      val customRepoPath = projectPath / "out/local-repo" / "com/example/lib5_3/0.0.1-SNAPSHOT"
      assert(os.exists(customRepoPath), s"Expected custom local repo at $customRepoPath")
      val files = os.list(customRepoPath).map(_.last).sorted
      assertEquals(files, expectedPublishLocalFiles("lib5_3-0.0.1-SNAPSHOT").sorted,
        s"publishLocal should produce exactly 12 files (4 artifacts × 3 checksums), got: $files")
    }
  }

  test("deder publishLocal should publish exactly 12 files to absolute custom folder") {
    val absoluteRepoDir = os.temp.dir()
    try {
      withTestProject("sample-projects/publish") { projectPath =>
        // Rewrite lib5's deder.pkl to use the absolute path for this test
        val dederPklPath = projectPath / "deder.pkl"
        val content = os.read(dederPklPath)
        os.write.over(dederPklPath, content.replace("\"out/local-repo\"", s""""${absoluteRepoDir}""""))
        executeDederCommand(projectPath, "exec", "-m", "lib5", "-t", "publishLocal").out.text()
        val customRepoPath = absoluteRepoDir / "com/example/lib5_3/0.0.1-SNAPSHOT"
        assert(os.exists(customRepoPath), s"Expected custom local repo at $customRepoPath")
        val files = os.list(customRepoPath).map(_.last).sorted
        assertEquals(files, expectedPublishLocalFiles("lib5_3-0.0.1-SNAPSHOT").sorted,
          s"publishLocal should produce exactly 12 files (4 artifacts × 3 checksums), got: $files")
      }
    } finally {
      os.remove.all(absoluteRepoDir)
    }
  }

  test("deder jar should contain custom manifest entries") {
    withTestProject("sample-projects/publish") { projectPath =>
      executeDederCommand(projectPath, "exec", "-t", "jar", "-m", "lib1").out.text()
      val jarPath = projectPath / ".deder/out/lib1/jar/lib1.jar"
      assert(os.exists(jarPath), s"JAR not found at $jarPath")
      val jarFile = new JarFile(jarPath.toIO)
      try {
        val manifest = jarFile.getManifest
        val mainAttrs = manifest.getMainAttributes
        assertEquals(mainAttrs.getValue("Implementation-Version"), "0.0.1-SNAPSHOT")
        assertEquals(mainAttrs.getValue("Implementation-Title"), "lib1")
        assertEquals(mainAttrs.getValue("Created-By"), "Deder build tool")
        assertEquals(mainAttrs.getValue("Manifest-Version"), "1.0")
      } finally {
        jarFile.close()
      }
    }
  }

  private def expectedPublishLocalFiles(baseName: String): Seq[String] =
    for { art <- Seq(".jar", "-sources.jar", "-javadoc.jar", ".pom")
          suf <- Seq("", ".md5", ".sha1")
    } yield s"$baseName$art$suf"
}
