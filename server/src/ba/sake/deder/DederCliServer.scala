package ba.sake.deder

import ba.sake.tupson.{*, given}

import java.io.{ByteArrayOutputStream, IOException}
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import scala.util.Using

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
        // Handle each client in a separate virtual thread
        val clientReadThread = new Thread(() => clientRead(clientChannel, currentClientId))
        val clientWriteThread = new Thread(() => clientWrite(clientChannel, currentClientId))
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
  private def clientRead(clientChannel: SocketChannel, clientId: Int): Unit = {
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
          println(s"GOT FULL MESSAGE from $clientId: " + message)
          message match {
            case m: ClientMessage.Run =>
              println(s"Running ${m.args.mkString(" ")}")
              projectState.execute(m.args(0), m.args(1))
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
  private def clientWrite(clientChannel: SocketChannel, clientId: Int): Unit =
    try {
      while true do {
        // newline delimited JSON messages
        val message = ServerMessage.PrintText(s"Hello client $clientId")
        val buf = ByteBuffer.wrap((message.toJson + '\n').getBytes(StandardCharsets.UTF_8))
        clientChannel.write(buf)
        Thread.sleep(3000)
      }
    } catch {
      case e: IOException =>
        println(s"Client ${clientId} disconnected... Bye!")
    }

}

enum ClientMessage derives JsonRW {
  case Run(args: Seq[String])
}

enum ServerMessage derives JsonRW {
  case PrintText(text: String)
}
