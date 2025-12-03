package ba.sake.deder.bsp

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path}
import java.nio.channels.Channels
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j._
import ba.sake.deder.DederProjectState
import ba.sake.deder.DederGlobals

class DederBspProxyServer(projectState: DederProjectState) {

  def start(): Unit = {
    val socketPath = DederGlobals.projectRootDir / ".deder/server-bsp.sock"
    // println(s"Starting BSP server with socket $socketPath")
    os.makeDir.all(socketPath / os.up)
    Files.deleteIfExists(socketPath.toNIO)

    val address = UnixDomainSocketAddress.of(socketPath.toNIO)
    val serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    serverChannel.bind(address)

    try {
      val localServer = new DederBspServer(projectState)
      while true do {
        val clientChannel = serverChannel.accept() // TODO finally close
        val os = Channels.newOutputStream(clientChannel)
        val is = Channels.newInputStream(clientChannel)
        val launcher = new Launcher.Builder[BuildClient]()
          .setOutput(os)
          .setInput(is)
          .setLocalService(localServer)
          .setRemoteInterface(classOf[BuildClient])
          .create()
        localServer.client = launcher.getRemoteProxy()
        launcher.startListening().get() // listen until BSP session is over
      }
    } finally {
      serverChannel.close()
      Files.deleteIfExists(socketPath.toNIO)
      println("BSP proxy server shut down")
    }
  }

}
