package ba.sake.deder

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.{Span, SpanBuilder, StatusCode, Tracer}
import scala.util.Using
import scala.util.control.NonFatal

object OTEL {
  val TRACER: Tracer = GlobalOpenTelemetry.getTracer("deder-server")

  /** Creates a span, configures attributes, runs `body` within the span's scope,
    * auto-records any `NonFatal` exception, and ends the span in a finally block.
    *
    * @param spanName  OTEL span name (e.g. "cli.exec.compile")
    * @param configure builder => builder with attributes set before span starts
    * @param body      receives the started Span for setting status/attributes at runtime
    */
  def withSpan(spanName: String)(
      configure: SpanBuilder => SpanBuilder = identity
  )(body: Span => Unit): Unit = {
    val span = configure(TRACER.spanBuilder(spanName)).startSpan()
    Using.resource(span.makeCurrent()) { _ =>
      try body(span)
      catch {
        case NonFatal(e) =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR)
          throw e
      } finally span.end()
    }
  }
}
