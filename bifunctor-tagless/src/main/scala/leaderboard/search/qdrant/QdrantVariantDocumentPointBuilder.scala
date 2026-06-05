package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.search.document.VariantSearchDocument

object QdrantVariantDocumentPointBuilder {
  def upsertPointJson(document: VariantSearchDocument, vectorName: String, vector: List[Double]): Json =
    QdrantJsonInterpreter.upsertPointJson(pointId(document), vectorName, vector, payload(document))

  def pointId(document: VariantSearchDocument): String =
    document.variantId.toString

  def payload(document: VariantSearchDocument): Map[String, Json] =
    Map(
      "variantId" -> Json.fromString(document.variantId.toString),
      "masterLocationId" -> Json.fromString(document.masterLocationId.toString),
      "serviceId" -> Json.fromString(document.serviceId.toString),
      "serviceName" -> Json.fromString(document.serviceName),
    )
}
