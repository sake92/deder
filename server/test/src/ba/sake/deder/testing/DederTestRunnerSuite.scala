package ba.sake.deder.testing

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
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
import ba.sake.deder.{ProjectContext, RequestContext, ServerNotification, ServerNotificationsLogger}

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

  test("parallel tasks preserve captured project and request context") {
    val root = os.temp.dir()
    val expectedRequestId = "parallel-request"
    val observedRoots = ConcurrentHashMap.newKeySet[os.Path]()
    val observedRequestIds = ConcurrentHashMap.newKeySet[String]()
    val framework = FakeFramework(taskCount = 12, onExecute = () => {
      observedRoots.add(ProjectContext.requireCurrent.projectRootDir)
      observedRequestIds.add(RequestContext.current.get().requestId)
    })
    val expectedRequest = RequestContext(requestId = expectedRequestId)

    ProjectContext.run(ProjectContext(root)) {
      RequestContext.current.supervisedWhere(expectedRequest) {
        val projectContext = ProjectContext.requireCurrent
        val taskContext: Runnable => Runnable = task => () => {
          ProjectContext.run(projectContext) {
            RequestContext.current.supervisedWhere(expectedRequest) {
              task.run()
            }
          }
        }
        val runner = buildRunner(framework, testParallelism = 4, taskContext = taskContext)
        val results = runner.run(DederTestOptions(testSelectors = Seq.empty))

        assertEquals(results.total, 12)
        assertEquals(observedRoots.toArray.toSeq, Seq(root))
        assertEquals(observedRequestIds.toArray.toSeq, Seq(expectedRequestId))
      }
    }
  }

  test("parallel event collection retains every result and class statistic") {
    val taskCount = 200
    val eventNumber = new AtomicInteger(0)
    val overlapLatch = new CountDownLatch(8)
    val framework = new FakeFramework(
      taskCount = taskCount,
      onExecute = () => {
        overlapLatch.countDown()
        assert(overlapLatch.await(5, TimeUnit.SECONDS), "same-suite tasks did not run concurrently")
      },
      classNameForTask = _ => "fake.Shared",
      nextEvent = () => {
        val index = eventNumber.getAndIncrement()
        if index == 0 then (Status.Failure, 7L) else (Status.Success, 3L)
      }
    )
    val runner = buildRunner(framework, testParallelism = 8)

    val results = runner.run(DederTestOptions(testSelectors = Seq.empty))

    assertEquals(results.total, taskCount)
    assertEquals(results.passed, taskCount - 1)
    assertEquals(results.failed, 1)
    assertEquals(results.errors, 0)
    assertEquals(results.suites.size, 1)
    assertEquals(results.suites.head.testCases.size, taskCount)
    val stats = runner.perClassStats
    assertEquals(stats.size, 1)
    assertEquals(stats("fake.Shared").durationMs, 7L + (taskCount - 1) * 3L)
    assertEquals(stats("fake.Shared").lastStatus, "failed")
  }

  // TODO move to "bootstrapped" tests? build test-runner and then do these tests
  test("runner preserves suite data for JUnit XML reporting") {
    val runner = buildRunner(FakeFramework(taskCount = 2, onExecute = () => ()), testParallelism = 1)

    val results = runner.run(DederTestOptions(testSelectors = Seq.empty))

    assertEquals(results.total, 2)
    assertEquals(results.suites.map(_.name).sorted, Seq("fake.Task0", "fake.Task1"))
    assert(results.suites.forall(_.testCases.size == 1))
    assertEquals(results.suites.head.testCases.head.classname, results.suites.head.name)
  }

  test("isCancelled=true from start: no tasks execute, returns empty results") {
    val executed = new java.util.concurrent.atomic.AtomicInteger(0)
    val framework = new FakeFramework(taskCount = 4, onExecute = () => {
      executed.incrementAndGet()
    })

    val runner = buildCancellableRunner(framework, testParallelism = 1, isCancelled = () => true)
    val results = runner.run(DederTestOptions(testSelectors = Seq.empty))

    assertEquals(executed.get(), 0, "no tasks should execute when cancelled from start")
    assertEquals(results.total, 0)
  }

  test("isCancelled after first task: partial results returned") {
    val executed = new java.util.concurrent.atomic.AtomicInteger(0)
    var cancelled = false
    val latch = new CountDownLatch(1)
    val startedLatch = new CountDownLatch(1)

    val framework = new FakeFramework(taskCount = 4, onExecute = () => {
      val n = executed.incrementAndGet()
      if (n == 1) {
        startedLatch.countDown()
        latch.await(5, TimeUnit.SECONDS)
      }
    })

    val runner = buildCancellableRunner(framework, testParallelism = 1, isCancelled = () => cancelled)
    val runnerThread = new Thread((() => runner.run(DederTestOptions(testSelectors = Seq.empty))): Runnable)
    runnerThread.start()

    assert(startedLatch.await(5, TimeUnit.SECONDS), "first task should have started")
    cancelled = true
    latch.countDown()

    runnerThread.join(5000)
    assert(!runnerThread.isAlive(), "runner should have exited after cancellation")

    assertEquals(executed.get(), 1, "only the in-flight task should complete")
  }

  test("parallel cancellation reads the captured request context on workers") {
    val root = os.temp.dir()
    val expectedRequest = RequestContext(requestId = "cancel-request")
    val taskStarted = new CountDownLatch(1)
    val releaseTask = new CountDownLatch(1)
    val cancelled = new AtomicBoolean(false)
    val sawRequestContext = new AtomicBoolean(false)
    val framework = FakeFramework(taskCount = 4, onExecute = () => {
      assertEquals(RequestContext.current.get().requestId, expectedRequest.requestId)
      taskStarted.countDown()
      assert(releaseTask.await(5, TimeUnit.SECONDS), "test task was not released")
    })

    ProjectContext.run(ProjectContext(root)) {
      RequestContext.current.supervisedWhere(expectedRequest) {
        val projectContext = ProjectContext.requireCurrent
        val taskContext: Runnable => Runnable = task => () => {
          ProjectContext.run(projectContext) {
            RequestContext.current.supervisedWhere(expectedRequest) {
              task.run()
            }
          }
        }
        val runner = buildCancellableRunner(
          framework,
          testParallelism = 2,
          isCancelled = () => {
            if RequestContext.current.get().requestId == expectedRequest.requestId then
              sawRequestContext.set(true)
            cancelled.get()
          },
          taskContext = taskContext
        )
        val runnerThread = Thread.ofVirtual().start((() => runner.run(DederTestOptions(testSelectors = Seq.empty))): Runnable)

        assert(taskStarted.await(5, TimeUnit.SECONDS), "a worker task should have started")
        cancelled.set(true)
        releaseTask.countDown()

        runnerThread.join(5000)
        assert(!runnerThread.isAlive(), "runner should have exited after cancellation")
        assert(sawRequestContext.get(), "cancellation callback should observe the request context")
      }
    }
  }

  // --- helpers ---

  private def buildCancellableRunner(
      framework: Framework,
      testParallelism: Int,
      isCancelled: () => Boolean,
      taskContext: Runnable => Runnable = identity
  ): DederTestRunner = {
    val notifications = new ConcurrentLinkedQueue[ServerNotification]()
    val logger = DederTestLogger(ServerNotificationsLogger(n => notifications.add(n)), "test-module")
    val discovered = Seq(
      DiscoveredFrameworkTests(
        frameworkName = framework.name(),
        frameworkClassName = framework.getClass.getName,
        testClasses = framework match {
          case f: FakeFramework =>
            (0 until f.taskCount).map(i =>
              DiscoveredFrameworkTest(
                className = f.classNameForTask(i),
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
      logger = logger,
      taskContext = taskContext,
      isCancelled = isCancelled
    )
  }

  private def buildRunner(
      framework: Framework,
      testParallelism: Int,
      taskContext: Runnable => Runnable = identity
  ): DederTestRunner = {
    val notifications = new ConcurrentLinkedQueue[ServerNotification]()
    val logger = DederTestLogger(ServerNotificationsLogger(n => notifications.add(n)), "test-module")
    val discovered = Seq(
      DiscoveredFrameworkTests(
        frameworkName = framework.name(),
        frameworkClassName = framework.getClass.getName,
        testClasses = framework match {
          case f: FakeFramework =>
            (0 until f.taskCount).map(i =>
              DiscoveredFrameworkTest(
                className = f.classNameForTask(i),
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
      logger = logger,
      taskContext = taskContext
    )
  }

  private class FakeFramework(
      val taskCount: Int,
      val onExecute: () => Unit,
      val classNameForTask: Int => String = i => s"fake.Task$i",
      val nextEvent: () => (Status, Long) = () => (Status.Success, 0L)
  ) extends Framework {
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
          taskDefs.map(td => FakeTask(td, onExecute, nextEvent))
      }
  }

  private object FakeFramework {
    def apply(taskCount: Int, onExecute: () => Unit): FakeFramework =
      new FakeFramework(taskCount, onExecute)
  }

  private class FakeTask(td: TaskDef, onExecute: () => Unit, nextEvent: () => (Status, Long)) extends SbtTestTask {
    override def tags(): Array[String] = Array.empty
    override def taskDef(): TaskDef = td
    override def execute(handler: EventHandler, loggers: Array[Logger]): Array[SbtTestTask] = {
      onExecute()
      val (testStatus, testDuration) = nextEvent()
      handler.handle(new Event {
        override def fullyQualifiedName(): String = td.fullyQualifiedName()
        override def fingerprint(): Fingerprint = td.fingerprint()
        override def selector(): Selector = td.selectors().headOption.getOrElse(new SuiteSelector)
        override def status(): Status = testStatus
        override def throwable(): OptionalThrowable = new OptionalThrowable()
        override def duration(): Long = testDuration
      })
      Array.empty
    }
  }
}
