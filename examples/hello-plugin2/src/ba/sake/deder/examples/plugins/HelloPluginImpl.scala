package ba.sake.deder.examples.plugins

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello2"

  private var pluginModule: Hello = _
  private var core: CoreTasksApi = _

  override def start(params: PluginStartParams): Either[String, Unit] = {
    // test if classpath isolation works,
    // os.zip exists from os-lib 0.11.0
    println(os.zip)

    pluginModule =
      PluginConfigEvaluators.evaluate(
        getClass.getClassLoader,
        modulePath = "HelloPlugin.pkl",
        configText = params.configText,
        clazz = classOf[Hello]
      )
    core = params.coreTasks
    Right(())
  }

  override def tasks(): Either[String, Seq[AbstractTask[?]]] = {
    val greeting = pluginModule.config.greeting

    val helloTask = TaskBuilder
      .make[String](name = "hello2")
      .dependsOn(core.compileTask)
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Right(Seq(helloTask))
  }
}
