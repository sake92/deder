package ba.sake.deder.testing

import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, TimeUnit}
import scala.collection.mutable
import sbt.testing.{
  Event,
  EventHandler,
  Fingerprint,
  Framework,
  Logger,
  OptionalThrowable,
  Runner,
  Selector,
  Status,
  SubclassFingerprint,
  SuiteSelector,
  Task as SbtTestTask,
  TaskDef
}
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class DederTestRunnerSuite extends munit.FunSuite {

  test("testParallelism=1 runs tasks serially on the calling thread") {
    val threadIds = ConcurrentHashMap.newKeySet[Long]()
    val framework = FakeFramework(taskCount = 5, onExecute = () => {
      threadIds.add(Thread.currentThread().getId)
    })

    val runner = buildRunner(framework, testParallelism = 1)
    runner.run(DederTestOptions(testSelectors = Seq.empty))

    val callingThreadId = Thread.currentThread().getId
    assertEquals(threadIds.size, 1, s"expected all tasks on one thread; got $threadIds")
    assertEquals(threadIds.iterator.next(), callingThreadId)
  }

  test("testParallelism=4 runs tasks on at most 4 threads and allows overlap") {
    val threadIds = ConcurrentHashMap.newKeySet[Long]()
    val overlapLatch = new CountDownLatch(4)

    // Each task counts down the latch, then waits up to 5s for others to reach the same
    // point. If parallelism actually happens, all 4 reach zero and proceed; if serial, the
    // first task never sees the count drop below 3 and times out (making the test fail).
    val framework = FakeFramework(taskCount = 8, onExecute = () => {
      threadIds.add(Thread.currentThread().getId)
      overlapLatch.countDown()
      val reached = overlapLatch.await(5, TimeUnit.SECONDS)
      assert(reached, "tasks did not run in parallel within 5s")
    })

    val runner = buildRunner(framework, testParallelism = 4)
    runner.run(DederTestOptions(testSelectors = Seq.empty))

    assert(threadIds.size >= 2, s"expected multiple threads; got $threadIds")
    assert(threadIds.size <= 4, s"expected at most 4 threads; got $threadIds")
  }

  // TODO reenable
  test("runner preserves suite data for JUnit XML reporting".ignore) {
    val runner = buildRunner(FakeFramework(taskCount = 2, onExecute = () => ()), testParallelism = 1)

    val results = runner.run(DederTestOptions(testSelectors = Seq.empty))

    assertEquals(results.total, 2)
    assertEquals(results.suites.map(_.name).sorted, Seq("fake.Task0", "fake.Task1"))
    assert(results.suites.forall(_.testCases.size == 1))
    assertEquals(results.suites.head.testCases.head.classname, results.suites.head.name)
  }

  // --- helpers ---

  private def buildRunner(framework: Framework, testParallelism: Int): DederTestRunner = {
    val notifications = mutable.ArrayBuffer[ServerNotification]()
    val logger = DederTestLogger(ServerNotificationsLogger(n => notifications += n), "test-module")
    val discovered = Seq(
      DiscoveredFrameworkTests(
        frameworkName = framework.name(),
        frameworkClassName = framework.getClass.getName,
        testClasses = framework match {
          case f: FakeFramework =>
            (0 until f.taskCount).map(i =>
              DiscoveredFrameworkTest(
                className = s"fake.Task$i",
                fingerprint = JsonableFingerprint.Subclass("java.lang.Object", false, false)
              )
            )
          case _ => Seq.empty
        }
      )
    )
    new DederTestRunner(
      testParallelism = testParallelism,
      discoveredTests = discovered,
      frameworkOverrides = Map(framework.getClass.getName -> framework),
      classLoader = getClass.getClassLoader,
      logger = logger
    )
  }

  private class FakeFramework(val taskCount: Int, val onExecute: () => Unit) extends Framework {
    override def name(): String = "fake"
    override def fingerprints(): Array[Fingerprint] = Array(
      new SubclassFingerprint {
        override def superclassName(): String = "java.lang.Object"
        override def isModule: Boolean = false
        override def requireNoArgConstructor(): Boolean = false
      }
    )
    override def runner(runnerArgs: Array[String], runnerRemoteArgs: Array[String], cl: ClassLoader): Runner =
      new Runner {
        override def args(): Array[String] = runnerArgs
        override def remoteArgs(): Array[String] = runnerRemoteArgs
        override def done(): String = ""
        override def tasks(taskDefs: Array[TaskDef]): Array[SbtTestTask] =
          taskDefs.map(td => FakeTask(td, onExecute))
      }
  }

  private object FakeFramework {
    def apply(taskCount: Int, onExecute: () => Unit): FakeFramework =
      new FakeFramework(taskCount, onExecute)
  }

  private class FakeTask(td: TaskDef, onExecute: () => Unit) extends SbtTestTask {
    override def tags(): Array[String] = Array.empty
    override def taskDef(): TaskDef = td
    override def execute(handler: EventHandler, loggers: Array[Logger]): Array[SbtTestTask] = {
      onExecute()
      handler.handle(new Event {
        override def fullyQualifiedName(): String = td.fullyQualifiedName()
        override def fingerprint(): Fingerprint = td.fingerprint()
        override def selector(): Selector = td.selectors().headOption.getOrElse(new SuiteSelector)
        override def status(): Status = Status.Success
        override def throwable(): OptionalThrowable = new OptionalThrowable()
        override def duration(): Long = 0L
      })
      Array.empty
    }
  }
}
