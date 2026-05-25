package ba.sake.deder.cli

import ba.sake.deder.*
import ba.sake.tupson.{JsonRW, toJson}

object OutputFormatting:
  def render[T](value: T)(using format: OutputFormat, jsonRw: JsonRW[T]): String =
    format match
      case OutputFormat.PlainText =>
        summon[PlainTextWritable[T]].write(value)
      case OutputFormat.Json =>
        summon[JsonRW[T]].write(value).toJson(spaces = 0, sort = false)
      case OutputFormat.Dot =>
        summon[DotWritable[T]].write(value)
      case OutputFormat.Mermaid =>
        summon[MermaidWritable[T]].write(value)
