package ba.sake.deder

import scala.concurrent.duration.*

class CachedTaskSuite extends BaseIntegrationSuite {

  override def munitTimeout = 2.minute

  test("cached tasks: first run should compute results") {
    withTestProject("sample-projects/multi", serverProperties = Map("logLevel" -> "DEBUG")) { projectPath =>
      val res = executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      val logLines = os.read.lines(projectPath / ".deder/logs/server.log")
      assert(
        logLines.exists(_.contains("Computed result for compileClasspath")),
        "Expected 'Computed result for compileClasspath' in log on first run"
      )
      assert(
        !logLines.exists(_.contains("Using cached result for compileClasspath")),
        "Did not expect 'Using cached result for compileClasspath' in log on first run"
      )
    }
  }

  test("cached tasks: second run should use cached results") {
    withTestProject("sample-projects/multi", serverProperties = Map("logLevel" -> "DEBUG")) { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      val offsetAfterFirstRun = serverLogOffset(projectPath)
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      val newLines = readNewServerLogLines(projectPath, offsetAfterFirstRun)
      assert(
        newLines.exists(_.contains("Using cached result for compileClasspath")),
        "Expected 'Using cached result for compileClasspath' in second run log"
      )
      assert(
        !newLines.exists(_.contains("Computed result for compileClasspath")),
        "Did not expect 'Computed result for compileClasspath' in second run log"
      )
    }
  }

  test("cached tasks: should recompute after dependency change") {
    withTestProject("sample-projects/multi", serverProperties = Map("logLevel" -> "DEBUG")) { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      val offsetAfterFirstRun = serverLogOffset(projectPath)
      // Add jsoup dep to common module — this changes the `deps` task output hash,
      // which invalidates the `compileClasspath` cache entry for common
      val pklContent = os.read(projectPath / "deder.pkl")
      os.write.over(
        projectPath / "deder.pkl",
        pklContent.replace(
          """  //"org.jsoup:jsoup:1.21.1"""",
          """  "org.jsoup:jsoup:1.21.1""""
        )
      )
      // Give the server file watcher a moment to pick up the config change
      Thread.sleep(500)
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      val newLines = readNewServerLogLines(projectPath, offsetAfterFirstRun)
      assert(
        newLines.exists(_.contains("Computed result for compileClasspath")),
        "Expected 'Computed result for compileClasspath' after dependency change"
      )
    }
  }

  test("cached tasks: should recompute after a source file rename that preserves sort order") {
    withTestProject("sample-projects/multi", serverProperties = Map("logLevel" -> "DEBUG")) { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "sourceFiles")
      val offsetAfterFirstRun = serverLogOffset(projectPath)
      // Rename common/src/Common.scala -> Commom.scala. It's the only entry in its dir,
      // so the rename trivially preserves sort order. Content unchanged. Before the Hashable fix
      // this rename was invisible to the source-dir hash and sourceFiles stayed cached.
      os.move(projectPath / "common/src/Common.scala", projectPath / "common/src/Commom.scala")
      // Give the server file watcher a moment to observe the rename.
      Thread.sleep(500)
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "sourceFiles")
      val newLines = readNewServerLogLines(projectPath, offsetAfterFirstRun)
      assert(
        newLines.exists(_.contains("Computed result for sourceFiles")),
        "Expected 'Computed result for sourceFiles' after source file rename"
      )
    }
  }

  test("cached tasks: should handle corrupted metadata.json gracefully") {
    withTestProject("sample-projects/multi", serverProperties = Map("logLevel" -> "DEBUG")) { projectPath =>
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      os.write.over(
        projectPath / ".deder/out/common/compileClasspath/metadata.json",
        "not valid json {{{"
      )
      val offsetAfterCorrupt = serverLogOffset(projectPath)
      val result = executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      assertEquals(result.exitCode, 0, "Expected successful exit after corrupted metadata")
      val newLines = readNewServerLogLines(projectPath, offsetAfterCorrupt)
      assert(
        newLines.exists(_.contains("Computed result for compileClasspath")),
        "Expected 'Computed result for compileClasspath' after corrupted metadata"
      )
    }
  }

  private def serverLogOffset(projectPath: os.Path): Long = {
    val logFile = projectPath / ".deder/logs/server.log"
    if os.exists(logFile) then os.stat(logFile).size else 0L
  }

  private def readNewServerLogLines(projectPath: os.Path, startOffset: Long): Seq[String] = {
    val logFile = projectPath / ".deder/logs/server.log"
    if !os.exists(logFile) then return Seq.empty
    val allBytes = os.read.bytes(logFile)
    new String(allBytes.drop(startOffset.toInt), "UTF-8").linesIterator.toSeq
  }
}
