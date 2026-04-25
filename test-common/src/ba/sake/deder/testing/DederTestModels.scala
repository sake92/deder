package ba.sake.deder.testing

import sbt.testing.Status
import ba.sake.tupson.JsonRW
import java.io.{PrintWriter, StringWriter}

case class DederTestOptions(
    testSelectors: Seq[String]
)

enum DederTestStatus derives JsonRW {
  case Success, Failure, Error, Skipped
}

object DederTestStatus {
  def fromSbt(status: Status): DederTestStatus = status match {
    case Status.Success  => DederTestStatus.Success
    case Status.Failure  => DederTestStatus.Failure
    case Status.Error    => DederTestStatus.Error
    case Status.Skipped  => DederTestStatus.Skipped
    case Status.Ignored  => DederTestStatus.Skipped
    case Status.Canceled => DederTestStatus.Skipped
    case Status.Pending  => DederTestStatus.Skipped
  }
}

object DederTestNames {
  def normalizeSuiteName(name: String): String = name.stripSuffix("$")
}

case class DederTestFailure(
    message: Option[String],
    stackTrace: Option[String]
) derives JsonRW

object DederTestFailure {
  def fromThrowable(t: Throwable): DederTestFailure = {
    val writer = new StringWriter()
    val printer = new PrintWriter(writer)
    try t.printStackTrace(printer)
    finally printer.close()
    DederTestFailure(
      message = Option(t.getMessage),
      stackTrace = Option(writer.toString).filter(_.nonEmpty)
    )
  }
}

case class DederTestCaseReport(
    name: String,
    classname: String,
    status: DederTestStatus,
    duration: Long,
    failure: Option[DederTestFailure] = None
) derives JsonRW

case class DederTestSuiteReport(
    name: String,
    testCases: Seq[DederTestCaseReport],
    duration: Long,
    systemOut: Option[String] = None,
    systemErr: Option[String] = None
) derives JsonRW {
  def total: Int = testCases.size
  def passed: Int = testCases.count(_.status == DederTestStatus.Success)
  def failed: Int = testCases.count(_.status == DederTestStatus.Failure)
  def errors: Int = testCases.count(_.status == DederTestStatus.Error)
  def skipped: Int = testCases.count(_.status == DederTestStatus.Skipped)
}

case class DederTestResult(
    name: String,
    suiteName: String,
    testCaseName: String,
    status: DederTestStatus,
    duration: Long,
    failure: Option[DederTestFailure]
)

case class DederTestResults(
    total: Int,
    passed: Int,
    failed: Int,
    errors: Int,
    skipped: Int,
    duration: Long,
    failedTestNames: Seq[String] = Seq.empty,
    suites: Seq[DederTestSuiteReport] = Seq.empty
) derives JsonRW {
  def success: Boolean = failed == 0 && errors == 0

  def withSuiteStdout(outputs: Map[String, String]): DederTestResults =
    copy(
      suites = suites.map { suite =>
        suite.copy(systemOut = outputs.get(suite.name).filter(_.nonEmpty).orElse(suite.systemOut))
      }
    )
}

object DederTestResults {
  def empty: DederTestResults = DederTestResults(0, 0, 0, 0, 0, 0)

  def aggregate(results: Seq[DederTestResult]): DederTestResults = {
    val failedOrError = results.filter(r =>
      r.status == DederTestStatus.Failure || r.status == DederTestStatus.Error
    )
    val suites = results
      .groupBy(_.suiteName)
      .toSeq
      .sortBy(_._1)
      .map { case (suiteName, suiteResults) =>
        DederTestSuiteReport(
          name = suiteName,
          testCases = suiteResults
            .sortBy(_.testCaseName)
            .map { res =>
              DederTestCaseReport(
                name = res.testCaseName,
                classname = suiteName,
                status = res.status,
                duration = res.duration,
                failure = res.failure
              )
            },
          duration = suiteResults.map(_.duration).sum
        )
      }
    DederTestResults(
      total = results.size,
      passed = results.count(_.status == DederTestStatus.Success),
      failed = results.count(_.status == DederTestStatus.Failure),
      errors = results.count(_.status == DederTestStatus.Error),
      skipped = results.count(_.status == DederTestStatus.Skipped),
      duration = results.map(_.duration).sum,
      failedTestNames = failedOrError.map(_.name),
      suites = suites
    )
  }
}
