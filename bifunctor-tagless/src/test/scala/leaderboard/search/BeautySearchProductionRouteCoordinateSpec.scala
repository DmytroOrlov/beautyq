package leaderboard.search

import leaderboard.api.BeautySearchApi
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchProductionRouteCoordinateSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "POST /beauty-search coordinate inputs" should {
    "return current behavior for normal Hamburg coordinates" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
          )
        )

        assertOkWithEmptyVariantCarousel(response)
      }
    }

    "return structured invalid_latitude for latitude too high (999.0)" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":999.0,"userLon":10.07672,"limit":3}"""),
          )
        )

        assertStructuredBadRequest(response, "invalid_latitude", "userLat must be between -90 and 90")
      }
    }

    "return structured invalid_longitude for longitude too high (999.0)" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":53.57532,"userLon":999.0,"limit":3}"""),
          )
        )

        assertStructuredBadRequest(response, "invalid_longitude", "userLon must be between -180 and 180")
      }
    }

    "return the first structured coordinate failure for very large finite coordinates (1e9, -1e9)" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"nails","userLat":1.0e9,"userLon":-1.0e9,"limit":3}"""),
          )
        )

        assertStructuredBadRequest(response, "invalid_latitude", "userLat must be between -90 and 90")
      }
    }
  }

}
