package ba.sake.deder.publish

import ba.sake.deder.Hashable
import ba.sake.deder.hashStr
import ba.sake.tupson.JsonRW

case class PomSettings(
    groupId: String,
    artifactId: String,
    version: String
) derives JsonRW

object PomSettings {

  given Hashable[PomSettings] with {
    override def hashStr(value: PomSettings): String =
      Seq[String](value.groupId, value.artifactId, value.version).hashStr
  }
}
