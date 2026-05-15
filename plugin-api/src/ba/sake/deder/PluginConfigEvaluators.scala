package ba.sake.deder

import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.pkl.config.java.Config
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.resource.ResourceReaders

object PluginConfigEvaluators {

  private[deder] def evaluateModulePathConfig(
      pluginClassLoader: ClassLoader,
      modulePath: String,
      configText: String
  ): Config = {
    val evaluatorBuilder = ConfigEvaluatorBuilder.preconfigured()
    val underlyingBuilder = evaluatorBuilder.getEvaluatorBuilder()
    val moduleKeyFactories =
      (ModuleKeyFactories
        .classPath(pluginClassLoader) +: underlyingBuilder.getModuleKeyFactories().asScala.toSeq).distinct
    underlyingBuilder.setModuleKeyFactories(moduleKeyFactories.asJava)
    val resourceReaders =
      (ResourceReaders.classPath(pluginClassLoader) +: underlyingBuilder.getResourceReaders().asScala.toSeq).distinct
    underlyingBuilder.setResourceReaders(resourceReaders.asJava)

    val moduleText =
      s"""|amends "modulepath:/$modulePath"
          |
          |$configText
          |""".stripMargin

    Using.resource(evaluatorBuilder.build()) { evaluator =>
      evaluator.evaluate(ModuleSource.text(moduleText))
    }
  }

  def evaluate[T](
      pluginClassLoader: ClassLoader,
      modulePath: String,
      configText: String,
      clazz: Class[T]
  ): T = {
    evaluateModulePathConfig(pluginClassLoader, modulePath, configText).as(clazz)
  }
}
