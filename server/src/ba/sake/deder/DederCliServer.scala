package ba.sake.deder

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import scala.util.Using

class DederCliServer(socketPath: Path) {

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
        val clientChannel = serverChannel.accept()
        clientId += 1
        val currentClientId = clientId
        println(s"Client #$currentClientId connected")
        // Handle each client in a separate virtual thread
        val t = clientHandlerThread(clientChannel, currentClientId)
        t.start()
      }
    } finally {
      serverChannel.close()
      Files.deleteIfExists(socketPath)
      println("Server shut down")
    }
  }

  // TODO virtual thread ??
  private def clientHandlerThread(clientChannel: SocketChannel, clientId: Int) =
    new Thread(() => {
      try {
        while true do {
          // newline delimited JSON messages
          val message = """ { "text" : "Hello world!" } """ + '\n'
          val buf = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8))
          clientChannel.write(buf)
          Thread.sleep(3000)
        }
      } catch {
        case e: IOException =>
          println(s"Client ${clientId} disconnected... Bye!")
      } finally {
        clientChannel.close()
      }
    })
}
