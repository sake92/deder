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
