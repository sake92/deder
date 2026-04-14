package ba.sake.deder

import java.net.URLClassLoader
import scala.util.Using

object ClassLoaderUtils {

  def withClassLoader[T](
      classPath: Seq[os.Path],
      parent: ClassLoader = getClass.getClassLoader
  )(f: ClassLoader => T): T = {
    // Disable jar URL caching so each URLClassLoader gets its own JarFile instances.
    // Without this, the JVM shares JarFile instances across classloaders via JarURLConnection cache,
    // and closing one classloader closes JarFiles still in use by concurrent classloaders.
    java.net.URLConnection.setDefaultUseCaches("jar", false)
    val oldClassloader = Thread.currentThread().getContextClassLoader
    val urls = classPath.map(_.toURI.toURL).toArray
    Using.resource(new URLClassLoader(urls, parent)) { newClassloader =>
      Thread.currentThread().setContextClassLoader(newClassloader)
      try f(newClassloader)
      finally Thread.currentThread().setContextClassLoader(oldClassloader)
    }
  }
  
  /** Creates an isolated classloader that only delegates specified package prefixes to the app classloader, preventing
    * Deder's own dependencies from leaking into user code. Everything else is loaded from the given classpath only.
    */
  def withIsolatedClassLoader[T](
      classPath: Seq[os.Path],
      sharedPrefixes: Seq[String]
  )(f: ClassLoader => T): T = {
    val appClassLoader = getClass.getClassLoader
    val bridgeClassLoader = new ClassLoader(ClassLoader.getPlatformClassLoader) {
      override def loadClass(name: String, resolve: Boolean): Class[?] = {
        if (sharedPrefixes.exists(name.startsWith)) {
          val c = appClassLoader.loadClass(name)
          if (resolve) resolveClass(c)
          c
        } else {
          super.loadClass(name, resolve)
        }
      }

      override def getResource(name: String): java.net.URL = {
        val pathPrefixes = sharedPrefixes.map(_.replace('.', '/'))
        if (pathPrefixes.exists(name.startsWith)) {
          appClassLoader.getResource(name)
        } else {
          super.getResource(name)
        }
      }
    }
    withClassLoader(classPath, parent = bridgeClassLoader)(f)
  }

  // sbt.testing.* interfaces must be shared between Deder and the test classloader,
  // since Deder casts loaded Framework/Runner/Task instances to these types.
  private val TestClassLoaderSharedPrefixes = Seq("sbt.testing.")
  
  def withTestsClassLoader[T](
      classPath: Seq[os.Path]
  )(f: ClassLoader => T): T = {
    withIsolatedClassLoader(classPath, sharedPrefixes = TestClassLoaderSharedPrefixes)(f)
  }
}
