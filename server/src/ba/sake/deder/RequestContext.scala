package ba.sake.deder

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object RequestContext {
  val id: ThreadLocal[String] = new ThreadLocal()
  val clientParams: ThreadLocal[CliClientParams] = new ThreadLocal()
  val outputFormat: ThreadLocal[OutputFormat] = new ThreadLocal[OutputFormat] {
    override def initialValue(): OutputFormat = OutputFormat.PlainText
  }
}
