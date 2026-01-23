package ba.sake.deder

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer

object OTEL {
  val TRACER: Tracer = GlobalOpenTelemetry.getTracer("deder-server")
}
