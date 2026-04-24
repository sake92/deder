package ba.sake.deder.testing.forked

import ba.sake.deder.testing.TestRunnerLogger

class ForkedTestLogger extends TestRunnerLogger {

  // capture stdout/stderr before any framework hijacks them
  private val out = System.out
  private val err = System.err

  def ansiCodesSupported(): Boolean = true
  def showStackTraces: Boolean = true
  def test(msg: String): Unit = info(msg)

  override def error(msg: String): Unit = err.println(msg)
  override def warn(msg: String): Unit = out.println(msg)
  override def info(msg: String): Unit = out.println(msg)
  override def debug(msg: String): Unit = out.println(msg)
  override def trace(t: Throwable): Unit = err.println(s"${t.toString}\n${t.getStackTrace.mkString("\n")}")
}
