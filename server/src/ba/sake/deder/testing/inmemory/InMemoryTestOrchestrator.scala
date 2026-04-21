package ba.sake.deder.testing.inmemory

import java.net.URLClassLoader
import scala.concurrent.duration.*
import com.github.blemale.scaffeine.*
import ba.sake.deder.*
import ba.sake.deder.testing.*

/** Runs tests in-process using a cached classloader, avoiding the overhead of creating a new
  * URLClassLoader on every test run. The classloader is keyed by the concatenated classpath string
  * and shared across modules with identical classpaths. Entries expire after 5 minutes of inactivity
  * and are closed on eviction via the removal listener.
  */
object InMemoryTestOrchestrator {

  private val classLoaderCache: Cache[String, URLClassLoader] =
    Scaffeine()
      .expireAfterAccess(5.minute)
      .maximumSize(20)
      .removalListener[String, URLClassLoader] { (_, cl, _) =>
        if cl != null then try cl.close()
        catch { case _: Exception => () }
      }
      .build()

  def run(
      discoveredTests: Seq[DiscoveredFrameworkTests],
      runtimeClasspath: Seq[os.Path],
      testOptions: DederTestOptions,
      notifications: ServerNotificationsLogger,
      moduleId: String,
      testParallelism: Int
  ): DederTestResults = {
    val cacheKey = runtimeClasspath.mkString(java.io.File.pathSeparator)
    val classLoader = classLoaderCache.get(cacheKey, _ => ClassLoaderUtils.createTestsClassLoader(runtimeClasspath))
    val oldClassloader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(classLoader)
    try {
      val logger = DederTestLogger(notifications, moduleId)
      val testRunner = DederTestRunner(testParallelism, discoveredTests, Map.empty, classLoader, logger)
      testRunner.run(testOptions)
    } finally {
      Thread.currentThread().setContextClassLoader(oldClassloader)
    }
  }
}
