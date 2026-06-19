package ba.sake.deder

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, Executors, ScheduledExecutorService, ScheduledFuture, Semaphore, TimeUnit}
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

  // Conventional output paths for a module. These are compile *outputs*, derived from
  // the `.deder/out/<module>/compile/<name>` layout — NOT modeled as tasks. Modeling them
  // as tasks (the old classesTask/semanticdbDirTask) made consumers content-hash dirs that
  // compile writes, which is self-referential. Derive the path; get change-detection from
  // compileTask. All compiler outputs (classes, semanticdb, zinc, generatedSources) are
  // now grouped under `.deder/out/<module>/compile/`.
  def moduleOutDir(moduleId: String, name: String): os.Path =
    projectRootDir / ".deder/out" / moduleId / name
  /** Compile task's output dir — all compiler-produced artifacts live under here. */
  private def compileOutDir(moduleId: String): os.Path = moduleOutDir(moduleId, "compile")
  def classesDir(moduleId: String): os.Path = compileOutDir(moduleId) / "classes"
  def semanticdbDir(moduleId: String): os.Path = compileOutDir(moduleId) / "semanticdb"
  def generatedSourcesDir(moduleId: String): os.Path = compileOutDir(moduleId) / "generatedSources"

  /** Classes dirs for a module and all its (transitive) dependency modules, this module first,
    * then dependents in the order given by `dependentModuleIds` (flattened from
    * `ctx.dependentModulesTree`, which is longest-path layered so shared foundational deps come
    * last). Replaces the old `classesTask`/`allClassesDirsTask` quasi-tasks: a pure mapping, no
    * I/O and no content-hashing — change-detection comes from each consumer's `compileTask` dep.
    *
    * Deliberately does NOT dedup: deduplication/shadowing is owned by [[Classpath]] (its `++`
    * keeps the last occurrence). Wrap the result in a `Classpath` to dedup. */
  def allClassesDirs(moduleId: String, dependentModuleIds: Seq[String]): Seq[os.Path] =
    (moduleId +: dependentModuleIds).map(classesDir)

  val cancellationTokens: ConcurrentHashMap[String, AtomicBoolean] = new ConcurrentHashMap()

  // Track running task threads per requestId so they can be interrupted on cancellation
  val runningTaskThreads: ConcurrentHashMap[String, ConcurrentLinkedQueue[Thread]] = new ConcurrentHashMap()

  // Single-thread scheduler for delayed thread interrupts (Phase 2 of cancellation)
  val interruptScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r => {
    val t = new Thread(r, "deder-cancel-interrupt")
    t.setDaemon(true)
    t
  })

  // Track scheduled interrupt futures so they can be cancelled if the request
  // completes cooperatively before the grace period expires
  val interruptFutures: ConcurrentHashMap[String, ScheduledFuture[?]] = new ConcurrentHashMap()

  // Maps CLI clientId -> requestId so that when a client disconnects (Ctrl+C)
  // the server can cancel the in-flight request automatically.
  val clientRequestMap: ConcurrentHashMap[String, String] = new ConcurrentHashMap()

  /** Caps the number of forked test JVMs alive at any one time across the whole server.
    * Configured from `deder.pkl` (`maxConcurrentTestForks`), defaulting to available CPU cores.
    * Acquired/released around each fork's spawn/exit inside ForkedTestOrchestrator.
    */
  @volatile private var _testForkSemaphore: Semaphore = new Semaphore(Runtime.getRuntime.availableProcessors(), true)

  def setTestForkSemaphore(permits: Int): Unit = synchronized {
    val target = math.max(1, permits)
    val current = _testForkSemaphore
    current.drainPermits()
    current.release(target)
  }

  def testForkSemaphore: Semaphore = _testForkSemaphore

  /** Caps the number of concurrent Zinc compilations across the whole server.
    * Configured from `deder.pkl` (`maxActiveCompilers`), defaulting to available CPU cores.
    * Acquired/released around each Zinc `compile()` call in CoreTasks.
    */
  @volatile private var _compileSemaphore: Semaphore = new Semaphore(Runtime.getRuntime.availableProcessors(), true)

  def setCompileSemaphore(permits: Int): Unit = synchronized {
    val target = math.max(1, permits)
    val current = _compileSemaphore
    current.drainPermits()
    current.release(target)
  }

  def compileSemaphore: Semaphore = _compileSemaphore

  /** Interval in milliseconds for periodic flushing of suite output from forked test JVMs.
    * Set to 0 to disable (output only appears when each suite completes).
    * Initialized once in ServerMain from the `forkTestFlushIntervalMs` server property.
    */
  @volatile private var _forkTestFlushIntervalMs: Long = 1000L

  def setForkTestFlushIntervalMs(ms: Long): Unit =
    _forkTestFlushIntervalMs = math.max(0L, ms)

  def forkTestFlushIntervalMs: Long = _forkTestFlushIntervalMs
}
