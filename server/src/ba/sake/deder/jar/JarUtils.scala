package ba.sake.deder.jar

import java.io.BufferedOutputStream
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import scala.collection.mutable

// stolen from Mill
object JarUtils {
  
  /**
   * Create a JAR file with default inflation level.
   *
   * @param jar         The final JAR file
   * @param inputPaths  The input paths resembling the content of the JAR file.
   *                    Files will be directly included in the root of the archive,
   *                    whereas for directories their content is added to the root of the archive.
   * @param manifest    The JAR Manifest
   * @param fileFilter  A filter to support exclusions of selected files
   * @param includeDirs If `true` the JAR archive will contain directory entries.
   *                    According to the ZIP specification, directory entries are not required.
   *                    In the Java ecosystem, most JARs have directory entries, so including them may reduce compatibility issues.
   *                    Directory entry names will result with a trailing `/`.
   * @param timestamp   If specified, this timestamp is used as modification timestamp (mtime) for all entries in the JAR file.
   *                    Having a stable timestamp may result in reproducible files, if all other content, including the JAR Manifest, keep stable.
   */
  def create(
                 jar: os.Path,
                 inputPaths: Seq[os.Path],
                 manifest: JarManifest = JarManifest.Default,
                 fileFilter: (os.Path, os.RelPath) => Boolean = (_, _) => true,
                 includeDirs: Boolean = true,
                 timestamp: Option[Long] = None
               ): os.Path = {

    val curTime = timestamp.getOrElse(System.currentTimeMillis())

    def mTime(file: os.Path) = timestamp.getOrElse(os.mtime(file))

    os.makeDir.all(jar / os.up)
    os.remove.all(jar)

    val seen = mutable.Set.empty[os.RelPath]
    val _ = seen.add(os.sub / "META-INF/MANIFEST.MF")

    val jarStream = new JarOutputStream(
      new BufferedOutputStream(Files.newOutputStream(jar.toNIO)),
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
      jar
    } finally {
      jarStream.close()
    }
  }
}
