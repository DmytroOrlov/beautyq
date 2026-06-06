package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocument

object QdrantVariantDocumentPointBuilder extends QdrantDocumentPointBuilder[VariantSearchDocument] {
  override def qdrantPointId(document: VariantSearchDocument): Either[QueryFailure, QdrantPointId] =
    QdrantPointId.fromUuidString(pointId(document))

  def pointId(document: VariantSearchDocument): String =
    document.variantId.toString

  def upsertPointJson(document: VariantSearchDocument, vectorName: String, vector: List[Double]): Json =
    QdrantJsonInterpreter.upsertPointJson(pointId(document), vectorName, vector, payload(document))

  override def payload(document: VariantSearchDocument): Map[String, Json] =
    Map(
      "variantId" -> Json.fromString(document.variantId.toString),
      "masterLocationId" -> Json.fromString(document.masterLocationId.toString),
      "serviceId" -> Json.fromString(document.serviceId.toString),
      "serviceName" -> Json.fromString(document.serviceName),
    )
}
