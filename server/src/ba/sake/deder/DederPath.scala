package ba.sake.deder

import ba.sake.tupson.JsonRW
import org.typelevel.jawn.ast.JValue

// project-root relative path
case class DederPath(path: os.SubPath) {
  def absPath: os.Path =
    DederGlobals.projectRootDir / path
}

object DederPath {
  given Hashable[DederPath] with {
    def hashStr(value: DederPath): String =
      val finalPath = value.absPath
      if os.exists(finalPath) then Hashable[os.Path].hashStr(finalPath)
      else throw DederException(s"Path does not exist: ${finalPath}")
  }

  given JsonRW[DederPath] with {
    def parse(path: String, jValue: JValue): DederPath =
      val str = JsonRW[String].parse(path, jValue)
      DederPath(os.SubPath(str.split("/").toIndexedSeq))

    def write(value: DederPath): JValue =
      JsonRW[String].write(value.path.toString)
  }

}
