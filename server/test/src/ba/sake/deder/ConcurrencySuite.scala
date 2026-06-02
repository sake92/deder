package ba.sake.deder

import java.util.UUID
import scala.util.Random
import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.publish.PublishTasks
import ba.sake.deder.graalvm.GraalVmNativeImageTasks
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider

class ConcurrencySuite extends munit.FunSuite {

  private def noopInternals: DederProjectInternalsImpl =
    val sdk = OpenTelemetrySdk.builder()
      .setMeterProvider(SdkMeterProvider.builder().build())
      .build()
    DederProjectInternalsImpl(sdk.getMeter("test"))

  private var testProjectDir: os.Path = scala.compiletime.uninitialized

  val coreTasks = CoreTasks()
  val runTasks = RunTasks(coreTasks)
  val publishTasks = PublishTasks(coreTasks)
  val scalaJsTasks = scalajs.ScalaJsTasks(coreTasks)
  val scalaNativeTasks = scalanative.ScalaNativeTasks(coreTasks)
  val graalvmNativeImageTasks = GraalVmNativeImageTasks(coreTasks)

  override def beforeAll(): Unit = {
    testProjectDir = os.pwd / "server/test/resources/sample-projects/multi"
    System.setProperty("DEDER_PROJECT_ROOT_DIR", testProjectDir.toString)
  }

  test("executeTask should guard against concurrent executions of the same task") {
    val tasksRegistry = TasksRegistry(coreTasks.all)
    var globalVar = 0
    val task1 = TaskBuilder
      .make[String](name = "task1", supportedModuleTypes = Set(ModuleType.SCALA))
      .dependsOn(coreTasks.compileTask)
      .build { _ =>
        Thread.sleep(Random.nextInt(10))
        globalVar += 1
        ""
      }
    tasksRegistry.add(task1)
    val dederExecutorService = java.util.concurrent.Executors.newFixedThreadPool(8)

    val state = DederProjectState(
      coreTasks,
      runTasks,
      publishTasks,
      scalaJsTasks,
      scalaNativeTasks,
      graalvmNativeImageTasks,
      tasksRegistry,
      Int.MaxValue,
      0,
      () => (),
      configFile = testProjectDir / "deder.pkl",
      internals = noopInternals
    )
    val serverNotificationsLogger = new ServerNotificationsLogger(_ => ())
    // simulate clients calling "task1" concurrently
    val clientsCount = 10
    val clientExecutorService = java.util.concurrent.Executors.newFixedThreadPool(32)
    val clientFutures = (1 to clientsCount).map { _ =>
      clientExecutorService.submit(() => {
        val ctx = RequestContext(clientId = UUID.randomUUID().toString, requestId = UUID.randomUUID().toString)
        state.executeTasks(ctx.requestId, CallerType.Cli, Seq("common"), "task1", Seq.empty, false, serverNotificationsLogger, false)
      })
    }
    clientFutures.foreach(_.get()) // wait for all clients to finish
    clientExecutorService.shutdown()
    dederExecutorService.shutdown()
    assertEquals(globalVar, clientsCount)
  }

  test("executeTask should serialize locks by task instance id") {
    val tasksRegistry = TasksRegistry(coreTasks.all)
    var globalVar = 0
    val task1 = TaskBuilder
      .make[String](name = "task1", supportedModuleTypes = Set(ModuleType.SCALA))
      .dependsOn(coreTasks.compileTask)
      .build { _ =>
        Thread.sleep(Random.nextInt(10))
        globalVar += 1
        ""
      }
    val task2 = TaskBuilder
      .make[String](name = "task2", supportedModuleTypes = Set(ModuleType.SCALA))
      .dependsOn(task1)
      .build { _ =>
        Thread.sleep(Random.nextInt(10))
        globalVar += 1
        ""
      }
    tasksRegistry.add(task1)
    tasksRegistry.add(task2)
    val dederExecutorService = java.util.concurrent.Executors.newFixedThreadPool(8)
    val state = DederProjectState(
      coreTasks,
      runTasks,
      publishTasks,
      scalaJsTasks,
      scalaNativeTasks,
      graalvmNativeImageTasks,
      tasksRegistry,
      Int.MaxValue,
      0,
      () => (),
      configFile = testProjectDir / "deder.pkl",
      internals = noopInternals
    )
    val serverNotificationsLogger = new ServerNotificationsLogger(_ => ())
    // simulate clients calling random tasks concurrently
    val taskNames = Seq("compile", "sources", "javacOptions")
    val clientsCount = 100
    val clientExecutorService = java.util.concurrent.Executors.newFixedThreadPool(32)
    val clientFutures = (1 to clientsCount).map { i =>
      clientExecutorService.submit(() => {
        // half of the clients always call "task2", the other half random tasks
        val taskName = if i % 2 == 0 then "task2" else taskNames(Random.nextInt(taskNames.length))
        val ctx = RequestContext(clientId = UUID.randomUUID().toString, requestId = UUID.randomUUID().toString)
        state.executeTasks(ctx.requestId, CallerType.Cli, Seq("common"), taskName, Seq.empty, false, serverNotificationsLogger, false)
      })
    }
    clientFutures.foreach(_.get()) // wait for all clients to finish
    clientExecutorService.shutdown()
    dederExecutorService.shutdown()
    // count gets incremented by task1+task2
    assertEquals(globalVar, clientsCount)
  }

  test("executeTask should timeout when lock is held longer than taskLockTimeoutSeconds") {
    val tasksRegistry = TasksRegistry(coreTasks.all)
    val slowTask = TaskBuilder
      .make[String](name = "slowTask", supportedModuleTypes = Set(ModuleType.SCALA))
      .dependsOn(coreTasks.compileTask)
      .build { _ =>
        Thread.sleep(5000)
        ""
      }
    tasksRegistry.add(slowTask)

    val state = DederProjectState(
      coreTasks,
      runTasks,
      publishTasks,
      scalaJsTasks,
      scalaNativeTasks,
      graalvmNativeImageTasks,
      tasksRegistry,
      Int.MaxValue,
      2,
      () => (),
      configFile = testProjectDir / "deder.pkl",
      internals = noopInternals
    )
    val serverNotificationsLogger = new ServerNotificationsLogger(_ => ())

    val requestId1 = UUID.randomUUID().toString
    val requestId2 = UUID.randomUUID().toString

    val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    val backgroundFuture = backgroundExecutor.submit(() => {
      try {
        state.executeTasks(requestId1, CallerType.Cli, Seq("common"), "slowTask", Seq.empty, false, serverNotificationsLogger, false)
      } catch {
        case _: Exception =>
      }
    })

    Thread.sleep(500)

    val ex = intercept[TaskEvaluationException] {
      state.executeTasks(requestId2, CallerType.Cli, Seq("common"), "slowTask", Seq.empty, false, serverNotificationsLogger, false)
    }

    assert(ex.getCause.isInstanceOf[TaskLockTimeoutException], s"Expected TaskLockTimeoutException, got: ${ex.getCause.getClass.getName}")
    assert(ex.getCause.getMessage.contains("Timed out waiting for lock"), s"Unexpected message: ${ex.getCause.getMessage}")

    backgroundExecutor.shutdown()
  }

  test("executeTask with taskLockTimeoutSeconds=0 should not timeout (unlimited wait)") {
    val tasksRegistry = TasksRegistry(coreTasks.all)
    val slowTask = TaskBuilder
      .make[String](name = "slowTaskZero", supportedModuleTypes = Set(ModuleType.SCALA))
      .dependsOn(coreTasks.compileTask)
      .build { _ =>
        Thread.sleep(1000)
        ""
      }
    tasksRegistry.add(slowTask)

    val state = DederProjectState(
      coreTasks,
      runTasks,
      publishTasks,
      scalaJsTasks,
      scalaNativeTasks,
      graalvmNativeImageTasks,
      tasksRegistry,
      Int.MaxValue,
      0,
      () => (),
      configFile = testProjectDir / "deder.pkl",
      internals = noopInternals
    )
    val serverNotificationsLogger = new ServerNotificationsLogger(_ => ())

    val requestId1 = UUID.randomUUID().toString
    val requestId2 = UUID.randomUUID().toString

    val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    val lockAcquiredLatch = new java.util.concurrent.CountDownLatch(1)
    val backgroundFuture = backgroundExecutor.submit(() => {
      try {
        lockAcquiredLatch.countDown()
        state.executeTasks(requestId1, CallerType.Cli, Seq("common"), "slowTaskZero", Seq.empty, false, serverNotificationsLogger, false)
      } catch {
        case _: Exception =>
      }
    })

    lockAcquiredLatch.await()

    state.executeTasks(requestId2, CallerType.Cli, Seq("common"), "slowTaskZero", Seq.empty, false, serverNotificationsLogger, false)

    backgroundExecutor.shutdown()
  }

}
