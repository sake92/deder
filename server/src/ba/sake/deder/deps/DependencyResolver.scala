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

class DependencyResolver(val repositories: Seq[CsRepository]) extends DependencyResolverApi {

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
    if dependencies.isEmpty then return Seq.empty
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

  def fetchFile(dependency: Dependency): os.Path =
    fetchFiles(Seq(dependency)).head
}

object DependencyResolver {

  /** Package-visible for tests. */
  private[deder] def depsCacheKey(dependencies: Seq[CoursierDependency]): String =
    dependencies.map(_.toString).sorted.mkString(",")

  /** Assemble the final ordered repo list from user-declared repos + the
    * `includeDefaultRepos` flag.
    *
    *   - `includeDefaultRepos = true`, user repos empty  → `Seq.empty`
    *     (Coursier applies its own defaults).
    *   - `includeDefaultRepos = true`, user repos non-empty → `user ++ defaults`.
    *   - `includeDefaultRepos = false`, user repos non-empty → `user`.
    *   - `includeDefaultRepos = false`, user repos empty → throws
    *     `IllegalArgumentException`.
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
      if userRepos.isEmpty then Seq.empty
      else userRepos ++ CsRepository.defaults().asScala.toSeq
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
