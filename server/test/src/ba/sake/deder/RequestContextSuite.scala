package ba.sake.deder

import ox.*

class RequestContextSuite extends munit.FunSuite {

  test("clientContext propagates into forkUser") {
    val expectedCtx = CliClientContext(
      clientId = "client-1",
      requestId = "request-1",
      envVars = Map("FOO" -> "BAR"),
      outputFormat = OutputFormat.Json
    )

    val forkCtx = supervised {
      RequestContext.clientContext.supervisedWhere(expectedCtx) {
        forkUser {
          RequestContext.clientContext.get()
        }.join()
      }
    }

    assertEquals(forkCtx, expectedCtx)
  }

  test("clientContext returns defaults outside supervisedWhere") {
    assertEquals(RequestContext.clientContext.get().clientId, "unknown")

    supervised {
      RequestContext.clientContext.supervisedWhere(
        CliClientContext(clientId = "client-2", requestId = "request-2")
      ) {
        assertEquals(RequestContext.clientContext.get().requestId, "request-2")
      }
    }

    assertEquals(RequestContext.clientContext.get().clientId, "unknown")

  }
}
