package ba.sake.deder.hello

import ba.sake.deder.*
import ba.sake.tupson.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, jsonConfig: String): Seq[AbstractTask[?]] = {
    val json = jsonConfig.parseJson
    val greeting = json("greeting").asStr

    val helloTask = TaskBuilder.make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
