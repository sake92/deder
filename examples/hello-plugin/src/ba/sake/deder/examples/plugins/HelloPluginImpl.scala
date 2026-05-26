package ba.sake.deder.examples.plugins

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  private var pluginModule: Hello = _
  private var core: CoreTasksApi = _

  override def start(params: PluginStartParams): Either[String, Unit] = {
    // test if classpath isolation works,
    // os.BasicStatInfo exists in os-lib 0.3.0, removed in later versions
    println(os.BasicStatInfo)

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
      .make[String](name = "hello")
      .dependsOn(core.compileTask)
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Right(Seq(helloTask))
  }
}
