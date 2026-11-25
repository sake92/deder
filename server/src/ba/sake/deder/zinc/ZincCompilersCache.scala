package ba.sake.deder.zinc

import scala.concurrent.duration.*
import com.github.blemale.scaffeine.*
import ba.sake.deder.CacheStatsRegistry
import ba.sake.deder.deps.*

class ZincCompilersCache(cacheRegistry: CacheStatsRegistry):

  private val zincCache: Cache[String, ZincCompiler] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minute)
      .maximumSize(10)
      .removalListener[String, ZincCompiler] { (_, compiler, _) =>
        if compiler != null then compiler.close()
      }
      .build()

  cacheRegistry.register("zinc-compilers", zincCache)

  def get(scalaVersion: String, dependencyResolver: DependencyResolverApi): ZincCompiler =
    zincCache.getIfPresent(scalaVersion).getOrElse {
      // Dependency resolution coalesces concurrent fetches without holding the Zinc cache's monitor.
      val compilerBridgeJar = resolveCompilerBridge(scalaVersion, dependencyResolver)
      zincCache.get(scalaVersion, _ => ZincCompiler(compilerBridgeJar, cacheRegistry, scalaVersion))
    }

  private def resolveCompilerBridge(scalaVersion: String, dependencyResolver: DependencyResolverApi): os.Path = {
    val dep =
      if scalaVersion.startsWith("3.") then s"org.scala-lang:scala3-sbt-bridge:${scalaVersion}"
      else "org.scala-sbt::compiler-bridge:1.11.0"
    dependencyResolver.fetchFile(Dependency.make(dep, scalaVersion))
  }
