package ba.sake.deder

import coursier.cache.{Cache, FileCache}

object DederGlobals {
  val projectRootDir: os.Path = os.Path(System.getProperty("DEDER_PROJECT_ROOT_DIR"))
}
