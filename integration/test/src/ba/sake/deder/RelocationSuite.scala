package ba.sake.deder

import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

class RelocationSuite extends BaseIntegrationSuite {

  test("relocation: copied project (with .deder/out) is a full compile cache hit") {
    val props = Map("logLevel" -> "DEBUG")
    val original = stagedServerProject("sample-projects/multi", props, dirSuffix = s"reloc-src-${System.nanoTime()}")
    val relocated = os.pwd / "tmp" / s"multi-reloc-dst-${System.nanoTime()}"
    try {
      val first = executeDederCommand(original, "exec", "-t", "compile", "-m", "frontend", "--log-level", "warning", "--no-color")
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

      val second = executeDederCommand(relocated, "exec", "-t", "compile", "-m", "frontend", "--log-level", "warning", "--no-color")
      assertEquals(second.exitCode, 0, second.err.text())
      val lines = os.read.lines(relocated / ".deder/logs/server.log")
      def usedCache(l: String) = l.trim.endsWith("Using cached result for compile")
      def recomputed(l: String) = l.trim.endsWith("Computed result for compile")
      assert(
        !lines.exists(recomputed),
        s"compile should be a cache hit after relocation. compile lines:\n${lines.filter(l => usedCache(l) || recomputed(l)).mkString("\n")}"
      )
      assert(lines.exists(usedCache), "Expected 'Using cached result for compile' after relocation")
    } finally {
      executeDederCommand(original, "shutdown")
      executeDederCommand(relocated, "shutdown")
    }
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
