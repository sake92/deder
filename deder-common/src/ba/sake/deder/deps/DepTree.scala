package ba.sake.deder.deps

case class DepNode(
    org: String,
    name: String,
    version: String,
    filePath: os.Path,
    fileSizeBytes: Long,
    depth: Int,
    parents: Seq[String]
) {
  def coordinate: String = s"$org:$name:$version"
}

case class DepConflict(
    coordinate: String,
    requestedVersions: Map[String, Seq[String]],
    resolvedVersion: String,
    isConflict: Boolean
)

case class DepTree(
    module: String,
    allDeps: Seq[DepNode],
    rootDeps: Seq[DepNode],
    conflicts: Seq[DepConflict],
    totalSizeBytes: Long,
    totalUniqueSizeBytes: Long
) {
  def conflictCount: Int = conflicts.count(_.isConflict)
}
