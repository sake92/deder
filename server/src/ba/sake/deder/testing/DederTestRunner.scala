package ba.sake.deder.testing

// peek at https://github.com/scala-js/scala-js/blob/main/test-bridge/src/main/scala/org/scalajs/testing/bridge/HTMLRunner.scala

// TODO forked execution

import java.time.Duration
import java.util.concurrent.ExecutorService
import scala.collection.mutable
import scala.util.control.NonFatal
import sbt.testing.{Task as SbtTestTask, *}
import ba.sake.deder.*
import ba.sake.tupson.JsonRW

class DederTestRunner(
    executorService: ExecutorService,
    tests: Seq[(Framework, Seq[(String, Fingerprint)])],
    classLoader: ClassLoader,
    logger: DederTestLogger
) {

  // TODO run in another thread+outputdir, so we can run tests from multiple terminals/BSP
  // TODO print how to re-run just those
  def run(options: DederTestOptions): DederTestResults = {
    val startedAt = System.currentTimeMillis()
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
    val endedAt = System.currentTimeMillis()
    val realDuration = Duration.ofMillis(endedAt - startedAt)
    val testsDuration = Duration.ofMillis(res.duration)
    logger.info(
      s"Test run complete. Passed: ${res.passed}, Failed: ${res.failed}, Errors: ${res.errors}, Skipped: ${res.skipped}; " +
        s"Executed in ${realDuration.toPrettyString}, Aggregated duration: ${testsDuration.toPrettyString}"
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
    try executeTasks(tasks, handler)
    catch {
      case _: CancelledException =>
        logger.warn("Test run was cancelled.")
    }
    val summary = runner.done()
    logger.info(summary)

    val results = handler.results
    val failedTests = results.filter(r => r.status == Status.Failure || r.status == Status.Error)
    if (failedTests.nonEmpty) {
      logger.info("Failed tests:")
      failedTests.foreach { t =>
        val msg = t.throwable.flatMap(thr => Option(thr.getMessage)).getOrElse("No error message")
        logger.info(s"  ${t.name} - ${msg.take(100)}")
      }
    }
    results
  }

  private def executeTasks(tasks: Seq[SbtTestTask], handler: EventHandler): Unit = {
    val currentRequestId = RequestContext.id.get()
    val futures = {
      tasks.map { task =>
        executorService.submit { () =>
          val cancelled = currentRequestId != null && DederGlobals.cancellationTokens.get(currentRequestId).get()
          if cancelled then throw CancelledException("Tests execution cancelled")
          task.execute(handler, Array(logger))
        }
      }
      // val nestedTasks = ... TODO
    }
    try futures.map(_.get())
    catch {
      case _: CancelledException =>
        // if one task fails (maybe cancelled..), cancel all other pending tasks
        logger.warn(s"Cancelling remaining tests...")
        futures.foreach(_.cancel(true))
    }
  }
}

case class DederTestOptions(
    testSelectors: Seq[String]
)

class DederTestEventHandler(logger: DederTestLogger) extends EventHandler {
  private val _results = mutable.ArrayBuffer[DederTestResult]()

  def handle(event: Event): Unit = {
    val status = event.status() match {
      case Status.Success  => fansi.Color.Green("PASS ✅")
      case Status.Failure  => fansi.Color.Red("FAIL \uD83D\uDD34")
      case Status.Error    => fansi.Color.Red("FAIL \uD83D\uDD34")
      case Status.Skipped  => fansi.Color.LightYellow("SKIP 🚫")
      case Status.Ignored  => fansi.Color.LightYellow("SKIP 🚫")
      case Status.Canceled => fansi.Color.LightYellow("SKIP 🚫")
      case Status.Pending  => fansi.Color.LightYellow("SKIP 🚫")
    }
    val fqn = event.fullyQualifiedName()
    val testName = event.selector() match {
      case s: TestSelector       => if s.testName().contains("#") then s.testName() else s"${fqn}#${s.testName()}"
      case s: NestedTestSelector => if s.testName().contains("#") then s.testName() else s"${fqn}#${s.testName()}"
      case s: SuiteSelector      => fqn
      case _                     => fqn
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
  def toPrettyString: String =
    d.toString
      .substring(2)
      .replaceAll("(\\d[HMS])(?!$)", "$1")
      .toLowerCase()
}
