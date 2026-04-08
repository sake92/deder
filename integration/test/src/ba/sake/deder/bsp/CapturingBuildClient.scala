package ba.sake.deder.bsp


import java.util.concurrent.*
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import ch.epfl.scala.bsp4j.*

class CapturingBuildClient extends BuildClient {
  val taskStarts = new ConcurrentLinkedQueue[TaskStartParams]()
  val taskFinishes = new ConcurrentLinkedQueue[TaskFinishParams]()
  val diagnostics = new ConcurrentLinkedQueue[PublishDiagnosticsParams]()
  val taskProgresses = new ConcurrentLinkedQueue[TaskProgressParams]()
  val didChangeNotifications = new ConcurrentLinkedQueue[DidChangeBuildTarget]()

  def clear(): Unit =
    taskStarts.clear()
    taskFinishes.clear()
    diagnostics.clear()
    taskProgresses.clear()
    didChangeNotifications.clear()

  def awaitTaskStart(
      timeout: FiniteDuration = 15.seconds,
      predicate: TaskStartParams => Boolean = _ => true
  ): Option[TaskStartParams] =
    pollUntil(timeout)(taskStarts.asScala.find(predicate))

  def awaitTaskFinish(
      timeout: FiniteDuration = 15.seconds,
      predicate: TaskFinishParams => Boolean = _ => true
  ): Option[TaskFinishParams] =
    pollUntil(timeout)(taskFinishes.asScala.find(predicate))

  def awaitDiagnostic(
      timeout: FiniteDuration = 15.seconds,
      predicate: PublishDiagnosticsParams => Boolean = _ => true
  ): Option[PublishDiagnosticsParams] =
    pollUntil(timeout)(diagnostics.asScala.find(predicate))

  def awaitTaskProgress(
      timeout: FiniteDuration = 15.seconds,
      predicate: TaskProgressParams => Boolean = _ => true
  ): Option[TaskProgressParams] =
    pollUntil(timeout)(taskProgresses.asScala.find(predicate))

  def awaitTargetDidChange(
      timeout: FiniteDuration = 15.seconds,
      predicate: DidChangeBuildTarget => Boolean = _ => true
  ): Option[DidChangeBuildTarget] =
    pollUntil(timeout)(didChangeNotifications.asScala.find(predicate))

  private def pollUntil[T](timeout: FiniteDuration)(check: => Option[T]): Option[T] =
    val deadline = System.currentTimeMillis() + timeout.toMillis
    while System.currentTimeMillis() < deadline do
      check match
        case s @ Some(_) => return s
        case None        => Thread.sleep(50)
    None

  override def onBuildTaskStart(p: TaskStartParams): Unit = taskStarts.add(p)
  override def onBuildTaskFinish(p: TaskFinishParams): Unit = taskFinishes.add(p)
  override def onBuildPublishDiagnostics(p: PublishDiagnosticsParams): Unit = diagnostics.add(p)
  override def onBuildTaskProgress(p: TaskProgressParams): Unit = taskProgresses.add(p)
  override def onBuildTargetDidChange(p: DidChangeBuildTarget): Unit = didChangeNotifications.add(p)
  override def onBuildShowMessage(p: ShowMessageParams): Unit = ()
  override def onBuildLogMessage(p: LogMessageParams): Unit = ()
  override def onRunPrintStdout(p: PrintParams): Unit = ()
  override def onRunPrintStderr(p: PrintParams): Unit = ()
}

