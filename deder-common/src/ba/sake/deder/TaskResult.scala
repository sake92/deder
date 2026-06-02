package ba.sake.deder

import ba.sake.tupson.JsonRW
import java.time.Instant

case class TaskResult[T](
    value: T,
    inputsHash: String,
    outputHash: String,
    cachedAt: Instant = Instant.now()
)derives JsonRW

