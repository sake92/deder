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

  /** Force-stop any deder server running in the given directory. */
  private def forceShutdownServer(dir: os.Path): Unit = {
    // Try graceful shutdown first (ignore errors)
    try executeDederCommand(dir, "shutdown")
    catch case _: Exception => ()
    // Kill any remaining server process for this directory
    val lockFile = dir / ".deder/server.lock"
    if os.exists(lockFile) then
      try
        val pid = os.read(lockFile).trim
        os.proc("kill", pid).call(check = false)
      catch case _: Exception => ()
    // Clean up lock/socket files
    try os.remove(lockFile) catch case _: Exception => ()
    try os.remove(dir / ".deder/server-cli.sock") catch case _: Exception => ()
    try os.remove(dir / ".deder/server-bsp.sock") catch case _: Exception => ()
    // Kill any sbt orphan processes
    try os.proc("pkill", "-f", s"sbt.*${dir.last}").call(check = false) catch case _: Exception => ()
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

      // Use autodetection (build.sbt should be present in sbt projects)
      val importRes = executeDederCommand(tempDir, "import")
      assertEquals(importRes.exitCode, 0, s"deder import failed (exit ${importRes.exitCode}):\n${importRes.err.text()}")

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

      // 8. Verify the imported project actually compiles
      val compileRes = executeDederCommand(tempDir, "exec", "-t", "compile")
      assertEquals(compileRes.exitCode, 0,
        s"deder exec compile failed (exit ${compileRes.exitCode}):\n${compileRes.err.text()}")

    } finally {
      forceShutdownServer(tempDir)
    }
  }

  test("import jawn v1.6.0") {
    testImport("https://github.com/typelevel/jawn.git", "v1.6.0", expectedMinModules = 30)
  }

  // TODO: enable once importer handles ScalaJsModule/ScalaNativeModule template types correctly
  // test("import chimney 1.7.3") {
  //   testImport("https://github.com/scalalandio/chimney.git", "1.7.3", expectedMinModules = 3)
  // }

  // test("import monocle v3.3.0") {
  //   testImport("https://github.com/optics-dev/Monocle.git", "v3.3.0", expectedMinModules = 5)
  // }
}
