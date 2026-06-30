package leaderboard.search

import io.circe.{Json, JsonObject}
import leaderboard.search.dsl.{SearchField, SearchFieldKind, SearchValue}
import leaderboard.search.qdrant.{QdrantCandidateHitDecoder, QdrantSearchHit}
import leaderboard.search.semantic.SemanticDocumentHit
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCandidateHitDecoderSpec extends AnyWordSpec {
  "QdrantCandidateHitDecoder" should {
    "decode non-BeautyQ document ids into semantic document hits and preserve hit order and scores" in {
      val hits = List(
        QdrantSearchHit("point-1", JsonObject.fromMap(Map("documentKey" -> Json.fromString("doc-2"))), 0.42),
        QdrantSearchHit("point-2", JsonObject.fromMap(Map("documentKey" -> Json.fromString("doc-1"))), 0.91),
      )

      val result = QdrantCandidateHitDecoder.decode[ToyDocument, ToyDocumentId](hits, documentKeyField)

      assert(result == Right(List(
        SemanticDocumentHit(ToyDocumentId("doc-2"), 0.42),
        SemanticDocumentHit(ToyDocumentId("doc-1"), 0.91),
      )))
    }

    "support a custom payload field path" in {
      val customField = SearchField[ToyDocument](
        path = "ids.semantic",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.documentKey.value)),
      )
      val hits = List(QdrantSearchHit("point-custom", JsonObject.fromMap(Map("ids.semantic" -> Json.fromString("doc-custom"))), 0.77))

      val result = QdrantCandidateHitDecoder.decode[ToyDocument, ToyDocumentId](hits, customField)

      assert(result == Right(List(SemanticDocumentHit(ToyDocumentId("doc-custom"), 0.77))))
    }

    "use payload path in missing id errors" in {
      val result = QdrantCandidateHitDecoder.decode[ToyDocument, ToyDocumentId](
        List(QdrantSearchHit("point-missing", JsonObject.empty, 0.12)),
        documentKeyField,
      )

      assert(result.left.exists(_.message.contains("Missing payload.documentKey for Qdrant hit point-missing")))
    }

    "use payload path in invalid id errors" in {
      val result = QdrantCandidateHitDecoder.decode[ToyDocument, ToyDocumentId](
        List(QdrantSearchHit("point-invalid", JsonObject.fromMap(Map("documentKey" -> Json.fromInt(7))), 0.12)),
        documentKeyField,
      )

      assert(result.left.exists(_.message.contains("Invalid payload.documentKey for Qdrant hit point-invalid: 7")))
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
}
