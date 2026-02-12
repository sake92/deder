package ba.sake.deder

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object DederGlobals {
  val projectRootDir: os.Path = os.Path(System.getProperty("DEDER_PROJECT_ROOT_DIR"))
  
  var testWorkerThreads: Int = 16


  val cancellationTokens: ConcurrentHashMap[String, AtomicBoolean] = new ConcurrentHashMap()
}
