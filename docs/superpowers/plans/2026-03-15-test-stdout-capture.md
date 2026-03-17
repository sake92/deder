# Test stdout/stderr Capture Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Forward `System.out`/`System.err` output from in-process test execution to the CLI client via `ServerNotificationsLogger`.

**Architecture:** A `TeePrintStream` wraps the original stdout/stderr, installed once at server startup. A `ThreadLocal`-based `OutputCaptureContext` scopes capture to test threads only. Output is line-buffered and sent as `ServerNotification.Log(INFO)`.

**Tech Stack:** Scala 3, `java.io.PrintStream`, `ThreadLocal`, munit for tests

**Spec:** `docs/superpowers/specs/2026-03-15-test-stdout-capture-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `server/src/ba/sake/deder/testing/OutputCaptureContext.scala` | Create | ThreadLocal holder + `withCapture` scoping |
| `server/src/ba/sake/deder/testing/TeePrintStream.scala` | Create | Tee PrintStream with per-thread line buffering |
| `server/src/ba/sake/deder/ServerMain.scala` | Modify (line ~35) | Install tee streams at startup |
| `server/src/ba/sake/deder/CoreTasks.scala` | Modify (lines 942, 1009, 1127) | Wrap 3 test task execute blocks with `OutputCaptureContext.withCapture()` |
| `server/src/ba/sake/deder/testing/DederTestRunner.scala` | Modify (lines 118-137) | Propagate ThreadLocal to worker threads |
| `server/test/src/ba/sake/deder/testing/TeePrintStreamSuite.scala` | Create | Unit tests for TeePrintStream |
| `server/test/src/ba/sake/deder/testing/OutputCaptureContextSuite.scala` | Create | Unit tests for OutputCaptureContext |

---

## Chunk 1: Core Components

### Task 1: Create `OutputCaptureContext`

**Files:**
- Create: `server/src/ba/sake/deder/testing/OutputCaptureContext.scala`
- Test: `server/test/src/ba/sake/deder/testing/OutputCaptureContextSuite.scala`

- [ ] **Step 1: Write the failing test**

Create `server/test/src/ba/sake/deder/testing/OutputCaptureContextSuite.scala`:

```scala
package ba.sake.deder.testing

import scala.collection.mutable
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class OutputCaptureContextSuite extends munit.FunSuite {

  test("withCapture sets and clears ThreadLocal") {
    // before: not set
    assert(OutputCaptureContext.currentNotificationsLogger.get() == null)
    assert(OutputCaptureContext.currentModuleId.get() == null)

    val notifications = mutable.ArrayBuffer[ServerNotification]()
    val logger = ServerNotificationsLogger(n => notifications += n)

    OutputCaptureContext.withCapture(logger, "test-module") {
      // inside: set
      assert(OutputCaptureContext.currentNotificationsLogger.get() eq logger)
      assert(OutputCaptureContext.currentModuleId.get() == "test-module")
    }

    // after: cleared
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mill server.test.testOnly ba.sake.deder.testing.OutputCaptureContextSuite`
Expected: Compilation error — `OutputCaptureContext` does not exist.

- [ ] **Step 3: Write implementation**

Create `server/src/ba/sake/deder/testing/OutputCaptureContext.scala`:

```scala
package ba.sake.deder.testing

import ba.sake.deder.ServerNotificationsLogger

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

- [ ] **Step 4: Run test to verify it passes**

Run: `./mill server.test.testOnly ba.sake.deder.testing.OutputCaptureContextSuite`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/ba/sake/deder/testing/OutputCaptureContext.scala \
       server/test/src/ba/sake/deder/testing/OutputCaptureContextSuite.scala
git commit -m "feat: add OutputCaptureContext for scoped test output capture"
```

---

### Task 2: Create `TeePrintStream`

**Files:**
- Create: `server/src/ba/sake/deder/testing/TeePrintStream.scala`
- Test: `server/test/src/ba/sake/deder/testing/TeePrintStreamSuite.scala`

- [ ] **Step 1: Write the failing tests**

Create `server/test/src/ba/sake/deder/testing/TeePrintStreamSuite.scala`:

```scala
package ba.sake.deder.testing

import java.io.{ByteArrayOutputStream, PrintStream}
import scala.collection.mutable
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class TeePrintStreamSuite extends munit.FunSuite {

  private def setup(): (ByteArrayOutputStream, TeePrintStream, mutable.ArrayBuffer[ServerNotification], ServerNotificationsLogger) = {
    val originalBaos = new ByteArrayOutputStream()
    val original = new PrintStream(originalBaos, true)
    val notifications = mutable.ArrayBuffer[ServerNotification]()
    val logger = ServerNotificationsLogger(n => notifications += n)
    val tee = TeePrintStream(original, isStdErr = false)
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
    // original gets it
    assert(originalBaos.toString.contains("captured line"))
    // notification sent
    val logMessages = notifications.collect {
      case ServerNotification.Log(_, _, msg, _) => msg
    }
    assert(logMessages.exists(_.contains("captured line")))
  }

  test("buffers per-line, sends complete lines only") {
    val (_, tee, notifications, slogger) = setup()
    OutputCaptureContext.withCapture(slogger, "mod1") {
      tee.print("part1")
      // no notification yet (no newline)
      val logsSoFar = notifications.collect {
        case ServerNotification.Log(_, _, msg, _) => msg
      }
      assert(logsSoFar.isEmpty, s"Expected no logs yet, got: $logsSoFar")
      tee.println(" part2") // this adds newline
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

  test("stderr prefix is added for stderr tee") {
    val originalBaos = new ByteArrayOutputStream()
    val original = new PrintStream(originalBaos, true)
    val notifications = mutable.ArrayBuffer[ServerNotification]()
    val slogger = ServerNotificationsLogger(n => notifications += n)
    val tee = TeePrintStream(original, isStdErr = true)
    OutputCaptureContext.withCapture(slogger, "mod1") {
      tee.println("error msg")
    }
    val logMessages = notifications.collect {
      case ServerNotification.Log(_, _, msg, _) => msg
    }
    assert(logMessages.exists(_.contains("[stderr]")))
  }

  test("thread isolation - only capturing thread sends notifications") {
    val (_, tee, notifications, slogger) = setup()
    // Thread without capture context
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mill server.test.testOnly ba.sake.deder.testing.TeePrintStreamSuite`
Expected: Compilation error — `TeePrintStream` does not exist.

- [ ] **Step 3: Write implementation**

Create `server/src/ba/sake/deder/testing/TeePrintStream.scala`:

```scala
package ba.sake.deder.testing

import java.io.{OutputStream, PrintStream}
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class TeePrintStream(
    original: PrintStream,
    isStdErr: Boolean
) extends PrintStream(new TeePrintStream.TeeOutputStream(original, isStdErr), true) {

  // Delegate flush to also flush any partial line buffer
  override def flush(): Unit = {
    super.flush()
    val logger = OutputCaptureContext.currentNotificationsLogger.get()
    if (logger != null) {
      val sb = TeePrintStream.lineBuffer.get()
      if (sb.nonEmpty) {
        TeePrintStream.flushLine(logger, sb.toString(), isStdErr)
        sb.setLength(0)
      }
    }
  }
}

object TeePrintStream {
  private[testing] val lineBuffer: ThreadLocal[StringBuilder] =
    ThreadLocal.withInitial(() => new StringBuilder())

  private def flushLine(
      logger: ServerNotificationsLogger,
      line: String,
      isStdErr: Boolean
  ): Unit = {
    val moduleId = Option(OutputCaptureContext.currentModuleId.get())
    val prefix = if isStdErr then "[stderr] " else ""
    logger.add(ServerNotification.logInfo(s"$prefix$line", moduleId))
  }

  private class TeeOutputStream(
      original: PrintStream,
      isStdErr: Boolean
  ) extends OutputStream {

    override def write(b: Int): Unit = {
      original.write(b)
      bufferByte(b.toByte)
    }

    override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
      original.write(buf, off, len)
      for (i <- off until off + len) bufferByte(buf(i))
    }

    override def flush(): Unit = {
      original.flush()
    }

    private def bufferByte(b: Byte): Unit = {
      val logger = OutputCaptureContext.currentNotificationsLogger.get()
      if (logger == null) return
      val ch = b.toChar
      val sb = lineBuffer.get()
      if (ch == '\n') {
        flushLine(logger, sb.toString(), isStdErr)
        sb.setLength(0)
      } else {
        sb.append(ch)
      }
    }
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mill server.test.testOnly ba.sake.deder.testing.TeePrintStreamSuite`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add server/src/ba/sake/deder/testing/TeePrintStream.scala \
       server/test/src/ba/sake/deder/testing/TeePrintStreamSuite.scala
git commit -m "feat: add TeePrintStream with per-thread line-buffered tee"
```

---

## Chunk 2: Integration

### Task 3: Install TeePrintStream at server startup

**Files:**
- Modify: `server/src/ba/sake/deder/ServerMain.scala:35`

- [ ] **Step 1: Add tee installation after `System.setProperty` and before logging starts**

In `ServerMain.scala`, after line 35 (`System.setProperty("DEDER_PROJECT_ROOT_DIR", ...)`), add:

```scala
    // Install tee streams to capture test stdout/stderr for CLI forwarding
    val teeOut = testing.TeePrintStream(System.out, isStdErr = false)
    val teeErr = testing.TeePrintStream(System.err, isStdErr = true)
    System.setOut(teeOut)
    System.setErr(teeErr)
```

Add the import at the top of the file:

```scala
import ba.sake.deder.testing
```

- [ ] **Step 2: Verify server still compiles**

Run: `./mill server.compile`
Expected: Compilation succeeds.

- [ ] **Step 3: Commit**

```bash
git add server/src/ba/sake/deder/ServerMain.scala
git commit -m "feat: install TeePrintStream at server startup"
```

---

### Task 4: Wrap test tasks with OutputCaptureContext

**Files:**
- Modify: `server/src/ba/sake/deder/CoreTasks.scala:942, 1009, 1127`

- [ ] **Step 1: Add import to CoreTasks.scala**

At the top of `CoreTasks.scala`, add:

```scala
import ba.sake.deder.testing.OutputCaptureContext
```

- [ ] **Step 2: Wrap `testTask` execute body (line 942)**

Change the execute block from:

```scala
      execute = { ctx =>
        val (classesDir, runClasspath) = ctx.depResults
        val runtimeClasspath = ...
        ClassLoaderUtils.withIsolatedClassLoader(...) { classLoader =>
          ...
        }
      },
```

To:

```scala
      execute = { ctx =>
        OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {
          val (classesDir, runClasspath) = ctx.depResults
          val runtimeClasspath = ...
          ClassLoaderUtils.withIsolatedClassLoader(...) { classLoader =>
            ...
          }
        }
      },
```

Concretely, insert `OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {` after `execute = { ctx =>` (line 942) and add a closing `}` before `},` (line 963).

- [ ] **Step 3: Wrap `testJsTask` execute body (line 1009)**

Same pattern. Insert `OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {` after `execute = { ctx =>` (line 1009) and add closing `}` before `},` (line 1028).

- [ ] **Step 4: Wrap `testNativeTask` execute body (line 1127)**

Same pattern. Insert `OutputCaptureContext.withCapture(ctx.notifications, ctx.module.id) {` after `execute = { ctx =>` (line 1127) and add closing `}` before `},` (line 1146).

- [ ] **Step 5: Verify compilation**

Run: `./mill server.compile`
Expected: Compilation succeeds.

- [ ] **Step 6: Commit**

```bash
git add server/src/ba/sake/deder/CoreTasks.scala
git commit -m "feat: wrap test tasks with OutputCaptureContext"
```

---

### Task 5: Propagate ThreadLocal to test worker threads

**Files:**
- Modify: `server/src/ba/sake/deder/testing/DederTestRunner.scala:118-137`

- [ ] **Step 1: Modify `executeTasks` to propagate capture context**

Replace the `executeTasks` method (lines 118-137) with:

```scala
  private def executeTasks(tasks: Seq[SbtTestTask], handler: EventHandler): Unit = {
    val currentRequestId = RequestContext.id.get()
    // Capture current thread's OutputCaptureContext for propagation
    val capturedNotificationsLogger = OutputCaptureContext.currentNotificationsLogger.get()
    val capturedModuleId = OutputCaptureContext.currentModuleId.get()
    val futures = {
      tasks.map { task =>
        executorService.submit { () =>
          // Propagate capture context to worker thread
          if (capturedNotificationsLogger != null) {
            OutputCaptureContext.currentNotificationsLogger.set(capturedNotificationsLogger)
            OutputCaptureContext.currentModuleId.set(capturedModuleId)
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
      // val nestedTasks = ... TODO
    }
    try futures.map(_.get())
    catch {
      case _: CancelledException =>
        // if one task fails (maybe cancelled..), cancel all other pending tasks
        logger.warn(s"Cancelling remaining tests...")
        futures.foreach(_.cancel(true))
    }
  }
```

- [ ] **Step 2: Verify compilation**

Run: `./mill server.compile`
Expected: Compilation succeeds.

- [ ] **Step 3: Run all existing server tests to check for regressions**

Run: `./mill server.test`
Expected: All existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add server/src/ba/sake/deder/testing/DederTestRunner.scala
git commit -m "feat: propagate OutputCaptureContext to test worker threads"
```

---

## Chunk 3: Verification

### Task 6: Run full test suite and verify

- [ ] **Step 1: Run all unit tests**

Run: `./mill server.test`
Expected: All tests pass (existing + new).

- [ ] **Step 2: Manual smoke test (if integration test setup is available)**

Run: `./scripts/run-it-tests.sh`
Expected: Integration tests pass. Test output from sample projects that use `println()` should now appear in CLI output.

- [ ] **Step 3: Final commit (if any fixups needed)**

```bash
git add -A
git commit -m "fix: address any issues from test verification"
```
