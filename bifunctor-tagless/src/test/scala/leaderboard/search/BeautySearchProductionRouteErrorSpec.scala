package leaderboard.search

import leaderboard.api.BeautySearchApi
import org.http4s.{Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Task}

final class BeautySearchProductionRouteErrorSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "POST /beauty-search invalid request behavior" should {
    "return 500 with empty body for malformed JSON body" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"broken""""),
          )
        )

        assert(response.status == Status.InternalServerError)
        assert(response.body == ""): Unit
      }
    }

    "return 500 with empty body for empty body" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            Request[Task](method = org.http4s.Method.POST, uri = org.http4s.Uri.unsafeFromString("/beauty-search")).putHeaders(org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json)),
          )
        )

        assert(response.status == Status.InternalServerError)
        assert(response.body == ""): Unit
      }
    }

    "return 500 with empty body for wrong limit type" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"маникюр","limit":"bad"}"""),
          )
        )

        assert(response.status == Status.InternalServerError)
        assert(response.body == ""): Unit
      }
    }

    "return 500 with empty body for missing required field" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assert(response.status == Status.InternalServerError)
        assert(response.body == ""): Unit
      }
    }
  }

}
