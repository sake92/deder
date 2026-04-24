package ba.sake.deder.config

class ConfigClasspathSuite extends munit.FunSuite {

  test("forked test classpath includes Pkl mapper metadata for DederProject") {
    val resourcePath = "META-INF/org/pkl/config/java/mapper/classes/ba.sake.deder.config.DederProject.properties"
    val resource = getClass.getClassLoader.getResource(resourcePath)
    assert(resource != null, s"missing classpath resource: $resourcePath")
  }
}
