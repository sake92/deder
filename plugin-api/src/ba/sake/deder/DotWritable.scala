package ba.sake.deder

trait DotWritable[T]:
  def write(value: T): String

object DotWritable:
  given default[T]: DotWritable[T] = _ => ""
