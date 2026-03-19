package ba.sake.deder.testing

// peek at https://github.com/scala-js/scala-js/blob/main/test-bridge/src/main/scala/org/scalajs/testing/bridge/HTMLRunner.scala

import java.time.Duration
import java.util.concurrent.ExecutorService
import scala.collection.mutable
import sbt.testing.{Task as SbtTestTask, *}
import ba.sake.deder.*
import ba.sake.deder.config.DederProject.DederModule
import ba.sake.tupson.JsonRW

class DederTestRunner(
    executorService: ExecutorService,
    discoveredTests: Seq[DiscoveredFrameworkTests],
    frameworkOverrides: Map[String, Framework],
    classLoader: ClassLoader,
    logger: DederTestLogger
) {

  // TODO run in another thread+outputdir, so we can run tests from multiple terminals/BSP
  // TODO print how to re-run just those
  def run(options: DederTestOptions): DederTestResults = {
    val startedAt = System.currentTimeMillis()
    val res = if (discoveredTests.isEmpty) {
      logger.warn("No tests found on the classpath.")
      DederTestResults.empty
    } else {
      logger.debug(
        s"Found ${discoveredTests.size} test framework(s): ${discoveredTests.map(_.frameworkName).mkString(", ")}"
      )
      val allResults = discoveredTests.flatMap { t =>
        val testClassNames = t.testClasses.map(_.className)
        val selectedTestClasses: Seq[(String, Fingerprint, Selector)] =
          if options.testSelectors.isEmpty then
            t.testClasses.map(tc => (tc.className, tc.fingerprint.toSbtFingerprint, new SuiteSelector))
          else {
            options.testSelectors.flatMap { ts =>
              ts.split("#") match {
                case Array(classNameSelector, testSelector) =>
                  val matchedClassNames = WildcardUtils.getMatches(testClassNames, classNameSelector)
                  matchedClassNames
                    .flatMap { n =>
                      t.testClasses.find(_.className == n)
                    }
                    .map(tc => (tc.className, tc.fingerprint.toSbtFingerprint, new TestSelector(testSelector)))
                // TODO add TestWildcardSelector? hmm just a substring
                case _ =>
                  val matchedClassNames = WildcardUtils.getMatches(testClassNames, ts)
                  matchedClassNames
                    .flatMap { n =>
                      t.testClasses.find(_.className == n)
                    }
                    .map(tc => (tc.className, tc.fingerprint.toSbtFingerprint, new SuiteSelector))
              }
            }
          }
        val framework = frameworkOverrides.getOrElse(
          t.frameworkClassName,
          classLoader.loadClass(t.frameworkClassName).getDeclaredConstructor().newInstance().asInstanceOf[Framework]
        )
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
    val handler = DederTestEventHandler(logger, framework.name())
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
    val capturedNotificationsLogger = OutputCaptureContext.currentNotificationsLogger.get()
    val capturedModuleId = OutputCaptureContext.currentModuleId.get()
    val futures = {
      tasks.map { task =>
        executorService.submit { () =>
          if (capturedNotificationsLogger != null) {
            OutputCaptureContext.currentNotificationsLogger.set(capturedNotificationsLogger)
            OutputCaptureContext.currentModuleId.set(capturedModuleId)
          }
          try {
            val cancelled = currentRequestId != null && DederGlobals.cancellationTokens.get(currentRequestId).get()
            if cancelled then throw CancelledException("Tests execution cancelled")
            task.execute(handler, Array(logger))
          } finally {
            OutputCaptureContext.currentNotificationsLogger.remove()
            OutputCaptureContext.currentModuleId.remove()
          }
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

class DederTestEventHandler(logger: DederTestLogger, frameworkName: String) extends EventHandler {
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
      case s: TestSelector       => if frameworkName == "Jupiter" then s"${fqn}#${s.testName()}" else s.testName()
      case s: NestedTestSelector => if frameworkName == "Jupiter" then s"${fqn}#${s.testName()}" else s.testName()
      case s: SuiteSelector      => fqn
      case _                     => fqn
    }
    val duration = Duration.ofMillis(event.duration())
    logger.test(s"$status $testName ; took ${duration.toPrettyString}")
    val eventThrowable = Option.when(event.throwable().isDefined)(event.throwable().get())
    eventThrowable.foreach { t =>
      logger.error(s"  ${t.getMessage}")
      if (shouldLogStackTrace(t)) {
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

  private def shouldLogStackTrace(t: Throwable): Boolean =
    logger.showStackTraces && !isScalaJsMappedFailure(t)

  private def isScalaJsMappedFailure(t: Throwable): Boolean = {
    val message = Option(t.getMessage).getOrElse("")
    message.contains(".scala:") &&
    t.getStackTrace.exists { line =>
      Option(line.getFileName).exists(_.endsWith("main.js"))
    }
  }
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
    duration: Long,
    failedTestNames: Seq[String] = Seq.empty
) derives JsonRW {
  def success: Boolean = failed == 0 && errors == 0
}

object DederTestResults {
  def empty: DederTestResults = DederTestResults(0, 0, 0, 0, 0, 0)

  def summarize(
      results: Seq[(DederModule, DederTestResults)],
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
        s"$statusIcon Test Summary: ${totalResults.total} total, ${totalResults.passed} passed, ${totalResults.failed} failed," +
          s" ${totalResults.errors} errors, ${totalResults.skipped} skipped"
      )
    )
    results.foreach { case (module, res) =>
      val icon = if res.success then "  PASS ✅" else "  FAIL \uD83D\uDD34"
      val detail = Option.when(!res.success) {
        Seq(
          Option.when(res.failed > 0)(s"${res.failed} failed"),
          Option.when(res.errors > 0)(s"${res.errors} errors")
        ).flatten.mkString(", ")
      }
      notifications.add(ServerNotification.logInfo(s"$icon ${module.id}${detail.map(d => s" ($d)").getOrElse("")}"))
      res.failedTestNames.foreach { testName =>
        notifications.add(ServerNotification.logInfo(s"       - $testName"))
      }
    }
    notifications.add(ServerNotification.logInfo(separator))
  }

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

extension (d: Duration) {
  def toPrettyString: String =
    d.toString
      .substring(2)
      .replaceAll("(\\d[HMS])(?!$)", "$1")
      .toLowerCase()
}
