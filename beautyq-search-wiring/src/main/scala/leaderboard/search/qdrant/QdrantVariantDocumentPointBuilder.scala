package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, VariantSearchDocument}

object QdrantVariantDocumentPointBuilder extends QdrantDocumentPointBuilder[VariantSearchDocument] {
  private val delegate: QdrantDocumentPointBuilder[VariantSearchDocument] =
    QdrantDocumentPointBuilder.fromPayloadSpec(BeautyQVariantSearchDocumentContract.qdrantPayloadSpec)

  override def qdrantPointId(document: VariantSearchDocument): Either[QueryFailure, QdrantPointId] =
    delegate.qdrantPointId(document)

  def pointId(document: VariantSearchDocument): String =
    BeautyQVariantSearchDocumentContract.documentSpec.id(document)

  def upsertPointJson(document: VariantSearchDocument, vectorName: String, vector: List[Double]): Json =
    QdrantJsonInterpreter.upsertPointJson(pointId(document), vectorName, vector, payload(document))

  override def payload(document: VariantSearchDocument): Map[String, Json] =
    delegate.payload(document)
}
