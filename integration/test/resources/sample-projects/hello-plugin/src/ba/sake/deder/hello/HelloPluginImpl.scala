package ba.sake.deder.hello

import ba.sake.deder.*
import ba.sake.deder.config.DederProject.Plugin

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, config: Plugin): Seq[AbstractTask[?]] = {
    val helloConfig = config.asInstanceOf[HelloConfig]
    val greeting = Option(helloConfig.getGreeting()).getOrElse("Hello!")

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting, ctx.module.id))
        greeting
      }
    Seq(helloTask)
  }
}
