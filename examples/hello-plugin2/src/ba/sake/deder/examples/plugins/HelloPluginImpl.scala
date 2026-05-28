package ba.sake.deder.examples.plugins

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello2"

  override def init(params: PluginInitParams): Either[String, Seq[AbstractTask[?]]] = {
    // test if classpath isolation works,
    // os.zip exists from os-lib 0.11.0
    println(os.zip)

    val pluginModule =
      PluginConfigEvaluators.evaluate(
        getClass.getClassLoader,
        modulePath = "HelloPlugin.pkl",
        configText = params.configText,
        clazz = classOf[Hello]
      )

    val greeting = pluginModule.config.greeting

    val helloTask = TaskBuilder
      .make[String](name = "hello2")
      .dependsOn(params.coreTasks.compileTask)
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Right(Seq(helloTask))
  }
}
