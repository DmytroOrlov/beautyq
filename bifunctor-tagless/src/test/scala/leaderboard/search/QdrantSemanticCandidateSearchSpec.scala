package leaderboard.search

import io.circe.{Json, JsonObject}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{QdrantJsonInterpreter, QdrantSearchClient, QdrantSearchHit, QdrantSemanticCandidateSearch}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantSemanticCandidateSearchSpec extends AnyWordSpec {
  private val spec = VectorSearchSpec(
    collectionName = "variant_candidates",
    vectorName = "semantic-vector",
    topK = 3,
    scoreThreshold = Some(0.7),
  )
  private val queryText = "semantic nail care query"
  private val embedding = Vector(0.1, 0.2, 0.3)
  private val expectedPath = "/collections/variant_candidates/points/search"
  private val expectedJson = QdrantJsonInterpreter.searchRequestJson(spec, embedding.toList)
  private val firstVariantId = UUID.fromString("00000000-0000-0000-0000-000000000101")
  private val secondVariantId = UUID.fromString("00000000-0000-0000-0000-000000000202")

  "QdrantSemanticCandidateSearch" should {
    "embed query text and search Qdrant with vector search JSON" in {
      val embeddingClient = new RecordingEmbeddingClient(embedding)
      val searchClient = new RecordingSearchClient(
        List(
          hit("qdrant-point-a", firstVariantId.toString, 0.91),
          hit("qdrant-point-b", secondVariantId.toString, 0.82),
        )
      )
      val service = new QdrantSemanticCandidateSearch(embeddingClient, searchClient)

      val result = run(service.search(queryText, spec))

      assert(embeddingClient.receivedTexts == List(queryText))
      assert(searchClient.receivedRequests == List(expectedPath -> expectedJson))
      assert(result.map(_.variantId) == List(firstVariantId, secondVariantId))
      assert(result.map(_.score) == List(0.91, 0.82))
    }

    "preserve Qdrant hit order" in {
      val service = new QdrantSemanticCandidateSearch(
        new RecordingEmbeddingClient(embedding),
        new RecordingSearchClient(
          List(
            hit("second-score", secondVariantId.toString, 0.2),
            hit("first-score", firstVariantId.toString, 0.99),
          )
        ),
      )

      val result = run(service.search(queryText, spec))

      assert(result.map(_.variantId) == List(secondVariantId, firstVariantId))
      assert(result.map(_.score) == List(0.2, 0.99))
    }

    "fail when variantId payload is missing" in {
      val service = new QdrantSemanticCandidateSearch(
        new RecordingEmbeddingClient(embedding),
        new RecordingSearchClient(List(QdrantSearchHit(firstVariantId.toString, JsonObject.empty, 0.5))),
      )

      val error = runFail(service.search(queryText, spec))

      assert(error.message.contains("Missing payload variantId"))
      assert(error.message.contains(firstVariantId.toString))
    }

    "fail when variantId payload is not a valid UUID" in {
      val service = new QdrantSemanticCandidateSearch(
        new RecordingEmbeddingClient(embedding),
        new RecordingSearchClient(List(hit("point-with-invalid-variant", "not-a-uuid", 0.5))),
      )

      val error = runFail(service.search(queryText, spec))

      assert(error.message.contains("Invalid payload variantId not-a-uuid"))
    }
  }

  private def hit(id: String, variantId: String, score: Double): QdrantSearchHit =
    QdrantSearchHit(
      id = id,
      payload = JsonObject("variantId" -> Json.fromString(variantId)),
      score = score,
    )

  private final class RecordingEmbeddingClient(vector: Vector[Double]) extends EmbeddingClient {
    var receivedTexts: List[String] = Nil

    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.succeed {
        receivedTexts = receivedTexts :+ text
        vector
      }
  }

  private final class RecordingSearchClient(hits: List[QdrantSearchHit]) extends QdrantSearchClient {
    var receivedRequests: List[(String, Json)] = Nil

    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.succeed {
        receivedRequests = receivedRequests :+ (path -> json)
        hits
      }
  }

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }
}
