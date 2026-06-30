package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityMismatch,
  QdrantCollectionCompatibilityValidator,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCollectionCompatibilityValidatorSpec extends AnyWordSpec {
  "QdrantCollectionCompatibilityValidator" should {
    "validate compatible collection info successfully" in {
      val result = QdrantCollectionCompatibilityValidator.validate(expectation, collectionInfoJson())

      assert(result == Right(Right(())))
    }

    "use expected collection name when qdrant omits collection name" in {
      val result = QdrantCollectionCompatibilityValidator.validate(
        expectation,
        collectionInfoJson(includeObservedCollectionName = false),
      )

      assert(result == Right(Right(())))
    }

    "propagate decode failure" in {
      val json = Json.obj(
        "result" -> Json.obj(
          "config" -> Json.obj(
            "params" -> Json.obj()
          )
        )
      )

      assertFailure(
        QdrantCollectionCompatibilityValidator.validate(expectation, json),
        operationName = "validate-qdrant-collection-compatibility",
        expectedMessage = "Missing params.vectors in Qdrant collection info",
      )
    }

    "return collection name mismatch" in {
      val result = QdrantCollectionCompatibilityValidator.validate(
        expectation,
        collectionInfoJson(observedCollectionName = "other_collection"),
      )

      assert(result == Right(Left(List(
        QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expectation.collectionName, "other_collection")
      ))))
    }

    "return vector name mismatch" in {
      val result = QdrantCollectionCompatibilityValidator.validate(
        expectation,
        collectionInfoJson(observedVectorName = "other-vector"),
      )

      assert(result == Right(Left(List(
        QdrantCollectionCompatibilityMismatch.VectorNameMismatch(expectation.vectorName, "other-vector")
      ))))
    }

    "return dimension mismatch" in {
      val result = QdrantCollectionCompatibilityValidator.validate(
        expectation.copy(expectedDimension = 768),
        collectionInfoJson(),
      )

      assert(result == Right(Left(List(
        QdrantCollectionCompatibilityMismatch.DimensionMismatch(768, 1024)
      ))))
    }

    "return distance mismatch" in {
      val result = QdrantCollectionCompatibilityValidator.validate(
        expectation.copy(expectedDistance = VectorDistance.Dot),
        collectionInfoJson(),
      )

      assert(result == Right(Left(List(
        QdrantCollectionCompatibilityMismatch.DistanceMismatch(VectorDistance.Dot, VectorDistance.Cosine)
      ))))
    }

    "return embedding model mismatch only when observed metadata is present" in {
      val mismatchResult = QdrantCollectionCompatibilityValidator.validate(
        expectation.copy(embeddingModelName = "other-model"),
        collectionInfoJson(observedEmbeddingModelName = Some(expectation.embeddingModelName)),
      )
      val noMetadataResult = QdrantCollectionCompatibilityValidator.validate(
        expectation.copy(embeddingModelName = "other-model"),
        collectionInfoJson(observedEmbeddingModelName = None),
      )

      assert(mismatchResult == Right(Left(List(
        QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch("other-model", expectation.embeddingModelName)
      ))))
      assert(noMetadataResult == Right(Right(())))
    }

    "decode embedding model name from result config metadata even when an earlier metadata location is present but unrelated" in {
      val json = collectionInfoJson().deepMerge(
        Json.obj(
          "result" -> Json.obj(
            "metadata" -> Json.obj(),
            "config" -> Json.obj(
              "metadata" -> Json.obj(
                "embeddingModelName" -> expectation.embeddingModelName.asJson
              )
            ),
          )
        )
      )

      val result = QdrantCollectionCompatibilityValidator.validate(expectation, json)

      assert(result == Right(Right(())))
    }
  }

  private val expectation =
    QdrantCollectionCompatibilityExpectation(
      collectionName = "generic_document_v1_local_generic_embedding_document_embedding_1024_cosine",
      vectorName = "document-embedding",
      expectedDimension = 1024,
      expectedDistance = VectorDistance.Cosine,
      embeddingModelName = "generic-embedding",
    )

  private def collectionInfoJson(
    observedCollectionName: String = expectation.collectionName,
    observedVectorName: String = expectation.vectorName,
    observedEmbeddingModelName: Option[String] = None,
    includeObservedCollectionName: Boolean = true,
  ): Json =
    Json.obj(
      "result" -> Json.obj(
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              observedVectorName -> Json.obj(
                "size" -> 1024.asJson,
                "distance" -> "Cosine".asJson,
              )
            )
          )
        ),
      ).deepMerge(
        Option.when(includeObservedCollectionName)(
          Json.obj(
            "name" -> observedCollectionName.asJson
          )
        ).getOrElse(Json.obj())
      ).deepMerge(
        observedEmbeddingModelName.fold(Json.obj()) { embeddingModelName =>
          Json.obj(
            "metadata" -> Json.obj(
              "embeddingModelName" -> embeddingModelName.asJson
            )
          )
        }
      )
    )

  private def assertFailure(
    result: QdrantCollectionCompatibilityValidator.ValidationResult,
    operationName: String,
    expectedMessage: String,
  ): Unit =
    result match {
      case Left(QueryFailure.OperationFailure(`operationName`, message)) =>
        assert(message == expectedMessage): Unit
      case other =>
        fail(s"Expected $operationName failure '$expectedMessage', got $other")
    }
}
