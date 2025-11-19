package ba.sake.deder.coursier

import coursier.core.Dependency
import coursier.{Fetch, Resolve}

object DependencyResolver {

  def fetch(dependencies: Dependency*) = {
    val files = Fetch()
      .withDependencies(dependencies)
      .run()

    files
  }
}
