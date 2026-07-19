package leaderboard.search.gen2.qdrant

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

final class QdrantPolicySpec extends AnyWordSpec {
  "QdrantPolicy" should {
    "derive payload fields and indexes from one document declaration" in {
      val policy = QdrantTestFixtures.policy

      assert(policy.payloadFields.map(_.id.value) == Vector("id", "count", "from", "to", "active", "at", "location"))
      assert(policy.payloadIndexes.map(index => (index.path.value, index.schema)) == Vector(
        ("count", QdrantPayloadFieldSchema.Integer),
        ("from", QdrantPayloadFieldSchema.Float),
        ("to", QdrantPayloadFieldSchema.Float),
        ("active", QdrantPayloadFieldSchema.Bool),
        ("at", QdrantPayloadFieldSchema.DateTime),
        ("location", QdrantPayloadFieldSchema.Geo),
      ))
      assert(policy.collectionContractFingerprint.value != policy.candidateContractFingerprint.value)
    }

    "reject a foreign identity and a non-searchable embedding field" in {
      val foreignIdentity = field[QdrantTestFixtures.NeutralDocument, java.util.UUID]("foreignId", _.id).keyword
      val foreignEmbedding = field[QdrantTestFixtures.NeutralDocument, String]("foreign", _.title).keyword
      QdrantPolicy(
        PlanContractVersion("neutral-v1"),
        QdrantTestFixtures.document,
        foreignIdentity,
        foreignEmbedding,
        QdrantTestFixtures.policy.vectorName,
        QdrantTestFixtures.policy.embeddingModel,
        QdrantDistance.Cosine,
        QdrantTestFixtures.policy.retrieval,
      ) match {
        case Left(errors) =>
          assert(errors.toVector.contains(QdrantPolicyError.ForeignIdentityField(foreignIdentity.id)))
          assert(errors.toVector.contains(QdrantPolicyError.ForeignEmbeddingField(foreignEmbedding.id)))
        case Right(policy) => fail(s"expected policy rejection, got $policy")
      }
    }

    "reject non-finite retrieval thresholds" in {
      QdrantPolicy(
        PlanContractVersion("neutral-v1"),
        QdrantTestFixtures.document,
        QdrantTestFixtures.id,
        QdrantTestFixtures.title,
        QdrantTestFixtures.policy.vectorName,
        QdrantTestFixtures.policy.embeddingModel,
        QdrantDistance.Cosine,
        QdrantRetrievalPolicy(2, 1, Some(Double.NaN)),
      ) match {
        case Left(errors) =>
          errors.toVector match {
            case Vector(QdrantPolicyError.InvalidValue(QdrantValueError.NonFinite("score-threshold", value))) => assert(value.isNaN)
            case other => fail(s"expected one non-finite threshold error, got $other")
          }
        case Right(policy) => fail(s"expected non-finite threshold rejection, got $policy")
      }
    }
  }
}
