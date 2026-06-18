package leaderboard.search

import leaderboard.api.BeautySearchApi
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchProductionRouteQuerySpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "POST /beauty-search query text inputs" should {
    "return structured invalid_query for empty query string" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
          )
        )

        assertStructuredBadRequest(response, "invalid_query", "query must not be blank")
      }
    }

    "return structured invalid_query for whitespace-only query string" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"     ","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
          )
        )

        assertStructuredBadRequest(response, "invalid_query", "query must not be blank")
      }
    }

    "return current behavior for normal text query" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }

    "return current behavior for very long query string" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val longQuery = ("nails " * 1000)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", s"""{"query":"$longQuery","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }
  }

}
