package ba.sake.deder

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import com.github.blemale.scaffeine.Cache as SCache

class CacheStatsRegistry:
    private val caches = new ConcurrentHashMap[String, SCache[?, ?]]()

    def register(name: String, cache: SCache[?, ?]): Unit =
        caches.put(name, cache)

    def getAllStats: Map[String, InMemCacheStats] =
        caches.asScala.view.mapValues(CacheStatsRegistry.statsOf).toMap

    def invalidateAll(): Int =
        val count = caches.size()
        caches.values().asScala.foreach(_.invalidateAll())
        count

object CacheStatsRegistry:
    /** Reads Caffeine stats and converts to plugin-api InMemCacheStats. */
    def statsOf(cache: SCache[?, ?]): InMemCacheStats =
        val s = cache.stats()
        InMemCacheStats(
            hitCount = s.hitCount(),
            missCount = s.missCount(),
            hitRate = if s.requestCount() > 0 then s.hitRate() else 0.0,
            estimatedSize = cache.estimatedSize(),
            evictionCount = s.evictionCount()
        )
