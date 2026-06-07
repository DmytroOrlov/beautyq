package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionInfoClient,
}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class QdrantCollectionCompatibilityGuardSpec extends AnyWordSpec {
  "QdrantCollectionCompatibilityGuard" should {
    "succeed when checker reports compatible" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson()))

      val result = run(guard(client).requireCompatible(expectation))

      assert(result == ())
    }

    "preserve client QueryFailure" in {
      val failure = QueryFailure.operation("get-qdrant-collection-info", "qdrant failed")
      val client = new ConstQdrantCollectionInfoClient(Left(failure))

      val error = runFail(guard(client).requireCompatible(expectation))

      assert(error == failure)
    }

    "preserve decode QueryFailure" in {
      val client = new ConstQdrantCollectionInfoClient(Right(Json.obj(
        "result" -> Json.obj(
          "name" -> expectation.collectionName.asJson,
          "config" -> Json.obj(
            "params" -> Json.obj()
          )
        )
      )))

      val error = runFail(guard(client).requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("validate-qdrant-collection-compatibility", message) =>
          assert(message == "Missing params.vectors in Qdrant collection info"): Unit
        case other =>
          fail(s"Expected validate-qdrant-collection-compatibility failure, got $other")
      }
    }

    "convert one mismatch to QueryFailure.operation" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(
        observedVectorName = "other-vector"
      )))

      val error = runFail(guard(client).requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains(s"collection ${expectation.collectionName}"))
          assert(message.contains("VectorNameMismatch(expected=variant-embedding, observed=other-vector)"))
        case other =>
          fail(s"Expected qdrant-collection-compatibility failure, got $other")
      }
    }

    "convert multiple mismatches to QueryFailure.operation with useful details" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(
        observedCollectionName = "other_collection",
        observedVectorName = "other-vector",
        dimension = 768,
        distance = "Dot",
        observedEmbeddingModelName = Some("other-model"),
      )))

      val error = runFail(guard(client).requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains("qdrant-collection-compatibility failed"))
          assert(message.contains("CollectionNameMismatch(expected=beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine, observed=other_collection)"))
          assert(message.contains("VectorNameMismatch(expected=variant-embedding, observed=other-vector)"))
          assert(message.contains("DimensionMismatch(expected=1024, observed=768)"))
          assert(message.contains("DistanceMismatch(expected=Cosine, observed=Dot)"))
          assert(message.contains("EmbeddingModelMismatch(expected=llama-cpp-embedding, observed=other-model)"))
        case other =>
          fail(s"Expected qdrant-collection-compatibility failure, got $other")
      }
    }

    "convert distance-only mismatch to QueryFailure.operation" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(
        distance = "Dot",
      )))

      val error = runFail(guard(client).requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains("DistanceMismatch(expected=Cosine, observed=Dot)"))
        case other =>
          fail(s"Expected qdrant-collection-compatibility failure, got $other")
      }
    }

    "convert embedding model mismatch to QueryFailure.operation" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(
        observedEmbeddingModelName = Some("other-embedding-model"),
      )))

      val error = runFail(guard(client).requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains("EmbeddingModelMismatch(expected=llama-cpp-embedding, observed=other-embedding-model)"))
        case other =>
          fail(s"Expected qdrant-collection-compatibility failure, got $other")
      }
    }

    "fail with decode QueryFailure when result is null (params.vectors missing)" in {
      val client = new ConstQdrantCollectionInfoClient(Right(Json.obj(
        "result" -> Json.Null
      )))

      val error = runFail(guard(client).requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("validate-qdrant-collection-compatibility", message) =>
          assert(message == "Missing params.vectors in Qdrant collection info")
        case other =>
          fail(s"Expected validate-qdrant-collection-compatibility failure, got $other")
      }
    }

    "fail with decode QueryFailure when config is missing (params.vectors missing)" in {
      val client = new ConstQdrantCollectionInfoClient(Right(Json.obj(
        "result" -> Json.obj(
          "name" -> expectation.collectionName.asJson,
        )
      )))

      val error = runFail(guard(client).requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("validate-qdrant-collection-compatibility", message) =>
          assert(message == "Missing params.vectors in Qdrant collection info")
        case other =>
          fail(s"Expected validate-qdrant-collection-compatibility failure, got $other")
      }
    }
  }

  private val expectation =
    QdrantCollectionCompatibilityExpectation(
      collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
      vectorName = "variant-embedding",
      expectedDimension = 1024,
      expectedDistance = VectorDistance.Cosine,
      embeddingModelName = "llama-cpp-embedding",
    )

  private def guard(client: QdrantCollectionInfoClient): QdrantCollectionCompatibilityGuard =
    new QdrantCollectionCompatibilityGuard(new QdrantCollectionCompatibilityChecker(client))

  private final class ConstQdrantCollectionInfoClient(result: Either[QueryFailure, Json]) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fromEither(result)
  }

  private def collectionInfoJson(
    observedCollectionName: String = expectation.collectionName,
    observedVectorName: String = expectation.vectorName,
    dimension: Int = expectation.expectedDimension,
    distance: String = "Cosine",
    observedEmbeddingModelName: Option[String] = None,
  ): Json =
    Json.obj(
      "result" -> Json.obj(
        "name" -> observedCollectionName.asJson,
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              observedVectorName -> Json.obj(
                "size" -> dimension.asJson,
                "distance" -> distance.asJson,
              )
            )
          )
        ),
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

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }
}
