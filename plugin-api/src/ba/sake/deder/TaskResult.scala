package ba.sake.deder

import ba.sake.tupson.JsonRW

case class TaskResult[T](
    value: T,
    inputsHash: String,
    outputHash: String
)derives JsonRW

