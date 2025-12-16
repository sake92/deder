package ba.sake.deder.testing

// peek at https://github.com/scala-js/scala-js/blob/main/test-bridge/src/main/scala/org/scalajs/testing/bridge/HTMLRunner.scala

// TODO forked execution

import sbt.testing.{Task as SbtTestTask, *}
import java.io.File
import java.net.URLClassLoader
import scala.collection.mutable
import ba.sake.deder.*

class DederTestRunner(
    testClasspath: Seq[File],
    testDiscovery: DederTestDiscovery,
    logger: DederTestLogger
) {

  def run(options: DederTestOptions = DederTestOptions()): DederTestResults = {
    val urls = testClasspath.map(_.toURI.toURL).toArray
    val classLoader = new URLClassLoader(urls, getClass.getClassLoader)
    val frameworks = testDiscovery.discoverFrameworks(classLoader)
    if (frameworks.isEmpty) {
      logger.warn("No test frameworks found on classpath")
      return DederTestResults.empty
    }
    logger.info(s"Found ${frameworks.size} test framework(s): ${frameworks.map(_.name).mkString(", ")}")
    val allResults = frameworks.flatMap { framework =>
      runFramework(framework, classLoader, options)
    }
    DederTestResults.aggregate(allResults)
  }

  private def runFramework(
      framework: Framework,
      classLoader: ClassLoader,
      options: DederTestOptions
  ): Seq[DederTestResult] = {
    logger.info(s"Running tests with ${framework.name()}")
    val testFingerprints = testDiscovery.discoverTests(framework, classLoader)
    if (testFingerprints.isEmpty) {
      logger.warn(s"No tests found for ${framework.name()}")
      return Seq.empty
    }
    val runner = framework.runner(
      Array.empty[String], // framework args
      Array.empty[String], // remoteArgs
      classLoader
    )
    val tasks = testFingerprints.map { case (className, fingerprint) =>
      val taskDef = new TaskDef(
        className,
        fingerprint,
        false, // explicitly specified
        Array(new SuiteSelector) // selectors
      )
      runner.tasks(Array(taskDef))
    }.flatten
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
    parallel: Boolean = false,
    tags: Set[String] = Set.empty,
    excludeTags: Set[String] = Set.empty
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
) {
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
