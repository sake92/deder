package ba.sake.deder

import ba.sake.tupson.{JsonRW, toJson}

/** DenseJson is a machine-friendly format does not use pretty-printing or newlines, and is optimized for compactness
  * and ease of parsing.
  */
enum OutputFormat:
  case PlainText, Json, DenseJson, Dot, Mermaid

object OutputFormat:
  /** Render a value in the given output format using its typeclass instances. */
  def render[T](value: T, format: OutputFormat)(using
      rw: JsonRW[T],
      pw: PlainTextWritable[T],
      dw: DotWritable[T],
      mw: MermaidWritable[T]
  ): String =
    format match
      case OutputFormat.Json      => value.toJson(spaces = 2, sort = true)
      case OutputFormat.DenseJson => value.toJson(spaces = 0, sort = false)
      case OutputFormat.PlainText => pw.write(value)
      case OutputFormat.Dot       => dw.write(value)
      case OutputFormat.Mermaid   => mw.write(value)
