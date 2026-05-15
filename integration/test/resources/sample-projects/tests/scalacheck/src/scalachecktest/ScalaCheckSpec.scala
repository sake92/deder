package scalachecktest

import org.scalacheck.*

object ScalaCheckSpec extends Properties("String") {

  property("startsWith") = Prop.forAll { (a: String, b: String) =>
    (a + b).startsWith(a)
  }

  property("concatenation length") = Prop.forAll { (a: String, b: String) =>
    (a + b).length == a.length + b.length
  }
}
