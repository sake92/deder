package ba.sake.deder.scalanative

import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

import scala.concurrent.ExecutionContext
import scala.scalanative.build.*
import scala.scalanative.util.Scope

private[scalanative] class ScalaNativeLinker(notifications: ServerNotificationsLogger, moduleId: String)(using
    ExecutionContext
) {

  /** Config-driven link: applies all user settings verbatim, no overrides. */
  def link(
      nirPaths: Seq[os.Path],
      outputDir: os.Path,
      mainClass: Option[String],
      settings: ScalaNativeLinkerSettings
  ): os.Path = linkImpl(
    nirPaths = nirPaths,
    outputDir = outputDir,
    mainClass = mainClass,
    gc = settings.gc,
    mode = settings.mode,
    multithreading = settings.multithreading,
    lto = settings.lto,
    embedResources = settings.embedResources,
    linkStubs = settings.linkStubs,
    check = settings.check,
    checkFatalWarnings = settings.checkFatalWarnings,
    optimize = settings.optimize,
    targetTriple = settings.targetTriple,
    extraLinkingOptions = settings.nativeLinkingOptions,
    extraCompileOptions = settings.nativeCompileOptions,
    extraCOptions = settings.nativeCOptions,
    extraCppOptions = settings.nativeCppOptions,
    resourceIncludePatterns = settings.resourceIncludePatterns,
    resourceExcludePatterns = settings.resourceExcludePatterns,
    label = "Linking"
  )

  /** Fast/debug link: reads user config, then forces debug mode and disables LTO. */
  def linkFast(
      nirPaths: Seq[os.Path],
      outputDir: os.Path,
      mainClass: Option[String],
      settings: ScalaNativeLinkerSettings
  ): os.Path = linkImpl(
    nirPaths = nirPaths,
    outputDir = outputDir,
    mainClass = mainClass,
    gc = settings.gc,
    mode = "debug",
    multithreading = settings.multithreading,
    lto = "none",
    embedResources = settings.embedResources,
    linkStubs = settings.linkStubs,
    check = settings.check,
    checkFatalWarnings = settings.checkFatalWarnings,
    optimize = settings.optimize,
    targetTriple = settings.targetTriple,
    extraLinkingOptions = settings.nativeLinkingOptions,
    extraCompileOptions = settings.nativeCompileOptions,
    extraCOptions = settings.nativeCOptions,
    extraCppOptions = settings.nativeCppOptions,
    resourceIncludePatterns = settings.resourceIncludePatterns,
    resourceExcludePatterns = settings.resourceExcludePatterns,
    label = "Fast-linking"
  )

  /** Full/production link: reads user config, then forces release-full mode and full LTO. */
  def linkFull(
      nirPaths: Seq[os.Path],
      outputDir: os.Path,
      mainClass: Option[String],
      settings: ScalaNativeLinkerSettings
  ): os.Path = linkImpl(
    nirPaths = nirPaths,
    outputDir = outputDir,
    mainClass = mainClass,
    gc = settings.gc,
    mode = "release-full",
    multithreading = settings.multithreading,
    lto = "full",
    embedResources = settings.embedResources,
    linkStubs = settings.linkStubs,
    check = settings.check,
    checkFatalWarnings = settings.checkFatalWarnings,
    optimize = settings.optimize,
    targetTriple = settings.targetTriple,
    extraLinkingOptions = settings.nativeLinkingOptions,
    extraCompileOptions = settings.nativeCompileOptions,
    extraCOptions = settings.nativeCOptions,
    extraCppOptions = settings.nativeCppOptions,
    resourceIncludePatterns = settings.resourceIncludePatterns,
    resourceExcludePatterns = settings.resourceExcludePatterns,
    label = "Full-linking"
  )

  private def linkImpl(
      nirPaths: Seq[os.Path],
      outputDir: os.Path,
      mainClass: Option[String],
      gc: String,
      mode: String,
      multithreading: Boolean,
      lto: String,
      embedResources: Boolean,
      linkStubs: Boolean,
      check: Boolean,
      checkFatalWarnings: Boolean,
      optimize: Boolean,
      targetTriple: Option[String],
      extraLinkingOptions: Seq[String],
      extraCompileOptions: Seq[String],
      extraCOptions: Seq[String],
      extraCppOptions: Seq[String],
      resourceIncludePatterns: Seq[String],
      resourceExcludePatterns: Seq[String],
      label: String
  ): os.Path = Scope { implicit scope =>
    notifications.add(ServerNotification.logInfo(s"$label scala-native binary...", Some(moduleId), source = Some("scalanative")))

    val clang = Discover.clang()
    val clangpp = Discover.clangpp()
    val linkopts = Discover.linkingOptions()
    val compopts = Discover.compileOptions()

    val nativeConfig = NativeConfig.empty
      .withGC(GC(gc))
      .withMode(Mode(mode))
      .withMultithreading(enabled = multithreading)
      .withLTO(LTO(lto))
      .withEmbedResources(embedResources)
      .withLinkStubs(linkStubs)
      .withCheck(check)
      .withCheckFatalWarnings(checkFatalWarnings)
      .withOptimize(optimize)
      .withClang(clang)
      .withClangPP(clangpp)
      .withLinkingOptions(linkopts ++ extraLinkingOptions)
      .withCompileOptions(compopts ++ extraCompileOptions)
      .withCOptions(extraCOptions)
      .withCppOptions(extraCppOptions)
      .withResourceIncludePatterns(resourceIncludePatterns)
      .withResourceExcludePatterns(resourceExcludePatterns)

    val nativeConfigWithTriple = targetTriple match {
      case Some(triple) => nativeConfig.withTargetTriple(triple)
      case None         => nativeConfig
    }

    val config = Config.empty
      .withCompilerConfig(nativeConfigWithTriple)
      .withClassPath(nirPaths.map(_.toNIO))
      .withModuleName(moduleId)
      .withMainClass(mainClass)
      .withBaseDir(outputDir.toNIO)

    val binaryPath = Build.buildCachedAwait(config)
    notifications.add(
      ServerNotification.logInfo(s"$label succeeded: " + binaryPath, Some(moduleId), source = Some("scalanative"))
    )
    os.Path(binaryPath)
  }
}
