package ba.sake.deder

import java.nio.ByteBuffer
import scala.util.Using

extension [T](value: T)(using hashable: Hashable[T]) {
  def hashStr: String = hashable.hashStr(value)
}

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

  given Hashable[Boolean] with {
    def hashStr(value: Boolean): String =
      HashUtils.hashStr(ByteBuffer.allocate(1).put((if value then 1 else 0).toByte).array())
  }

  given Hashable[os.Path] with {
    // TODO add file path into hash
    def hashStr(value: os.Path): String = {
      if os.isFile(value) then
        Using.resource(os.read.inputStream(value)) { inputStream =>
          HashUtils.hashStr(inputStream)
        }
      else if os.isDir(value) then {
        val childrenHashes = os.list(value, sort = true).map(Hashable[os.Path].hashStr)
        val combinedHash = childrenHashes.mkString("-")
        HashUtils.hashStr(combinedHash)
      } else {
        throw DederException(s"Cannot hash path: ${value}")
      }
    }
  }

  given [T](using h: Hashable[T]): Hashable[Option[T]] with {
    def hashStr(value: Option[T]): String =
      value match {
        case Some(v) => h.hashStr(v)
        case None    => ""
      }
  }

  given [T](using h: Hashable[T]): Hashable[Seq[T]] with {
    def hashStr(value: Seq[T]): String = {
      val combinedHash = value.map(h.hashStr).mkString("-")
      HashUtils.hashStr(combinedHash)
    }
  }

  given [K: Hashable, V: Hashable]: Hashable[Map[K, V]] with {
    def hashStr(value: Map[K, V]): String = {
      val combinedHash = value.toSeq.sortBy(_._1.hashStr).map { (k, v) =>
        s"${k.hashStr}=${v.hashStr}"
      }.mkString("-")
      HashUtils.hashStr(combinedHash)
    }
  }
}
