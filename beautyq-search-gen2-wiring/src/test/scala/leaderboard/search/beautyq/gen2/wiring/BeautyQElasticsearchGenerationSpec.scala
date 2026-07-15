package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshot, MaterializedBeautyQVariantDocuments}
import leaderboard.search.gen2.contract.GeoPoint
import leaderboard.search.gen2.core.materialization.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.{Json, JsonObject}

import java.time.Instant
import java.util.UUID

/** Concrete BeautyQ proofs for `BeautyQElasticsearchGeneration`, compiling one representative
  * `VariantSearchDocumentGen2` through the real declaration/policy - never a manual BeautyQ encoder or a
  * second, hand-maintained field inventory.
  */
final class BeautyQElasticsearchGenerationSpec extends AnyWordSpec {
  import BeautyQSearchDeclarations.variants.Fields

  private val fixtureDocument: VariantSearchDocumentGen2 =
    VariantSearchDocumentGen2(
      variantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
      masterServiceOfferId = MasterServiceOfferId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
      masterLocationId = MasterLocationId(UUID.fromString("00000000-0000-0000-0000-000000000003")),
      masterId = MasterId(UUID.fromString("00000000-0000-0000-0000-000000000004")),
      serviceId = ServiceId(UUID.fromString("00000000-0000-0000-0000-000000000005")),
      serviceCode = ServiceCode.fromString("manicure").getOrElse(fail("expected a valid ServiceCode fixture")),
      categoryId = CategoryId(UUID.fromString("00000000-0000-0000-0000-000000000006")),
      categoryCode = CategoryCode.fromString("nails").getOrElse(fail("expected a valid CategoryCode fixture")),
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Jane Doe",
      locationName = "Downtown Studio",
      address = "1 Main St",
      location = GeoPoint(BigDecimal("40.7128"), BigDecimal("74.0059")),
      lat = BigDecimal("40.7128"),
      lon = BigDecimal("74.0059"),
      priceFrom = BigDecimal("20.5"),
      priceTo = BigDecimal("40.75"),
      durationMin = 45,
      enumAttributes = Map("nail_coating_type" -> "gel_polish"),
      booleanAttributes = Map("with_design" -> true),
      intAttributes = Map("session_count" -> 1),
      bigDecimalAttributes = Map("deposit_amount" -> BigDecimal("10.25")),
      allText = "manicure nails gel polish",
      serviceText = "manicure nails",
      attributeText = "gel polish",
      providerText = "jane doe downtown studio",
      locationText = "downtown studio 1 main st nails",
    )

  // The ES generation compiler never reads the snapshot's own value, only its contentFingerprint; an
  // empty snapshot is a legitimate fixture for that reason.
  private val emptySnapshot = BeautyQSearchSnapshot(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  private val materialized: MaterializedBeautyQVariantDocuments =
    MaterializedSearchDocuments(
      sourceSnapshot = VersionedSnapshot(emptySnapshot, ContentFingerprint("content-fp-1"), Some(SourceRevision("rev-1")), Instant.parse("2024-01-01T00:00:00Z")),
      documents = Vector(fixtureDocument),
      projectedDocumentsFingerprint = ProjectedDocumentsFingerprint("projected-fp-1"),
      projectionFormatVersion = ProjectionFormatVersion("beautyq-projection-v1"),
    )

  private def propertiesOf(json: Json): JsonObject =
    json.asObject.flatMap(_.apply("properties")).flatMap(_.asObject).getOrElse(fail("expected a properties object"))

  private def compiledOrFail: CompiledBeautyQElasticsearchGeneration =
    BeautyQElasticsearchGeneration.compile(materialized) match {
      case Right(compiled) => compiled
      case Left(error)     => fail(s"expected successful BeautyQ ES generation, got $error")
    }

  private def onlyDocumentOrFail = compiledOrFail.documents match {
    case Vector(one) => one
    case other        => fail(s"expected exactly one compiled document, got $other")
  }

  "BeautyQElasticsearchGeneration.compile" should {
    "produce a mapping that includes every static declaration field" in {
      val mappingProps = propertiesOf(compiledOrFail.mapping.json)
      Fields.staticFields.foreach(field => assert(mappingProps.contains(field.id.value), s"expected mapping to contain static field '${field.id.value}'"))
    }

    "produce a mapping with nested dynamic attribute objects for every dynamic family, containing this fixture's own attribute codes" in {
      val mappingProps = propertiesOf(compiledOrFail.mapping.json)

      val intAttributesProps = propertiesOf(mappingProps("intAttributes").getOrElse(fail("expected intAttributes in the mapping")))
      assert(intAttributesProps.contains("session_count"))

      val decimalAttributesProps = propertiesOf(mappingProps("bigDecimalAttributes").getOrElse(fail("expected bigDecimalAttributes in the mapping")))
      assert(decimalAttributesProps.contains("deposit_amount"))

      val enumAttributesProps = propertiesOf(mappingProps("enumAttributes").getOrElse(fail("expected enumAttributes in the mapping")))
      assert(enumAttributesProps.contains("nail_coating_type"))

      val booleanAttributesProps = propertiesOf(mappingProps("booleanAttributes").getOrElse(fail("expected booleanAttributes in the mapping")))
      assert(booleanAttributesProps.contains("with_design"))
    }

    "compile the representative BeautyQ document without any manual BeautyQ encoder" in {
      assert(compiledOrFail.documents.size == 1)
      assert(onlyDocumentOrFail.id == "00000000-0000-0000-0000-000000000001")
    }

    "encode this fixture's exact canonical UUID/code/decimal/geo values in the compiled source" in {
      val sourceObj = onlyDocumentOrFail.source.asObject.getOrElse(fail("expected a source object"))

      assert(sourceObj("variantId") == Some(Json.fromString("00000000-0000-0000-0000-000000000001")))
      assert(sourceObj("serviceCode") == Some(Json.fromString("manicure")))
      assert(sourceObj("categoryCode") == Some(Json.fromString("nails")))
      assert(sourceObj("priceFrom") == Some(Json.fromBigDecimal(BigDecimal("20.5"))))
      assert(sourceObj("priceTo") == Some(Json.fromBigDecimal(BigDecimal("40.75"))))
      assert(sourceObj("location") == Some(Json.obj("lat" -> Json.fromBigDecimal(BigDecimal("40.7128")), "lon" -> Json.fromBigDecimal(BigDecimal("74.0059")))))

      val decimalAttrs = sourceObj("bigDecimalAttributes").flatMap(_.asObject).getOrElse(fail("expected bigDecimalAttributes in source"))
      assert(decimalAttrs("deposit_amount") == Some(Json.fromBigDecimal(BigDecimal("10.25"))))
    }
  }
}
