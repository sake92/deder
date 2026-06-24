package ba.sake.deder.cli

import java.io.*
import java.nio.channels.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.*
import ba.sake.deder.*
import ox.*

class CliClientSocketReader(
    projectState: DederProjectState,
    handler: CliClientMessageHandler,
    clientChannel: SocketChannel,
    clientId: String,
    serverMessages: BlockingQueue[CliServerMessage]
) extends Runnable,
      StrictLogging {
  override def run(): Unit = {
    try clientRead(clientChannel, clientId, serverMessages)
    catch {
      case _: AsynchronousCloseException =>
      // all good, client disconnected
    }
    // Client disconnected — cancel any in-flight request for this client.
    // This handles Ctrl+C for native-image clients where sun.misc.Signal
    // doesn't fire, so the Cancel message is never sent explicitly.
    Option(DederGlobals.clientRequestMap.remove(clientId)).foreach { requestId =>
      logger.info(s"Client $clientId disconnected, cancelling request $requestId")
      projectState.cancelRequest(requestId)
    }
  }

  // in theory there can be many client messages:
  // initial command to run + cancellation, possibly more in future
  private def clientRead(
      clientChannel: SocketChannel,
      clientId: String,
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
      val ctx = requestContext(clientId, requestId, message)

      // Track this client's request so we can cancel it on disconnect (Ctrl+C)
      message match
        case _: CliClientMessage.Exec => DederGlobals.clientRequestMap.put(clientId, requestId)
        case _: CliClientMessage.Cancel => DederGlobals.clientRequestMap.remove(clientId)
        case _ => ()

      Thread
        .ofVirtual()
        .start(() =>
          supervised {
            try {
              RequestContext.current.supervisedWhere(ctx) {
                handler.handle(message)
              }
            } catch {
              case e: IOException =>
                // probably client disconnected... but log just in case..
                logger.error(s"IO error processing message from client $clientId, probably client disconnected", e)
              case e: Throwable =>
                logger.error(s"Unhandled error processing message from client $clientId", e)
                serverMessages.put(CliServerMessage.Log(s"Internal error: ${e.getMessage}", LogLevel.ERROR))
                serverMessages.put(CliServerMessage.Exit(1))
            }
          }
        )
      // run in another thread so we can cancel it if needed
    }
  }

  private def requestContext(clientId: String, requestId: String, message: CliClientMessage): RequestContext =
    message match {
      case m: CliClientMessage.Exec =>
        val opts = mainargs
          .Parser[DederCliExecOptions]
          .constructEither(m.args, autoPrintHelpAndExit = None)
          .toOption
        val outputFormat = opts.map(_.format).getOrElse(OutputFormat.PlainText)
        val logLevel = opts.map(_.logLevel).getOrElse(cli.LogLevel.INFO)
        val noColor = m.envVars.contains("NO_COLOR") || opts.exists(_.noColor.value)
        RequestContext(clientId, requestId, m.envVars, outputFormat, logLevel, noColor)
      case _ =>
        RequestContext(clientId, requestId)
    }
}
