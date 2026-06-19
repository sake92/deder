package ba.sake.deder

import scala.util.control.NonFatal

object CloseUtils:
  /** Execute `f`, silently ignoring any NonFatal error. For fire-and-forget cleanup operations
    * where failure should not propagate (e.g. releasing locks, closing resources, interrupting threads).
    */
  def quietly(f: => Unit): Unit =
    try f
    catch { case NonFatal(_) => }
