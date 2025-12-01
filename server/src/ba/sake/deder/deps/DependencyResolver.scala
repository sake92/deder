package ba.sake.deder.deps

import scala.jdk.CollectionConverters.*
import coursierapi.Fetch
import coursierapi.Dependency

object DependencyResolver {

  def fetch(dependencies: Dependency*): Seq[os.Path] = {
    val files = Fetch
      .create()
      .withDependencies(dependencies*)
      .fetch()
      .asScala
      .toSeq

    files.map(f => os.Path(f))
  }

  def fetchOne(dependency: Dependency): os.Path =
    fetch(dependency).head
}
