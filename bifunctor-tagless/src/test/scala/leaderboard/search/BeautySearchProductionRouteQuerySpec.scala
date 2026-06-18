package leaderboard.search

import io.circe.parser.parse
import leaderboard.api.BeautySearchApi
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchProductionRouteQuerySpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "POST /beauty-search query text inputs" should {
    "return current behavior for empty query string" in {
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

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty), "ES zero-hit: carousel should be empty"): Unit
      }
    }

    "return current behavior for whitespace-only query string" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)
        val apis  = probe.allHttpApis

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"     ","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
          )
        )

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty)): Unit
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

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty)): Unit
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

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
        assert(carousel.asArray.exists(_.isEmpty)): Unit
      }
    }
  }

}
