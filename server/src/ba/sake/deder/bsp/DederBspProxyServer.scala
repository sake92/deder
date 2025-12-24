package ba.sake.deder.bsp

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path, Paths}
import java.nio.channels.Channels
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j._
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.DederProjectState
import ba.sake.deder.DederGlobals

class DederBspProxyServer(projectState: DederProjectState) extends StrictLogging {

  def start(): Unit = {
    val relativeSocketPath = ".deder/server-bsp.sock"
    val socketPath = DederGlobals.projectRootDir / os.RelPath(relativeSocketPath)
    os.makeDir.all(socketPath / os.up)
    Files.deleteIfExists(socketPath.toNIO)

    // unix limitation for socket path is 108 bytes, so use relative path
    val address = UnixDomainSocketAddress.of(Paths.get(relativeSocketPath))
    val serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    serverChannel.bind(address)

    try {
      while true do {
        val clientChannel = serverChannel.accept() // TODO finally close
        val localServer = new DederBspServer(projectState, () => clientChannel.close())
        val os = Channels.newOutputStream(clientChannel)
        val is = Channels.newInputStream(clientChannel)
        val launcher = new Launcher.Builder[BuildClient]()
          .setOutput(os)
          .setInput(is)
          .setLocalService(localServer)
          .setRemoteInterface(classOf[BuildClient])
          .create()
        localServer.client = launcher.getRemoteProxy
        launcher.startListening().get() // listen until BSP session is over
        if (clientChannel.isOpen) {
          clientChannel.close()
        }
      }
    } finally {
      serverChannel.close()
      Files.deleteIfExists(socketPath.toNIO)
      logger.info("BSP proxy server shut down")
    }
  }

}
