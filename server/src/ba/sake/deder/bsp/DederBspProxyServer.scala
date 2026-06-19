package ba.sake.deder.bsp

import ba.sake.deder.scalajs.ScalaJsTasks
import ba.sake.deder.scalanative.ScalaNativeTasks

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import java.nio.file.{Files, Path, Paths}
import java.nio.channels.Channels
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.messages.Message
import ch.epfl.scala.bsp4j.*
import com.typesafe.scalalogging.StrictLogging
import com.google.gson.{Gson, JsonParser, TypeAdapter, TypeAdapterFactory}
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.{JsonReader, JsonWriter}
import ba.sake.deder.*
import scala.compiletime.uninitialized

class DederBspProxyServer(
    coreTasks: CoreTasks,
    runTasks: RunTasks,
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
          localServer = new DederBspServer(
            coreTasks,
            runTasks,
            scalaJsTasks,
            scalaNativeTasks,
            projectState,
            () => clientChannel.close()
          )
          val os = Channels.newOutputStream(clientChannel)
          val is = Channels.newInputStream(clientChannel)
          val launcher = new Launcher.Builder[BuildClient]()
            .setOutput(os)
            .setInput(is)
            .setLocalService(localServer)
            .setRemoteInterface(classOf[BuildClient])
            .configureGson { gsonBuilder =>
              gsonBuilder.registerTypeAdapterFactory(new TraceContextExtractorFactory())
            }
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
    CloseUtils.quietly { if (serverChannel != null && serverChannel.isOpen()) serverChannel.close() }
    // Socket file intentionally NOT deleted here — the next server's start() handles cleanup.
    // Deleting here would race with a new server process that already rebound to the socket.
  }

}

/** Gson TypeAdapterFactory that extracts W3C traceparent from incoming BSP messages. Metals injects `_traceparent` into
  * the `params` object of BSP requests. This factory intercepts Message deserialization, peeks at the JSON to find
  * `_traceparent` inside `params`, stores it in [[RequestContext.traceparent]], then delegates to the standard lsp4j
  * MessageTypeAdapter.
  */
private class TraceContextExtractorFactory extends TypeAdapterFactory {

  override def create[T](gson: Gson, tpe: TypeToken[T]): TypeAdapter[T] = {
    if (!classOf[Message].isAssignableFrom(tpe.getRawType)) return null

    val delegate = gson.getDelegateAdapter(this, tpe)
    new TypeAdapter[T] {
      override def read(in: JsonReader): T = {
        // Parse the JSON-RPC envelope into a tree to inspect params._traceparent
        val tree = JsonParser.parseReader(in)
        if (tree.isJsonObject()) {
          val obj = tree.getAsJsonObject()
          if (obj.has("params")) {
            val params = obj.get("params")
            if (params.isJsonObject()) {
              val paramsObj = params.getAsJsonObject()
              if (paramsObj.has("_traceparent")) {
                RequestContext.traceparent.set(paramsObj.get("_traceparent").getAsString())
              }
            }
          }
        }
        delegate.fromJsonTree(tree)
      }

      override def write(out: JsonWriter, value: T): Unit = delegate.write(out, value)
    }
  }
}
