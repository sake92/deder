package ba.sake.deder.scalanative

import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.scalanative.build.*
import scala.scalanative.util.Scope

class ScalaNativeLinker(notifications: ServerNotificationsLogger, moduleId: String)(using ExecutionContext) {
  def link(
      nirPaths: Seq[os.Path], // classesDir + all JARs in compileClasspath
      outputDir: os.Path,
      mainClass: Option[String],
      nativeLibs: Seq[String],
      gc: String = "immix",
      mode: String = "debug",
      multithreading: Boolean = false,
      lto: String = "none",
      embedResources: Boolean = false,
      extraLinkingOptions: Seq[String] = Seq.empty,
      extraCompileOptions: Seq[String] = Seq.empty
  ): Unit = Scope { implicit scope =>
    notifications.add(ServerNotification.logInfo("Linking scala-native binary...", Some(moduleId)))

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
      ServerNotification.logInfo("Linking succeeded: " + binaryPath, Some(moduleId))
    )
  }
}
