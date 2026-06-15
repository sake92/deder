package ba.sake.deder.cli

import java.time.{Duration, Instant}
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import ba.sake.deder.ServerNotification
import ba.sake.deder.RequestContext

final class CliExecHeartbeat(
    quietPeriod: Duration = Duration.ofSeconds(60),
    startTime: Instant = Instant.now(),
    emit: CliServerMessage => Unit,
    now: () => Instant = () => Instant.now(),
    sleep: Duration => Unit = duration => Thread.sleep(duration.toMillis)
) extends AutoCloseable {

  private val requestContext = RequestContext.current.get()
  private val lastVisibleActivityAt = new AtomicReference[Instant](now())
  private val closed = new AtomicBoolean(false)
  private val worker = Thread
    .ofVirtual()
    .name("CliExecHeartbeat")
    .start(() => runLoop())

  def recordServerNotification(notification: ServerNotification): Unit =
    if CliServerMessage.fromServerNotification(notification).nonEmpty then
      lastVisibleActivityAt.set(now())

  override def close(): Unit =
    if closed.compareAndSet(false, true) then
      worker.interrupt()
      worker.join()

  private def runLoop(): Unit =
    try
      while !closed.get() do
        val activityAt = lastVisibleActivityAt.get()
        val emitAt = activityAt.plus(quietPeriod)
        val remaining = Duration.between(now(), emitAt)
        if remaining.isNegative || remaining.isZero then
          val heartbeatAt = now()
          if !closed.get() && lastVisibleActivityAt.compareAndSet(activityAt, heartbeatAt) then
            RequestContext.current.supervisedWhere(requestContext) {
              val ts = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(heartbeatAt)
              val elapsed = formatDuration(Duration.between(startTime, heartbeatAt))
              emit(CliServerMessage.info(s"[$ts] Still working... ($elapsed)"))
            }
        else
          sleep(remaining)
    catch
      case _: InterruptedException if closed.get() =>

  private def formatDuration(d: Duration): String =
    val totalSeconds = d.toSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    if hours > 0 then f"${hours}h ${minutes}m ${seconds}s"
    else if minutes > 0 then f"${minutes}m ${seconds}s"
    else f"${seconds}s"
}
