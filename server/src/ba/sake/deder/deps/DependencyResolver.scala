package ba.sake.deder.deps

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import com.github.blemale.scaffeine.*
import coursierapi.Fetch
import coursierapi.FetchResult
import coursierapi.Dependency as CoursierDependency
import coursierapi.{MavenRepository as CsMavenRepository, Repository as CsRepository}
import dependency.api.ops.*
import ba.sake.deder.{OTEL, ServerNotificationsLogger}
import ba.sake.deder.ServerNotification
import ba.sake.deder.deps.{DepTree, DepNode, DepConflict}
import com.typesafe.scalalogging.StrictLogging

class DependencyResolver(val repositories: Seq[CsRepository]) extends DependencyResolverApi with StrictLogging {

  // In-process cache for resolved file paths, keyed by sorted dependency
  // coordinates. Scoped to this resolver instance, so repos implicitly key
  // the cache: a new project config → new resolver → new cache.
  private val fetchFilesCache: Cache[String, Seq[os.Path]] =
    Scaffeine()
      .expireAfterAccess(5.minute)
      .maximumSize(50)
      .build()

  def doFetch(
      coursierDependencies: Seq[CoursierDependency],
      notifications: Option[ServerNotificationsLogger] = None
  ): FetchResult = {
    val cache = coursierapi.Cache
      .create()
      .withLogger(notifications.map(new DederCoursierLogger(_)).orNull)
    val fetch = Fetch
      .create()
      .withCache(cache)
      .withDependencies(coursierDependencies*)
    if repositories.nonEmpty then fetch.withRepositories(repositories*)
    fetch.fetchResult()
  }

  def doFetchOne(dependency: CoursierDependency): os.Path =
    os.Path(doFetch(Seq(dependency)).getFiles.asScala.head.toPath)

  def fetch(
      dependencies: Seq[Dependency],
      notifications: Option[ServerNotificationsLogger] = None
  ): FetchResult = {
    val coursierDeps = dependencies.map(_.applied.toCs)
    doFetch(coursierDeps, notifications)
  }

  def fetchFiles(
      dependencies: Seq[Dependency],
      notifications: Option[ServerNotificationsLogger] = None
  ): Seq[os.Path] = {
    if dependencies.isEmpty then Seq.empty
    else {
      val coursierDeps = dependencies.map(_.applied.toCs)
      val key = DependencyResolver.depsCacheKey(coursierDeps)
      fetchFilesCache.get(
        key,
        _ => {
          val span = OTEL.TRACER
            .spanBuilder("DependencyResolver.fetchFiles")
            .setAttribute("deps.count", dependencies.size.toLong)
            .startSpan()
          try doFetch(coursierDeps, notifications).getFiles.asScala.map(f => os.Path(f.toPath)).toSeq
          finally span.end()
        }
      )
    }
  }

  def fetchFile(dependency: Dependency): os.Path =
    fetchFiles(Seq(dependency)).head

  // used from GraalVM tasks
  def resolveTransitiveCoordinates(
      dependencies: Seq[Dependency],
      notifications: Option[ServerNotificationsLogger] = None
  ): Seq[(String, String, String)] = {
    if dependencies.isEmpty then Seq.empty
    else
      fetch(dependencies, notifications)
        .getDependencies()
        .asScala
        .toSeq
        .map(d => (d.getModule.getOrganization, d.getModule.getName, d.getVersion))
  }

  def buildDepTree(
      dependencies: Seq[Dependency],
      notifications: Option[ServerNotificationsLogger] = None
  ): DepTree = {
    if dependencies.isEmpty then
      emptyDepTree()
    else {
      val coursierDeps = dependencies.map(_.applied.toCs)
      val fetchResult = doFetch(coursierDeps, notifications)
      
      // Extract resolved deps with file info
      val resolvedDeps = fetchResult.getDependencies.asScala.toSeq
      val files = fetchResult.getFiles.asScala.map(f => os.Path(f.toPath)).toSeq
      
      // Build DepNode for each resolved dependency
      val depNodes = resolvedDeps.zipWithIndex.flatMap { (dep, idx) =>
        if idx < files.size then {
          val file = files(idx)
          val sizeBytes = if os.exists(file) then os.size(file) else 0L
           
          Some(DepNode(
            org = dep.getModule.getOrganization,
            name = dep.getModule.getName,
            version = dep.getVersion,
            filePath = file.toString,
            fileSizeBytes = sizeBytes,
            depth = 0,
            parents = Seq.empty
          ))
        } else None
      }
      
      // Identify version conflicts
      val conflicts = detectConflicts(resolvedDeps, coursierDeps)
      
      // Calculate total size
      val totalSize = depNodes.map(_.fileSizeBytes).sum
      
      // Direct deps are those in the original dependency list
      val directDepCoords = coursierDeps.map(d => 
        s"${d.getModule.getOrganization}:${d.getModule.getName}:${d.getVersion}"
      ).toSet
      val rootDeps = depNodes.filter(n => directDepCoords.contains(n.coordinate))
      
      DepTree(
        module = "unknown",
        allDeps = depNodes,
        rootDeps = rootDeps,
        conflicts = conflicts,
        totalSizeBytes = totalSize,
        totalUniqueSizeBytes = totalSize
      )
    }
  }

  private def detectConflicts(
      resolvedDeps: Seq[coursierapi.Dependency],
      originalDeps: Seq[coursierapi.Dependency]
  ): Seq[DepConflict] = {
    resolvedDeps
      .groupBy(d => s"${d.getModule.getOrganization}:${d.getModule.getName}")
      .map { case (coord, versionsSeq) =>
        val versions = versionsSeq.map(_.getVersion).distinct
        val isConflict = versions.length > 1
        
        DepConflict(
          coordinate = coord,
          requestedVersions = versions.map(_ -> Seq.empty).toMap,
          resolvedVersion = versionsSeq.head.getVersion,
          isConflict = isConflict
        )
      }
      .toSeq
  }

  private def emptyDepTree(): DepTree =
    DepTree(
      module = "unknown",
      allDeps = Seq.empty,
      rootDeps = Seq.empty,
      conflicts = Seq.empty,
      totalSizeBytes = 0,
      totalUniqueSizeBytes = 0
    )
}

object DependencyResolver {

  /** Package-visible for tests. */
  private[deder] def depsCacheKey(dependencies: Seq[CoursierDependency]): String =
    dependencies.map(_.toString).sorted.mkString(",")

  /** Assemble the final ordered repo list from user-declared repos + the `includeDefaultRepos` flag.
    *
    *   - `includeDefaultRepos = true`, user repos empty or not → `user ++ [~/.m2/repository] ++ Coursier defaults`.
    *   - `includeDefaultRepos = false`, user repos non-empty → `user`.
    *   - `includeDefaultRepos = false`, user repos empty → throws `IllegalArgumentException`.
    */
  def assembleRepositories(
      userRepoUrls: Seq[String],
      includeDefaultRepos: Boolean
  ): Seq[CsRepository] = {
    if !includeDefaultRepos && userRepoUrls.isEmpty then
      throw new IllegalArgumentException(
        "`includeDefaultRepos = false` requires at least one entry in `repositories`."
      )
    val userRepos = userRepoUrls.map { url =>
      try URI.create(url)
      catch
        case _: IllegalArgumentException =>
          throw new IllegalArgumentException(s"Invalid repository URL: $url")
      CsMavenRepository.of(url)
    }
    if includeDefaultRepos then
      val m2local = CsMavenRepository.of(s"file://${os.home}/.m2/repository")
      userRepos ++ Seq(m2local) ++ CsRepository.defaults().asScala.toSeq
    else userRepos
  }
}

class DederCoursierLogger(notifications: ServerNotificationsLogger) extends coursierapi.SimpleLogger {
  private val downloadLengthMap = ConcurrentHashMap[String, Long]()
  override def starting(url: String): Unit =
    notifications.add(ServerNotification.logInfo(s"Download started: $url"))
  override def length(url: String, total: Long, alreadyDownloaded: Long, watching: Boolean): Unit = {
    downloadLengthMap.putIfAbsent(url, total)
  }
  override def progress(url: String, downloaded: Long): Unit = {
    val length = downloadLengthMap.getOrDefault(url, 0L)
    val percentage = if length > 0 then (downloaded * 100 / length) else 0
    notifications.add(ServerNotification.logInfo(s"Downloading $url ... (${percentage}%)"))
  }
  override def done(url: String, success: Boolean): Unit = {
    val status = if success then "completed" else "failed"
    notifications.add(ServerNotification.logInfo(s"Download $status: $url"))
  }
}
