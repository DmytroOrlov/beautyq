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
        assertPreparedStartupTransition(probe.startupTransition)

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
        assertOperatorVisibilityEndpointAbsent(probe.allHttpApis)
        assertSeedOnlyProductionReadinessState(probe.productionReadinessState)
        assertPreparedStartupTransition(probe.startupTransition)

        val response = runIO(
          observeRoute(
            probe.allHttpApis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)

        val lifecycleResponse = runIO(
          observeRoute(
            probe.allHttpApis,
            get("/ops/beauty-search/lifecycle"),
          )
        )

        assert(lifecycleResponse.status == org.http4s.Status.NotFound)
        (): Unit
      }
    }

    "not expose EsLifecycleStatusApi in the default HttpApi set" in {
      withZeroHitEsServer { port =>
        val probe = buildProductionApiGraphRouteProbe(port)
        val apis  = probe.allHttpApis

        assertOperatorVisibilityEndpointAbsent(apis)
        assert(apis.collect { case _: BeautySearchApi[IO] => () }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)

        val lifecycleResponse = runIO(
          observeRoute(
            apis,
            get("/ops/beauty-search/lifecycle"),
          )
        )

        assert(lifecycleResponse.status == org.http4s.Status.NotFound)
        (): Unit
      }
    }

    "expose EsLifecycleStatusApi in the explicit opt-in HttpApi set" in {
      withZeroHitEsServer { port =>
        val probe = buildProductionApiGraphWithOperatorVisibilityRouteProbe(port)
        val apis  = probe.allHttpApis

        assertOperatorVisibilityEndpointPresent(apis)
        assert(apis.collect { case _: BeautySearchApi[IO] => () }.size == 1)

        val response = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)

        val lifecycleResponse = runIO(
          observeRoute(
            apis,
            get("/ops/beauty-search/lifecycle"),
          )
        )

        assertPreparedLifecycleStatusResponse(lifecycleResponse)
        (): Unit
      }
    }

    "serve the operator endpoint without adding request-time Elasticsearch calls" in {
      withRecordingZeroHitEsServer { server =>
        val probe = buildProductionApiGraphWithOperatorVisibilityRouteProbe(server.port)
        val apis  = probe.allHttpApis

        assertOperatorVisibilityEndpointPresent(apis)

        val requestCountBeforeLifecycleCall = server.requestCount

        val lifecycleResponse = runIO(
          observeRoute(
            apis,
            get("/ops/beauty-search/lifecycle"),
          )
        )

        assertPreparedLifecycleStatusResponse(lifecycleResponse)
        assert(server.requestCount == requestCountBeforeLifecycleCall)

        val beautySearchResponse = runIO(
          observeRoute(
            apis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(beautySearchResponse)
        assert(server.requestCount == requestCountBeforeLifecycleCall + 1)
        assert(server.requestPaths.lastOption.exists(_.contains("_search")))
        (): Unit
      }
    }
  }

}
