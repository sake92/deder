package ba.sake.deder.testing

import java.time.Duration
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger, PlainTextWritable, Summarizable}
import ba.sake.tupson.JsonRW

case class TestSummary(
    total: Int,
    passed: Int,
    failed: Int,
    errors: Int,
    skipped: Int,
    duration: Long,
    success: Boolean,
    failedTestNames: Seq[String]
) derives JsonRW

object TestSummary:
  given PlainTextWritable[TestSummary] with
    def write(s: TestSummary): String =
      val statusIcon = if s.success then "PASS" else "FAIL"
      val totalFailed = s.failed + s.errors
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      parts += s"${s.passed} passed"
      if totalFailed > 0 then parts += s"$totalFailed failed"
      if s.skipped > 0 then parts += s"${s.skipped} skipped"
      parts += s"${s.total} total"
      val timeStr = java.time.Duration.ofMillis(s.duration).toString
        .replace("PT", "").replace("S", "s").replace("M", "m").replace("H", "h").toLowerCase
      s"$statusIcon: ${parts.mkString(", ")} | $timeStr"

object TestResultsSummary {
  def summarize(
      results: Seq[(String, DederTestResults)],
      notifications: ServerNotificationsLogger
  ): Unit = {
    val totalResults = DederTestResults(
      total = results.map(_._2.total).sum,
      passed = results.map(_._2.passed).sum,
      failed = results.map(_._2.failed).sum,
      errors = results.map(_._2.errors).sum,
      skipped = results.map(_._2.skipped).sum,
      duration = results.map(_._2.duration).sum,
      failedTestNames = results.flatMap(_._2.failedTestNames),
      suites = results.flatMap(_._2.suites).sortBy(_.name)
    )
    val statusIcon = if totalResults.success then "✅ PASS" else "🔴 FAIL"
    val totalFailed = totalResults.failed + totalResults.errors
    val suitesStr = renderCounts(totalResults.suitesPassed, totalResults.suitesFailed, 0, totalResults.suitesTotal)
    val testsStr = renderCounts(totalResults.passed, totalFailed, totalResults.skipped, totalResults.total)
    val timeStr = Duration.ofMillis(totalResults.duration).toPrettyString
    val summaryLine = s"$statusIcon  Suites: $suitesStr  │  Tests: $testsStr  │  $timeStr"
    val separator = "═" * summaryLine.length
    notifications.add(ServerNotification.logInfo(separator))
    notifications.add(ServerNotification.logInfo(summaryLine))
    val interesting = results.filter { case (_, res) =>
      val moduleFailed = res.failed + res.errors
      moduleFailed > 0 || res.skipped > 0
    }
    val (skippedOnly, hasFailed) = interesting.partition { case (_, res) =>
      val moduleFailed = res.failed + res.errors
      moduleFailed == 0
    }
    (skippedOnly ++ hasFailed).foreach { case (moduleId, res) =>
      val moduleFailed = res.failed + res.errors
      val icon = if res.success then "  ✅" else "  🔴"
      val detail = Seq(
        Option.when(moduleFailed > 0)(s"$moduleFailed failed"),
        Option.when(res.skipped > 0)(s"${res.skipped} skipped")
      ).flatten.mkString(", ")
      notifications.add(ServerNotification.logInfo(s"$icon $moduleId ($detail)"))
      res.failedTestNames.foreach { testName =>
        notifications.add(ServerNotification.logInfo(s"       - $testName"))
      }
    }
    notifications.add(ServerNotification.logInfo(separator))
  }

  private def renderCounts(passed: Int, failed: Int, skipped: Int, total: Int): String = {
    val parts = Seq(
      Option.when(passed > 0)(s"$passed passed"),
      Option.when(failed > 0)(s"$failed failed"),
      Option.when(skipped > 0)(s"$skipped skipped")
    ).flatten
    if parts.size == 1 && passed == total then parts.head
    else (parts :+ s"$total total").mkString(", ")
  }

  given Summarizable[DederTestResults, TestSummary] with
    def summarize(results: Seq[(String, DederTestResults)]): TestSummary =
      val merged = DederTestResults(
        total = results.map(_._2.total).sum,
        passed = results.map(_._2.passed).sum,
        failed = results.map(_._2.failed).sum,
        errors = results.map(_._2.errors).sum,
        skipped = results.map(_._2.skipped).sum,
        duration = results.map(_._2.duration).sum,
        failedTestNames = results.flatMap(_._2.failedTestNames),
        suites = results.flatMap(_._2.suites).sortBy(_.name)
      )
      TestSummary(
        total = merged.total,
        passed = merged.passed,
        failed = merged.failed,
        errors = merged.errors,
        skipped = merged.skipped,
        duration = merged.duration,
        success = merged.success,
        failedTestNames = merged.failedTestNames
      )
}
