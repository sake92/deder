package ba.sake.deder.deps

import coursier.{Fetch, Resolve}
import coursier.core.Dependency

object DependencyResolver {

  def resolve(dependencies: Dependency*): Seq[Dependency] = {
    val resolution = Resolve().withDependencies(dependencies).run()
    resolution.orderedDependencies
  }

  def fetch(dependencies: Dependency*): Seq[os.Path] = {
    val files = Fetch()
      .withDependencies(dependencies)
      .run()

    files.map(f => os.Path(f))
  }

  def fetchOne(dependency: Dependency): os.Path =
    fetch(dependency).head
}
