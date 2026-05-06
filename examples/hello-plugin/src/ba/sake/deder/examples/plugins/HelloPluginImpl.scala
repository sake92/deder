package ba.sake.deder.examples.plugins

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, pcfText: String): Seq[AbstractTask[?]] = {
    val moduleText =
      s"""|amends "file:///home/sake/projects/sake92/deder/.worktrees/add-plugins-support/examples/hello-plugin/HelloPlugin.pkl"
          |
          |config = new HelloPluginConfig {
          |  $pcfText
          |}
          |
          |""".stripMargin

    val config = Using.resource(ConfigEvaluator.preconfigured) { evaluator =>
      evaluator.evaluate(ModuleSource.text(moduleText)).as(classOf[Hello.HelloPluginConfig])
    }

    val greeting = config.greeting

    val helloTask = TaskBuilder.make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
