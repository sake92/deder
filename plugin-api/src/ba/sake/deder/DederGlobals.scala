package ba.sake.deder

import java.util.concurrent.{ConcurrentHashMap, Semaphore}
import java.util.concurrent.atomic.AtomicBoolean

object DederGlobals {
  val version: String = getClass.getPackage.getImplementationVersion

  def projectRootDir: os.Path =
    val prop = System.getProperty("DEDER_PROJECT_ROOT_DIR")
    if prop == null then
      throw IllegalStateException(
        "DEDER_PROJECT_ROOT_DIR system property is not set. The server must be started with this property pointing to the project root directory."
      )
    os.Path(prop)

  val cancellationTokens: ConcurrentHashMap[String, AtomicBoolean] = new ConcurrentHashMap()

  /** Caps the number of forked test JVMs alive at any one time across the whole server.
    * Initialized once in ServerMain from the `maxConcurrentTestForks` server property
    * (default: Runtime.availableProcessors()). Acquired/released around each fork's spawn/exit
    * inside ForkedTestOrchestrator.
    */
  @volatile private var _testForkSemaphore: Semaphore = new Semaphore(Runtime.getRuntime.availableProcessors(), true)

  def setTestForkSemaphore(permits: Int): Unit =
    _testForkSemaphore = new Semaphore(math.max(1, permits), true)

  def testForkSemaphore: Semaphore = _testForkSemaphore

  /** Interval in milliseconds for periodic flushing of suite output from forked test JVMs.
    * Set to 0 to disable (output only appears when each suite completes).
    * Initialized once in ServerMain from the `forkTestFlushIntervalMs` server property.
    */
  @volatile private var _forkTestFlushIntervalMs: Long = 1000L

  def setForkTestFlushIntervalMs(ms: Long): Unit =
    _forkTestFlushIntervalMs = math.max(0L, ms)

  def forkTestFlushIntervalMs: Long = _forkTestFlushIntervalMs
}
