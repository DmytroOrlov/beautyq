package leaderboard.search

import leaderboard.api.BeautySearchApi
import org.http4s.Request
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Task}

final class BeautySearchProductionRouteErrorSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "POST /beauty-search invalid request behavior" should {
    "return Tapir default bad-input response for malformed JSON body" in {
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

        assertDefaultBadRequest(response)
      }
    }

    "return Tapir default bad-input response for empty body" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            Request[Task](method = org.http4s.Method.POST, uri = org.http4s.Uri.unsafeFromString("/beauty-search")).putHeaders(org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json)),
          )
        )

        assertDefaultBadRequest(response)
      }
    }

    "return Tapir default bad-input response for wrong limit type" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"маникюр","limit":"bad"}"""),
          )
        )

        assertDefaultBadRequest(response)
      }
    }

    "return Tapir default bad-input response for missing required field" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertDefaultBadRequest(response)
      }
    }
  }

}
