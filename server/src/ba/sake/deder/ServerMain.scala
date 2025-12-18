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

    // TODO read from server.properties file
    // TODO maybe make it elastic ThreadPool with min/max threads for better memory usage?
    val tasksExecutorService = Executors.newFixedThreadPool(10)
    val onShutdown = () => {
      println("Deder server is shutting down...")
      // TODO cleaner shutdown of threads
      tasksExecutorService.shutdownNow()
      System.exit(0)
    }
    val projectState = DederProjectState(tasksExecutorService, onShutdown)

    val cliServer = DederCliServer(projectState)
    val cliServerThread = new Thread(() => cliServer.start(), "DederCliServer")

    // TODO make BSP configurable, no need in CI for example..
    val bspProxyServer = DederBspProxyServer(projectState)
    val bspProxyServerThread = new Thread(() => bspProxyServer.start(), "DederBspProxyServer")
    cliServerThread.start()
    bspProxyServerThread.start()

    println("Deder server started.")

    os.watch.watch(
      roots = Seq(projectRoot),
      onEvent = paths => {
        if paths.exists(isServerConfigFile) then
          println(s"Server configuration file changed: ${paths}, restarting server...")
          // TODO
        else if paths.exists(isProjectConfigFile) then
          println(s"Configuration file changed: ${paths}, reloading project...")
          projectState.refreshProjectState(_ => ())
        else if paths.exists(isTaskTriggerCandidate) then
          println(s"Source files changed: ${paths}, triggering tasks...")
      }
    )

    cliServerThread.join()
    bspProxyServerThread.join()
  }

  private def acquireServerLock(projectRoot: os.Path): Unit = {
    val serverLockFile = projectRoot / ".deder/server.lock"

    os.makeDir.all(serverLockFile / os.up)

    val lockFileHandle = new RandomAccessFile(serverLockFile.toIO, "rw")
    val fileLock = lockFileHandle.getChannel.tryLock()
    if fileLock == null then
      println("ERROR: Could not acquire server lock - another server instance is already running for this project")
      lockFileHandle.close()
      sys.exit(1)

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
        !isDederArtifact(p) && !isDevArtifact(p)
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

  def isDevArtifact(p: os.Path): Boolean = {
    val pathSegments = p.segments.toSeq
    pathSegments.contains(".git") ||
    pathSegments.contains(".github") ||
    pathSegments.contains(".idea") ||
    pathSegments.contains(".vscode") ||
    pathSegments.contains(".metals") ||
    pathSegments.contains(".bsp") ||
    pathSegments.contains("target") ||
    pathSegments.contains("out")
  }
}
