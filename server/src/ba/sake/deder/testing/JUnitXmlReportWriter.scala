package ba.sake.deder.testing

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import javax.xml.stream.{XMLOutputFactory, XMLStreamWriter}
import scala.util.Using
import ba.sake.deder.DederPath
import ba.sake.deder.config.DederProject.{DederModule, JavaTestModule, JUnitXmlReportSettings, ScalaJsTestModule, ScalaNativeTestModule, ScalaTestModule}

object JUnitXmlReportWriter {

  def outputDir(module: DederModule, taskOutDir: os.Path): Option[os.Path] =
    settings(module).filter(_.enabled).map { cfg =>
      Option(cfg.outputDir).filter(_.nonEmpty) match {
        case None => taskOutDir / "reports" / "junit"
        case Some(raw) if Paths.get(raw).isAbsolute => os.Path(raw)
        case Some(raw)                              => DederPath(s"${module.root}/$raw").absPath
      }
    }

  def writeReports(results: DederTestResults, outputDir: os.Path): Unit = {
    if os.exists(outputDir) then os.remove.all(outputDir)
    os.makeDir.all(outputDir)
    val usedNames = scala.collection.mutable.Set[String]()
    results.suites.sortBy(_.name).foreach { suite =>
      val fileName = nextFileName(suite.name, usedNames)
      writeSuiteReport(suite, outputDir / fileName)
    }
  }

  private def settings(module: DederModule): Option[JUnitXmlReportSettings] = module match {
    case m: JavaTestModule        => Some(m.junitXmlReport)
    case m: ScalaTestModule       => Some(m.junitXmlReport)
    case m: ScalaJsTestModule     => Some(m.junitXmlReport)
    case m: ScalaNativeTestModule => Some(m.junitXmlReport)
    case _                        => None
  }

  private def nextFileName(suiteName: String, usedNames: collection.mutable.Set[String]): String = {
    val base = sanitizeFileName(suiteName)
    Iterator
      .single(s"TEST-$base.xml")
      .concat(LazyList.from(2).map(i => s"TEST-$base-$i.xml"))
      .find(name => !usedNames.contains(name))
      .map { name =>
        usedNames += name
        name
      }
      .get
  }

  private def sanitizeFileName(name: String): String = {
    val sanitized = name.replaceAll("[^A-Za-z0-9._-]+", "_").stripPrefix(".").stripSuffix(".")
    if sanitized.nonEmpty then sanitized else "suite"
  }

  private def writeSuiteReport(suite: DederTestSuiteReport, path: os.Path): Unit =
    Using.resource(os.write.outputStream(path, createFolders = true)) { out =>
      val writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out, StandardCharsets.UTF_8.name())
      try {
        writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0")
        writer.writeStartElement("testsuite")
        writeAttr(writer, "name", suite.name)
        writeAttr(writer, "tests", suite.total.toString)
        writeAttr(writer, "failures", suite.failed.toString)
        writeAttr(writer, "errors", suite.errors.toString)
        writeAttr(writer, "skipped", suite.skipped.toString)
        writeAttr(writer, "time", formatDurationSeconds(suite.duration))
        suite.testCases.foreach { testCase =>
          writer.writeStartElement("testcase")
          writeAttr(writer, "name", testCase.name)
          writeAttr(writer, "classname", testCase.classname)
          writeAttr(writer, "time", formatDurationSeconds(testCase.duration))
          testCase.status match {
            case DederTestStatus.Failure =>
              writeProblem(writer, "failure", testCase.failure)
            case DederTestStatus.Error =>
              writeProblem(writer, "error", testCase.failure)
            case DederTestStatus.Skipped =>
              writer.writeEmptyElement("skipped")
            case DederTestStatus.Success =>
          }
          writer.writeEndElement()
        }
        suite.systemOut.filter(_.nonEmpty).foreach { text =>
          writer.writeStartElement("system-out")
          writer.writeCData(text)
          writer.writeEndElement()
        }
        suite.systemErr.filter(_.nonEmpty).foreach { text =>
          writer.writeStartElement("system-err")
          writer.writeCData(text)
          writer.writeEndElement()
        }
        writer.writeEndElement()
        writer.writeEndDocument()
      } finally {
        writer.close()
      }
    }

  private def writeProblem(
      writer: XMLStreamWriter,
      elementName: String,
      failure: Option[DederTestFailure]
  ): Unit = {
    writer.writeStartElement(elementName)
    failure.flatMap(_.message).foreach(msg => writeAttr(writer, "message", msg))
    writer.writeCData(
      failure.flatMap(_.stackTrace).orElse(failure.flatMap(_.message)).getOrElse(elementName)
    )
    writer.writeEndElement()
  }

  private def formatDurationSeconds(durationMs: Long): String =
    f"${math.max(0L, durationMs).toDouble / 1000d}%.3f"

  private def writeAttr(writer: XMLStreamWriter, name: String, value: String): Unit =
    writer.writeAttribute(name, value)
}
