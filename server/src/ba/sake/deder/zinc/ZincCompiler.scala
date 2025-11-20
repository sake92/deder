package ba.sake.deder.zinc

import sbt.internal.inc.consistent.ConsistentFileAnalysisStore

import java.io.File
import java.util.Optional
import java.util.function.Supplier
import sbt.internal.inc.{FileAnalysisStore, PlainVirtualFileConverter, ZincUtil}
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{
  AnalysisContents,
  ClasspathOptionsUtil,
  CompileAnalysis,
  CompileOptions,
  CompileOrder,
  CompileProgress,
  CompilerCache,
  DefinesClass,
  GlobalsCache,
  IncOptions,
  PerClasspathEntryLookup,
  PreviousResult,
  Setup
}
import sbt.internal.inc.ScalaInstance
import java.nio.file.Path

object ZincCompiler {
  def apply(compilerBridgeJar: os.Path): ZincCompiler =
    new ZincCompiler(compilerBridgeJar)
}

class ZincCompiler(compilerBridgeJar: os.Path) {

  private val incrementalCompiler = ZincUtil.defaultIncrementalCompiler

  def compile(
      scalaVersion: String,
      scalaCompilerJar: os.Path,
      scalaLibraryJars: Seq[os.Path],
      scalaReflectJar: Option[os.Path],
      zincCacheFile: os.Path,
      sources: Seq[os.Path],
      classesDir: os.Path,
      scalacOptions: Seq[String],
      javacOptions: Seq[String]
  ): Unit = {

    val classloader = this.getClass.getClassLoader

    val scalaInstance = new ScalaInstance(
      version = scalaVersion,
      loader = classloader,
      loaderCompilerOnly = classloader,
      loaderLibraryOnly = classloader,
      libraryJars = scalaLibraryJars.map(_.toIO).toArray,
      compilerJars = (Array(scalaCompilerJar) ++ scalaReflectJar).map(_.toIO),
      allJars = (Array(scalaCompilerJar) ++ scalaLibraryJars ++ scalaReflectJar).map(_.toIO),
      explicitActual = Some(scalaVersion)
    )

    val classpathOptions = ClasspathOptionsUtil.auto()
    val scalaCompiler = ZincUtil.scalaCompiler(scalaInstance, compilerBridgeJar.toIO, classpathOptions)
    val compilers = ZincUtil.compilers(scalaInstance, classpathOptions, javaHome = None, scalac = scalaCompiler)

    val converter = PlainVirtualFileConverter.converter

    val sourcesVFs = sources.map(s => converter.toVirtualFile(s.toNIO)).toArray
    val classpath = scalaLibraryJars.map(f => converter.toVirtualFile(f.toNIO)).toArray

    val compileOptions = CompileOptions.of(
      /*_classpath =*/ classpath,
      /*_sources =*/ sourcesVFs,
      /*_classesDirectory =*/ classesDir.toNIO,
      /*_scalacOptions =*/ scalacOptions.toArray,
      /*_javacOptions =*/ javacOptions.toArray,
      /*_maxErrors =*/ 100,
      /*_sourcePositionMapper =*/ null,
      /*_order =*/ CompileOrder.Mixed
    )

    val analysisStore = ConsistentFileAnalysisStore.binary(
      file = zincCacheFile.toIO,
      mappers = ReadWriteMappers.getEmptyMappers,
      reproducible = true,
      // No need to utilize more than 8 cores to serialize a small file
      parallelism = math.min(Runtime.getRuntime.availableProcessors(), 8)
    )
    val previousResult = locally {
      val previous = analysisStore.get()
      PreviousResult.of(
        previous.map(_.getAnalysis),
        previous.map(_.getMiniSetup)
      )
    }

    val setup = getSetup(zincCacheFile.toNIO)
    val inputs = xsbti.compile.Inputs.of(compilers, compileOptions, setup, previousResult)

    val newResult = incrementalCompiler.compile(
      inputs,
      new DederZincLogger
    )

    analysisStore.set(AnalysisContents.create(newResult.analysis(), newResult.setup()))
  }

  class DederZincLogger extends xsbti.Logger {

    override def error(msg: Supplier[String]): Unit = println(s"[error] ${msg.get()}")

    override def warn(msg: Supplier[String]): Unit = println(s"[warn] ${msg.get()}")

    override def info(msg: Supplier[String]): Unit = println(s"[info] ${msg.get()}")

    override def debug(msg: Supplier[String]): Unit = () // println(s"[debug] ${msg.get()}")

    override def trace(exception: Supplier[Throwable]): Unit = () // println(s"[trace] ${exception.get()}")
  }

  private def getSetup(cacheFile: Path): Setup = {
    val perClasspathEntryLookup: PerClasspathEntryLookup = new PerClasspathEntryLookup {
      override def analysis(x$0: xsbti.VirtualFile): java.util.Optional[CompileAnalysis] =
        Optional.empty[CompileAnalysis]

      override def definesClass(x$0: xsbti.VirtualFile): DefinesClass = (className: String) => true
    }

    val skip: Boolean = false
    val cache: GlobalsCache = CompilerCache.getDefault
    val incOptions: IncOptions = IncOptions.of()
    val reporter = xsbti.ReporterUtil.getDefault(xsbti.ReporterUtil.getDefaultReporterConfig)
    val compileProgress = new CompileProgress {
      // TODO
    }

    Setup.of(
      perClasspathEntryLookup,
      skip,
      cacheFile,
      cache,
      incOptions,
      reporter,
      compileProgress,
      Array.empty[xsbti.T2[String, String]] // extra
    )
  }
}
