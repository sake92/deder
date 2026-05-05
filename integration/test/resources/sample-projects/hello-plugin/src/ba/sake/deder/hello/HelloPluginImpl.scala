package ba.sake.deder.hello

import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, configText: String): Seq[AbstractTask[?]] = {
    // Parse the Pkl config expression to get a typed HelloConfig object
    val evaluator = org.pkl.config.java.ConfigEvaluator.preconfigured
    val config = evaluator.evaluate(
      org.pkl.core.ModuleSource.text(configText)
    ).as(classOf[HelloConfig])

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
