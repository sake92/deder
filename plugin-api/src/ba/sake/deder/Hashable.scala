package ba.sake.deder

import java.nio.ByteBuffer
import scala.util.Using
import ba.sake.tupson.{JsonRW, toJson, given}

extension [T](value: T)(using hashable: Hashable[T]) {
  def hashStr: String = hashable.hashStr(value)
}

trait Hashable[T] {
  def hashStr(value: T): String
}

// Low-priority fallback: derive Hashable[T] from JsonRW[T] by hashing the JSON string.
// Instances defined directly in object Hashable (and in type companions) take priority.
private trait HashableLowPriority {
  given [T](using rw: JsonRW[T]): Hashable[T] with {
    def hashStr(value: T): String = HashUtils.hashStr(value.toJson)
  }
}

object Hashable extends HashableLowPriority {

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
    def hashStr(value: os.Path): String = {
      if !os.exists(value) then ""
      else if os.isFile(value) then
        Using.resource(os.read.inputStream(value)) { inputStream =>
          HashUtils.hashStr(inputStream)
        }
      else if os.isDir(value) then {
        // Bind each child's leaf name to its recursive hash so renames that
        // preserve sibling sort order still change the directory hash.
        val childrenHashes = os.list(value, sort = true).map { child =>
          s"${child.last}=${Hashable[os.Path].hashStr(child)}"
        }
        HashUtils.hashStr(childrenHashes.mkString("-"))
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
      val combinedHash = value.toSeq
        .sortBy(_._1.hashStr)
        .map { (k, v) =>
          s"${k.hashStr}=${v.hashStr}"
        }
        .mkString("-")
      HashUtils.hashStr(combinedHash)
    }
  }
}
