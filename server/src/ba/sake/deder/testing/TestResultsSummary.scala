package ba.sake.deder.testing

import java.time.Duration
import ba.sake.deder.{ModuleFailure, ServerNotification, ServerNotificationsLogger, PlainTextWritable, Summarizable}
import ba.sake.tupson.JsonRW

case class TestResultsSummary(
    success: Boolean,
    suitesTotal: Int,
    suitesFailed: Int,
    suitesPassed: Int,
    testsTotal: Int,
    testsFailed: Int,
    testsSkipped: Int,
    testsPassed: Int,
    duration: Long,
    modules: Map[String, DederTestResults]
) derives JsonRW

object TestResultsSummary {

  given PlainTextWritable[TestResultsSummary] with {
    def write(summary: TestResultsSummary): String = {
      val statusIcon = if summary.success then "✅ PASS" else "🔴 FAIL"
      val suitesStr = renderCounts(summary.suitesPassed, summary.suitesFailed, 0, summary.suitesTotal)
      val testsStr = renderCounts(summary.testsPassed, summary.testsFailed, summary.testsSkipped, summary.testsTotal)
      val timeStr = Duration.ofMillis(summary.duration).toPrettyString
      val summaryLine = s"$statusIcon  Suites: $suitesStr  │  Tests: $testsStr  │  $timeStr"
      val separator = "═" * summaryLine.length
      // render successful modules first, then failed
      val successfulModules =
        summary.modules.filter { case (_, res) => res.success }.toSeq.sortBy(_._1)
      val successfulModulesSummary = successfulModules
        .map { case (moduleId, res) => s"  ✅ PASS $moduleId" }
        .mkString("\n")
      val failedModules = summary.modules.filter { case (_, res) => !res.success }.toSeq.sortBy(_._1)
      val failedModulesSummary = failedModules
        .map { case (moduleId, res) =>
          val failedTestsSummary = res.failedTestNames
            .map(testName => s"       - $testName")
            .mkString("\n")
          Seq(s"  🔴 FAIL $moduleId (${res.failed} failed tests)", failedTestsSummary)
            .filter(_.trim.nonEmpty)
            .mkString("\n")
        }
        .mkString("\n")

      Seq(
        separator,
        summaryLine,
        successfulModulesSummary,
        failedModulesSummary,
        separator
      ).filter(_.trim.nonEmpty).mkString("\n")
    }
  }

  private def renderCounts(passed: Int, failed: Int, skipped: Int, total: Int): String = {
    val parts = Seq(
      Option.when(passed > 0)(s"$passed passed"),
      Option.when(failed > 0)(s"$failed failed"),
      Option.when(skipped > 0)(s"$skipped skipped")
    ).flatten
    if parts.size == 1 && passed == total then parts.head
    else (parts.appended(s"$total total")).mkString(", ")
  }

  given Summarizable[DederTestResults, TestResultsSummary] with
    def summarize(resultsMap: Seq[(String, DederTestResults)], failures: Seq[ModuleFailure]): TestResultsSummary = {
      val allResults = resultsMap.map(_._2)
      TestResultsSummary(
        success = allResults.forall(_.success),
        suitesTotal = allResults.map(_.suitesTotal).sum,
        suitesFailed = allResults.map(_.suitesFailed).sum,
        suitesPassed = allResults.map(_.suitesPassed).sum,
        testsTotal = allResults.map(_.total).sum,
        testsFailed = allResults.map(_.failed).sum,
        testsSkipped = allResults.map(_.skipped).sum,
        testsPassed = allResults.map(_.passed).sum,
        duration = allResults.map(_.duration).sum,
        modules = resultsMap.toMap
      )
    }

}
