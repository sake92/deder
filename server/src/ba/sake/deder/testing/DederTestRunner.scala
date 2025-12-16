package ba.sake.deder.testing

import ba.sake.deder.*

// peek at https://github.com/scala-js/scala-js/blob/main/test-bridge/src/main/scala/org/scalajs/testing/bridge/HTMLRunner.scala

// TODO forked execution

import sbt.testing.{Task as SbtTestTask, *}
import java.io.File
import java.net.URLClassLoader
import scala.collection.mutable

class DederTestRunner(
    testClasspath: Seq[File],
    testClassesDir: File,
    logger: DederTestLogger
) {

  def run(options: DederTestOptions = DederTestOptions()): DederTestResults = {

    println(
      s"Running tests in ${testClassesDir.getAbsolutePath} with classpath: ${testClasspath.map(_.getAbsolutePath).mkString(":")}"
    )

    val urls = testClasspath.map(_.toURI.toURL).toArray
    val classLoader = new URLClassLoader(urls, getClass.getClassLoader)

    // TODO allow only one framework to be configured..
    val frameworks = discoverFrameworks(classLoader)

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

  private def discoverFrameworks(classLoader: ClassLoader): Seq[Framework] = {
    val frameworkClasses = Seq(
      "org.scalatest.tools.Framework",
      "org.specs2.runner.Specs2Framework",
      "munit.Framework",
      "utest.runner.Framework",
      "zio.test.sbt.ZTestFramework"
    )

    frameworkClasses.flatMap { className =>
      try {
        val cls = classLoader.loadClass(className)
        Some(cls.getDeclaredConstructor().newInstance().asInstanceOf[Framework])
      } catch {
        case _: ClassNotFoundException => None
        case e: Exception =>
          logger.warn(s"Failed to load framework $className: ${e.getMessage}")
          None
      }
    }
  }

  private def runFramework(
      framework: Framework,
      classLoader: ClassLoader,
      options: DederTestOptions
  ): Seq[DederTestResult] = {

    logger.info(s"Running tests with ${framework.name()}")

    val testFingerprints = discoverTests(framework, classLoader)

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

  private def discoverTests(
      framework: Framework,
      classLoader: ClassLoader
  ): Seq[(String, Fingerprint)] = {
    val fingerprints = framework.fingerprints()
    val testClasses = findClassFiles(testClassesDir)
    testClasses.flatMap { className =>
      fingerprints.collectFirst {
        case fp if matchesFingerprint(className, fp, classLoader) =>
          (className, fp)
      }
    }
  }

  private def findClassFiles(dir: File): Seq[String] = {
    val osDir = os.Path(dir)
    os.walk(osDir).filter(_.last.endsWith(".class")).map(_.relativeTo(osDir).baseName)
  }

  private def matchesFingerprint(
      className: String,
      fingerprint: Fingerprint,
      classLoader: ClassLoader
  ): Boolean = {
    try {
      val cls = classLoader.loadClass(className)
      fingerprint match {
        case sub: SubclassFingerprint =>
          val superCls = classLoader.loadClass(sub.superclassName())
          superCls.isAssignableFrom(cls) &&
          sub.isModule == isModule(cls)
        case ann: AnnotatedFingerprint =>
          val annCls = classLoader.loadClass(ann.annotationName())
          cls.isAnnotationPresent(annCls.asInstanceOf[Class[java.lang.annotation.Annotation]]) &&
          ann.isModule == isModule(cls)
      }
    } catch {
      case _: Exception =>
        logger.debug(s"Failed to match fingerprint for class $className")
        false
    }
  }

  private def isModule(cls: Class[?]): Boolean = {
    cls.getName.endsWith("$")
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

class DederTestLogger(
    serverNotificationsLogger: ServerNotificationsLogger,
    moduleId: String
) extends Logger {

  def ansiCodesSupported(): Boolean = true

  def showStackTraces: Boolean = true

  def test(msg: String): Unit = info(msg)

  def error(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logError(msg, Some(moduleId)))

  def warn(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logWarning(msg, Some(moduleId)))

  def info(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logInfo(msg, Some(moduleId)))

  def debug(msg: String): Unit =
    serverNotificationsLogger.add(ServerNotification.logDebug(msg, Some(moduleId)))

  def trace(t: Throwable): Unit =
    error(t.getStackTrace().mkString("\n"))
}
