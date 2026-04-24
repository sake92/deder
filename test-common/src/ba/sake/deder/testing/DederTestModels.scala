package ba.sake.deder.testing

import sbt.testing.Status
import ba.sake.tupson.JsonRW

case class DederTestOptions(
    testSelectors: Seq[String]
)

case class DederTestResult(
    name: String,
    status: Status,
    duration: Long,
    throwable: Option[Throwable]
)

case class DederTestResults(
    total: Int,
    passed: Int,
    failed: Int,
    errors: Int,
    skipped: Int,
    duration: Long,
    failedTestNames: Seq[String] = Seq.empty
) derives JsonRW {
  def success: Boolean = failed == 0 && errors == 0
}

object DederTestResults {
  def empty: DederTestResults = DederTestResults(0, 0, 0, 0, 0, 0)

  def aggregate(results: Seq[DederTestResult]): DederTestResults = {
    val failedOrError = results.filter(r => r.status == Status.Failure || r.status == Status.Error)
    DederTestResults(
      total = results.size,
      passed = results.count(_.status == Status.Success),
      failed = results.count(_.status == Status.Failure),
      errors = results.count(_.status == Status.Error),
      skipped = results.count(r =>
        r.status == Status.Skipped ||
          r.status == Status.Ignored ||
          r.status == Status.Canceled
      ),
      duration = results.map(_.duration).sum,
      failedTestNames = failedOrError.map(_.name)
    )
  }
}
