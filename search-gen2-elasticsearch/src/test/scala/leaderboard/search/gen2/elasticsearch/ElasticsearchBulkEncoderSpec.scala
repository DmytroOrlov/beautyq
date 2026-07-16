package leaderboard.search.gen2.elasticsearch

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchBulkEncoderSpec extends AnyWordSpec {
  "ElasticsearchBulkEncoder" should {
    "preserve order, enforce document limits and emit final-newline NDJSON without _index" in {
      val policy = ElasticsearchBulkBatchingPolicy.create(2, 1000L).getOrElse(fail("expected policy"))
      val documents = Vector.tabulate(3)(index => ElasticsearchIndexedDocument(s"id-$index", Json.obj("n" -> Json.fromInt(index))))
      val batches = ElasticsearchBulkEncoder.encode(documents, policy).getOrElse(fail("expected batches"))
      assert(batches.map(_.documents.map(_.id)) == Vector(Vector("id-0", "id-1"), Vector("id-2")))
      assert(batches.forall(_.body.endsWith("\n")))
      assert(batches.forall(!_.body.contains("_index")))
    }

    "reject one document larger than the byte budget" in {
      val policy = ElasticsearchBulkBatchingPolicy.create(10, 8L).getOrElse(fail("expected policy"))
      assert(ElasticsearchBulkEncoder.encode(Vector(ElasticsearchIndexedDocument("id", Json.obj("value" -> Json.fromString("large")))), policy).isLeft)
    }

    "apply the UTF-8 byte limit together with document count without changing source bytes or order" in {
      val documents = Vector(
        ElasticsearchIndexedDocument("one", Json.obj("text" -> Json.fromString("ёж"))),
        ElasticsearchIndexedDocument("two", Json.obj("text" -> Json.fromString("plain"))),
      )
      val roomy = ElasticsearchBulkBatchingPolicy.create(10, 1000L).getOrElse(fail("expected policy"))
      val individualSizes = documents.map { document =>
        ElasticsearchBulkEncoder.encode(Vector(document), roomy).getOrElse(fail("expected batch")).headOption.map(_.utf8Bytes).getOrElse(fail("expected one batch"))
      }
      val policy = ElasticsearchBulkBatchingPolicy.create(10, individualSizes.max).getOrElse(fail("expected byte policy"))
      val batches = ElasticsearchBulkEncoder.encode(documents, policy).getOrElse(fail("expected byte batches"))
      assert(batches.map(_.documents.map(_.id)) == Vector(Vector("one"), Vector("two")))
      assert(batches.flatMap(_.documents.map(_.source)) == documents.map(_.source))
    }

    "emit no batches for an empty generation" in {
      val policy = ElasticsearchBulkBatchingPolicy.create(1, 100L).getOrElse(fail("expected policy"))
      assert(ElasticsearchBulkEncoder.encode(Vector.empty, policy) == Right(Vector.empty))
    }
  }
}
