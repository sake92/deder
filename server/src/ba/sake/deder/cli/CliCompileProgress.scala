package ba.sake.deder.cli

import java.time.Duration

import ba.sake.deder.{ServerNotification, toPrettyString}

final class CliCompileProgress(
    intervalMs: Long,
    emit: CliServerMessage => Unit,
    nowNanos: () => Long = System.nanoTime
) {

  private case class CompileState(
      startedAtNanos: Long,
      sourceCount: Int,
      lastCurrent: Long,
      lastTotal: Long
  )

  private val intervalNanos = math.max(0L, intervalMs) * 1_000_000L
  private val states = scala.collection.mutable.Map.empty[String, CompileState]
  private var lastProgressAtNanos: Option[Long] = None

  /** Returns true when the notification belongs to compile-progress handling. */
  def handle(notification: ServerNotification): Boolean = synchronized {
    notification match {
      case ServerNotification.CompileStarted(moduleId, files) =>
        if enabled then {
          val startedAt = nowNanos()
          states.update(
            moduleId,
            CompileState(startedAt, files.size, lastCurrent = 0L, lastTotal = files.size.toLong)
          )
          lastProgressAtNanos = Some(startedAt)
          val sourceWord = if files.size == 1 then "source" else "sources"
          emitInfo(moduleId, s"Compiling ${files.size} $sourceWord...")
        }
        true

      case ServerNotification.TaskProgress(moduleId, "compile", current, total) =>
        if enabled then handleProgress(moduleId, current, total)
        true

      case ServerNotification.CompileFinished(moduleId, _, _) =>
        if enabled then {
          val elapsedNanos = states.remove(moduleId) match {
            case Some(state) => nowNanos() - state.startedAtNanos
            case None        => 0L
          }
          val elapsed = Duration.ofNanos(math.max(0L, elapsedNanos)).toPrettyString
          emitInfo(moduleId, s"Compiled in $elapsed")
        }
        true

      case ServerNotification.CompileFailed(moduleId, errors, warnings) =>
        if enabled then {
          states.remove(moduleId)
          val summary = failureSummary(errors, warnings)
          emit(CliServerMessage.log(summary, LogLevel.ERROR, Some(moduleId)))
        }
        true

      case _ => false
    }
  }

  private def enabled: Boolean = intervalNanos > 0

  private def handleProgress(moduleId: String, current: Long, total: Long): Unit = {
    val progressChanged = states.get(moduleId).forall(state => state.lastCurrent != current || state.lastTotal != total)
    states.get(moduleId).foreach { state =>
      states.update(moduleId, state.copy(lastCurrent = current, lastTotal = total))
    }
    val now = nowNanos()
    val intervalElapsed = lastProgressAtNanos.forall(last => now - last >= intervalNanos)
    if total > 0 && intervalElapsed && progressChanged then {
      lastProgressAtNanos = Some(now)
      emitInfo(moduleId, s"Compile progress: $current/$total")
    }
  }

  private def failureSummary(errors: Int, warnings: Int): String = {
    val errorWord = if errors == 1 then "error" else "errors"
    val warningWord = if warnings == 1 then "warning" else "warnings"
    s"Compilation failed: $errors $errorWord, $warnings $warningWord"
  }

  private def emitInfo(moduleId: String, message: String): Unit =
    emit(CliServerMessage.log(message, LogLevel.INFO, Some(moduleId)))
}
