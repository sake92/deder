package ba.sake.deder.testing

import scala.collection.mutable
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class OutputCaptureContextSuite extends munit.FunSuite {

  test("withCapture sets and clears ThreadLocal") {
    assert(OutputCaptureContext.currentNotificationsLogger.get() == null)
    assert(OutputCaptureContext.currentModuleId.get() == null)

    val notifications = mutable.ArrayBuffer[ServerNotification]()
    val logger = ServerNotificationsLogger(n => notifications += n)

    OutputCaptureContext.withCapture(logger, "test-module") {
      assert(OutputCaptureContext.currentNotificationsLogger.get() eq logger)
      assert(OutputCaptureContext.currentModuleId.get() == "test-module")
    }

    assert(OutputCaptureContext.currentNotificationsLogger.get() == null)
    assert(OutputCaptureContext.currentModuleId.get() == null)
  }

  test("withCapture clears ThreadLocal even on exception") {
    val notifications = mutable.ArrayBuffer[ServerNotification]()
    val logger = ServerNotificationsLogger(n => notifications += n)

    try {
      OutputCaptureContext.withCapture(logger, "test-module") {
        throw new RuntimeException("boom")
      }
    } catch {
      case _: RuntimeException => // expected
    }

    assert(OutputCaptureContext.currentNotificationsLogger.get() == null)
    assert(OutputCaptureContext.currentModuleId.get() == null)
  }
}
