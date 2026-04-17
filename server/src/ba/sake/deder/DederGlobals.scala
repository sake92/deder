package ba.sake.deder

import java.util.concurrent.{ConcurrentHashMap, Semaphore}
import java.util.concurrent.atomic.AtomicBoolean

object DederGlobals {
  val version: String = getClass.getPackage.getImplementationVersion

  val projectRootDir: os.Path = os.Path(System.getProperty("DEDER_PROJECT_ROOT_DIR"))

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
}
