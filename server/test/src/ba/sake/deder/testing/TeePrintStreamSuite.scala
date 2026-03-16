package ba.sake.deder.testing

import java.io.{ByteArrayOutputStream, PrintStream}
import scala.collection.mutable
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class TeePrintStreamSuite extends munit.FunSuite {

  private def setup(isStdErr: Boolean = false) = {
    val originalBaos = new ByteArrayOutputStream()
    val original = new PrintStream(originalBaos, true)
    val notifications = mutable.ArrayBuffer[ServerNotification]()
    val logger = ServerNotificationsLogger(n => notifications += n)
    val tee = TeePrintStream(original, isStdErr = isStdErr)
    (originalBaos, tee, notifications, logger)
  }

  test("writes to original stream always") {
    val (originalBaos, tee, _, _) = setup()
    tee.println("hello")
    assert(originalBaos.toString.contains("hello"))
  }

  test("does not tee when no capture context is set") {
    val (_, tee, notifications, _) = setup()
    tee.println("hello")
    assert(notifications.isEmpty)
  }

  test("tees output when capture context is set") {
    val (originalBaos, tee, notifications, slogger) = setup()
    OutputCaptureContext.withCapture(slogger, "mod1") {
      tee.println("captured line")
    }
    assert(originalBaos.toString.contains("captured line"))
    val logMessages = notifications.collect {
      case ServerNotification.Log(_, _, msg, _) => msg
    }
    assert(logMessages.exists(_.contains("captured line")))
  }

  test("buffers per-line, sends complete lines only") {
    val (_, tee, notifications, slogger) = setup()
    OutputCaptureContext.withCapture(slogger, "mod1") {
      tee.print("part1")
      val logsSoFar = notifications.collect {
        case ServerNotification.Log(_, _, msg, _) => msg
      }
      assert(logsSoFar.isEmpty, s"Expected no logs yet, got: $logsSoFar")
      tee.println(" part2")
    }
    val logMessages = notifications.collect {
      case ServerNotification.Log(_, _, msg, _) => msg
    }
    assert(logMessages.exists(_.contains("part1 part2")))
  }

  test("flush sends partial line") {
    val (_, tee, notifications, slogger) = setup()
    OutputCaptureContext.withCapture(slogger, "mod1") {
      tee.print("partial")
      tee.flush()
    }
    val logMessages = notifications.collect {
      case ServerNotification.Log(_, _, msg, _) => msg
    }
    assert(logMessages.exists(_.contains("partial")))
  }

  test("thread isolation - only capturing thread sends notifications") {
    val (_, tee, notifications, slogger) = setup()
    val otherThread = new Thread(() => {
      tee.println("from other thread")
    })
    OutputCaptureContext.withCapture(slogger, "mod1") {
      otherThread.start()
      otherThread.join()
      tee.println("from capture thread")
    }
    val logMessages = notifications.collect {
      case ServerNotification.Log(_, _, msg, _) => msg
    }
    assert(logMessages.exists(_.contains("from capture thread")))
    assert(!logMessages.exists(_.contains("from other thread")))
  }

  test("handles multi-byte UTF-8 characters correctly") {
    val (originalBaos, tee, notifications, slogger) = setup()
    OutputCaptureContext.withCapture(slogger, "mod1") {
      tee.println("Hello 世界 🎉")
    }
    assert(originalBaos.toString.contains("Hello 世界 🎉"))
    val logMessages = notifications.collect {
      case ServerNotification.Log(_, _, msg, _) => msg
    }
    assert(logMessages.exists(_.contains("Hello 世界 🎉")), s"Expected UTF-8 output, got: $logMessages")
  }
}
