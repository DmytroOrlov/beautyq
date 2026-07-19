package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json

import java.time.Instant

/** Effectual group traversal proof with a scripted client and the neutral book document shape. */
final class ElasticsearchGroupExecutorSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private val groupId = GroupId("genre-group")
  private val metricId = GroupMetricId("best-score")
  private val group = GroupRequest[BookDocument, String](
    groupId,
    genre,
    GroupSize.from(10).getOrElse(fail("expected group size")),
    RepresentativeRequest.IdentityOnly(),
    Vector(GroupMetricRequest.BestScore(metricId)),
    Vector(GroupOrder.Metric(metricId, SortDirection.Desc), GroupOrder.MatchingDocumentCount(SortDirection.Desc), GroupOrder.Key(SortDirection.Asc)),
    GroupPrecisionPolicy.RequireExact,
  )

  private val bound = {
    val plan = SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, PageRequest(None, PageSize.from(10).getOrElse(fail("expected page size"))), Vector.empty, Vector(group), PlanDiagnostics.empty)
    SearchCursorEnvelope.bind(plan, CanonicalPlanView[BookDocument](fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))
  }

  private val prepared = ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound).getOrElse(fail("expected prepared request"))

  private val authorized: AuthorizedElasticsearchSearchRequest[BookDocument, String] = {
    val identity = ElasticsearchPersistedGenerationIdentity.fromTrusted(
      ElasticsearchGenerationIdentity(
        ContentFingerprint("source"),
        ProjectedDocumentsFingerprint("projected"),
        bound.identity.contractFingerprint,
        ProjectionFormatVersion("projection-v1"),
        fullPolicy.index.compilerVersion,
        fullPolicy.index.indexFormatVersion,
      )
    )
    val metadata = ElasticsearchGenerationMetadata(
      ElasticsearchGenerationMetadataSchemaVersion.Current,
      ElasticsearchGenerationNaming.generationId(identity),
      Instant.parse("2026-01-01T00:00:00Z"),
      2L,
      identity,
    )
    ElasticsearchSearchRequestAuthorization.authorize(
      prepared,
      new LifecycleResolvedElasticsearchGeneration(ElasticsearchGenerationReference("books-generation"), ElasticsearchSearchTarget("books-generation"), metadata),
    ).getOrElse(fail("expected an authorized request"))
  }

  private def bucket(key: String, count: Int, id: String, score: String): Json =
    Json.obj(
      "key" -> Json.obj("group_key" -> Json.fromString(key)),
      "doc_count" -> Json.fromInt(count),
      "representative" -> Json.obj("hits" -> Json.obj("hits" -> Json.arr(Json.obj("_id" -> Json.fromString(id), "_score" -> Json.fromBigDecimal(BigDecimal(score))))))
    )

  private val response = Json.obj(
    "timed_out" -> Json.fromBoolean(false),
    "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
    "aggregations" -> Json.obj(
      "gen2_group" -> Json.obj(
        "buckets" -> Json.arr(bucket("Technology", 1, "isbn-a", "2.0"), bucket("Reference", 2, "isbn-b", "5.0"))
      )
    ),
  )

  "ElasticsearchGroupExecutor" should {
    "traverse and order exact group buckets independently of the hit window" in {
      ElasticsearchGroupExecutor.execute(new ScriptedGroupClient(response), authorized) match {
        case Right(Vector(result)) =>
          assert(result.id == groupId)
          assert(result.precision == ElasticsearchGroupPrecision.Exact)
          assert(result.buckets.map(_.canonicalKey) == Vector("Reference", "Technology"))
          assert(result.buckets.map(_.matchingDocumentCount) == Vector(2L, 1L))
          assert(result.buckets.map(_.bestScore(metricId)) == Vector(Some(BigDecimal("5.0")), Some(BigDecimal("2.0"))))
        case Right(other) => fail(s"expected one group result, got $other")
        case Left(error) => fail(s"expected successful group execution, got $error")
      }
    }
  }

  private final class ScriptedGroupClient(response: Json) extends ElasticsearchGen2JsonClient {
    def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = Right(response)
    def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
    def post(path: String) = fail(s"unexpected post($path)")
    def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path)")
    def getJson(path: String) = fail(s"unexpected getJson($path)")
    def delete(path: String) = fail(s"unexpected delete($path)")
  }
}
