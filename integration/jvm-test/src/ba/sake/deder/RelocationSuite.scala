package ba.sake.deder

import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

class RelocationSuite extends BaseIntegrationSuite {

  private val quietFlags = Seq("--log-level", "warning", "--no-color")
  private val props = Map("logLevel" -> "DEBUG")

  /** Exercise both a compile-only cache and a cache populated by running the application. */
  private def compileAndRelocate(
      warmRuntime: Boolean = false,
      keepOriginal: Boolean = false,
      scalaVersion: String = "3.7.1",
      withWarning: Boolean = false
  ): (os.Path, os.Path) = {
    val original = stagedServerProject("sample-projects/multi", props, dirSuffix = s"reloc-src-${System.nanoTime()}")
    val relocated = os.pwd / "tmp" / s"multi-reloc-dst-${System.nanoTime()}"
    val config = original / "deder.pkl"
    os.write.over(config, os.read(config).replace("3.7.1", scalaVersion))
    // One changed source must not recompile this unrelated source in the same module.
    os.write(original / "common/src/Independent.scala", "package common\nobject Independent { val value = 17 }\n")
    os.write(
      original / "common/src/JHelper.java",
      "package common; public class JHelper { public static String value() { return \"java1\"; } }\n"
    )
    val main = original / "uber/src/Main.scala"
    os.write.over(
      main,
      os.read(main)
        .replace(
          "println(frontend.Frontend.value)",
          "println(frontend.Frontend.value)\n    println(common.JHelper.value())"
        )
    )
    if withWarning then {
      os.write.over(
        config,
        os.read(config).replace("publish = false", "publish = false\n  scalacOptions { \"-deprecation\" }")
      )
      os.write(
        original / "common/src/Warning.scala",
        "package common\nobject Warning { def year = new java.util.Date().getYear() }\n"
      )
    }
    // flags must come AFTER -t/-m, otherwise those are ignored
    val first = executeDederCommand(original, Seq("exec", "-t", "compile") ++ quietFlags*)
    assertEquals(first.exitCode, 0, first.err.text())
    if warmRuntime then {
      val warmed = executeDederCommand(original, Seq("exec", "-m", "uber", "-t", "runClasspath") ++ quietFlags*)
      assertEquals(warmed.exitCode, 0, warmed.err.text())
    }
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
    if !keepOriginal then os.remove.all(original)
    (original, relocated)
  }

  private def cleanup(original: os.Path, relocated: os.Path): Unit = {
    for project <- Seq(original, relocated).distinct if os.exists(project) do
      val stopped = executeDederCommand(project, "shutdown")
      assertEquals(stopped.exitCode, 0, stopped.err.text())
    Thread.sleep(1000)
  }

  private def serverLog(project: os.Path): Seq[String] =
    val log = project / ".deder/logs/server.log"
    if os.exists(log) then os.read.lines(log) else Seq.empty

  private def compile(project: os.Path): os.CommandResult =
    executeDederCommand(project, Seq("exec", "-t", "compile") ++ quietFlags*)

  private def compileSuccessfully(project: os.Path): Unit = {
    val result = compile(project)
    assertEquals(result.exitCode, 0, result.err.text())
  }

  private def assertRuntime(project: os.Path, value: String, javaValue: String = "java1"): Unit = {
    val result = executeDederCommand(project, Seq("exec", "-m", "uber", "-t", "run") ++ quietFlags*)
    assertEquals(result.exitCode, 0, result.err.text())
    assert(result.out.text().contains(s"frontend + $value"), result.out.text())
    assert(result.out.text().contains(s"backend + $value"), result.out.text())
    assert(result.out.text().linesIterator.contains(javaValue), result.out.text())
  }

  private def edit(project: os.Path, source: String, before: String, after: String): Unit = {
    val file = project / os.RelPath(source)
    val contents = os.read(file)
    assert(contents.contains(before), s"Missing edit target in $file")
    os.write.over(file, contents.replace(before, after))
    Thread.sleep(500)
  }

  private def assertNoop(project: os.Path): Unit = {
    val offset = serverLog(project).size
    compileSuccessfully(project)
    val lines = serverLog(project).drop(offset)
    assert(!lines.exists(_.contains("Starting compilation")), lines.mkString("\n"))
    assert(lines.exists(_.trim.endsWith("Using cached result for compile")), lines.mkString("\n"))
  }

  test("relocation: copied project (with .deder/out) is a full cache hit for all modules") {
    val (original, relocated) = compileAndRelocate()
    try {
      // tasks that persisted a metadata.json in the first run are the cached ones
      val cachedTasks = os
        .walk(relocated / ".deder/out", maxDepth = 3)
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
        os.walk(dir)
          .filter(f => os.isFile(f) && f.ext == "class")
          .map(f => f.relativeTo(dir).toString -> os.read.bytes(f).toSeq)
          .toMap
      }
      val modules = Seq("common", "frontend", "backend", "uber")
      val before = modules.map(m => m -> classBytes(m)).toMap
      assert(before.values.forall(_.nonEmpty), "Expected class files for all modules")

      // implementation-only change: public API of Common stays the same
      val commonSrc = relocated / "common/src/Common.scala"
      os.write.over(commonSrc, os.read(commonSrc).replace("komon1", "komon2"))
      Thread.sleep(500)
      val offset = serverLog(relocated).size
      val res = executeDederCommand(relocated, Seq("exec", "-t", "compile") ++ quietFlags*)
      assertEquals(res.exitCode, 0, res.err.text())

      // Byte equality alone cannot distinguish a skipped compile from identical re-emitted bytes.
      val compiling = serverLog(relocated).drop(offset).filter(_.contains("] compiling "))
      assertEquals(compiling.size, 1, compiling.mkString("\n"))
      assert(compiling.head.contains("[common] compiling 1 Scala source to "), compiling.head)

      val after = modules.map(m => m -> classBytes(m)).toMap
      assertNotEquals(after("common"), before("common"), "common should have been recompiled")
      for m <- Seq("frontend", "backend", "uber") do
        assertEquals(after(m), before(m), s"$m should not be recompiled for an API-neutral change in common")
      assertRuntime(relocated, "komon2")
      assertNoop(relocated)
    } finally cleanup(original, relocated)
  }

  test("relocation: API changes fail dependents and recover without a clean") {
    val (original, relocated) = compileAndRelocate()
    try {
      edit(relocated, "common/src/Common.scala", "val value", "val renamed")
      val broken = compile(relocated)
      assertNotEquals(broken.exitCode, 0, broken.err.text())
      assert(broken.err.text().contains("value is not a member"), broken.err.text())
      assert(broken.err.text().contains("Frontend.scala"), broken.err.text())
      assert(broken.err.text().contains("Backend.scala"), broken.err.text())
      edit(relocated, "common/src/Common.scala", "val renamed = \"komon1\"", "val value = 42")
      compileSuccessfully(relocated)
      assertRuntime(relocated, "42")
      assertNoop(relocated)
    } finally cleanup(original, relocated)
  }

  test("relocation: source add, rename and deletion remove classes and TASTy APIs") {
    val (original, relocated) = compileAndRelocate()
    try {
      val added = relocated / "common/src/Added.scala"
      val renamed = relocated / "common/src/Renamed.scala"
      val classes = relocated / ".deder/out/common/compile/classes/common"
      os.write(added, "package common\nobject Added { val value = 1 }\n")
      compileSuccessfully(relocated)
      assert(os.exists(classes / "Added.class"))
      assert(os.exists(classes / "Added.tasty"))
      os.move(added, renamed)
      compileSuccessfully(relocated)
      os.remove(renamed)
      compileSuccessfully(relocated)
      for name <- Seq("Added.class", "Added$.class", "Added.tasty") do
        assert(!os.exists(classes / name), s"Stale product: $name")

      // A stale Common.tasty used to let dependents compile successfully, then fail at runtime.
      val common = relocated / "common/src/Common.scala"
      val source = os.read(common)
      os.remove(common)
      val deleted = compile(relocated)
      assertNotEquals(deleted.exitCode, 0, deleted.err.text())
      assert(!os.exists(classes / "Common.tasty"))
      os.write(common, source)
      compileSuccessfully(relocated)
      assertRuntime(relocated, "komon1")
      assertNoop(relocated)
    } finally cleanup(original, relocated)
  }

  test("relocation: warmed runtime classpaths follow copies and repeated moves") {
    val (original, relocated) = compileAndRelocate(warmRuntime = true, keepOriginal = true)
    val second = os.pwd / "tmp" / s"multi-reloc-second-${System.nanoTime()}"
    try {
      assertNoop(relocated)
      edit(relocated, "common/src/Common.scala", "komon1", "copied-value")
      compileSuccessfully(relocated)
      // The original still exists: stale classpaths must not silently run its old classes.
      assertRuntime(relocated, "copied-value")
      assert(os.read(original / "common/src/Common.scala").contains("komon1"))
      cleanup(original, relocated)
      os.move(relocated, second)
      os.remove.all(original)
      assertNoop(second)
      edit(second, "common/src/Common.scala", "copied-value", "second-copy")
      compileSuccessfully(second)
      assertRuntime(second, "second-copy")
      assertNoop(second)
    } finally {
      cleanup(original, relocated)
      if os.exists(second) then cleanup(second, second)
    }
  }

  test("relocation: Scala 2 SemanticDB options and incremental analysis survive a copy") {
    val (original, relocated) = compileAndRelocate(scalaVersion = "2.13.16")
    try {
      assertNoop(relocated)
      edit(relocated, "common/src/Common.scala", "komon1", "scala2-copy")
      compileSuccessfully(relocated)
      assertRuntime(relocated, "scala2-copy")
      assertNoop(relocated)
    } finally cleanup(original, relocated)
  }

  test("relocation: mixed Java edits compile and run the copied classes") {
    val (original, relocated) = compileAndRelocate()
    try {
      edit(relocated, "common/src/JHelper.java", "java1", "java2")
      val offset = serverLog(relocated).size
      compileSuccessfully(relocated)
      val compiling = serverLog(relocated).drop(offset).filter(_.contains("] compiling "))
      assertEquals(compiling.size, 1, compiling.mkString("\n"))
      assert(compiling.head.contains("[common] compiling 1 Java source to "), compiling.head)
      assertRuntime(relocated, "komon1", javaValue = "java2")
      assertNoop(relocated)
    } finally cleanup(original, relocated)
  }

  test("relocation: warning positions and compiler-option failure recovery follow the copy") {
    val (original, relocated) = compileAndRelocate(withWarning = true)
    try {
      assertNoop(relocated)
      edit(relocated, "deder.pkl", "\"-deprecation\"", "\"-deprecation\"; \"-Werror\"")
      val fatalWarnings = compile(relocated)
      assertNotEquals(fatalWarnings.exitCode, 0, fatalWarnings.err.text())
      assert(fatalWarnings.err.text().contains("Warning.scala"), fatalWarnings.err.text())
      edit(relocated, "deder.pkl", "\"-deprecation\"; \"-Werror\"", "\"-deprecation\"")
      compileSuccessfully(relocated)
      edit(relocated, "common/src/Common.scala", "komon1", "warning-copy")
      compileSuccessfully(relocated)
      val cached = compile(relocated)
      assertEquals(cached.exitCode, 0, cached.err.text())
      assert(cached.err.text().contains("common/src/Warning.scala"), cached.err.text())
      assert(!cached.err.text().contains(original.toString), cached.err.text())
      assertRuntime(relocated, "warning-copy")
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
    try {
      val zip = new ZipFile(file.toIO)
      try {
        zip.entries().asScala.exists { e =>
          if e.getName.contains(needle) then true
          else if e.isDirectory then false
          else {
            val stream = zip.getInputStream(e)
            try stream.readAllBytes().containsSlice(bytes)
            finally stream.close()
          }
        }
      } finally zip.close()
    } catch {
      // not a valid zip (e.g. empty/partial file): fall back to a raw byte scan
      case _: java.util.zip.ZipException | _: java.io.IOException =>
        os.read.bytes(file).containsSlice(bytes)
    }
  }
}
