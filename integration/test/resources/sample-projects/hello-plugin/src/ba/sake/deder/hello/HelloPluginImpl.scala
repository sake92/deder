package ba.sake.deder.hello

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{ModuleSource, PklException}
import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, pklConfigSource: String): Seq[AbstractTask[?]] = {
    println(s"HelloPluginImpl: Parsing config: $pklConfigSource")
    val parsedConfig1 = Using.resource(ConfigEvaluator.preconfigured) { configEvaluator =>
      configEvaluator.evaluate(ModuleSource.text(pklConfigSource)).as(classOf[HelloConfig])
    }
    println(s"HelloPluginImpl: Got config: $parsedConfig1")
    val greeting = parsedConfig1.getGreeting()

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting, ctx.module.id))
        greeting
        "result..."
      }
    Seq(helloTask)
  }
}
