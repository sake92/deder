package ba.sake.deder

import java.time.{Duration, Instant}
import ba.sake.tupson.JsonRW

enum CallerType:
  case Cli, Bsp, Plugin

case class LiveRequest(
    requestId: String,
    caller: CallerType,
    taskName: String,
    moduleIds: Seq[String],
    startTime: Instant
)

case class CompletedRequest(
    requestId: String,
    caller: CallerType,
    taskName: String,
    moduleIds: Seq[String],
    startTime: Instant,
    duration: Duration,
    success: Boolean,
    error: Option[String]
)

case class DurationDistribution(
    count: Long,
    min: Duration,
    max: Duration,
    mean: Duration,
    p50: Duration,
    p95: Duration,
    p99: Duration
)

case class TaskStats(
    executions: Long,
    cacheHits: Long,
    errors: Long,
    lastError: Option[String],
    duration: DurationDistribution
)

case class LoadedPluginInfo(
    id: String,
    taskNames: Seq[String],
    error: Option[String] = None
) derives JsonRW

enum RequestState:
  case QUEUED           // request received, not yet acquiring locks
  case ACQUIRING_LOCKS  // in the lock acquisition loop
  case EXECUTING        // locks acquired, tasks running
  case COMPLETED        // done (then moves to recentHistory)

case class LockProgress(
    acquired: Int,                 // locks acquired so far (0..total)
    total: Int,                    // total locks needed
    blockingOn: Option[String],    // TaskInstance id we're currently waiting on (e.g. "foo.compile")
    heldBy: Option[String]         // requestId that holds the blocking lock (if any)
)

case class TaskStageProgress(
    currentStage: Int,
    totalStages: Int,
    completed: Seq[String],   // TaskInstance ids
    failed: Seq[String],
    skipped: Seq[String],
    running: Seq[String],     // currently executing
    pending: Seq[String]      // not yet started in current stage
)

case class RequestStatus(
    requestId: String,
    caller: CallerType,
    taskName: String,
    moduleIds: Seq[String],
    startTime: Instant,
    state: RequestState,
    lockProgress: Option[LockProgress],    // set during ACQUIRING_LOCKS
    taskProgress: Option[TaskStageProgress] // set during EXECUTING
)

trait DederProjectInternals:
  /** Currently executing top-level requests (CLI or BSP). */
  def currentRequests: Seq[LiveRequest]

  /** Last N completed requests, most recent first. Bounded. */
  def recentHistory: Seq[CompletedRequest]

  /** Per-task aggregated stats for server process lifetime. */
  def taskStats(taskName: String): Option[TaskStats]
  def allTaskStats: Seq[(String, TaskStats)]

  /** Lifetime counters across all callers and tasks. */
  def totalRequestsServed: Long
  def totalErrors: Long

  /** Currently loaded plugins and their registered tasks. */
  def loadedPlugins: Seq[LoadedPluginInfo]

  /** Per-cache stats keyed by cache identifier.
   *  Keys: "dep-resolver", "zinc-compilers", "zinc-setup:<version>",
   *        "test-classloaders"
   */
  def inMemoryCachesStats: Map[String, InMemCacheStats]

  /** Server metadata. */
  def serverUptime: Duration

  /** Cancel an in-flight request by its requestId.
    * Returns true if the request was found and cancellation was triggered.
    * Returns false if the requestId is unknown or already completed. */
  def cancelRequest(requestId: String): Boolean

  /** Rich status snapshot for a single in-flight request, or None if not found. */
  def requestStatus(requestId: String): Option[RequestStatus]

  /** Rich status snapshots for all in-flight requests. */
  def allRequestStatuses: Seq[RequestStatus]

  /** Execute a named task on the given modules and block until completion.
    * Goes through the same execution pipeline as CLI requests (planning, locking, caching).
    * Returns one outcome per module.
    *
    * @param taskName   e.g. "compile", "test", "run"
    * @param moduleIds  module IDs to execute on; empty = all modules
    * @param args       forwarded to tasks as `ctx.args` (e.g. test filter args)
    * @return           per-module outcomes
    */
  def invoke(
      taskName: String,
      moduleIds: Seq[String],
      args: Seq[String]
  ): Seq[TaskInvokeOutcome]

  /** Purges all registered in-memory caches (Scaffeine caches, internals history, completed
    * BSP in-flight compilation entries) and suggests a GC to the JVM.
    * Waits up to 10s for in-flight requests to drain; returns a zeroed result if busy. */
  def purgeInMemoryCaches(): PurgeCachesResult

case class PurgeCachesResult(
    cachesCleared: Int,
    bspEntriesRemoved: Int,
    historyEntriesRemoved: Int,
    gcSuggested: Boolean
)

/** Per-module outcome of a dynamic task invocation via [[DederProjectInternals.invoke]]. */
case class TaskInvokeOutcome(
    moduleId: String,
    success: Boolean,
    error: Option[String],
    fromCache: Boolean
) derives JsonRW

/** Aggregated result of a dynamic task invocation, including per-module outcomes
  * and a plaintext cross-module summary (same as CLI output).
  */
case class TaskInvokeResult(
    outcomes: Seq[TaskInvokeOutcome],
    renderedSummary: Option[String]
) derives JsonRW
