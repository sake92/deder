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
import scala.compiletime.uninitialized

class DederCliServer(projectState: DederProjectState) extends StrictLogging {

  private var serverChannel: ServerSocketChannel = uninitialized

  def start(): Unit = {

    val relativeSocketPath = ".deder/server-cli.sock"
    val socketPath = DederGlobals.projectRootDir / os.RelPath(relativeSocketPath)
    os.makeDir.all(socketPath / os.up)
    Files.deleteIfExists(socketPath.toNIO)

    // unix limitation for socket path is 108 bytes, so use relative path
    val address = UnixDomainSocketAddress.of(Paths.get(relativeSocketPath))
    serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    serverChannel.bind(address)

    // TODO better try catch
    try {
      while true do {
        // Accept client connection (blocking)
        val clientChannel = serverChannel.accept()
        val clientId = UUID.randomUUID().toString
        logger.info(s"Client $clientId connected")
        val serverMessages = new LinkedBlockingQueue[CliServerMessage]()
        val handler = new CliClientMessageHandler(projectState, serverMessages, this)
        val clientReadThread =
          new CliClientReadThread(projectState, handler, clientChannel, clientId, serverMessages)
        val clientWriteThread = new CliClientWriteThread(projectState, clientChannel, clientId, serverMessages)
        clientWriteThread.start()
        clientReadThread.start()
        // no join, just let them run
      }
    } finally {
      stop()
    }
  }

  /** Close the accept socket immediately so no new clients can connect.
    * Existing client channels are unaffected.
    * Does NOT delete the socket file — that's done by start() of the next server. */
  def stopAccepting(): Unit = {
    try { if (serverChannel != null && serverChannel.isOpen()) serverChannel.close() } catch { case _: Exception => }
  }

  def stop(): Unit = {
    logger.info("Shutting down CLI server...")
    try { if (serverChannel != null && serverChannel.isOpen()) serverChannel.close() } catch { case _: Exception => }
    // Socket file intentionally NOT deleted here — the next server's start() handles cleanup.
    // Deleting here would race with a new server process that already rebound to the socket.
  }

}
