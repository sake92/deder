package ba.sake.deder.deps

import ba.sake.tupson.JsonRW
import ba.sake.deder.PlainTextWritable

case class DepNode(
    org: String,
    name: String,
    version: String,
    filePath: String,
    fileSizeBytes: Long,
    depth: Int,
    parents: Seq[String]
) derives JsonRW {
  def coordinate: String = s"$org:$name:$version"
}

case class DepConflict(
    coordinate: String,
    requestedVersions: Map[String, Seq[String]],
    resolvedVersion: String,
    isConflict: Boolean
) derives JsonRW

case class DepTree(
    module: String,
    allDeps: Seq[DepNode],
    rootDeps: Seq[DepNode],
    conflicts: Seq[DepConflict],
    totalSizeBytes: Long,
    totalUniqueSizeBytes: Long
) derives JsonRW {
  def conflictCount: Int = conflicts.count(_.isConflict)
}

object DepTree:
  given PlainTextWritable[DepTree] with
    def write(tree: DepTree): String =
      val buf = new StringBuilder
      buf ++= s"Dependency Tree for module: ${tree.module}\n"
      buf ++= s"Total size: ${formatBytes(tree.totalSizeBytes)}\n"
      if tree.conflicts.exists(_.isConflict) then
        buf ++= s"⚠️  Conflicts: ${tree.conflictCount}\n"
      buf ++= "\n"
      buf ++= "Direct Dependencies:\n"
      for node <- tree.rootDeps do
        renderNodeTree(node, tree, buf, "", isLast = true)
      if tree.conflicts.exists(_.isConflict) then
        buf ++= "\n\n⚠️  Version Conflicts:\n"
        for conflict <- tree.conflicts.filter(_.isConflict) do
          buf ++= s"  ${conflict.coordinate}:\n"
          for version <- conflict.requestedVersions.keys do
            buf ++= s"    • $version\n"
          buf ++= s"    ➜ Resolved: ${conflict.resolvedVersion}\n\n"
      buf.toString

  private def renderNodeTree(
      node: DepNode,
      tree: DepTree,
      buf: StringBuilder,
      prefix: String,
      isLast: Boolean
  ): Unit =
    val connector = if isLast then "└── " else "├── "
    val directSize = formatBytes(node.fileSizeBytes)
    val transitiveSize = formatBytes(calculateTransitiveSize(node, tree))
    val conflict = tree.conflicts.find(c =>
      c.coordinate == s"${node.org}:${node.name}" && c.isConflict
    )
    val conflictMark = if conflict.isDefined then " [⚠️ CONFLICT]" else ""
    buf ++= s"$prefix$connector${node.coordinate} ($directSize | $transitiveSize)$conflictMark\n"
    val children = tree.allDeps.filter(_.parents.contains(node.coordinate))
    for (child, idx) <- children.zipWithIndex do
      val newPrefix = prefix + (if isLast then "    " else "│   ")
      renderNodeTree(child, tree, buf, newPrefix, idx == children.length - 1)

  private def calculateTransitiveSize(node: DepNode, tree: DepTree): Long =
    node.fileSizeBytes +
      tree.allDeps
        .filter(_.parents.contains(node.coordinate))
        .map(child => calculateTransitiveSize(child, tree))
        .sum

  private def formatBytes(bytes: Long): String =
    if bytes < 1024 then s"${bytes}B"
    else if bytes < 1024 * 1024 then s"${bytes / 1024}KB"
    else if bytes < 1024L * 1024 * 1024 then s"${bytes / (1024 * 1024)}MB"
    else s"${bytes / (1024L * 1024 * 1024)}GB"
