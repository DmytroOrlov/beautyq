package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.gen2.elasticsearch.*
import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

import java.time.Clock

final class BeautyQElasticsearchBaselineServiceSpec extends AnyWordSpec {
  "BeautyQElasticsearchBaselineService.make" should {
    "compose the one generic lifecycle from canonical BeautyQ resource names" in {
      val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
      assert(BeautyQElasticsearchBaselineService.make(new FailIfCalledClient, Clock.systemUTC(), batching).isRight)
      assert(BeautyQElasticsearchGeneration.compile(BeautyQElasticsearchTestFixtures.materialized).isRight)
    }
  }

  private final class FailIfCalledClient extends ElasticsearchGen2JsonClient {
    def getJson(path: String) = fail(s"unexpected getJson($path)")
    def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
    def post(path: String) = fail(s"unexpected post($path)")
    def postJson(path: String, body: Json) = fail(s"unexpected postJson($path, $body)")
    def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
    def delete(path: String) = fail(s"unexpected delete($path)")
  }
}
