package ba.sake.deder.jvm

import munit.FunSuite
import java.nio.file.{Files, Path}

class JarUtilsSuite extends FunSuite {

  test("resolveShadeRules handles null/empty rules file paths") {
    val result = JarUtils.resolveShadeRules(None, os.Path("/module/root"))
    assertEquals(result, Seq())
  }

  test("resolveShadeRules resolves relative path from module root") {
    val tmpDir = Files.createTempDirectory("shade-test")
    val moduleRoot = os.Path(tmpDir)
    val rulesFile = moduleRoot / "shade.rules"
    os.write(rulesFile, "rule ba.sake.tupson.** ba.sake.deder.shaded.tupson.@1\n")
    
    val result = JarUtils.resolveShadeRules(Some("shade.rules"), moduleRoot)
    assert(result.nonEmpty, "Should load shade rules from relative path")
    
    // Cleanup
    os.remove.all(moduleRoot)
  }

  test("resolveShadeRules resolves absolute path directly") {
    val tmpFile = Files.createTempFile("shade", ".rules")
    Files.write(tmpFile, "rule ba.sake.tupson.** ba.sake.deder.shaded.tupson.@1\n".getBytes)
    
    val result = JarUtils.resolveShadeRules(Some(tmpFile.toString), os.Path("/ignored/root"))
    assert(result.nonEmpty, "Should load shade rules from absolute path")
    
    // Cleanup
    Files.delete(tmpFile)
  }
}
