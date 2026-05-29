package ba.sake.deder

import java.time.Instant
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider

class DederProjectInternalsImplSuite extends munit.FunSuite {

  private def testInternals(): DederProjectInternalsImpl =
    val sdk = OpenTelemetrySdk.builder()
      .setMeterProvider(SdkMeterProvider.builder().build())
      .build()
    val meter = sdk.getMeter("test")
    DederProjectInternalsImpl(meter)

  test("currentRequests tracks live requests") {
    val internals = testInternals()
    assertEquals(internals.currentRequests.size, 0)

    internals.recordRequestStarted("req-1", CallerType.Cli, "compile", Seq("app"), Instant.now())
    assertEquals(internals.currentRequests.size, 1)
    assertEquals(internals.currentRequests.head.requestId, "req-1")
    assertEquals(internals.currentRequests.head.caller, CallerType.Cli)
    assertEquals(internals.currentRequests.head.taskName, "compile")
    assertEquals(internals.currentRequests.head.moduleIds, Seq("app"))

    internals.recordRequestCompleted("req-1", "compile", success = true, 100.millis, CallerType.Cli)
    assertEquals(internals.currentRequests.size, 0)
    assertEquals(internals.totalRequestsServed, 1L)
    assertEquals(internals.totalErrors, 0L)
  }

  test("recentHistory is bounded to 100 entries") {
    val internals = testInternals()
    for i <- 1 to 150 do
      internals.recordRequestStarted(s"req-$i", CallerType.Cli, "compile", Seq("app"), Instant.now())
      internals.recordRequestCompleted(s"req-$i", "compile", success = true, 10.millis, CallerType.Cli)

    assertEquals(internals.recentHistory.size, 100)
    assertEquals(internals.recentHistory.head.requestId, "req-150")
    assertEquals(internals.recentHistory.last.requestId, "req-51")
  }

  test("taskStats accumulates per-task execution data") {
    val internals = testInternals()

    internals.recordTaskExecution("compile", 100.millis, cacheHit = false)
    internals.recordTaskExecution("compile", 200.millis, cacheHit = true)
    internals.recordTaskExecution("test", 50.millis, cacheHit = false)

    val compileStats = internals.taskStats("compile").get
    assertEquals(compileStats.executions, 2L)
    assertEquals(compileStats.cacheHits, 1L)
    assertEquals(compileStats.errors, 0L)
    assertEquals(compileStats.duration.count, 2L)
    assertEquals(compileStats.duration.min, 100.millis)
    assertEquals(compileStats.duration.max, 200.millis)

    val testStats = internals.taskStats("test").get
    assertEquals(testStats.executions, 1L)
    assertEquals(testStats.duration.count, 1L)

    assert(internals.taskStats("nonexistent").isEmpty)
  }

  test("totalRequestsServed and totalErrors are independent") {
    val internals = testInternals()
    internals.recordRequestStarted("ok", CallerType.Cli, "compile", Seq("app"), Instant.now())
    internals.recordRequestCompleted("ok", "compile", success = true, 10.millis, CallerType.Cli)
    assertEquals(internals.totalRequestsServed, 1L)
    assertEquals(internals.totalErrors, 0L)

    internals.recordRequestStarted("fail", CallerType.Bsp, "test", Seq("app"), Instant.now())
    internals.recordRequestCompleted("fail", "test", success = false, 10.millis, CallerType.Bsp)
    assertEquals(internals.totalRequestsServed, 2L)
    assertEquals(internals.totalErrors, 1L)
  }

  test("allTaskStats returns all tracked tasks alphabetically") {
    val internals = testInternals()
    internals.recordTaskExecution("classes", 10.millis, false)
    internals.recordTaskExecution("compile", 100.millis, false)
    internals.recordTaskExecution("sourceFiles", 5.millis, false)

    val all = internals.allTaskStats
    assertEquals(all.size, 3)
    assertEquals(all.map(_._1), Seq("classes", "compile", "sourceFiles"))
  }

  test("serverUptime increases over time") {
    val internals = testInternals()
    val t0 = internals.serverUptime
    Thread.sleep(10)
    val t1 = internals.serverUptime
    assert(t1.toMillis > t0.toMillis)
  }

  test("multiple concurrent recordTaskExecution calls are thread-safe") {
    import scala.concurrent.{Await, ExecutionContext, Future}
    import scala.concurrent.ExecutionContext.Implicits.global
    val internals = testInternals()
    val tasks = (1 to 1000).map { i =>
      Future {
        internals.recordTaskExecution(s"task-${i % 10}", (i % 100).millis, i % 3 == 0)
      }
    }
    Await.result(Future.sequence(tasks), 10.seconds)
    val all = internals.allTaskStats
    assertEquals(all.size, 10)
    val totalExecs = all.map(_._2.executions).sum
    assertEquals(totalExecs, 1000L)
  }
}
