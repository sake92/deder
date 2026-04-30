package ba.sake.deder.deps

trait DependencyResolverApi {
  def fetchFiles(
      dependencies: Seq[Dependency],
      notifications: Option[ba.sake.deder.ServerNotificationsLogger] = None
  ): Seq[os.Path]

  def fetchFile(dependency: Dependency): os.Path

  /** Resolves the transitive dependency graph and returns coordinates as (org, name, version) triples. */
  def resolveTransitiveCoordinates(
      dependencies: Seq[Dependency],
      notifications: Option[ba.sake.deder.ServerNotificationsLogger] = None
  ): Seq[(String, String, String)]
}
