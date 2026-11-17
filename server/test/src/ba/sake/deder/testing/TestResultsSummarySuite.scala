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

  test("reports errors separately and emits fully qualified rerun plans") {
    val testCases = Seq(
      DederTestCaseReport(
        name = "returns value",
        classname = "example.MySuite",
        status = DederTestStatus.Failure,
        duration = 10L
      ),
      DederTestCaseReport(
        name = "example.MySuite.throws unexpectedly",
        classname = "example.MySuite",
        status = DederTestStatus.Error,
        duration = 20L
      ),
      DederTestCaseReport(
        name = "passes",
        classname = "example.MySuite",
        status = DederTestStatus.Success,
        duration = 5L
      )
    )
    val results = DederTestResults(
      total = testCases.size,
      passed = 1,
      failed = 1,
      errors = 1,
      skipped = 0,
      duration = testCases.map(_.duration).sum,
      suites = Seq(
        DederTestSuiteReport(
          name = "example.MySuite",
          testCases = testCases,
          duration = testCases.map(_.duration).sum
        )
      )
    )
    val summary = summon[Summarizable[DederTestResults, TestResultsSummary]].summarize(
      resultsMap = Seq("app-test" -> results),
      failures = Seq.empty,
      totalDuration = Duration.ofSeconds(2)
    )

    assertEquals(summary.testsFailed, 1)
    assertEquals(summary.testsErrors, 1)
    assertEquals(
      summary.rerunPlans,
      Seq(
        TestRerunPlan(
          moduleId = "app-test",
          selectors = Seq(
            "example.MySuite#returns value",
            "example.MySuite#throws unexpectedly"
          )
        )
      )
    )

    val output = summon[PlainTextWritable[TestResultsSummary]].write(summary)
    assert(output.contains("1 failed, 1 errored"))
    assert(output.contains("example.MySuite#returns value"))
    assert(output.contains("example.MySuite#throws unexpectedly"))
    assert(output.contains("""deder exec -t test -m app-test "example.MySuite#returns value" "example.MySuite#throws unexpectedly""""))
  }

  test("uses the suite name for suite-level failures and omits reruns on success") {
    val suiteName = "example.SuiteLevelFailure"
    val failedResults = DederTestResults(
      total = 1,
      passed = 0,
      failed = 1,
      errors = 0,
      skipped = 0,
      duration = 1L,
      suites = Seq(
        DederTestSuiteReport(
          name = suiteName,
          testCases = Seq(
            DederTestCaseReport(
              name = suiteName,
              classname = suiteName,
              status = DederTestStatus.Failure,
              duration = 1L
            )
          ),
          duration = 1L
        )
      )
    )
    val failingSummary = summon[Summarizable[DederTestResults, TestResultsSummary]].summarize(
      resultsMap = Seq("app-test" -> failedResults),
      failures = Seq.empty,
      totalDuration = Duration.ofSeconds(1)
    )
    assertEquals(failingSummary.rerunPlans.map(_.selectors), Seq(Seq(suiteName)))

    val passingSummary = summon[Summarizable[DederTestResults, TestResultsSummary]].summarize(
      resultsMap = Seq("app-test" -> DederTestResults.empty),
      failures = Seq.empty,
      totalDuration = Duration.ofSeconds(1)
    )
    assertEquals(passingSummary.rerunPlans, Seq.empty)
    assert(!summon[PlainTextWritable[TestResultsSummary]].write(passingSummary).contains("Re-run:"))
  }
}
