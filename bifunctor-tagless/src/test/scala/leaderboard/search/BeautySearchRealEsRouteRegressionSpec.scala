package leaderboard.search

import distage.{DIKey, Mode}
import io.circe.parser.parse
import io.circe.syntax.*
import izumi.distage.model.definition.Activation
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.{LeaderboardTest, ProdTest}
import org.http4s.Status
import zio.ZIO

import java.util.UUID

/**
 * U1/AP1 minimal real-ES default-route characterization spec.
 *
 * Scope: a single, source-confirmed default ES Beauty search route graph
 * ([[leaderboard.plugins.BeautySearchRouteModules.apiElasticsearch]]) seeded into a real, repo-local
 * Elasticsearch instance. Using one request/input, it characterizes the OBSERVED current behavior:
 *
 *   1. the graph-wired `BeautySearchService.search(input)` succeeds and returns a non-empty
 *      `variantCarousel`; and
 *   2. the graph-wired `BeautySearchApi` actual `POST /beauty-search` route returns `200 OK` with a
 *      non-empty `variantCarousel`.
 *
 * IMPORTANT — relation to `docs/codebase-review/U_REAL_ES_ROUTE_500_INVESTIGATION.md`:
 *   - This minimal default ES route graph does NOT reproduce the prior U investigation's `500`.
 *   - The prior `500` is therefore NOT accepted as the current minimal AP1 blocker; it is treated as
 *     diagnostic-patch / test-wiring / cold-start specific until reproduced by a minimal spec.
 *   - AP1 is advanced (default route proven 200 OK non-empty over real ES), but this does NOT claim
 *     broader program readiness, default-route switch, runtime hybrid activation, or hybrid behavior.
 *
 * This is a real-ES default-route characterization/proof scope only. The route path asserted is the
 * graph-wired `BeautySearchApi` (observed via the graph's `HttpApi` set); no fresh `BeautySearchApi`
 * is constructed for the asserted path, no service warm-up precedes the route assertion, and no
 * Qdrant / fallback / fusion / reranking / shadow-mirror / route-switch / hybrid path is involved.
 */
final class BeautySearchRealEsRouteRegressionSpec
    extends LeaderboardTest
    with ProdTest
    with BeautySearchProductionRouteSpecSupport {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg],
  )

  private val baseSpec  = BeautySearchSpecV1.spec
  private val evalSuite = BeautySearchEvalInventory.evalSuite

  // Reuse a query proven non-empty against the seed-resource ES graph by the business-demo smoke spec.
  private val demoQuery =
    evalSuite.queries
      .find(_.id == "q_nails_001")
      .getOrElse(throw new RuntimeException("Eval dataset is missing required query id q_nails_001"))

  // The single request/input reused for both the service assertion and the route assertion.
  private val sameInput: UserSearchInput =
    UserSearchInput(
      query = demoQuery.query,
      userLat = Some(evalSuite.testUserLocation.lat),
      userLon = Some(evalSuite.testUserLocation.lon),
      limit = 5,
    )
  private val sameRequestBody: String = sameInput.asJson.noSpaces

  private def variantCarouselSize(body: String): Int =
    parse(body) match {
      case Right(json) =>
        json.hcursor.downField("variantCarousel").focus.flatMap(_.asArray) match {
          case Some(values) => values.size
          case None         => fail(s"Missing/!array variantCarousel in route body: $body")
        }
      case Left(error) =>
        fail(s"Invalid Beauty search route JSON: ${error.getMessage}; body: $body")
    }

  "Real-ES default Beauty search route graph (U1/AP1 characterization)" should {
    "characterize observed truth: graph-wired service.search succeeds non-empty AND the graph-wired POST /beauty-search route returns 200 OK non-empty (prior U 500 NOT reproduced by the minimal default graph)" in {
      (portCfg: ElasticsearchPortCfg) =>
        val testSpec = baseSpec.copy(
          variantDocument = baseSpec.variantDocument.copy(
            indexName = s"${baseSpec.variantDocument.indexName}_ap1_${UUID.randomUUID().toString.replace('-', '_')}"
          )
        )
        val client = new ElasticsearchTestClient(portCfg.host, portCfg.port)

        ZIO.succeed {
          // Build the default ES route graph once; it eagerly seeds the real index at produce time.
          val probe = buildRealEsRouteAndServiceProbe(portCfg.port, testSpec)

          // (1) The graph-wired service succeeds with a non-empty variantCarousel against real ES.
          val serviceResult = runIO(probe.beautySearchService.search(sameInput))
          assert(
            serviceResult.variantCarousel.nonEmpty,
            "AP1 characterization: graph-wired BeautySearchService.search must return a non-empty variantCarousel against real Elasticsearch",
          )

          // (2) The graph-wired route returns 200 OK non-empty for the SAME input. No warm-up precedes
          //     this; the asserted route is the graph-wired BeautySearchApi via the graph's HttpApi set.
          //     This minimal default ES route graph does NOT reproduce the prior U investigation's 500.
          val routeResponse = runIO(observeRoute(probe.allHttpApis, postJson("/beauty-search", sameRequestBody)))
          assert(
            routeResponse.status == Status.Ok,
            s"AP1 characterization: graph-wired POST /beauty-search returns 200 OK over real ES; the prior U 500 is NOT reproduced by the minimal default graph. Got ${routeResponse.status}; body=${routeResponse.body}",
          )
          assert(
            variantCarouselSize(routeResponse.body) > 0,
            s"AP1 characterization: graph-wired route variantCarousel must be non-empty for the same input; body=${routeResponse.body}",
          )
          (): Unit
        }.ensuring(client.deleteIndex(testSpec.variantDocument.indexName).either.unit)
    }
  }
}
