package ba.sake.deder.hello

import ba.sake.deder.*
import org.typelevel.jawn.ast.{JParser, JString}

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, configText: String): Seq[AbstractTask[?]] = {
    val json = JParser.parseFromString(configText).toOption.getOrElse(
      throw new RuntimeException(s"Failed to parse plugin config JSON: $configText")
    )
    val greeting = Option(json.get("greeting")).collect { case js: JString => js.s }.getOrElse("Hello!")

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting, ctx.module.id))
        greeting
      }
    Seq(helloTask)
  }
}
