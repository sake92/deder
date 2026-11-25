package ba.sake.deder.zinc

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Using
import ba.sake.deder.CacheStatsRegistry
import ba.sake.deder.deps.{Dependency, DependencyResolver}

class ZincCompilerCacheSuite extends munit.FunSuite {

  test("concurrent bridge lookups can proceed before either caller creates the cached compiler") {
    val lookupsStarted = new CountDownLatch(2)
    val releaseLookups = new CountDownLatch(1)
    val lookupCount = new AtomicInteger()
    val registry = CacheStatsRegistry()
    val resolver = new DependencyResolver(Seq.empty, registry) {
      override def fetchFile(dependency: Dependency): os.Path = {
        lookupCount.incrementAndGet()
        lookupsStarted.countDown()
        if !releaseLookups.await(15, TimeUnit.SECONDS) then
          throw new TimeoutException("bridge lookups should be released by the test")
        os.pwd / "compiler-bridge.jar"
      }
    }
    val cache = ZincCompilersCache(registry)
    val scalaVersion = "3.7.4"

    Using.resource(Executors.newVirtualThreadPerTaskExecutor()) { executor =>
      val first = executor.submit[ZincCompiler](() => cache.get(scalaVersion, resolver))
      val second = executor.submit[ZincCompiler](() => cache.get(scalaVersion, resolver))

      try assert(lookupsStarted.await(5, TimeUnit.SECONDS), "neither lookup should hold the compiler cache lock")
      finally releaseLookups.countDown()

      val compiler = first.get(5, TimeUnit.SECONDS)
      val otherCompiler = second.get(5, TimeUnit.SECONDS)
      assert(compiler eq otherCompiler)
      assert(cache.get(scalaVersion, resolver) eq compiler)
      assertEquals(lookupCount.get(), 2)
    }
  }

  test("close() clears setup cache") {
    val tmpDir = os.temp.dir()
    val dummyJar = tmpDir / "dummy.jar"
    val jos = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(dummyJar.toIO))
    jos.close()

    val compiler = ZincCompiler(dummyJar, CacheStatsRegistry(), "3.7.0")

    // Access private caches via reflection
    val setupField = compiler.getClass.getDeclaredField("setupCache")
    setupField.setAccessible(true)
    val setupCache = setupField.get(compiler).asInstanceOf[com.github.blemale.scaffeine.Cache[?, ?]]

    // Starts empty
    assertEquals(setupCache.estimatedSize(), 0L)

    // close() on empty should not throw
    compiler.close()
    assertEquals(setupCache.estimatedSize(), 0L)

    os.remove.all(tmpDir)
  }
}
