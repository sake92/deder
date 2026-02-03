package ba.sake.deder

import scala.util.control.NonFatal
import com.typesafe.scalalogging.StrictLogging

object DederCleaner extends StrictLogging {
  def cleanModules(moduleIds: Seq[String]): Boolean =
    moduleIds.forall { moduleId =>
      val moduleOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId
      try {
        logger.debug(s"Cleaning module '$moduleId' output directory: $moduleOutDir")
        os.remove.all(moduleOutDir, ignoreErrors = true)
        true
      } catch {
        case NonFatal(_) =>
          false
      }
    }
}
