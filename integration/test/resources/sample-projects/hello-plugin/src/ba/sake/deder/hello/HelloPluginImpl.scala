package ba.sake.deder.hello

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, configText: String): Seq[AbstractTask[?]] = {
    val pluginRootUri =
      java.nio.file.Paths.get(sys.props("user.dir")).resolveSibling("hello-plugin/HelloPlugin.pkl").normalize().toUri.toString
    val moduleText =
      s"""|amends "$pluginRootUri"
           |
           |$configText
           |""".stripMargin

    val pluginModule = Using.resource(ConfigEvaluator.preconfigured) { evaluator =>
      evaluator.evaluate(ModuleSource.text(moduleText)).as(classOf[Hello])
    }

    val greeting = pluginModule.config.greeting

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
