package ba.sake.deder

import scala.concurrent.duration.*
import ba.sake.deder.importing.DederPklRenderer

class ImportIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 15.minutes

  private val sbtAvailable: Boolean =
    try os.proc("sbt", "--version").call(check = false, stderr = os.Pipe).exitCode == 0
    catch case _: Exception => false

  private val gitAvailable: Boolean =
    try os.proc("git", "--version").call(check = false, stderr = os.Pipe).exitCode == 0
    catch case _: Exception => false

  override def beforeAll(): Unit = {
    assume(sbtAvailable, "sbt not found on PATH - skipping import tests")
    assume(gitAvailable, "git not found on PATH - skipping import tests")
  }

  test("import sbt hello-world") {
    testImportFromFixture("import-sbt-hello-world")
  }

  test("import sbt cross-versions") {
    testImportFromFixture("import-sbt-cross-versions")
  }

  test("import sbt cross-full") {
    testImportFromFixture("import-sbt-cross-full")
  }

  test("import sbt tpolecat".ignore) {
    testImportFromFixture("import-sbt-tpolecat")
  }

  test("import sbt typelevel".ignore) {
    testImportFromFixture("import-sbt-typelevel", initGit = true)
  }

  test("import jawn v1.6.0".ignore) {
    testImport("https://github.com/typelevel/jawn.git", "v1.6.0", expectedMinModules = 30)
  }

  /** Force-stop any deder server running in the given directory. */
  private def forceShutdownServer(dir: os.Path): Unit = {
    try executeDederCommand(dir, "shutdown")
    catch case _: Exception => ()
    val lockFile = dir / ".deder/server.lock"
    if os.exists(lockFile) then
      try
        val pid = os.read(lockFile).trim
        os.proc("kill", pid).call(check = false)
      catch case _: Exception => ()
    try os.remove(lockFile) catch case _: Exception => ()
    try os.remove(dir / ".deder/server-cli.sock") catch case _: Exception => ()
    try os.remove(dir / ".deder/server-bsp.sock") catch case _: Exception => ()
    try os.proc("pkill", "-f", s"sbt.*${dir.last}").call(check = false) catch case _: Exception => ()
  }

  /** Stage an sbt fixture from sample-projects/, run deder import, compile, and optionally test. */
  private def testImportFromFixture(
      fixtureName: String,
      initGit: Boolean = false
  ): Unit = {
    val fixturePath = os.RelPath("sample-projects") / fixtureName
    val tempDir = os.pwd / "tmp" / s"$fixtureName-${System.currentTimeMillis()}"

    try {
      // Stage the fixture (copy all files except .deder/)
      val sourceDir = testResourceDir / fixturePath
      os.makeDir.all(tempDir)
      for entry <- os.list(sourceDir) if entry.last != ".deder" do
        os.copy(entry, tempDir / entry.last, createFolders = true, replaceExisting = true)

      // Initialize a real git repo if needed (sbt-typelevel requires it)
      if initGit then
        os.proc("git", "init").call(cwd = tempDir)
        os.proc("git", "config", "user.email", "test@test.com").call(cwd = tempDir)
        os.proc("git", "config", "user.name", "Test").call(cwd = tempDir)
        os.proc("git", "add", ".").call(cwd = tempDir)
        os.proc("git", "commit", "-m", "init").call(cwd = tempDir, check = false)

      // Write server properties
      val serverProps =
        s"localPath=$dederServerPath\n" +
          s"testRunnerLocalPath=$dederTestRunnerPath\n" +
          s"maxConnectSeconds=300\n"
      os.write.over(tempDir / ".deder/server.properties", serverProps, createFolders = true)

      // Run deder import
      val importRes = executeDederCommand(tempDir, "import", "--from", "sbt")
      assertEquals(importRes.exitCode, 0,
        s"deder import failed (exit ${importRes.exitCode}):\n${importRes.err.text()}")

      assert(os.exists(tempDir / "deder.pkl"), "deder.pkl was not created by import")

      // Tweak amends to use local config (so we don't need network)
      val dederPklContent = os.read(tempDir / "deder.pkl")
      val tweakedContent = dederPklContent
        .replaceFirst(
          s"amends \"https://sake92.github.io/deder/config/${DederPklRenderer.DederVersion}/DederProject.pkl\"",
          "amends \"../../config/DederProject.pkl\""
        )
        .replace(
          s"import \"https://sake92.github.io/deder/config/${DederPklRenderer.DederVersion}/DederTypelevel.pkl\"",
          "import \"../../config/DederTypelevel.pkl\""
        )
        .replace(
          s"import \"https://sake92.github.io/deder/config/${DederPklRenderer.DederVersion}/DederTpolecat.pkl\"",
          "import \"../../config/DederTpolecat.pkl\""
        )
      os.write.over(tempDir / "deder.pkl", tweakedContent)

      // Shutdown server so next command starts fresh
      forceShutdownServer(tempDir)

      // Verify modules are resolved
      val modulesRes = executeDederCommand(tempDir, "modules")
      val modulesErr = modulesRes.err.text()
      assertEquals(modulesRes.exitCode, 0,
        s"deder modules failed (exit ${modulesRes.exitCode}):\n$modulesErr")

      // Compile
      val compileRes = executeDederCommand(tempDir, "exec", "-t", "compile")
      assertEquals(compileRes.exitCode, 0,
        s"deder exec compile failed (exit ${compileRes.exitCode}):\n${compileRes.err.text()}")

      // Run tests on JVM test modules
      val testModules = findJvmTestModules(tempDir)
      for tm <- testModules do
        val testRes = executeDederCommand(tempDir, "exec", "-t", "test", "-m", tm)
        assertEquals(testRes.exitCode, 0,
          s"deder exec test -m $tm failed (exit ${testRes.exitCode}):\n${testRes.err.text()}")

    } finally {
      forceShutdownServer(tempDir)
    }
  }

  /** Find JVM test module IDs from a deder.pkl. */
  private def findJvmTestModules(dir: os.Path): Seq[String] = {
    val pklContent = os.read(dir / "deder.pkl")
    val idPattern = """id = "([^"]*)"""".r
    val idMappings = idPattern.findAllMatchIn(pklContent).map(_.group(1)).toSeq
    // Find module builder IDs whose test module variant is a JVM test
    val testIdPattern = """testId = "([^"]*)"""".r
    val explicitTestIds = testIdPattern.findAllMatchIn(pklContent).map(_.group(1)).toSeq
    if explicitTestIds.nonEmpty then explicitTestIds
    else
      // For cross-version modules, look for jvm-test- pattern in module list output
      val modRes = executeDederCommand(dir, "modules")
      val modOut = modRes.out.text()
      val pattern = """\S+-jvm-test-\S+""".r
      pattern.findAllIn(modOut).toSeq.distinct
  }

  /** Clone a repo at a specific tag, run deder import, validate output. */
  private def testImport(repoUrl: String, gitRef: String, expectedMinModules: Int): Unit = {
    val repoName = repoUrl.split("/").last.stripSuffix(".git")
    val tempDir = os.pwd / "tmp" / s"import-$repoName-${System.currentTimeMillis()}"

    try {
      val cloneRes = os
        .proc("git", "clone", "--depth", "1", "--branch", gitRef, repoUrl, tempDir.toString)
        .call(cwd = os.pwd, stderr = os.Pipe, check = false)
      assertEquals(cloneRes.exitCode, 0, s"git clone $repoUrl@$gitRef failed:\n${cloneRes.err.text()}")

      val serverProps =
        s"localPath=$dederServerPath\n" +
          s"testRunnerLocalPath=$dederTestRunnerPath\n" +
          s"maxConnectSeconds=300\n"
      os.write.over(tempDir / ".deder/server.properties", serverProps, createFolders = true)

      val importRes = executeDederCommand(tempDir, "import")
      assertEquals(importRes.exitCode, 0, s"deder import failed (exit ${importRes.exitCode}):\n${importRes.err.text()}")

      assert(os.exists(tempDir / "deder.pkl"), "deder.pkl was not created by import")
      val dederPklContent = os.read(tempDir / "deder.pkl")
      val tweakedContent = dederPklContent
        .replaceFirst(
          s"amends \"https://sake92.github.io/deder/config/${DederPklRenderer.DederVersion}/DederProject.pkl\"",
          "amends \"../../config/DederProject.pkl\""
        )
        .replace(
          s"import \"https://sake92.github.io/deder/config/${DederPklRenderer.DederVersion}/DederTypelevel.pkl\"",
          "import \"../../config/DederTypelevel.pkl\""
        )
        .replace(
          s"import \"https://sake92.github.io/deder/config/${DederPklRenderer.DederVersion}/DederTpolecat.pkl\"",
          "import \"../../config/DederTpolecat.pkl\""
        )
      os.write.over(tempDir / "deder.pkl", tweakedContent)

      forceShutdownServer(tempDir)

      val modulesRes = executeDederCommand(tempDir, "modules")
      val modulesOut = modulesRes.out.text()
      val modulesErr = modulesRes.err.text()
      assertEquals(
        modulesRes.exitCode,
        0,
        s"deder modules failed (exit ${modulesRes.exitCode}):\nstderr: $modulesErr\nstdout: $modulesOut"
      )

      val allOutput = if modulesOut.nonEmpty then modulesOut else modulesErr
      val moduleLines = allOutput.linesIterator.filter { line =>
        val t = line.trim
        t.nonEmpty && !t.startsWith("Deder") && !t.startsWith("Using") && !t.startsWith("[")
      }.toSeq
      assert(
        moduleLines.size >= expectedMinModules,
        s"Expected >= $expectedMinModules modules, got ${moduleLines.size}\nOutput:\n${allOutput}"
      )

      val compileRes = executeDederCommand(tempDir, "exec", "-t", "compile")
      assertEquals(compileRes.exitCode, 0,
        s"deder exec compile failed (exit ${compileRes.exitCode}):\n${compileRes.err.text()}")

    } finally {
      forceShutdownServer(tempDir)
    }
  }
}
