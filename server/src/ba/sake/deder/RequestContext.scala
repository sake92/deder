package ba.sake.deder

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import ox.ForkLocal

object RequestContext {
  val clientContext: ForkLocal[Option[CliClientContext]] = ForkLocal(None)
  val id: ThreadLocal[String] = new ThreadLocal()
  val clientParams: ThreadLocal[CliClientParams] = new ThreadLocal()
  val outputFormat: ThreadLocal[OutputFormat] = new ThreadLocal[OutputFormat] {
    override def initialValue(): OutputFormat = OutputFormat.PlainText
  }
  val traceparent: ThreadLocal[String] = new ThreadLocal()
}
