package ba.sake.deder.cli

import java.io.IOException
import java.nio.channels.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.toJson
import ba.sake.deder.DederProjectState

class CliClientSocketWriter(
    projectState: DederProjectState,
    clientChannel: SocketChannel,
    clientId: String,
    serverMessages: BlockingQueue[CliServerMessage]
) extends Runnable, StrictLogging {

  private val MaxMessageChunkSize = 30_000

  /** Write a large-text message split into chunks to keep each JSON line under avaje-jsonb's
    * hardcoded 50 K string-buffer limit.
    */
  private def writeChunks(
      text: String,
      makeMsg: String => CliServerMessage,
      outputStream: java.io.OutputStream
  ): Unit =
    text.grouped(MaxMessageChunkSize).foreach { chunk =>
      val json = makeMsg(chunk).toJson(spaces = 0, sort = false)
      outputStream.write((json + '\n').getBytes(StandardCharsets.UTF_8))
    }

  override def run(): Unit = {
    try {
      val outputStream = Channels.newOutputStream(clientChannel)
      var running = true
      while running do {
        // newline delimited JSON messages
        val message = serverMessages.take()
        message match
          case CliServerMessage.Output(text) if text.length > MaxMessageChunkSize =>
            writeChunks(text, CliServerMessage.Output(_), outputStream)
          case CliServerMessage.Log(text, level) if text.length > MaxMessageChunkSize =>
            writeChunks(text, CliServerMessage.Log(_, level), outputStream)
          case _ =>
            // very important to have ZERO SPACES/NEWLINES!!!
            val jsonMessage = message.toJson(spaces = 0, sort = false)
            outputStream.write((jsonMessage + '\n').getBytes(StandardCharsets.UTF_8))
        running = !message.isInstanceOf[CliServerMessage.Exit] // to exit this thread..
      }
    } catch {
      case e: IOException => // all good, client disconnected..
    } finally {
      logger.info(s"Client ${clientId} disconnected... Bye!")
      if clientChannel.isOpen then clientChannel.close()
      projectState.removeWatchedTasks(clientId)
    }
  }
}
