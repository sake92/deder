package ba.sake.deder

case class InMemCacheStats(
    hitCount: Long,
    missCount: Long,
    hitRate: Double,        // 0.0 to 1.0
    estimatedSize: Long,    // approximate entry count
    evictionCount: Long
)
