package ba.sake.deder.cli

import java.io.*
import java.nio.channels.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.BlockingQueue
import com.typesafe.scalalogging.StrictLogging
import ba.sake.tupson.*
import ba.sake.deder.DederProjectState
import ba.sake.deder.OTEL
import scala.util.Using
import io.opentelemetry.api.trace.StatusCode

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
            CliClientMessage.Help(Seq.empty)
        }
      val requestId = message.getRequestId

      val t1 = new Thread(() => {
        val (spanName, extraAttrs) = message match {
          case exec: CliClientMessage.Exec =>
            val taskName = {
              val idx = exec.args.indexOf("-t")
              if (idx >= 0 && idx + 1 < exec.args.size) exec.args(idx + 1) else ""
            }
            val moduleIds = exec.args.zipWithIndex
              .collect { case (arg, i) if arg == "-m" && i + 1 < exec.args.size => exec.args(i + 1) }
              .mkString(",")
            (s"cli.exec.$taskName", Seq("cli.task" -> taskName, "cli.moduleIds" -> moduleIds))
          case _ =>
            (s"cli.${message.getClass.getSimpleName.toLowerCase}", Seq.empty)
        }
        val spanBuilder = OTEL.TRACER
          .spanBuilder(spanName)
          .setAttribute("clientId", clientId)
          .setAttribute("request.id", requestId)
        extraAttrs.foreach { case (k, v) => spanBuilder.setAttribute(k, v) }
        val span = spanBuilder.startSpan()
        Using.resource(span.makeCurrent()) { scope =>
          try {
            handler.handle(clientId, requestId, message)
          } catch {
            case e: IOException =>
            // all good, client disconnected...
            case e: Throwable =>
              span.recordException(e)
              span.setStatus(StatusCode.ERROR)
              logger.error(s"Unhandled error processing message from client $clientId", e)
              serverMessages.put(CliServerMessage.Log(s"Internal error: ${e.getMessage}", LogLevel.ERROR))
              serverMessages.put(CliServerMessage.Exit(1))
          } finally span.end()
        }
      })
      // run in another thread so we can cancel it if needed
      t1.start()

    }
  }
}
