package ba.sake.deder

import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging

object DederCleaner extends StrictLogging {
  def cleanModules(moduleIds: Seq[String]): Boolean =
    moduleIds.forall { moduleId =>
      val moduleOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId
      try {
        os.remove.all(moduleOutDir, ignoreErrors = true)
        true
      } catch {
        case NonFatal(_) =>
          false
      }
    }

  def cleanTask(moduleId: String, taskName: String): Boolean = {
    val taskOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId / taskName
    try {
      os.remove.all(taskOutDir, ignoreErrors = true)
      true
    } catch {
      case NonFatal(_) =>
        false
    }
  }
}
