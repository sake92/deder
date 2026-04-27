package ba.sake.deder.testing

import java.time.Duration
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

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
    val separator = "═" * 50
    val statusIcon = if totalResults.success then "✅ PASS" else "🔴 FAIL"
    val totalFailed = totalResults.failed + totalResults.errors
    val suitesStr = renderCounts(totalResults.suitesPassed, totalResults.suitesFailed, 0, totalResults.suitesTotal)
    val testsStr = renderCounts(totalResults.passed, totalFailed, totalResults.skipped, totalResults.total)
    val timeStr = Duration.ofMillis(totalResults.duration).toPrettyString
    notifications.add(ServerNotification.logInfo(separator))
    notifications.add(
      ServerNotification.logInfo(
        s"$statusIcon  Suites: $suitesStr  │  Tests: $testsStr  │  $timeStr"
      )
    )
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
}
