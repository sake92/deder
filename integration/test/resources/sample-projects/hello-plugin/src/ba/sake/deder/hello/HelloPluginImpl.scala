package ba.sake.deder.hello

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, pklSource: String): Seq[AbstractTask[?]] = {
    val config = Using.resource(ConfigEvaluator.preconfigured) { evaluator =>
      val pklSourceTrimmed = pklSource.trim
      val moduleText = s"""$pklSourceTrimmed

output {
  value = this
}"""
      evaluator.evaluate(ModuleSource.text(moduleText)).as(classOf[HelloConfig])
    }

    val greeting = Option(config.getGreeting()).getOrElse("Hello!")

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting, ctx.module.id))
        greeting
      }
    Seq(helloTask)
  }
}
