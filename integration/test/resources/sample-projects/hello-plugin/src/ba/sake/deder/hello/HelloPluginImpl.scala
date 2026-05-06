package ba.sake.deder.hello

import ba.sake.deder.*

class HelloPluginImpl extends DederPluginApi {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, configText: String): Seq[AbstractTask[?]] = {
    val pluginModule =
      PluginConfigEvaluators.evaluateModulePath(
        getClass.getClassLoader,
        modulePath = "HelloPlugin.pkl",
        configText = configText,
        clazz = classOf[Hello]
      )

    val greeting = pluginModule.config.greeting

    val helloTask = TaskBuilder
      .make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
