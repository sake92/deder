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
    val (deduped, conflicts) = grouped.values.partition(_.size == 1)
    val keptSingletons = deduped.flatten.toSeq
    val (duplicateResults, conflictInfos) = conflicts.map { group =>
      val versions = group.map(_.applied.version)
      val kept = group.last
      val mod = kept.applied.module
      val coordinate = s"${mod.organization}:${mod.name}"
      val conflict = DedupConflict(
        coordinate = coordinate,
        versions = versions,
        keptVersion = kept.applied.version
      )
      (kept, conflict)
    }.unzip

    DedupResult(
      deduplicated = (keptSingletons ++ duplicateResults),
      conflicts = conflictInfos.toSeq
    )
}
