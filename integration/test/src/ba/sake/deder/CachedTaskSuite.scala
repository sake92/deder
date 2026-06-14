package ba.sake.deder

import scala.concurrent.duration.*

class CachedTaskSuite extends BaseIntegrationSuite {

  test("cached tasks: first run computes, second run uses cache") {
    withTestProject("sample-projects/multi", serverProperties = Map("logLevel" -> "DEBUG")) { projectPath =>
      // First run — should compute the result
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      val logAfterFirst = os.read.lines(projectPath / ".deder/logs/server.log")
      assert(
        logAfterFirst.exists(_.contains("Computed result for compileClasspath")),
        "Expected 'Computed result for compileClasspath' in log on first run"
      )
      assert(
        !logAfterFirst.exists(_.contains("Using cached result for compileClasspath")),
        "Did not expect 'Using cached result for compileClasspath' in log on first run"
      )

      // Second run — should use the cached result
      val offsetAfterFirst = serverLogOffset(projectPath)
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compileClasspath")
      val newLines = readNewServerLogLines(projectPath, offsetAfterFirst)
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

  test("cached tasks: compile is reused on second run (no self-referential deps)") {
    withTestProject("sample-projects/multi", serverProperties = Map("logLevel" -> "DEBUG")) { projectPath =>
      // First run computes
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compile")
      val offsetAfterFirst = serverLogOffset(projectPath)
      // Second run with nothing changed must hit cache. Regression guard: compile must not
      // depend on its own outputs (classes dir, semanticdb dir) — those are written by compile,
      // so content-hashing them as inputs made compile's key change every build (never hit).
      executeDederCommand(projectPath, "exec", "-m", "common", "-t", "compile")
      val newLines = readNewServerLogLines(projectPath, offsetAfterFirst)
      // Exact-match the task name: `contains("...for compile")` would also match
      // `compilerJars` / `compileOnlyDeps` / `compileClasspath`.
      def usedCache(l: String) = l.trim.endsWith("Using cached result for compile")
      def recomputed(l: String) = l.trim.endsWith("Computed result for compile")
      val compileLines = newLines.filter(l => usedCache(l) || recomputed(l))
      assert(
        newLines.exists(usedCache),
        s"Expected compile to be served from cache on the second identical run, got:\n${compileLines.mkString("\n")}"
      )
      assert(
        !newLines.exists(recomputed),
        s"compile should not recompute when nothing changed. compile lines:\n${compileLines.mkString("\n")}"
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
