package ba.sake.deder.importing.sbt

import ba.sake.tupson.JsonRW

case class ProjectExport(
    id: String,
    base: String, // base directory
    name: String,
    javacOptions: Seq[String],
    scalaVersion: String,
    crossScalaVersions: Seq[String],
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

case class DependencyExport(
    organization: String, // groupId
    name: String, // artifactName
    revision: String, // version
    extraAttributes: Map[String, String], // type, classifier ..
    configurations: Option[String], // provided, test ..
    excludes: Seq[DependencyExcludeExport],
    crossVersion: String
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
