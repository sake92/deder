package ba.sake.deder.hello

import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, jsonConfig: String): Seq[AbstractTask[?]] = {
    val jsonNode = org.pkl.core.util.json.Json.parse(jsonConfig)
    val greeting = {
      val obj = jsonNode.asObject()
      if obj.containsKey("greeting") then obj.get("greeting").asString()
      else "Hello!"
    }

    val helloTask = TaskBuilder.make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
