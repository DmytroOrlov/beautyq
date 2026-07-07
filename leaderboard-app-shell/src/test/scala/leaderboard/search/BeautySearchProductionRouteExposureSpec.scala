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

  // M16B serving-gate smoke/evidence harness: table-driven coverage over the accepted serving-gate
  // route states via the M16A explicit selector surface. These cases replace the prior four M16A
  // one-off serving-gate route tests with a single reusable, stable-id evidence table.
  private val validBeautySearchRequest   = """{"query":"haircut","userLat":53.58,"userLon":10.08,"limit":3}"""
  private val invalidBeautySearchRequest = """{"query":"","userLat":53.58,"userLon":10.08,"limit":3}"""

  private val servingGateEvidenceCases: List[ServingGateEvidenceCase] = List(
    ServingGateEvidenceCase(
      id = "gate_disabled_valid_request",
      gate = BeautySearchServingGate.disabled,
      requestBody = validBeautySearchRequest,
      expectedStatus = Status.Ok,
      assertBehavior = assertOkWithEmptyBeautySearchResponseShape,
    ),
    ServingGateEvidenceCase(
      id = "gate_enabled_not_ready_valid_request",
      gate = BeautySearchServingGate.enabledNotReady,
      requestBody = validBeautySearchRequest,
      expectedStatus = Status.ServiceUnavailable,
      assertBehavior = _ => (),
    ),
    ServingGateEvidenceCase(
      id = "gate_enabled_ready_valid_request",
      gate = BeautySearchServingGate.enabledReady,
      requestBody = validBeautySearchRequest,
      expectedStatus = Status.Ok,
      assertBehavior = assertOkWithEmptyBeautySearchResponseShape,
    ),
    ServingGateEvidenceCase(
      id = "gate_enabled_not_ready_invalid_request",
      gate = BeautySearchServingGate.enabledNotReady,
      requestBody = invalidBeautySearchRequest,
      expectedStatus = Status.BadRequest,
      assertBehavior = _ => (),
    ),
  )

  "BeautySearchRouteModules.seedCatalogElasticsearchWithServingGate serving-gate evidence harness" should {
    "expose stable, unique evidence case ids covering the accepted serving-gate states" in {
      val ids = servingGateEvidenceCases.map(_.id)
      assert(ids.distinct.size == ids.size, s"Evidence case ids must be unique, got: $ids")
      assert(
        ids.toSet == Set(
          "gate_disabled_valid_request",
          "gate_enabled_not_ready_valid_request",
          "gate_enabled_ready_valid_request",
          "gate_enabled_not_ready_invalid_request",
        ),
        s"Unexpected evidence case id set: $ids",
      )
      (): Unit
    }

    servingGateEvidenceCases.foreach { evidenceCase =>
      s"record ${evidenceCase.id} as HTTP ${evidenceCase.expectedStatus.code}" in {
        val observedStatus = runServingGateEvidenceCase(evidenceCase)
        assert(
          observedStatus == evidenceCase.expectedStatus,
          s"[${evidenceCase.id}] expected ${evidenceCase.expectedStatus}, observed $observedStatus",
        )
        (): Unit
      }
    }
  }

}
