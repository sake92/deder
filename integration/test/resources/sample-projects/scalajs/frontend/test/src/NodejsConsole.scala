package ba.sake.deder.scalajstest

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal("console")
object NodejsConsole extends js.Object {

  def info(data: js.Any, substitutions: js.Any*): Unit = js.native
}
