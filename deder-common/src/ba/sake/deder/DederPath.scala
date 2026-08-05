package ba.sake.deder

import ba.sake.tupson.JsonRW
import org.typelevel.jawn.ast.JValue
import scala.util.control.NonFatal

// project-root relative path
case class DederPath(path: os.SubPath) {
  def absPath: os.Path =
    DederGlobals.projectRootDir / path
}

object DederPath {
  def apply(relPath: String): DederPath =
    DederPath(os.SubPath(relPath))

  def apply(absPath: os.Path): DederPath =
    DederPath(absPath.subRelativeTo(DederGlobals.projectRootDir))

  given Hashable[DederPath] with {
    def hashStr(value: DederPath): String =
      val finalPath = value.absPath
      // Bind the path to the content hash: renaming a file with unchanged content
      // must still change the hash. Otherwise tasks whose results are path sets
      // (sourceFiles, resources) keep identical output hashes and downstream cached
      // tasks (compile) never invalidate — stale diagnostics referencing the old
      // file name get replayed forever.
      if os.exists(finalPath) then
        HashUtils.hashStr(s"${value.path.toString}=${Hashable[os.Path].hashStr(finalPath)}")
      else ""
  }

  given JsonRW[DederPath] with {
    def parse(path: String, jValue: JValue): DederPath =
      val str = JsonRW[String].parse(path, jValue)
      try DederPath(os.SubPath(str.split("/").toIndexedSeq))
      catch case NonFatal(_) => DederPath(os.Path(str))

    def write(value: DederPath): JValue =
      JsonRW[String].write(value.path.toString)
  }

}
