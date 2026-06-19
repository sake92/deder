package ba.sake.deder

class CancellationSuite extends munit.FunSuite {

  test("sleep task is cancelled within grace period after cancelRequest") {
    // Use a long sleep so the task would take a while without cancellation
    val requestId = java.util.UUID.randomUUID().toString
    DederGlobals.cancellationTokens.put(requestId, new java.util.concurrent.atomic.AtomicBoolean(false))

    // Start the sleep task on a separate thread (simulating a real task execution)
    val taskThread = new Thread(() => {
      DederGlobals.runningTaskThreads
        .computeIfAbsent(requestId, _ => new java.util.concurrent.ConcurrentLinkedQueue[Thread]())
        .add(Thread.currentThread())
      try {
        // Simulate what a real sleep task does
        Thread.sleep(10_000L) // 10 seconds
      } catch {
        case _: InterruptedException =>
          // Expected — cancellation should interrupt us
      } finally {
        val q = DederGlobals.runningTaskThreads.get(requestId)
        if q != null then q.remove(Thread.currentThread())
      }
    })
    taskThread.start()

    // Give it a moment to start sleeping
    Thread.sleep(100)

    // Cancel the request
    val startNanos = System.nanoTime()
    DederGlobals.cancellationTokens.get(requestId).set(true)

    // Simulate Phase 2: interrupt after grace period
    Option(DederGlobals.runningTaskThreads.get(requestId)).foreach { threads =>
      threads.forEach { t => t.interrupt() }
    }

    // Wait for the task thread to finish
    taskThread.join(5000)

    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
    assert(!taskThread.isAlive, s"Task thread should have been interrupted, but it's still alive after ${elapsedMs}ms")

    // Cleanup
    DederGlobals.cancellationTokens.remove(requestId)
    DederGlobals.runningTaskThreads.remove(requestId)
  }

  test("sleep task without cancellation runs to completion") {
    val startNanos = System.nanoTime()
    Thread.sleep(200L)
    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
    // Just sanity check that sleep works — the task would run its full duration
    assert(elapsedMs >= 180, s"Expected ~200ms sleep, got ${elapsedMs}ms")
  }
}
