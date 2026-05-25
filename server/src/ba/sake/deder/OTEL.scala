package ba.sake.deder

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.{Span, SpanBuilder, StatusCode, Tracer}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapPropagator}

import scala.util.Using
import scala.util.control.NonFatal

object OTEL {
  val TRACER: Tracer = GlobalOpenTelemetry.getTracer("deder-server")

  private val propagator: TextMapPropagator =
    GlobalOpenTelemetry.getPropagators().getTextMapPropagator()

  /** Extracts an OTEL [[Context]] from a W3C traceparent string.
    * Returns `Context.root()` if the extraction fails or the string is malformed.
    */
  def extractParentContext(traceparent: String): Context = {
    val carrier = new java.util.HashMap[String, String]()
    carrier.put("traceparent", traceparent)
    propagator.extract(
      Context.root(),
      carrier,
      new TextMapGetter[java.util.HashMap[String, String]] {
        override def get(carrier: java.util.HashMap[String, String], key: String): String =
          carrier.get(key)
        override def keys(carrier: java.util.HashMap[String, String]): java.lang.Iterable[String] =
          carrier.keySet()
      },
    )
  }

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
