package leaderboard.search

import leaderboard.api.BeautySearchApi
import leaderboard.search.dsl.BeautySearchSpecV1
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
            postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }

    "return structured invalid_limit for zero limit" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":0}"""),
          )
        )

        assertStructuredBadRequest(
          response,
          code = "invalid_limit",
          message = s"limit must be between 1 and ${BeautySearchSpecV1.spec.carouselSpec.variantSize}",
        )
      }
    }

    "return structured invalid_limit for negative limit" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":-5}"""),
          )
        )

        assertStructuredBadRequest(
          response,
          code = "invalid_limit",
          message = s"limit must be between 1 and ${BeautySearchSpecV1.spec.carouselSpec.variantSize}",
        )
      }
    }

    "return structured invalid_limit for limit above the carousel maximum" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":100000}"""),
          )
        )

        assertStructuredBadRequest(
          response,
          code = "invalid_limit",
          message = s"limit must be between 1 and ${BeautySearchSpecV1.spec.carouselSpec.variantSize}",
        )
      }
    }
  }

}
