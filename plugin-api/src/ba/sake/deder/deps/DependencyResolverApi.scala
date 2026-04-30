package ba.sake.deder.deps

trait DependencyResolverApi {
  def fetchFiles(
      dependencies: Seq[Dependency],
      notifications: Option[ba.sake.deder.ServerNotificationsLogger] = None
  ): Seq[os.Path]

  def fetchFile(dependency: Dependency): os.Path
}
