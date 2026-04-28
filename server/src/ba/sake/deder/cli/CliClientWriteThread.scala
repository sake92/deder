package ba.sake.deder.cli

import java.io.IOException
import java.nio.channels.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.toJson
import ba.sake.deder.DederProjectState

class CliClientWriteThread(
    projectState: DederProjectState,
    clientChannel: SocketChannel,
    clientId: Int,
    serverMessages: BlockingQueue[CliServerMessage]
) extends Thread(s"CliClientWriteThread-${clientId}"),
      StrictLogging {

  override def run(): Unit = {
    try {
      val outputStream = Channels.newOutputStream(clientChannel)
      var running = true
      while running do {
        // newline delimited JSON messages
        val message = serverMessages.take()
        outputStream.write((message.toJson + '\n').getBytes(StandardCharsets.UTF_8))
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
