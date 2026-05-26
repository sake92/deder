package ba.sake.deder.hello

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  private var pluginModule: HelloPluginModule = _

  override def start(params: PluginStartParams): Either[String, Unit] = {
    pluginModule =
      PluginConfigEvaluators.evaluate(
        getClass.getClassLoader,
        modulePath = "HelloPluginModule.pkl",
        configText = params.configText,
        clazz = classOf[HelloPluginModule]
      )
    Right(())
  }

  override def tasks(): Either[String, Seq[AbstractTask[?]]] = {
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
