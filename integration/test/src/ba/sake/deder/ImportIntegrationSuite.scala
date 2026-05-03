package ba.sake.deder

import scala.concurrent.duration.*

class ImportIntegrationSuite extends BaseIntegrationSuite {

  override def munitTimeout = 10.minutes

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

  /** Clone a repo at a specific tag, run deder import, validate output. */
  private def testImport(repoUrl: String, gitRef: String, expectedMinModules: Int): Unit = {
    val repoName = repoUrl.split("/").last.stripSuffix(".git")
    val tempDir = os.pwd / "tmp" / s"import-$repoName-${System.currentTimeMillis()}"

    try {
      // 1. Shallow clone at specific tag
      val cloneRes = os.proc("git", "clone", "--depth", "1", "--branch", gitRef, repoUrl, tempDir.toString)
        .call(cwd = os.pwd, stderr = os.Pipe, check = false)
      assertEquals(cloneRes.exitCode, 0, s"git clone $repoUrl@$gitRef failed:\n${cloneRes.err.text()}")

      // 2. Set up server properties so the server JAR can be found
      val serverProps =
        s"localPath=$dederServerPath\n" +
        s"testRunnerLocalPath=$dederTestRunnerPath\n"
      os.write.over(tempDir / ".deder/server.properties", serverProps, createFolders = true)

      // 3. Run deder import (writes sbt plugin, runs sbt exportBuildStructure, parses JSON, generates deder.pkl)
      val importRes = executeDederCommand(tempDir, "import", "--from", "sbt")
      assertEquals(importRes.exitCode, 0,
        s"deder import failed (exit ${importRes.exitCode}):\n${importRes.err.text()}")

      // 4. Verify deder.pkl was created and tweak to use local config
      assert(os.exists(tempDir / "deder.pkl"), "deder.pkl was not created by import")
      val dederPklContent = os.read(tempDir / "deder.pkl")
      val tweakedContent = dederPklContent.replaceFirst(
        "amends \"https://sake92.github.io/deder/config/early-access/DederProject.pkl\"",
        "amends \"../../config/DederProject.pkl\""
      )
      os.write.over(tempDir / "deder.pkl", tweakedContent)

      // 5. Shutdown server so next command starts fresh with the new deder.pkl
      executeDederCommand(tempDir, "shutdown")

      // 6. Validate generated Pkl by listing modules
      val modulesRes = executeDederCommand(tempDir, "modules")
      val modulesOut = modulesRes.out.text()
      val modulesErr = modulesRes.err.text()
      assertEquals(modulesRes.exitCode, 0,
        s"deder modules failed (exit ${modulesRes.exitCode}):\nstderr: $modulesErr\nstdout: $modulesOut")

      // 7. Check module count (at least the expected minimum)  
      // deder modules outputs module names to stdout, one per line
      val allOutput = if modulesOut.nonEmpty then modulesOut else modulesErr
      val moduleLines = allOutput.linesIterator.filter { line =>
        val t = line.trim
        t.nonEmpty && !t.startsWith("Deder") && !t.startsWith("Using") && !t.startsWith("[")
      }.toSeq
      assert(moduleLines.size >= expectedMinModules,
        s"Expected >= $expectedMinModules modules, got ${moduleLines.size}\nOutput:\n${allOutput}")

    } finally {
      executeDederCommand(tempDir, "shutdown")
      // keep temp dir for debugging
    }
  }

  test("import scalacheck v1.19.0") {
    testImport("https://github.com/typelevel/scalacheck.git", "v1.19.0", expectedMinModules = 5)
  }
}
