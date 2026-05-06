package ba.sake.deder.hello

import scala.util.Using
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource
import ba.sake.deder.*

class HelloPluginImpl extends DederPlugin {
  def id: String = "hello"

  def tasks(coreTasks: CoreTasksApi, pcfText: String): Seq[AbstractTask[?]] = {
    // Read the Pkl class definition from plugin JAR resources
    val classDef = new String(
      getClass.getClassLoader.getResourceAsStream("HelloPlugin.pkl").readAllBytes()
    )

    // Indent PCF values so they are valid member definitions inside new HelloPlugin { ... }
    val indentedValues = pcfText.trim.linesIterator.map("      " + _).mkString("\n")

    val moduleText =
      s"""|$classDef
          |
          |output {
          |  value = new HelloPlugin {
          |$indentedValues
          |  }
          |}""".stripMargin

    val config = Using.resource(ConfigEvaluator.preconfigured) { evaluator =>
      evaluator.evaluate(ModuleSource.text(moduleText)).as(classOf[HelloPlugin])
    }

    val greeting = Option(config.getGreeting()).getOrElse("Hello!")

    val helloTask = TaskBuilder.make[String](name = "hello")
      .build { ctx =>
        ctx.notifications.add(ServerNotification.logInfo(greeting))
        greeting
      }

    Seq(helloTask)
  }
}
