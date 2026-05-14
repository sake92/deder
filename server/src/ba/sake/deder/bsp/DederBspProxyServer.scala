package ba.sake.deder.bsp

import ba.sake.deder.scalajs.ScalaJsTasks
import ba.sake.deder.scalanative.ScalaNativeTasks

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path, Paths}
import java.nio.channels.Channels
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j.*
import com.typesafe.scalalogging.StrictLogging
import ba.sake.deder.{CoreTasks, DederGlobals, DederProjectState}
import scala.compiletime.uninitialized

class DederBspProxyServer(
    coreTasks: CoreTasks,
    scalaJsTasks: ScalaJsTasks,
    scalaNativeTasks: ScalaNativeTasks,
    projectState: DederProjectState
) extends StrictLogging {

  private var serverChannel: ServerSocketChannel = uninitialized

  def start(): Unit = {
    val relativeSocketPath = ".deder/server-bsp.sock"
    val socketPath = DederGlobals.projectRootDir / os.RelPath(relativeSocketPath)
    os.makeDir.all(socketPath / os.up)
    Files.deleteIfExists(socketPath.toNIO)

    // unix limitation for socket path is 108 bytes, so use relative path
    val address = UnixDomainSocketAddress.of(Paths.get(relativeSocketPath))
    serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    serverChannel.bind(address)

    try {
      while true do {
        var clientChannel: SocketChannel = null
        var localServer: DederBspServer = null
        try {
          clientChannel = serverChannel.accept()
          localServer =
            new DederBspServer(coreTasks, scalaJsTasks, scalaNativeTasks, projectState, () => clientChannel.close())
          val os = Channels.newOutputStream(clientChannel)
          val is = Channels.newInputStream(clientChannel)
          val launcher = new Launcher.Builder[BuildClient]()
            .setOutput(os)
            .setInput(is)
            .setLocalService(localServer)
            .setRemoteInterface(classOf[BuildClient])
            .create()
          localServer.client = launcher.getRemoteProxy
          projectState.registerBspServer(localServer)
          launcher.startListening().get() // listen until BSP session is over
        } finally {
          projectState.unregisterBspServer(localServer)
          if clientChannel != null && clientChannel.isOpen then clientChannel.close()
        }
      }
    } finally {
      stop()
    }
  }

  def stop(): Unit = {
    logger.info("BSP proxy server shutting down...")
    try { if (serverChannel != null && serverChannel.isOpen()) serverChannel.close() } catch { case _: Exception => }
    val socketPath = DederGlobals.projectRootDir / os.RelPath(".deder/server-bsp.sock")
    try Files.deleteIfExists(socketPath.toNIO) catch { case _: Exception => }
  }

}
