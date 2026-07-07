package leaderboard.search

import distage.{DIKey, Mode}
import io.circe.Json
import io.circe.parser.parse
import izumi.distage.model.definition.Activation
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.{LeaderboardTest, ProdTest}
import org.http4s.Status
import zio.ZIO

import java.util.UUID

// This is the Elasticsearch business demo smoke. It intentionally exercises the explicit ES-only route
// graph (`BeautySearchRouteModules.apiElasticsearch`, via `buildRealEsRouteAndServiceProbe`) seeded into
// a real, repo-local Elasticsearch instance under a unique per-run index, NOT the local managed launcher
// default. The local managed default `/beauty-search` is the ES + constrained Qdrant supplement
// route; that route and its provenance (`executionMode`, `qdrantSupplement`, per-variant `resultOrigin`)
// are covered by `BeautySearchQdrantSupplementProvenanceSpec`. This spec proves the ES business demo
// returns useful non-empty results without depending on a live Qdrant/embedding backend, and uses the
// isolated probe harness so its ES index never collides with other suites' shared default index.
final class BeautySearchElasticsearchBusinessDemoSpec
    extends LeaderboardTest
    with ProdTest
    with BeautySearchProductionRouteSpecSupport {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg],
  )

  private val baseSpec  = BeautySearchSpecV1.spec
  private val evalSuite = BeautySearchEvalInventory.evalSuite

  private val DemoQueryIds: Set[String] = Set(
    "q_nails_001",
    "q_nails_006",
    "q_nails_003",
    "q_face_001",
    "q_brows_001",
    "q_lashes_001",
    "q_pmu_001",
    "q_hair_001",
    "q_hair_004",
    "q_broad_001",
    "q_brows_005",
    "q_hair_008",
  )

  private val demoQueries: List[BeautySearchEvalQuery] = {
    val byId = evalSuite.queries.map(q => q.id -> q).toMap
    val missing = DemoQueryIds.filterNot(byId.contains)
    if (missing.nonEmpty) {
      throw new RuntimeException(s"Demo query ids missing from eval dataset: ${missing.mkString("[", ",", "]")}")
    }
    evalSuite.queries.filter(q => DemoQueryIds.contains(q.id))
  }

  "BeautySearch Elasticsearch business demo smoke" should {
    "return non-empty useful results for all selected demo queries via POST /beauty-search" in {
      (portCfg: ElasticsearchPortCfg) =>
        val testSpec = baseSpec.copy(
          variantDocument = baseSpec.variantDocument.copy(
            indexName = s"${baseSpec.variantDocument.indexName}_business_demo_${UUID.randomUUID().toString.replace('-', '_')}"
          )
        )
        val client = new ElasticsearchTestClient(portCfg.host, portCfg.port)
        val lat    = evalSuite.testUserLocation.lat
        val lon    = evalSuite.testUserLocation.lon

        ZIO.succeed {
          // Build the explicit ES-only route graph once; it eagerly seeds the unique real index at
          // produce time. The asserted route is the graph-wired BeautySearchApi via the HttpApi set.
          val probe = buildRealEsRouteAndServiceProbe(portCfg.port, testSpec)

          demoQueries.foreach { query =>
            val response = runIO(
              observeRoute(
                probe.allHttpApis,
                postJson("/beauty-search", s"""{"query":"${escapeJson(query.query)}","userLat":$lat,"userLon":$lon,"limit":5}"""),
              )
            )
            assert(response.status == Status.Ok, s"queryId=${query.id}: expected 200 OK, got ${response.status}; body=${response.body}")

            val json          = parseJsonOrFail(response.body)
            val variantIds    = variantIdsFromResponse(json)
            val acceptableIds = query.expectedVariantCarousel.acceptableVariantIds.map(_.toString).toSet
            val diagnostic    = s"queryId=${query.id} query=${query.query} returned=${variantIds.mkString("[", ",", "]")} acceptable=${acceptableIds.mkString("[", ",", "]")}"

            assert(variantIds.nonEmpty, s"variantCarousel empty: $diagnostic")
            assert(variantIds.size <= 5, s"variantCarousel.size=${variantIds.size} > 5: $diagnostic")
            assert(variantIds.exists(acceptableIds.contains), s"no acceptable variant: $diagnostic")
          }
          (): Unit
        }.ensuring(client.deleteIndex(testSpec.variantDocument.indexName).either.unit)
    }
  }

  private def parseJsonOrFail(value: String): Json =
    parse(value) match {
      case Right(json) => json
      case Left(error) => fail(s"invalid JSON: ${error.getMessage}")
    }

  private def variantIdsFromResponse(json: Json): Set[String] =
    json.hcursor
      .downField("variantCarousel")
      .focus
      .flatMap(_.asArray)
      .getOrElse(Vector.empty)
      .flatMap(_.hcursor.downField("variantId").focus.flatMap(_.asString))
      .toSet

  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")
}
