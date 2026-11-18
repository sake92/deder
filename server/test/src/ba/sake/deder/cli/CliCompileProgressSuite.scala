package ba.sake.deder.cli

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

import ox.*
import ba.sake.deder.{RequestContext, ServerNotification}

class CliCompileProgressSuite extends munit.FunSuite {

  test("emits compile lifecycle and globally throttled progress") {
    withProgress(intervalMs = 500L) { (clock, emitted, progress) =>
      assert(progress.handle(ServerNotification.CompileStarted("common", Seq.fill(4)(os.temp()))))
      clock.advanceMillis(100L)
      progress.handle(ServerNotification.TaskProgress("common", "compile", progress = 1L, total = 4L))
      assertEquals(progressTexts(emitted), Seq("[common] Compiling 4 sources..."))

      clock.advanceMillis(500L)
      progress.handle(ServerNotification.TaskProgress("common", "compile", progress = 2L, total = 4L))
      assertEquals(
        progressTexts(emitted),
        Seq("[common] Compiling 4 sources...", "[common] Compile progress: 50%")
      )

      clock.advanceMillis(100L)
      progress.handle(ServerNotification.TaskProgress("common", "compile", progress = 3L, total = 4L))
      assertEquals(progressTexts(emitted).size, 2)

      clock.advanceMillis(500L)
      progress.handle(ServerNotification.TaskProgress("common", "compile", progress = 3L, total = 4L))
      assertEquals(progressTexts(emitted).size, 2)

      progress.handle(ServerNotification.TaskProgress("common", "compile", progress = 4L, total = 4L))
      assertEquals(progressTexts(emitted).last, "[common] Compile progress: 100%")

      clock.advanceMillis(1000L)
      progress.handle(ServerNotification.CompileFinished("common", errors = 0, warnings = 0))
      assert(progressTexts(emitted).last.startsWith("[common] Compiled in "))
    }
  }

  test("concurrent modules share one progress interval") {
    withProgress(intervalMs = 500L) { (clock, emitted, progress) =>
      progress.handle(ServerNotification.CompileStarted("common", Seq.fill(10)(os.temp())))
      clock.advanceMillis(100L)
      progress.handle(ServerNotification.CompileStarted("backend", Seq.fill(10)(os.temp())))

      clock.advanceMillis(500L)
      progress.handle(ServerNotification.TaskProgress("common", "compile", progress = 2L, total = 10L))
      clock.advanceMillis(100L)
      progress.handle(ServerNotification.TaskProgress("backend", "compile", progress = 3L, total = 10L))

      val texts = progressTexts(emitted)
      assertEquals(texts.count(_.contains("Compile progress:")), 1)
      assert(texts.exists(_.startsWith("[common] Compile progress: 20%")))
    }
  }

  test("zero interval disables all dedicated compile progress output") {
    withProgress(intervalMs = 0L) { (_, emitted, progress) =>
      progress.handle(ServerNotification.CompileStarted("common", Seq.fill(2)(os.temp())))
      progress.handle(ServerNotification.TaskProgress("common", "compile", progress = 1L, total = 2L))
      progress.handle(ServerNotification.CompileFinished("common", errors = 0, warnings = 0))
      progress.handle(ServerNotification.CompileFailed("common", errors = 1, warnings = 0))

      assertEquals(progressTexts(emitted), Seq.empty)
    }
  }

  test("failed compilation is an error and unrelated task progress is ignored") {
    withProgress(intervalMs = 500L) { (_, emitted, progress) =>
      assert(!progress.handle(ServerNotification.TaskProgress("common", "test", progress = 1L, total = 2L)))
      progress.handle(ServerNotification.CompileStarted("common", Seq.fill(2)(os.temp())))
      progress.handle(ServerNotification.CompileFailed("common", errors = 1, warnings = 2))

      val logs = emitted.iterator().asScala.collect { case log: CliServerMessage.Log => log }.toSeq
      assertEquals(logs.last.level, LogLevel.ERROR)
      assert(logs.last.text.contains("[common] Compilation failed: 1 error, 2 warnings"))
    }
  }

  private def withProgress(
      intervalMs: Long
  )(testCode: (ManualClock, ConcurrentLinkedQueue[CliServerMessage], CliCompileProgress) => Unit): Unit =
    supervised {
      RequestContext.current.supervisedWhere(RequestContext(noColor = true)) {
        val clock = new ManualClock
        val emitted = new ConcurrentLinkedQueue[CliServerMessage]()
        val progress = new CliCompileProgress(intervalMs, emitted.add, clock.nowNanos)
        testCode(clock, emitted, progress)
      }
    }

  private def progressTexts(emitted: ConcurrentLinkedQueue[CliServerMessage]): Seq[String] =
    emitted.iterator().asScala.collect { case log: CliServerMessage.Log => log.text }.toSeq

  private final class ManualClock {
    private var current = 0L
    def nowNanos(): Long = current
    def advanceMillis(millis: Long): Unit =
      current += millis * 1_000_000L
  }
}
