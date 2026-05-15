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
}
