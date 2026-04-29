package ba.sake.deder.scalajs

import ba.sake.deder.config.DederProject.{
  ScalaJsLinkerConfig,
  ScalaJsModuleKind,
  ScalaJsModuleSplitStyle,
  ScalaJsESVersion
}

import scala.concurrent.{Await, ExecutionContext, Future}
import org.scalajs.linker.*
import org.scalajs.linker.interface.*
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*

class ScalaJsLinker(notifications: ServerNotificationsLogger, moduleId: String)(using ExecutionContext) {

  /** Config-driven link: applies all user settings verbatim, no overrides. */
  def link(
      irContainers: Seq[os.Path],
      outputDir: os.Path,
      moduleInitializers: Seq[ModuleInitializer],
      jsModuleKind: ScalaJsModuleKind,
      linkerConfig: ScalaJsLinkerConfig
  ): Report = {
    notifications.add(ServerNotification.logInfo("Linking scalajs module...", Some(moduleId)))
    val config = buildLinkerConfig(jsModuleKind, linkerConfig)
    linkImpl(irContainers, outputDir, moduleInitializers, config, "Linking")
  }

  /** Fast/debug link: reads user config, then overrides mode-specific flags for dev. */
  def linkFast(
      irContainers: Seq[os.Path],
      outputDir: os.Path,
      moduleInitializers: Seq[ModuleInitializer],
      jsModuleKind: ScalaJsModuleKind,
      linkerConfig: ScalaJsLinkerConfig
  ): Report = {
    notifications.add(ServerNotification.logInfo("Fast-linking scalajs module...", Some(moduleId)))
    val config = buildLinkerConfig(jsModuleKind, linkerConfig)
      .withSourceMap(true)
      .withOptimizer(false)
      .withMinify(false)
      .withSemantics(Semantics.Defaults) // productionMode = false
      .withPrettyPrint(true)
      .withCheckIR(false)
    linkImpl(irContainers, outputDir, moduleInitializers, config, "Fast-linking")
  }

  /** Full/production link: reads user config, then overrides mode-specific flags for production. */
  def linkFull(
      irContainers: Seq[os.Path],
      outputDir: os.Path,
      moduleInitializers: Seq[ModuleInitializer],
      jsModuleKind: ScalaJsModuleKind,
      linkerConfig: ScalaJsLinkerConfig
  ): Report = {
    notifications.add(ServerNotification.logInfo("Full-linking scalajs module...", Some(moduleId)))
    val config = buildLinkerConfig(jsModuleKind, linkerConfig)
      .withSourceMap(false)
      .withOptimizer(true)
      .withMinify(true)
      .withSemantics(Semantics.Defaults.optimized) // productionMode = true
      .withPrettyPrint(false)
      .withCheckIR(false)
      .withRelativizeSourceMapBase(None)
    linkImpl(irContainers, outputDir, moduleInitializers, config, "Full-linking")
  }

  private def linkImpl(
      irContainers: Seq[os.Path],
      outputDir: os.Path,
      moduleInitializers: Seq[ModuleInitializer],
      config: StandardConfig,
      label: String
  ): Report = {
    val linker = StandardImpl.linker(config)
    val cache = StandardImpl.irFileCache().newCache

    val irFiles = PathIRContainer
      .fromClasspath(irContainers.map(_.toNIO))
      .flatMap { case (irContainers, paths) =>
        cache.cached(irContainers)
      }

    val output = PathOutputDirectory(outputDir.toNIO)
    val logger = new DederScalaJsLogger(notifications, moduleId)
    val res = irFiles
      .flatMap { files =>
        linker.link(files, moduleInitializers, output, logger)
      }

    val report = Await.result(res, Duration.Inf)
    val publicModulePaths = report.publicModules.map(_.jsFileName).map(outputDir / _)
    notifications.add(
      ServerNotification.logInfo(s"$label succeeded: " + publicModulePaths.mkString(", "), Some(moduleId))
    )
    report
  }

  private def buildLinkerConfig(
      jsModuleKind: ScalaJsModuleKind,
      cfg: ScalaJsLinkerConfig
  ): StandardConfig = {
    val moduleKind = jsModuleKind match {
      case ScalaJsModuleKind.NO_MODULE       => ModuleKind.NoModule
      case ScalaJsModuleKind.ES_MODULE       => ModuleKind.ESModule
      case ScalaJsModuleKind.COMMONJS_MODULE => ModuleKind.CommonJSModule
    }

    val moduleSplitStyle = cfg.moduleSplitStyle match {
      case ScalaJsModuleSplitStyle.FEWEST_MODULES =>
        ModuleSplitStyle.FewestModules
      case ScalaJsModuleSplitStyle.SMALLEST_MODULES =>
        ModuleSplitStyle.SmallestModules
      case ScalaJsModuleSplitStyle.SMALL_MODULES_FOR =>
        val pkgs = cfg.smallModulesFor.asScala.toList
        ModuleSplitStyle.SmallModulesFor(pkgs.toArray)
    }

    val esVersion = cfg.esVersion match {
      case ScalaJsESVersion.ES2015 => ESVersion.ES2015
      case ScalaJsESVersion.ES2016 => ESVersion.ES2016
      case ScalaJsESVersion.ES2017 => ESVersion.ES2017
      case ScalaJsESVersion.ES2018 => ESVersion.ES2018
      case ScalaJsESVersion.ES2019 => ESVersion.ES2019
      case ScalaJsESVersion.ES2020 => ESVersion.ES2020
      case ScalaJsESVersion.ES2021 => ESVersion.ES2021
    }

    val esFeatures = ESFeatures.Defaults
      .withESVersion(esVersion)
      .withAvoidClasses(cfg.avoidClasses)
      .withAvoidLetsAndConsts(cfg.avoidLetsAndConsts)
      .withAllowBigIntsForLongs(cfg.allowBigIntsForLongs)

    val semantics = if (cfg.productionMode) Semantics.Defaults.optimized else Semantics.Defaults

    val relativizeSourceMapBaseOpt = Option(cfg.relativizeSourceMapBase).map { uriStr =>
      java.net.URI.create(uriStr)
    }

    StandardConfig()
      .withModuleKind(moduleKind)
      .withModuleSplitStyle(moduleSplitStyle)
      .withESFeatures(esFeatures)
      .withSourceMap(cfg.sourceMap)
      .withOptimizer(cfg.optimizer)
      .withMinify(cfg.minify)
      .withSemantics(semantics)
      .withJSHeader(cfg.jsHeader)
      .withPrettyPrint(cfg.prettyPrint)
      .withOutputPatterns(OutputPatterns.fromJSFile(cfg.jsOutputPattern))
      .withCheckIR(cfg.checkIR)
      .withRelativizeSourceMapBase(relativizeSourceMapBaseOpt)
      .withBatchMode(cfg.batchMode)
      .withMaxConcurrentWrites(cfg.maxConcurrentWrites.toInt)
      .withExperimentalUseWebAssembly(cfg.experimentalUseWebAssembly)
      .withClosureCompiler(false) // intentionally disabled — incompatible with ES modules
  }
}
