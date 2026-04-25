package ba.sake.deder.testing

// peek at https://github.com/scala-js/scala-js/blob/main/test-bridge/src/main/scala/org/scalajs/testing/bridge/HTMLRunner.scala

import java.time.Duration
import java.util.concurrent.{Executors, Future as JFuture}
import scala.collection.mutable
import sbt.testing.{Task as SbtTestTask, *}
import ba.sake.deder.*
import ba.sake.deder.testing.forked.{ForkRunnerHooks, ForkedTestEnvelope}

class DederTestRunner(
    testParallelism: Int,
    discoveredTests: Seq[DiscoveredFrameworkTests],
    frameworkOverrides: Map[String, Framework],
    classLoader: ClassLoader,
    logger: TestRunnerLogger,
    forkHooks: Option[ForkRunnerHooks] = None,
    isCancelled: () => Boolean = () => false
) {

  private val _perClassStats = scala.collection.mutable.Map[String, TestClassStats]()

  /** Per-test-class timing aggregated across all frameworks. Populated during [[run]]. */
  def perClassStats: Map[String, TestClassStats] = _perClassStats.toMap

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

    _perClassStats ++= handler.perClassStats

    val results = handler.results
    val failedTests = results.filter(r =>
      r.status == DederTestStatus.Failure || r.status == DederTestStatus.Error
    )
    if (failedTests.nonEmpty) {
      logger.info("Failed tests:")
      failedTests.foreach { t =>
        val msg = t.failure.flatMap(_.message).getOrElse("No error message")
        logger.info(s"  ${t.name} - ${msg.take(100)}")
      }
    }
    results
  }

  private def executeTasks(tasks: Seq[SbtTestTask], handler: EventHandler): Unit = {
    val capturedNotificationsLogger = OutputCaptureContext.currentNotificationsLogger.get()
    val capturedModuleId = OutputCaptureContext.currentModuleId.get()

    def runOne(task: SbtTestTask): Unit = {
      if isCancelled() then throw CancelledException("Tests execution cancelled")
      val suiteName = task.taskDef().fullyQualifiedName()
      val threadId = Thread.currentThread().getId
      forkHooks.foreach { h =>
        h.capture.startSuite()
        h.reporter.emit(ForkedTestEnvelope.SuiteStarted(suiteName, threadId))
      }
      try task.execute(handler, Array(logger))
      finally {
        forkHooks.foreach { h =>
          val captured = h.capture.finishSuite()
          h.reporter.emit(ForkedTestEnvelope.SuiteCompleted(suiteName, threadId, captured))
        }
      }
    }

    if testParallelism <= 1 then {
      try tasks.foreach(runOne)
      catch {
        case _: CancelledException =>
          logger.warn("Cancelling remaining tests...")
      }
    } else {
      val executor = Executors.newFixedThreadPool(testParallelism)
      try {
        val futures: Seq[JFuture[?]] = tasks.map { task =>
          executor.submit((() => {
            if (capturedNotificationsLogger != null) {
              OutputCaptureContext.currentNotificationsLogger.set(capturedNotificationsLogger)
              OutputCaptureContext.currentModuleId.set(capturedModuleId)
            }
            try runOne(task)
            finally {
              OutputCaptureContext.currentNotificationsLogger.remove()
              OutputCaptureContext.currentModuleId.remove()
            }
          }): Runnable)
        }
        try futures.foreach(_.get())
        catch {
          case _: CancelledException =>
            logger.warn("Cancelling remaining tests...")
            futures.foreach(_.cancel(true))
        }
      } finally {
        executor.shutdown()
      }
    }
  }
}

class DederTestEventHandler(logger: TestRunnerLogger, frameworkName: String) extends EventHandler {
  private val _results = mutable.ArrayBuffer[DederTestResult]()
  private val _classStats = mutable.Map[String, TestClassStats]()

  def handle(event: Event): Unit = {
    val fqn = event.fullyQualifiedName()
    val suiteName = DederTestNames.normalizeSuiteName(fqn)
    val testCaseName = event.selector() match {
      case s: TestSelector       => s.testName()
      case s: NestedTestSelector => s.testName()
      case _: SuiteSelector      => suiteName
      case _                     => suiteName
    }
    val testName =
      if frameworkName == "Jupiter" &&
          testCaseName != suiteName &&
          !testCaseName.startsWith(s"$suiteName#")
      then s"$suiteName#$testCaseName"
      else testCaseName
    val duration = Duration.ofMillis(event.duration())
    val eventThrowable = Option.when(event.throwable().isDefined)(event.throwable().get())
    val failure = eventThrowable.map(DederTestFailure.fromThrowable)
    val testStatus = DederTestStatus.fromSbt(event.status())

    // TODO maybe skip PASS here
    val status = event.status() match {
      case Status.Failure  => fansi.Color.Red("FAIL \uD83D\uDD34")
      case Status.Error    => fansi.Color.Red("FAIL \uD83D\uDD34")
      case Status.Skipped  => fansi.Color.LightYellow("SKIP 🚫")
      case Status.Ignored  => fansi.Color.LightYellow("SKIP 🚫")
      case Status.Canceled => fansi.Color.LightYellow("SKIP 🚫")
      case Status.Pending  => fansi.Color.LightYellow("SKIP 🚫")
      case Status.Success  => fansi.Color.Green("PASS ✅")
    }
    logger.test(s"$status $testName ; took ${duration.toPrettyString}")
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
      suiteName = suiteName,
      testCaseName = testCaseName,
      status = testStatus,
      duration = event.duration(),
      failure = failure
    )
    val existing = _classStats.getOrElse(suiteName, TestClassStats(0L, "passed", 0L))
    val newStatus =
      if event.status() == Status.Failure || event.status() == Status.Error then "failed"
      else existing.lastStatus
    _classStats.update(
      suiteName,
      TestClassStats(
        durationMs = existing.durationMs + event.duration(),
        lastStatus = newStatus,
        lastRunEpochMs = System.currentTimeMillis()
      )
    )
  }

  def results: Seq[DederTestResult] = _results.toSeq

  def perClassStats: Map[String, TestClassStats] = _classStats.toMap

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

extension (d: Duration) {
  def toPrettyString: String =
    d.toString
      .substring(2)
      .replaceAll("(\\d[HMS])(?!$)", "$1")
      .toLowerCase()
}
