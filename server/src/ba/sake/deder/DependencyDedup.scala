package ba.sake.deder

import ba.sake.deder.deps.Dependency

object DependencyDedup {

  final case class DedupResult(
      deduplicated: Seq[Dependency],
      conflicts: Seq[DedupConflict]
  )

  final case class DedupConflict(
      coordinate: String,
      versions: Seq[String],
      keptVersion: String
  )

  def deduplicate(deps: Seq[Dependency]): DedupResult =
    val grouped = deps.groupBy { dep =>
      val mod = dep.applied.module
      (mod.organization, mod.name)
    }
    // Iterate keys in first-seen order (groupBy values are non-deterministic)
    val keys = deps.map(d => (d.applied.module.organization, d.applied.module.name)).distinct
    val deduped = Seq.newBuilder[Dependency]
    val conflictInfos = Seq.newBuilder[DedupConflict]
    keys.foreach { key =>
      val group = grouped(key)
      if group.size == 1 then deduped += group.head
      else
        val versions = group.map(_.applied.version)
        val kept = group.last
        val mod = kept.applied.module
        val coordinate = s"${mod.organization}:${mod.name}"
        deduped += kept
        conflictInfos += DedupConflict(
          coordinate = coordinate,
          versions = versions,
          keptVersion = kept.applied.version
        )
    }
    DedupResult(
      deduplicated = deduped.result(),
      conflicts = conflictInfos.result()
    )
}
