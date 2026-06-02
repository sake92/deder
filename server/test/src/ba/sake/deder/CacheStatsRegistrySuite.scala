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

    registry.register("test-cache", () => CacheStatsRegistry.statsOf(cache))

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

  test("register overwrites previous supplier for same key") {
    val registry = CacheStatsRegistry()

    val cache1 = Scaffeine().recordStats().build[String, String]()
    cache1.put("x", "y")
    registry.register("same-key", () => CacheStatsRegistry.statsOf(cache1))

    val cache2 = Scaffeine().recordStats().build[String, String]()
    cache2.put("a", "v")
    cache2.put("b", "v")
    registry.register("same-key", () => CacheStatsRegistry.statsOf(cache2))

    val stats = registry.getAllStats
    assertEquals(stats("same-key").estimatedSize, 2L)
  }
}
