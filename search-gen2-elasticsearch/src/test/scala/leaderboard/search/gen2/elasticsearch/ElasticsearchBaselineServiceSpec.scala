package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.lifecycle.*
import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

import java.time.Clock

final class ElasticsearchBaselineServiceSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  "ElasticsearchBaselineService" should {
    "fail at lifecycle resolution before transport when no active generation exists" in {
      val page = PageRequest(None, PageSize.from(2).getOrElse(fail("expected page size")))
      val plan = SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, page, Vector.empty, Vector.empty, PlanDiagnostics.empty)
      val bound = SearchCursorEnvelope.bind(plan, CanonicalPlanView(fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))
      val prepared = ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound).getOrElse(fail("expected prepared request"))
      val batching = ElasticsearchBulkBatchingPolicy.create(10, 10000L).getOrElse(fail("expected batching"))
      val config = ElasticsearchGenerationLifecycleConfig.create("books", "books_", batching).getOrElse(fail("expected config"))
      val lifecycle = new ElasticsearchGenerationLifecycle(new MissingAliasClient, config, Clock.systemUTC())

      new ElasticsearchBaselineService(lifecycle).search(prepared) match {
        case Left(ElasticsearchBaselineServiceError.Lifecycle(error @ ElasticsearchGenerationLifecycleError.MissingActiveGeneration("books"))) =>
          assert(error == ElasticsearchGenerationLifecycleError.MissingActiveGeneration("books"))
        case other => fail(s"expected missing active generation, got $other")
      }
    }

    "searchBound returns bound result with target and generationReference" in {
      val page = PageRequest(None, PageSize.from(2).getOrElse(fail("expected page size")))
      val plan = SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, page, Vector.empty, Vector.empty, PlanDiagnostics.empty)
      val bound = SearchCursorEnvelope.bind(plan, CanonicalPlanView(fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))
      val prepared = ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound).getOrElse(fail("expected prepared request"))
      val response = Json.obj(
        "timed_out" -> Json.fromBoolean(false),
        "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
        "hits" -> Json.obj(
          "total" -> Json.obj("value" -> Json.fromInt(0), "relation" -> Json.fromString("eq")),
          "hits" -> Json.arr(),
        ),
      )
      val service = new ElasticsearchBaselineService(ScriptedSearchBoundClient(response))
      service.searchBound(prepared) match {
        case Right(boundResult) =>
          assert(boundResult.target == testPhysicalTarget)
          assert(boundResult.generationReference == testGenerationReference)
          assert(boundResult.result.hits.isEmpty)
        case Left(error) => fail(s"expected bound result, got $error")
      }
    }

    "search delegates to searchBound without changing observable result" in {
      val page = PageRequest(None, PageSize.from(2).getOrElse(fail("expected page size")))
      val plan = SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, page, Vector.empty, Vector.empty, PlanDiagnostics.empty)
      val bound = SearchCursorEnvelope.bind(plan, CanonicalPlanView(fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))
      val prepared = ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound).getOrElse(fail("expected prepared request"))
      val response = Json.obj(
        "timed_out" -> Json.fromBoolean(false),
        "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
        "hits" -> Json.obj(
          "total" -> Json.obj("value" -> Json.fromInt(0), "relation" -> Json.fromString("eq")),
          "hits" -> Json.arr(),
        ),
      )
      val service = new ElasticsearchBaselineService(ScriptedSearchBoundClient(response))
      service.search(prepared) match {
        case Right(result) =>
          assert(result.hits.isEmpty)
        case Left(error) => fail(s"expected search result, got $error")
      }
    }
  }

  private final class MissingAliasClient extends ElasticsearchGen2JsonClient {
    def getJson(path: String) = Left(ElasticsearchGen2TransportError.HttpFailure("GET", path, 404, "missing"))
    def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
    def post(path: String) = fail(s"unexpected post($path)")
    def postJson(path: String, body: Json) = fail(s"unexpected postJson($path, $body)")
    def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
    def delete(path: String) = fail(s"unexpected delete($path)")
  }

  private def ScriptedSearchBoundClient(response: Json) = new ElasticsearchGenerationLifecycle(
    new ElasticsearchGen2JsonClient {
      def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = {
        (path, body.asObject.flatMap(_("query"))) match {
          case (target @ s"/${testPhysicalTarget.value}/_search", _) =>
            assert(target == s"/${testPhysicalTarget.value}/_search")
            Right(response)
          case (countPath @ s"/${testPhysicalTarget.value}/_count", Some(queryObj)) if queryObj.toString.contains("match_all") =>
            Right(Json.obj("count" -> Json.fromLong(2L), "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0))))
          case _ => fail(s"unexpected postJson($path, $body)")
        }
      }
      def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
      def post(path: String) = fail(s"unexpected post($path)")
      def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
      def getJson(path: String): Either[ElasticsearchGen2TransportError, Json] = path match {
        case "/_alias/books" =>
          Right(Json.obj(testPhysicalTarget.value -> Json.obj("aliases" -> Json.obj("books" -> Json.obj()))))
        case s"/${testPhysicalTarget.value}/_mapping" =>
          Right(Json.obj(testPhysicalTarget.value -> Json.obj("mappings" -> Json.obj(
            "_meta" -> testMetadataJson,
            "properties" -> Json.obj(),
          ))))
        case other => fail(s"unexpected getJson($other)")
      }
      def delete(path: String): Either[ElasticsearchGen2TransportError, Unit] = fail(s"unexpected delete($path)")
    },
    testLifecycleConfig,
    Clock.systemUTC(),
  )
}
