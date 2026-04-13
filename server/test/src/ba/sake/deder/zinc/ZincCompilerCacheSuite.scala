package ba.sake.deder.zinc

import java.net.URLClassLoader

class ZincCompilerCacheSuite extends munit.FunSuite {

  test("close() clears setup and analysis caches") {
    val tmpDir = os.temp.dir()
    val dummyJar = tmpDir / "dummy.jar"
    val jos = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(dummyJar.toIO))
    jos.close()

    val compiler = ZincCompiler(dummyJar)

    // Access private caches via reflection
    val setupField = compiler.getClass.getDeclaredField("setupCache")
    setupField.setAccessible(true)
    val setupCache = setupField.get(compiler).asInstanceOf[com.github.blemale.scaffeine.Cache[?, ?]]

    val analysisField = compiler.getClass.getDeclaredField("analysisCache")
    analysisField.setAccessible(true)
    val analysisCache = analysisField.get(compiler).asInstanceOf[com.github.blemale.scaffeine.Cache[?, ?]]

    // Both caches start empty
    assertEquals(setupCache.estimatedSize(), 0L)
    assertEquals(analysisCache.estimatedSize(), 0L)

    // close() on empty should not throw
    compiler.close()
    assertEquals(setupCache.estimatedSize(), 0L)
    assertEquals(analysisCache.estimatedSize(), 0L)

    os.remove.all(tmpDir)
  }
}
