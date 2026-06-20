package ba.sake.deder

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}
import java.time.Duration
import scala.jdk.CollectionConverters.*
import io.opentelemetry.api.metrics.{LongCounter, LongHistogram, Meter}
import io.opentelemetry.api.common.{Attributes, AttributeKey}
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.JsonRW

class DederProjectInternalsImpl private (
    private val startTime: Instant,
    private val currentReqs: ConcurrentHashMap[String, LiveRequest],
    private val history: ConcurrentLinkedDeque[CompletedRequest],
    private val maxHistory: Int,
    private val taskAccumulators: ConcurrentHashMap[String, TaskStatsAccumulator],
    private val totalServed: AtomicLong,
    private val totalErrCount: AtomicLong,
    private val meter: Meter,
    private val cacheStatsRegistry: CacheStatsRegistry
) extends DederProjectInternals, TaskInvokerApi, StrictLogging:

  logger.info(s"DederProjectInternals initialized")

  // OTEL instruments
  private val requestsServedCounter: LongCounter = meter
    .counterBuilder("deder.requests.served")
    .setDescription("Total requests served by caller and task")
    .build()

  private val taskExecutionsCounter: LongCounter = meter
    .counterBuilder("deder.task.executions")
    .setDescription("Total task instance executions")
    .build()

  private val taskCacheHitsCounter: LongCounter = meter
    .counterBuilder("deder.task.cache.hits")
    .setDescription("Cache hits per task")
    .build()

  private val taskDurationHistogram: LongHistogram = meter
    .histogramBuilder("deder.task.duration")
    .setDescription("Task execution duration in milliseconds")
    .setUnit("ms")
    .ofLongs()
    .build()

  // -- Request progress tracking --
  private val requestStatuses: ConcurrentHashMap[String, MutableRequestStatus] = new ConcurrentHashMap()
  // Maps TaskInstance.id -> requestId that currently holds the lock
  private val lockHolders: ConcurrentHashMap[String, String] = new ConcurrentHashMap()

  // Delegated cancel function — wired after construction by ServerMain
  @volatile private[deder] var cancelFn: String => Unit = _ => ()

  // Delegated purge function — wired after construction by ServerMain
  @volatile private[deder] var purgeCachesFn: () => PurgeCachesResult = () => PurgeCachesResult(0, 0, 0, false)

  // Direct reference to projectState — wired after construction by ServerMain
  @volatile private[deder] var projectState: Option[DederProjectState] = None

  override def currentRequests: Seq[LiveRequest] =
    currentReqs.values().asScala.toSeq

  override def recentHistory: Seq[CompletedRequest] =
    history.iterator().asScala.toSeq

  override def taskStats(taskName: String): Option[TaskStats] =
    Option(taskAccumulators.get(taskName)).map(_.toStats)

  override def allTaskStats: Seq[(String, TaskStats)] =
    taskAccumulators.asScala.toSeq
      .map { case (name, acc) => name -> acc.toStats }
      .sortBy(_._1)

  override def totalRequestsServed: Long = totalServed.get()
  override def totalErrors: Long = totalErrCount.get()

  private var _loadedPlugins: Seq[LoadedPluginInfo] = Seq.empty
  override def loadedPlugins: Seq[LoadedPluginInfo] = _loadedPlugins

  override def serverUptime: Duration =
    Duration.ofMillis(System.currentTimeMillis() - startTime.toEpochMilli)

  private[deder] def registry: CacheStatsRegistry = cacheStatsRegistry

  override def inMemoryCachesStats: Map[String, InMemCacheStats] =
    cacheStatsRegistry.getAllStats

  override def cancelRequest(requestId: String): Boolean =
    if requestStatuses.containsKey(requestId) then
      cancelFn(requestId)
      true
    else false

  override def requestStatus(requestId: String): Option[RequestStatus] =
    Option(requestStatuses.get(requestId)).map(_.toRequestStatus)

  override def allRequestStatuses: Seq[RequestStatus] =
    requestStatuses.values().asScala.toSeq.map(_.toRequestStatus).sortBy(_.startTime)

  override def purgeInMemoryCaches(): PurgeCachesResult =
    purgeCachesFn()

  // TaskInvokerApi
  def invoke(
      taskName: String,
      moduleIds: Seq[String],
      args: Seq[String],
      onNotification: ServerNotification => Unit
  ): TaskInvokeResult = {
    val state = projectState.getOrElse(
      throw new IllegalStateException("Server not initialized — projectState reference not wired yet"))
    val requestId = UUID.randomUUID().toString
    val logger = ServerNotificationsLogger(onNotification)
    val execStartNanos = System.nanoTime()

    // Resolve wildcards: empty moduleIds → all modules
    val resolvedIds = state.readState(useLastGood = false) match {
      case Left(_) => Seq.empty
      case Right(s) =>
        val allIds = s.tasksResolver.allModules.map(_.id)
        if moduleIds.isEmpty then allIds
        else WildcardUtils.getMatchesOrRecommendations(allIds, moduleIds) match {
          case Left(_)    => Seq.empty
          case Right(ids) => ids
        }
    }

    val results = state.executeTasks(
      requestId, CallerType.Plugin, resolvedIds, taskName, args,
      watch = false, logger, useLastGood = false)

    // Map to public outcomes
    val outcomes = results.map {
      case TaskExecResult.Success(ti, _, _, fromCache) =>
        TaskInvokeOutcome(ti.moduleId, success = true, None, fromCache)
      case TaskExecResult.Failure(ti, error) =>
        TaskInvokeOutcome(ti.moduleId, success = false, Some(error), fromCache = false)
      case TaskExecResult.Skipped(ti, because) =>
        TaskInvokeOutcome(ti.moduleId, success = false,
          Some(s"skipped — ${because.taskInstance.moduleId} failed"), fromCache = false)
      case TaskExecResult.Cancelled(ti, message) =>
        TaskInvokeOutcome(ti.moduleId, success = false, Some(message), fromCache = false)
    }

    // Render cross-module plaintext summary (same as CLI output)
    val totalDuration = java.time.Duration.ofNanos(System.nanoTime() - execStartNanos)
    val renderedSummary = if results.nonEmpty then {
      val successes = results.collect { case s: TaskExecResult.Success => s }
      val failures = results.collect {
        case f: TaskExecResult.Failure => ModuleFailure(f.taskInstance.moduleId, f.error, None)
        case s: TaskExecResult.Skipped =>
          ModuleFailure(s.taskInstance.moduleId,
            s"skipped — ${s.because.taskInstance.moduleId} failed",
            Some(s.because.taskInstance.moduleId))
        case c: TaskExecResult.Cancelled => ModuleFailure(c.taskInstance.moduleId, c.message, None)
      }
      if successes.nonEmpty then {
        val task = successes.head.taskInstance.task
        val moduleResults = successes.sortBy(_.taskInstance.moduleId).map(r => r.taskInstance.moduleId -> r.value)
        val summary = task.summarizeValueUnsafe(moduleResults, failures, totalDuration)
        given PlainTextWritable[Any] = task.summarizable.plainTextW.asInstanceOf[PlainTextWritable[Any]]
        Some(OutputFormat.render[Any](summary, OutputFormat.PlainText)(
          using task.summarizable.jsonRW.asInstanceOf[JsonRW[Any]],
          task.summarizable.plainTextW.asInstanceOf[PlainTextWritable[Any]],
          task.summarizable.dotW.asInstanceOf[DotWritable[Any]],
          task.summarizable.mermaidW.asInstanceOf[MermaidWritable[Any]]
        ))
      } else None
    } else None

    TaskInvokeResult(outcomes, renderedSummary)
  }

  private[deder] def clearHistory(): Int = {
    val size = history.size()
    history.clear()
    size
  }

  // -- Write methods --

  private[deder] def setLoadedPlugins(plugins: Seq[LoadedPluginInfo]): Unit =
    _loadedPlugins = plugins

  private[deder] def recordRequestStarted(
      requestId: String,
      caller: CallerType,
      taskName: String,
      moduleIds: Seq[String],
      startTime: Instant
  ): Unit =
    currentReqs.put(requestId, LiveRequest(requestId, caller, taskName, moduleIds, startTime))
    requestStatuses.put(requestId, MutableRequestStatus(requestId, caller, taskName, moduleIds, startTime))
    
  private[deder] def recordRequestCompleted(
      requestId: String,
      taskName: String,
      success: Boolean,
      duration: Duration,
      caller: CallerType,
      error: Option[String] = None
  ): Unit =
    val liveReq = currentReqs.remove(requestId)
    val moduleIds = Option(liveReq).map(_.moduleIds).getOrElse(Seq.empty)
    val startTime = Option(liveReq).map(_.startTime).getOrElse(Instant.now())
    val completed = CompletedRequest(requestId, caller, taskName, moduleIds, startTime, duration, success, error)
    history.addFirst(completed)
    while history.size() > maxHistory do history.pollLast()
    totalServed.incrementAndGet()
    if !success then totalErrCount.incrementAndGet()
    // OTEL
    logger.debug(s"OTEL: request completed: caller=${caller} task=${taskName} success=${success} duration=${duration.toMillis}ms")
    requestsServedCounter.add(1, Attributes.of(
      AttributeKey.stringKey("caller"), caller.toString.toLowerCase,
      AttributeKey.stringKey("task"), taskName,
      AttributeKey.booleanKey("success"), success
    ))
    // Remove progress tracking
    requestStatuses.remove(requestId)
    // Clean up any lock holders held by this request
    lockHolders.values().removeIf(_ == requestId)

  private[deder] def recordTaskExecution(
      taskName: String,
      duration: Duration,
      cacheHit: Boolean,
      errorMessage: Option[String] = None
  ): Unit =
    val acc = taskAccumulators.computeIfAbsent(taskName, _ => new TaskStatsAccumulator(1024))
    acc.record(duration, cacheHit, errorMessage)
    // OTEL
    logger.debug(s"OTEL: task execution: task=${taskName} duration=${duration.toMillis}ms cacheHit=${cacheHit} error=${errorMessage}")
    val attrs = Attributes.of(AttributeKey.stringKey("task"), taskName)
    taskExecutionsCounter.add(1, attrs)
    if cacheHit then taskCacheHitsCounter.add(1, attrs)
    taskDurationHistogram.record(duration.toMillis, attrs)

  // -- Package-private progress methods, called by DederProjectState and TasksExecutor --

  private[deder] def transitionToAcquiringLocks(requestId: String, totalLocks: Int): Unit =
    Option(requestStatuses.get(requestId)).foreach { rs =>
      rs.state = RequestState.ACQUIRING_LOCKS
      rs.lockProgress = Some(LockProgress(0, totalLocks, None, None))
    }

  private[deder] def updateLockBlocking(requestId: String, taskInstanceId: String): Unit =
    Option(requestStatuses.get(requestId)).foreach { rs =>
      val lp = rs.lockProgress
      val currentTotal = lp.map(_.total).getOrElse(0)
      val currentAcquired = lp.map(_.acquired).getOrElse(0)
      val holder = Option(lockHolders.get(taskInstanceId))
      rs.lockProgress = Some(LockProgress(currentAcquired, currentTotal, Some(taskInstanceId), holder))
    }

  private[deder] def updateLockAcquired(requestId: String, taskInstanceId: String): Unit =
    lockHolders.put(taskInstanceId, requestId)
    Option(requestStatuses.get(requestId)).foreach { rs =>
      val total = rs.lockProgress.map(_.total).getOrElse(0)
      val acquired = rs.lockProgress.map(_.acquired).getOrElse(0) + 1
      rs.lockProgress = Some(LockProgress(acquired, total, None, None))
    }

  private[deder] def transitionToExecuting(requestId: String, totalStages: Int, allTaskIds: Seq[String]): Unit =
    Option(requestStatuses.get(requestId)).foreach { rs =>
      rs.state = RequestState.EXECUTING
      rs.taskProgress = Some(TaskStageProgress(
        currentStage = 0,
        totalStages = totalStages,
        completed = Seq.empty,
        failed = Seq.empty,
        skipped = Seq.empty,
        running = Seq.empty,
        pending = allTaskIds
      ))
    }

  private[deder] def updateStageProgress(requestId: String, progress: TaskStageProgress): Unit =
    Option(requestStatuses.get(requestId)).foreach { rs =>
      rs.taskProgress = Some(progress)
    }

  private[deder] def transitionToCompleted(requestId: String): Unit =
    Option(requestStatuses.get(requestId)).foreach { rs =>
      rs.state = RequestState.COMPLETED
    }

  private[deder] def releaseLockHolder(requestId: String, taskInstanceId: String): Unit =
    lockHolders.remove(taskInstanceId, requestId)

object DederProjectInternalsImpl:
  def apply(meter: Meter, cacheStatsRegistry: CacheStatsRegistry): DederProjectInternalsImpl =
    new DederProjectInternalsImpl(
      startTime = Instant.now(),
      currentReqs = new ConcurrentHashMap[String, LiveRequest](),
      history = new ConcurrentLinkedDeque[CompletedRequest](),
      maxHistory = 100,
      taskAccumulators = new ConcurrentHashMap[String, TaskStatsAccumulator](),
      totalServed = new AtomicLong(0),
      totalErrCount = new AtomicLong(0),
      meter = meter,
      cacheStatsRegistry = cacheStatsRegistry
    )

private class TaskStatsAccumulator(sampleCapacity: Int):
  private val executions = new AtomicLong(0)
  private val cacheHits = new AtomicLong(0)
  private val errors = new AtomicLong(0)
  private val lastError = new AtomicReference[Option[String]](None)
  private val totalDurationNanos = new AtomicLong(0)
  private val maxDurationNanos = new AtomicLong(0)
  private val minDurationNanos = new AtomicLong(Long.MaxValue)
  private val samples = new Array[Long](sampleCapacity)
  private val sampleCursor = new AtomicInteger(0)
  private val sampleCount = new AtomicInteger(0)

  def record(duration: Duration, cacheHit: Boolean, errorMessage: Option[String]): Unit =
    val nanos = duration.toNanos
    executions.incrementAndGet()
    if cacheHit then cacheHits.incrementAndGet()
    errorMessage.foreach { msg =>
      errors.incrementAndGet()
      lastError.set(Some(msg))
    }
    totalDurationNanos.addAndGet(nanos)
    maxDurationNanos.updateAndGet(math.max(_, nanos))
    minDurationNanos.updateAndGet(math.min(_, nanos))
    val idx = sampleCursor.getAndIncrement() % sampleCapacity
    samples(idx) = nanos
    sampleCount.updateAndGet(n => math.min(n + 1, sampleCapacity))

  def toStats: TaskStats =
    val execs = executions.get()
    if execs == 0 then
      TaskStats(0L, 0L, 0L, None,
        DurationDistribution(0L, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO))
    else
      val count = math.min(sampleCount.get(), sampleCapacity)
      val sorted = samples.take(count).sorted
      def pct(p: Double): Duration =
        val idx = ((count - 1) * p).toInt
        Duration.ofNanos(sorted(idx))
      TaskStats(
        executions = execs,
        cacheHits = cacheHits.get(),
        errors = errors.get(),
        lastError = lastError.get(),
        duration = DurationDistribution(
          count = count,
          min = Duration.ofNanos(minDurationNanos.get()),
          max = Duration.ofNanos(maxDurationNanos.get()),
          mean = Duration.ofNanos(totalDurationNanos.get() / execs),
          p50 = pct(0.50),
          p95 = pct(0.95),
          p99 = pct(0.99)
        )
      )

private class MutableRequestStatus(
    val requestId: String,
    val caller: CallerType,
    val taskName: String,
    val moduleIds: Seq[String],
    val startTime: java.time.Instant
):
  @volatile var state: RequestState = RequestState.QUEUED
  @volatile var lockProgress: Option[LockProgress] = None
  @volatile var taskProgress: Option[TaskStageProgress] = None

  def toRequestStatus: RequestStatus =
    RequestStatus(requestId, caller, taskName, moduleIds, startTime, state, lockProgress, taskProgress)
