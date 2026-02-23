package ba.sake.deder.zinc

import scala.concurrent.duration.*
import com.github.blemale.scaffeine.*
import ba.sake.deder.deps.*

object ZincCompilersCache {
  
  private val zincCache: Cache[String, ZincCompiler] =
    Scaffeine()
      .expireAfterAccess(5.minute)
      .maximumSize(10)
      .build()

   def get(scalaVersion: String): ZincCompiler =
    zincCache.get(scalaVersion, _ => makeZincCompiler(scalaVersion))

  private def makeZincCompiler(scalaVersion: String) = {
      val dep =
        if scalaVersion.startsWith("3.") then s"org.scala-lang:scala3-sbt-bridge:${scalaVersion}"
        else "org.scala-sbt::compiler-bridge:1.11.0"
      val compilerBridgeJar = DependencyResolver.fetchFile(
        Dependency.make(dep, scalaVersion)
      )
      ZincCompiler(compilerBridgeJar)
    }
}
