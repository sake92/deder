package ba.sake.deder.zinc

import java.nio.file.Path
import xsbti.VirtualFileRef
import xsbti.compile.MiniSetup
import xsbti.compile.analysis.{ReadMapper, ReadWriteMappers, Stamp, WriteMapper}

/** Zinc analysis mappers that make `inc_compile.zip` independent of the project root location.
  *
  * Zinc's built-in machine-independent mappers relativize paths, but leave compiler options untouched.
  * Options such as `-sourceroot` / `-semanticdb-target` contain the absolute project root, and Zinc
  * compares stored vs current options to decide on a full recompile. So we also swap the root for a
  * placeholder in options when writing, and back to the current root when reading.
  */
object RelocatableMappers {

  private val Placeholder = "${PROJECT_ROOT}"

  def apply(projectRoot: Path): ReadWriteMappers = {
    val root = projectRoot.toString
    val read = ReadMapper.getMachineIndependentMapper(projectRoot)
    val write = WriteMapper.getMachineIndependentMapper(projectRoot)
    new ReadWriteMappers(new RootReadMapper(read, root), new RootWriteMapper(write, root))
  }

  private class RootWriteMapper(d: WriteMapper, root: String) extends WriteMapper {
    def mapSourceFile(f: VirtualFileRef): VirtualFileRef = d.mapSourceFile(f)
    def mapBinaryFile(f: VirtualFileRef): VirtualFileRef = d.mapBinaryFile(f)
    def mapProductFile(f: VirtualFileRef): VirtualFileRef = d.mapProductFile(f)
    def mapOutputDir(p: Path): Path = d.mapOutputDir(p)
    def mapSourceDir(p: Path): Path = d.mapSourceDir(p)
    def mapClasspathEntry(p: Path): Path = d.mapClasspathEntry(p)
    def mapJavacOption(o: String): String = o.replace(root, Placeholder)
    def mapScalacOption(o: String): String = o.replace(root, Placeholder)
    def mapProductStamp(f: VirtualFileRef, s: Stamp): Stamp = d.mapProductStamp(f, s)
    def mapSourceStamp(f: VirtualFileRef, s: Stamp): Stamp = d.mapSourceStamp(f, s)
    def mapBinaryStamp(f: VirtualFileRef, s: Stamp): Stamp = d.mapBinaryStamp(f, s)
    def mapMiniSetup(m: MiniSetup): MiniSetup = d.mapMiniSetup(m)
  }

  private class RootReadMapper(d: ReadMapper, root: String) extends ReadMapper {
    def mapSourceFile(f: VirtualFileRef): VirtualFileRef = d.mapSourceFile(f)
    def mapBinaryFile(f: VirtualFileRef): VirtualFileRef = d.mapBinaryFile(f)
    def mapProductFile(f: VirtualFileRef): VirtualFileRef = d.mapProductFile(f)
    def mapOutputDir(p: Path): Path = d.mapOutputDir(p)
    def mapSourceDir(p: Path): Path = d.mapSourceDir(p)
    def mapClasspathEntry(p: Path): Path = d.mapClasspathEntry(p)
    def mapJavacOption(o: String): String = o.replace(Placeholder, root)
    def mapScalacOption(o: String): String = o.replace(Placeholder, root)
    def mapProductStamp(f: VirtualFileRef, s: Stamp): Stamp = d.mapProductStamp(f, s)
    def mapSourceStamp(f: VirtualFileRef, s: Stamp): Stamp = d.mapSourceStamp(f, s)
    def mapBinaryStamp(f: VirtualFileRef, s: Stamp): Stamp = d.mapBinaryStamp(f, s)
    def mapMiniSetup(m: MiniSetup): MiniSetup = d.mapMiniSetup(m)
  }
}
