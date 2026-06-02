package ba.sake.deder

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

class CacheStatsRegistry:
    private val suppliers = new ConcurrentHashMap[String, () => InMemCacheStats]()

    def register(name: String, supplier: () => InMemCacheStats): Unit =
        suppliers.put(name, supplier)

    def getAllStats: Map[String, InMemCacheStats] =
        suppliers.asScala.view.mapValues(_()).toMap

object CacheStatsRegistry:
    import com.github.blemale.scaffeine.Cache as SCache

    /** Reads Caffeine stats and converts to plugin-api InMemCacheStats. */
    def statsOf(cache: SCache[_, _]): InMemCacheStats =
        val s = cache.stats()
        InMemCacheStats(
            hitCount = s.hitCount(),
            missCount = s.missCount(),
            hitRate = if s.requestCount() > 0 then s.hitRate() else 0.0,
            estimatedSize = cache.estimatedSize(),
            evictionCount = s.evictionCount()
        )
