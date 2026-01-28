package ba.sake.deder

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object RequestContext {
  val id: ThreadLocal[String] = new ThreadLocal()
}
