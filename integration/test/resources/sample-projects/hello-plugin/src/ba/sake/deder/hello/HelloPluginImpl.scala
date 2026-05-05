package ba.sake.deder.hello

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, jsonConfig: String): Seq[AbstractTask[?]] = {
    // Parse JSON via Pkl's pkl:json module, then convert to typed HelloConfig.
    // Use Pkl's custom-delim raw string: ##"..."## avoids needing to escape " and \
    val pklSource =
      s"""|import "pkl:json"
          |output {
          |  value = new json.Parser {}.parse(##\"\"\"
          |$jsonConfig
          |\"\"\"##)
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
