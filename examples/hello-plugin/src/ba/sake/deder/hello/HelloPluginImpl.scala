package ba.sake.deder.hello

import ba.sake.deder.*
import ba.sake.deder.config.DederProject.Plugin

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, config: Plugin): Seq[AbstractTask[?]] = {
    // Downcast to typed config
    val helloConfig = config.asInstanceOf[HelloPlugin]
    val greeting = Option(helloConfig.getGreeting()).getOrElse("Hello!")

    val helloTask = TaskBuilder.make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}

// Dummy class to ensure .tasty files are generated (required for scaladoc during publish)
case class HelloPluginConfig(greeting: String = "Hello!")
