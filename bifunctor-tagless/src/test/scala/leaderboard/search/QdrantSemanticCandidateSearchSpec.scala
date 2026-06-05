package leaderboard.search

import io.circe.{Json, JsonObject}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{QdrantCandidateHit, QdrantJsonInterpreter, QdrantSearchClient, QdrantSearchHit, QdrantSemanticCandidateSearch}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantSemanticCandidateSearchSpec extends AnyWordSpec {
  "QdrantSemanticCandidateSearch" should {
    "embed the exact query text, send the expected path and request json, and preserve decoded hit order and scores" in {
      val spec = VectorSearchSpec(
        collectionName = "beauty-semantic",
        vectorName = "variant-embedding",
        topK = 3,
        scoreThreshold = Some(0.42),
      )
      val queryText = "маникюр рядом"
      val vector = Vector(0.1, 0.2, 0.3)
      val firstVariantId = UUID.fromString("00000000-0000-0000-0000-000000000111")
      val secondVariantId = UUID.fromString("00000000-0000-0000-0000-000000000222")
      val hits = List(
        QdrantSearchHit(
          id = "point-1",
          payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(firstVariantId.toString))),
          score = 0.91,
        ),
        QdrantSearchHit(
          id = "point-2",
          payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(secondVariantId.toString))),
          score = 0.73,
        ),
      )

      val queryRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(queryRef, Right(vector))
      val searchClient = new FakeQdrantSearchClient(pathRef, jsonRef, Right(hits))

      val result = run(new QdrantSemanticCandidateSearch(embeddingClient, searchClient).search(queryText, spec))

      assert(runUio(queryRef.get).contains(queryText))
      assert(runUio(pathRef.get).contains("/collections/beauty-semantic/points/search"))
      assert(runUio(jsonRef.get).contains(QdrantJsonInterpreter.searchRequestJson(spec, vector.toList)))
      assert(result == List(
        QdrantCandidateHit(firstVariantId, 0.91),
        QdrantCandidateHit(secondVariantId, 0.73),
      ))
    }

    "propagate decoder failure for missing payload.variantId" in {
      val spec = VectorSearchSpec(
        collectionName = "beauty-semantic",
        vectorName = "variant-embedding",
        topK = 1,
        scoreThreshold = None,
      )
      val embeddingClient = new ConstEmbeddingClient(Vector(0.5, 0.4))
      val searchClient = new ConstQdrantSearchClient(Right(List(
        QdrantSearchHit(
          id = "point-missing-variant-id",
          payload = JsonObject.empty,
          score = 0.66,
        )
      )))

      val error = runFail(new QdrantSemanticCandidateSearch(embeddingClient, searchClient).search("query", spec))

      assert(error.message.contains("Missing payload.variantId"))
      assert(error.message.contains("point-missing-variant-id"))
    }

    "propagate embedding failure" in {
      val spec = VectorSearchSpec(
        collectionName = "beauty-semantic",
        vectorName = "variant-embedding",
        topK = 1,
        scoreThreshold = None,
      )
      val failure = QueryFailure.operation("embed-query", "embedding failed")
      val embeddingClient = new ConstEmbeddingClient(failure)
      val searchClient = new ConstQdrantSearchClient(Right(Nil))

      val error = runFail(new QdrantSemanticCandidateSearch(embeddingClient, searchClient).search("query", spec))

      assert(error == failure)
    }

    "propagate qdrant search failure" in {
      val spec = VectorSearchSpec(
        collectionName = "beauty-semantic",
        vectorName = "variant-embedding",
        topK = 1,
        scoreThreshold = None,
      )
      val failure = QueryFailure.operation("search-qdrant-points", "qdrant failed")
      val embeddingClient = new ConstEmbeddingClient(Vector(0.5, 0.4))
      val searchClient = new ConstQdrantSearchClient(Left(failure))

      val error = runFail(new QdrantSemanticCandidateSearch(embeddingClient, searchClient).search("query", spec))

      assert(error == failure)
    }
  }

  private final class FakeEmbeddingClient(
    queryRef: Ref[Option[String]],
    result: Either[QueryFailure, Vector[Double]],
  ) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      queryRef.set(Some(text)) *> ZIO.fromEither(result)
  }

  private final class FakeQdrantSearchClient(
    pathRef: Ref[Option[String]],
    jsonRef: Ref[Option[Json]],
    result: Either[QueryFailure, List[QdrantSearchHit]],
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      pathRef.set(Some(path)) *> jsonRef.set(Some(json)) *> ZIO.fromEither(result)
  }

  private final class ConstEmbeddingClient(result: Either[QueryFailure, Vector[Double]]) extends EmbeddingClient {
    def this(vector: Vector[Double]) = this(Right(vector))
    def this(failure: QueryFailure) = this(Left(failure))

    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.fromEither(result)
  }

  private final class ConstQdrantSearchClient(result: Either[QueryFailure, List[QdrantSearchHit]]) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.fromEither(result)
  }

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
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }

  private type UIO[A] = zio.UIO[A]
}
