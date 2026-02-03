package ba.sake.deder.cli

import java.io.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{AsynchronousCloseException, Channels, ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}
import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.{*, given}
import ba.sake.deder.*
import ba.sake.deder.importing.Importer
import io.opentelemetry.api.trace.StatusCode

import java.util.UUID
import scala.util.Using

class DederCliServer(projectState: DederProjectState) extends StrictLogging {

  def start(): Unit = {

    val relativeSocketPath = ".deder/server-cli.sock"
    val socketPath = DederGlobals.projectRootDir / os.RelPath(relativeSocketPath)
    os.makeDir.all(socketPath / os.up)
    Files.deleteIfExists(socketPath.toNIO)

    // unix limitation for socket path is 108 bytes, so use relative path
    val address = UnixDomainSocketAddress.of(Paths.get(relativeSocketPath))
    val serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    serverChannel.bind(address)

    // TODO better try catch
    try {
      var clientId = 0
      while true do {
        // Accept client connection (blocking)
        val clientChannel = serverChannel.accept()
        clientId += 1
        val currentClientId = clientId
        logger.info(s"Client #$currentClientId connected")
        val serverMessages = new LinkedBlockingQueue[CliServerMessage]()
        val clientReadThread = new Thread(
          () => clientRead(clientChannel, currentClientId, serverMessages),
          s"ClientReadThread-$currentClientId"
        )
        val clientWriteThread = new Thread(
          () => clientWrite(clientChannel, currentClientId, serverMessages),
          s"ClientWriteThread-$currentClientId"
        )
        clientWriteThread.start()
        clientReadThread.start()
        // no join, just let them run
      }
    } finally {
      logger.info("Shutting down CLI server...")
      serverChannel.close()
      Files.deleteIfExists(socketPath.toNIO)
    }
  }

  // in theory there can be many client messages,
  // but for now it is just one: initial command to run
  private def clientRead(
      clientChannel: SocketChannel,
      clientId: Int,
      serverMessages: BlockingQueue[CliServerMessage]
  ): Unit = {
    // newline delimited JSON messages, only one for now..
    val reader =
      new BufferedReader(new InputStreamReader(Channels.newInputStream(clientChannel), StandardCharsets.UTF_8))
    var messageJson: String = null
    while {
      messageJson = reader.readLine()
      messageJson != null
    } do {
      println(s"PARSING CLIENT MESSAGE: $messageJson")
      val message =
        try messageJson.parseJson[CliClientMessage]
        catch {
          case e: TupsonException =>
            CliClientMessage.Help(Seq.empty)
        }
      val requestId = message.getRequestId
      val span = OTEL.TRACER
        .spanBuilder(s"cli.${message.getClass.getSimpleName.toLowerCase}")
        .setAttribute("clientId", clientId)
        .setAttribute("request.id", requestId)
        .startSpan()
      try {
        Using.resource(span.makeCurrent()) { scope =>
          val t1 = new Thread(() => {
            handleClientMessage(clientId, requestId, message, serverMessages)
          })
          // run in another thread so we can cancel it if needed
          t1.start()
        }
      } catch {
        case e: IOException =>
          // all good, client disconnected...
        case e: Throwable =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          throw e
      } finally span.end()
    }
  }

  private def handleClientMessage(
      clientId: Int,
        requestId: String,
      message: CliClientMessage,
      serverMessages: BlockingQueue[CliServerMessage]
  ): Unit = {
    println(s"Handling client message: $message")
    message match {
      case m: CliClientMessage.Help =>
        val defaultHelpText =
          """Deder Build Tool Help:
            |
            |Available commands:
            |  version                 Show server version
            |  modules [options]       List modules
            |  tasks [options]         List tasks
            |  plan [options]          Show execution plan for a task
            |  exec [options]          Execute a task
            |  clean [options]         Clean modules
            |  import [options]        Import from other build tool
            |  shutdown                Shutdown the server
            |
            |Use help -c <command> for more details about each command.
            |""".stripMargin

        mainargs.Parser[DederCliHelpOptions].constructEither(m.args) match {
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
              case "shutdown" =>
                serverMessages.put(CliServerMessage.Output("Shuts down the Deder server."))
              case _ =>
                serverMessages.put(CliServerMessage.Output(defaultHelpText))
            }
        }
        serverMessages.put(CliServerMessage.Exit(0))
      case m: CliClientMessage.Version =>
        serverMessages.put(CliServerMessage.Output(s"Server version: 0.0.1"))
        serverMessages.put(CliServerMessage.Exit(0))
      case m: CliClientMessage.Modules =>
        mainargs.Parser[DederCliModulesOptions].constructEither(m.args) match {
          case Left(error) =>
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          case Right(cliOptions) =>
            projectState.lastGood match {
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

                } else if cliOptions.ascii.value then {
                  val asciiGraph = GraphUtils.generateAscii(state.tasksResolver.modulesGraph, v => v.id)
                  serverMessages.put(CliServerMessage.Output(asciiGraph))
                } else {
                  val allModules = state.tasksResolver.allModules.sortBy(_.id)
                  serverMessages.put(CliServerMessage.Output(allModules.map(_.id).mkString("\n")))
                }
                serverMessages.put(CliServerMessage.Exit(0))
            }
        }
      case m: CliClientMessage.Tasks =>
        mainargs.Parser[DederCliTasksOptions].constructEither(m.args) match {
          case Left(error) =>
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          case Right(cliOptions) =>
            projectState.lastGood match {
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
                    GraphUtils.generateDOT(state.tasksResolver.taskInstancesGraph, v => v.id, v => Map("label" -> v.id))
                  serverMessages.put(CliServerMessage.Output(dot))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else if cliOptions.ascii.value then {
                  val asciiGraph = GraphUtils.generateAscii(state.tasksResolver.taskInstancesGraph, v => v.id)
                  serverMessages.put(CliServerMessage.Output(asciiGraph))
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
        // TODO handle errors better, when task not found etc
        mainargs.Parser[DederCliPlanOptions].constructEither(m.args) match {
          case Left(error) =>
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          case Right(cliOptions) =>
            projectState.lastGood match {
              case Left(error) =>
                serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                serverMessages.put(CliServerMessage.Exit(1))
              case Right(state) =>
                val selectedModuleIds =
                  if cliOptions.modules.isEmpty then state.tasksResolver.allModules.map(_.id)
                  else cliOptions.modules
                val tasksExecSubgraph = state.executionPlanner.getExecSubgraph(selectedModuleIds, cliOptions.task)
                if cliOptions.json.value then {
                  val tasksExecStages = state.executionPlanner.getExecStages(selectedModuleIds, cliOptions.task)
                  serverMessages.put(CliServerMessage.Output(tasksExecStages.map(_.map(_.id)).toJson))
                } else if cliOptions.dot.value then {
                  val dot = GraphUtils.generateDOT(tasksExecSubgraph, v => v.id, v => Map("label" -> v.id))
                  serverMessages.put(CliServerMessage.Output(dot))
                } else if cliOptions.ascii.value then {
                  val asciiGraph = GraphUtils.generateAscii(tasksExecSubgraph, v => v.id)
                  serverMessages.put(CliServerMessage.Output(asciiGraph))
                } else {
                  val tasksExecStages = state.executionPlanner.getExecStages(selectedModuleIds, cliOptions.task)
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
      case m: CliClientMessage.Exec =>
        mainargs.Parser[DederCliExecOptions].constructEither(m.args) match {
          case Left(error) =>
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          case Right(cliOptions) =>
            logger.debug(s"Executing $cliOptions")
            val notificationCallback: ServerNotification => Unit = {
              case logMsg: ServerNotification.Log if logMsg.level.ordinal > cliOptions.logLevel.ordinal =>
              // skip
              case sn =>
                CliServerMessage.fromServerNotification(sn).foreach(serverMessages.put)
            }
            val serverNotificationsLogger = ServerNotificationsLogger(notificationCallback)
            projectState.executeCLI(
              clientId,
              requestId,
              cliOptions.modules,
              cliOptions.task,
              args = cliOptions.args.value,
              serverNotificationsLogger,
              json = cliOptions.json.value,
              startWatch = cliOptions.watch.value,
              exitOnEnd = !cliOptions.watch.value
            )
        }
      case m: CliClientMessage.Cancel =>
        projectState.cancelRequest(m.requestId)
        serverMessages.put(CliServerMessage.Exit(130))
      case m: CliClientMessage.Clean =>
        mainargs.Parser[DederCliCleanOptions].constructEither(m.args) match {
          case Left(error) =>
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          case Right(cliOptions) =>
            projectState.lastGood match {
              case Left(error) =>
                serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
                serverMessages.put(CliServerMessage.Exit(1))
              case Right(state) =>
                val moduleIds =
                  if cliOptions.modules.nonEmpty then cliOptions.modules
                  else state.tasksResolver.allModules.map(_.id)
                projectState.cleanModules(moduleIds)
                serverMessages.put(CliServerMessage.Exit(0))
            }
        }
      case m: CliClientMessage.Import =>
        mainargs.Parser[DederCliImportOptions].constructEither(m.args) match {
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
      case _: CliClientMessage.Shutdown =>
        logger.info(s"Client $clientId requested server shutdown.")
        serverMessages.put(CliServerMessage.Log("Deder server is shutting down...", LogLevel.INFO))
        serverMessages.put(CliServerMessage.Exit(0))
        Thread.sleep(100) // let the message be sent
        projectState.shutdown()
    }
  }

  private def clientWrite(
      clientChannel: SocketChannel,
      clientId: Int,
      serverMessages: BlockingQueue[CliServerMessage]
  ): Unit =
    try {
      val outputStream = Channels.newOutputStream(clientChannel)
      var running = true
      while running do {
        // newline delimited JSON messages
        val message = serverMessages.take()
        outputStream.write((message.toJson + '\n').getBytes(StandardCharsets.UTF_8))
        running = !message.isInstanceOf[CliServerMessage.Exit] // to exit this thread..
      }
    } catch {
      case e: IOException => // all good, client disconnected..
    } finally {
      logger.info(s"Client ${clientId} disconnected... Bye!")
      if clientChannel.isOpen then clientChannel.close()
      projectState.removeWatchedTasks(clientId)
    }

}

enum LogLevel derives JsonRW:
  case ERROR, WARNING, INFO, DEBUG, TRACE
