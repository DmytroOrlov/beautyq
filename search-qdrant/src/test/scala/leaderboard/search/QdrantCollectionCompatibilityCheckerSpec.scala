package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityMismatch,
  QdrantCollectionInfoClient,
}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

final class QdrantCollectionCompatibilityCheckerSpec extends AnyWordSpec {
  "QdrantCollectionCompatibilityChecker" should {
    "call expected collection info path" in {
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val client = new FakeQdrantCollectionInfoClient(pathRef, Right(collectionInfoJson()))

      val result = run(new QdrantCollectionCompatibilityChecker(client).check(expectation))

      assert(runUio(pathRef.get).contains(s"/collections/${expectation.collectionName}"))
      assert(result == Right(()))
    }

    "return success for compatible json" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson()))

      val result = run(new QdrantCollectionCompatibilityChecker(client).check(expectation))

      assert(result == Right(()))
    }

    "return success when qdrant omits collection name" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(includeObservedCollectionName = false)))

      val result = run(new QdrantCollectionCompatibilityChecker(client).check(expectation))

      assert(result == Right(()))
    }

    "propagate client QueryFailure" in {
      val failure = QueryFailure.operation("get-qdrant-collection-info", "qdrant failed")
      val client = new ConstQdrantCollectionInfoClient(Left(failure))

      val error = runFail(new QdrantCollectionCompatibilityChecker(client).check(expectation))

      assert(error == failure)
    }

    "propagate validator decode QueryFailure" in {
      val json = Json.obj(
        "result" -> Json.obj(
          "name" -> expectation.collectionName.asJson,
          "config" -> Json.obj(
            "params" -> Json.obj()
          )
        )
      )
      val client = new ConstQdrantCollectionInfoClient(Right(json))

      val error = runFail(new QdrantCollectionCompatibilityChecker(client).check(expectation))

      error match {
        case QueryFailure.OperationFailure("validate-qdrant-collection-compatibility", message) =>
          assert(message == "Missing params.vectors in Qdrant collection info"): Unit
        case other =>
          fail(s"Expected validate-qdrant-collection-compatibility failure, got $other")
      }
    }

    "return compatibility mismatches unchanged" in {
      val client = new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(
        observedCollectionName = "other_collection",
        observedVectorName = "other-vector",
        dimension = 768,
        distance = "Dot",
        observedEmbeddingModelName = Some("other-model"),
      )))

      val result = run(new QdrantCollectionCompatibilityChecker(client).check(expectation))

      assert(result == Left(List(
        QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expectation.collectionName, "other_collection"),
        QdrantCollectionCompatibilityMismatch.VectorNameMismatch(expectation.vectorName, "other-vector"),
        QdrantCollectionCompatibilityMismatch.DimensionMismatch(expectation.expectedDimension, 768),
        QdrantCollectionCompatibilityMismatch.DistanceMismatch(expectation.expectedDistance, VectorDistance.Dot),
        QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch(expectation.embeddingModelName, "other-model"),
      )))
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

  private final class FakeQdrantCollectionInfoClient(
    pathRef: Ref[Option[String]],
    result: Either[QueryFailure, Json],
  ) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      pathRef.set(Some(path)) *> ZIO.fromEither(result)
  }

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
    includeObservedCollectionName: Boolean = true,
  ): Json =
    Json.obj(
      "result" -> Json.obj(
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

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runUio[A](effect: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(fail("expected failure"))
    }

  private type UIO[A] = zio.UIO[A]
}
