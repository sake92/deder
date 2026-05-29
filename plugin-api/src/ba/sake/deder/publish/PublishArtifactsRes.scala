package ba.sake.deder.publish

import ba.sake.tupson.JsonRW

case class PublishArtifactsRes(
    pom: PomSettings,
    outDir: os.Path
) derives JsonRW
