package ba.sake.deder.hello

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  override def init(params: PluginInitParams): Either[String, Seq[AbstractTask[?]]] = {
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

    val generatedResourceTask = TaskBuilder
      .make[DederPath](name = "generatedResource", kind = TaskKind.ResourceGenerator)
      .build { ctx =>
        val content = os.read(DederGlobals.projectRootDir / "generated-resource.txt")
        val output = ctx.out / "plugin-generated-resource.txt"
        os.write.over(output, content, createFolders = true)
        DederPath(ctx.out)
      }

    Right(Seq(helloTask, generatedResourceTask))
  }
}
