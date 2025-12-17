package ba.sake.deder.cli

import java.io.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{Channels, ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}
import scala.util.control.NonFatal
import ba.sake.tupson.{*, given}
import ba.sake.deder.*

class DederCliServer(projectState: DederProjectState) {

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
        val clientChannel = serverChannel.accept() // TODO if null
        clientId += 1
        val currentClientId = clientId
        println(s"Client #$currentClientId connected")
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
      println("Shutting down CLI server...")
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
    var reader =
      new BufferedReader(new InputStreamReader(Channels.newInputStream(clientChannel), StandardCharsets.UTF_8), 1)
    var messageJson: String = reader.readLine()
    val message = messageJson.parseJson[CliClientMessage]
    message match {
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
                  val modules = state.tasksResolver.allModules
                  serverMessages.put(CliServerMessage.Output(modules.map(_.id).toJson))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else if cliOptions.dot.value then {
                  val dot =
                    GraphUtils.generateDOT(state.tasksResolver.modulesGraph, v => v.id, v => Map("label" -> v.id))
                  serverMessages.put(CliServerMessage.Output(dot))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else if cliOptions.ascii.value then {
                  val asciiGraph = GraphUtils.generateAscii(state.tasksResolver.modulesGraph, v => v.id)
                  serverMessages.put(CliServerMessage.Output(asciiGraph))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else {
                  val modules = state.tasksResolver.allModules
                  serverMessages.put(CliServerMessage.Output(modules.map(_.id).mkString("\n")))
                  serverMessages.put(CliServerMessage.Exit(0))
                }
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
                  val tasksPerModule = state.tasksResolver.tasksPerModule.map { case (moduleId, tasks) =>
                    moduleId -> tasks.map(_.task.name)
                  }
                  serverMessages.put(CliServerMessage.Output(tasksPerModule.toJson))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else if cliOptions.dot.value then {
                  val dot =
                    GraphUtils.generateDOT(state.tasksResolver.tasksGraph, v => v.id, v => Map("label" -> v.id))
                  serverMessages.put(CliServerMessage.Output(dot))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else if cliOptions.ascii.value then {
                  val asciiGraph = GraphUtils.generateAscii(state.tasksResolver.tasksGraph, v => v.id)
                  serverMessages.put(CliServerMessage.Output(asciiGraph))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else {
                  val modulesWithTasks = state.tasksResolver.allModules.map { module =>
                    val moduleTasks = state.tasksResolver.tasksPerModule(module.id).map(t => s"  ${t.task.name}")
                    s"${module.id}:\n${moduleTasks.mkString("\n")}"
                  }
                  serverMessages.put(CliServerMessage.Output(modulesWithTasks.mkString("\n")))
                  serverMessages.put(CliServerMessage.Exit(0))
                }
            }
        }

      case m: CliClientMessage.Plan =>
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
                val tasksExecSubgraph = state.executionPlanner.getExecSubgraph(cliOptions.module, cliOptions.task)
                if cliOptions.json.value then {
                  val tasksExecStages = state.executionPlanner.execStages(cliOptions.module, cliOptions.task)
                  serverMessages.put(CliServerMessage.Output(tasksExecStages.map(_.map(_.id)).toJson))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else if cliOptions.dot.value then {
                  val dot = GraphUtils.generateDOT(tasksExecSubgraph, v => v.id, v => Map("label" -> v.id))
                  serverMessages.put(CliServerMessage.Output(dot))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else if cliOptions.ascii.value then {
                  val asciiGraph = GraphUtils.generateAscii(tasksExecSubgraph, v => v.id)
                  serverMessages.put(CliServerMessage.Output(asciiGraph))
                  serverMessages.put(CliServerMessage.Exit(0))
                } else {
                  val tasksExecStages = state.executionPlanner.execStages(cliOptions.module, cliOptions.task)
                  val stagesStr = tasksExecStages.zipWithIndex
                    .map { case (stage, idx) =>
                      s"Stage #${idx}:\n" + stage.map(ti => s"  ${ti.id}").mkString("\n")
                    }
                    .mkString("\n")
                  serverMessages.put(CliServerMessage.Output(stagesStr))
                  serverMessages.put(CliServerMessage.Exit(0))
                }
            }
        }
      case m: CliClientMessage.Exec =>
        mainargs.Parser[DederCliExecOptions].constructEither(m.args) match {
          case Left(error) =>
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          case Right(cliOptions) =>
            // println(s"Running $cliOptions")
            val logCallback: ServerNotification => Unit = {
              case logMsg: ServerNotification.Log if logMsg.level.ordinal > cliOptions.logLevel.ordinal =>
              // skip
              case sn => serverMessages.put(CliServerMessage.fromServerNotification(sn))
            }
            projectState.executeCLI(
              cliOptions.modules,
              cliOptions.task,
              args = cliOptions.args.value,
              logCallback,
              json = cliOptions.json.value
            )
        }
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
                DederCleaner.cleanModules(moduleIds)
                serverMessages.put(CliServerMessage.Exit(0))
            }
        }
      case _: CliClientMessage.Shutdown =>
        // println(s"Client $clientId requested server shutdown.")
        serverMessages.put(CliServerMessage.Log("Deder server is shutting down...", LogLevel.INFO))
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
      val os = Channels.newOutputStream(clientChannel)
      while true do {
        // newline delimited JSON messages
        val message = serverMessages.take()
        os.write((message.toJson + '\n').getBytes(StandardCharsets.UTF_8))
      }
    } catch {
      case e: IOException => // all good, client disconnected..
    } finally {
      println(s"Client ${clientId} disconnected... Bye!")
      clientChannel.close()
    }

}

enum CliClientMessage derives JsonRW {
  case Version()
  case Modules(args: Seq[String])
  case Tasks(args: Seq[String])
  case Plan(args: Seq[String])
  case Exec(args: Seq[String])
  case Clean(args: Seq[String])
  case Shutdown()
}

enum CliServerMessage derives JsonRW {
  case Output(text: String)
  case Log(text: String, level: LogLevel)
  case RunSubprocess(cmd: Seq[String])
  case Exit(exitCode: Int)
}

object CliServerMessage {
  def fromServerNotification(sn: ServerNotification): CliServerMessage = sn match {
    case m: ServerNotification.Output =>
      CliServerMessage.Output(m.text)
    case m: ServerNotification.Log =>
      val level = m.level match {
        case ServerNotification.LogLevel.ERROR   => LogLevel.ERROR
        case ServerNotification.LogLevel.WARNING => LogLevel.WARNING
        case ServerNotification.LogLevel.INFO    => LogLevel.INFO
        case ServerNotification.LogLevel.DEBUG   => LogLevel.DEBUG
        case ServerNotification.LogLevel.TRACE   => LogLevel.TRACE
      }
      CliServerMessage.Log(s"[${m.level.toString.toLowerCase}] ${m.message}", level)
    case tp: ServerNotification.TaskProgress =>
      CliServerMessage.Log(s"${tp.moduleId}.${tp.taskName} progress: ${tp.progress}/${tp.total}", LogLevel.TRACE)
    case rs: ServerNotification.RunSubprocess =>
      CliServerMessage.RunSubprocess(rs.cmd)
    case ServerNotification.RequestFinished(success) =>
      CliServerMessage.Exit(if success then 0 else 1)
  }
}

enum LogLevel derives JsonRW:
  case ERROR, WARNING, INFO, DEBUG, TRACE
