package ba.sake.deder.importing.sbt

import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.importing._
import munit.FunSuite

class SbtProjectAnalyzerDummyRootSuite extends FunSuite {

  private def collectingLogger(buffer: scala.collection.mutable.ArrayBuffer[String]) =
    ServerNotificationsLogger(msg => buffer += msg.toString)

  private val noopLogger = ServerNotificationsLogger(_ => ())

  private def baseModule(
      id: String,
      base: String,
      name: String,
      scalaVersion: String = "3.3.5",
      externalDeps: Seq[DependencyExport] = Seq.empty,
      interProjectDeps: Seq[InterProjectDependencyExport] = Seq.empty
  ): ExportedProjectExportFile = ExportedProjectExportFile(
    payload = ProjectExport(
      id = id,
      base = base,
      name = name,
      javacOptions = Seq.empty,
      scalaVersion = scalaVersion,
      scalacOptions = Seq.empty,
      interProjectDependencies = interProjectDeps,
      externalDependencies = externalDeps,
      repositories = Seq.empty,
      sourceDirs = Seq("src/main/scala"),
      testSourceDirs = Seq("src/test/scala"),
      resourceDirs = Seq.empty,
      testResourceDirs = Seq.empty,
      plugins = Seq.empty,
      organization = "com.example",
      artifactName = name,
      artifactType = "jar",
      artifactClassifier = None,
      version = "0.1.0",
      description = "",
      homepage = None,
      developers = Seq.empty,
      licenses = Seq.empty,
      scmInfo = None
    ),
    exportedProjectId = id,
    exportedScalaVersion = scalaVersion,
    platform = ExportedPlatform.fromBase(base)
  )

  private def withFile(path: os.Path, content: String = "object RootSource")(body: => Unit): Unit = {
    val backupPath = path / os.up / s".${path.last}.dummy-root-suite-backup"
    if os.exists(backupPath) then os.remove(backupPath)
    if os.exists(path) then {
      os.makeDir.all(backupPath / os.up)
      os.move(path, backupPath)
    }
    os.makeDir.all(path / os.up)
    os.write.over(path, content)
    try body
    finally {
      if os.exists(path) then os.remove(path)
      if os.exists(backupPath) then {
        os.makeDir.all(path / os.up)
        os.move(backupPath, path)
      }
    }
  }

  test("skips aggregate-only root group and logs reasons") {
    val logs = scala.collection.mutable.ArrayBuffer.empty[String]
    val analyzer = new SbtProjectAnalyzer(collectingLogger(logs))
    val root = baseModule("root", os.pwd.toString, "jawn-root")
    val core = baseModule("core", (os.pwd / "core").toString, "core")

    val build = analyzer.analyze(IndexedSeq(root, core))

    assertEquals(build.moduleGroups.map(_.builderVarName), Seq("core"))
    assert(logs.exists(_.contains("Skipping aggregate-only sbt group")))
    assert(logs.exists(_.contains("root")))
  }

  test("keeps root-named module when it has real sources on disk") {
    val analyzer = new SbtProjectAnalyzer(noopLogger)
    val root = baseModule("root", os.pwd.toString, "jawn-root")
    val core = baseModule("core", (os.pwd / "core").toString, "core")
    val rootSource = os.pwd / "src" / "main" / "scala" / "DummyRootSuiteRoot.scala"

    withFile(rootSource) {
      val build = analyzer.analyze(IndexedSeq(root, core))
      assert(build.moduleGroups.map(_.builderVarName).contains("jawn_root"))
    }
  }

  test("keeps single-module root project") {
    val analyzer = new SbtProjectAnalyzer(noopLogger)
    val root = baseModule("root", os.pwd.toString, "jawn-root")

    val build = analyzer.analyze(IndexedSeq(root))

    assert(build.moduleGroups.map(_.builderVarName).contains("jawn_root"))
  }
}

