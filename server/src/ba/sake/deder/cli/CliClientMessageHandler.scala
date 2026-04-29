package ba.sake.deder.cli

import java.io.IOException
import java.nio.channels.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.toJson
import ba.sake.deder.*
import ba.sake.deder.importing.Importer

class CliClientMessageHandler(projectState: DederProjectState, serverMessages: BlockingQueue[CliServerMessage])
    extends StrictLogging {

  def handle(
      clientId: Int,
      requestId: String,
      message: CliClientMessage
  ): Unit = {
    message match {
      case m: CliClientMessage.Help =>
        val defaultHelpText =
          """Deder Build Tool Help:
            |
            |Available commands:
            |  version                 Show client and server versions
            |  modules [options]       List modules
            |  tasks [options]         List tasks
            |  plan [options]          Show execution plan for a task
            |  exec [options]          Execute a task
            |  clean [options]         Clean modules
            |  bsp install             Generate BSP configuration for this project
            |  bsp                     Start BSP server for this project
            |  import [options]        Import from other build tool
            |  complete [options]      Generate shell completion script
            |  shutdown                Shutdown the server
            |
            |Use help -c <command> for more details about each command.
            |""".stripMargin

        mainargs.Parser[DederCliHelpOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
          case Left(_) =>
            serverMessages.put(CliServerMessage.Output(defaultHelpText))
          case Right(cliOptions) =>
            cliOptions.command match {
              case "version" =>
                serverMessages.put(CliServerMessage.Output("Shows the Deder version."))
              case "modules" =>
                serverMessages.put(
                  CliServerMessage.Output(mainargs.Parser[DederCliModulesOptions].helpText())
                )
              case "tasks" =>
                serverMessages.put(
                  CliServerMessage.Output(mainargs.Parser[DederCliTasksOptions].helpText())
                )
              case "plan" =>
                serverMessages.put(
                  CliServerMessage.Output(mainargs.Parser[DederCliPlanOptions].helpText())
                )
              case "exec" =>
                serverMessages.put(
                  CliServerMessage.Output(mainargs.Parser[DederCliExecOptions].helpText())
                )
              case "clean" =>
                serverMessages.put(
                  CliServerMessage.Output(mainargs.Parser[DederCliCleanOptions].helpText())
                )
              case "import" =>
                serverMessages.put(
                  CliServerMessage.Output(mainargs.Parser[DederCliImportOptions].helpText())
                )
              case "complete" =>
                serverMessages.put(
                  CliServerMessage.Output(mainargs.Parser[DederCliCompleteOptions].helpText())
                )
              case "shutdown" =>
                serverMessages.put(CliServerMessage.Output("Shuts down the Deder server."))
              case _ =>
                serverMessages.put(CliServerMessage.Output(defaultHelpText))
            }
        }
        serverMessages.put(CliServerMessage.Exit(0))
      case m: CliClientMessage.Version =>
        serverMessages.put(CliServerMessage.Output(s"Server version: ${DederGlobals.version}"))
        serverMessages.put(CliServerMessage.Exit(0))
      case m: CliClientMessage.Modules =>
        if m.args == Seq("--help") || m.args == Seq("-h") then
          serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliModulesOptions].helpText()))
          serverMessages.put(CliServerMessage.Exit(0))
        else
          mainargs.Parser[DederCliModulesOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
            case Left(error) =>
              serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
            case Right(cliOptions) =>
              projectState.readState(useLastGood = false) match {
                case Left(error) =>
                  serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                  serverMessages.put(CliServerMessage.Exit(1))
                case Right(state) =>
                  if cliOptions.json.value then {
                    val allModules = state.tasksResolver.allModules.sortBy(_.id)
                    serverMessages.put(CliServerMessage.Output(allModules.map(_.id).toJson))
                  } else if cliOptions.dot.value then {
                    val dot =
                      GraphUtils.generateDOT(state.tasksResolver.modulesGraph, v => v.id, v => Map("label" -> v.id))
                    serverMessages.put(CliServerMessage.Output(dot))
                  } else {
                    val allModules = state.tasksResolver.allModules.sortBy(_.id)
                    serverMessages.put(CliServerMessage.Output(allModules.map(_.id).mkString("\n")))
                  }
                  serverMessages.put(CliServerMessage.Exit(0))
              }
          }
      case m: CliClientMessage.Tasks =>
        if m.args == Seq("--help") || m.args == Seq("-h") then
          serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliTasksOptions].helpText()))
          serverMessages.put(CliServerMessage.Exit(0))
        else
          mainargs.Parser[DederCliTasksOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
            case Left(error) =>
              serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
            case Right(cliOptions) =>
              projectState.readState(useLastGood = true) match {
                case Left(error) =>
                  serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                  serverMessages.put(CliServerMessage.Exit(1))
                case Right(state) =>
                  if cliOptions.json.value then {
                    val taskNamesPerModule = state.tasksResolver.taskInstancesPerModule.map { case (moduleId, tasks) =>
                      moduleId -> tasks.map(_.task.name)
                    }
                    serverMessages.put(CliServerMessage.Output(taskNamesPerModule.toJson))
                    serverMessages.put(CliServerMessage.Exit(0))
                  } else if cliOptions.dot.value then {
                    val dot =
                      GraphUtils.generateDOT(
                        state.tasksResolver.taskInstancesGraph,
                        v => v.id,
                        v => Map("label" -> v.id)
                      )
                    serverMessages.put(CliServerMessage.Output(dot))
                    serverMessages.put(CliServerMessage.Exit(0))
                  } else {
                    val modules = cliOptions.module match {
                      case Some(moduleId) =>
                        state.tasksResolver.allModules.filter(_.id == moduleId)
                      case None =>
                        state.tasksResolver.allModules
                    }
                    val sortedModules = modules.sortBy(_.id)
                    val modulesWithTasks = sortedModules.map { module =>
                      val moduleTaskNames =
                        state.tasksResolver.taskInstancesPerModule(module.id).map(t => s"  ${t.task.name}")
                      s"${module.id}:\n${moduleTaskNames.mkString("\n")}"
                    }
                    serverMessages.put(CliServerMessage.Output(modulesWithTasks.mkString("\n")))
                    serverMessages.put(CliServerMessage.Exit(0))
                  }
              }
          }

      case m: CliClientMessage.Plan =>
        if m.args == Seq("--help") || m.args == Seq("-h") then
          serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliPlanOptions].helpText()))
          serverMessages.put(CliServerMessage.Exit(0))
        else
          mainargs.Parser[DederCliPlanOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
            case Left(error) =>
              serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
            case Right(cliOptions) =>
              projectState.readState(useLastGood = true) match {
                case Left(error) =>
                  serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                  serverMessages.put(CliServerMessage.Exit(1))
                case Right(state) =>
                  val selectedModuleIds =
                    if cliOptions.modules.isEmpty then state.tasksResolver.allModules.map(_.id)
                    else cliOptions.modules
                  state.executionPlanner.getTaskInstances(selectedModuleIds, cliOptions.task) match {
                    case Left(recommendations) =>
                      val msg =
                        if recommendations.isEmpty then s"No '${cliOptions.task}' tasks found"
                        else s"No '${cliOptions.task}' tasks found, did you mean: ${recommendations.mkString(", ")} ?"
                      serverMessages.put(CliServerMessage.Log(msg, LogLevel.ERROR))
                      serverMessages.put(CliServerMessage.Exit(1))
                    case Right(validModuleTasks) =>
                      val validModuleIds = validModuleTasks.map(_._1)
                      val tasksExecSubgraph = state.executionPlanner.getExecSubgraph(validModuleIds, cliOptions.task)
                      if cliOptions.json.value then {
                        val tasksExecStages = state.executionPlanner.getExecStages(validModuleIds, cliOptions.task)
                        serverMessages.put(CliServerMessage.Output(tasksExecStages.map(_.map(_.id)).toJson))
                      } else if cliOptions.dot.value then {
                        val dot = GraphUtils.generateDOT(tasksExecSubgraph, v => v.id, v => Map("label" -> v.id))
                        serverMessages.put(CliServerMessage.Output(dot))
                      } else {
                        val tasksExecStages = state.executionPlanner.getExecStages(validModuleIds, cliOptions.task)
                        val stagesStr = tasksExecStages.zipWithIndex
                          .map { case (stage, idx) =>
                            s"Stage #${idx}:\n" + stage.map(ti => s"  ${ti.id}").mkString("\n")
                          }
                          .mkString("\n")
                        serverMessages.put(CliServerMessage.Output(stagesStr))
                      }
                      serverMessages.put(CliServerMessage.Exit(0))
                  }
              }
          }
      case m: CliClientMessage.Exec =>
        if m.args == Seq("--help") || m.args == Seq("-h") then
          serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliExecOptions].helpText()))
          serverMessages.put(CliServerMessage.Exit(0))
        else
          mainargs.Parser[DederCliExecOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
            case Left(error) =>
              serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
            case Right(cliOptions) =>
              val notificationCallback: ServerNotification => Unit = {
                case logMsg: ServerNotification.Log if logMsg.level.ordinal > cliOptions.logLevel.ordinal =>
                // skip
                case sn =>
                  CliServerMessage.fromServerNotification(sn).foreach(serverMessages.put)
              }
              val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
              val argss =
                if cliOptions.args.value.headOption == Some("--") then cliOptions.args.value.tail
                else cliOptions.args.value
              projectState.executeCLI(
                clientId,
                requestId,
                cliOptions.modules,
                cliOptions.task,
                args = argss,
                serverNotificationsLogger,
                json = cliOptions.json.value,
                startWatch = cliOptions.watch.value,
                exitOnEnd = !cliOptions.watch.value,
                clientParams = CliClientParams(m.envVars)
              )
          }
      case m: CliClientMessage.Cancel =>
        projectState.cancelRequest(m.requestId)
        serverMessages.put(CliServerMessage.Exit(130))
      case m: CliClientMessage.Clean =>
        if m.args == Seq("--help") || m.args == Seq("-h") then
          serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliCleanOptions].helpText()))
          serverMessages.put(CliServerMessage.Exit(0))
        else
          mainargs.Parser[DederCliCleanOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
            case Left(error) =>
              serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
            case Right(cliOptions) =>
              val success = cliOptions.task match {
                case Some(taskName) =>
                  projectState.cleanTasks(cliOptions.modules, taskName)
                case None =>
                  projectState.cleanModules(cliOptions.modules)
              }
              serverMessages.put(CliServerMessage.Exit(if success then 0 else 1))
          }
      case m: CliClientMessage.Import =>
        if m.args == Seq("--help") || m.args == Seq("-h") then
          serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliImportOptions].helpText()))
          serverMessages.put(CliServerMessage.Exit(0))
        else
          mainargs.Parser[DederCliImportOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
            case Left(error) =>
              serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
            case Right(cliOptions) =>
              val notificationCallback: ServerNotification => Unit = { sn =>
                CliServerMessage.fromServerNotification(sn).foreach(serverMessages.put)
              }
              val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
              val importer = new Importer(serverNotificationsLogger)
              val success = importer.doImport(cliOptions.from)
              val exitCode = if success then 0 else 1
              serverMessages.put(CliServerMessage.Exit(exitCode))
          }
      case m: CliClientMessage.Complete =>
        if m.args == Seq("--help") || m.args == Seq("-h") then
          serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliCompleteOptions].helpText()))
          serverMessages.put(CliServerMessage.Exit(0))
        else
          mainargs.Parser[DederCliCompleteOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
            case Left(error) =>
              serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
            case Right(cliOptions) =>
              val res = if cliOptions.output.value then {
                cliOptions.shell match {
                  case ShellType.bash       => TabCompleter.bashScript
                  case ShellType.zsh        => TabCompleter.zshScript
                  case ShellType.fish       => TabCompleter.fishScript
                  case ShellType.powershell => TabCompleter.powershellScript
                }
              } else {
                val tabCompletions =
                  projectState.getTabCompletions(
                    cliOptions.commandLine.getOrElse(""),
                    cliOptions.cursorPos.getOrElse(-1)
                  )
                tabCompletions.mkString(" ")
              }
              serverMessages.put(CliServerMessage.Output(res))
              serverMessages.put(CliServerMessage.Exit(0))
          }
      case _: CliClientMessage.Shutdown =>
        logger.info(s"Client $clientId requested server shutdown.")
        serverMessages.put(CliServerMessage.Log("Deder server is shutting down...", LogLevel.INFO))
        serverMessages.put(CliServerMessage.Exit(0))
        Thread.sleep(100) // let the message be sent
        projectState.shutdown()
    }
  }
}
