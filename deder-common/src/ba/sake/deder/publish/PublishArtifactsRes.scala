package ba.sake.deder.publish

import ba.sake.deder.{*, given}
import ba.sake.tupson.JsonRW

case class PublishArtifactsRes(
    pom: PomSettings,
    outDir: os.Path
) derives JsonRW

object PublishArtifactsRes {
  // Custom Hashable that includes actual artifact file content hash, not just the path string.
  // Without this, JsonRW-derived Hashable (low-priority fallback) only hashes the JSON
  // representation — which serializes outDir as a path string, missing any file content changes.
  given Hashable[PublishArtifactsRes] with {
    def hashStr(value: PublishArtifactsRes): String =
      val pomHash = Hashable[PomSettings].hashStr(value.pom)
      val dirHash = Hashable[os.Path].hashStr(value.outDir)
      s"${pomHash}-${dirHash}".hashStr
  }
}
