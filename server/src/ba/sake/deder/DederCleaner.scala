package ba.sake.deder

import scala.jdk.CollectionConverters.*
import ba.sake.deder.config.DederProject
import scala.util.control.NonFatal

object DederCleaner {
  def cleanModules(moduleIds: Seq[String]): Boolean =
    moduleIds.forall { moduleId =>
      val moduleOutDir = DederGlobals.projectRootDir / ".deder/out" / moduleId
      try {
        println(s"Cleaning module '$moduleId' output directory: $moduleOutDir")
        os.remove.all(moduleOutDir, ignoreErrors = true)
        true
      } catch {
        case NonFatal(_) =>
          false
      }
    }
}
