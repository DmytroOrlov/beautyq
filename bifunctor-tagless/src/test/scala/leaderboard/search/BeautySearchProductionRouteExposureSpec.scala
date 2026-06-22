package leaderboard.search

import leaderboard.api.{BeautySearchApi, BeautySearchServingGate}
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchProductionRouteExposureSpec extends AnyWordSpec with BeautySearchProductionRouteSpecSupport {
  "BeautySearchRouteModules.apiElasticsearch" should {
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

    "keep POST /beauty-search ES-backed until separate production-route activation is approved" in {
      withZeroHitEsServer { port =>
        val probe = buildProductionApiGraphRouteProbe(port)

        assert(probe.allHttpApis.collect { case _: BeautySearchApi[IO] => () }.size == 1)
        assertSeedOnlyLifecycleMetadata(probe.lifecycleMetadata)
        assertSeedOnlyProductionReadinessState(probe.productionReadinessState)
        assertPreparedStartupTransition(probe.startupTransition)

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

  "BeautySearchRouteModules.seedCatalogElasticsearchWithServingGate" should {
    "keep the default api module binding BeautySearchServingGate.disabled and serve 200 OK for a valid /beauty-search" in {
      withZeroHitEsServer { port =>
        val probe = buildServingGateEsRouteProbe(port, BeautySearchServingGate.disabled)

        assert(probe.allHttpApis.collect { case _: BeautySearchApi[IO] => () }.size == 1)
        assertOperatorVisibilityEndpointAbsent(probe.allHttpApis)

        val response = runIO(
          observeRoute(
            probe.allHttpApis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)
      }
    }

    "return HTTP 503 for a valid /beauty-search when the explicit gate is enabled-not-ready" in {
      withZeroHitEsServer { port =>
        val probe = buildServingGateEsRouteProbe(port, BeautySearchServingGate.enabledNotReady)

        val response = runIO(
          observeRoute(
            probe.allHttpApis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assert(response.status == Status.ServiceUnavailable, s"Expected 503, got ${response.status}")
        (): Unit
      }
    }

    "follow existing ES-backed success behavior for a valid /beauty-search when the explicit gate is enabled-ready" in {
      withZeroHitEsServer { port =>
        val probe = buildServingGateEsRouteProbe(port, BeautySearchServingGate.enabledReady)

        val response = runIO(
          observeRoute(
            probe.allHttpApis,
            postJson("/beauty-search", """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assertOkWithEmptyBeautySearchResponseShape(response)
      }
    }

    "return HTTP 400 before the gate for an invalid /beauty-search even when the explicit gate is enabled-not-ready" in {
      withZeroHitEsServer { port =>
        val probe = buildServingGateEsRouteProbe(port, BeautySearchServingGate.enabledNotReady)

        val response = runIO(
          observeRoute(
            probe.allHttpApis,
            postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":3}"""),
          )
        )

        assert(response.status == Status.BadRequest, s"Expected 400, got ${response.status}")
        (): Unit
      }
    }
  }

}
