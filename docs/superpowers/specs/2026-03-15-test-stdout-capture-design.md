# Test stdout/stderr Capture Design

## Problem

Tests in deder run in-process within an isolated ClassLoader (no forked JVM). Output from test code that uses `System.out.println()` / `System.err.println()` (or Scala's `println()`) goes directly to the server process's stdout/stderr and is never forwarded to the CLI client. Similarly, test framework output that bypasses the `sbt.testing.Logger` interface is lost.

The client only receives output routed through `DederTestLogger` → `ServerNotificationsLogger` → `CliServerMessage` → socket.

## Approach: Scoped Tee PrintStream with ThreadLocal Context

Install a `TeePrintStream` that wraps the original `System.out`/`System.err`. When a test thread writes output, the stream checks a `ThreadLocal` for an active capture context. If present, it also sends the output as a `ServerNotification.Log(INFO)` to the client. The original stream always receives the data (server-side logging unaffected).

## Components

### 1. `OutputCaptureContext` (new object)

**Location:** `server/src/ba/sake/deder/testing/OutputCaptureContext.scala`

Holds two `ThreadLocal` fields:
- `currentNotificationsLogger: ThreadLocal[ServerNotificationsLogger]`
- `currentModuleId: ThreadLocal[String]`

Provides a scoping method:

```scala
object OutputCaptureContext {
  private[deder] val currentNotificationsLogger = new ThreadLocal[ServerNotificationsLogger]()
  private[deder] val currentModuleId = new ThreadLocal[String]()

  def withCapture[T](logger: ServerNotificationsLogger, moduleId: String)(body: => T): T = {
    currentNotificationsLogger.set(logger)
    currentModuleId.set(moduleId)
    try body
    finally {
      currentNotificationsLogger.remove()
      currentModuleId.remove()
    }
  }
}
```

### 2. `TeePrintStream` (new class)

**Location:** `server/src/ba/sake/deder/testing/TeePrintStream.scala`

Wraps the original `PrintStream`. Buffers output per-line. On each complete line, checks `OutputCaptureContext.currentNotificationsLogger`. If set, sends a `ServerNotification.Log` at INFO level with the module ID from `OutputCaptureContext.currentModuleId`.

Key behaviors:
- **Line buffering:** accumulates bytes in a `ThreadLocal<StringBuilder>` until `\n` is encountered, then flushes as a single log message.
- **Always writes to the original stream:** the tee is additive, never suppresses output.
- **Thread-safe:** each thread has its own buffer via `ThreadLocal`. The `ServerNotificationsLogger.add()` call is already thread-safe.

```scala
class TeePrintStream(
    original: PrintStream,
    isStdErr: Boolean
) extends PrintStream(original, true) {

  private val lineBuffer = ThreadLocal.withInitial[StringBuilder](() => new StringBuilder())

  override def write(b: Int): Unit = {
    original.write(b)
    bufferByte(b.toByte)
  }

  override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
    original.write(buf, off, len)
    for (i <- off until off + len) bufferByte(buf(i))
  }

  private def bufferByte(b: Byte): Unit = {
    val logger = OutputCaptureContext.currentNotificationsLogger.get()
    if (logger == null) return
    val ch = b.toChar
    val sb = lineBuffer.get()
    if (ch == '\n') {
      flushLine(logger, sb.toString())
      sb.setLength(0)
    } else {
      sb.append(ch)
    }
  }

  private def flushLine(logger: ServerNotificationsLogger, line: String): Unit = {
    val moduleId = Option(OutputCaptureContext.currentModuleId.get())
    val prefix = if isStdErr then "[stderr] " else ""
    logger.add(ServerNotification.logInfo(s"$prefix$line", moduleId))
  }

  override def flush(): Unit = {
    original.flush()
    val logger = OutputCaptureContext.currentNotificationsLogger.get()
    if (logger != null) {
      val sb = lineBuffer.get()
      if (sb.nonEmpty) {
        flushLine(logger, sb.toString())
        sb.setLength(0)
      }
    }
  }
}
```

### 3. Installation at Server Startup

**Location:** `server/src/ba/sake/deder/DederServer.scala` (or equivalent entry point)

Install once during server initialization:

```scala
val teeOut = TeePrintStream(System.out, isStdErr = false)
val teeErr = TeePrintStream(System.err, isStdErr = true)
System.setOut(teeOut)
System.setErr(teeErr)
```

### 4. Usage in `DederTestRunner`

**Location:** `server/src/ba/sake/deder/testing/DederTestRunner.scala`

Wrap the test execution in `OutputCaptureContext.withCapture(...)`:

In `executeTasks()`, propagate the ThreadLocal into worker threads:

```scala
private def executeTasks(tasks: Seq[SbtTestTask], handler: EventHandler): Unit = {
  val currentRequestId = RequestContext.id.get()
  val notificationsLogger = OutputCaptureContext.currentNotificationsLogger.get()
  val moduleId = OutputCaptureContext.currentModuleId.get()
  val futures = {
    tasks.map { task =>
      executorService.submit { () =>
        // Propagate capture context to worker thread
        if (notificationsLogger != null) {
          OutputCaptureContext.currentNotificationsLogger.set(notificationsLogger)
          OutputCaptureContext.currentModuleId.set(moduleId)
        }
        try {
          val cancelled = currentRequestId != null && DederGlobals.cancellationTokens.get(currentRequestId).get()
          if cancelled then throw CancelledException("Tests execution cancelled")
          task.execute(handler, Array(logger))
        } finally {
          OutputCaptureContext.currentNotificationsLogger.remove()
          OutputCaptureContext.currentModuleId.remove()
        }
      }
    }
  }
  // ... rest unchanged
}
```

In `CoreTasks.scala`, wrap the test task `execute` body:

```scala
OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {
  // existing test execution code
}
```

This applies to all three test tasks: `testTask`, `testJsTask`, `testNativeTask`.

## Output Flow (After Change)

```
Test code: println("debug") / System.out.println("debug")
  ↓
TeePrintStream.write(...)
  ├─ original PrintStream (server stdout, as before)
  └─ OutputCaptureContext ThreadLocal check
      ↓ (if set)
      ServerNotificationsLogger.add(ServerNotification.Log(INFO, "[test-module] debug"))
        ↓
      CliServerMessage.Log → socket → client stderr
```

## Edge Cases

- **Threads spawned by test code:** If a test spawns its own threads (not through the ExecutorService), those threads won't have the ThreadLocal set. This is acceptable — truly isolated threads are rare in test code, and the alternative (agent-based capture) adds too much complexity.
- **Interleaved output from concurrent tests:** Multiple test threads may write concurrently. Since each thread has its own line buffer, lines won't get mixed. However, the order of log messages at the client may interleave across tests — this is expected and matches how concurrent test output works in other tools (sbt, mill, etc.).
- **Binary output:** The line-buffering approach assumes text. Binary data written to stdout during tests will be garbled in the notification. This is an acceptable limitation.
- **Logback output:** The server uses Logback which writes to `System.out`/`System.err`. After installing the tee, Logback output from test threads would also be captured. This could be noisy. Consider: Logback's own output typically goes to files, not stdout. If it does go to stdout, it would only be forwarded when a test thread is active, which is fine context.

## Files to Create/Modify

| File | Action |
|------|--------|
| `server/src/ba/sake/deder/testing/OutputCaptureContext.scala` | **Create** — ThreadLocal holder + `withCapture` scoping |
| `server/src/ba/sake/deder/testing/TeePrintStream.scala` | **Create** — Tee implementation with line buffering |
| `server/src/ba/sake/deder/DederServer.scala` (or entry point) | **Modify** — Install tee streams at startup |
| `server/src/ba/sake/deder/CoreTasks.scala` | **Modify** — Wrap 3 test tasks with `OutputCaptureContext.withCapture()` |
| `server/src/ba/sake/deder/testing/DederTestRunner.scala` | **Modify** — Propagate ThreadLocal to worker threads in `executeTasks()` |
| Unit tests for `TeePrintStream` | **Create** — Verify line buffering, tee behavior, thread isolation |
