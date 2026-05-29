package ba.sake.deder

import java.time.{Duration, Instant}

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
    success: Boolean
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
    duration: DurationDistribution
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

  /** Server metadata. */
  def serverUptime: Duration
