package ba.sake.deder

import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

class RelocationSuite extends BaseIntegrationSuite {

  private val quietFlags = Seq("--log-level", "warning", "--no-color")
  private val props = Map("logLevel" -> "DEBUG")

  /** Compiles all modules in a fresh project, copies it (with .deder/out) elsewhere and deletes the original. */
  private def compileAndRelocate(): (os.Path, os.Path) = {
    val original = stagedServerProject("sample-projects/multi", props, dirSuffix = s"reloc-src-${System.nanoTime()}")
    val relocated = os.pwd / "tmp" / s"multi-reloc-dst-${System.nanoTime()}"
    // flags must come AFTER -t/-m, otherwise those are ignored
    val first = executeDederCommand(original, Seq("exec", "-t", "compile") ++ quietFlags*)
    assertEquals(first.exitCode, 0, first.err.text())
    // stop the server so outputs are flushed and the sockets/lock are not copied
    executeDederCommand(original, "shutdown")
    Thread.sleep(1000)

    // relocate: copy everything except the runtime artifacts in .deder (sockets, lock, logs)
    os.makeDir.all(relocated)
    for entry <- os.list(original) if entry.last != ".deder" do
      os.copy(entry, relocated / entry.last, createFolders = true)
    os.makeDir.all(relocated / ".deder")
    os.copy(original / ".deder/out", relocated / ".deder/out", createFolders = true)
    os.copy(original / ".deder/server.properties", relocated / ".deder/server.properties")

    // no leaked absolute project path, neither in plain files nor inside zip/jar archives
    val leaks = leakedFiles(relocated / ".deder/out", original.toString)
    assert(leaks.isEmpty, s"Found absolute path of the original project in:\n${leaks.mkString("\n")}")

    // the old location must not be needed anymore
    os.remove.all(original)
    (original, relocated)
  }

  private def cleanup(original: os.Path, relocated: os.Path): Unit = {
    if os.exists(original) then executeDederCommand(original, "shutdown")
    if os.exists(relocated) then executeDederCommand(relocated, "shutdown")
  }

  private def serverLog(project: os.Path): Seq[String] =
    os.read.lines(project / ".deder/logs/server.log")

  test("relocation: copied project (with .deder/out) is a full cache hit for all modules") {
    val (original, relocated) = compileAndRelocate()
    try {
      // tasks that persisted a metadata.json in the first run are the cached ones
      val cachedTasks = os.walk(relocated / ".deder/out", maxDepth = 3)
        .filter(_.last == "metadata.json")
        .map(_.segments.toSeq.reverse(1))
        .toSet
      assert(cachedTasks.contains("compile"), s"Expected compile to be a cached task, got: $cachedTasks")

      val second = executeDederCommand(relocated, Seq("exec", "-t", "compile") ++ quietFlags*)
      assertEquals(second.exitCode, 0, second.err.text())
      val lines = serverLog(relocated)

      val recomputed = cachedTasks.toSeq.sorted.flatMap { task =>
        lines.filter(_.trim.endsWith(s"Computed result for $task"))
      }
      assert(recomputed.isEmpty, s"Cached tasks recomputed after relocation:\n${recomputed.mkString("\n")}")
      assert(lines.exists(_.trim.endsWith("Using cached result for compile")), "Expected compile served from cache")
      // second signal: Zinc was never invoked
      assert(
        !lines.exists(_.contains("Starting compilation")),
        "Zinc should not run at all after relocation"
      )
    } finally cleanup(original, relocated)
  }

  test("relocation: relocated analysis supports minimal incremental recompilation") {
    val (original, relocated) = compileAndRelocate()
    try {
      val warm = executeDederCommand(relocated, Seq("exec", "-t", "compile") ++ quietFlags*)
      assertEquals(warm.exitCode, 0, warm.err.text())

      def classBytes(module: String): Map[String, Seq[Byte]] = {
        val dir = relocated / ".deder/out" / module / "compile/classes"
        os.walk(dir).filter(f => os.isFile(f) && f.ext == "class")
          .map(f => f.relativeTo(dir).toString -> os.read.bytes(f).toSeq).toMap
      }
      val modules = Seq("common", "frontend", "backend", "uber")
      val before = modules.map(m => m -> classBytes(m)).toMap
      assert(before.values.forall(_.nonEmpty), "Expected class files for all modules")

      // implementation-only change: public API of Common stays the same
      val commonSrc = relocated / "common/src/Common.scala"
      os.write.over(commonSrc, os.read(commonSrc).replace("komon1", "komon2"))
      Thread.sleep(500)
      val res = executeDederCommand(relocated, Seq("exec", "-t", "compile") ++ quietFlags*)
      assertEquals(res.exitCode, 0, res.err.text())

      val after = modules.map(m => m -> classBytes(m)).toMap
      assertNotEquals(after("common"), before("common"), "common should have been recompiled")
      for m <- Seq("frontend", "backend", "uber") do
        assertEquals(after(m), before(m), s"$m should not be recompiled for an API-neutral change in common")
    } finally cleanup(original, relocated)
  }

  private def leakedFiles(dir: os.Path, needle: String): Seq[os.Path] =
    os.walk(dir).filter(os.isFile).filter { f =>
      val name = f.last
      if name.endsWith(".zip") || name.endsWith(".jar") then zipContains(f, needle)
      else os.read.bytes(f).containsSlice(needle.getBytes("UTF-8"))
    }

  private def zipContains(file: os.Path, needle: String): Boolean = {
    val bytes = needle.getBytes("UTF-8")
    try
      val zip = new ZipFile(file.toIO)
      try
        zip.entries().asScala.exists { e =>
          e.getName.contains(needle) || (!e.isDirectory && zip.getInputStream(e).readAllBytes().containsSlice(bytes))
        }
      finally zip.close()
    catch
      // not a valid zip (e.g. empty/partial file): fall back to a raw byte scan
      case _: java.util.zip.ZipException | _: java.io.IOException =>
        os.read.bytes(file).containsSlice(bytes)
  }
}
