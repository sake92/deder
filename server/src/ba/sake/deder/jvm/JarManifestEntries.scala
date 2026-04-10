package ba.sake.deder.jvm

import ba.sake.tupson.JsonRW
import ba.sake.deder.{*, given}

case class ManifestEntries(
    mainAttributes: Map[String, String],
    groups: Map[String, Map[String, String]]
) derives JsonRW {
  def toJarManifest: JarManifest = {
    val base = JarManifest.Default.add(mainAttributes.toSeq*)
    groups.foldLeft(base) { case (m, (group, attrs)) =>
      m.addGroup(group, attrs.toSeq*)
    }
  }
}

object ManifestEntries {
  val Empty: ManifestEntries = ManifestEntries(Map.empty, Map.empty)

  given Hashable[ManifestEntries] with {
    override def hashStr(value: ManifestEntries): String =
      Seq(value.mainAttributes.hashStr, value.groups.hashStr).hashStr
  }
}
