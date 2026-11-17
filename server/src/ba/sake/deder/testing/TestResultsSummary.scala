package ba.sake.deder.testing

import java.time.Duration
import ba.sake.deder.{ModuleFailure, ServerNotification, ServerNotificationsLogger, PlainTextWritable, Summarizable}
import ba.sake.deder.toPrettyString
import ba.sake.tupson.JsonRW

case class TestResultsSummary(
    success: Boolean,
    suitesTotal: Int,
    suitesFailed: Int,
    suitesPassed: Int,
    testsTotal: Int,
    testsFailed: Int,
    testsErrors: Int,
    testsSkipped: Int,
    testsPassed: Int,
    duration: Long,
    totalDurationMillis: Long,
    modules: Map[String, DederTestResults],
    rerunPlans: Seq[TestRerunPlan] = Seq.empty,
    failures: Seq[ModuleFailure] = Seq.empty
) derives JsonRW

case class TestRerunPlan(
    moduleId: String,
    selectors: Seq[String]
) derives JsonRW

object TestResultsSummary {

  given PlainTextWritable[TestResultsSummary] with {
    def write(summary: TestResultsSummary): String = {
      val statusIcon = if summary.success then "✅ PASSED" else "🔴 FAILED"
      val suitesStr = renderCounts(summary.suitesPassed, summary.suitesFailed, 0, summary.suitesTotal)
      val testsStr = renderTestCounts(
        summary.testsPassed,
        summary.testsFailed,
        summary.testsErrors,
        summary.testsSkipped,
        summary.testsTotal
      )
      val timeStr = Duration.ofMillis(summary.duration).toPrettyString
      val summaryLine = s"$statusIcon  Suites: $suitesStr  │  Tests: $testsStr  │  $timeStr"
      val separator = "═" * summaryLine.length
      // render successful modules first, then failed
      val successfulModules =
        summary.modules.filter { case (_, res) => res.success }.toSeq.sortBy(_._1)
      val successfulModulesSummary = successfulModules
        .map { case (moduleId, res) => s"  ✅ PASSED $moduleId" }
        .mkString("\n")
      val failedModules = summary.modules.filter { case (_, res) => !res.success }.toSeq.sortBy(_._1)
      val failedModulesSummary = failedModules
        .map { case (moduleId, res) =>
          val rerunPlan = summary.rerunPlans.find(_.moduleId == moduleId)
          val selectors = rerunPlan.map(_.selectors).getOrElse(res.failedTestNames)
          val failedTestsSummary = selectors
            .map(selector => s"       - $selector")
            .mkString("\n")
          val counts = renderFailureCounts(res.failed, res.errors)
          val rerunSummary = rerunPlan
            .map(plan => s"       Re-run:\n         ${renderRerunCommand(plan)}")
            .getOrElse("")
          Seq(s"  🔴 FAILED $moduleId ($counts)", failedTestsSummary, rerunSummary)
            .filter(_.trim.nonEmpty)
            .mkString("\n")
        }
        .mkString("\n")

      val (skippedModuleFailures, failedModuleFailures) = summary.failures.partition(_.causedBy.isDefined)
      val failedModuleFailuresSummary = failedModuleFailures.sortBy(_.moduleId)
        .map(f => s"  🔴 FAILED ${f.moduleId} (${f.error})")
        .mkString("\n")
      val skippedModuleFailuresSummary = skippedModuleFailures.sortBy(_.moduleId)
        .map { f =>
          s"  ⏭️  SKIPPED ${f.moduleId} (${f.causedBy.get} failed)"
        }
        .mkString("\n")

      Seq(
        separator,
        summaryLine,
        successfulModulesSummary,
        failedModulesSummary,
        failedModuleFailuresSummary,
        skippedModuleFailuresSummary,
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

  private def renderTestCounts(
      passed: Int,
      failed: Int,
      errors: Int,
      skipped: Int,
      total: Int
  ): String = {
    val parts = Seq(
      Option.when(passed > 0)(s"$passed passed"),
      Option.when(failed > 0)(s"$failed failed"),
      Option.when(errors > 0)(s"$errors errored"),
      Option.when(skipped > 0)(s"$skipped skipped")
    ).flatten
    if parts.size == 1 && passed == total then parts.head
    else (parts.appended(s"$total total")).mkString(", ")
  }

  private def renderFailureCounts(failed: Int, errors: Int): String = {
    val failurePart = Option.when(failed > 0)(s"$failed failed")
    val errorPart = Option.when(errors > 0)(s"$errors errored")
    Seq(failurePart, errorPart).flatten.mkString(", ")
  }

  private def renderRerunCommand(plan: TestRerunPlan): String = {
    val commandParts =
      Seq("deder", "exec", "-t", "test", "-m", plan.moduleId) ++ plan.selectors
    val quotedParts = commandParts.map(shellQuote)
    quotedParts.mkString(" ")
  }

  private def shellQuote(value: String): String = {
    val safeCharacters = value.nonEmpty && value.forall { char =>
      char.isLetterOrDigit || "-_./:#%+".contains(char)
    }
    if safeCharacters then value
    else {
      val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
      s"\"$escaped\""
    }
  }

  private def rerunPlan(moduleId: String, results: DederTestResults): Option[TestRerunPlan] = {
    val selectors = results.suites
      .flatMap { suite =>
        suite.testCases.collect {
          case testCase
              if testCase.status == DederTestStatus.Failure || testCase.status == DederTestStatus.Error =>
            renderSelector(suite.name, testCase.name)
        }
      }
      .distinct
      .sorted
    Option.when(selectors.nonEmpty)(TestRerunPlan(moduleId, selectors))
  }

  private def renderSelector(suiteName: String, testCaseName: String): String = {
    val suitePrefix = s"$suiteName."
    val selectorPrefix = s"$suiteName#"
    if testCaseName == suiteName then suiteName
    else if testCaseName.startsWith(selectorPrefix) then testCaseName
    else if testCaseName.startsWith(suitePrefix) then
      val relativeTestName = testCaseName.stripPrefix(suitePrefix)
      s"$suiteName#$relativeTestName"
    else s"$suiteName#$testCaseName"
  }

  given Summarizable[DederTestResults, TestResultsSummary] with
    def summarize(resultsMap: Seq[(String, DederTestResults)], failures: Seq[ModuleFailure], totalDuration: java.time.Duration): TestResultsSummary = {
      val allResults = resultsMap.map(_._2)
      val rerunPlans = resultsMap.flatMap((moduleId, results) => rerunPlan(moduleId, results)).sortBy(_.moduleId)
      TestResultsSummary(
        success = allResults.forall(_.success) && failures.isEmpty,
        suitesTotal = allResults.map(_.suitesTotal).sum,
        suitesFailed = allResults.map(_.suitesFailed).sum,
        suitesPassed = allResults.map(_.suitesPassed).sum,
        testsTotal = allResults.map(_.total).sum,
        testsFailed = allResults.map(_.failed).sum,
        testsErrors = allResults.map(_.errors).sum,
        testsSkipped = allResults.map(_.skipped).sum,
        testsPassed = allResults.map(_.passed).sum,
        duration = allResults.map(_.duration).sum,
        totalDurationMillis = totalDuration.toMillis(),
        modules = resultsMap.toMap,
        rerunPlans = rerunPlans,
        failures = failures
      )
    }

}
