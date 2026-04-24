package ba.sake.deder.testing

import sbt.testing.Logger

trait TestRunnerLogger extends Logger {
  def test(msg: String): Unit
  def showStackTraces: Boolean
}
