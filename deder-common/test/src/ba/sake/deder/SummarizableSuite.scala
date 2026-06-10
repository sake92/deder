package ba.sake.deder

import java.time.Duration

class SummarizableSuite extends munit.FunSuite {

  val summarizable = summon[Summarizable[String, MultiModuleResults[String]]]
  val ptw = summon[PlainTextWritable[MultiModuleResults[String]]]

  test("all success - boxed format with header and separator") {
    val summary = summarizable.summarize(
      results = Seq("server" -> "body1", "client" -> "body2"),
      failures = Seq.empty,
      totalDuration = Duration.ofSeconds(3)
    )

    val rendered = ptw.write(summary)

    val lines = rendered.split("\n")
    assertEquals(lines(1), "✅ OK  2 modules", "header should show OK with module count")
    assert(lines.exists(_.contains("✅ server")), "should show successful server module")
    assert(lines.exists(_.contains("✅ client")), "should show successful client module")
    // Separator lines should all have the same width (longest line determines width)
    assertEquals(lines(0), "═" * lines.map(_.length).max, "first line should be separator matching max line width")
    assertEquals(lines.last, "═" * lines.map(_.length).max, "last line should be separator matching max line width")
    assert(!rendered.contains("Failed modules"), "should not have old 'Failed modules:' text")
    assert(lines.exists(_.contains("body1")), "should include body text from per-module results (indented)")
    assert(lines.exists(_.contains("body2")), "should include body text from per-module results (indented)")
  }

  test("mixed success and failure - failures listed last inside box") {
    val summary = summarizable.summarize(
      results = Seq("server" -> "body1"),
      failures = Seq(
        ModuleFailure("client", "compilation failed", None),
        ModuleFailure("app", "Skipped - client failed", Some("client"))
      ),
      totalDuration = Duration.ofSeconds(1)
    )

    val rendered = ptw.write(summary)

    val lines = rendered.split("\n")
    assertEquals(lines(1), "🔴 FAIL  3 modules", "header should show FAIL with total module count")

    val serverIdx = lines.indexWhere(_.contains("✅ server"))
    val clientIdx = lines.indexWhere(_.contains("🔴 client"))
    val appIdx = lines.indexWhere(_.contains("🔴 app"))
    assert(serverIdx > 0 && clientIdx > 0 && appIdx > 0, "all module lines should be present")
    assert(serverIdx < clientIdx, "successful server should come before failed client")
    assert(serverIdx < appIdx, "successful server should come before failed app")

    assert(lines.exists(_.contains("compilation failed")), "should include error message")
    assert(lines.exists(_.contains("caused by failure in client")), "should include causedBy info")

    assertEquals(lines(0), "═" * lines.map(_.length).max, "separator should match max line width")
    assertEquals(lines.last, "═" * lines.map(_.length).max, "bottom separator should match max line width")
  }

  test("all failures - no success section") {
    val summary = summarizable.summarize(
      results = Seq.empty,
      failures = Seq(
        ModuleFailure("server", "compilation failed", None)
      ),
      totalDuration = Duration.ofSeconds(1)
    )

    val rendered = ptw.write(summary)

    val lines = rendered.split("\n")
    assertEquals(lines(1), "🔴 FAIL  1 module", "header should show FAIL with module count")
    assert(lines.exists(_.contains("🔴 server")), "should show failed server module")
    assert(lines.exists(_.contains("compilation failed")), "should include error message")
    assertEquals(lines(0), lines.last, "top and bottom separators should be equal")
    assertEquals(lines(0), "═" * lines.map(_.length).max, "separator width should match max line width")
  }

  test("modules sorted alphabetically - successes first, then failures") {
    val summary = summarizable.summarize(
      results = Seq("banana" -> "x", "apple" -> "x"),
      failures = Seq(
        ModuleFailure("dog", "error", None),
        ModuleFailure("cat", "error", None)
      ),
      totalDuration = Duration.ofSeconds(1)
    )

    val rendered = ptw.write(summary)

    val lines = rendered.split("\n")
    // Module lines are indented with "  " and start with ✅ or 🔴
    val moduleLines = lines.filter(l => l.startsWith("  ✅") || l.startsWith("  🔴"))
    assertEquals(moduleLines(0), "  ✅ apple")
    assertEquals(moduleLines(1), "  ✅ banana")
    assertEquals(moduleLines(2), "  🔴 cat: error")
    assertEquals(moduleLines(3), "  🔴 dog: error")
  }

  test("single module success - uses singular 'module'") {
    val summary = summarizable.summarize(
      results = Seq("myapp" -> "done"),
      failures = Seq.empty,
      totalDuration = Duration.ofMillis(500)
    )

    val rendered = ptw.write(summary)

    assertEquals(rendered.split("\n")(1), "✅ OK  1 module", "header should use singular 'module'")
  }

  test("body text is included indented below module status") {
    val summary = summarizable.summarize(
      results = Seq("server" -> "some/output/path"),
      failures = Seq.empty,
      totalDuration = Duration.ofSeconds(1)
    )

    val rendered = ptw.write(summary)

    val lines = rendered.split("\n")
    val serverIdx = lines.indexWhere(_.contains("✅ server"))
    assert(serverIdx > 0, "server module line should be present")
    // Body text should be on the next line, indented
    assert(lines(serverIdx + 1).contains("some/output/path"), "body text should follow module line")
    assert(lines(serverIdx + 1).startsWith("         "), "body text should be indented")
  }

  test("empty body text is not shown") {
    val summary = summarizable.summarize(
      results = Seq("server" -> ""),
      failures = Seq.empty,
      totalDuration = Duration.ofSeconds(1)
    )

    val rendered = ptw.write(summary)

    val lines = rendered.split("\n")
    // Only header, one module line, and two separators = 4 lines
    assertEquals(lines.length, 4, "should have header + 1 module + 2 separators = 4 lines")
  }
}
