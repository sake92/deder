package ba.sake.deder.deps

trait DependencyResolverApi {
  def fetchFiles(
      dependencies: Seq[Dependency],
      notifications: Option[ba.sake.deder.ServerNotificationsLogger] = None
  ): Seq[os.Path]

  def fetchFile(dependency: Dependency): os.Path

  /** Fetches source JARs for all given dependencies in parallel. Each dependency is fetched independently so a single
    * failed or slow source lookup does not prevent others from completing. Per-dependency timeout is 30 seconds.
    */
  def fetchSourceFiles(
      dependencies: Seq[Dependency],
      notifications: Option[ba.sake.deder.ServerNotificationsLogger] = None
  ): Seq[os.Path]

  /** Resolves the transitive dependency graph and returns coordinates as (org, name, version) triples. */
  def resolveTransitiveCoordinates(
      dependencies: Seq[Dependency],
      notifications: Option[ba.sake.deder.ServerNotificationsLogger] = None
  ): Seq[(String, String, String)]

  def buildDepTree(
      dependencies: Seq[Dependency],
      notifications: Option[ba.sake.deder.ServerNotificationsLogger] = None
  ): DepTree
}
