package ba.sake.deder

@main def serverMain(): Unit = {

  if getMajorJavaVersion() < 21 then abort("Must use JDK >= 21")

  val projectRoot = os.pwd / "examples/multi"
  System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

  val projectState = DederProjectState()

  val cliServer = DederCliServer((projectRoot / ".deder/cli.sock").toNIO, projectState)
  val cliServerThread = new Thread(
    () => cliServer.start(),
    "DederCliServer"
  )
  cliServerThread.start()
  cliServerThread.join()

}

def abort(message: String) =
  throw new RuntimeException(message)

def getMajorJavaVersion(): Int = {
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
