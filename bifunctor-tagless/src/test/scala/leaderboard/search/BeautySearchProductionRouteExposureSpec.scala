package leaderboard.search

import leaderboard.api.BeautySearchApi
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchProductionRouteExposureSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "LeaderboardPlugin apiBase plus BeautySearchRouteModules.apiElasticsearch" should {
    "expose BeautySearchApi in the HttpApi set and serve the seed-catalog ES route" in {
      withZeroHitEsServer { port =>
        val probe = buildProductionApiGraphRouteProbe(port)
        val apis  = probe.allHttpApis

        assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
        assertSeedOnlyLifecycleMetadata(probe.lifecycleMetadata)
        assertSeedOnlyProductionReadinessState(probe.productionReadinessState)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)
      }
    }

    "root the non-serving readiness state through the targeted ES seed route" in {
      withZeroHitEsServer { port =>
        val probe = buildTargetedEsRouteProbe(port)

        assert(probe.allHttpApis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
        assertSeedOnlyProductionReadinessState(probe.productionReadinessState)

        val response = runIO(
          observeRoute(
            probe.allHttpApis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)
      }
    }
  }

}
