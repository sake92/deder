package ba.sake.deder.zinc

import java.io.File
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import sbt.internal.inc.{Analysis, MappedFileConverter, SourceInfos}
import sbt.util.InterfaceUtil
import xsbti.{Problem, VirtualFileRef}
import xsbti.compile.CompileAnalysis

/** Zinc 1.11 maps source-info keys, but serializes diagnostic positions without its path mappers. Keep those positions
  * in the same virtual-file coordinates as their source-info keys on disk.
  */
private[zinc] final class RelocatableAnalysis(converter: MappedFileConverter) {

  def write(analysis: CompileAnalysis): CompileAnalysis =
    mapProblems(analysis, file => new File(converter.toVirtualFile(converter.toPath(file)).id()))

  def read(analysis: CompileAnalysis): CompileAnalysis =
    mapProblems(analysis, file => converter.toPath(file).toFile)

  private def mapProblems(analysis: CompileAnalysis, sourceFile: VirtualFileRef => File): CompileAnalysis =
    analysis match {
      case a: Analysis =>
        val infos = a.infos.allInfos.map { (file, info) =>
          val target = sourceFile(file)
          file -> SourceInfos.makeInfo(
            info.getReportedProblems.toSeq.map(mapProblem(_, target)),
            info.getUnreportedProblems.toSeq.map(mapProblem(_, target)),
            info.getMainClasses.toSeq
          )
        }
        a.copy(infos = SourceInfos.of(infos))
      case _ => analysis
    }

  private def mapProblem(problem: Problem, target: File): Problem = {
    val pos = problem.position()
    val oldPath = pos.sourcePath().toScala.orElse(pos.sourceFile().toScala.map(_.getPath))
    val position = InterfaceUtil.position(
      pos.line().toScala,
      pos.lineContent(),
      pos.offset().toScala,
      pos.pointer().toScala,
      pos.pointerSpace().toScala,
      pos.sourcePath().toScala.map(_ => target.getPath),
      pos.sourceFile().toScala.map(_ => target),
      pos.startOffset().toScala,
      pos.endOffset().toScala,
      pos.startLine().toScala,
      pos.startColumn().toScala,
      pos.endLine().toScala,
      pos.endColumn().toScala
    )
    InterfaceUtil.problem(
      problem.category(),
      position,
      problem.message(),
      problem.severity(),
      problem.rendered().toScala.map(text => oldPath.fold(text)(old => text.replace(old, target.getPath))),
      problem.diagnosticCode().toScala,
      problem.diagnosticRelatedInformation().asScala.toList,
      problem.actions().asScala.toList
    )
  }
}
