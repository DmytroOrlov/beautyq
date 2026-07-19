package leaderboard.search.gen2.qdrant

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

final class QdrantEmbeddingSpec extends AnyWordSpec {
  private val input = QdrantEmbeddingInput.from(
    QdrantEmbeddingPurpose.CandidateQuery,
    "candidate-query",
    "alpha",
    QdrantTestFixtures.model,
  ).getOrElse(fail("expected valid embedding input"))

  "QdrantEmbeddingInput and QdrantEmbeddingResult" should {
    "reject blank text, wrong dimensions and non-finite values" in {
      assert(QdrantEmbeddingInput.from(QdrantEmbeddingPurpose.Document, "doc-1", " ", QdrantTestFixtures.model) == Left(QdrantEmbeddingError.BlankText("doc-1")))
      assert(QdrantEmbeddingResult.from(input, Vector(0.1, 0.2)) == Left(QdrantEmbeddingError.DimensionMismatch(3, 2)))
      QdrantEmbeddingResult.from(input, Vector(0.1, Double.PositiveInfinity, 0.3)) match {
        case Left(QdrantEmbeddingError.NonFinite(1, value)) => assert(value.isPosInfinity)
        case Left(error) => fail(s"expected non-finite error, got $error")
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }

    "bind a finite vector to the exact input fingerprint and model" in {
      QdrantEmbeddingResult.from(input, Vector(0.1, 0.2, 0.3)) match {
        case Left(error) => fail(s"expected embedding result, got $error")
        case Right(result) =>
          assert(result.inputFingerprint == input.fingerprint)
          assert(result.model == input.modelValue)
          assert(result.values == Vector(0.1, 0.2, 0.3))
      }
    }
  }

  "QdrantPointId" should {
    "support UUID and unsigned-long wire identities" in {
      val uuidField = field[QdrantTestFixtures.NeutralDocument, java.util.UUID]("id", _.id).keyword
      QdrantPointId.fromCanonical(uuidField, "00000000-0000-0000-0000-000000000001") match {
        case Right(QdrantPointId.Uuid(value)) => assert(value == "00000000-0000-0000-0000-000000000001")
        case other => fail(s"expected UUID point id, got $other")
      }

      val longField = computedField[QdrantTestFixtures.NeutralDocument, Long]("longId", "longId")(document => Some(document.count.toLong)).long
      QdrantPointId.fromCanonical(longField, "18446744073709551615") match {
        case Right(QdrantPointId.UnsignedLong(value)) => assert(value == BigInt("18446744073709551615"))
        case other => fail(s"expected unsigned-long point id, got $other")
      }
    }
  }
}
