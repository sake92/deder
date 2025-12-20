package ba.sake.deder.zinc

import xsbti.*
import ba.sake.deder.ServerNotificationsLogger
import ba.sake.deder.ServerNotification

class DederZincReporter(
    moduleId: String,
    notifications: ServerNotificationsLogger,
    logger: Logger,
    maxErrors: Int = 100
) extends Reporter {

  private var _problems = Vector.empty[Problem]
  private var _hasErrors = false
  private var _hasWarnings = false

  override def reset(): Unit = {
    _problems = Vector.empty
    _hasErrors = false
    _hasWarnings = false
  }

  override def hasErrors(): Boolean = _hasErrors

  override def hasWarnings(): Boolean = _hasWarnings

  override def printSummary(): Unit = {
    notifications.add(
      ServerNotification.CompileFinished(
        moduleId,
        _problems.count(_.severity == Severity.Error),
        _problems.count(_.severity == Severity.Warn)
      )
    )
  }

  override def problems(): Array[Problem] = _problems.toArray

  override def log(problem: Problem): Unit = {
    _problems = _problems.appended(problem)

    problem.severity() match {
      case Severity.Error => _hasErrors = true
      case Severity.Warn  => _hasWarnings = true
      case _              =>
    }

    notifications.add(
      ServerNotification.CompileDiagnostic(moduleId, problem)
    )
  }

  override def comment(pos: Position, msg: String): Unit = {
    logger.info(() => msg)
  }

}
