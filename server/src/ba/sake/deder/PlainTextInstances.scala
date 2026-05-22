package ba.sake.deder

import ba.sake.deder.deps.Dependency

object PlainTextInstances:
  given PlainTextWritable[Dependency] with
    def write(dep: Dependency): String =
      s"${dep.coursierDep.module.organization}:${dep.coursierDep.module.name}:${dep.coursierDep.version}"
