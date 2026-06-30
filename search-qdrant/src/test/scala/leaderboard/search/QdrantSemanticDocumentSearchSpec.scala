package leaderboard.search

import io.circe.{Json, JsonObject}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{SearchField, SearchFieldKind, SearchValue, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{QdrantJsonInterpreter, QdrantSearchClient, QdrantSearchHit, QdrantSemanticDocumentSearch}
import leaderboard.search.semantic.SemanticDocumentHit
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

final class QdrantSemanticDocumentSearchSpec extends AnyWordSpec {
  "QdrantSemanticDocumentSearch" should {
    "embed exact query text, call expected Qdrant path and request json, and return semantic document hits" in {
      val spec = VectorSearchSpec("toy-semantic", "toy-vector", topK = 2, scoreThreshold = Some(0.4))
      val queryText = "exact semantic query"
      val vector = Vector(0.1, 0.2, 0.3)
      val hits = List(
        QdrantSearchHit("point-1", JsonObject.fromMap(Map("documentKey" -> Json.fromString("doc-a"))), 0.81),
        QdrantSearchHit("point-2", JsonObject.fromMap(Map("documentKey" -> Json.fromString("doc-b"))), 0.73),
      )
      val queryRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new ExpectingEmbeddingClient(queryRef, Right(vector))
      val searchClient = new ExpectingQdrantSearchClient(pathRef, jsonRef, Right(hits))

      val result = run(new QdrantSemanticDocumentSearch[ToyDocument, ToyDocumentId](embeddingClient, searchClient, documentKeyField).search(queryText, spec))

      assert(runUio(queryRef.get).contains(queryText))
      assert(runUio(pathRef.get).contains("/collections/toy-semantic/points/search"))
      assert(runUio(jsonRef.get).contains(QdrantJsonInterpreter.searchRequestJson(spec, vector.toList)))
      assert(result == List(
        SemanticDocumentHit(ToyDocumentId("doc-a"), 0.81),
        SemanticDocumentHit(ToyDocumentId("doc-b"), 0.73),
      ))
    }

    "propagate decoder failure" in {
      val spec = VectorSearchSpec("toy-semantic", "toy-vector", topK = 1, scoreThreshold = None)
      val embeddingClient = new ConstEmbeddingClient(Right(Vector(0.5, 0.4)))
      val searchClient = new ConstQdrantSearchClient(Right(List(QdrantSearchHit("point-missing", JsonObject.empty, 0.3))))

      val error = runFail(new QdrantSemanticDocumentSearch[ToyDocument, ToyDocumentId](embeddingClient, searchClient, documentKeyField).search("query", spec))

      assert(error.message.contains("Missing payload.documentKey for Qdrant hit point-missing"))
    }

    "propagate embedding failure" in {
      val spec = VectorSearchSpec("toy-semantic", "toy-vector", topK = 1, scoreThreshold = None)
      val failure = QueryFailure.operation("embed-query", "embedding failed")
      val embeddingClient = new ConstEmbeddingClient(Left(failure))
      val searchClient = new ConstQdrantSearchClient(Right(Nil))

      val error = runFail(new QdrantSemanticDocumentSearch[ToyDocument, ToyDocumentId](embeddingClient, searchClient, documentKeyField).search("query", spec))

      assert(error == failure)
    }

    "propagate Qdrant search failure" in {
      val spec = VectorSearchSpec("toy-semantic", "toy-vector", topK = 1, scoreThreshold = None)
      val failure = QueryFailure.operation("search-qdrant-points", "qdrant failed")
      val embeddingClient = new ConstEmbeddingClient(Right(Vector(0.5, 0.4)))
      val searchClient = new ConstQdrantSearchClient(Left(failure))

      val error = runFail(new QdrantSemanticDocumentSearch[ToyDocument, ToyDocumentId](embeddingClient, searchClient, documentKeyField).search("query", spec))

      assert(error == failure)
    }
  }

  private final case class ToyDocument(
    documentKey: ToyDocumentId,
  )

  private final case class ToyDocumentId(value: String)

  private object ToyDocumentId {
    implicit val decoder: io.circe.Decoder[ToyDocumentId] =
      io.circe.Decoder.decodeString.map(ToyDocumentId.apply)
  }

  private val documentKeyField: SearchField[ToyDocument] =
    SearchField(
      path = "documentKey",
      kind = SearchFieldKind.Keyword,
      extract = document => Some(SearchValue.Keyword(document.documentKey.value)),
    )

  private final class ExpectingEmbeddingClient(
    queryRef: Ref[Option[String]],
    result: Either[QueryFailure, Vector[Double]],
  ) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      queryRef.set(Some(text)) *> ZIO.fromEither(result)
  }

  private final class ExpectingQdrantSearchClient(
    pathRef: Ref[Option[String]],
    jsonRef: Ref[Option[Json]],
    result: Either[QueryFailure, List[QdrantSearchHit]],
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      pathRef.set(Some(path)) *> jsonRef.set(Some(json)) *> ZIO.fromEither(result)
  }

  private final class ConstEmbeddingClient(result: Either[QueryFailure, Vector[Double]]) extends EmbeddingClient {
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
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(fail("expected failure"))
    }

  private type UIO[A] = zio.UIO[A]
}
