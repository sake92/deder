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
      nativeLibs: Seq[String]
  ): Unit = Scope { implicit scope =>
    notifications.add(ServerNotification.logInfo("Linking scala-native binary..."))

    val clang = Discover.clang()
    val clangpp = Discover.clangpp()
    val linkopts = Discover.linkingOptions()
    val compopts = Discover.compileOptions()

    val config = Config.empty
      .withCompilerConfig {
        NativeConfig.empty
          .withGC(GC.default)
          .withMode(Mode.default)
          .withMultithreading(enabled = false)
          .withClang(clang)
          .withClangPP(clangpp)
          .withLinkingOptions(linkopts)
          .withCompileOptions(compopts)
          .withLinkStubs(true)
      }
      .withClassPath(nirPaths.map(_.toNIO))
      .withModuleName(moduleId)
      .withMainClass(mainClass)
      .withBaseDir(outputDir.toNIO)

    val binaryPath = Build.buildCachedAwait(config)
    notifications.add(
      ServerNotification.logInfo("Linking succeeded: " + binaryPath)
    )
  }
}
