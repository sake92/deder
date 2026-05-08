package ba.sake.deder.bsp

import ba.sake.deder.config.DederProject
import ba.sake.deder.config.DederProject.DederModule

private object BspVisibleTargets {

  private given Ordering[Seq[Int]] with
    override def compare(left: Seq[Int], right: Seq[Int]): Int = {
      val paddedLeft = left.padTo(math.max(left.size, right.size), 0)
      val paddedRight = right.padTo(math.max(left.size, right.size), 0)
      paddedLeft.zip(paddedRight).collectFirst {
        case (l, r) if l != r => l.compare(r)
      }.getOrElse(0)
    }

  private enum Platform {
    case JVM, JS, NATIVE, JAVA
  }

  private case class ModuleMeta(
      module: DederModule,
      baseRoot: String,
      platform: Platform,
      isTest: Boolean,
      scalaVersion: Option[String]
  )

  private case class FamilyKey(baseRoot: String, platform: Platform, isTest: Boolean)

  def visibleModuleIds(modules: Seq[DederModule]): Set[String] = {
    val defaultVisibleModuleIds = computeDefaultVisibleModuleIds(modules)
    modules.collect {
      case module
          if Option(module.bspVisible).map(_.booleanValue()).getOrElse(defaultVisibleModuleIds.contains(module.id)) =>
        module.id
    }.toSet
  }

  private def computeDefaultVisibleModuleIds(modules: Seq[DederModule]): Set[String] = {
    val metas = modules.map(moduleMeta)
    val scalaCrossRoots = metas.collect {
      case meta if meta.platform != Platform.JAVA => meta
    }.groupBy(_.baseRoot).collect {
      case (baseRoot, family) if family.map(_.platform).toSet.size > 1 => baseRoot
    }.toSet

    metas.groupBy(meta => FamilyKey(meta.baseRoot, meta.platform, meta.isTest)).values.flatMap { family =>
      val distinctScalaVersions = family.flatMap(_.scalaVersion).distinct
      val requiresProjection = scalaCrossRoots.contains(family.head.baseRoot) || distinctScalaVersions.size > 1
      if requiresProjection then Seq(selectLatestScalaVersion(family).module.id)
      else family.map(_.module.id)
    }.toSet
  }

  private def moduleMeta(module: DederModule): ModuleMeta =
    ModuleMeta(
      module = module,
      baseRoot = normalizedBaseRoot(module),
      platform = platformOf(module),
      isTest = isTestModule(module),
      scalaVersion = scalaVersionOf(module)
    )

  private def normalizedBaseRoot(module: DederModule): String =
    if isTestModule(module) && module.root.endsWith("/test") then module.root.stripSuffix("/test")
    else module.root

  private def platformOf(module: DederModule): Platform = module match {
    case _: DederProject.ScalaJsTestModule => Platform.JS
    case _: DederProject.ScalaJsModule     => Platform.JS
    case _: DederProject.ScalaNativeTestModule =>
      Platform.NATIVE
    case _: DederProject.ScalaNativeModule => Platform.NATIVE
    case _: DederProject.ScalaTestModule => Platform.JVM
    case _: DederProject.ScalaModule     => Platform.JVM
    case _                               => Platform.JAVA
  }

  private def scalaVersionOf(module: DederModule): Option[String] = module match {
    case m: DederProject.ScalaModule => Some(m.scalaVersion)
    case _                           => None
  }

  private def isTestModule(module: DederModule): Boolean = module match {
    case _: DederProject.JavaTestModule        => true
    case _: DederProject.ScalaTestModule       => true
    case _: DederProject.ScalaJsTestModule     => true
    case _: DederProject.ScalaNativeTestModule => true
    case _                                     => false
  }

  private def selectLatestScalaVersion(family: Seq[ModuleMeta]): ModuleMeta =
    family.maxBy(meta => meta.scalaVersion.map(versionKey).getOrElse(Seq.empty))

  private def versionKey(version: String): Seq[Int] =
    version
      .takeWhile(_ != '+')
      .split('.')
      .toSeq
      .map(_.toIntOption.getOrElse(0))
}
