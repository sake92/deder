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
import ba.sake.tupson.toJson
import ba.sake.deder.*
import ba.sake.deder.importing.Importer
import io.opentelemetry.api.trace.StatusCode
import ba.sake.deder.OTEL

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
    val ctx = RequestContext.cliContext
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
    val ctx = RequestContext.cliContext
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
    val ctx = RequestContext.cliContext
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
                      val output = cliOptions.format match
                        case OutputFormat.PlainText =>
                          filteredModules.map(_.id).mkString("\n")
                        case OutputFormat.Json =>
                          filteredModules.map(_.id).toJson(spaces = 2, sort = true)
                        case OutputFormat.DenseJson =>
                          filteredModules.map(_.id).toJson(spaces = 0, sort = false)
                        case OutputFormat.Dot =>
                          GraphUtils.generateDOT(graph, v => v.id, v => Map("label" -> v.id))
                        case OutputFormat.Mermaid =>
                          GraphUtils.generateMermaid(graph, v => v.id, v => v.id)
                      serverMessages.put(CliServerMessage.Output(output))
                      serverMessages.put(CliServerMessage.Exit(0))
                  }
                }
            }
          }
      }
  }

  private def handleTasks(m: CliClientMessage.Tasks): Unit = {
    val ctx = RequestContext.cliContext
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
                // TODO handle these in a case class + typeclassess
                cliOptions.format match
                  case format @ (OutputFormat.Json | OutputFormat.DenseJson) =>
                    val taskInfosPerModule = state.tasksResolver.publicTaskInstancesPerModule.map {
                      case (moduleId, tasks) =>
                        moduleId -> tasks.map { ti =>
                          TaskInfo(ti.task.name, ti.task.featureTags.map(_.jsonKey).toSeq)
                        }
                    }
                    val json = format match
                      case OutputFormat.Json =>
                        taskInfosPerModule.toJson(spaces = 2, sort = true)
                      case OutputFormat.DenseJson =>
                        taskInfosPerModule.toJson(spaces = 0, sort = false)
                    serverMessages.put(CliServerMessage.Output(json))
                    serverMessages.put(CliServerMessage.Exit(0))
                  case OutputFormat.Dot =>
                    val dot =
                      GraphUtils.generateDOT(
                        state.tasksResolver.publicTaskInstancesGraph,
                        v => v.id,
                        v => Map("label" -> v.id)
                      )
                    serverMessages.put(CliServerMessage.Output(dot))
                    serverMessages.put(CliServerMessage.Exit(0))
                  case OutputFormat.Mermaid =>
                    val mermaid =
                      GraphUtils.generateMermaidWithSubgraphs(
                        state.tasksResolver.publicTaskInstancesGraph,
                        state.tasksResolver.publicTaskInstancesPerModule,
                        v => v.id,
                        v => v.task.name
                      )
                    serverMessages.put(CliServerMessage.Output(mermaid))
                    serverMessages.put(CliServerMessage.Exit(0))
                  case OutputFormat.PlainText =>
                    val modules = cliOptions.module match {
                      case Some(moduleId) =>
                        state.tasksResolver.allModules.filter(_.id == moduleId)
                      case None =>
                        state.tasksResolver.allModules
                    }
                    val sortedModules = modules.sortBy(_.id)
                    val categoryOrder = Seq(
                      "Build",
                      "Configuration",
                      "Dependencies",
                      "Verification",
                      "Run",
                      "Publishing",
                      "REPL",
                      "Scala.js",
                      "Scala Native",
                      "GraalVM"
                    )
                    val modulesWithTasks = sortedModules.map { module =>
                      val moduleTasks = state.tasksResolver.publicTaskInstancesPerModule(module.id).map(_.task)
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
                      s"${module.id}:\n${categoryLines.mkString("\n")}"
                    }
                    val legend = FeatureTag.values.map(ft => s"${ft.emoji} = ${ft.description}").mkString("  |  ")
                    val output = modulesWithTasks.mkString("\n") + "\n\n  " + legend
                    serverMessages.put(CliServerMessage.Output(output))
                    serverMessages.put(CliServerMessage.Exit(0))
            }
          }
      }
  }

  private def handlePlugins(m: CliClientMessage.Plugins): Unit = {
    val ctx = RequestContext.cliContext
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
              case format @ (OutputFormat.Json | OutputFormat.DenseJson) =>
                val jsonMap = plugins.map(p => p.id -> p).toMap
                val json = format match
                  case OutputFormat.Json =>
                    jsonMap.toJson(spaces = 2, sort = true)
                  case OutputFormat.DenseJson =>
                    jsonMap.toJson(spaces = 0, sort = false)
                serverMessages.put(CliServerMessage.Output(json))
              case OutputFormat.PlainText =>
                val output =
                  if plugins.isEmpty then "No plugins loaded."
                  else plugins.map(p => s"${p.id} (${p.taskNames.mkString(", ")})").mkString("\n")
                serverMessages.put(CliServerMessage.Output(output))
              case _ =>
                serverMessages.put(CliServerMessage.Log("Format not supported for plugins (try plain, json, or densejson)", LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(0))
          }
      }
  }

  private def handlePlan(m: CliClientMessage.Plan): Unit = boundary {
    val ctx = RequestContext.cliContext
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
                    val tasksExecSubgraph = state.executionPlanner.getExecSubgraph(validModuleIds, cliOptions.task)
                    val publicSubgraph = GraphUtils.projectPublic(tasksExecSubgraph, !_.task.internal)
                    cliOptions.format match
                      case format @ (OutputFormat.Json | OutputFormat.DenseJson) =>
                        val tasksExecStages = state.executionPlanner.getExecStages(validModuleIds, cliOptions.task)
                        val publicStages = tasksExecStages.map(_.filter(!_.task.internal)).filter(_.nonEmpty)
                        val values = publicStages.map(_.map(_.id))
                        val json = format match
                          case OutputFormat.Json =>
                            values.toJson(spaces = 2, sort = true)
                          case OutputFormat.DenseJson =>
                            values.toJson(spaces = 0, sort = false)
                        serverMessages.put(CliServerMessage.Output(json))
                      case OutputFormat.Dot =>
                        val dot = GraphUtils.generateDOT(publicSubgraph, v => v.id, v => Map("label" -> v.id))
                        serverMessages.put(CliServerMessage.Output(dot))
                      case OutputFormat.Mermaid =>
                        val tasksExecStages2 = state.executionPlanner.getExecStages(validModuleIds, cliOptions.task)
                        val stageByTask = tasksExecStages2.zipWithIndex.flatMap { case (stage, stageIdx) =>
                          stage.map(_ -> stageIdx)
                        }.toMap
                        val stageClassDefs = publicSubgraph
                          .vertexSet()
                          .asScala
                          .map(stageByTask)
                          .toSet
                          .toSeq
                          .sorted
                          .map { stageIdx =>
                            s"stage$stageIdx" -> CliClientMessageHandler.planMermaidStagePalette(
                              stageIdx % CliClientMessageHandler.planMermaidStagePalette.length
                            )
                          }
                          .toMap
                        val groups = publicSubgraph.vertexSet().asScala.toSeq.groupBy(_.moduleId)
                        val mermaid =
                          GraphUtils.generateMermaidWithSubgraphs(
                            publicSubgraph,
                            groups,
                            v => v.id,
                            v => s"${v.task.name} (#${stageByTask(v)})",
                            extraLines = Seq("%% #0 = evaluated first stage"),
                            vertexCssClassProvider = v => Some(s"stage${stageByTask(v)}"),
                            classDefs = stageClassDefs
                          )
                        serverMessages.put(CliServerMessage.Output(mermaid))
                      case OutputFormat.PlainText =>
                        val tasksExecStages = state.executionPlanner.getExecStages(validModuleIds, cliOptions.task)
                        val stagesStr = tasksExecStages.zipWithIndex
                          .flatMap { case (stage, idx) =>
                            val publicStage = stage.filter(!_.task.internal)
                            Option.when(publicStage.nonEmpty)(
                              s"Stage #${idx}:\n" + publicStage.map(ti => s"  ${ti.id}").mkString("\n")
                            )
                          }
                          .mkString("\n")
                        serverMessages.put(CliServerMessage.Output(stagesStr))
                    serverMessages.put(CliServerMessage.Exit(0))
                }
            }
          }
      }
  }

  private def handleExec(m: CliClientMessage.Exec): Unit = {
    val ctx = RequestContext.cliContext
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
    val ctx = RequestContext.cliContext
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
    val ctx = RequestContext.cliContext
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
    val ctx = RequestContext.cliContext
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
    val ctx = RequestContext.cliContext
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
    val ctx = RequestContext.cliContext
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
