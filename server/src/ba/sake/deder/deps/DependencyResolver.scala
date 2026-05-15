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

case class ResolvedDependency(
    organization: String,
    name: String,
    version: String
) {
  def key: String = s"${organization}:${name}"
  def repr: String = s"${organization}:${name}:${version}"
}

case class ResolvedDependencyGraph(
    dependencies: Seq[ResolvedDependency],
    rootDependencies: Set[String],
    parentDependencies: Map[String, Seq[String]],
    artifactFilesByDependency: Map[String, java.io.File]
)

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
    prepareFetch(coursierDependencies, notifications).fetchResult()
  }

  def resolveGraph(
      dependencies: Seq[Dependency],
      notifications: Option[ServerNotificationsLogger] = None
  ): ResolvedDependencyGraph = {
    if dependencies.isEmpty then
      ResolvedDependencyGraph(Seq.empty, Set.empty, Map.empty, Map.empty)
    else
      val coursierDeps = dependencies.map(_.applied.toCs)
      val shadedResult = doFetchDetailed(coursierDeps, notifications)
      val resolution = invoke(shadedResult, "resolution")
      val orderedDependencies = shadedSeqToSeq(invoke(resolution, "orderedDependencies"))
        .map(toResolvedDependency)
        .groupBy(_.repr)
        .values
        .map(_.head)
        .toSeq
        .sortBy(_.repr)
      val rootDependencies = shadedSeqToSeq(invoke(resolution, "rootDependencies"))
        .map(shadedDepRepr)
        .toSet
      val parentDependencies =
        shadedMapToSeq(invoke(resolution, "reverseDependencies"))
          .map { case (child, parents) =>
            shadedDepRepr(child) -> shadedSeqToSeq(parents).map(shadedDepRepr).distinct.sorted
          }
          .toMap
      val artifactFilesByDependency =
        shadedSeqToSeq(invoke(shadedResult, "fullDetailedArtifacts"))
          .flatMap { tuple =>
            val fileOpt = invoke(tuple, "_4")
            if invokeBoolean(fileOpt, "isDefined") then Some(shadedDepRepr(invoke(tuple, "_1")) -> invoke(fileOpt, "get").asInstanceOf[java.io.File])
            else None
          }
          .groupMap(_._1)(_._2)
          .view
          .mapValues(_.head)
          .toMap
      ResolvedDependencyGraph(
        dependencies = orderedDependencies,
        rootDependencies = rootDependencies,
        parentDependencies = parentDependencies,
        artifactFilesByDependency = artifactFilesByDependency
      )
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

  private def prepareFetch(
      coursierDependencies: Seq[CoursierDependency],
      notifications: Option[ServerNotificationsLogger]
  ): Fetch = {
    val cache = coursierapi.Cache
      .create()
      .withLogger(notifications.map(new DederCoursierLogger(_)).orNull)
    val fetch = Fetch
      .create()
      .withCache(cache)
      .withDependencies(coursierDependencies*)
    if repositories.nonEmpty then fetch.withRepositories(repositories*)
    fetch
  }

  private def doFetchDetailed(
      coursierDependencies: Seq[CoursierDependency],
      notifications: Option[ServerNotificationsLogger]
  ): AnyRef = {
    val apiFetch = prepareFetch(coursierDependencies, notifications)
    val helper = moduleSingleton("coursierapi.shaded.coursier.internal.api.ApiHelper$")
    val fetchMethod = helper.getClass.getDeclaredMethods.find(_.getName == "fetch").get
    fetchMethod.setAccessible(true)
    val shadedFetch = fetchMethod.invoke(helper, apiFetch).asInstanceOf[AnyRef]
    val fetchOps = moduleSingleton("coursierapi.shaded.coursier.Fetch$FetchTaskOps$")
    val defaultEcMethod = fetchOps.getClass.getMethods.find(_.getName == "eitherResult$default$1$extension").get
    val executionContext = defaultEcMethod.invoke(fetchOps, shadedFetch)
    val eitherResultMethod = fetchOps.getClass.getMethods.find(_.getName == "eitherResult$extension").get
    val resultEither = eitherResultMethod.invoke(fetchOps, shadedFetch, executionContext).asInstanceOf[AnyRef]
    if invokeBoolean(resultEither, "isLeft") then
      throw new IllegalStateException(invoke(invoke(resultEither, "left"), "get").toString)
    invoke(invoke(resultEither, "toOption"), "get")
  }

  private def shadedSeqToSeq(seq: AnyRef): Seq[AnyRef] = {
    val buffer = scala.collection.mutable.ArrayBuffer.empty[AnyRef]
    val iterator = invoke(seq, "iterator")
    while invokeBoolean(iterator, "hasNext") do buffer += invoke(iterator, "next")
    buffer.toSeq
  }

  private def shadedMapToSeq(map: AnyRef): Seq[(AnyRef, AnyRef)] = {
    val buffer = scala.collection.mutable.ArrayBuffer.empty[(AnyRef, AnyRef)]
    val iterator = invoke(map, "iterator")
    while invokeBoolean(iterator, "hasNext") do {
      val entry = invoke(iterator, "next")
      buffer += ((invoke(entry, "_1"), invoke(entry, "_2")))
    }
    buffer.toSeq
  }

  private def shadedDepRepr(dep: AnyRef): String = {
    val module = invoke(dep, "module")
    s"${invoke(module, "organization")}:${invoke(module, "name")}:${invoke(dep, "version")}"
  }

  private def toResolvedDependency(dep: AnyRef): ResolvedDependency = {
    val module = invoke(dep, "module")
    ResolvedDependency(
      organization = invoke(module, "organization").toString,
      name = invoke(module, "name").toString,
      version = invoke(dep, "version").toString
    )
  }

  private def moduleSingleton(className: String): AnyRef =
    Class.forName(className).getField("MODULE$").get(null).asInstanceOf[AnyRef]

  private def invoke(target: AnyRef, methodName: String, args: AnyRef*): AnyRef = {
    val method = target.getClass.getMethods.find(m => m.getName == methodName && m.getParameterCount == args.length)
      .orElse(target.getClass.getDeclaredMethods.find(m => m.getName == methodName && m.getParameterCount == args.length))
      .getOrElse(throw new NoSuchMethodException(s"${target.getClass.getName}.${methodName}/${args.length}"))
    method.setAccessible(true)
    method.invoke(target, args*)
      .asInstanceOf[AnyRef]
  }

  private def invokeBoolean(target: AnyRef, methodName: String): Boolean =
    invoke(target, methodName).asInstanceOf[java.lang.Boolean].booleanValue()
}

object DependencyResolver {

  /** Package-visible for tests. */
  private[deder] def depsCacheKey(dependencies: Seq[CoursierDependency]): String =
    dependencies.map(_.toString).sorted.mkString(",")

  /** Assemble the final ordered repo list from user-declared repos + the
    * `includeDefaultRepos` flag.
    *
    *   - `includeDefaultRepos = true`, user repos empty or not →
    *     `user ++ [~/.m2/repository] ++ Coursier defaults`.
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
