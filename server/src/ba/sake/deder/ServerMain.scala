package ba.sake.deder

import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

import java.util.concurrent.Semaphore

import java.util as ju
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.Properties
import scala.util.Using
import com.typesafe.scalalogging.StrictLogging
import mainargs.*
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.opentelemetry.context.Context
import io.opentelemetry.api.GlobalOpenTelemetry
import ba.sake.deder.TeePrintStream
import ba.sake.deder.cli.DederCliServer
import ba.sake.deder.bsp.DederBspProxyServer
import ba.sake.deder.publish.PublishTasks
import ba.sake.deder.graalvm.GraalVmNativeImageTasks

object ServerMain extends StrictLogging {

  private var serverLockHandle: RandomAccessFile = uninitialized
  private var serverFileLock: FileLock = uninitialized

  @volatile private var gitignorePatterns: Seq[String] = Seq.empty

  // Watcher fields — used by stopFileWatcher/stopDebounceScheduler during shutdown
  @volatile private var watcherThread: Thread = uninitialized
  @volatile private var debounceThread: Thread = uninitialized
  @volatile private var debounceRunning = true
  private val debounceLock = new Object()
  private val accumulatedChangedPaths = ConcurrentHashMap.newKeySet[os.Path]()

  def main(args: Array[String]): Unit = Parser(this).runOrExit(args)

  @main def startServer(
      @arg(doc = "Root directory of the project") rootDir: Option[String]
  ): Unit = {

    val projectRootDir = rootDir.getOrElse(".")
    logger.info(s"Deder server starting for project root dir: $projectRootDir")

    // 21 because unix sockets locking bug, i'd have to use bytebuffers for 17 and 18.. meh
    if !Properties.isJavaAtLeast(21) then throw DederException("Must run with Java 21+")

    val realProjectDir =
      try {
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

    val cfg = ServerProperties.from(props)
    DederGlobals.setTestForkSemaphore(cfg.maxConcurrentTestForks)
    DederGlobals.setForkTestFlushIntervalMs(cfg.forkTestFlushIntervalMs)
    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    rootLogger.setLevel(Level.toLevel(cfg.logLevel))

    val watchDebounceMs = cfg.watchDebounceMillis

    // TODO check if OTEL still works with virutal threads
    // Use the global OTEL instance for metrics.
    // Export is handled externally (OTEL Java agent, env vars, etc.).
    val metricsMeter = GlobalOpenTelemetry.get().getMeter("deder-server")
    val internals = DederProjectInternalsImpl(metricsMeter)



    // Must be declared before onShutdown to avoid forward reference error (var, not val)
    var cliServer: DederCliServer | Null = null
    var bspProxyServer: DederBspProxyServer | Null = null

    val onShutdown: () => Unit = () => {
      logger.info("Deder server is shutting down...")

      // 1. Stop file watcher and debounce scheduler (prevents events during shutdown)
      stopFileWatcher()
      stopDebounceScheduler()

      // 2. Release server lock (may already be released via early callback — idempotent)
      logger.info("Releasing server lock...")
      try { serverFileLock.release() }
      catch { case _: Exception => }
      try { serverLockHandle.close() }
      catch { case _: Exception => }

      // 3. Close sockets so new connections go to the new server process
      if (cliServer != null) cliServer.nn.stop()
      if bspProxyServer != null then bspProxyServer.stop()

      logger.info("Server shutdown complete.")
      sys.exit(0)
    }

    val configFile = projectRoot / "deder.pkl"
    if !os.exists(configFile) || !os.isFile(configFile) then
      logger.warn(
        s"No deder.pkl found at '${configFile}'. Create a deder.pkl configuration file in your project root to get started."
      )

    val coreTasks = CoreTasks()
    val runTasks = RunTasks(coreTasks)
    val publishTasks = PublishTasks(coreTasks)
    val scalaJsTasks = scalajs.ScalaJsTasks(coreTasks)
    val scalaNativeTasks = scalanative.ScalaNativeTasks(coreTasks)
    val graalvmNativeImageTasks = GraalVmNativeImageTasks(coreTasks)

    val allTasks =
      coreTasks.all ++ runTasks.all ++ publishTasks.all ++ scalaJsTasks.all ++ scalaNativeTasks.all ++ graalvmNativeImageTasks.all
    val tasksRegistry = TasksRegistry(allTasks)
    val projectState = DederProjectState(
      coreTasks,
      runTasks,
      scalaJsTasks,
      scalaNativeTasks,
      graalvmNativeImageTasks,
      tasksRegistry,
      cfg.maxInactiveSeconds,
      cfg.taskLockTimeoutSeconds,
      onShutdown,
      configFile = DederGlobals.projectRootDir / "deder.pkl",
      internals = internals
    )

    debounceThread = Thread.ofVirtual().name("watch-debounce").start(() => {
      debounceLock.synchronized {
        while (debounceRunning) {
          debounceLock.wait(watchDebounceMs)
          // debounceRunning may have been set to false during wait() — while condition will exit
          val snapshot = {
            val iter = accumulatedChangedPaths.iterator()
            val buf = Set.newBuilder[os.Path]
            while (iter.hasNext) do
              buf += iter.next()
              iter.remove()
            buf.result()
          }
          if (snapshot.nonEmpty) {
            logger.debug(
              s"Debounce fired, triggering tasks for ${snapshot.size} changed paths: ${snapshot.take(3).mkString(", ")}..."
            )
            projectState.triggerFileWatchedTasks(snapshot)
          }
        }
      }
    })

    // Wire up early lock release for fast shutdown+restart
    projectState.setReleaseServerLock(() => {
      try { serverFileLock.release() }
      catch { case _: Exception => }
      try { serverLockHandle.close() }
      catch { case _: Exception => }
    })

    // Platform thread — Runtime shutdown hooks must execute reliably during JVM teardown
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      logger.warn("JVM shutdown hook fired — best-effort BSP request cancellation")
      try projectState.notifyBspClientsShuttingDown()
      catch { case NonFatal(e) => logger.warn(s"Failed to notify BSP clients during shutdown hook: ${e.getMessage}") }
      try Thread.sleep(300) // short flush window for cancelled responses
      catch { case _: InterruptedException => }
    }))

    cliServer = DederCliServer(projectState)
    // Platform thread — root accept loop keeping the JVM alive
    val cliServerThread = new Thread(() => cliServer.nn.start(), "DederCliServer")
    cliServerThread.start()

    // Platform thread — root accept loop keeping the JVM alive
    if cfg.bspEnabled then {
      bspProxyServer = DederBspProxyServer(coreTasks, runTasks, scalaJsTasks, scalaNativeTasks, projectState)
      val bspProxyServerThread = new Thread(() => bspProxyServer.nn.start(), "DederBspProxyServer")
      bspProxyServerThread.start()
    }

    logger.info("Deder server started.")

    loadGitignore()

    // Run file watcher on a dedicated daemon thread so it can be interrupted during shutdown
    // Platform thread — uses native filesystem I/O (os.watch.watch); joined from main to keep JVM alive
    watcherThread = new Thread(
      () => {
        try {
          os.watch.watch(
            roots = Seq(projectRoot),
            onEvent = paths =>
              try {
                if paths.exists(isServerConfigFile) then
                  logger.debug(
                    s"Server configuration file changed: ${paths}, you need to shutdown the server with 'deder shutdown' and start it again!"
                  )
                else if paths.exists(isProjectConfigFile) then
                  logger.debug(s"Configuration file changed: ${paths}, reloading project...")
                  projectState.reloadProject()
                else if paths.exists(p => p == projectRoot / ".gitignore") then
                  logger.debug(s".gitignore changed, reloading...")
                  loadGitignore()
                else if paths.exists(isTaskTriggerCandidate) then
                  val candidates = paths.filter(isTaskTriggerCandidate)
                  if candidates.nonEmpty then {
                    accumulatedChangedPaths.addAll(candidates.asJava)
                    debounceLock.synchronized { debounceLock.notify() }
                  }
              } catch {
                case _: InterruptedException => throw new InterruptedException // propagate to exit watcher loop
                case NonFatal(_)             =>
                // ignore, config might be bad, tasks might fail, etc
              },
            filter = p => {
              val segs = p.relativeTo(DederGlobals.projectRootDir).segments.toSeq
              val firstSeg = segs.headOption.getOrElse("")
              val isDederSubdir =
                firstSeg == ".deder" && segs.lift(1).exists(FileWatchUtils.ignoredDederSubdirs.contains)
              val isDevDir = FileWatchUtils.ignoredDirNames.contains(firstSeg)
              !(isDederSubdir || isDevDir)
            }
          )
        } catch {
          case _: InterruptedException => logger.info("File watcher interrupted, stopping...")
          case NonFatal(e)             => logger.error(s"File watcher error: ${e.getMessage}", e)
        }
        ()
      },
      "file-watcher"
    )
    watcherThread.setDaemon(true)
    watcherThread.start()
    watcherThread.join()
  }

  private def stopFileWatcher(): Unit = {
    logger.info("Stopping file watcher...")
    try { watcherThread.interrupt() }
    catch { case _: Exception => }
    try { watcherThread.join(3000) }
    catch { case _: Exception => }
  }

  private def stopDebounceScheduler(): Unit = {
    logger.info("Stopping debounce thread...")
    debounceRunning = false
    debounceLock.synchronized { debounceLock.notify() }
    try { debounceThread.join(3000) }
    catch { case _: Exception => }
  }

  private[deder] def newCompileSemaphore(cfg: ServerProperties): Semaphore =
    new Semaphore(cfg.maxActiveCompilers)

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
      val existingPidOpt =
        try {
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

    // Platform thread — Runtime shutdown hooks must execute reliably during JVM teardown
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      logger.warn("JVM shutdown hook fired (unexpected exit) — releasing lock as safety net")
      try { serverFileLock.release() }
      catch { case _: Exception => }
      try { serverLockHandle.close() }
      catch { case _: Exception => }
    }))
  }

  private def isServerConfigFile(p: os.Path): Boolean =
    p == DederGlobals.projectRootDir / ".deder/server.properties"

  private def isProjectConfigFile(p: os.Path): Boolean =
    p == DederGlobals.projectRootDir / "deder.pkl"

  private def isTaskTriggerCandidate(p: os.Path): Boolean =
    !isServerConfigFile(p) && (
      isProjectConfigFile(p) ||
        !(isDederArtifact(p) || isDevArtifact(p) || isIgnoredByGitignore(p))
    )

  private def isDederArtifact(p: os.Path): Boolean =
    FileWatchUtils.isDederArtifact(p, DederGlobals.projectRootDir)

  private def isDevArtifact(p: os.Path): Boolean =
    FileWatchUtils.isDevArtifact(p, DederGlobals.projectRootDir)

  private def loadGitignore(): Unit = {
    val gitignoreFile = DederGlobals.projectRootDir / ".gitignore"
    gitignorePatterns = FileWatchUtils.readGitignorePatterns(gitignoreFile)
    logger.debug(s"Loaded ${gitignorePatterns.size} .gitignore patterns from ${gitignoreFile}")
  }

  private def isIgnoredByGitignore(p: os.Path): Boolean = {
    val relativePath = p.relativeTo(DederGlobals.projectRootDir).toString.replace(java.io.File.separatorChar, '/')
    val isDir = os.isDir(p)
    FileWatchUtils.isIgnoredByGitignore(relativePath, isDir, gitignorePatterns)
  }

}
