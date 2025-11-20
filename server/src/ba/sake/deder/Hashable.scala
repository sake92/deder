package ba.sake.deder

import java.nio.ByteBuffer

trait Hashable[T] {
  def hashStr(value: T): String
}

object Hashable {

  def apply[T](using h: Hashable[T]): Hashable[T] = h

  given Hashable[Int] with {
    def hashStr(value: Int): String =
      HashUtils.hashStr(ByteBuffer.allocate(4).putInt(value).array())
  }

  given Hashable[String] with {
    def hashStr(value: String): String = HashUtils.hashStr(value)
  }

  given Hashable[os.Path] with {
    // TODO add file path into hash
    def hashStr(value: os.Path): String = {
      if os.isFile(value) then HashUtils.hashStr(os.read.inputStream(value))
      else if os.isDir(value) then {
        val childrenHashes = os.list(value, sort = true).map(Hashable[os.Path].hashStr)
        val combinedHash = childrenHashes.mkString("-")
        HashUtils.hashStr(combinedHash)
      } else {
        throw RuntimeException(s"Cannot hash path: ${value}")
      }
    }
  }

  given [T](using h: Hashable[T]): Hashable[Seq[T]] with {
    def hashStr(value: Seq[T]): String = {
      val combinedHash = value.map(h.hashStr).mkString("-")
      HashUtils.hashStr(combinedHash)
    }
  }
}
