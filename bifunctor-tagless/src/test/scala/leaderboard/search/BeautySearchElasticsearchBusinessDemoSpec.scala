package leaderboard.search

import cats.syntax.foldable.*
import distage.{DIKey, Mode}
import fs2.text
import io.circe.Json
import io.circe.parser.parse
import izumi.distage.model.definition.Activation
import leaderboard.api.BeautySearchApi
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.{HttpContractTestSupport, LeaderboardTest, ObservedResponse, ProdTest}
import org.http4s.{HttpApp, Status}
import zio.{IO, Task, ZIO}
import zio.interop.catz.*

final class BeautySearchElasticsearchBusinessDemoSpec extends LeaderboardTest with ProdTest with HttpContractTestSupport {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg],
  )

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
      (
        beautySearchApi: BeautySearchApi[IO],
      ) =>
        val app: HttpApp[Task] = List(beautySearchApi.http).foldK.orNotFound
        val lat = evalSuite.testUserLocation.lat
        val lon = evalSuite.testUserLocation.lon

        ZIO.foreachDiscard(demoQueries) {
          query =>
            for {
              response <- observeRoute(app, postJson("/beauty-search", s"""{"query":"${escapeJson(query.query)}","userLat":$lat,"userLon":$lon,"limit":5}"""))
              _ <- ZIO.succeed(assert(response.status == Status.Ok, s"queryId=${query.id}: expected 200 OK, got ${response.status}"))
              json = parseJsonOrFail(response.body)
              variantIds = variantIdsFromResponse(json)
              acceptableIds = query.expectedVariantCarousel.acceptableVariantIds.map(_.toString).toSet
              diagnostic = s"queryId=${query.id} query=${query.query} returned=${variantIds.mkString("[", ",", "]")} acceptable=${acceptableIds.mkString("[", ",", "]")}"
              _ <- ZIO.succeed(assert(variantIds.nonEmpty, s"variantCarousel empty: $diagnostic"))
              _ <- ZIO.succeed(assert(variantIds.size <= 5, s"variantCarousel.size=${variantIds.size} > 5: $diagnostic"))
              _ <- ZIO.succeed(assert(variantIds.exists(acceptableIds.contains), s"no acceptable variant: $diagnostic"))
            } yield ()
        }
    }
  }

  private def observeRoute(
    app: HttpApp[Task],
    request: org.http4s.Request[Task],
  ): Task[ObservedResponse] =
    app.run(request).flatMap {
      response =>
        response.body
          .through(text.utf8.decode)
          .compile
          .string
          .map(body => ObservedResponse(response.status, body))
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
