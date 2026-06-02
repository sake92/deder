package ba.sake.deder

import ox.ForkLocal

object RequestContext {
  val clientContext: ForkLocal[CliClientContext] = ForkLocal(CliClientContext())

  // BSP-only; stays ThreadLocal (not on CLI path)
  val traceparent: ThreadLocal[String] = new ThreadLocal()
}
