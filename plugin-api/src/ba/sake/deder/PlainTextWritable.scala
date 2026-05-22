package ba.sake.deder

trait PlainTextWritable[T]:
  def write(value: T): String

object PlainTextWritable extends PlainTextWritableLowPriority:

  given PlainTextWritable[String] = s => s
  given PlainTextWritable[Int] = _.toString
  given PlainTextWritable[Long] = _.toString
  given PlainTextWritable[Boolean] = _.toString

  given PlainTextWritable[os.Path] with
    def write(value: os.Path): String = value.toString

  given PlainTextWritable[DederPath] with
    def write(value: DederPath): String = value.toString

  given [T: PlainTextWritable]: PlainTextWritable[Option[T]] with
    def write(value: Option[T]): String =
      value.map(summon[PlainTextWritable[T]].write).getOrElse("")

  given [T: PlainTextWritable]: PlainTextWritable[Seq[T]] with
    def write(value: Seq[T]): String =
      value.map(summon[PlainTextWritable[T]].write).mkString("\n")

trait PlainTextWritableLowPriority:
  /** Low-priority catch-all: returns empty string for any T. */
  given default[T]: PlainTextWritable[T] = _ => ""
