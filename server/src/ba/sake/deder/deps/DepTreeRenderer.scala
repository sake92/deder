package ba.sake.deder.deps

object DepTreeRenderer {

  def renderTree(tree: DepTree): String = {
    val buffer = StringBuilder()
    buffer ++= s"Dependency Tree for module: ${tree.module}\n"
    buffer ++= s"Total size: ${formatBytes(tree.totalSizeBytes)}\n"
    if tree.conflicts.exists(_.isConflict) then
      buffer ++= s"⚠️  Conflicts: ${tree.conflictCount}\n"
    buffer ++= "\n"
    
    buffer ++= "Direct Dependencies:\n"
    for node <- tree.rootDeps do
      renderNodeTree(node, tree, buffer, "", isLast = true)
    
    // Conflict summary section
    if tree.conflicts.exists(_.isConflict) then
      buffer ++= "\n\n⚠️  Version Conflicts:\n"
      for conflict <- tree.conflicts.filter(_.isConflict) do
        buffer ++= s"  ${conflict.coordinate}:\n"
        for version <- conflict.requestedVersions.keys do
          buffer ++= s"    • $version\n"
        buffer ++= s"    ➜ Resolved: ${conflict.resolvedVersion}\n\n"
    
    buffer.toString
  }

  private def renderNodeTree(
      node: DepNode,
      tree: DepTree,
      buffer: StringBuilder,
      prefix: String,
      isLast: Boolean
  ): Unit = {
    val connector = if isLast then "└── " else "├── "
    val directSize = formatBytes(node.fileSizeBytes)
    val transitiveSize = formatBytes(calculateTransitiveSize(node, tree))
    
    val conflict = tree.conflicts.find(c =>
      c.coordinate == s"${node.org}:${node.name}" && c.isConflict
    )
    val conflictMark = if conflict.isDefined then " [⚠️ CONFLICT]" else ""
    
    buffer ++= s"$prefix$connector${node.org}:${node.name}:${node.version} ($directSize | $transitiveSize)$conflictMark\n"
    
    // Render children
    val children = tree.allDeps.filter(_.parents.contains(node.coordinate))
    for (child, idx) <- children.zipWithIndex do
      val newPrefix = prefix + (if isLast then "    " else "│   ")
      renderNodeTree(child, tree, buffer, newPrefix, idx == children.length - 1)
  }

  private def calculateTransitiveSize(node: DepNode, tree: DepTree): Long = {
    node.fileSizeBytes +
    tree.allDeps
      .filter(_.parents.contains(node.coordinate))
      .map(child => calculateTransitiveSize(child, tree))
      .sum
  }

  def renderFlat(tree: DepTree): String = {
    val buffer = StringBuilder()
    buffer ++= s"Flat Dependency List for module: ${tree.module}\n"
    buffer ++= s"Total size: ${formatBytes(tree.totalSizeBytes)}\n"
    if tree.conflicts.exists(_.isConflict) then
      buffer ++= s"⚠️  Conflicts: ${tree.conflictCount}\n"
    buffer ++= "\n"
    
    for node <- tree.allDeps.sortBy(n => (n.org, n.name, n.version)) do
      val sizeStr = formatBytes(node.fileSizeBytes)
      val conflict = tree.conflicts.find(c =>
        c.coordinate == s"${node.org}:${node.name}" && c.isConflict && c.resolvedVersion != node.version
      )
      val mark = if conflict.isDefined then " [NOT RESOLVED]" else ""
      buffer ++= s"${node.org}:${node.name}:${node.version} ($sizeStr)$mark\n"
    
    if tree.conflicts.exists(_.isConflict) then
      buffer ++= s"\n⚠️  ${tree.conflictCount} version conflict(s) detected\n"
    
    buffer.toString
  }

  def renderJson(tree: DepTree): String = {
    import scala.collection.mutable
    import org.typelevel.jawn.ast._
    
    val depNodesJson = tree.allDeps.map { node =>
      JObject(mutable.Map(
        "org" -> (JString(node.org): JValue),
        "name" -> (JString(node.name): JValue),
        "version" -> (JString(node.version): JValue),
        "filePath" -> (JString(node.filePath.toString()): JValue),
        "fileSizeBytes" -> (JNum(node.fileSizeBytes.toDouble): JValue),
        "depth" -> (JNum(node.depth.toDouble): JValue),
        "parents" -> (JArray(node.parents.map(JString(_)).toArray): JValue)
      ))
    }
    
    val conflictsJson = tree.conflicts.map { conflict =>
      val requestedVersionsJson = conflict.requestedVersions.view.mapValues { versions =>
        JArray(versions.map(JString(_)).toArray): JValue
      }.to(mutable.Map)
      JObject(mutable.Map(
        "coordinate" -> (JString(conflict.coordinate): JValue),
        "requestedVersions" -> (JObject(requestedVersionsJson): JValue),
        "resolvedVersion" -> (JString(conflict.resolvedVersion): JValue),
        "isConflict" -> (JBool(conflict.isConflict): JValue)
      ))
    }
    
    val treeJson = JObject(mutable.Map(
      "module" -> (JString(tree.module): JValue),
      "allDeps" -> (JArray(depNodesJson.toArray): JValue),
      "rootDeps" -> (JArray(tree.rootDeps.map { node =>
        JObject(mutable.Map(
          "org" -> (JString(node.org): JValue),
          "name" -> (JString(node.name): JValue),
          "version" -> (JString(node.version): JValue),
          "filePath" -> (JString(node.filePath.toString()): JValue),
          "fileSizeBytes" -> (JNum(node.fileSizeBytes.toDouble): JValue),
          "depth" -> (JNum(node.depth.toDouble): JValue),
          "parents" -> (JArray(node.parents.map(JString(_)).toArray): JValue)
        ))
      }.toArray): JValue),
      "conflicts" -> (JArray(conflictsJson.toArray): JValue),
      "totalSizeBytes" -> (JNum(tree.totalSizeBytes.toDouble): JValue),
      "totalUniqueSizeBytes" -> (JNum(tree.totalUniqueSizeBytes.toDouble): JValue)
    ))
    
    CanonicalRenderer.render(treeJson)
  }

  private def formatBytes(bytes: Long): String = {
    if bytes < 1024 then s"${bytes}B"
    else if bytes < 1024 * 1024 then s"${bytes / 1024}KB"
    else if bytes < 1024 * 1024 * 1024 then s"${bytes / (1024 * 1024)}MB"
    else s"${bytes / (1024 * 1024 * 1024)}GB"
  }
}
