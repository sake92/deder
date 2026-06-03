package ba.sake.deder

class FileWatchUtilsSuite extends munit.FunSuite {

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

  // ========= readGitignorePatterns =========

  test("readGitignorePatterns: strips comments and empty lines") {
    val gitignore = root / ".gitignore"
    os.write(gitignore,
      """|# This is a comment
         |
         |*.class
         |# another comment
         |build/
         |""".stripMargin)
    val patterns = FileWatchUtils.readGitignorePatterns(gitignore)
    assertEquals(patterns, Seq("*.class", "build/"))
  }

  test("readGitignorePatterns: preserves ! prefix") {
    val gitignore = root / ".gitignore-preserves"
    os.write(gitignore,
      """|*.class
         |!Important.class
         |""".stripMargin)
    val patterns = FileWatchUtils.readGitignorePatterns(gitignore)
    assertEquals(patterns, Seq("*.class", "!Important.class"))
  }

  test("readGitignorePatterns: returns empty for non-existent file") {
    val patterns = FileWatchUtils.readGitignorePatterns(root / "nonexistent")
    assertEquals(patterns, Seq.empty)
  }

  test("readGitignorePatterns: handles file without trailing newline") {
    val gitignore = root / ".gitignore-notrail"
    os.write(gitignore, "*.class")
    val patterns = FileWatchUtils.readGitignorePatterns(gitignore)
    assertEquals(patterns, Seq("*.class"))
  }

  // ========= isIgnoredByGitignore =========

  test("isIgnoredByGitignore: simple glob matches filename") {
    val patterns = Seq("*.class")
    assert(FileWatchUtils.isIgnoredByGitignore("Foo.class", isDir = false, patterns))
    assert(!FileWatchUtils.isIgnoredByGitignore("Foo.scala", isDir = false, patterns))
  }

  test("isIgnoredByGitignore: directory-only pattern (trailing /)") {
    val patterns = Seq("build/")
    assert(FileWatchUtils.isIgnoredByGitignore("build", isDir = true, patterns))
    assert(!FileWatchUtils.isIgnoredByGitignore("build", isDir = false, patterns))
  }

  test("isIgnoredByGitignore: directory-only pattern does not match file with same name") {
    val patterns = Seq("logs/")
    assert(!FileWatchUtils.isIgnoredByGitignore("logs", isDir = false, patterns))
    assert(FileWatchUtils.isIgnoredByGitignore("logs", isDir = true, patterns))
  }

  test("isIgnoredByGitignore: ** glob matches nested paths") {
    val patterns = Seq("**/*.class")
    assert(FileWatchUtils.isIgnoredByGitignore("Foo.class", isDir = false, patterns))
    assert(FileWatchUtils.isIgnoredByGitignore("bar/Foo.class", isDir = false, patterns))
    assert(FileWatchUtils.isIgnoredByGitignore("a/b/c/Foo.class", isDir = false, patterns))
  }

  test("isIgnoredByGitignore: **/build/ matches nested build directories") {
    val patterns = Seq("**/build/")
    assert(FileWatchUtils.isIgnoredByGitignore("build", isDir = true, patterns))
    assert(FileWatchUtils.isIgnoredByGitignore("foo/build", isDir = true, patterns))
    assert(FileWatchUtils.isIgnoredByGitignore("a/b/build", isDir = true, patterns))
  }

  test("isIgnoredByGitignore: leading / anchors to root") {
    val patterns = Seq("/build/")
    assert(FileWatchUtils.isIgnoredByGitignore("build", isDir = true, patterns))
    assert(!FileWatchUtils.isIgnoredByGitignore("src/build", isDir = true, patterns))
  }

  test("isIgnoredByGitignore: negation (!) un-ignores a path") {
    val patterns = Seq("*.class", "!Important.class")
    assert(!FileWatchUtils.isIgnoredByGitignore("Important.class", isDir = false, patterns))
    assert(FileWatchUtils.isIgnoredByGitignore("Other.class", isDir = false, patterns))
  }

  test("isIgnoredByGitignore: last matching pattern wins for negation") {
    val patterns = Seq("!Important.class", "*.class")
    assert(FileWatchUtils.isIgnoredByGitignore("Important.class", isDir = false, patterns))
  }

  test("isIgnoredByGitignore: path-style pattern matches from root with boundary check") {
    val patterns = Seq("target/scala-3")
    assert(FileWatchUtils.isIgnoredByGitignore("target/scala-3", isDir = true, patterns))
    assert(FileWatchUtils.isIgnoredByGitignore("target/scala-3/classes", isDir = true, patterns))
    assert(!FileWatchUtils.isIgnoredByGitignore("src/target/scala-3", isDir = true, patterns))
  }

  test("isIgnoredByGitignore: prefix match does not match sibling prefixes") {
    val patterns = Seq("build/output")
    assert(FileWatchUtils.isIgnoredByGitignore("build/output", isDir = false, patterns))
    assert(!FileWatchUtils.isIgnoredByGitignore("build/output2.class", isDir = false, patterns))
  }

  test("isIgnoredByGitignore: empty patterns list matches nothing") {
    val patterns = Seq.empty[String]
    assert(!FileWatchUtils.isIgnoredByGitignore("anything.txt", isDir = false, patterns))
  }

  test("isIgnoredByGitignore: * matches any single directory component") {
    val patterns = Seq("foo/*/bar")
    assert(FileWatchUtils.isIgnoredByGitignore("foo/x/bar", isDir = false, patterns))
    assert(!FileWatchUtils.isIgnoredByGitignore("foo/x/y/bar", isDir = false, patterns))
  }

  // ========= Merged .gitignore + Pkl watchIgnore =========

  test("merged patterns: Pkl pattern can ignore what .gitignore doesn't") {
    val gitignorePatterns = Seq("*.class")
    val pklPatterns = Seq("*.log")
    val merged = gitignorePatterns ++ pklPatterns

    // .gitignore alone would NOT ignore this
    assert(!FileWatchUtils.isIgnoredByGitignore("debug.log", isDir = false, gitignorePatterns))
    // merged patterns DO ignore it (Pkl adds *.log)
    assert(FileWatchUtils.isIgnoredByGitignore("debug.log", isDir = false, merged))
    // .gitignore pattern still works
    assert(FileWatchUtils.isIgnoredByGitignore("Foo.class", isDir = false, merged))
  }

  test("merged patterns: Pkl negation overrides .gitignore (last match wins)") {
    val gitignorePatterns = Seq("*.log")        // ignore all logs
    val pklPatterns = Seq("!important.log")     // but NOT important.log
    val merged = gitignorePatterns ++ pklPatterns

    // important.log is NOT ignored (Pkl ! overrides .gitignore *)
    assert(!FileWatchUtils.isIgnoredByGitignore("important.log", isDir = false, merged))
    // other .log files ARE still ignored
    assert(FileWatchUtils.isIgnoredByGitignore("debug.log", isDir = false, merged))
  }

  test("merged patterns: empty Pkl list = same behavior as .gitignore alone") {
    val gitignorePatterns = Seq("*.class", "build/")
    val pklPatterns = Seq.empty[String]
    val merged = gitignorePatterns ++ pklPatterns

    assert(FileWatchUtils.isIgnoredByGitignore("Foo.class", isDir = false, merged))
    assert(FileWatchUtils.isIgnoredByGitignore("build", isDir = true, merged))
    assert(!FileWatchUtils.isIgnoredByGitignore("Main.scala", isDir = false, merged))
  }

  test("merged patterns: Pkl directory pattern with trailing slash") {
    val gitignorePatterns = Seq.empty[String]
    val pklPatterns = Seq("generated/")
    val merged = gitignorePatterns ++ pklPatterns

    assert(FileWatchUtils.isIgnoredByGitignore("generated", isDir = true, merged))
    assert(!FileWatchUtils.isIgnoredByGitignore("generated", isDir = false, merged))
  }

  test("merged patterns: Pkl ** pattern matches nested paths") {
    val gitignorePatterns = Seq.empty[String]
    val pklPatterns = Seq("**/deprecated/**")
    val merged = gitignorePatterns ++ pklPatterns

    assert(FileWatchUtils.isIgnoredByGitignore("src/deprecated/Old.scala", isDir = false, merged))
    assert(FileWatchUtils.isIgnoredByGitignore("a/b/deprecated/x/y/Foo.java", isDir = false, merged))
    assert(!FileWatchUtils.isIgnoredByGitignore("src/new/Feature.scala", isDir = false, merged))
  }
}
