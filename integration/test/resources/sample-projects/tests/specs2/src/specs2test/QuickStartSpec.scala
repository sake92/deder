package specs2test

import org.specs2.*

class QuickStartSpec extends Specification {
  def is =
    s2"""

  This is my first specification
    it is working $ok
    really working! $ok

  """
}