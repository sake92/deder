package ba.sake.deder.importing

import ba.sake.deder.config.DederProject

/** Top-level representation of a deder.pkl project. Build-tool-agnostic. */
case class DederBuild(
    dederVersion: String,
    moduleGroups: Seq[ModuleGroup],
    repositories: Seq[RepositoryDef],
    warnings: Seq[ImportWarning]
)

/** A group of modules sharing a root and builder variable.
  * jvm is always present; js/native are optional for cross-platform projects. */
case class ModuleGroup(
    builderVarName:  String,
    root:            String,
    layout:          DederProject.DirLayout,
    crossScalaVersions: Seq[String],
    jvmModule:       ModuleDef,
    jsModule:        Option[ModuleDef],
    nativeModule:    Option[ModuleDef],
    hasJsModule:     Boolean,
    hasNativeModule: Boolean
)

/** All properties of one concrete module (single platform). */
case class ModuleDef(
    scalaVersion:       String,
    scalacOptions:      Seq[String],
    javacOptions:       Seq[String],
    deps:               Seq[DepDef],
    scalacPluginDeps:   Seq[DepDef],
    testDeps:           Seq[DepDef],
    moduleDeps:         Seq[ModuleDepRef],
    testModuleDeps:     Seq[ModuleDepRef],
    scalaJsVersion:     Option[String],
    scalaNativeVersion: Option[String],
    publish:            Option[PublishInfo],
    sources:            Seq[String],
    testSources:        Seq[String],
    resources:          Seq[String],
    testResources:      Seq[String]
)

/** A dependency with its formatted Pkl string and metadata for summary. */
case class DepDef(
    formatted:    String,
    organization: String,
    name:         String
)

/** Reference to another module group's module. */
case class ModuleDepRef(
    targetGroup:    String,
    targetPlatform: String,
    isTest:         Boolean
)

/** Publish metadata (maps to DederProject pomSettings / publishTo). */
case class PublishInfo(
    organization: String,
    artifactName: String,
    version:      String,
    description:  Option[String],
    homepage:     Option[String],
    developers:   Seq[DeveloperDef],
    licenses:     Seq[LicenseDef],
    scmInfo:      Option[ScmDef]
)

case class DeveloperDef(id: String, name: String, email: String)
case class LicenseDef(name: String, url: String)
case class ScmDef(browseUrl: String, connection: String, devConnection: Option[String])
case class RepositoryDef(url: String)

/** Warnings collected during analysis about things that couldn't be fully mapped. */
enum ImportWarning:
    case Reserved extends ImportWarning
