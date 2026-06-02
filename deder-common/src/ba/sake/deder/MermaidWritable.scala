package ba.sake.deder

trait MermaidWritable[T]:
  def write(value: T): String

object MermaidWritable:
  given default[T]: MermaidWritable[T] = _ => ""
