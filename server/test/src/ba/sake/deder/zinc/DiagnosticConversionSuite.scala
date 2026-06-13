package ba.sake.deder.zinc

import ba.sake.deder.{CompileSeverity, CompileRange, DederPath}
import java.util.Optional
import xsbti.{Problem, Position, Severity as XSeverity}

class DiagnosticConversionSuite extends munit.FunSuite {

  // minimal xsbti.Position stub (1-indexed line/column, like scalac)
  private def pos(file: java.io.File, sl: Int, sc: Int, el: Int, ec: Int): Position =
    new Position {
      override def line(): Optional[Integer] = Optional.of(sl)
      override def lineContent(): String = ""
      override def offset(): Optional[Integer] = Optional.empty()
      override def pointer(): Optional[Integer] = Optional.empty()
      override def pointerSpace(): Optional[String] = Optional.empty()
      override def sourcePath(): Optional[String] = Optional.of(file.getPath)
      override def sourceFile(): Optional[java.io.File] = Optional.of(file)
      override def startLine(): Optional[Integer] = Optional.of(sl)
      override def startColumn(): Optional[Integer] = Optional.of(sc)
      override def endLine(): Optional[Integer] = Optional.of(el)
      override def endColumn(): Optional[Integer] = Optional.of(ec)
    }

  private def problem(p: Position, msg: String, sev: XSeverity, cat: String): Problem =
    new Problem {
      override def category(): String = cat
      override def severity(): XSeverity = sev
      override def message(): String = msg
      override def position(): Position = p
    }

  test("converts a warning problem with 1->0 indexed range") {
    val root = os.temp.dir()
    val srcAbs = root / "src" / "Foo.scala"
    os.makeDir.all(srcAbs / os.up)
    os.write.over(srcAbs, "")
    val p = problem(pos(srcAbs.toIO, 3, 5, 3, 9), "deprecated", XSeverity.Warn, "deprecation")

    val result = DiagnosticConversion.groupByFile(
      problems = Seq(p),
      allSources = Seq(srcAbs),
      projectRoot = root
    )

    val key = DederPath(srcAbs.subRelativeTo(root))
    assertEquals(result(key).size, 1)
    val d = result(key).head
    assertEquals(d.severity, CompileSeverity.Warning)
    assertEquals(d.message, "deprecated")
    assertEquals(d.code, Some("deprecation"))
    // Zinc is 1-indexed for lines, deder CompileDiagnostic 0-indexed
    assertEquals(d.range, CompileRange(startLine = 2, startChar = 5, endLine = 2, endChar = 9))
  }

  test("clean source files appear as keys with empty lists") {
    val root = os.temp.dir()
    val cleanAbs = root / "src" / "Clean.scala"
    os.makeDir.all(cleanAbs / os.up)
    os.write.over(cleanAbs, "")

    val result = DiagnosticConversion.groupByFile(Seq.empty, Seq(cleanAbs), root)
    val key = DederPath(cleanAbs.subRelativeTo(root))
    assertEquals(result.get(key), Some(Nil))
  }
}
