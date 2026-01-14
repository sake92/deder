package ba.sake.deder.testing

// peek at https://github.com/scala-js/scala-js/blob/main/test-bridge/src/main/scala/org/scalajs/testing/bridge/HTMLRunner.scala

// TODO forked execution

import sbt.testing.{Task as SbtTestTask, *}
import java.io.File
import java.net.URLClassLoader
import scala.collection.mutable
import ba.sake.deder.*
import ba.sake.tupson.JsonRW

class DederTestRunner(
    tests: Seq[(Framework, Seq[(String, Fingerprint)])],
    classLoader: ClassLoader,
    logger: DederTestLogger
) {

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
                  matchedClassNames.flatMap { n =>
                     testClasses.find(_._1 == n)
                  }.map(tc => (tc._1, tc._2, new TestSelector(testSelector)))
                case _ =>
                  val matchedClassNames = WildcardUtils.getMatches(testClassNames, ts)
                  matchedClassNames.flatMap { n =>
                    testClasses.find(_._1 == n)
                  }.map(tc => (tc._1, tc._2, new SuiteSelector))
              }
            }
          }
        runFramework(framework, selectedTestClasses)
      }
      DederTestResults.aggregate(allResults)
    }
    logger.info(
      s"Test run complete. Passed: ${res.passed}, Failed: ${res.failed}, Errors: ${res.errors}, Skipped: ${res.skipped}; Duration: ${res.duration}ms"
    )
    res
  }

  private def runFramework(
      framework: Framework,
      testClasses: Seq[(String, Fingerprint, Selector)],
  ): Seq[DederTestResult] = {
    logger.info(s"Running tests with ${framework.name()}")
    if (testClasses.isEmpty) {
      logger.warn(s"No tests found for ${framework.name()}")
      return Seq.empty
    }
    val runner = framework.runner(
      Array.empty[String], // framework args
      Array.empty[String], // remoteArgs
      classLoader
    )
    val tasks = testClasses.flatMap { case (className, fingerprint, selector) =>
      val taskDef = new TaskDef(
        className,
        fingerprint,
        false, // explicitly specified
        Array(selector)
      )
      runner.tasks(Array(taskDef))
    }
    val handler = DederTestEventHandler(logger)
    executeTasks(tasks, handler)
    val summary = runner.done()
    logger.info(summary)
    handler.results
  }

  private def executeTasks(tasks: Seq[SbtTestTask], handler: EventHandler): Unit = {
    tasks.foreach { task =>
      val nestedTasks = task.execute(handler, Array(logger))
      if (nestedTasks.nonEmpty) {
        executeTasks(nestedTasks, handler)
      }
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
      case Status.Success  => "✓"
      case Status.Failure  => "✗"
      case Status.Error    => "✗"
      case Status.Skipped  => "○"
      case Status.Ignored  => "○"
      case Status.Canceled => "○"
      case Status.Pending  => "○"
    }
    val testName = event.selector() match {
      case s: TestSelector       => s.testName()
      case s: NestedTestSelector => s.testName()
      case s: SuiteSelector      => event.fullyQualifiedName()
      case _                     => event.fullyQualifiedName()
    }
    logger.test(s"$status $testName")
    val eventThrowable = Option.when(event.throwable().isDefined())(event.throwable().get())
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
