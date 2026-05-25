package ba.sake.deder

import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging
import org.pkl.core.stdlib.math.MathNodes.log

object DederCleaner extends StrictLogging {
  def cleanModules(moduleIds: Seq[String]): Boolean =
    moduleIds.forall { moduleId =>
      val moduleOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId
      try {
        os.remove.all(moduleOutDir, ignoreErrors = false)
        true
      } catch {
        case NonFatal(e) =>
          logger.warn(s"Error while cleaning module '$moduleId'", e)
          false
      }
    }

  def cleanTask(moduleId: String, taskName: String): Boolean = {
    val taskOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId / taskName
    try {
      os.remove.all(taskOutDir, ignoreErrors = false)
      true
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Error while cleaning task '$moduleId.$taskName'", e)
        false
    }
  }
}
