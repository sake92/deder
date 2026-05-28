package ba.sake.deder.examples.plugins

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  override def init(params: PluginInitParams): Either[String, Seq[AbstractTask[?]]] = {
    // test if classpath isolation works,
    // os.BasicStatInfo exists in os-lib 0.3.0, removed in later versions
    println(os.BasicStatInfo)

    val pluginModule =
      PluginConfigEvaluators.evaluate(
        getClass.getClassLoader,
        modulePath = "HelloPlugin.pkl",
        configText = params.configText,
        clazz = classOf[Hello]
      )

    val greeting = pluginModule.config.greeting

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .dependsOn(params.coreTasks.compileTask)
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Right(Seq(helloTask))
  }
}
