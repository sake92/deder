package ba.sake.deder.cli

import ba.sake.deder.*
import ba.sake.tupson.{JsonRW, toJson}

object OutputFormatting:
  def render[T](value: T)(using format: OutputFormat, jsonRw: JsonRW[T]): String =
    format match
      case _: (ExecOutputFormat.PlainText.type | GraphOutputFormat.PlainText.type) =>
        summon[PlainTextWritable[T]].write(value)
      case _: (ExecOutputFormat.Json.type | GraphOutputFormat.Json.type) =>
        summon[JsonRW[T]].write(value).toJson(spaces = 0, sort = false)
      case GraphOutputFormat.Dot =>
        summon[DotWritable[T]].write(value)
      case GraphOutputFormat.Mermaid =>
        summon[MermaidWritable[T]].write(value)
