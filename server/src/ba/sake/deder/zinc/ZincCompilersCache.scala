package ba.sake.deder.zinc

import scala.concurrent.duration.*
import com.github.blemale.scaffeine.*
import ba.sake.deder.deps.*

object ZincCompilersCache {

  private val zincCache: Cache[String, ZincCompiler] =
    Scaffeine()
      .expireAfterAccess(5.minute)
      .maximumSize(10)
      .removalListener[String, ZincCompiler] { (_, compiler, _) =>
        if compiler != null then compiler.close()
      }
      .build()

  def get(scalaVersion: String, dependencyResolver: DependencyResolverApi): ZincCompiler =
    zincCache.get(scalaVersion, _ => makeZincCompiler(scalaVersion, dependencyResolver))

  private def makeZincCompiler(scalaVersion: String, dependencyResolver: DependencyResolverApi) = {
    val dep =
      if scalaVersion.startsWith("3.") then s"org.scala-lang:scala3-sbt-bridge:${scalaVersion}"
      else "org.scala-sbt::compiler-bridge:1.11.0"
    val compilerBridgeJar = dependencyResolver.fetchFile(
      Dependency.make(dep, scalaVersion)
    )
    ZincCompiler(compilerBridgeJar)
  }
}
