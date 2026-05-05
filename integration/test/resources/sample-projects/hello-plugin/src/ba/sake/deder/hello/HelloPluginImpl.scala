package ba.sake.deder.hello

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, jsonConfig: String): Seq[AbstractTask[?]] = {
    // Wrap JSON object as a Pkl expression value, then convert to typed HelloConfig
    // (Pkl evaluator can't parse a JSON object as a module top-level, but it can as an expression)
    val pklSource =
      s"""|output {
          |  value = $jsonConfig
          |}
          |""".stripMargin
    val config = Using.resource(ConfigEvaluator.preconfigured) { configEvaluator =>
      configEvaluator.evaluate(ModuleSource.text(pklSource)).as(classOf[HelloConfig])
    }
    val greeting = config.getGreeting()

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting, ctx.module.id))
        greeting
      }
    Seq(helloTask)
  }
}
