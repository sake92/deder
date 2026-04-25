package ba.sake.deder.testing

import javax.xml.parsers.DocumentBuilderFactory

class JUnitXmlReportWriterSuite extends munit.FunSuite {

  test("writes one JUnit XML file per suite") {
    val reportDir = os.temp.dir(prefix = "deder-junit-xml-")
    try {
      val results = DederTestResults(
        total = 3,
        passed = 1,
        failed = 1,
        errors = 0,
        skipped = 1,
        duration = 35,
        failedTestNames = Seq("demo.FailingSuite#fails"),
        suites = Seq(
          DederTestSuiteReport(
            name = "demo.FailingSuite",
            testCases = Seq(
              DederTestCaseReport("passes", "demo.FailingSuite", DederTestStatus.Success, 10),
              DederTestCaseReport(
                "fails",
                "demo.FailingSuite",
                DederTestStatus.Failure,
                20,
                Some(DederTestFailure(Some("boom"), Some("stacktrace")))
              )
            ),
            duration = 30,
            systemOut = Some("hello from suite")
          ),
          DederTestSuiteReport(
            name = "demo.SkippedSuite",
            testCases = Seq(
              DederTestCaseReport("skipped", "demo.SkippedSuite", DederTestStatus.Skipped, 5)
            ),
            duration = 5
          )
        )
      )

      JUnitXmlReportWriter.writeReports(results, reportDir)

      val files = os.list(reportDir).map(_.last).sorted
      assertEquals(files, Seq("TEST-demo.FailingSuite.xml", "TEST-demo.SkippedSuite.xml"))

      val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse((reportDir / files.head).toIO)
      val suite = doc.getDocumentElement
      assertEquals(suite.getTagName, "testsuite")
      assertEquals(suite.getAttribute("tests"), "2")
      assertEquals(suite.getAttribute("failures"), "1")
      assertEquals(suite.getAttribute("errors"), "0")
      assertEquals(suite.getAttribute("skipped"), "0")
      assertEquals(doc.getElementsByTagName("failure").item(0).getAttributes.getNamedItem("message").getNodeValue, "boom")
      assertEquals(doc.getElementsByTagName("system-out").item(0).getTextContent, "hello from suite")
    } finally {
      os.remove.all(reportDir)
    }
  }
}
