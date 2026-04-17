package ba.sake.deder.testing

object TestDistribution {

  /** Distribute discovered tests across up to `maxForks` buckets using longest-processing-time-first
    * (LPT) bin-packing, weighted by historical per-class durations.
    *
    *   - Empty input → empty output.
    *   - Effective bucket count = min(maxForks, total test class count). No empty buckets.
    *   - Classes without recorded duration get the median of known durations (1 if no history)
    *     so they sort reasonably among known-heavy classes.
    *   - Per-framework groupings are preserved: each bucket's Seq[DiscoveredFrameworkTests] holds
    *     only the frameworks that actually have classes in that bucket.
    */
  def distribute(
      discoveredTests: Seq[DiscoveredFrameworkTests],
      history: TestHistory,
      maxForks: Int
  ): Seq[Seq[DiscoveredFrameworkTests]] = {
    val totalClasses = discoveredTests.map(_.testClasses.size).sum
    if totalClasses == 0 || maxForks <= 0 then return Seq.empty
    val effectiveForks = math.min(maxForks, totalClasses)
    val fallbackDuration = medianOf(history.stats.values.map(_.durationMs).toSeq)

    case class WeightedClass(
        frameworkName: String,
        frameworkClassName: String,
        cls: DiscoveredFrameworkTest,
        weight: Long
    )

    val weighted = discoveredTests.flatMap { dft =>
      dft.testClasses.map { cls =>
        val w = history.durationOf(cls.className).getOrElse(fallbackDuration)
        WeightedClass(dft.frameworkName, dft.frameworkClassName, cls, w)
      }
    }.sortBy(wc => -wc.weight) // descending by weight

    val buckets = Array.fill(effectiveForks)((0L, Vector.empty[WeightedClass]))
    weighted.foreach { wc =>
      var minIdx = 0
      var minLoad = buckets(0)._1
      var i = 1
      while i < buckets.length do {
        if buckets(i)._1 < minLoad then {
          minLoad = buckets(i)._1
          minIdx = i
        }
        i += 1
      }
      val (load, items) = buckets(minIdx)
      buckets(minIdx) = (load + math.max(wc.weight, 1L), items :+ wc)
    }

    buckets.iterator.map { case (_, items) =>
      items
        .groupBy(wc => (wc.frameworkName, wc.frameworkClassName))
        .toSeq
        .map { case ((fname, fcls), members) =>
          DiscoveredFrameworkTests(fname, fcls, members.map(_.cls))
        }
    }.toSeq
  }

  private def medianOf(values: Seq[Long]): Long = {
    if values.isEmpty then 1L
    else {
      val sorted = values.sorted
      sorted(sorted.size / 2)
    }
  }
}
