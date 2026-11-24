package ba.sake.deder.deps

import java.time.Duration
import java.util.concurrent.{CountDownLatch, ExecutionException, FutureTask, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.AtomicInteger
import ba.sake.deder.{CacheStatsRegistry, ServerNotificationsLogger}
import coursierapi.{Artifact, Dependency as CoursierDependency, FetchResult}

class DependencyResolverCacheSuite extends munit.FunSuite {

  test("depsCacheKey is stable for same deps in different order") {
    val dep1 = coursierapi.Dependency.of("org.scala-lang", "scala-library", "2.13.12")
    val dep2 = coursierapi.Dependency.of("com.lihaoyi", "os-lib_2.13", "0.9.1")

    val key1 = DependencyResolver.depsCacheKey(Seq(dep1, dep2))
    val key2 = DependencyResolver.depsCacheKey(Seq(dep2, dep1))

    assertEquals(key1, key2, "Cache key should be order-independent (sorted)")
  }

  test("fetchFiles returns empty for empty deps") {
    val resolver = new DependencyResolver(Seq.empty, CacheStatsRegistry())
    val result = resolver.fetchFiles(Seq.empty)
    assertEquals(result, Seq.empty)
  }

  test("interrupting a caller waiting for the same dependency leaves the active fetch running") {
    val fetchStarted = new CountDownLatch(1)
    val releaseFetch = new CountDownLatch(1)
    val fetchCount = new AtomicInteger()
    val jar = os.pwd / "test-dependency.jar"
    val artifact = Artifact.of(jar.toNIO.toUri.toString)
    val fetchResult = FetchResult.of(java.util.List.of(java.util.Map.entry(artifact, jar.toIO)))
    val resolver = new DependencyResolver(Seq.empty, CacheStatsRegistry()) {
      override def doFetch(
          dependencies: Seq[CoursierDependency],
          notifications: Option[ServerNotificationsLogger]
      ): FetchResult = {
        fetchCount.incrementAndGet()
        fetchStarted.countDown()
        if !releaseFetch.await(15, TimeUnit.SECONDS) then
          throw new TimeoutException("fetch should be released by the test")
        fetchResult
      }
    }
    val dependencies = Seq(Dependency.make("example:test-dependency:1.0", "3.9.0"))
    val activeFetch = new FutureTask[Seq[os.Path]](() => resolver.fetchFiles(dependencies))
    val waitingFetch = new FutureTask[Seq[os.Path]](() => resolver.fetchFiles(dependencies))
    val owner = Thread.ofVirtual().unstarted(activeFetch)
    val waiter = Thread.ofVirtual().unstarted(waitingFetch)

    try {
      owner.start()
      assert(fetchStarted.await(5, TimeUnit.SECONDS), "the first caller should start the fetch")

      waiter.start()
      waiter.interrupt()
      val failure = intercept[ExecutionException](waitingFetch.get(5, TimeUnit.SECONDS))
      assert(failure.getCause.isInstanceOf[InterruptedException])
      assert(!activeFetch.isDone, "the active fetch should continue")

      releaseFetch.countDown()
      assertEquals(activeFetch.get(5, TimeUnit.SECONDS), Seq(jar))
      assertEquals(resolver.fetchFiles(dependencies), Seq(jar))
      assertEquals(fetchCount.get(), 1)
    } finally {
      releaseFetch.countDown()
      val cleanupTimeout = Duration.ofSeconds(5)
      assert(owner.join(cleanupTimeout), "the active caller should exit")
      assert(waiter.join(cleanupTimeout), "the waiting caller should exit")
    }
  }

  test("buildDepTree detects requested version clashes even after resolution picks one winner") {
    val resolver = new DependencyResolver(Seq.empty, CacheStatsRegistry())
    val deps = Seq(
      Dependency.make("org.jsoup:jsoup:1.21.1", "3.7.4"),
      Dependency.make("org.jsoup:jsoup:1.16.2", "3.7.4"),
      Dependency.make("org.jsoup:jsoup:1.1.2", "3.7.4")
    )

    val tree = resolver.buildDepTree(deps)
    val jsoupConflict = tree.conflicts.find(_.coordinate == "org.jsoup:jsoup")

    assert(jsoupConflict.nonEmpty)
    assert(jsoupConflict.get.isConflict)
    assertEquals(jsoupConflict.get.requestedVersions.keySet, Set("1.21.1", "1.16.2", "1.1.2"))
    assertEquals(jsoupConflict.get.resolvedVersion, "1.21.1")
  }
}
