package ba.sake.deder

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters.*
import scala.jdk.DurationConverters.*
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider

class DederProjectInternalsImplSuite extends munit.FunSuite {

  private def testInternals(): DederProjectInternalsImpl =
    val sdk = OpenTelemetrySdk.builder()
      .setMeterProvider(SdkMeterProvider.builder().build())
      .build()
    val meter = sdk.getMeter("test")
    DederProjectInternalsImpl(meter, CacheStatsRegistry())

  test("currentRequests tracks live requests") {
    val internals = testInternals()
    assertEquals(internals.currentRequests.size, 0)

    internals.recordRequestStarted("req-1", CallerType.Cli, "compile", Seq("app"), Instant.now())
    assertEquals(internals.currentRequests.size, 1)
    assertEquals(internals.currentRequests.head.requestId, "req-1")
    assertEquals(internals.currentRequests.head.caller, CallerType.Cli)
    assertEquals(internals.currentRequests.head.taskName, "compile")
    assertEquals(internals.currentRequests.head.moduleIds, Seq("app"))

    internals.recordRequestCompleted("req-1", "compile", success = true, Duration.ofMillis(100), CallerType.Cli, error = None)
    assertEquals(internals.currentRequests.size, 0)
    assertEquals(internals.totalRequestsServed, 1L)
    assertEquals(internals.totalErrors, 0L)
  }

  test("recentHistory is bounded to 100 entries") {
    val internals = testInternals()
    for i <- 1 to 150 do
      internals.recordRequestStarted(s"req-$i", CallerType.Cli, "compile", Seq("app"), Instant.now())
      internals.recordRequestCompleted(s"req-$i", "compile", success = true, Duration.ofMillis(10), CallerType.Cli, error = None)

    assertEquals(internals.recentHistory.size, 100)
    assertEquals(internals.recentHistory.head.requestId, "req-150")
    assertEquals(internals.recentHistory.last.requestId, "req-51")
  }

  test("taskStats accumulates per-task execution data") {
    val internals = testInternals()

    internals.recordTaskExecution("compile", Duration.ofMillis(100), cacheHit = false, errorMessage = None)
    internals.recordTaskExecution("compile", Duration.ofMillis(200), cacheHit = true, errorMessage = None)
    internals.recordTaskExecution("test", Duration.ofMillis(50), cacheHit = false, errorMessage = None)

    val compileStats = internals.taskStats("compile").get
    assertEquals(compileStats.executions, 2L)
    assertEquals(compileStats.cacheHits, 1L)
    assertEquals(compileStats.errors, 0L)
    assertEquals(compileStats.lastError, None)
    assertEquals(compileStats.duration.count, 2L)
    assertEquals(compileStats.duration.min, Duration.ofMillis(100))
    assertEquals(compileStats.duration.max, Duration.ofMillis(200))

    val testStats = internals.taskStats("test").get
    assertEquals(testStats.executions, 1L)
    assertEquals(testStats.duration.count, 1L)

    assert(internals.taskStats("nonexistent").isEmpty)
  }

  test("totalRequestsServed and totalErrors are independent") {
    val internals = testInternals()
    internals.recordRequestStarted("ok", CallerType.Cli, "compile", Seq("app"), Instant.now())
    internals.recordRequestCompleted("ok", "compile", success = true, Duration.ofMillis(10), CallerType.Cli, error = None)
    assertEquals(internals.totalRequestsServed, 1L)
    assertEquals(internals.totalErrors, 0L)

    internals.recordRequestStarted("fail", CallerType.Bsp, "test", Seq("app"), Instant.now())
    internals.recordRequestCompleted("fail", "test", success = false, Duration.ofMillis(10), CallerType.Bsp, error = Some("something broke"))
    assertEquals(internals.totalRequestsServed, 2L)
    assertEquals(internals.totalErrors, 1L)
  }

  test("recordTaskExecution with error increments errors counter and stores lastError") {
    val internals = testInternals()

    internals.recordTaskExecution("compile", Duration.ofMillis(100), cacheHit = false, errorMessage = Some("compile failed"))
    val stats = internals.taskStats("compile").get
    assertEquals(stats.executions, 1L)
    assertEquals(stats.errors, 1L)
    assertEquals(stats.lastError, Some("compile failed"))
  }

  test("recordTaskExecution multiple mixed errors accumulate and lastError reflects most recent") {
    val internals = testInternals()

    internals.recordTaskExecution("compile", Duration.ofMillis(100), cacheHit = false, errorMessage = Some("error 1"))
    internals.recordTaskExecution("compile", Duration.ofMillis(50), cacheHit = false, errorMessage = None)
    internals.recordTaskExecution("compile", Duration.ofMillis(200), cacheHit = false, errorMessage = Some("error 2"))

    val stats = internals.taskStats("compile").get
    assertEquals(stats.executions, 3L)
    assertEquals(stats.errors, 2L)
    assertEquals(stats.lastError, Some("error 2"))
  }

  test("recordTaskExecution successful after error preserves prior error state") {
    val internals = testInternals()

    internals.recordTaskExecution("compile", Duration.ofMillis(100), cacheHit = false, errorMessage = Some("boom"))
    internals.recordTaskExecution("compile", Duration.ofMillis(50), cacheHit = true, errorMessage = None)

    val stats = internals.taskStats("compile").get
    assertEquals(stats.errors, 1L)
    assertEquals(stats.lastError, Some("boom"))
    assertEquals(stats.cacheHits, 1L)
  }

  test("recordRequestCompleted with error stores error in CompletedRequest and increments totalErrCount") {
    val internals = testInternals()

    internals.recordRequestStarted("req-err", CallerType.Cli, "compile", Seq("app"), Instant.now())
    internals.recordRequestCompleted("req-err", "compile", success = false, Duration.ofMillis(10), CallerType.Cli, error = Some("something broke"))

    assertEquals(internals.totalErrors, 1L)
    val history = internals.recentHistory
    assertEquals(history.size, 1)
    assertEquals(history.head.error, Some("something broke"))
    assertEquals(history.head.success, false)
  }

  test("allTaskStats returns all tracked tasks alphabetically") {
    val internals = testInternals()
    internals.recordTaskExecution("classes", Duration.ofMillis(10), cacheHit = false, errorMessage = None)
    internals.recordTaskExecution("compile", Duration.ofMillis(100), cacheHit = false, errorMessage = None)
    internals.recordTaskExecution("sourceFiles", Duration.ofMillis(5), cacheHit = false, errorMessage = None)

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
        internals.recordTaskExecution(s"task-${i % 10}", Duration.ofMillis(i % 100), cacheHit = i % 3 == 0, errorMessage = None)
      }
    }
    Await.result(Future.sequence(tasks),  Duration.ofSeconds(10).toScala)
    val all = internals.allTaskStats
    assertEquals(all.size, 10)
    val totalExecs = all.map(_._2.executions).sum
    assertEquals(totalExecs, 1000L)
  }
}
