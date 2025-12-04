package ba.sake.deder

class DederException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

class TaskEvaluationException(message: String, cause: Throwable = null) extends DederException(message, cause)
