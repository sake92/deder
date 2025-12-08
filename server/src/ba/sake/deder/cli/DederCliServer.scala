package ba.sake.deder.cli

import java.io.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{Channels, ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}
import scala.util.control.NonFatal
import ba.sake.tupson.{*, given}
import ba.sake.deder.*

class DederCliServer(projectState: DederProjectState) {

  def start(): Unit = {
    val socketPath = DederGlobals.projectRootDir / ".deder/server-cli.sock"
    os.makeDir.all(socketPath / os.up)
    Files.deleteIfExists(socketPath.toNIO)

    val address = UnixDomainSocketAddress.of(socketPath.toNIO)
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
      case m: CliClientMessage.Run =>
        mainargs.Parser[DederCliOptions].constructEither(m.args) match {
          case Left(error) =>
            serverMessages.put(CliServerMessage.Log(error, LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
          case Right(cliOptions) =>
            println(s"Running $cliOptions")
            val logCallback: ServerNotification => Unit = {
              case logMsg: ServerNotification.Log if logMsg.level.ordinal > cliOptions.logLevel.ordinal => 
                // skip
              case sn => serverMessages.put(CliServerMessage.fromServerNotification(sn))
            }
            projectState.executeAll(cliOptions.modules, cliOptions.task, logCallback)
        }
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
  case Run(args: Seq[String])
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
    case rs: ServerNotification.RunSubprocess =>
      CliServerMessage.RunSubprocess(rs.cmd)
    case ServerNotification.RequestFinished(success) =>
      CliServerMessage.Exit(if success then 0 else 1)
  }
}

enum LogLevel derives JsonRW:
  case ERROR, WARNING, INFO, DEBUG, TRACE
