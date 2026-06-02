package ba.sake.deder

import ox.*

class RequestContextSuite extends munit.FunSuite {

  test("current propagates into forkUser") {
    val expectedCtx = RequestContext(
      clientId = "client-1",
      requestId = "request-1",
      envVars = Map("FOO" -> "BAR"),
      outputFormat = OutputFormat.Json
    )

    val forkCtx = supervised {
      RequestContext.current.supervisedWhere(expectedCtx) {
        forkUser {
          RequestContext.current.get()
        }.join()
      }
    }

    assertEquals(forkCtx, expectedCtx)
  }

  test("current returns defaults outside supervisedWhere") {
    assertEquals(RequestContext.current.get().clientId, "unknown")

    supervised {
      RequestContext.current.supervisedWhere(
        RequestContext(
          clientId = "client-2",
          requestId = "request-2",
          envVars = Map.empty,
          outputFormat = OutputFormat.Json
        )
      ) {
        assertEquals(RequestContext.current.get().requestId, "request-2")
      }
    }

    assertEquals(RequestContext.current.get().clientId, "unknown")

  }
}
