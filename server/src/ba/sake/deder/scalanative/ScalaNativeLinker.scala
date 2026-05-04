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
    extraLinkingOptions = nativeModule.nativeLinkingOptions.asScala.toSeq,
    extraCompileOptions = nativeModule.nativeCompileOptions.asScala.toSeq,
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
    extraLinkingOptions = nativeModule.nativeLinkingOptions.asScala.toSeq,
    extraCompileOptions = nativeModule.nativeCompileOptions.asScala.toSeq,
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
    extraLinkingOptions = nativeModule.nativeLinkingOptions.asScala.toSeq,
    extraCompileOptions = nativeModule.nativeCompileOptions.asScala.toSeq,
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
      extraLinkingOptions: Seq[String],
      extraCompileOptions: Seq[String],
      label: String
  ): os.Path = Scope { implicit scope =>
    notifications.add(ServerNotification.logInfo(s"$label scala-native binary...", Some(moduleId)))

    val clang = Discover.clang()
    val clangpp = Discover.clangpp()
    val linkopts = Discover.linkingOptions()
    val compopts = Discover.compileOptions()

    val config = Config.empty
      .withCompilerConfig {
        NativeConfig.empty
          .withGC(GC(gc))
          .withMode(Mode(mode))
          .withMultithreading(enabled = multithreading)
          .withLTO(LTO(lto))
          .withEmbedResources(embedResources)
          .withClang(clang)
          .withClangPP(clangpp)
          .withLinkingOptions(linkopts ++ extraLinkingOptions)
          .withCompileOptions(compopts ++ extraCompileOptions)
          .withLinkStubs(true)
      }
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
