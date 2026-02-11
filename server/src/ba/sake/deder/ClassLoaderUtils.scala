package ba.sake.deder

import java.net.URLClassLoader
import scala.util.Using

object ClassLoaderUtils {

  def withClassLoader[T](
      classPath: Seq[os.Path],
      parent: ClassLoader = getClass.getClassLoader,
  )(f: ClassLoader => T): T = {
    val oldClassloader = Thread.currentThread().getContextClassLoader
    val urls = classPath.map(_.toURI.toURL).toArray
    Using.resource(new URLClassLoader(urls, parent)) { newClassloader =>
      Thread.currentThread().setContextClassLoader(newClassloader)
      try {
        f(newClassloader)
      } finally {
        Thread.currentThread().setContextClassLoader(oldClassloader)
        newClassloader.close()
      }
    }
  }
}
