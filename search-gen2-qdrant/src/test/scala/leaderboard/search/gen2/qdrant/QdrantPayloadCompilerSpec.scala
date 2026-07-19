package leaderboard.search.gen2.qdrant

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

final class QdrantPayloadCompilerSpec extends AnyWordSpec {
  "QdrantPayloadCompiler" should {
    "encode declared scalar values with their Qdrant payload shapes" in {
      QdrantPayloadCompiler.compile(QdrantTestFixtures.policy, QdrantTestFixtures.first) match {
        case Right(payload) =>
          assert(payload.hcursor.downField("id").focus.contains(Json.fromString("00000000-0000-0000-0000-000000000001")))
          assert(payload.hcursor.downField("count").focus.contains(Json.fromInt(2)))
          assert(payload.hcursor.downField("active").focus.contains(Json.fromBoolean(true)))
          assert(payload.hcursor.downField("at").focus.contains(Json.fromString("2025-01-01T00:00:00Z")))
          assert(payload.hcursor.downField("location").downField("lat").focus.contains(Json.fromBigDecimal(BigDecimal("52.5"))))
        case Left(error) => fail(s"expected payload, got $error")
      }
    }

    "omit a non-payload embedding field" in {
      QdrantPayloadCompiler.compile(QdrantTestFixtures.policy, QdrantTestFixtures.first) match {
        case Right(payload) => assert(payload.hcursor.downField("title").focus.isEmpty)
        case Left(error)    => fail(s"expected payload, got $error")
      }
    }
  }
}
