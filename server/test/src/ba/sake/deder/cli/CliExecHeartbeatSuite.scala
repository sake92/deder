package ba.sake.deder.cli

import java.time.{Duration, Instant}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

import ox.*
import ba.sake.deder.{OutputFormat, RequestContext, ServerNotification}

class CliExecHeartbeatSuite extends munit.FunSuite {

  test("emits exactly one INFO heartbeat after 60 seconds of visible silence") {
    withHeartbeat { (clock, emitted, heartbeat) =>
      clock.awaitSleepers(1)
      clock.advanceBy(Duration.ofSeconds(59))
      assertNoHeartbeat(emitted)

      clock.advanceBy(Duration.ofSeconds(1))
      eventually() {
        assertEquals(heartbeatLogs(emitted).map(_.level), Seq(LogLevel.INFO))
      }
    }
  }

  test("visible Log, Output and RunSubprocess activity reset the quiet timer") {
    val visibleNotifications = Seq[ServerNotification](
      ServerNotification.logInfo("Compiling app"),
      ServerNotification.Output("compiler output"),
      ServerNotification.RunSubprocess(Seq("node", "main.js"), Map("NODE_ENV" -> "test"), watch = false)
    )

    visibleNotifications.foreach { notification =>
      withHeartbeat { (clock, emitted, heartbeat) =>
        clock.awaitSleepers(1)
        clock.advanceBy(Duration.ofSeconds(59))
        heartbeat.recordServerNotification(notification)
        clock.advanceBy(Duration.ofSeconds(59))
        clock.awaitSleepCount(2)
        assertNoHeartbeat(emitted)

        clock.advanceBy(Duration.ofSeconds(1))
        eventually() {
          assertEquals(heartbeatLogs(emitted).map(_.level), Seq(LogLevel.INFO))
        }
      }
    }
  }

  test("hidden notifications do not suppress heartbeats") {
    val hiddenNotifications = Seq[ServerNotification](
      ServerNotification.TaskProgress("app", "compile", progress = 3, total = 10),
      ServerNotification.CompileStarted("app", Seq.empty),
      ServerNotification.CompileFinished("app", errors = 0, warnings = 0),
      ServerNotification.CompileFailed("app", errors = 1, warnings = 0)
    )

    hiddenNotifications.foreach { notification =>
      withHeartbeat { (clock, emitted, heartbeat) =>
        clock.awaitSleepers(1)
        clock.advanceBy(Duration.ofSeconds(59))
        heartbeat.recordServerNotification(notification)
        clock.advanceBy(Duration.ofSeconds(1))

        eventually() {
          assertEquals(heartbeatLogs(emitted).map(_.level), Seq(LogLevel.INFO))
        }
      }
    }
  }

  test("close stops heartbeats and emits nothing afterwards") {
    withHeartbeat(autoClose = false) { (clock, emitted, heartbeat) =>
      clock.awaitSleepers(1)
      clock.advanceBy(Duration.ofSeconds(60))
      eventually() {
        assertEquals(heartbeatLogs(emitted).map(_.level), Seq(LogLevel.INFO))
      }

      heartbeat.close()
      clock.awaitNoSleepers()

      clock.advanceBy(Duration.ofSeconds(60))
      assertEquals(heartbeatLogs(emitted).map(_.level), Seq(LogLevel.INFO))
    }
  }

  private def withHeartbeat(
      testCode: (ManualClock, ConcurrentLinkedQueue[CliServerMessage], CliExecHeartbeat) => Unit
  ): Unit =
    withHeartbeat(autoClose = true)(testCode)

  private def withHeartbeat(
      autoClose: Boolean
  )(testCode: (ManualClock, ConcurrentLinkedQueue[CliServerMessage], CliExecHeartbeat) => Unit): Unit = {
    supervised {
      RequestContext.current.supervisedWhere(
        RequestContext("client-1", "request-1", outputFormat = OutputFormat.Json)
      ) {
        val clock = new ManualClock()
        val emitted = new ConcurrentLinkedQueue[CliServerMessage]()
        val heartbeat = new CliExecHeartbeat(
          quietPeriod = Duration.ofSeconds(60),
          emit = msg => emitted.add(msg),
          now = clock.now,
          sleep = clock.sleep
        )
        try testCode(clock, emitted, heartbeat)
        finally if autoClose then heartbeat.close()
      }
    }
  }

  private def heartbeatLogs(emitted: ConcurrentLinkedQueue[CliServerMessage]): Seq[CliServerMessage.Log] =
    emitted.iterator().asScala.collect { case msg: CliServerMessage.Log => msg }.toSeq

  private def assertNoHeartbeat(emitted: ConcurrentLinkedQueue[CliServerMessage]): Unit = {
    assertEquals(heartbeatLogs(emitted), Seq.empty)
  }

  private def eventually(timeoutMillis: Long = 1000L)(assertion: => Unit): Unit = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var lastError: Option[Throwable] = None
    while System.currentTimeMillis() < deadline do
      try {
        assertion
        return
      } catch {
        case t: Throwable =>
          lastError = Some(t)
          Thread.sleep(10)
      }
    throw lastError.getOrElse(new AssertionError("condition was never satisfied"))
  }

  private final class ManualClock(start: Instant = Instant.parse("2024-01-01T00:00:00Z")) {
    private val lock = new Object
    private var current = start
    private var sleepers = 0
    private var sleepCount = 0L

    def now(): Instant = lock.synchronized(current)

    def sleep(duration: Duration): Unit = {
      val deadline = lock.synchronized {
        sleepCount += 1
        sleepers += 1
        lock.notifyAll()
        current.plus(duration)
      }

      try
        lock.synchronized {
          while current.isBefore(deadline) do lock.wait()
        }
      finally
        lock.synchronized {
          sleepers -= 1
          lock.notifyAll()
        }
    }

    def advanceBy(duration: Duration): Unit = lock.synchronized {
      current = current.plus(duration)
      lock.notifyAll()
    }

    def awaitSleepers(expected: Int, timeout: Duration = Duration.ofSeconds(1)): Unit =
      awaitCondition(timeout, s"Expected $expected sleepers, saw $sleepers") {
        sleepers == expected
      }

    def awaitNoSleepers(timeout: Duration = Duration.ofSeconds(1)): Unit =
      awaitSleepers(expected = 0, timeout = timeout)

    def awaitSleepCount(expected: Long, timeout: Duration = Duration.ofSeconds(1)): Unit =
      awaitCondition(timeout, s"Expected at least $expected sleeps, saw $sleepCount") {
        sleepCount >= expected
      }

    private def awaitCondition(timeout: Duration, failureMessage: => String)(condition: => Boolean): Unit =
      lock.synchronized {
        val deadline = System.nanoTime() + timeout.toNanos
        while !condition && System.nanoTime() < deadline do
          val remainingNanos = deadline - System.nanoTime()
          val remainingMillis = math.max(1L, Duration.ofNanos(remainingNanos).toMillis)
          lock.wait(remainingMillis)
        assert(condition, failureMessage)
      }
  }
}
