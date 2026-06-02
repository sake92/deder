package ba.sake.deder.cli

import java.io.IOException
import java.nio.channels.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import scala.jdk.CollectionConverters.*
import scala.util.boundary
import scala.util.chaining.*
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.JsonRW
import org.typelevel.jawn.ast.JValue
import ba.sake.deder.*
import ba.sake.deder.importing.Importer
import io.opentelemetry.api.trace.StatusCode
import ba.sake.deder.OTEL
import ba.sake.deder.config.DederProject.DederModule
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

private case class TaskInfo(name: String, features: Seq[String]) derives JsonRW

class CliClientMessageHandler(
    projectState: DederProjectState,
    serverMessages: BlockingQueue[CliServerMessage],
    cliServer: DederCliServer
) extends StrictLogging {

  def handle(message: CliClientMessage): Unit = {
    message match {
      case m: CliClientMessage.Help     => handleHelp(m)
      case m: CliClientMessage.Version  => handleVersion()
      case m: CliClientMessage.Modules  => handleModules(m)
      case m: CliClientMessage.Tasks    => handleTasks(m)
      case m: CliClientMessage.Plan     => handlePlan(m)
      case m: CliClientMessage.Exec     => handleExec(m)
      case m: CliClientMessage.Cancel   => handleCancel(m)
      case m: CliClientMessage.Clean    => handleClean(m)
      case m: CliClientMessage.Import   => handleImport(m)
      case m: CliClientMessage.Complete => handleComplete(m)
      case m: CliClientMessage.Plugins => handlePlugins(m)
      case m: CliClientMessage.Shutdown => handleShutdown(m)
    }
  }

  private def handleHelp(m: CliClientMessage.Help): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    OTEL.withSpan("cli.help")(
      _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
    ) { span =>
      val defaultHelpText =
        """Deder Build Tool Help:
          |
          |Available commands:
          |  version                 Show client and server versions
          |  modules [options]       List modules
          |  tasks [options]         List tasks
          |  plugins [options]       List loaded plugins
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
          span.setAttribute("cli.command", cliOptions.command)
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
            case "plugins" =>
              serverMessages.put(
                CliServerMessage.Output(mainargs.Parser[DederCliPluginsOptions].helpText())
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
    }
  }

  private def handleVersion(): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    OTEL.withSpan("cli.version")(
      _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
    ) { _ =>
      serverMessages.put(CliServerMessage.Output(s"Server version: ${DederGlobals.version}"))
      serverMessages.put(CliServerMessage.Exit(0))
    }
  }

  private def handleModules(m: CliClientMessage.Modules): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliModulesOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliModulesOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.modules")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          OTEL.withSpan("cli.modules")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
              .setAttribute("cli.moduleFilters", cliOptions.modules.mkString(","))
              .setAttribute("cli.depthDown", cliOptions.depthDown)
              .setAttribute("cli.depthUp", cliOptions.depthUp)
          ) { span =>
            projectState.readState(useLastGood = false) match {
              case Left(error) =>
                span.setStatus(StatusCode.ERROR)
                span.setAttribute("error", error)
                serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                serverMessages.put(CliServerMessage.Exit(1))
              case Right(state) =>
                if cliOptions.depthDown < 0 || cliOptions.depthUp < 0 then {
                  val errMsg = "--depth-down and --depth-up must be non-negative"
                  span.setStatus(StatusCode.ERROR)
                  span.setAttribute("error", errMsg)
                  serverMessages.put(CliServerMessage.Log(errMsg, LogLevel.ERROR))
                  serverMessages.put(CliServerMessage.Exit(1))
                } else {
                  val fullGraph = state.tasksResolver.modulesGraph
                  val graphToRender =
                    if cliOptions.modules.isEmpty && cliOptions.depthDown == Int.MaxValue && cliOptions.depthUp == Int.MaxValue
                    then Right(fullGraph)
                    else {
                      val focalResult =
                        if cliOptions.modules.isEmpty then Right(state.tasksResolver.allModules.toSeq)
                        else
                          WildcardUtils.getMatchesOrRecommendations(
                            state.tasksResolver.allModules.map(_.id),
                            cliOptions.modules
                          ) match {
                            case Left(recommendations) =>
                              val msg =
                                if recommendations.isEmpty then
                                  s"No modules found for selectors: ${cliOptions.modules.mkString(", ")}"
                                else s"No modules found, did you mean: ${recommendations.mkString(", ")} ?"
                              Left(msg)
                            case Right(ids) =>
                              Right(ids.flatMap(id => state.tasksResolver.modulesMap.get(id)))
                          }
                      focalResult.map { focalModules =>
                        GraphUtils.subgraphAround(
                          fullGraph,
                          focalModules.toSet,
                          cliOptions.depthDown,
                          cliOptions.depthUp
                        )
                      }
                    }
                  graphToRender match {
                    case Left(errorMsg) =>
                      span.setStatus(StatusCode.ERROR)
                      span.setAttribute("error", errorMsg)
                      serverMessages.put(CliServerMessage.Log(errorMsg, LogLevel.ERROR))
                      serverMessages.put(CliServerMessage.Exit(1))
                    case Right(graph) =>
                      val filteredModules = graph.vertexSet().asScala.toSeq.sortBy(_.id)
                      val modulesOutput = ModulesOutput(filteredModules.map(_.id), graph)
                      val output = OutputFormat.render(modulesOutput, cliOptions.format)
                      serverMessages.put(CliServerMessage.Output(output))
                      serverMessages.put(CliServerMessage.Exit(0))
                  }
                }
            }
          }
      }
  }

  private def handleTasks(m: CliClientMessage.Tasks): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliTasksOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliTasksOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.tasks")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          OTEL.withSpan("cli.tasks")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
              .pipe(b => cliOptions.module.fold(b)(m => b.setAttribute("cli.module", m)))
          ) { span =>
            projectState.readState(useLastGood = true) match {
              case Left(error) =>
                span.setStatus(StatusCode.ERROR)
                span.setAttribute("error", error)
                serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                serverMessages.put(CliServerMessage.Exit(1))
              case Right(state) =>
                val taskInfosPerModule = state.tasksResolver.publicTaskInstancesPerModule.map {
                  case (moduleId, tasks) =>
                    moduleId -> tasks.map { ti =>
                      TaskInfo(ti.task.name, ti.task.featureTags.map(_.jsonKey).toSeq)
                    }
                }
                val tasksOutput = TasksOutput(
                  taskInfosPerModule,
                  state.tasksResolver.publicTaskInstancesGraph,
                  state.tasksResolver.publicTaskInstancesPerModule,
                  cliOptions.module
                )
                val output = OutputFormat.render(tasksOutput, cliOptions.format)
                serverMessages.put(CliServerMessage.Output(output))
                serverMessages.put(CliServerMessage.Exit(0))
            }
          }
      }
  }

  private def handlePlugins(m: CliClientMessage.Plugins): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliPluginsOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliPluginsOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.plugins")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          OTEL.withSpan("cli.plugins")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
          ) { _ =>
            val plugins = projectState.internals.loadedPlugins
            cliOptions.format match
              case OutputFormat.Dot | OutputFormat.Mermaid =>
                serverMessages.put(CliServerMessage.Log("Format not supported for plugins (try plain, json, or densejson)", LogLevel.ERROR))
              case _ =>
                val output = OutputFormat.render(PluginsOutput(plugins), cliOptions.format)
                serverMessages.put(CliServerMessage.Output(output))
            serverMessages.put(CliServerMessage.Exit(0))
          }
      }
  }

  private def handlePlan(m: CliClientMessage.Plan): Unit = boundary {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliPlanOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliPlanOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.plan")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          OTEL.withSpan("cli.plan")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
              .setAttribute("cli.task", cliOptions.task)
              .setAttribute("cli.moduleFilters", cliOptions.modules.mkString(","))
          ) { span =>
            projectState.readState(useLastGood = true) match {
              case Left(error) =>
                span.setStatus(StatusCode.ERROR)
                span.setAttribute("error", error)
                serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                serverMessages.put(CliServerMessage.Exit(1))
              case Right(state) =>
                val selectedModuleIds =
                  if cliOptions.modules.isEmpty then state.tasksResolver.allModules.map(_.id)
                  else
                    WildcardUtils.getMatchesOrRecommendations(
                      state.tasksResolver.allModules.map(_.id),
                      cliOptions.modules
                    ) match {
                      case Left(recommendations) =>
                        val msg =
                          if recommendations.isEmpty then
                            s"No modules found for selectors: ${cliOptions.modules.mkString(", ")}"
                          else s"No modules found, did you mean: ${recommendations.mkString(", ")} ?"
                        span.setStatus(StatusCode.ERROR)
                        span.setAttribute("error", msg)
                        serverMessages.put(CliServerMessage.Log(msg, LogLevel.ERROR))
                        serverMessages.put(CliServerMessage.Exit(1))
                        boundary.break()
                      case Right(ids) => ids
                    }
                state.executionPlanner.getTaskInstances(selectedModuleIds, cliOptions.task) match {
                  case Left(recommendations) =>
                    val msg =
                      if recommendations.isEmpty then s"No '${cliOptions.task}' tasks found"
                      else s"No '${cliOptions.task}' tasks found, did you mean: ${recommendations.mkString(", ")} ?"
                    span.setStatus(StatusCode.ERROR)
                    span.setAttribute("error", msg)
                    serverMessages.put(CliServerMessage.Log(msg, LogLevel.ERROR))
                    serverMessages.put(CliServerMessage.Exit(1))
                  case Right(validModuleTasks) =>
                    val validModuleIds = validModuleTasks.map(_._1)
                    val tasksExecStages = state.executionPlanner.getExecStages(validModuleIds, cliOptions.task)
                    val tasksExecSubgraph = state.executionPlanner.getExecSubgraph(validModuleIds, cliOptions.task)
                    val publicSubgraph = GraphUtils.projectPublic(tasksExecSubgraph, !_.task.internal)
                    val stageByTask = tasksExecStages.zipWithIndex.flatMap { case (stage, stageIdx) =>
                      stage.map(_ -> stageIdx)
                    }.toMap
                    val publicStagesWithIdx = tasksExecStages.zipWithIndex.flatMap { case (stage, idx) =>
                      val publicStage = stage.filter(!_.task.internal)
                      Option.when(publicStage.nonEmpty)(idx -> publicStage.map(_.id))
                    }
                    val groups = publicSubgraph.vertexSet().asScala.toSeq.groupBy(_.moduleId)
                    val planOutput = PlanOutput(publicStagesWithIdx, publicSubgraph, groups, stageByTask)
                    val output = OutputFormat.render(planOutput, cliOptions.format)
                    serverMessages.put(CliServerMessage.Output(output))
                    serverMessages.put(CliServerMessage.Exit(0))
                }
            }
          }
      }
  }

  private def handleExec(m: CliClientMessage.Exec): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliExecOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliExecOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.exec")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          
          OTEL.withSpan(s"cli.exec.${cliOptions.task}")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
              .setAttribute("cli.task", cliOptions.task)
              .setAttribute("cli.moduleIds", cliOptions.modules.mkString(","))
              .setAttribute("cli.watch", cliOptions.watch.value)
              .setAttribute("cli.format", ctx.outputFormat.toString)
          ) { _ =>
            val heartbeat = new CliExecHeartbeat(emit = serverMessages.put)
            try
              val notificationCallback: ServerNotification => Unit = {
                case logMsg: ServerNotification.Log if logMsg.level.ordinal > cliOptions.logLevel.ordinal =>
                // skip
                case sn =>
                  heartbeat.recordServerNotification(sn)
                  CliServerMessage.fromServerNotification(sn).foreach(serverMessages.put)
              }
              val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
              val argss =
                if cliOptions.args.value.headOption == Some("--") then cliOptions.args.value.tail
                else cliOptions.args.value
              projectState.executeCLI(
                cliOptions.modules,
                cliOptions.task,
                args = argss,
                serverNotificationsLogger,
                startWatch = cliOptions.watch.value,
                exitOnEnd = !cliOptions.watch.value,
                watch = cliOptions.watch.value,
              )
            finally
              heartbeat.close()
          }
      }
  }

  private def handleCancel(m: CliClientMessage.Cancel): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    OTEL.withSpan("cli.cancel")(
      _.setAttribute("clientId", clientId)
        .setAttribute("request.id", requestId)
        .setAttribute("cli.targetRequestId", m.requestId)
    ) { _ =>
      projectState.cancelRequest(m.requestId)
      serverMessages.put(CliServerMessage.Exit(130))
    }
  }

  private def handleClean(m: CliClientMessage.Clean): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliCleanOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliCleanOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.clean")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          OTEL.withSpan("cli.clean")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
              .setAttribute("cli.moduleFilters", cliOptions.modules.mkString(","))
              .pipe(b => cliOptions.task.fold(b)(t => b.setAttribute("cli.task", t)))
          ) { span =>
            val success = cliOptions.task match {
              case Some(taskName) =>
                projectState.cleanTasks(cliOptions.modules, taskName)
              case None =>
                projectState.cleanModules(cliOptions.modules)
            }
            if !success then
              span.setStatus(StatusCode.ERROR)
              span.setAttribute("error", "clean operation failed")
            serverMessages.put(CliServerMessage.Exit(if success then 0 else 1))
          }
      }
  }

  private def handleImport(m: CliClientMessage.Import): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliImportOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliImportOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.import")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          OTEL.withSpan("cli.import")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
              .setAttribute("cli.from", cliOptions.from.toString)
          ) { span =>
            val notificationCallback: ServerNotification => Unit = { sn =>
              CliServerMessage.fromServerNotification(sn).foreach(serverMessages.put)
            }
            val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
            val importer = new Importer(serverNotificationsLogger)
            try {
              importer.doImport(cliOptions.from)
              serverMessages.put(CliServerMessage.Exit(0))
            } catch {
              case e: Exception =>
                logger.error("Import failed", e)
                span.setStatus(StatusCode.ERROR)
                span.setAttribute("error", e.getMessage)
                serverMessages.put(CliServerMessage.Log(e.getMessage, LogLevel.ERROR))
                serverMessages.put(CliServerMessage.Exit(1))
            }
          }
      }
  }

  private def handleComplete(m: CliClientMessage.Complete): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    val requestId = ctx.requestId
    if m.args == Seq("--help") || m.args == Seq("-h") then
      serverMessages.put(CliServerMessage.Output(mainargs.Parser[DederCliCompleteOptions].helpText()))
      serverMessages.put(CliServerMessage.Exit(0))
    else
      mainargs.Parser[DederCliCompleteOptions].constructEither(m.args, autoPrintHelpAndExit = None) match {
        case Left(error) =>
          OTEL.withSpan("cli.complete")(
            _.setAttribute("clientId", clientId).setAttribute("request.id", requestId)
          ) { span =>
            span.setStatus(StatusCode.ERROR)
            span.setAttribute("error", error)
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          }
        case Right(cliOptions) =>
          OTEL.withSpan("cli.complete")(
            _.setAttribute("clientId", clientId)
              .setAttribute("request.id", requestId)
              .setAttribute("cli.shell", cliOptions.shell.toString)
              .pipe(b => cliOptions.commandLine.fold(b)(c => b.setAttribute("cli.commandLine", c)))
          ) { _ =>
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
      }
  }

  private def handleShutdown(m: CliClientMessage.Shutdown): Unit = {
    val ctx = RequestContext.clientContext.get()
    val clientId = ctx.clientId
    OTEL.withSpan("cli.shutdown")(
      _.setAttribute("clientId", clientId)
    ) { _ =>
      logger.info(s"Client $clientId requested server shutdown.")
      serverMessages.put(CliServerMessage.Log("Deder server is shutting down...", LogLevel.INFO))
      serverMessages.put(CliServerMessage.Exit(0, serverShuttingDown = true))
      Thread.sleep(100) // let the messages be sent to CLI client

      // Stop accepting new CLI connections immediately — prevents new clients from
      // connecting to this dying server during the flush sleep below
      cliServer.stopAccepting()

      // Release the server lock BEFORE BSP flush sleep so a new server process can
      // start immediately (the client's reconnection loop spawns a server only once)
      projectState.releaseServerLock()

      // Notify BSP clients — they now have time to disconnect while the new server starts
      projectState.notifyBspClientsShuttingDown()
      Thread.sleep(500) // flush window for BSP clients to process disconnect
      projectState.shutdown()
    }
  }
}

object CliClientMessageHandler {
  val planMermaidStagePalette: Seq[String] = Seq(
    "fill:#e8f0fe,stroke:#1a73e8,color:#0b1f44",
    "fill:#e6f4ea,stroke:#137333,color:#0d2e1a",
    "fill:#fef7e0,stroke:#ea8600,color:#3a2500",
    "fill:#fce8e6,stroke:#c5221f,color:#3a0d0c",
    "fill:#f3e8fd,stroke:#9334e6,color:#2c0b4a",
    "fill:#e8eaed,stroke:#5f6368,color:#202124"
  )
}

// --- Output case classes & typeclass instances ---

private case class ModulesOutput(
    moduleIds: Seq[String],
    graph: Graph[DederModule, DefaultEdge]
)
private object ModulesOutput:
  given JsonRW[ModulesOutput] with
    def write(value: ModulesOutput): JValue = JsonRW[Seq[String]].write(value.moduleIds)
    def parse(path: String, jValue: JValue): ModulesOutput =
      throw UnsupportedOperationException("ModulesOutput is write-only")

  given PlainTextWritable[ModulesOutput] with
    def write(value: ModulesOutput): String = value.moduleIds.mkString("\n")

  given DotWritable[ModulesOutput] with
    def write(value: ModulesOutput): String =
      GraphUtils.generateDOT(value.graph, v => v.id, v => Map("label" -> v.id))

  given MermaidWritable[ModulesOutput] with
    def write(value: ModulesOutput): String =
      GraphUtils.generateMermaid(value.graph, v => v.id, v => v.id)

private case class TasksOutput(
    taskInfosPerModule: Map[String, Seq[TaskInfo]],
    graph: Graph[TaskInstance, DefaultEdge],
    groups: Map[String, Seq[TaskInstance]],
    moduleFilter: Option[String]
)
private object TasksOutput:
  given JsonRW[TasksOutput] with
    def write(value: TasksOutput): JValue = JsonRW[Map[String, Seq[TaskInfo]]].write(value.taskInfosPerModule)
    def parse(path: String, jValue: JValue): TasksOutput =
      throw UnsupportedOperationException("TasksOutput is write-only")

  given PlainTextWritable[TasksOutput] with
    def write(value: TasksOutput): String =
      val filteredGroups = value.moduleFilter match
        case Some(moduleId) => value.groups.filter(_._1 == moduleId)
        case None           => value.groups
      val sortedGroupIds = filteredGroups.keys.toSeq.sorted
      val categoryOrder = Seq(
        "Build", "Configuration", "Dependencies", "Verification",
        "Run", "Publishing", "REPL", "Scala.js", "Scala Native", "GraalVM"
      )
      val modulesWithTasks = sortedGroupIds.map { groupId =>
        val moduleTasks = filteredGroups(groupId).map(_.task)
        val grouped = moduleTasks.groupBy(t => if t.category.isEmpty then "Other" else t.category)
        val sortedCategories = categoryOrder.filter(grouped.contains) ++
          grouped.keys.filterNot(categoryOrder.contains).toSeq.sorted
        val categoryLines = sortedCategories.flatMap { cat =>
          val taskNames = grouped(cat).toSeq.sortBy(_.name).map { task =>
            val tags = task.featureTags.map(_.emoji).mkString(" ")
            val suffix = if tags.nonEmpty then s"  $tags" else ""
            s"    ${task.name}$suffix"
          }
          Seq(s"  ${cat}:") ++ taskNames
        }
        s"${groupId}:\n${categoryLines.mkString("\n")}"
      }
      val legend = FeatureTag.values.map(ft => s"${ft.emoji} = ${ft.description}").mkString("  |  ")
      modulesWithTasks.mkString("\n") + "\n\n  " + legend

  given DotWritable[TasksOutput] with
    def write(value: TasksOutput): String =
      GraphUtils.generateDOT(value.graph, v => v.id, v => Map("label" -> v.id))

  given MermaidWritable[TasksOutput] with
    def write(value: TasksOutput): String =
      GraphUtils.generateMermaidWithSubgraphs(
        value.graph, value.groups,
        v => v.id, v => v.task.name
      )

private case class PluginsOutput(plugins: Seq[LoadedPluginInfo])
private object PluginsOutput:
  given JsonRW[PluginsOutput] with
    def write(value: PluginsOutput): JValue =
      val map = value.plugins.map(p => p.id -> p).toMap
      JsonRW[Map[String, LoadedPluginInfo]].write(map)
    def parse(path: String, jValue: JValue): PluginsOutput =
      throw UnsupportedOperationException("PluginsOutput is write-only")

  given PlainTextWritable[PluginsOutput] with
    def write(value: PluginsOutput): String =
      if value.plugins.isEmpty then "No plugins loaded."
      else value.plugins.map(p => s"${p.id} (${p.taskNames.mkString(", ")})").mkString("\n")

private case class PlanOutput(
    stages: Seq[(Int, Seq[String])],
    graph: Graph[TaskInstance, DefaultEdge],
    groups: Map[String, Seq[TaskInstance]],
    stageIdxByTask: Map[TaskInstance, Int]
)
private object PlanOutput:
  given JsonRW[PlanOutput] with
    def write(value: PlanOutput): JValue = JsonRW[Seq[Seq[String]]].write(value.stages.map(_._2))
    def parse(path: String, jValue: JValue): PlanOutput =
      throw UnsupportedOperationException("PlanOutput is write-only")

  given PlainTextWritable[PlanOutput] with
    def write(value: PlanOutput): String =
      value.stages
        .map { case (idx, taskIds) =>
          s"Stage #${idx}:\n" + taskIds.map(s => s"  $s").mkString("\n")
        }
        .mkString("\n")

  given DotWritable[PlanOutput] with
    def write(value: PlanOutput): String =
      GraphUtils.generateDOT(value.graph, v => v.id, v => Map("label" -> v.id))

  given MermaidWritable[PlanOutput] with
    def write(value: PlanOutput): String =
      val stageClassDefs = value.stageIdxByTask.values.toSet.toSeq.sorted.map { stageIdx =>
        s"stage$stageIdx" -> CliClientMessageHandler.planMermaidStagePalette(
          stageIdx % CliClientMessageHandler.planMermaidStagePalette.length
        )
      }.toMap
      GraphUtils.generateMermaidWithSubgraphs(
        value.graph, value.groups,
        v => v.id, v => s"${v.task.name} (#${value.stageIdxByTask(v)})",
        extraLines = Seq("%% #0 = evaluated first stage"),
        vertexCssClassProvider = v => Some(s"stage${value.stageIdxByTask(v)}"),
        classDefs = stageClassDefs
      )
