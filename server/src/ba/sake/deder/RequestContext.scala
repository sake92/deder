package ba.sake.deder

import ox.ForkLocal

object RequestContext {
  val clientContext: ForkLocal[Option[CliClientContext]] = ForkLocal(None)

  /** Convenience accessor for CLI request context. Throws if no context is set. */
  def cliContext: CliClientContext = clientContext.get().getOrElse(
    throw new IllegalStateException("No CLI client context available on the current virtual thread")
  )

  // BSP-only; stays ThreadLocal (not on CLI path)
  val traceparent: ThreadLocal[String] = new ThreadLocal()
}
