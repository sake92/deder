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

    val projectRoot = os.pwd / os.RelPath(projectRootDir)
    System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

    acquireServerLock(projectRoot)

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
}
