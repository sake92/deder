package ba.sake.deder

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import ba.sake.tupson.JsonRW
import org.typelevel.jawn.ast.*

given JsonRW[os.Path] with {
  def parse(path: String, jValue: JValue): os.Path =
    val str = JsonRW[String].parse(path, jValue)
    os.Path(str)

  def write(value: os.Path): JValue =
    JsonRW[String].write(value.toString)
}
