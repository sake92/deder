package ba.sake.deder

class DederException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

class TaskNotFoundException(message: String, cause: Throwable = null) extends DederException(message, cause)

class TaskEvaluationException(message: String, cause: Throwable = null) extends DederException(message, cause)

class CancelledException(message: String, cause: Throwable = null) extends DederException(message, cause)