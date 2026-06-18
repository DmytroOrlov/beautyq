package leaderboard.search

import leaderboard.api.BeautySearchApi
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchProductionRouteLimitSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "POST /beauty-search limit parameter" should {
    "return empty variantCarousel for normal positive limit (ES zero-hit)" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }

    "return empty variantCarousel for zero limit" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":0}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }

    "return empty variantCarousel for negative limit" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":-5}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }

    "return empty variantCarousel for huge limit (ES zero-hit)" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":100000}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }
  }

}
