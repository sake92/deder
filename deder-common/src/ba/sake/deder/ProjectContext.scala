package ba.sake.deder

import ox.ForkLocal
import ox.Ox

/** Project-scoped values used by code that resolves paths relative to a Deder project. */
case class ProjectContext(projectRootDir: os.Path)

object ProjectContext {

  /** The active project context, or None when code runs outside a project scope. */
  val current: ForkLocal[Option[ProjectContext]] = ForkLocal(None)

  /** Returns the active project context, failing instead of silently using a process-global root. */
  def requireCurrent: ProjectContext =
    current.get().getOrElse {
      throw IllegalStateException(
        "No project context is active. Run this operation inside ProjectContext.current.supervisedWhere(...)."
      )
    }

  /** Runs code in a fresh Ox scope with the supplied project context. */
  def run[T](context: ProjectContext)(f: Ox ?=> T): T =
    ox.supervised {
      current.supervisedWhere(Some(context))(f)
    }
}
