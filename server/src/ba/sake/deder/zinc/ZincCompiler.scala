package ba.sake.deder.zinc

import sbt.internal.inc.consistent.ConsistentFileAnalysisStore

import java.io.File
import java.util.Optional
import java.util.function.Supplier
import sbt.internal.inc.{FileAnalysisStore, PlainVirtualFileConverter, ZincUtil}
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{AnalysisContents, ClasspathOptionsUtil, CompileAnalysis, CompileOptions, CompileOrder, CompileProgress, CompilerCache, DefinesClass, GlobalsCache, IncOptions, PerClasspathEntryLookup, Setup}

import java.nio.file.Path

object ZincCompiler {
  def compile(): Unit = {

    val classloader = this.getClass.getClassLoader
    val scalaCompilerJar = os.pwd / "scala-compiler-2.13.17.jar"
    val scalaLibraryJar = os.pwd / "scala-library-2.13.17.jar"
    val scalaReflectJar = os.pwd / "scala-reflect-2.13.17.jar"
    val scalaInstance = new sbt.internal.inc.ScalaInstance(
      version = "2.13.17",
      loader = classloader,
      loaderCompilerOnly = classloader,
      loaderLibraryOnly = classloader,
      libraryJars = Array(scalaLibraryJar.toIO),
      compilerJars = Array(scalaCompilerJar.toIO, scalaReflectJar.toIO),
      allJars = Array(scalaLibraryJar.toIO, scalaCompilerJar.toIO, scalaReflectJar.toIO),
      explicitActual = Some("2.13.17")
    )

    val classpathOptions = ClasspathOptionsUtil.auto()
    val compilerBridgeJar = (os.pwd / "compiler-bridge_2.13-1.11.0.jar").toIO
    val scalaCompiler = ZincUtil.scalaCompiler(scalaInstance, compilerBridgeJar, classpathOptions)
    val compilers = ZincUtil.compilers(scalaInstance, classpathOptions, javaHome = None, scalac = scalaCompiler)

    val converter = PlainVirtualFileConverter.converter
    val scalaFiles = os.walk(os.pwd / "d/src/scala", skip = p => {
      os.isFile(p) && p.ext != "scala"
    })
    val sources = scalaFiles.toArray.map(p => converter.toVirtualFile(p.toNIO))
    val classpath = Array(scalaLibraryJar).map(f => converter.toVirtualFile(f.toNIO))
    val classesDir = (os.pwd / "out_deder/zinc/classes").toNIO

    val compileOptions = CompileOptions.of(
      /*_classpath =*/ classpath,
      /*_sources =*/ sources,
      /*_classesDirectory =*/ classesDir,
      /*_scalacOptions =*/ Array.empty[String],
      /*_javacOptions =*/ Array.empty[String],
      /*_maxErrors =*/ 100,
      /*_sourcePositionMapper =*/ null,
      /*_order =*/ CompileOrder.Mixed
    )
    val zincCacheFile = os.pwd / "out_deder/zinc/inc_compile.zip"
    val setup = getSetup(zincCacheFile.toNIO)
    val previousResult = xsbti.compile.PreviousResult.of(Optional.empty(), Optional.empty())
    val inputs = xsbti.compile.Inputs.of(compilers, compileOptions, setup, previousResult)

    val incrementalCompiler = ZincUtil.defaultIncrementalCompiler
    val newResult = incrementalCompiler.compile(
      inputs,
      new DederZincLogger
    )

    val analysisStore = ConsistentFileAnalysisStore.binary(
      file = zincCacheFile.toIO,
      mappers = ReadWriteMappers.getEmptyMappers,
      reproducible = true,
      // No need to utilize more than 8 cores to serialize a small file
      parallelism = math.min(Runtime.getRuntime.availableProcessors(), 8)
    )
    analysisStore.set(AnalysisContents.create(newResult.analysis(), newResult.setup()))

    println(s"compile finished: " + newResult)

  }

  class DederZincLogger extends xsbti.Logger {

    override def error(msg: Supplier[String]): Unit = println(s"[error] ${msg.get()}")

    override def warn(msg: Supplier[String]): Unit = println(s"[warn] ${msg.get()}")

    override def info(msg: Supplier[String]): Unit = println(s"[info] ${msg.get()}")

    override def debug(msg: Supplier[String]): Unit = println(s"[debug] ${msg.get()}")

    override def trace(exception: Supplier[Throwable]): Unit = println(s"[trace] ${exception.get()}")
  }

  private def getSetup(cacheFile: Path): Setup = {
    val perClasspathEntryLookup: PerClasspathEntryLookup = new PerClasspathEntryLookup {
      override def analysis(x$0: xsbti.VirtualFile): java.util.Optional[CompileAnalysis] = Optional.empty[CompileAnalysis]

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
