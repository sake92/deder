package ba.sake.deder

import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.Executors
import scala.util.Properties
import scala.util.Using
import ba.sake.deder.cli.DederCliServer
import ba.sake.deder.bsp.DederBspProxyServer

@main def serverMain(projectRootDir: String = "."): Unit = {

  println(s"Deder server starting for project root dir: $projectRootDir")

  // 21 because unix sockets locking bug, i'd have to use bytebuffers for 17 and 18.. meh
  if !Properties.isJavaAtLeast(21) then throw DederException("Must run with Java 21+")

  val projectRoot = os.pwd / os.RelPath(projectRootDir)
  System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

  acquireServerLock(projectRoot)

  val tasksExecutorService = Executors.newFixedThreadPool(10)
  val projectState = DederProjectState(tasksExecutorService)

  val cliServer = DederCliServer(projectState)
  val cliServerThread = new Thread(() => cliServer.start(), "DederCliServer")
  val bspProxyServer = DederBspProxyServer(projectState)
  val bspProxyServerThread = new Thread(() => bspProxyServer.start(), "DederBspProxyServer")
  cliServerThread.start()
  bspProxyServerThread.start()

  println("Deder server started.")

  cliServerThread.join()
  bspProxyServerThread.join()
}

def acquireServerLock(projectRoot: os.Path): Unit = {
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
