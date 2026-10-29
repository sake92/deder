package ba.sake.deder.scalajs

import scala.jdk.CollectionConverters.*
import ba.sake.tupson.JsonRW
import ba.sake.deder.config.DederProject.ScalaJsLinkerConfig

/** Immutable cache input containing the user-supplied linker settings. */
private[scalajs] case class ScalaJsLinkerSettings(
    moduleSplitStyle: String,
    smallModulesFor: Seq[String],
    esVersion: String,
    sourceMap: Boolean,
    optimizer: Boolean,
    minify: Boolean,
    productionMode: Boolean,
    jsHeader: String,
    prettyPrint: Boolean,
    jsOutputPattern: String,
    avoidClasses: Boolean,
    avoidLetsAndConsts: Boolean,
    checkIR: Boolean,
    relativizeSourceMapBase: Option[String],
    batchMode: Boolean,
    maxConcurrentWrites: Long,
    allowBigIntsForLongs: Boolean,
    experimentalUseWebAssembly: Boolean
) derives JsonRW

private[scalajs] object ScalaJsLinkerSettings {
  def fromConfig(cfg: ScalaJsLinkerConfig): ScalaJsLinkerSettings =
    ScalaJsLinkerSettings(
      moduleSplitStyle = cfg.moduleSplitStyle.name(),
      smallModulesFor = cfg.smallModulesFor.asScala.toSeq,
      esVersion = cfg.esVersion.name(),
      sourceMap = cfg.sourceMap,
      optimizer = cfg.optimizer,
      minify = cfg.minify,
      productionMode = cfg.productionMode,
      jsHeader = cfg.jsHeader,
      prettyPrint = cfg.prettyPrint,
      jsOutputPattern = cfg.jsOutputPattern,
      avoidClasses = cfg.avoidClasses,
      avoidLetsAndConsts = cfg.avoidLetsAndConsts,
      checkIR = cfg.checkIR,
      relativizeSourceMapBase = Option(cfg.relativizeSourceMapBase),
      batchMode = cfg.batchMode,
      maxConcurrentWrites = cfg.maxConcurrentWrites,
      allowBigIntsForLongs = cfg.allowBigIntsForLongs,
      experimentalUseWebAssembly = cfg.experimentalUseWebAssembly
    )
}
