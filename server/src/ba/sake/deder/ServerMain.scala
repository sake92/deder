package ba.sake.deder

import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.Executors
import java.util as ju
import scala.util.control.NonFatal
import scala.util.Properties
import scala.util.Using
import com.typesafe.scalalogging.StrictLogging
import mainargs.*
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ba.sake.deder.cli.DederCliServer
import ba.sake.deder.bsp.DederBspProxyServer
import io.opentelemetry.context.Context

object ServerMain extends StrictLogging {

  def main(args: Array[String]): Unit = Parser(this).runOrExit(args)

  @main def startServer(
      @arg(doc = "Root directory of the project") rootDir: Option[String]
  ): Unit = {

    val projectRootDir = rootDir.getOrElse(".")
    logger.info(s"Deder server starting for project root dir: $projectRootDir")

    // 21 because unix sockets locking bug, i'd have to use bytebuffers for 17 and 18.. meh
    if !Properties.isJavaAtLeast(21) then throw DederException("Must run with Java 21+")

    val projectRoot = os.Path(projectRootDir)
    System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

    acquireServerLock(projectRoot)

    val propFile = projectRoot / ".deder/server.properties"
    val props = new ju.Properties()
    if (os.exists(propFile) && os.isFile(propFile)) {
      val inputStream = os.read.inputStream(propFile)
      props.load(inputStream)
    }

    val logLevel = props.getProperty("logLevel", "INFO").toUpperCase
    val maxInactiveSeconds = props.getProperty("maxInactiveSeconds", "600").toInt
    val workerThreads = props.getProperty("workerThreads", "16").toInt
    val testWorkerThreads = props.getProperty("testWorkerThreads", "16").toInt
    DederGlobals.testWorkerThreads = testWorkerThreads
    val bspEnabled = props.getProperty("bspEnabled", "true").toBoolean

    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    rootLogger.setLevel(Level.toLevel(logLevel))

    // TODO maybe make it elastic ThreadPool with min/max threads for better memory usage?
    val originalTasksExecutorService = Executors.newFixedThreadPool(workerThreads)
    // automatically propagate OTEL context, parent span
    val tasksExecutorService = Context.taskWrapping(originalTasksExecutorService)
    val onShutdown = () => {
      logger.info("Deder server is shutting down...")
      // TODO cleaner shutdown of threads
      tasksExecutorService.shutdownNow()
      sys.exit(0)
    }

    val coreTasks = CoreTasks()
    val tasksRegistry = TasksRegistry(coreTasks)
    val projectState = DederProjectState(tasksRegistry, maxInactiveSeconds, tasksExecutorService, onShutdown)

    val cliServer = DederCliServer(projectState)
    val cliServerThread = new Thread(() => cliServer.start(), "DederCliServer")
    cliServerThread.start()

    if bspEnabled then {
      val bspProxyServer = DederBspProxyServer(projectState)
      val bspProxyServerThread = new Thread(() => bspProxyServer.start(), "DederBspProxyServer")
      bspProxyServerThread.start()
    }

    logger.info("Deder server started.")

    os.watch.watch(
      roots = Seq(projectRoot),
      onEvent = paths =>
        try {
          if paths.exists(isServerConfigFile) then
            logger.debug(
              s"Server configuration file changed: ${paths}, you need to shutdown the server with 'deder shutdown' and start it again!"
            )
            // TODO maybe implement restart in client ?
            // or even in server, but that is trickier..
          else if paths.exists(isProjectConfigFile) then
            logger.debug(s"Configuration file changed: ${paths}, reloading project...")
            projectState.reloadProject()
          else if paths.exists(isTaskTriggerCandidate) then
            logger.debug(s"Source files changed: ${paths}, triggering tasks...")
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
      logger.error("ERROR: Could not acquire server lock - another server process is already running for this project")
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
