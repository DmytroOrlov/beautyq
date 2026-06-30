package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.qdrant.QdrantPointId
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantPointIdSpec extends AnyWordSpec {
  "QdrantPointId" should {
    "render UUID string as JSON string" in {
      val uuid = UUID.fromString("00000000-0000-0000-0000-000000000123")
      val result = QdrantPointId.Uuid(uuid).asJson

      assert(result == Json.fromString(uuid.toString))
    }

    "render unsigned long as JSON number" in {
      val result = QdrantPointId.UnsignedLong(42L).asJson

      assert(result == Json.fromLong(42L))
    }

    "fail negative unsigned long" in {
      val result = QdrantPointId.fromUnsignedLong(-1L)

      assertFailure(result, "Qdrant unsigned integer point id must be non-negative, got -1")
    }

    "fail invalid UUID string" in {
      val result = QdrantPointId.fromUuidString("not-a-uuid")

      assertFailure(result, "Qdrant point id must be a UUID string or unsigned integer, got 'not-a-uuid'")
    }
  }

  private def assertFailure(result: Either[QueryFailure, QdrantPointId], expectedMessage: String): Unit =
    result match {
      case Left(QueryFailure.OperationFailure("qdrant-point-id", message)) =>
        assert(message == expectedMessage): Unit
      case other =>
        fail(s"Expected qdrant-point-id failure '$expectedMessage', got $other")
    }
}
