package ba.sake.deder.importing.sbt

import munit.FunSuite

class SbtImporterSuite extends FunSuite {

  private val noopLogger = ba.sake.deder.ServerNotificationsLogger(_ => ())

  private def projectExportJson(id: String, base: String, scalaVersion: String): String =
    s"""{
       |  "id": "$id",
       |  "base": "$base",
       |  "name": "$id",
       |  "javacOptions": [],
       |  "scalaVersion": "$scalaVersion",
       |  "scalacOptions": [],
       |  "interProjectDependencies": [],
       |  "externalDependencies": [],
       |  "repositories": [],
       |  "sourceDirs": ["src/main/scala"],
       |  "testSourceDirs": ["src/test/scala"],
       |  "resourceDirs": [],
       |  "testResourceDirs": [],
       |  "plugins": [],
       |  "organization": "com.example",
       |  "artifactName": "$id",
       |  "artifactType": "jar",
       |  "artifactClassifier": null,
       |  "version": "0.1.0",
       |  "description": "",
       |  "homepage": null,
       |  "developers": [],
       |  "licenses": [],
       |  "scmInfo": null
       |}""".stripMargin

  private def withExportedBuildFiles(files: Seq[(String, String)])(body: => Unit): Unit = {
    val exportDir = os.pwd / "target/build-export"
    val backupDir = os.pwd / "target/build-export-sbt-importer-suite-backup"
    if os.exists(backupDir) then os.remove.all(backupDir)
    if os.exists(exportDir) then os.move(exportDir, backupDir)
    os.makeDir.all(exportDir)
    files.foreach { case (fileName, content) =>
      os.write.over(exportDir / fileName, content)
    }
    try body
    finally {
      if os.exists(exportDir) then os.remove.all(exportDir)
      if os.exists(backupDir) then os.move(backupDir, exportDir)
    }
  }

  private def withExportBuildPluginPath(body: => Unit): Unit = {
    val projectDir = os.pwd / "project"
    os.makeDir.all(projectDir)
    val pluginPath = projectDir / "exportBuildStructure.sbt"
    val backupPath = projectDir / "exportBuildStructure-sbt-importer-suite-backup.sbt"
    if os.exists(backupPath) then os.remove(backupPath)
    if os.exists(pluginPath) then os.move(pluginPath, backupPath)
    try body
    finally {
      if os.exists(pluginPath) then os.remove(pluginPath)
      if os.exists(backupPath) then os.move(backupPath, pluginPath)
    }
  }

  private def readAndParseExportedModules(importer: SbtImporter): IndexedSeq[ExportedProjectExportFile] = {
    val method = classOf[SbtImporter].getDeclaredMethods.find(_.getName == "readAndParseExportedModules").get
    method.setAccessible(true)
    method.invoke(importer).asInstanceOf[IndexedSeq[ExportedProjectExportFile]]
  }

  private def dumpSbtBuild(importer: SbtImporter): Unit = {
    val method = classOf[SbtImporter].getDeclaredMethods.find(_.getName == "dumpSbtBuild").get
    method.setAccessible(true)
    method.invoke(importer)
  }

  test("parses concrete exported file metadata from filename") {
    val metadata = ExportedProjectExportFile.parseFileName("myproject_3.3.4.json")

    assertEquals(metadata.projectId, "myproject")
    assertEquals(metadata.scalaVersion, "3.3.4")
  }

  test("parses 0.0.5 exported project file without crossScalaVersions in payload") {
    val exportedFile = ExportedProjectExportFile.parse(
      os.pwd / "target/build-export/myproject_3.3.4.json",
      """{
        |  "id": "myproject",
        |  "base": "/workspace/myproject",
        |  "name": "myproject",
        |  "javacOptions": ["-Xlint:unchecked"],
        |  "scalaVersion": "3.3.4",
        |  "scalacOptions": ["-deprecation"],
        |  "interProjectDependencies": [],
        |  "externalDependencies": [],
        |  "repositories": ["https://repo1.maven.org/maven2"],
        |  "sourceDirs": ["src/main/scala"],
        |  "testSourceDirs": ["src/test/scala"],
        |  "resourceDirs": ["src/main/resources"],
        |  "testResourceDirs": ["src/test/resources"],
        |  "plugins": [],
        |  "organization": "com.example",
        |  "artifactName": "myproject",
        |  "artifactType": "jar",
        |  "artifactClassifier": null,
        |  "version": "0.1.0",
        |  "description": "demo",
        |  "homepage": null,
        |  "developers": [],
        |  "licenses": [],
        |  "scmInfo": null
        |}""".stripMargin
    )

    assertEquals(exportedFile.exportedProjectId, "myproject")
    assertEquals(exportedFile.exportedScalaVersion, "3.3.4")
    assertEquals(exportedFile.platform, ExportedPlatform.Jvm)
    assertEquals(exportedFile.payload.id, "myproject")
    assertEquals(exportedFile.payload.scalaVersion, "3.3.4")
  }

  test("rejects exported filename metadata that disagrees with payload metadata") {
    val ex = intercept[IllegalArgumentException] {
      ExportedProjectExportFile.parse(
        os.pwd / "target/build-export/app_2.13.15.json",
        projectExportJson("different-app", os.pwd.toString, "3.3.5")
      )
    }

    assert(clue(ex.getMessage).contains("does not match"))
  }

  test("keeps multiple concrete root-level exports") {
    withExportedBuildFiles(Seq(
      "app_2.13.15.json" -> projectExportJson("app", os.pwd.toString, "2.13.15"),
      "app_3.3.5.json" -> projectExportJson("app", os.pwd.toString, "3.3.5"),
    )) {
      val importer = new SbtImporter(noopLogger)
      val exports = readAndParseExportedModules(importer)

      assertEquals(exports.map(_.exportedScalaVersion), IndexedSeq("2.13.15", "3.3.5"))
      assert(exports.forall(_.base == os.pwd.toString))
    }
  }

  test("fails with a clear error when sbt export exits non-zero") {
    withExportBuildPluginPath {
      val importer = new SbtImporter(noopLogger, _ => 23)

      val ex = intercept[java.lang.reflect.InvocationTargetException] {
        dumpSbtBuild(importer)
      }.getCause

      assert(clue(ex).isInstanceOf[IllegalStateException])
      assert(clue(ex.getMessage).contains("exportAllBuildStructures"))
      assert(clue(ex.getMessage).contains("23"))
    }
  }

  test("clears stale build-export files before running sbt export") {
    withExportedBuildFiles(Seq(
      "app_3.3.5.json" -> projectExportJson("app", os.pwd.toString, "3.3.5")
    )) {
      withExportBuildPluginPath {
        val exportDir = os.pwd / "target/build-export"
        val importer = new SbtImporter(noopLogger, _ => {
          assert(os.exists(exportDir))
          assertEquals(os.list(exportDir).toIndexedSeq, IndexedSeq.empty)
          17
        })

        intercept[java.lang.reflect.InvocationTargetException] {
          dumpSbtBuild(importer)
        }

        assert(os.exists(exportDir))
        assertEquals(os.list(exportDir).toIndexedSeq, IndexedSeq.empty)
      }
    }
  }

  test("isPluginDependency returns true for 'plugin' configuration") {
    val dep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover_3",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    assert(SbtProjectAnalyzer.isPluginDependency(dep))
  }

  test("isPluginDependency returns true for 'plugin->default' configuration") {
    val dep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover_3",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin->default(compile)"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    assert(SbtProjectAnalyzer.isPluginDependency(dep))
  }

  test("isPluginDependency returns false for 'test' configuration") {
    val dep = DependencyExport(
      organization = "org.scalameta",
      name = "munit_3",
      revision = "1.2.1",
      extraAttributes = Map.empty,
      configurations = Some("test"),
      excludes = Seq.empty,
      crossVersion = "binary"
    )
    assert(!SbtProjectAnalyzer.isPluginDependency(dep))
  }

  test("isPluginDependency returns false for None configuration") {
    val dep = DependencyExport(
      organization = "org.jsoup",
      name = "jsoup",
      revision = "1.21.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "none"
    )
    assert(!SbtProjectAnalyzer.isPluginDependency(dep))
  }

  test("formatDependency produces correct Maven coordinate for full crossVersion") {
    val dep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    assertEquals(SbtProjectAnalyzer.formatDependency(dep), "org.wartremover:::wartremover:3.5.0")
  }

  test("formatDependency produces correct Maven coordinate for binary crossVersion") {
    val dep = DependencyExport(
      organization = "org.scalameta",
      name = "munit",
      revision = "1.2.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "binary"
    )
    assertEquals(SbtProjectAnalyzer.formatDependency(dep), "org.scalameta::munit:1.2.1")
  }

  test("formatDependency produces correct Maven coordinate for Java dependency") {
    val dep = DependencyExport(
      organization = "org.jsoup",
      name = "jsoup",
      revision = "1.21.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "none"
    )
    assertEquals(SbtProjectAnalyzer.formatDependency(dep), "org.jsoup:jsoup:1.21.1")
  }

  test("dependencies are correctly partitioned between deps and scalacPluginDeps") {
    val regularDep = DependencyExport(
      organization = "org.jsoup",
      name = "jsoup",
      revision = "1.21.1",
      extraAttributes = Map.empty,
      configurations = None,
      excludes = Seq.empty,
      crossVersion = "none"
    )
    val pluginDep = DependencyExport(
      organization = "org.wartremover",
      name = "wartremover",
      revision = "3.5.0",
      extraAttributes = Map.empty,
      configurations = Some("plugin"),
      excludes = Seq.empty,
      crossVersion = "full"
    )
    val testDep = DependencyExport(
      organization = "org.scalameta",
      name = "munit",
      revision = "1.2.1",
      extraAttributes = Map.empty,
      configurations = Some("test"),
      excludes = Seq.empty,
      crossVersion = "binary"
    )

    val allDeps = Seq(regularDep, pluginDep, testDep)
    val (plugins, regular) = allDeps.partition(SbtProjectAnalyzer.isPluginDependency)

    assertEquals(plugins.length, 1)
    assertEquals(plugins.head.name, "wartremover")
    assertEquals(regular.length, 2)
    assert(regular.exists(_.name == "jsoup"))
    assert(regular.exists(_.name == "munit"))
  }
}
