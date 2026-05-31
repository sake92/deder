package ba.sake.deder

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.time.Duration
import scala.jdk.CollectionConverters.*
import io.opentelemetry.api.metrics.{LongCounter, LongHistogram, Meter}
import io.opentelemetry.api.common.{Attributes, AttributeKey}
import com.typesafe.scalalogging.StrictLogging

class DederProjectInternalsImpl private (
    private val startTime: Instant,
    private val currentReqs: ConcurrentHashMap[String, LiveRequest],
    private val history: ConcurrentLinkedDeque[CompletedRequest],
    private val maxHistory: Int,
    private val taskAccumulators: ConcurrentHashMap[String, TaskStatsAccumulator],
    private val totalServed: AtomicLong,
    private val totalErrCount: AtomicLong,
    private val meter: Meter
) extends DederProjectInternals, StrictLogging:

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

  private[deder] def recordRequestCompleted(
      requestId: String,
      taskName: String,
      success: Boolean,
      duration: Duration,
      caller: CallerType
  ): Unit =
    currentReqs.remove(requestId)
    val completed = CompletedRequest(requestId, caller, taskName, Seq.empty, Instant.now(), duration, success)
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

  private[deder] def recordTaskExecution(
      taskName: String,
      duration: Duration,
      cacheHit: Boolean
  ): Unit =
    val acc = taskAccumulators.computeIfAbsent(taskName, _ => new TaskStatsAccumulator(1024))
    acc.record(duration, cacheHit)
    // OTEL
    logger.debug(s"OTEL: task execution: task=${taskName} duration=${duration.toMillis}ms cacheHit=${cacheHit}")
    val attrs = Attributes.of(AttributeKey.stringKey("task"), taskName)
    taskExecutionsCounter.add(1, attrs)
    if cacheHit then taskCacheHitsCounter.add(1, attrs)
    taskDurationHistogram.record(duration.toMillis, attrs)

object DederProjectInternalsImpl:
  def apply(meter: Meter): DederProjectInternalsImpl =
    new DederProjectInternalsImpl(
      startTime = Instant.now(),
      currentReqs = new ConcurrentHashMap[String, LiveRequest](),
      history = new ConcurrentLinkedDeque[CompletedRequest](),
      maxHistory = 100,
      taskAccumulators = new ConcurrentHashMap[String, TaskStatsAccumulator](),
      totalServed = new AtomicLong(0),
      totalErrCount = new AtomicLong(0),
      meter = meter
    )

private class TaskStatsAccumulator(sampleCapacity: Int):
  private val executions = new AtomicLong(0)
  private val cacheHits = new AtomicLong(0)
  private val errors = new AtomicLong(0)
  private val totalDurationNanos = new AtomicLong(0)
  private val maxDurationNanos = new AtomicLong(0)
  private val minDurationNanos = new AtomicLong(Long.MaxValue)
  private val samples = new Array[Long](sampleCapacity)
  private val sampleCursor = new AtomicInteger(0)
  private val sampleCount = new AtomicInteger(0)

  def record(duration: Duration, cacheHit: Boolean): Unit =
    val nanos = duration.toNanos
    executions.incrementAndGet()
    if cacheHit then cacheHits.incrementAndGet()
    totalDurationNanos.addAndGet(nanos)
    maxDurationNanos.updateAndGet(math.max(_, nanos))
    minDurationNanos.updateAndGet(math.min(_, nanos))
    val idx = sampleCursor.getAndIncrement() % sampleCapacity
    samples(idx) = nanos
    sampleCount.updateAndGet(n => math.min(n + 1, sampleCapacity))

  def toStats: TaskStats =
    val execs = executions.get()
    if execs == 0 then
      TaskStats(0L, 0L, 0L,
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
