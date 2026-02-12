package ba.sake.deder.testing

// peek at https://github.com/scala-js/scala-js/blob/main/test-bridge/src/main/scala/org/scalajs/testing/bridge/HTMLRunner.scala

// TODO forked execution

import java.time.Duration
import scala.concurrent.duration.Duration as ScalaDuration
import sbt.testing.{Task as SbtTestTask, *}
import scala.collection.mutable
import ba.sake.deder.*
import ba.sake.tupson.JsonRW

class DederTestRunner(
    tests: Seq[(Framework, Seq[(String, Fingerprint)])],
    classLoader: ClassLoader,
    logger: DederTestLogger
) {

  // TODO run in another thread, so we can run tests from multiple terminals/BSP
  // TODO run suites in parallel
  // TODO print failed tests at the end, with option to re-run just those
  // TODO handle cancellation requests
  def run(options: DederTestOptions): DederTestResults = {
    val res = if (tests.isEmpty) {
      logger.warn("No tests found on the classpath.")
      DederTestResults.empty
    } else {
      logger.debug(s"Found ${tests.size} test framework(s): ${tests.map(_._1.name()).mkString(", ")}")
      val allResults = tests.flatMap { case (framework, testClasses) =>
        val testClassNames = testClasses.map(_._1)
        val selectedTestClasses: Seq[(String, Fingerprint, Selector)] =
          if options.testSelectors.isEmpty then testClasses.map(tc => (tc._1, tc._2, new SuiteSelector))
          else {
            options.testSelectors.flatMap { ts =>
              ts.split("#") match {
                case Array(classNameSelector, testSelector) =>
                  val matchedClassNames = WildcardUtils.getMatches(testClassNames, classNameSelector)
                  matchedClassNames
                    .flatMap { n =>
                      testClasses.find(_._1 == n)
                    }
                    .map(tc => (tc._1, tc._2, new TestSelector(testSelector)))
                // TODO add TestWildcardSelector? hmm just a substring
                case _ =>
                  val matchedClassNames = WildcardUtils.getMatches(testClassNames, ts)
                  matchedClassNames
                    .flatMap { n =>
                      testClasses.find(_._1 == n)
                    }
                    .map(tc => (tc._1, tc._2, new SuiteSelector))
              }
            }
          }
        runFramework(framework, selectedTestClasses)
      }
      DederTestResults.aggregate(allResults)
    }
    val duration = Duration.ofMillis(res.duration)
    logger.info(
      s"Test run complete. Passed: ${res.passed}, Failed: ${res.failed}, Errors: ${res.errors}, Skipped: ${res.skipped}; Duration: ${duration.toPrettyString}"
    )
    res
  }

  private def runFramework(
      framework: Framework,
      testClasses: Seq[(String, Fingerprint, Selector)]
  ): Seq[DederTestResult] = {
    logger.info(s"Running tests with ${framework.name()}")
    if (testClasses.isEmpty) {
      logger.warn(s"No tests found for ${framework.name()}")
      return Seq.empty
    }
    // TODO handle tags, framework specific..
    val runner = framework.runner(
      Array.empty[String], // framework args
      Array.empty[String], // remoteArgs
      classLoader
    )
    val tasks = testClasses.flatMap { case (className, fingerprint, selector) =>
      // weaver is wonky, wants class name without $ suffix, even for objects..
      val tweakedClassName = if framework.name().startsWith("weaver-") then className.stripSuffix("$") else className
      val taskDef = new TaskDef(
        tweakedClassName,
        fingerprint,
        false, // explicitly specified
        Array(selector)
      )
      runner.tasks(Array(taskDef))
    }
    val handler = DederTestEventHandler(logger)
    val cancelled = executeTasks(tasks, handler)
    if cancelled then {
      logger.warn("Test run was cancelled.")
    }
    val summary = runner.done()
    logger.info(summary)

    val results = handler.results
    val failedTests = results.filter(r => r.status == Status.Failure || r.status == Status.Error)
    if (failedTests.nonEmpty) {
      logger.info("Failed tests:")
      failedTests.foreach { t =>
        logger.info(s"  ${t.name} - ${t.throwable.map(_.getMessage).getOrElse("No error message").take(100)}")
      }
    }
    results
  }

  private def executeTasks(tasks: Seq[SbtTestTask], handler: EventHandler): Boolean =
    tasks.exists { task =>
      val currentRequestId = RequestContext.id.get()
      val cancelled = currentRequestId != null && DederGlobals.cancellationTokens.get(currentRequestId).get()
      if !cancelled then {
        val nestedTasks = task.execute(handler, Array(logger))
        if nestedTasks.nonEmpty then {
          executeTasks(nestedTasks, handler)
        }
      }
      cancelled
    }
}

case class DederTestOptions(
    testSelectors: Seq[String]
)

class DederTestEventHandler(logger: DederTestLogger) extends EventHandler {
  private val _results = mutable.ArrayBuffer[DederTestResult]()

  def handle(event: Event): Unit = {
    // TODO add some color
    val status = event.status() match {
      case Status.Success  => "PASS"
      case Status.Failure  => "FAIL"
      case Status.Error    => "FAIL"
      case Status.Skipped  => "SKIP"
      case Status.Ignored  => "SKIP"
      case Status.Canceled => "SKIP"
      case Status.Pending  => "SKIP"
    }
    val testName = event.selector() match {
      case s: TestSelector       => s.testName()
      case s: NestedTestSelector => s.testName()
      case s: SuiteSelector      => event.fullyQualifiedName()
      case _                     => event.fullyQualifiedName()
    }
    val duration = Duration.ofMillis(event.duration())
    logger.test(s"$status $testName ; took ${duration.toPrettyString}")
    val eventThrowable = Option.when(event.throwable().isDefined)(event.throwable().get())
    eventThrowable.foreach { t =>
      logger.error(s"  ${t.getMessage}")
      if (logger.showStackTraces) {
        t.getStackTrace.take(10).foreach { line =>
          logger.error(s"    at $line")
        }
      }
    }
    _results += DederTestResult(
      name = testName,
      status = event.status(),
      duration = event.duration(),
      throwable = eventThrowable
    )
  }

  def results: Seq[DederTestResult] = _results.toSeq
}

case class DederTestResult(
    name: String,
    status: Status,
    duration: Long,
    throwable: Option[Throwable]
)

// TODO also return DederPath of results folder
case class DederTestResults(
    total: Int,
    passed: Int,
    failed: Int,
    errors: Int,
    skipped: Int,
    duration: Long
) derives JsonRW {
  def success: Boolean = failed == 0 && errors == 0
}

object DederTestResults {
  def empty: DederTestResults = DederTestResults(0, 0, 0, 0, 0, 0)

  def aggregate(results: Seq[DederTestResult]): DederTestResults = {
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
      duration = results.map(_.duration).sum
    )
  }
}

extension (d: Duration) {
  def toPrettyString: String = {
    val prettyDuration = d.toString
      .substring(2)
      .replaceAll("(\\d[HMS])(?!$)", "$1 ")
      .toLowerCase()
    prettyDuration
  }
}
