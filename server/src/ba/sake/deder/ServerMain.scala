package ba.sake.deder

import java.util.concurrent.Executors

@main def serverMain(projectRootDir: String = "."): Unit = {

  if getMajorJavaVersion < 17 then
    throw DederException("Must run with Java 17+")

  val projectRoot = os.pwd / os.SubPath(projectRootDir)
  System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

  val tasksExecutorService = Executors.newFixedThreadPool(10)
  val projectState = DederProjectState(tasksExecutorService)

  val cliServer = DederCliServer(projectState)
  val cliServerThread = new Thread(
    () => cliServer.start(),
    "DederCliServer"
  )
  cliServerThread.start()
  cliServerThread.join()
}

def getMajorJavaVersion: Int = {
  // Java 9+ versions are just the major number (e.g., "17")
  // Java 8- versions start with "1." (e.g., "1.8.0_202")
  val parts = scala.util.Properties.javaVersion.split('.')
  if (parts.length > 1 && parts(0) == "1") {
    // For Java 8 and earlier (e.g., "1.8.x"), the major version is the second part ("8")
    parts(1).toIntOption.getOrElse(0)
  } else {
    // For Java 9+ (e.g., "9", "11", "17.0.1"), the major version is the first part
    parts(0).toIntOption.getOrElse(0)
  }
}
