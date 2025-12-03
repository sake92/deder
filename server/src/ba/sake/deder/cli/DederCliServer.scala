package ba.sake.deder.cli

import java.io.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{Channels, ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}
import ba.sake.tupson.{*, given}
import ba.sake.deder.*

class DederCliServer(projectState: DederProjectState) {

  def start(): Unit = {
    val socketPath = DederGlobals.projectRootDir / ".deder/server-cli.sock"
    // println(s"Starting server with socket $socketPath")
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
        println("Waiting for a CLI client to connect...")
        val clientChannel = serverChannel.accept() // TODO finally close
        clientId += 1
        val currentClientId = clientId
        println(s"Client #$currentClientId connected")
        val serverMessages = new LinkedBlockingQueue[CliServerMessage]()
        val clientReadThread = new Thread(() => clientRead(clientChannel, currentClientId, serverMessages))
        val clientWriteThread = new Thread(() => clientWrite(clientChannel, currentClientId, serverMessages))
        clientReadThread.start()
        clientWriteThread.start()
      }
    } finally {
      serverChannel.close()
      Files.deleteIfExists(socketPath.toNIO)
      println("Server shut down")
    }
  }

  // in theory there can be many client messages,
  // but for now it is just one: initial command to run
  private def clientRead(
      clientChannel: SocketChannel,
      clientId: Int,
      serverMessages: BlockingQueue[CliServerMessage]
  ): Unit = {
    // newline delimited JSON messages
    var reader = new BufferedReader(
      new InputStreamReader(Channels.newInputStream(clientChannel), StandardCharsets.UTF_8)
    )
    var messageJson: String = null
    println(s"Waiting for messages from client ${clientId}...")
    while ({ messageJson = reader.readLine(); println(s"oppp $messageJson"); messageJson != null }) {
      println(s"Received message from client ${clientId}: ${messageJson}")
      val message = messageJson.parseJson[CliClientMessage]
      message match {
        case m: CliClientMessage.Run =>
          val logCallback: ServerNotification => Unit = sn =>
            serverMessages.put(CliServerMessage.fromServerNotification(sn))
          projectState.execute(m.args(0), m.args(1), logCallback)
      }
    }
    println(s"Client ${clientId} disconnected from reading thread.")
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
        os.flush()
      }
    } catch {
      case e: IOException =>
        println(s"Client ${clientId} disconnected... Bye!")
    } finally {
      clientChannel.close()
    }

}

enum CliClientMessage derives JsonRW {
  case Run(args: Seq[String])
}

enum CliServerMessage derives JsonRW {
  case Output(text: String)
  case Log(text: String, level: CliServerMessage.Level)
  case RunSubprocess(cmd: Seq[String])
  case Exit(exitCode: Int)
}

object CliServerMessage {
  def fromServerNotification(sn: ServerNotification): CliServerMessage = sn match {
    case m: ServerNotification.Output =>
      CliServerMessage.Output(m.text)
    case m: ServerNotification.Log =>
      val level = m.level match {
        case ServerNotification.Level.ERROR   => Level.ERROR
        case ServerNotification.Level.WARNING => Level.WARNING
        case ServerNotification.Level.INFO    => Level.INFO
        case ServerNotification.Level.DEBUG   => Level.DEBUG
        case ServerNotification.Level.TRACE   => Level.TRACE
      }
      val modulePrefix = m.moduleId.map(id => s"${id}:").getOrElse("")
      CliServerMessage.Log(s"[${modulePrefix}${m.level.toString.toLowerCase}] ${m.message}", level)
    case rs: ServerNotification.RunSubprocess =>
      CliServerMessage.RunSubprocess(rs.cmd)
    case ServerNotification.RequestFinished(success) =>
      CliServerMessage.Exit(if success then 0 else 1)
  }

  enum Level derives JsonRW:
    case ERROR, WARNING, INFO, DEBUG, TRACE

}
