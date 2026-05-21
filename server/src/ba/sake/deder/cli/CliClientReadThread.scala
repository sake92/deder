package ba.sake.deder.cli

import java.io.*
import java.nio.channels.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.*
import ba.sake.deder.DederProjectState

class CliClientReadThread(
    projectState: DederProjectState,
    handler: CliClientMessageHandler,
    clientChannel: SocketChannel,
    clientId: Int,
    serverMessages: BlockingQueue[CliServerMessage]
) extends Thread(s"CliClientReadThread-${clientId}"),
      StrictLogging {
  override def run(): Unit = {
    try clientRead(clientChannel, clientId, serverMessages)
    catch {
      case _: AsynchronousCloseException =>
      // all good, client disconnected
    }
  }

  // in theory there can be many client messages:
  // initial command to run + cancellation, possibly more in future
  private def clientRead(
      clientChannel: SocketChannel,
      clientId: Int,
      serverMessages: BlockingQueue[CliServerMessage]
  ): Unit = {
    // newline delimited JSON messages, only one for now..
    val reader =
      new BufferedReader(new InputStreamReader(Channels.newInputStream(clientChannel), StandardCharsets.UTF_8))
    var messageJson: String = null
    while {
      messageJson = reader.readLine()
      messageJson != null
    } do {
      val message =
        try messageJson.parseJson[CliClientMessage]
        catch {
          case e: TupsonException =>
            logger.error(s"Failed to parse message from client $clientId: $messageJson", e)
            CliClientMessage.Help(Seq.empty)
        }
      val requestId = message.getRequestId

      val t1 = new Thread(() => {
        try {
          handler.handle(clientId, requestId, message)
        } catch {
          case e: IOException =>
            // probably client disconnected... but log just in case..
            logger.error(s"IO error processing message from client $clientId, probably client disconnected", e)
          case e: Throwable =>
            logger.error(s"Unhandled error processing message from client $clientId", e)
            serverMessages.put(CliServerMessage.Log(s"Internal error: ${e.getMessage}", LogLevel.ERROR))
            serverMessages.put(CliServerMessage.Exit(1))
        }
      })
      // run in another thread so we can cancel it if needed
      t1.start()

    }
  }
}
