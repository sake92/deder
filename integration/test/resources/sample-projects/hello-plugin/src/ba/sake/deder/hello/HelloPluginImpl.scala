package ba.sake.deder.hello

import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, jsonConfig: String): Seq[AbstractTask[?]] = {
    val jsonObj = org.pkl.core.util.json.Json.parseObject(jsonConfig)
    val greeting = if jsonObj.containsKey("greeting") then jsonObj.getString("greeting") else "Hello!"

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting, ctx.module.id))
        greeting
      }
    Seq(helloTask)
  }
}
