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
        val handler = new CliClientMessageHandler(projectState, serverMessages)
        val clientReadThread =
          new CliClientReadThread(projectState, handler, clientChannel, currentClientId, serverMessages)
        val clientWriteThread = new CliClientWriteThread(projectState, clientChannel, currentClientId, serverMessages)
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

}

enum LogLevel derives JsonRW:
  case ERROR, WARNING, INFO, DEBUG, TRACE
