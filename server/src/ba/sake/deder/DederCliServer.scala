package ba.sake.deder

import ba.sake.deder.ServerNotification.Level

import java.io.{ByteArrayOutputStream, IOException}
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}
import ba.sake.tupson.{*, given}

class DederCliServer(socketPath: Path, projectState: DederProjectState) {

  def start(): Unit = {
    println(s"Starting server on $socketPath")
    Files.deleteIfExists(socketPath)

    val address = UnixDomainSocketAddress.of(socketPath)
    val serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    serverChannel.bind(address)

    // TODO better try catch
    try {
      var clientId = 0
      while true do {
        // Accept client connection (blocking)
        println("Waiting for a client to connect...")
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
      Files.deleteIfExists(socketPath)
      println("Server shut down")
    }
  }

  // in theory there can be many client messages,
  // but for now it is just one..
  private def clientRead(
      clientChannel: SocketChannel,
      clientId: Int,
      serverMessages: BlockingQueue[CliServerMessage]
  ): Unit = {
    // newline delimited JSON messages
    val buf = ByteBuffer.allocate(1024)
    var messageOS = new ByteArrayOutputStream(1024)
    while clientChannel.read(buf) != -1 do {
      buf.flip()
      while buf.hasRemaining do {
        val c = buf.get()
        if c == '\n' then {
          val messageJson = messageOS.toString(StandardCharsets.UTF_8)
          val message = messageJson.parseJson[CliClientMessage]
          message match {
            case m: CliClientMessage.Run =>
              val logCallback: ServerNotification => Unit = sn =>
                serverMessages.put(CliServerMessage.fromServerNotification(sn))
              projectState.execute(m.args(0), m.args(1), logCallback)
          }
          messageOS = new ByteArrayOutputStream(1024)
        } else {
          messageOS.write(c)
        }
      }
      buf.clear()
    }
  }

  // TODO virtual thread ??
  private def clientWrite(
      clientChannel: SocketChannel,
      clientId: Int,
      serverMessages: BlockingQueue[CliServerMessage]
  ): Unit =
    try {
      while true do {
        // newline delimited JSON messages
        val message = serverMessages.take()
        val buf = ByteBuffer.wrap((message.toJson + '\n').getBytes(StandardCharsets.UTF_8))
        clientChannel.write(buf)
      }
    } catch {
      case e: IOException =>
        println(s"Client ${clientId} disconnected... Bye!")
    }

}

enum CliClientMessage derives JsonRW {
  case Run(args: Seq[String])
}

// TODO add log level so that client can filter
enum CliServerMessage derives JsonRW {
  case PrintText(text: String, level: CliServerMessage.Level)
  case RunSubprocess(cmd: Seq[String])
  case Exit(exitCode: Int)
}

object CliServerMessage {
  def fromServerNotification(sn: ServerNotification): CliServerMessage = sn match {
    case m: ServerNotification.Message =>
      val level = m.level match {
        case ServerNotification.Level.ERROR   => Level.ERROR
        case ServerNotification.Level.WARNING => Level.WARNING
        case ServerNotification.Level.INFO    => Level.INFO
        case ServerNotification.Level.DEBUG   => Level.DEBUG
      }
      val modulePrefix = m.moduleId.map(id => s"${id}:").getOrElse("")
      CliServerMessage.PrintText(s"[${modulePrefix}${m.level.toString.toLowerCase}] ${m.message}", level)
    case rs: ServerNotification.RunSubprocess =>
      CliServerMessage.RunSubprocess(rs.cmd)
    case ServerNotification.RequestFinished(success) =>
      CliServerMessage.Exit(if success then 0 else -1)
  }

  enum Level derives JsonRW:
    case ERROR, WARNING, INFO, DEBUG

}
