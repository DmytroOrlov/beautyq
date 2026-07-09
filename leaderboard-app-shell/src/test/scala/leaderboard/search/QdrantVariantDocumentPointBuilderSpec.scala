package leaderboard.search

import io.circe.Json
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, VariantSearchDocument}
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.qdrant.{QdrantJsonInterpreter, QdrantVariantDocumentPointBuilder}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantVariantDocumentPointBuilderSpec extends AnyWordSpec {
  "QdrantVariantDocumentPointBuilder" should {
    "build variant document upsert point json using the existing Qdrant convention" in {
      val document = variantDocument()
      val vectorName = "variant-semantic-vector"
      val vector = List(0.125, -0.25, 0.5)

      val json = QdrantVariantDocumentPointBuilder.upsertPointJson(document, vectorName, vector)
      val point = json.hcursor.downField("points").downArray
      val payload = point.downField("payload")
      val expectedPayload = Map(
        "variantId" -> Json.fromString(document.variantId.toString),
        "masterLocationId" -> Json.fromString(document.masterLocationId.toString),
        "serviceId" -> Json.fromString(document.serviceId.toString),
        "serviceName" -> Json.fromString(document.serviceName),
      )

      assert(QdrantVariantDocumentPointBuilder.pointId(document) == document.variantId.toString)
      assert(BeautyQVariantSearchDocumentContract.qdrantPayloadSpec.payload(document) == Right(expectedPayload))
      assert(QdrantVariantDocumentPointBuilder.payload(document) == expectedPayload)
      assert(BeautyQVariantSearchDocumentContract.qdrantPayloadSpec.payload(document).contains(QdrantVariantDocumentPointBuilder.payload(document)))
      assert(point.downField("id").as[String] == Right(document.variantId.toString))
      assert(point.downField("vector").downField(vectorName).as[List[Double]] == Right(vector))
      assert(payload.downField("variantId").as[String] == Right(document.variantId.toString))
      assert(payload.downField("masterLocationId").as[String] == Right(document.masterLocationId.toString))
      assert(payload.downField("serviceId").as[String] == Right(document.serviceId.toString))
      assert(payload.downField("serviceName").as[String] == Right(document.serviceName))
      assert(json == QdrantJsonInterpreter.upsertPointJson(
        document.variantId.toString,
        vectorName,
        vector,
        QdrantVariantDocumentPointBuilder.payload(document),
      ))
    }
  }

  private def variantDocument(): VariantSearchDocument =
    VariantSearchDocument(
      variantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000101")),
      masterServiceOfferId = MasterServiceOfferId(UUID.fromString("00000000-0000-0000-0000-000000000202")),
      masterLocationId = MasterLocationId(UUID.fromString("00000000-0000-0000-0000-000000000303")),
      masterId = MasterId(UUID.fromString("00000000-0000-0000-0000-000000000404")),
      serviceId = ServiceId(UUID.fromString("00000000-0000-0000-0000-000000000505")),
      categoryId = CategoryId(UUID.fromString("00000000-0000-0000-0000-000000000606")),
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Beauty Master",
      locationName = "Central Studio",
      address = "Main street 1",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = "manicure nails beauty master central studio",
      serviceText = "manicure nails",
      attributeText = "coverage gel with removal",
      providerText = "beauty master central studio",
      locationText = "central studio main street 1 nails",
    )
}
