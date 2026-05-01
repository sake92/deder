package ba.sake.deder

import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
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
import io.opentelemetry.context.Context
import ba.sake.deder.TeePrintStream
import ba.sake.deder.cli.DederCliServer
import ba.sake.deder.bsp.DederBspProxyServer
import ba.sake.deder.publish.PublishTasks
import ba.sake.deder.graalvm.GraalVmNativeImageTasks

object ServerMain extends StrictLogging {

  private var serverLockHandle: RandomAccessFile = _
  private var serverFileLock: FileLock = _

  def main(args: Array[String]): Unit = Parser(this).runOrExit(args)

  @main def startServer(
      @arg(doc = "Root directory of the project") rootDir: Option[String]
  ): Unit = {

    val projectRootDir = rootDir.getOrElse(".")
    logger.info(s"Deder server starting for project root dir: $projectRootDir")

    // 21 because unix sockets locking bug, i'd have to use bytebuffers for 17 and 18.. meh
    if !Properties.isJavaAtLeast(21) then throw DederException("Must run with Java 21+")

    val realProjectDir = try {
      java.nio.file.Path.of(projectRootDir).toRealPath().toString
    } catch {
      case e: Exception =>
        logger.warn(s"Could not resolve canonical path for '$projectRootDir', using as-is: ${e.getMessage}")
        projectRootDir
    }
    val projectRoot = os.Path(realProjectDir)
    System.setProperty("DEDER_PROJECT_ROOT_DIR", projectRoot.toString)

    // Tee stdout/stderr so test output reaches CLI clients
    System.setOut(TeePrintStream(System.out, isStdErr = false))
    System.setErr(TeePrintStream(System.err, isStdErr = true))

    acquireServerLock(projectRoot)

    val propFile = projectRoot / ".deder/server.properties"
    val props = new ju.Properties()
    if (os.exists(propFile) && os.isFile(propFile)) {
      Using.resource(os.read.inputStream(propFile))(props.load)
    }

    val logLevel = props.getProperty("logLevel", "INFO").toUpperCase
    val maxInactiveSeconds = props.getProperty("maxInactiveSeconds", "600").toInt
    val workerThreads = props.getProperty("workerThreads", "16").toInt
    val bspEnabled = props.getProperty("bspEnabled", "true").toBoolean
    val maxConcurrentTestForks = Option(props.getProperty("maxConcurrentTestForks"))
      .filter(_.nonEmpty)
      .map(_.toInt)
      .getOrElse(Runtime.getRuntime.availableProcessors())
    DederGlobals.setTestForkSemaphore(maxConcurrentTestForks)

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

    val configFile = projectRoot / "deder.pkl"
    if !os.exists(configFile) || !os.isFile(configFile) then
      logger.warn(
        s"No deder.pkl found at '${configFile}'. Create a deder.pkl configuration file in your project root to get started."
      )

    val coreTasks = CoreTasks()
    val publishTasks = PublishTasks(coreTasks)
    val scalaJsTasks = scalajs.ScalaJsTasks(coreTasks)
    val scalaNativeTasks = scalanative.ScalaNativeTasks(coreTasks)
    val graalvmNativeImageTasks = GraalVmNativeImageTasks(coreTasks)

    val allTasks =
      coreTasks.all ++ publishTasks.all ++ scalaJsTasks.all ++ scalaNativeTasks.all ++ graalvmNativeImageTasks.all
    val tasksRegistry = TasksRegistry(allTasks)
    val projectState = DederProjectState(tasksRegistry, maxInactiveSeconds, tasksExecutorService, onShutdown)

    val cliServer = DederCliServer(projectState)
    val cliServerThread = new Thread(() => cliServer.start(), "DederCliServer")
    cliServerThread.start()

    if bspEnabled then {
      val bspProxyServer = DederBspProxyServer(coreTasks, scalaJsTasks, scalaNativeTasks, projectState)
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

    val attemptLock = () => {
      val handle = new RandomAccessFile(serverLockFile.toIO, "rw")
      val lock = handle.getChannel.tryLock()
      if lock != null then {
        val pid = ProcessHandle.current().pid().toString()
        handle.setLength(0) // truncate any existing content
        handle.seek(0)
        handle.write(pid.getBytes(StandardCharsets.UTF_8))
      }
      (handle, lock)
    }

    val (handle, lock) = attemptLock()
    if lock == null then {
      val existingPidOpt = try {
        val content = os.read(serverLockFile).trim
        if content.nonEmpty then Some(content.toLong) else None
      } catch { case _: Exception => None }

      val isStale = existingPidOpt match {
        case Some(pid) =>
          val alive = ProcessHandle.of(pid).isPresent && ProcessHandle.of(pid).get().isAlive
          !alive
        case None => true
      }

      if isStale then {
        logger.warn(
          s"Found stale server lock (PID: ${existingPidOpt.getOrElse("unknown")}). Breaking lock and retrying..."
        )
        handle.close()
        os.remove.all(serverLockFile)
        val (handle2, lock2) = attemptLock()
        if lock2 == null then {
          val msg = "ERROR: Could not acquire server lock - another server process is already running for this project"
          logger.error(msg)
          System.err.println(msg)
          handle2.close()
          sys.exit(1)
        }
        serverLockHandle = handle2
        serverFileLock = lock2
      } else {
        val msg = "ERROR: Could not acquire server lock - another server process is already running for this project"
        logger.error(msg)
        System.err.println(msg)
        handle.close()
        sys.exit(1)
      }
    } else {
      serverLockHandle = handle
      serverFileLock = lock
    }

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      try {
        serverFileLock.release()
        serverLockHandle.close()
        os.remove.all(serverLockFile)
      } catch {
        case _: Exception =>
      }
    }))
  }

  private def isServerConfigFile(p: os.Path): Boolean =
    p == DederGlobals.projectRootDir / ".deder/server.properties"

  private def isProjectConfigFile(p: os.Path): Boolean =
    p == DederGlobals.projectRootDir / "deder.pkl"

  private def isTaskTriggerCandidate(p: os.Path): Boolean =
    !isServerConfigFile(p) && (
      isProjectConfigFile(p) ||
        !(isDederArtifact(p) || isDevArtifact(p))
    )

  private def isDederArtifact(p: os.Path): Boolean = {
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
    pathSegments.contains(".scala-build") ||
    pathSegments.contains("target") ||
    pathSegments.contains("out")
  }
}
