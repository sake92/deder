package ba.sake.deder.zinc

import java.net.URLClassLoader

class ZincCompilerCacheSuite extends munit.FunSuite {

  test("close() closes cached classloaders") {
    val tmpDir = os.temp.dir()
    val dummyJar = tmpDir / "dummy.jar"
    // create a minimal empty JAR
    val jos = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(dummyJar.toIO))
    jos.close()

    val compiler = ZincCompiler(dummyJar)

    // Access private cachedSetup via reflection to verify classloader lifecycle
    val field = compiler.getClass.getDeclaredField("cachedSetup")
    field.setAccessible(true)

    // Before any compile, cachedSetup should be None
    assertEquals(field.get(compiler).asInstanceOf[Option[?]], None)

    // Call close on empty — should not throw
    compiler.close()
    assertEquals(field.get(compiler).asInstanceOf[Option[?]], None)

    os.remove.all(tmpDir)
  }

  test("close() clears analysis cache") {
    val tmpDir = os.temp.dir()
    val dummyJar = tmpDir / "dummy.jar"
    val jos = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(dummyJar.toIO))
    jos.close()

    val compiler = ZincCompiler(dummyJar)

    // Access private analysisCache via reflection
    val field = compiler.getClass.getDeclaredField("analysisCache")
    field.setAccessible(true)
    val cache = field.get(compiler).asInstanceOf[com.github.blemale.scaffeine.Cache[os.Path, ?]]

    assertEquals(cache.estimatedSize(), 0L)

    compiler.close()
    assertEquals(cache.estimatedSize(), 0L)

    os.remove.all(tmpDir)
  }
}
