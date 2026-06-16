package ba.sake.deder

import java.time.{Duration, Instant}
import ba.sake.tupson.JsonRW

enum CallerType:
  case Cli, Bsp

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
   *  Keys: "dep-resolver", "zinc-compilers", "zinc-analysis:<version>",
   *        "zinc-setup:<version>", "test-classloaders"
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
