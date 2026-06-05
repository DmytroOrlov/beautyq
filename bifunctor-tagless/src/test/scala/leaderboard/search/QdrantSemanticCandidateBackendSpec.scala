package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{QdrantCandidateHit, QdrantSearchClient, QdrantSearchHit, QdrantSemanticCandidateBackend, QdrantSemanticCandidateSearch}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantSemanticCandidateBackendSpec extends AnyWordSpec {
  "QdrantSemanticCandidateBackend" should {
    "pass input.query to the helper, use the configured VectorSearchSpec, and return hits unchanged" in {
      val spec = VectorSearchSpec(
        collectionName = "beauty-semantic",
        vectorName = "variant-embedding",
        topK = 2,
        scoreThreshold = Some(0.55),
      )
      val input = UserSearchInput(query = "shellac entfernen", userLat = None, userLon = None, limit = 10)
      val intent = ParsedSearchIntent(
        originalQuery = input.query,
        normalizedTokens = List("shellac", "entfernen"),
        explicitConstraints = Nil,
        softBoosts = Nil,
        remainingText = "маникюр рядом",
      )
      val vector = Vector(0.8, 0.1, 0.4)
      val hits = List(
        QdrantSearchHit(
          id = "point-1",
          payload = io.circe.JsonObject.fromMap(Map("variantId" -> Json.fromString("00000000-0000-0000-0000-000000000011"))),
          score = 0.93,
        ),
        QdrantSearchHit(
          id = "point-2",
          payload = io.circe.JsonObject.fromMap(Map("variantId" -> Json.fromString("00000000-0000-0000-0000-000000000022"))),
          score = 0.81,
        ),
      )
      val queryRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val embeddingClient = new RecordingEmbeddingClient(queryRef, Right(vector))
      val searchClient = new RecordingQdrantSearchClient(pathRef, Right(hits))
      val backend = new QdrantSemanticCandidateBackend(new QdrantSemanticCandidateSearch(embeddingClient, searchClient), spec)

      val result = run(backend.candidates(input, intent))

      assert(runUio(queryRef.get).contains(input.query))
      assert(!runUio(queryRef.get).contains(intent.remainingText))
      assert(runUio(pathRef.get).contains("/collections/beauty-semantic/points/search"))
      assert(result == List(
        QdrantCandidateHit(UUID.fromString("00000000-0000-0000-0000-000000000011"), 0.93),
        QdrantCandidateHit(UUID.fromString("00000000-0000-0000-0000-000000000022"), 0.81),
      ))
    }

    "propagate helper QueryFailure unchanged" in {
      val spec = VectorSearchSpec(
        collectionName = "beauty-semantic",
        vectorName = "variant-embedding",
        topK = 5,
        scoreThreshold = None,
      )
      val failure = QueryFailure.operation("search-qdrant-points", "qdrant unavailable")
      val backend = new QdrantSemanticCandidateBackend(
        new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.2, 0.6)),
          new ConstQdrantSearchClient(Left(failure)),
        ),
        spec,
      )

      val error = runFail(backend.candidates(
        UserSearchInput(query = "pedicure", userLat = None, userLon = None),
        ParsedSearchIntent("pedicure", List("pedicure"), Nil, Nil, "ignored residual text"),
      ))

      assert(error == failure)
    }
  }

  private final class RecordingEmbeddingClient(
    queryRef: Ref[Option[String]],
    result: Either[QueryFailure, Vector[Double]],
  ) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      queryRef.set(Some(text)) *> ZIO.fromEither(result)
  }

  private final class RecordingQdrantSearchClient(
    pathRef: Ref[Option[String]],
    result: Either[QueryFailure, List[QdrantSearchHit]],
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      pathRef.set(Some(path)) *> ZIO.fromEither(result)
  }

  private final class ConstEmbeddingClient(result: Either[QueryFailure, Vector[Double]]) extends EmbeddingClient {
    def this(vector: Vector[Double]) = this(Right(vector))

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
