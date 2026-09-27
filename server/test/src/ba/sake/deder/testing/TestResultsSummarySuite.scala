package ba.sake.deder.testing

import java.time.Duration
import ba.sake.deder.{ModuleFailure, PlainTextWritable, Summarizable}

class TestResultsSummarySuite extends munit.FunSuite {

  test("includes skipped modules in a failed cross-module test summary") {
    val summary = summon[Summarizable[DederTestResults, TestResultsSummary]].summarize(
      resultsMap = Seq("passing" -> DederTestResults.empty),
      failures = Seq(ModuleFailure("native", "native", Some("native"))),
      totalDuration = Duration.ofSeconds(1)
    )

    assert(!summary.success)
    assertEquals(summary.failures.map(_.moduleId), Seq("native"))
    val output = summon[PlainTextWritable[TestResultsSummary]].write(summary)
    assert(output.contains("🔴 FAILED"))
    assert(output.contains("✅ PASSED passing"))
    assert(output.contains("⏭️  SKIPPED native (native failed)"))
  }
}
