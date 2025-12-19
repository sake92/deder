package ba.sake.deder

import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.Executors
import scala.util.Properties
import scala.util.Using
import mainargs.*
import ba.sake.deder.cli.DederCliServer
import ba.sake.deder.bsp.DederBspProxyServer
import scala.caps.cap
import scala.util.control.NonFatal
import java.{util => ju}

object ServerMain {

  def main(args: Array[String]): Unit = Parser(this).runOrExit(args)

  @main def startServer(
      @arg(doc = "Root directory of the project") rootDir: Option[String]
  ): Unit = {

    val projectRootDir = rootDir.getOrElse(".")
    println(s"Deder server starting for project root dir: $projectRootDir")

    // 21 because unix sockets locking bug, i'd have to use bytebuffers for 17 and 18.. meh
    if !Properties.isJavaAtLeast(21) then throw DederException("Must run with Java 21+")

    val projectRoot = os.Path(projectRootDir)
    System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

    acquireServerLock(projectRoot)

    val propFile = projectRoot / ".deder/server.properties"
    val props = new ju.Properties()
    var workerThreads = 10
    var maxInactiveSeconds = 600
    var bspEnabled = true
    if (os.exists(propFile) && os.isFile(propFile)) {
      val inputStream = os.read.inputStream(propFile)
      props.load(inputStream)
      maxInactiveSeconds = props.getProperty("maxInactiveSeconds", "600").toInt
      workerThreads = props.getProperty("workerThreads", "10").toInt
      bspEnabled = props.getProperty("bspEnabled", "true").toBoolean
    }
    // TODO maybe make it elastic ThreadPool with min/max threads for better memory usage?
    val tasksExecutorService = Executors.newFixedThreadPool(workerThreads)
    val onShutdown = () => {
      println("Deder server is shutting down...")
      // TODO cleaner shutdown of threads
      tasksExecutorService.shutdownNow()
      sys.exit(0)
    }
    val projectState = DederProjectState(maxInactiveSeconds, tasksExecutorService, onShutdown)

    val cliServer = DederCliServer(projectState)
    val cliServerThread = new Thread(() => cliServer.start(), "DederCliServer")
    cliServerThread.start()

    if bspEnabled then {
      val bspProxyServer = DederBspProxyServer(projectState)
      val bspProxyServerThread = new Thread(() => bspProxyServer.start(), "DederBspProxyServer")
      bspProxyServerThread.start()
    }

    println("Deder server started.")

    os.watch.watch(
      roots = Seq(projectRoot),
      onEvent = paths =>
        try {
          if paths.exists(isServerConfigFile) then
            println(
              s"Server configuration file changed: ${paths}, you need to restart the server with 'deder restart'!"
            )
            // TODO implement restart in client
          else if paths.exists(isProjectConfigFile) then
            println(s"Configuration file changed: ${paths}, reloading project...")
            projectState.reloadProject()
          else if paths.exists(isTaskTriggerCandidate) then
            println(s"Source files changed: ${paths}, triggering tasks...")
            projectState.triggerFileWatchedTasks(paths)
        } catch {
          case NonFatal(_) =>
          // ignore, config might be bad, tasks might fail, etc
        }
    )
  }

  private def acquireServerLock(projectRoot: os.Path): Unit = {
    val serverLockFile = projectRoot / ".deder/server.lock"
    os.makeDir.all(serverLockFile / os.up)
    val lockFileHandle = new RandomAccessFile(serverLockFile.toIO, "rw")
    val fileLock = lockFileHandle.getChannel.tryLock()
    if fileLock == null then {
      println("ERROR: Could not acquire server lock - another server instance is already running for this project")
      lockFileHandle.close()
      sys.exit(1)
    }
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      try {
        fileLock.release()
        lockFileHandle.close()
        os.remove.all(serverLockFile)
      } catch {
        case _: Exception => // Ignore errors during cleanup
      }
    }))
  }

  def isServerConfigFile(p: os.Path): Boolean =
    p == DederGlobals.projectRootDir / ".deder/server.properties"

  def isProjectConfigFile(p: os.Path): Boolean =
    p == DederGlobals.projectRootDir / "deder.pkl"

  def isTaskTriggerCandidate(p: os.Path): Boolean =
    !isServerConfigFile(p) && (
      isProjectConfigFile(p) ||
        !(isDederArtifact(p) || isDevArtifact(p))
    )

  def isDederArtifact(p: os.Path): Boolean = {
    val pathSegments = p.segments.toSeq
    val pathSegments2 = pathSegments.sliding(2).map(s => (s(0), s(1))).toSet
    pathSegments2.contains(".deder" -> "out") ||
    pathSegments2.contains(".deder" -> "logs") ||
    pathSegments2.contains(".deder" -> "server.jar") ||
    pathSegments2.contains(".deder" -> "server.lock") ||
    pathSegments2.contains(".deder" -> "server-cli.sock") ||
    pathSegments2.contains(".deder" -> "server-bsp.sock")
  }

  // TODO read gitignore..
  def isDevArtifact(p: os.Path): Boolean = {
    val pathSegments = p.segments.toSeq
    pathSegments.contains(".git") ||
    pathSegments.contains(".github") ||
    pathSegments.contains(".idea") ||
    pathSegments.contains(".vscode") ||
    pathSegments.contains(".metals") ||
    pathSegments.contains(".bsp") ||
    pathSegments.contains(".bsp") ||
    pathSegments.contains(".scala-build") ||
    pathSegments.contains("target") ||
    pathSegments.contains("out")
  }
}
