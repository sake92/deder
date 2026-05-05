package ba.sake.deder.hello

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, configText: String): Seq[AbstractTask[?]] = {
    val config = Using.resource(ConfigEvaluator.preconfigured) { evaluator =>
      val configTrimmed = configText.trim
      val moduleText = s"""$configTrimmed

output {
  value = this
}"""
      evaluator.evaluate(ModuleSource.text(moduleText)).as(classOf[HelloPlugin])
    }

    val greeting = Option(config.getGreeting()).getOrElse("Hello!")

    val helloTask = TaskBuilder.make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
