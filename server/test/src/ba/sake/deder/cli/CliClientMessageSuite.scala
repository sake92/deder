package ba.sake.deder.cli

import java.util.UUID

class CliClientMessageSuite extends munit.FunSuite {

  test("CliClientMessage.Exec should preserve client-owned requestId") {
    val requestId = "request-123"
    val message = CliClientMessage.Exec(requestId, Seq("exec", "-m", "server"))

    assertEquals(message.getRequestId, requestId)
  }

  test("CliClientMessage.Cancel should preserve client-owned requestId") {
    val requestId = "request-123"
    val message = CliClientMessage.Cancel(requestId)

    assertEquals(message.getRequestId, requestId)
  }

  test("Non-request CliClientMessage types should produce UUID-shaped requestId") {
    val messages = Seq[CliClientMessage](
      CliClientMessage.Help(Seq.empty),
      CliClientMessage.Version(),
      CliClientMessage.Modules(Seq.empty),
      CliClientMessage.Tasks(Seq.empty),
      CliClientMessage.Plan(Seq.empty),
      CliClientMessage.Clean(Seq.empty),
      CliClientMessage.Import(Seq.empty),
      CliClientMessage.Complete(Seq.empty),
      CliClientMessage.Shutdown()
    )

    messages.foreach { message =>
      val requestId = message.getRequestId
      assertNoDiff(UUID.fromString(requestId).toString, requestId)
    }
  }
}
