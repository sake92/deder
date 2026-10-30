package ba.sake.deder

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.immutable.AbstractSeq
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import ba.sake.deder.config.ConfigParser
import ba.sake.deder.config.DederProject.ModuleType
import ba.sake.deder.deps.DependencyResolver

class TasksExecutorSuite extends munit.FunSuite {

  private val projectRoot = os.pwd / "server/test/resources/sample-projects/simple"
  private val projectConfig =
    ConfigParser(writeJson = false).parse(projectRoot / "deder.pkl")
      .getOrElse(fail("Failed to parse simple test project configuration"))
  private val coreTasks = CoreTasks()

  override def beforeEach(context: BeforeEach): Unit =
    super.beforeEach(context)
    System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

  private def newExecutor(extraTasks: Seq[Task[?, ?, ?]] = Seq.empty): TasksExecutor = {
    val tasksResolver = TasksResolver(projectConfig, TasksRegistry(coreTasks.all ++ extraTasks))
    val sdk = OpenTelemetrySdk.builder()
      .setMeterProvider(SdkMeterProvider.builder().build())
      .build()
    val internals = DederProjectInternalsImpl(sdk.getMeter("test"), CacheStatsRegistry())
    val repositories = DependencyResolver.assembleRepositories(Seq.empty, includeDefaultRepos = true)
    val dependencyResolver = DependencyResolver(repositories, CacheStatsRegistry())
    TasksExecutor(
      projectConfig,
      tasksResolver.modulesGraph,
      tasksResolver.taskInstancesGraph,
      dependencyResolver,
      internals
    )
  }

  private def taskInstance(
      taskName: String,
      extraTasks: Seq[Task[?, ?, ?]],
      moduleId: String = "core"
  ): TaskInstance = {
    val tasksResolver = TasksResolver(projectConfig, TasksRegistry(coreTasks.all ++ extraTasks))
    tasksResolver.taskInstancesPerModule(moduleId).find(_.task.name == taskName)
      .getOrElse(fail(s"Task '$taskName' not found on module '$moduleId'"))
  }

  test("task preparation failures become task failures and do not execute the body") {
    var executed = false
    val prepFailure = TaskBuilder
      .make[String]("prepFailure", supportedModuleTypes = Set(ModuleType.SCALA))
      .dependsOn(coreTasks.scalaVersionTask)
      .build { _ =>
        executed = true
        "unexpected"
      }
    val ti = taskInstance(prepFailure.name, Seq(prepFailure))

    val results = newExecutor(Seq(prepFailure)).execute(
      requestId = "prep-failure",
      stages = Seq(Seq(ti)),
      moduleIds = Seq("core"),
      taskName = prepFailure.name,
      args = Seq.empty,
      watch = false,
      serverNotificationsLogger = ServerNotificationsLogger(_ => ())
    )

    val failure = results.collectFirst { case f: TaskExecResult.Failure => f }
      .getOrElse(fail(s"Expected a failure result, got $results"))
    assertEquals(failure.taskInstance.id, ti.id)
    assert(failure.error.contains("core.scalaVersion"), s"Unexpected preparation error: ${failure.error}")
    assert(!executed, "Task body should not run when dependency preparation fails")
  }

  test("unexpected stage failures are rethrown to the request boundary") {
    val executor = newExecutor()
    val explodingStage = new AbstractSeq[TaskInstance] {
      override def apply(index: Int): TaskInstance =
        throw new AssertionError(s"unexpected stage access: $index")

      override def iterator: Iterator[TaskInstance] =
        throw RuntimeException("stage failure")

      override def length: Int = 0
    }

    val error = intercept[RuntimeException] {
      executor.execute(
        requestId = "stage-failure",
        stages = Seq(explodingStage),
        moduleIds = Seq("core"),
        taskName = "compile",
        args = Seq.empty,
        watch = false,
        serverNotificationsLogger = ServerNotificationsLogger(_ => ())
      )
    }
    assertEquals(error.getMessage, "stage failure")
  }

  test("ordinary task failures remain failure outcomes") {
    val ordinaryFailure = TaskBuilder
      .make[String]("ordinaryFailure", supportedModuleTypes = Set(ModuleType.SCALA))
      .build { _ => throw RuntimeException("ordinary failure") }
    val ti = taskInstance(ordinaryFailure.name, Seq(ordinaryFailure))

    val results = newExecutor(Seq(ordinaryFailure)).execute(
      requestId = "ordinary-failure",
      stages = Seq(Seq(ti)),
      moduleIds = Seq("core"),
      taskName = ordinaryFailure.name,
      args = Seq.empty,
      watch = false,
      serverNotificationsLogger = ServerNotificationsLogger(_ => ())
    )

    assertEquals(results.collect { case f: TaskExecResult.Failure => f }.map(_.error), Seq("ordinary failure"))
  }

  test("a failure in one module does not abort independent modules in the same stage") {
    var successfulModuleExecuted = false
    val mixedOutcome = TaskBuilder
      .make[String]("mixedOutcome", supportedModuleTypes = Set(ModuleType.SCALA))
      .build { ctx =>
        if ctx.module.id == "core" then throw RuntimeException("core failure")
        successfulModuleExecuted = true
        "app success"
      }
    val taskInstances = Seq(
      taskInstance(mixedOutcome.name, Seq(mixedOutcome), moduleId = "core"),
      taskInstance(mixedOutcome.name, Seq(mixedOutcome), moduleId = "app")
    )

    val results = newExecutor(Seq(mixedOutcome)).execute(
      requestId = "independent-modules",
      stages = Seq(taskInstances),
      moduleIds = Seq("core", "app"),
      taskName = mixedOutcome.name,
      args = Seq.empty,
      watch = false,
      serverNotificationsLogger = ServerNotificationsLogger(_ => ())
    )

    assertEquals(results.collect { case f: TaskExecResult.Failure => f }.map(_.taskInstance.moduleId), Seq("core"))
    assertEquals(results.collect { case s: TaskExecResult.Success => s }.map(_.taskInstance.moduleId), Seq("app"))
    assert(successfulModuleExecuted, "Independent module should execute even when another module fails")
  }

  test("cancellation after task execution remains a cancelled outcome") {
    val cancelledTask = TaskBuilder
      .make[String]("cancelledTask", supportedModuleTypes = Set(ModuleType.SCALA))
      .build(_ => "completed")
    val ti = taskInstance(cancelledTask.name, Seq(cancelledTask))
    val requestId = "cancelled-task"
    DederGlobals.cancellationTokens.put(requestId, new AtomicBoolean(true))

    try {
      val results = newExecutor(Seq(cancelledTask)).execute(
        requestId = requestId,
        stages = Seq(Seq(ti)),
        moduleIds = Seq("core"),
        taskName = cancelledTask.name,
        args = Seq.empty,
        watch = false,
        serverNotificationsLogger = ServerNotificationsLogger(_ => ())
      )

      val cancelled = results.collectFirst { case c: TaskExecResult.Cancelled => c }
        .getOrElse(fail(s"Expected a cancellation result, got $results"))
      assertEquals(cancelled.taskInstance.id, ti.id)
      assert(cancelled.message.contains("cancelled"), s"Unexpected cancellation message: ${cancelled.message}")
    } finally {
      DederGlobals.cancellationTokens.remove(requestId)
    }
  }
}
