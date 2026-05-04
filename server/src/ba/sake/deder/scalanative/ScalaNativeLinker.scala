package ba.sake.deder.scalanative

import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*
import scala.scalanative.build.*
import scala.scalanative.util.Scope
import ba.sake.deder.config.DederProject.ScalaNativeModule

class ScalaNativeLinker(notifications: ServerNotificationsLogger, moduleId: String)(using ExecutionContext) {

  /** Config-driven link: applies all user settings verbatim, no overrides. */
  def link(
      nirPaths: Seq[os.Path],
      outputDir: os.Path,
      mainClass: Option[String],
      nativeModule: ScalaNativeModule
  ): os.Path = linkImpl(
    nirPaths = nirPaths,
    outputDir = outputDir,
    mainClass = mainClass,
    gc = nativeModule.gc.toString,
    mode = nativeModule.mode.toString,
    multithreading = nativeModule.multithreading,
    lto = nativeModule.lto.toString,
    embedResources = nativeModule.embedResources,
    linkStubs = nativeModule.linkStubs,
    check = nativeModule.check,
    checkFatalWarnings = nativeModule.checkFatalWarnings,
    optimize = nativeModule.optimize,
    targetTriple = Option(nativeModule.targetTriple),
    extraLinkingOptions = nativeModule.nativeLinkingOptions.asScala.toSeq,
    extraCompileOptions = nativeModule.nativeCompileOptions.asScala.toSeq,
    extraCOptions = nativeModule.nativeCOptions.asScala.toSeq,
    extraCppOptions = nativeModule.nativeCppOptions.asScala.toSeq,
    resourceIncludePatterns = nativeModule.resourceIncludePatterns.asScala.toSeq,
    resourceExcludePatterns = nativeModule.resourceExcludePatterns.asScala.toSeq,
    label = "Linking"
  )

  /** Fast/debug link: reads user config, then forces debug mode and disables LTO. */
  def linkFast(
      nirPaths: Seq[os.Path],
      outputDir: os.Path,
      mainClass: Option[String],
      nativeModule: ScalaNativeModule
  ): os.Path = linkImpl(
    nirPaths = nirPaths,
    outputDir = outputDir,
    mainClass = mainClass,
    gc = nativeModule.gc.toString,
    mode = "debug",
    multithreading = nativeModule.multithreading,
    lto = "none",
    embedResources = nativeModule.embedResources,
    linkStubs = nativeModule.linkStubs,
    check = nativeModule.check,
    checkFatalWarnings = nativeModule.checkFatalWarnings,
    optimize = nativeModule.optimize,
    targetTriple = Option(nativeModule.targetTriple),
    extraLinkingOptions = nativeModule.nativeLinkingOptions.asScala.toSeq,
    extraCompileOptions = nativeModule.nativeCompileOptions.asScala.toSeq,
    extraCOptions = nativeModule.nativeCOptions.asScala.toSeq,
    extraCppOptions = nativeModule.nativeCppOptions.asScala.toSeq,
    resourceIncludePatterns = nativeModule.resourceIncludePatterns.asScala.toSeq,
    resourceExcludePatterns = nativeModule.resourceExcludePatterns.asScala.toSeq,
    label = "Fast-linking"
  )

  /** Full/production link: reads user config, then forces release-full mode and full LTO. */
  def linkFull(
      nirPaths: Seq[os.Path],
      outputDir: os.Path,
      mainClass: Option[String],
      nativeModule: ScalaNativeModule
  ): os.Path = linkImpl(
    nirPaths = nirPaths,
    outputDir = outputDir,
    mainClass = mainClass,
    gc = nativeModule.gc.toString,
    mode = "release-full",
    multithreading = nativeModule.multithreading,
    lto = "full",
    embedResources = nativeModule.embedResources,
    linkStubs = nativeModule.linkStubs,
    check = nativeModule.check,
    checkFatalWarnings = nativeModule.checkFatalWarnings,
    optimize = nativeModule.optimize,
    targetTriple = Option(nativeModule.targetTriple),
    extraLinkingOptions = nativeModule.nativeLinkingOptions.asScala.toSeq,
    extraCompileOptions = nativeModule.nativeCompileOptions.asScala.toSeq,
    extraCOptions = nativeModule.nativeCOptions.asScala.toSeq,
    extraCppOptions = nativeModule.nativeCppOptions.asScala.toSeq,
    resourceIncludePatterns = nativeModule.resourceIncludePatterns.asScala.toSeq,
    resourceExcludePatterns = nativeModule.resourceExcludePatterns.asScala.toSeq,
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
    notifications.add(ServerNotification.logInfo(s"$label scala-native binary...", Some(moduleId)))

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
      ServerNotification.logInfo(s"$label succeeded: " + binaryPath, Some(moduleId))
    )
    os.Path(binaryPath)
  }
}
