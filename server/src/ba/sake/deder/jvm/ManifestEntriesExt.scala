package ba.sake.deder.jvm

extension (entries: ManifestEntries) {
  def toJarManifest: JarManifest = {
    val base = JarManifest.Default.add(entries.mainAttributes.toSeq*)
    entries.groups.foldLeft(base) { case (m, (group, attrs)) =>
      m.addGroup(group, attrs.toSeq*)
    }
  }
}
