package ba.sake.deder

import scala.concurrent.duration.*
import com.github.blemale.scaffeine.*

class CacheStatsRegistrySuite extends munit.FunSuite {

  test("register and getAllStats returns correct values") {
    val registry = CacheStatsRegistry()

    val cache = Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minute)
      .maximumSize(10)
      .build[String, String]()

    cache.put("a", "value-a")
    cache.put("b", "value-b")
    cache.getIfPresent("a") // hit
    cache.getIfPresent("a") // hit
    cache.getIfPresent("c") // miss

    registry.register("test-cache", cache)

    val stats = registry.getAllStats
    assert(stats.contains("test-cache"))

    val cs = stats("test-cache")
    assertEquals(cs.hitCount, 2L)
    assertEquals(cs.missCount, 1L)
    assert(cs.hitRate > 0.0)
    assertEquals(cs.estimatedSize, 2L)
    assertEquals(cs.evictionCount, 0L)
  }

  test("statsOf with empty cache returns zeros") {
    val cache = Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minute)
      .maximumSize(10)
      .build[String, String]()

    val stats = CacheStatsRegistry.statsOf(cache)
    assertEquals(stats.hitCount, 0L)
    assertEquals(stats.missCount, 0L)
    assertEquals(stats.hitRate, 0.0)
    assertEquals(stats.estimatedSize, 0L)
    assertEquals(stats.evictionCount, 0L)
  }

  test("getAllStats on empty registry returns empty map") {
    val registry = CacheStatsRegistry()
    val stats = registry.getAllStats
    assert(stats.isEmpty)
  }

  test("register overwrites previous cache for same key") {
    val registry = CacheStatsRegistry()

    val cache1 = Scaffeine().recordStats().build[String, String]()
    cache1.put("x", "y")
    registry.register("same-key", cache1)

    val cache2 = Scaffeine().recordStats().build[String, String]()
    cache2.put("a", "v")
    cache2.put("b", "v")
    registry.register("same-key", cache2)

    val stats = registry.getAllStats
    assertEquals(stats("same-key").estimatedSize, 2L)
  }

  test("invalidateAll clears all registered caches") {
    val registry = CacheStatsRegistry()

    val cache1 = Scaffeine().recordStats().build[String, String]()
    cache1.put("a", "val1")
    registry.register("cache-1", cache1)

    val cache2 = Scaffeine().recordStats().build[String, String]()
    cache2.put("b", "val2")
    registry.register("cache-2", cache2)

    // Both caches have entries
    assert(cache1.estimatedSize() > 0)
    assert(cache2.estimatedSize() > 0)

    val cleared = registry.invalidateAll()
    assertEquals(cleared, 2)

    // Both caches should now be empty
    assertEquals(cache1.estimatedSize(), 0L)
    assertEquals(cache2.estimatedSize(), 0L)
  }
}
