package ba.sake.deder.jar

import java.io.BufferedOutputStream
import java.io._
import java.util.jar._
import scala.collection.mutable
import java.nio.file.{Files, Path}
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import scala.collection.mutable
import com.eed3si9n.jarjarabrams.{ShadeRule, Shader}

object JarUtils {

  // stolen from Mill
  /** Create a JAR file with default inflation level.
    *
    * @param resultJarPath
    *   The final JAR file path
    * @param inputPaths
    *   The input paths resembling the content of the JAR file. Files will be directly included in the root of the
    *   archive, whereas for directories their content is added to the root of the archive.
    * @param manifest
    *   The JAR Manifest
    * @param fileFilter
    *   A filter to support exclusions of selected files
    * @param includeDirs
    *   If `true` the JAR archive will contain directory entries. According to the ZIP specification, directory entries
    *   are not required. In the Java ecosystem, most JARs have directory entries, so including them may reduce
    *   compatibility issues. Directory entry names will result with a trailing `/`.
    * @param timestamp
    *   If specified, this timestamp is used as modification timestamp (mtime) for all entries in the JAR file. Having a
    *   stable timestamp may result in reproducible files, if all other content, including the JAR Manifest, keep
    *   stable.
    */
  def createJar(
      resultJarPath: os.Path,
      inputPaths: Seq[os.Path],
      manifest: JarManifest = JarManifest.Default,
      fileFilter: (os.Path, os.RelPath) => Boolean = (_, _) => true,
      includeDirs: Boolean = true,
      timestamp: Option[Long] = None
  ): Unit = {
    val curTime = timestamp.getOrElse(System.currentTimeMillis())
    def mTime(file: os.Path) = timestamp.getOrElse(os.mtime(file))
    os.makeDir.all(resultJarPath / os.up)
    os.remove.all(resultJarPath)
    val seen = mutable.Set.empty[os.RelPath]
    val _ = seen.add(os.sub / "META-INF/MANIFEST.MF")
    val jarStream = new JarOutputStream(
      new BufferedOutputStream(Files.newOutputStream(resultJarPath.toNIO)),
      manifest.build
    )
    try {
      assert(inputPaths.iterator.forall(os.exists(_)))
      if (includeDirs) {
        val _ = seen.add(os.sub / "META-INF")
        val entry = new JarEntry("META-INF/")
        entry.setTime(curTime)
        jarStream.putNextEntry(entry)
        jarStream.closeEntry()
      }
      // Note: we only sort each input path, but not the whole archive
      for {
        p <- inputPaths
        (file, mapping) <-
          if (os.isFile(p)) Seq((p, os.sub / p.last))
          else os.walk(p).map(sub => (sub, sub.subRelativeTo(p))).sorted
        if (includeDirs || os.isFile(file)) && !seen(mapping) && fileFilter(p, mapping)
      } {
        val _ = seen.add(mapping)
        val name = mapping.toString() + (if (os.isDir(file)) "/" else "")
        val entry = new JarEntry(name)
        entry.setTime(mTime(file))
        jarStream.putNextEntry(entry)
        if (os.isFile(file)) jarStream.write(os.read.bytes(file))
        jarStream.closeEntry()
      }
    } finally {
      jarStream.close()
    }
  }

  def mergeJars(resultJarPath: os.Path, jarPaths: Seq[os.Path], manifest: JarManifest): Unit = {
    val out = new JarOutputStream(Files.newOutputStream(resultJarPath.toNIO), manifest.build)
    val seenEntries = mutable.Set[String]()
    def skipEntry(name: String) = name.endsWith("MANIFEST.MF")
    // map of concatenated files (like services)
    val concatBuffers = mutable.Map[String, ByteArrayOutputStream]()
    for jarPath <- jarPaths do {
      val jin = new JarInputStream(Files.newInputStream(jarPath.toNIO))
      var entry = jin.getNextJarEntry
      while (entry != null) {
        val name = entry.getName
        if skipEntry(name) then {
          ()
        } else if (name.startsWith("META-INF/services/")) {
          // STRATEGY: CONCATENATE
          val baos = concatBuffers.getOrElseUpdate(name, new ByteArrayOutputStream())
          jin.transferTo(baos)
          baos.write("\n".getBytes) // Ensure a newline between merges
        } else if (!seenEntries.contains(name)) {
          // STRATEGY: FIRST-WINS / DEDUPLICATE
          out.putNextEntry(new JarEntry(name))
          jin.transferTo(out)
          out.closeEntry()
          seenEntries += name
        }
        entry = jin.getNextJarEntry
      }
      jin.close()
    }

    // write out all the concatenated files
    for ((name, baos) <- concatBuffers) {
      out.putNextEntry(new JarEntry(name))
      out.write(baos.toByteArray)
      out.closeEntry()
    }

    out.close()
  }

  def createAssemblyJar(resultJarPath: os.Path, mergedJar: os.Path): Unit = {
    val shadeRules = Seq()
    Shader.shadeFile(
      shadeRules,
      mergedJar.toNIO,
      resultJarPath.toNIO,
      verbose = false,
      skipManifest = true,
      resetTimestamp = false,
      warnOnDuplicateClass = true
    )

  }
}
