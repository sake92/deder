package ba.sake.deder.testing.forked

import java.io.{ByteArrayOutputStream, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import scala.util.control.NonFatal
import ba.sake.tupson.{*, given}

/** Captures per-suite stdout in a forked JVM and emits JSON-lines envelopes back to the orchestrator on the original
  * stdout (the one the parent process actually reads).
  *
  * The capturing PrintStream is installed once in ForkedTestMain before any test framework runs. Per-suite buffers are
  * keyed off a ThreadLocal — a worker thread executing a suite appends its prints into the suite's buffer; on suite
  * completion the buffer is drained into a SuiteCompleted envelope. Bytes written while no suite is active for this
  * thread are emitted as UnattributedOutput envelopes, flushed per newline to keep them ordered but not per-byte.
  */
class ForkedTestReporter private (original: PrintStream) {
  private val emitLock = new Object

  def emit(env: ForkedTestEnvelope): Unit = emitLock.synchronized {
    try {
      original.print(ForkedTestEnvelope.LinePrefix)
      original.println(env.toJson(spaces = 0, sort = false))
      original.flush()
    } catch {
      case NonFatal(_) => ()
    }
  }
}

object ForkedTestReporter {

  /** Installs a capturing PrintStream on System.out and returns a reporter + capture handle. Must be called BEFORE any
    * logger captures System.out, so the capturing stream is the one downstream code writes through.
    */
  def install(flushIntervalMs: Long = 0): (ForkedTestReporter, SuiteOutputCapture) = {
    val originalOut = System.out
    val reporter = new ForkedTestReporter(originalOut)
    val capture = new SuiteOutputCapture(reporter, flushIntervalMs)
    val capturing = new PrintStream(capture, true, StandardCharsets.UTF_8)
    capture.setCapturingStream(capturing)
    System.setOut(capturing)
    (reporter, capture)
  }
}

/** OutputStream that routes bytes into the currently-active per-thread suite buffer, or emits UnattributedOutput
  * envelopes when no suite is active on the current thread. The unattributed path is newline-flushed so we don't emit
  * one envelope per byte under normal text output.
  *
  * When `flushIntervalMs` > 0, periodically flushes the per-suite buffer as `SuiteProgress` envelopes while a suite
  * is running, so the orchestrator can show incremental test output instead of waiting until suite completion.
  * Suite buffers are stored in a ConcurrentHashMap (not ThreadLocal) so the periodic flush task running on the
  * flush executor thread can access them.
  */
class SuiteOutputCapture(reporter: ForkedTestReporter, flushIntervalMs: Long = 0) extends OutputStream {

  // Suite buffers keyed by suite name, accessible from any thread (needed for periodic flush)
  private val suiteBuffers = new ConcurrentHashMap[String, ByteArrayOutputStream]()
  // Per-thread tracking of the currently active suite name (used by write() to find the right buffer)
  private val suiteNameTL = new ThreadLocal[String]
  private val unattributedBuffer = new ThreadLocal[ByteArrayOutputStream] {
    override def initialValue: ByteArrayOutputStream = new ByteArrayOutputStream()
  }

  @volatile private var flushRunning = true

  // Dedicated virtual thread for periodic flush of all suite buffers
  private val flushThread: Thread = {
    val delay = Math.max(flushIntervalMs, 100)
    Thread.ofVirtual().name("suite-output-flush").start(() => {
      while (flushRunning) {
        try {
          Thread.sleep(delay)
          flushAllSuiteBuffers()
        } catch {
          case _: InterruptedException => flushRunning = false
        }
      }
    })
  }

  private def flushAllSuiteBuffers(): Unit = {
    val entries = suiteBuffers.entrySet().iterator()
    while (entries.hasNext) {
      val entry = entries.next()
      val buf = entry.getValue
      buf.synchronized {
        if (buf.size() > 0) {
          val text = buf.toString(StandardCharsets.UTF_8)
          buf.reset()
          if (text.nonEmpty)
            reporter.emit(ForkedTestEnvelope.SuiteProgress(entry.getKey, text))
        }
      }
    }
  }

  // Stored so startSuite can reset System.out if a prior test forgot to restore it.
  @volatile private var capturingStream: PrintStream = scala.compiletime.uninitialized
  def setCapturingStream(ps: PrintStream): Unit = capturingStream = ps

  def startSuite(suiteName: String): Unit = {
    suiteNameTL.set(suiteName)
    suiteBuffers.put(suiteName, new ByteArrayOutputStream())
    val cs = capturingStream
    // In case a test (or framework) forgets to restore it, reset it here
    if cs != null && (System.out ne cs) then System.setOut(cs)
  }

  def finishSuite(): String = {
    val name = suiteNameTL.get()
    suiteNameTL.remove()
    val buf = if name != null then suiteBuffers.remove(name) else null
    if buf == null then "" else buf.toString(StandardCharsets.UTF_8)
  }

  override def write(b: Int): Unit = {
    val name = suiteNameTL.get()
    val buf = if name != null then suiteBuffers.get(name) else null
    if buf != null then buf.write(b)
    else {
      val ub = unattributedBuffer.get()
      ub.write(b)
      if b == '\n' then flushUnattributed(ub)
    }
  }

  override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
    val name = suiteNameTL.get()
    val buf = if name != null then suiteBuffers.get(name) else null
    if buf != null then buf.write(bytes, off, len)
    else {
      val ub = unattributedBuffer.get()
      // Flush line-by-line so UnattributedOutput envelopes stay newline-delimited.
      var i = off
      val end = off + len
      var segmentStart = off
      while i < end do {
        if bytes(i) == '\n' then {
          ub.write(bytes, segmentStart, i - segmentStart + 1)
          flushUnattributed(ub)
          segmentStart = i + 1
        }
        i += 1
      }
      if segmentStart < end then ub.write(bytes, segmentStart, end - segmentStart)
    }
  }

  override def flush(): Unit = {
    val name = suiteNameTL.get()
    val buf = if name != null then suiteBuffers.get(name) else null
    if buf == null then {
      val ub = unattributedBuffer.get()
      if ub.size() > 0 then flushUnattributed(ub)
    }
  }

  private def flushUnattributed(ub: ByteArrayOutputStream): Unit = {
    val text = ub.toString(StandardCharsets.UTF_8)
    ub.reset()
    if text.nonEmpty then reporter.emit(ForkedTestEnvelope.UnattributedOutput(text))
  }
}
