package ba.sake.deder

class FileWatchUtilsSuite extends munit.FunSuite:

  private val root = os.temp.dir()

  override def afterAll(): Unit = os.remove.all(root)

  // ========= isDederArtifact =========

  test("isDederArtifact: paths under .deder/ are artifacts") {
    val path = root / ".deder" / "server.jar"
    os.write(path, "", createFolders = true)
    assert(FileWatchUtils.isDederArtifact(path, root))
  }

  test("isDederArtifact: .deder/server.lock is an artifact") {
    val path = root / ".deder" / "server.lock"
    os.write(path, "", createFolders = true)
    assert(FileWatchUtils.isDederArtifact(path, root))
  }

  test("isDederArtifact: .deder/out/foo.class is an artifact") {
    val path = root / ".deder" / "out" / "foo.class"
    os.write(path, "", createFolders = true)
    assert(FileWatchUtils.isDederArtifact(path, root))
  }

  test("isDederArtifact: .deder/logs/app.log is an artifact") {
    val path = root / ".deder" / "logs" / "app.log"
    os.write(path, "", createFolders = true)
    assert(FileWatchUtils.isDederArtifact(path, root))
  }

  test("isDederArtifact: src/Main.scala is NOT an artifact") {
    val path = root / "src" / "Main.scala"
    os.write(path, "", createFolders = true)
    assert(!FileWatchUtils.isDederArtifact(path, root))
  }

  test("isDederArtifact: deder.pkl is NOT an artifact") {
    val path = root / "deder.pkl"
    os.write(path, "", createFolders = true)
    assert(!FileWatchUtils.isDederArtifact(path, root))
  }

  // ========= isDevArtifact =========

  test("isDevArtifact: target/scala-3/classes/ is a dev artifact") {
    val path = root / "target" / "scala-3" / "classes"
    os.makeDir.all(path)
    assert(FileWatchUtils.isDevArtifact(path, root))
  }

  test("isDevArtifact: .git/HEAD is a dev artifact") {
    val path = root / ".git" / "HEAD"
    os.write(path, "", createFolders = true)
    assert(FileWatchUtils.isDevArtifact(path, root))
  }

  test("isDevArtifact: .idea/workspace.xml is a dev artifact") {
    val path = root / ".idea" / "workspace.xml"
    os.write(path, "", createFolders = true)
    assert(FileWatchUtils.isDevArtifact(path, root))
  }

  test("isDevArtifact: out/foo is a dev artifact") {
    val path = root / "out" / "foo"
    os.write(path, "", createFolders = true)
    assert(FileWatchUtils.isDevArtifact(path, root))
  }

  test("isDevArtifact: src/target/Foo.scala is NOT a dev artifact (nested)") {
    val path = root / "src" / "target" / "Foo.scala"
    os.write(path, "", createFolders = true)
    assert(!FileWatchUtils.isDevArtifact(path, root))
  }

  test("isDevArtifact: root file named target-fix.txt is NOT a dev artifact") {
    val path = root / "target-fix.txt"
    os.write(path, "", createFolders = true)
    assert(!FileWatchUtils.isDevArtifact(path, root))
  }
