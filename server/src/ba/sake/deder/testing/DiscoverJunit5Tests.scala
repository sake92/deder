package ba.sake.deder.testing

// stolen from Mill
// https://github.com/com-lihaoyi/mill/blob/11b3e8867f4c61ba2d5ec20a164fa9bc12445ef2/libs/javalib/testrunner/src/mill/javalib/testrunner/DiscoverJunit5Tests.scala#L5
object DiscoverJunit5Tests {

  def discover(classLoader: ClassLoader, classesDir: os.Path, testCp: Seq[os.Path]): Seq[String] = {
    val builderClass: Class[?] =
      classLoader.loadClass("com.github.sbt.junit.jupiter.api.JupiterTestCollector$Builder")
    val builder = builderClass.getConstructor().newInstance()

    builderClass
      .getMethod("withClassDirectory", classOf[java.io.File])
      .invoke(
        builder,
        classesDir.wrapped.toFile
      )

    builderClass
      .getMethod("withRuntimeClassPath", classOf[Array[java.net.URL]])
      .invoke(
        builder,
        testCp.map(_.toURL).toArray
      )
    builderClass.getMethod("withClassLoader", classOf[ClassLoader]).invoke(builder, classLoader)

    val testCollector = builderClass.getMethod("build").invoke(builder)
    val testCollectorClass =
      classLoader.loadClass("com.github.sbt.junit.jupiter.api.JupiterTestCollector")

    val result = testCollectorClass.getMethod("collectTests").invoke(testCollector)
    val resultClass =
      classLoader.loadClass("com.github.sbt.junit.jupiter.api.JupiterTestCollector$Result")

    val items = resultClass
      .getMethod("getDiscoveredTests")
      .invoke(result)
      .asInstanceOf[java.util.List[?]]
    val itemClass =
      classLoader.loadClass("com.github.sbt.junit.jupiter.api.JupiterTestCollector$Item")

    import scala.jdk.CollectionConverters.*
    items.asScala.map { item =>
      itemClass.getMethod("getFullyQualifiedClassName").invoke(item).asInstanceOf[String]
    }.toSeq
  }
}
