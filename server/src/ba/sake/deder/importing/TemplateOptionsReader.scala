package ba.sake.deder.importing

import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.module.ModuleKeyFactories
import ba.sake.deder.config.{DederTpolecat, DederTypelevel}

/** Reads template default option values at runtime by evaluating the bundled Pkl templates
  * via `ConfigEvaluator` with classpath module path resolution.
  *
  * Mirrors [[ba.sake.deder.PluginConfigEvaluators]] pattern: builds an evaluator that
  * resolves `modulepath:/` URIs against the config JAR's classpath, then evaluates a
  * "dummy module" (`amends "modulepath:/..."` with no overrides) to extract default values.
  *
  * Falls back to empty option sets when the template modules are not resolvable (e.g. in
  * test environments where the config JAR classpath resources aren't set up). In that case
  * the renderer falls through to the old blanket-suppression behaviour.
  */
object TemplateOptionsReader {

  private val configClassLoader: ClassLoader = classOf[DederTpolecat].getClassLoader

  private lazy val tpolecatInstance: Option[DederTpolecat] =
    try Some(evaluateTemplate("ba/sake/deder/config/DederTpolecat", classOf[DederTpolecat]))
    catch { case NonFatal(_) => None }

  private lazy val typelevelInstance: Option[DederTypelevel] =
    try Some(evaluateTemplate("ba/sake/deder/config/DederTypelevel", classOf[DederTypelevel]))
    catch { case NonFatal(_) => None }

  /** Returns the full set of tpolecat scalacOptions defaults for all Scala versions. */
  def tpolecatOptions: Option[DederTpolecat] = tpolecatInstance

  /** Returns the full set of typelevel scalacOptions defaults for all Scala versions. */
  def typelevelOptions: Option[DederTypelevel] = typelevelInstance

  /** Given a DederTpolecat instance, extract the option list for a specific Scala version. */
  def tpolecatScalacOptions(opts: Option[DederTpolecat], scalaVersion: String): Set[String] =
    opts match {
      case Some(o) =>
        val list: java.util.List[String] = scalaVersion match {
          case v if v.startsWith("3")    => o.tpolecatScalacOptions3
          case v if v.startsWith("2.13") => o.tpolecatScalacOptions2_13
          case v if v.startsWith("2.12") => o.tpolecatScalacOptions2_12
          case _                          => o.tpolecatScalacOptions2_13
        }
        list.asScala.toSet
      case None => Set.empty
    }

  /** Given a DederTypelevel instance, extract the option list for a specific Scala version. */
  def typelevelScalacOptions(opts: Option[DederTypelevel], scalaVersion: String): Set[String] =
    opts match {
      case Some(o) =>
        val list: java.util.List[String] = scalaVersion match {
          case v if v.startsWith("3")    => o.typelevelScalacOptions3
          case v if v.startsWith("2.13") => o.typelevelScalacOptions2_13
          case v if v.startsWith("2.12") => o.typelevelScalacOptions2_12
          case _                          => o.typelevelScalacOptions2_13
        }
        list.asScala.toSet
      case None => Set.empty
    }

  private def evaluateTemplate[T](modulePath: String, clazz: Class[T]): T = {
    val evaluatorBuilder = ConfigEvaluatorBuilder.preconfigured()
    val underlyingBuilder = evaluatorBuilder.getEvaluatorBuilder()

    // Register classpath module resolution so `modulepath:/` URIs resolve against the config JAR
    val moduleKeyFactories = (ModuleKeyFactories
      .classPath(configClassLoader) +: underlyingBuilder.getModuleKeyFactories().asScala.toSeq).distinct
    underlyingBuilder.setModuleKeyFactories(moduleKeyFactories.asJava)

    val moduleText = s"""amends "modulepath:/$modulePath""""

    Using.resource(evaluatorBuilder.build()) { evaluator =>
      evaluator.evaluate(ModuleSource.text(moduleText)).as(clazz)
    }
  }
}
