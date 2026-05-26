package ba.sake.deder.importing

import ba.sake.deder.config.DederProject

/** Top-level representation of a deder.pkl project. Build-tool-agnostic. */
case class DederBuild(
    moduleGroups: Seq[ModuleGroup],
    repositories: Seq[RepositoryDef]
)

/** A group of modules sharing a root and builder variable. concreteModules preserve the exported scala-version/platform
  * slices.
  */
case class ModuleGroup(
    builderVarName: String,
    root: String,
    layout: DederProject.DirLayout,
    crossScalaVersions: Seq[String],
    concreteModules: Seq[ConcreteModule],
    usesTpolecat: Boolean = false,
    usesTypelevel: Boolean = false
):
  private def platformModule(platforms: Set[String]): Option[ModuleDef] =
    concreteModules.find(cm => platforms.contains(cm.platform)).map(_.module)

  def jvmModule: ModuleDef =
    platformModule(Set("main", "jvm")).get

  def jsModule: Option[ModuleDef] =
    platformModule(Set("js"))

  def nativeModule: Option[ModuleDef] =
    platformModule(Set("native"))

  def hasJsModule: Boolean =
    concreteModules.exists(_.platform == "js")

  def hasNativeModule: Boolean =
    concreteModules.exists(_.platform == "native")

object ModuleGroup:
  def apply(
      builderVarName: String,
      root: String,
      layout: DederProject.DirLayout,
      crossScalaVersions: Seq[String],
      jvmModule: ModuleDef,
      jsModule: Option[ModuleDef],
      nativeModule: Option[ModuleDef],
      hasJsModule: Boolean,
      hasNativeModule: Boolean,
      usesTpolecat: Boolean,
      usesTypelevel: Boolean
  ): ModuleGroup =
    val versions = if crossScalaVersions.nonEmpty then crossScalaVersions else Seq(jvmModule.scalaVersion)
    val jvmPlatform = if hasJsModule || hasNativeModule then "jvm" else "main"
    val concreteModules = versions.flatMap { scalaVersion =>
      Seq(
        Some(ConcreteModule(builderVarName, scalaVersion, jvmPlatform, jvmModule.copy(scalaVersion = scalaVersion))),
        jsModule.map(m => ConcreteModule(builderVarName, scalaVersion, "js", m.copy(scalaVersion = scalaVersion))),
        nativeModule.map(m =>
          ConcreteModule(builderVarName, scalaVersion, "native", m.copy(scalaVersion = scalaVersion))
        )
      ).flatten
    }
    new ModuleGroup(builderVarName, root, layout, crossScalaVersions, concreteModules, usesTpolecat, usesTypelevel)

case class ConcreteModule(
    sbtProjectId: String,
    scalaVersion: String,
    platform: String,
    module: ModuleDef
)

/** All properties of one concrete module (single platform). */
case class ModuleDef(
    scalaVersion: String,
    scalacOptions: Seq[String],
    javacOptions: Seq[String],
    deps: Seq[DepDef],
    scalacPluginDeps: Seq[DepDef],
    testDeps: Seq[DepDef],
    moduleDeps: Seq[ModuleDepRef],
    testModuleDeps: Seq[ModuleDepRef],
    scalaJsVersion: Option[String],
    scalaNativeVersion: Option[String],
    publish: Option[PublishInfo],
    sources: Seq[String],
    testSources: Seq[String],
    resources: Seq[String],
    testResources: Seq[String]
)

object ModuleDef:
  val empty: ModuleDef = ModuleDef(
    scalaVersion = "",
    scalacOptions = Seq.empty,
    javacOptions = Seq.empty,
    deps = Seq.empty,
    scalacPluginDeps = Seq.empty,
    testDeps = Seq.empty,
    moduleDeps = Seq.empty,
    testModuleDeps = Seq.empty,
    scalaJsVersion = None,
    scalaNativeVersion = None,
    publish = None,
    sources = Seq.empty,
    testSources = Seq.empty,
    resources = Seq.empty,
    testResources = Seq.empty
  )

/** A dependency with its formatted Pkl string and metadata for summary. */
case class DepDef(
    formatted: String,
    organization: String,
    name: String
)

/** Reference to another module group's module. */
case class ModuleDepRef(
    targetGroup: String,
    targetPlatform: String,
    targetScalaVersion: Option[String] = None,
    isTest: Boolean
)

/** Publish metadata (maps to DederProject pomSettings / publishTo). */
case class PublishInfo(
    organization: String,
    artifactName: String,
    version: String,
    description: Option[String],
    homepage: Option[String],
    developers: Seq[DeveloperDef],
    licenses: Seq[LicenseDef],
    scmInfo: Option[ScmDef]
)

case class DeveloperDef(id: String, name: String, email: String)
case class LicenseDef(name: String, url: String)
case class ScmDef(browseUrl: String, connection: String, devConnection: Option[String])
case class RepositoryDef(url: String)

