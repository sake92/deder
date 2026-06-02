package ba.sake.deder.testing

class CancelledException(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)
