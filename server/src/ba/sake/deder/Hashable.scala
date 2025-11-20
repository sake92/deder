package ba.sake.deder

import java.nio.ByteBuffer
import ba.sake.tupson.JsonRW

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

// project-root relative path
case class DederPath(path: os.SubPath)

object DederPath {
  given Hashable[DederPath] with {
    def hashStr(value: DederPath): String =
      val finalPath = DederGlobals.projectRootDir / value.path
      if os.exists(finalPath) then
        Hashable[os.Path].hashStr(finalPath)
      else
        throw RuntimeException(s"Path does not exist: ${finalPath}")
  }

  import ba.sake.tupson.{*, given}
  import org.typelevel.jawn.ast.JValue

  given JsonRW[DederPath] with {
    def parse(path: String, jValue: JValue): DederPath =
      val str = JsonRW[String].parse(path, jValue)
      DederPath(os.SubPath(str.split("/").toIndexedSeq))

    def write(value: DederPath): JValue =
      JsonRW[String].write(value.path.toString)
  }

}
