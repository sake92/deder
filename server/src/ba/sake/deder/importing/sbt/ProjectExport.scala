package ba.sake.deder.importing.sbt

import ba.sake.tupson.JsonRW
import ba.sake.tupson.parseJson

case class ProjectExport(
    id: String,
    base: String, // base directory
    name: String,
    javacOptions: Seq[String],
    scalaVersion: String,
    scalacOptions: Seq[String],
    interProjectDependencies: Seq[InterProjectDependencyExport],
    externalDependencies: Seq[DependencyExport],
    repositories: Seq[String], // custom maven repos
    sourceDirs: Seq[String],
    testSourceDirs: Seq[String],
    resourceDirs: Seq[String],
    testResourceDirs: Seq[String],
    plugins: Seq[String],
    // publish stuff
    organization: String, // groupId
    artifactName: String,
    artifactType: String, // jar, war ..
    artifactClassifier: Option[String], // sources, javadoc ..
    version: String,
    description: String,
    homepage: Option[String],
    developers: Seq[DeveloperExport],
    licenses: Seq[LicenseExport],
    scmInfo: Option[ScmInfoExport]
) derives JsonRW

enum ExportedPlatform:
  case Jvm, Js, Native

object ExportedPlatform:
  def fromBase(base: String): ExportedPlatform =
    os.Path(base).last match
      case "js" | ".js"         => ExportedPlatform.Js
      case "native" | ".native" => ExportedPlatform.Native
      case _                    => ExportedPlatform.Jvm

case class ExportedProjectExportFileName(
    projectId: String,
    scalaVersion: String
)

case class ExportedProjectExportFile(
    payload: ProjectExport,
    exportedProjectId: String,
    exportedScalaVersion: String,
    platform: ExportedPlatform
):
  export payload.*

object ExportedProjectExportFile:
  def parse(path: os.Path, json: String): ExportedProjectExportFile =
    fromPayload(path, json.parseJson[ProjectExport])

  def fromPayload(path: os.Path, payload: ProjectExport): ExportedProjectExportFile =
    val fileName = parseFileName(path.last)
    if fileName.projectId != payload.id then
      throw IllegalArgumentException(
        s"Exported sbt project file name project id '${fileName.projectId}' does not match payload id '${payload.id}' in $path"
      )
    if fileName.scalaVersion != payload.scalaVersion then
      throw IllegalArgumentException(
        s"Exported sbt project file name scalaVersion '${fileName.scalaVersion}' does not match payload scalaVersion '${payload.scalaVersion}' in $path"
      )
    ExportedProjectExportFile(
      payload = payload,
      exportedProjectId = fileName.projectId,
      exportedScalaVersion = fileName.scalaVersion,
      platform = ExportedPlatform.fromBase(payload.base)
    )

  def parseFileName(fileName: String): ExportedProjectExportFileName =
    val jsonSuffix = ".json"
    if !fileName.endsWith(jsonSuffix) then
      throw IllegalArgumentException(s"Expected exported sbt project file to end with $jsonSuffix: $fileName")
    val baseName = fileName.stripSuffix(jsonSuffix)
    val splitIdx = baseName.lastIndexOf('_')
    if splitIdx <= 0 || splitIdx == baseName.length - 1 then
      throw IllegalArgumentException(
        s"Expected exported sbt project file name to look like <projectId>_<scalaVersion>.json: $fileName"
      )
    ExportedProjectExportFileName(
      projectId = baseName.take(splitIdx),
      scalaVersion = baseName.drop(splitIdx + 1)
    )

case class DependencyExport(
    organization: String, // groupId
    name: String, // artifactName
    revision: String, // version
    extraAttributes: Map[String, String], // type, classifier ..
    configurations: Option[String], // provided, test ..
    excludes: Seq[DependencyExcludeExport],
    crossVersion: String, // "binary", "full", "none", etc.
    platformOpt: Option[String] = None // "js", "native", or None (JVM)
) derives JsonRW

case class DependencyExcludeExport(
    organization: String, // groupId
    name: String // artifactName
) derives JsonRW

case class InterProjectDependencyExport(
    project: String,
    configuration: String
) derives JsonRW

case class DeveloperExport(id: String, name: String, email: String, url: String) derives JsonRW

case class LicenseExport(name: String, url: String) derives JsonRW

case class ScmInfoExport(browseUrl: String, connection: String, devConnection: Option[String]) derives JsonRW
