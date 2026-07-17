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
  }

  private final class MissingAliasClient extends ElasticsearchGen2JsonClient {
    def getJson(path: String) = Left(ElasticsearchGen2TransportError.HttpFailure("GET", path, 404, "missing"))
    def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
    def post(path: String) = fail(s"unexpected post($path)")
    def postJson(path: String, body: Json) = fail(s"unexpected postJson($path, $body)")
    def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
    def delete(path: String) = fail(s"unexpected delete($path)")
  }
}
