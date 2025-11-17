package ba.sake.deder

import ba.sake.tupson.{given, *}
import org.typelevel.jawn.ast.*

case class TaskResult[T](
  value: T,
  inputsHash: String,
  outputHash: String
) 

object TaskResult {
  given TaskResultRW[T](using tRW: JsonRW[T]): JsonRW[TaskResult[T]] = new JsonRW {
    def parse (path: String, jValue: JValue): TaskResult[T] =
      jValue match {
        case JObject(o) =>
          val value = tRW.parse(path, o("value"))
          val inputsHash = JsonRW[String].parse(path, o("inputsHash"))
          val outputHash = JsonRW[String].parse(path, o("outputHash"))
          TaskResult(value, inputsHash, outputHash)
      }
    
    def write(value: TaskResult[T]): JValue = 
      val map = Map(
        "value" -> tRW.write(value.value),
        "inputsHash" -> JsonRW[String].write(value.inputsHash),
        "outputHash" -> JsonRW[String].write(value.outputHash)
      )
      JsonRW[Map[String, JValue]].write(map)
  }
}
