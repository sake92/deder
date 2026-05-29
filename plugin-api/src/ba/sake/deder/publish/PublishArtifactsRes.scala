package ba.sake.deder.publish

import ba.sake.tupson.JsonRW
import ba.sake.deder.given

case class PublishArtifactsRes(
    pom: PomSettings,
    outDir: os.Path
) derives JsonRW
