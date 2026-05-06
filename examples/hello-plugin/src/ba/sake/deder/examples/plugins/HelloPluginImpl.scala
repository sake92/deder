package ba.sake.deder.examples.plugins

import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.pkl.config.java.ConfigEvaluatorBuilder
import org.pkl.core.ModuleSource
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.resource.ResourceReaders
import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, pcfText: String): Seq[AbstractTask[?]] = {
    val moduleText =
      s"""|amends "modulepath:/HelloPlugin.pkl"
           |
           |$pcfText
           |
           |""".stripMargin

    val evaluatorBuilder = ConfigEvaluatorBuilder.preconfigured()
    val underlyingBuilder = evaluatorBuilder.getEvaluatorBuilder()
    val moduleKeyFactories =
      (ModuleKeyFactories.classPath(getClass.getClassLoader) +: underlyingBuilder.getModuleKeyFactories().asScala.toSeq).distinct
    underlyingBuilder.setModuleKeyFactories(moduleKeyFactories.asJava)
    val resourceReaders =
      (ResourceReaders.classPath(getClass.getClassLoader) +: underlyingBuilder.getResourceReaders().asScala.toSeq).distinct
    underlyingBuilder.setResourceReaders(resourceReaders.asJava)

    val pluginModule = Using.resource(evaluatorBuilder.build()) { evaluator =>
      evaluator.evaluate(ModuleSource.text(moduleText)).as(classOf[Hello])
    }

    val greeting = pluginModule.config.greeting

    val helloTask = TaskBuilder.make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
