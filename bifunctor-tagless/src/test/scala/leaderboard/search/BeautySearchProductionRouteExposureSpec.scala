package leaderboard.search

import io.circe.parser.parse
import leaderboard.api.BeautySearchApi
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchProductionRouteExposureSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "LeaderboardPlugin apiBase plus BeautySearchRouteModules.apiElasticsearch" should {
    "expose BeautySearchApi in the HttpApi set and serve the seed-catalog ES route" in {
      withZeroHitEsServer { port =>
        val probe = buildProductionApiGraphRouteProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assert(response.status == Status.Ok)

        val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
        assert(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
        assert(json.hcursor.downField("providerCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
        assert(json.hcursor.downField("serviceIntentCarousel").focus.exists(_.asArray.exists(_.isEmpty)))
        assert(json.hcursor.downField("facets").focus.isDefined)
        assert(json.hcursor.downField("inferredFilters").focus.isDefined): Unit
      }
    }
  }

}
