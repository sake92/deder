package ba.sake.deder.coursier

import coursier.*
import coursier.core.Dependency

@main def CoursierApp = {

  println(
    DependencyResolver.fetch(
      Dependency(
        Module(Organization("ba.sake"), ModuleName("tupson_3"), Map.empty),
        VersionConstraint("3.13.0")
      )
    )
  )
}
