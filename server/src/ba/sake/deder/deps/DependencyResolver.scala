package ba.sake.deder.deps

import coursier.Fetch
import coursier.core.Dependency

object DependencyResolver {

  def fetch(dependencies: Dependency*): Seq[os.Path] = {
    val files = Fetch()
      .withDependencies(dependencies)
      .run()

    files.map(f=> os.Path(f.toPath))
  }

  def fetchOne(dependency: Dependency): os.Path = 
    fetch(dependency).head
}
