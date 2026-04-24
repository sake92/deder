package ba.sake.deder

object ForkedGlobals {
  def projectRootDir: os.Path = os.Path(System.getProperty("DEDER_PROJECT_ROOT_DIR"))
}
