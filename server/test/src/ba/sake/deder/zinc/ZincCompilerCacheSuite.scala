package ba.sake.deder.zinc

import java.net.URLClassLoader
import ba.sake.deder.CacheStatsRegistry

class ZincCompilerCacheSuite extends munit.FunSuite {

  test("close() clears setup cache") {
    val tmpDir = os.temp.dir()
    val dummyJar = tmpDir / "dummy.jar"
    val jos = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(dummyJar.toIO))
    jos.close()

    val compiler = ZincCompiler(dummyJar, CacheStatsRegistry(), "3.7.0")

    // Access private caches via reflection
    val setupField = compiler.getClass.getDeclaredField("setupCache")
    setupField.setAccessible(true)
    val setupCache = setupField.get(compiler).asInstanceOf[com.github.blemale.scaffeine.Cache[?, ?]]

    // Starts empty
    assertEquals(setupCache.estimatedSize(), 0L)

    // close() on empty should not throw
    compiler.close()
    assertEquals(setupCache.estimatedSize(), 0L)

    os.remove.all(tmpDir)
  }
}
