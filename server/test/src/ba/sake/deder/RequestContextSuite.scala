package ba.sake.deder

import ox.*

class RequestContextSuite extends munit.FunSuite {

  test("clientContext propagates into ox fork") {
    val expectedCtx = CliClientContext(
      clientId = "client-1",
      requestId = "request-1",
      envVars = Map("FOO" -> "BAR"),
      outputFormat = OutputFormat.Json
    )

    val forkCtx = supervised {
      RequestContext.clientContext.supervisedWhere(Some(expectedCtx)) {
        fork {
          RequestContext.clientContext.get()
        }.join()
      }
    }

    assertEquals(forkCtx, Some(expectedCtx))
  }

  test("clientContext is None outside supervisedWhere") {
    assertEquals(RequestContext.clientContext.get(), None)

    supervised {
      RequestContext.clientContext.supervisedWhere(
        Some(CliClientContext(clientId = "client-2", requestId = "request-2"))
      ) {
        assertEquals(RequestContext.clientContext.get().map(_.requestId), Some("request-2"))
      }
    }

    assertEquals(RequestContext.clientContext.get(), None)
  }
}
