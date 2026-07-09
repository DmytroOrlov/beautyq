package leaderboard.search.beautyq.contract

import leaderboard.model.Category.CategoryId
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.search.contract.SearchFieldKind
import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, VariantSearchDocument}
import leaderboard.search.dsl.{BeautyQSearchPresentation, BeautySearchSpecV1, ResolvedSearchConstraint, SearchConstraint, SearchGeoPoint}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class BeautyQDocumentContractSpec extends AnyWordSpec {

  private def uuid(suffix: String): UUID = UUID.fromString(s"00000000-0000-0000-0000-$suffix")

  private def sampleDocument: VariantSearchDocument =
    VariantSearchDocument(
      variantId = MasterServiceOfferVariantId(uuid("0000000000a1")),
      masterServiceOfferId = MasterServiceOfferId(uuid("0000000000a2")),
      masterLocationId = MasterLocationId(uuid("0000000000a3")),
      masterId = MasterId(uuid("0000000000a4")),
      serviceId = ServiceId(uuid("0000000000a5")),
      categoryId = CategoryId(uuid("0000000000a6")),
      serviceName = "Маникюр",
      categoryName = "Ногти, маникюр и педикюр",
      masterName = "Studio One",
      locationName = "Downtown",
      address = "Main St 1",
      location = SearchGeoPoint(BigDecimal("52.5"), BigDecimal("13.4")),
      lat = BigDecimal("52.5"),
      lon = BigDecimal("13.4"),
      priceFrom = BigDecimal(30),
      priceTo = BigDecimal(50),
      durationMin = 60,
      enumAttributes = Map.empty,
      booleanAttributes = Map.empty,
      intAttributes = Map.empty,
      bigDecimalAttributes = Map.empty,
      allText = "маникюр downtown",
      serviceText = "маникюр",
      attributeText = "",
      providerText = "studio one downtown",
      locationText = "downtown main st 1",
    )

  "VariantSearchDocument" should {
    "be constructible from beautyq-search-contract alone, with representative values" in {
      val document = sampleDocument
      assert(document.serviceName == "Маникюр")
      assert(document.priceFrom == BigDecimal(30))
    }
  }

  "BeautyQVariantSearchDocumentContract.documentSpec" should {
    "declare the beautyq_variant_v1 index" in {
      assert(BeautyQVariantSearchDocumentContract.documentSpec.indexName == "beautyq_variant_v1")
    }

    "declare a non-empty field list including the expected static paths" in {
      val paths = BeautyQVariantSearchDocumentContract.documentSpec.fields.map(_.path)
      assert(paths.nonEmpty)
      assert(paths.contains("variantId"))
      assert(paths.contains("serviceName"))
      assert(paths.contains("categoryName"))
      assert(paths.contains("priceFrom"))
      assert(paths.contains("durationMin"))
      assert(paths.contains("location"))
      assert(paths.contains("allText"))
    }
  }

  "BeautyQVariantSearchDocumentContract.qdrantPayloadSpec" should {
    "own exactly the expected Qdrant payload field paths" in {
      val paths = BeautyQVariantSearchDocumentContract.qdrantPayloadSpec.fieldPaths
      assert(paths.contains("variantId"))
      assert(paths.contains("masterLocationId"))
      assert(paths.contains("serviceId"))
      assert(paths.contains("serviceName"))
    }
  }

  "BeautyQVariantSearchDocumentContract.querySchema" should {
    "preserve the explicit query field names, order, handles, and geo scoring field" in {
      val querySchema = BeautyQVariantSearchDocumentContract.querySchema
      val fields = BeautyQVariantSearchDocumentContract.Fields

      assert(querySchema.fields.map(_.name) == List("serviceName", "categoryName", "priceFrom", "durationMin", "location"))
      assert(querySchema.fields.map(_.field) == List(fields.serviceName, fields.categoryName, fields.priceFrom, fields.durationMin, fields.location))
      assert(querySchema.geoScoringField == Some(fields.location))
    }

    "resolve ServiceAny to a terms constraint on serviceName" in {
      BeautyQVariantSearchDocumentContract.querySchema.resolve(SearchConstraint.ServiceAny(Set("Маникюр"))) match {
        case Right(ResolvedSearchConstraint.Terms(field, values, _)) =>
          assert(field.path == "serviceName")
          assert(values == Set("Маникюр"))
        case other =>
          fail(s"Expected a Terms constraint on serviceName, got $other")
      }
    }

    "resolve PriceRange to a range constraint on priceFrom" in {
      BeautyQVariantSearchDocumentContract.querySchema.resolve(SearchConstraint.PriceRange(None, Some(BigDecimal(50)))) match {
        case Right(ResolvedSearchConstraint.Range(field, min, max, _)) =>
          assert(field.path == "priceFrom")
          assert(min.isEmpty)
          assert(max.contains(BigDecimal(50)))
        case other =>
          fail(s"Expected a Range constraint on priceFrom, got $other")
      }
    }

    "resolve NearUser to a geo-distance constraint on location" in {
      BeautyQVariantSearchDocumentContract.querySchema.resolve(SearchConstraint.NearUser) match {
        case Right(ResolvedSearchConstraint.GeoDistance(field, _)) =>
          assert(field.path == "location")
        case other =>
          fail(s"Expected a GeoDistance constraint on location, got $other")
      }
    }
  }

  "BeautySearchSpecV1.runtimeSpec" should {
    "carry the Qdrant payload spec under its declared name" in {
      assert(BeautySearchSpecV1.runtimeSpec.payloadSpecs.contains(BeautySearchSpecV1.QdrantPayloadSpecName))
    }
  }

  "BeautyQSearchPresentation.carouselSpec" should {
    "expose provider and service-intent groups built from contract fields" in {
      val fields = BeautyQVariantSearchDocumentContract.Fields
      val carousel = BeautyQSearchPresentation.carouselSpec(
        providerGroupField = fields.masterLocationId,
        serviceIntentGroupField = fields.serviceId,
      )

      assert(BeautyQSearchPresentation.providerGroupField(carousel) == Right(fields.masterLocationId))
      assert(BeautyQSearchPresentation.serviceIntentGroupField(carousel) == Right(fields.serviceId))
    }
  }

  "BeautyQSearchResultUnitContract.variant" should {
    "point at the current document index and variant carousel limit" in {
      assert(BeautyQSearchResultUnitContract.variant.documentIndexName == BeautyQVariantSearchDocumentContract.documentSpec.indexName)
      assert(BeautyQSearchResultUnitContract.variant.carouselLimitName == BeautyQSearchPresentation.CarouselLimits.Variant)
    }
  }

  "BeautyQSearchDocumentFieldContract.fields" should {
    "preserve the current document field count" in {
      assert(BeautyQSearchDocumentFieldContract.fields.size == BeautyQVariantSearchDocumentContract.documentSpec.fields.size)
    }

    "map representative field paths/kinds" in {
      val kindByName = BeautyQSearchDocumentFieldContract.fields.map(field => field.name.value -> field.kind).toMap
      assert(kindByName("allText") == SearchFieldKind.Text)
      assert(kindByName("variantId") == SearchFieldKind.Keyword)
      assert(kindByName("serviceName") == SearchFieldKind.Facet)
      assert(kindByName("priceFrom") == SearchFieldKind.Range)
      assert(kindByName("priceTo") == SearchFieldKind.Numeric)
      assert(kindByName("location") == SearchFieldKind.Geo)
    }
  }
}
