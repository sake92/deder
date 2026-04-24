package ba.sake.deder.testing

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
      failedTestNames = results.flatMap(_._2.failedTestNames)
    )
    val separator = "═" * 50
    notifications.add(ServerNotification.logInfo(separator))
    val statusIcon = if totalResults.success then "PASS ✅" else "FAIL \uD83D\uDD34"
    notifications.add(
      ServerNotification.logInfo(
        s"$statusIcon Test Summary: ${totalResults.total} total, ${totalResults.passed} passed, " +
          s"${totalResults.failed} failed, ${totalResults.errors} errors, ${totalResults.skipped} skipped"
      )
    )
    results.foreach { case (moduleId, res) =>
      val icon = if res.success then "  PASS ✅" else "  FAIL \uD83D\uDD34"
      val detail = Option.when(!res.success) {
        Seq(
          Option.when(res.failed > 0)(s"${res.failed} failed"),
          Option.when(res.errors > 0)(s"${res.errors} errors")
        ).flatten.mkString(", ")
      }
      notifications.add(ServerNotification.logInfo(s"$icon $moduleId${detail.map(d => s" ($d)").getOrElse("")}"))
      res.failedTestNames.foreach { testName =>
        notifications.add(ServerNotification.logInfo(s"       - $testName"))
      }
    }
    notifications.add(ServerNotification.logInfo(separator))
  }
}
