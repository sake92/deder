package ba.sake.deder.hello

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  override def tasks(params: PluginTasksParams): Either[String, Seq[AbstractTask[?]]] = {
    val pluginModule =
      PluginConfigEvaluators.evaluate(
        getClass.getClassLoader,
        modulePath = "HelloPluginModule.pkl",
        configText = params.configText,
        clazz = classOf[HelloPluginModule]
      )

    val greeting = pluginModule.config.greeting

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Right(Seq(helloTask))
  }
}
