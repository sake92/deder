package ba.sake.deder.importing

import scala.jdk.CollectionConverters.*
import ba.sake.deder.PluginConfigEvaluators
import ba.sake.deder.config.DederTpolecat
import ba.sake.deder.config.DederTypelevel
import scala.util.Using
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.core.ModuleSource

/** Template default scalacOptions, transcribed from `DederTpolecat.pkl` and `DederTypelevel.pkl`.
  *
  * These are literal constants (not loaded from Pkl at runtime) so the renderer can diff against them without requiring
  * Pkl modulepath resolution on the classpath. When the template `.pkl` files are updated, these constants must be
  * synced manually.
  */
object TemplateOptionsReader {

  private val tpolecat = evalPklConfig(
    classOf[DederTpolecat],
    s"""amends "modulepath:/ba/sake/deder/config/DederTpolecat.pkl" """
  )

  private val typelevel = evalPklConfig(
    classOf[DederTypelevel],
    s"""amends "modulepath:/ba/sake/deder/config/DederTypelevel.pkl" """
  )

  /** Returns the default tpolecat scalacOptions for the given Scala version. */
  def tpolecatScalacOptions(scalaVersion: String): Set[String] =
    scalaVersion match {
      case v if v.startsWith("2.12") => tpolecat.tpolecatScala212.scalacOptions.asScala.toSet
      case v if v.startsWith("2.13") => tpolecat.tpolecatScala213.scalacOptions.asScala.toSet
      case _                         => tpolecat.tpolecatScala3.scalacOptions.asScala.toSet
    }

  /** Returns the default typelevel scalacOptions for the given Scala version. */
  def typelevelScalacOptions(scalaVersion: String): Set[String] =
    scalaVersion match {
      case v if v.startsWith("2.12") => typelevel.typelevelScala212.scalacOptions.asScala.toSet
      case v if v.startsWith("2.13") => typelevel.typelevelScala213.scalacOptions.asScala.toSet
      case _                         => typelevel.typelevelScala3.scalacOptions.asScala.toSet
    }

  private def evalPklConfig[T](clazz: Class[T], moduleText: String) = {
    Using.resource(ConfigEvaluatorBuilder.preconfigured().build()) { evaluator =>
      evaluator.evaluate(ModuleSource.text(moduleText)).as(clazz)
    }
  }
}
