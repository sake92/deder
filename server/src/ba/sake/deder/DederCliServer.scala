package ba.sake.deder

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
        val serverMessages = new LinkedBlockingQueue[ServerMessage]()
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
      serverMessages: BlockingQueue[ServerMessage]
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
          val message = messageJson.parseJson[ClientMessage]
          message match {
            case m: ClientMessage.Run =>
              val logCallback: ServerNotification => Unit = n => {
                val modulePrefix = n.moduleId.map(id => s"${id}:").getOrElse("")
                serverMessages.put(
                  ServerMessage.PrintText(s"[${modulePrefix}${n.level.toString.toLowerCase}}] ${n.message}")
                )
              }
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
      serverMessages: BlockingQueue[ServerMessage]
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

enum ClientMessage derives JsonRW {
  case Run(args: Seq[String])
}

// TODO add log level so that client can filter
enum ServerMessage derives JsonRW {
  case PrintText(text: String)
}
