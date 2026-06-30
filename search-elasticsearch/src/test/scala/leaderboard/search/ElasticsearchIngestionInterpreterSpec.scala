package leaderboard.search

import io.circe.Json
import leaderboard.search.elasticsearch.ElasticsearchIngestionInterpreter
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchIngestionInterpreterSpec extends AnyWordSpec {
  "ElasticsearchIngestionInterpreter" should {
    "build source JSON from a generic document spec" in {
      val source = ElasticsearchIngestionInterpreter.sourceJson(ToyElasticsearchSearchSpec.documentSpec, ToyElasticsearchSearchSpec.document)

      assert(source.hcursor.get[String]("title") == Right("Fresh haircut"))
      assert(source.hcursor.get[BigDecimal]("priceFrom") == Right(BigDecimal("18.50")))
      assert(source.hcursor.get[Boolean]("available") == Right(true))
      assert(source.hcursor.downField("location").get[BigDecimal]("lat") == Right(BigDecimal("52.5")))
      assert(source.hcursor.downField("attrs").downField("color").as[String] == Right("red"))
      assert(source.hcursor.downField("counts").downField("level").as[Int] == Right(2))
      assert(source.hcursor.downField("metrics").downField("rating").as[BigDecimal] == Right(BigDecimal("4.7")))
    }

    "build NDJSON bulk payload using generic index name, id, and source JSON" in {
      val payload = ElasticsearchIngestionInterpreter.bulkPayload(ToyElasticsearchSearchSpec.documentSpec, List(ToyElasticsearchSearchSpec.document))
      val source = ElasticsearchIngestionInterpreter.sourceJson(ToyElasticsearchSearchSpec.documentSpec, ToyElasticsearchSearchSpec.document)
      val action = Json.obj(
        "index" -> Json.obj(
          "_index" -> Json.fromString("toy_documents"),
          "_id" -> Json.fromString("toy-1"),
        )
      ).noSpaces

      assert(payload == s"\n$action\n${source.noSpaces}\n")
    }
  }
}
