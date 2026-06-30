package leaderboard.search

import leaderboard.model.*
import leaderboard.search.document.BeautyQVariantSearchDocumentSchema
import leaderboard.search.dsl.*
import org.scalatest.wordspec.AnyWordSpec

final class SearchDslTypedFieldSpec extends AnyWordSpec {
  private val fields = BeautyQVariantSearchDocumentSchema.Fields
  private val querySchema = BeautyQVariantSearchDocumentSchema.querySchema

  "typed search field handles" should {
    "derive EmbeddingSpec sourceTextFieldPaths from sourceTextFields" in {
      val spec = EmbeddingSpec(
        vectorName = "variant-embedding",
        modelName = "test-model",
        dimension = 384,
        distance = VectorDistance.Cosine,
        sourceTextFields = List(fields.serviceText, fields.attributeText, fields.allText),
      )

      assert(spec.sourceTextFieldPaths == List("serviceText", "attributeText", "allText"))
    }

    "derive FacetField path from its typed field" in {
      val facetField = FacetField(fields.serviceName, FacetFieldMode.Terms)

      assert(facetField.path == fields.serviceName.path)
    }

    "derive carousel group paths from typed fields" in {
      val carouselSpec = BeautyQSearchPresentation.carouselSpec(
        providerGroupField = fields.masterLocationId,
        serviceIntentGroupField = fields.serviceId,
      )
      val providerGroupField = BeautyQSearchPresentation.providerGroupField(carouselSpec).getOrElse(fail("expected provider group field"))
      val serviceIntentGroupField = BeautyQSearchPresentation.serviceIntentGroupField(carouselSpec).getOrElse(fail("expected service-intent group field"))

      assert(providerGroupField.path == "masterLocationId")
      assert(serviceIntentGroupField.path == "serviceId")
    }

    "derive SearchDocumentPayloadSpec fieldPaths from typed fields" in {
      val payloadSpec = BeautyQVariantSearchDocumentSchema.qdrantPayloadSpec

      assert(payloadSpec.fields == List(fields.variantId, fields.masterLocationId, fields.serviceId, fields.serviceName))
      assert(payloadSpec.fieldPaths == List("variantId", "masterLocationId", "serviceId", "serviceName"))
    }
  }

  "SearchQuerySchema.resolve" should {
    "resolve static constraints to schema-owned handles" in {
      assert(resolve(SearchConstraint.ServiceAny(Set("Маникюр"))) == ResolvedSearchConstraint.Terms(fields.serviceName, Set("Маникюр"), BeautyQSearchPresentation.BoostRoles.Service))
      assert(resolve(SearchConstraint.CategoryAny(Set("Ногти"))) == ResolvedSearchConstraint.Terms(fields.categoryName, Set("Ногти"), BeautyQSearchPresentation.BoostRoles.Service))
      assert(resolve(SearchConstraint.PriceRange(Some(BigDecimal(10)), Some(BigDecimal(20)))) == ResolvedSearchConstraint.Range(fields.priceFrom, Some(BigDecimal(10)), Some(BigDecimal(20)), BeautyQSearchPresentation.BoostRoles.Attribute))
      assert(resolve(SearchConstraint.DurationRange(Some(30), Some(60))) == ResolvedSearchConstraint.Range(fields.durationMin, Some(BigDecimal(30)), Some(BigDecimal(60)), BeautyQSearchPresentation.BoostRoles.Attribute))
    }

    "resolve dynamic enum, bool, int, and decimal constraints through schema-owned maps" in {
      val enumDefinition = firstEnumDefinition()
      val booleanDefinition = firstBooleanDefinition()
      val intDefinition = firstIntDefinition()
      val decimalDefinition = firstDecimalDefinition()

      fields.enumAttributesByCode.get(enumDefinition.code) match {
        case Some(field) =>
          assert(resolve(SearchConstraint.EnumAttr(enumDefinition.code, Set("value"))) == ResolvedSearchConstraint.Terms(field, Set("value"), BeautyQSearchPresentation.BoostRoles.Attribute))
        case None =>
          fail(s"Missing enum field handle for ${enumDefinition.code}")
      }
      fields.booleanAttributesByCode.get(booleanDefinition.code) match {
        case Some(field) =>
          assert(resolve(SearchConstraint.BoolAttr(booleanDefinition.code, value = true)) == ResolvedSearchConstraint.BooleanTerm(field, value = true, BeautyQSearchPresentation.BoostRoles.Attribute))
        case None =>
          fail(s"Missing boolean field handle for ${booleanDefinition.code}")
      }
      fields.intAttributesByCode.get(intDefinition.code) match {
        case Some(field) =>
          assert(resolve(SearchConstraint.IntRange(intDefinition.code, Some(1), Some(3))) == ResolvedSearchConstraint.Range(field, Some(BigDecimal(1)), Some(BigDecimal(3)), BeautyQSearchPresentation.BoostRoles.Attribute))
        case None =>
          fail(s"Missing int field handle for ${intDefinition.code}")
      }
      fields.decimalAttributesByCode.get(decimalDefinition.code) match {
        case Some(field) =>
          assert(resolve(SearchConstraint.DecimalRange(decimalDefinition.code, Some(BigDecimal("1.5")), Some(BigDecimal("3.5")))) == ResolvedSearchConstraint.Range(field, Some(BigDecimal("1.5")), Some(BigDecimal("3.5")), BeautyQSearchPresentation.BoostRoles.Attribute))
        case None =>
          fail(s"Missing decimal field handle for ${decimalDefinition.code}")
      }
    }
  }

  private def resolve(constraint: SearchConstraint): ResolvedSearchConstraint[leaderboard.search.document.VariantSearchDocument] =
    querySchema.resolve(constraint) match {
      case Right(value) => value
      case Left(error)  => fail(s"Expected resolved constraint for $constraint, got ${error.message}")
    }

  private def firstEnumDefinition(): EnumAttributeDefinition[?] =
    AttributeDefinition.enumDefinitions match {
      case first :: _ => first
      case Nil        => fail("Expected at least one enum attribute definition")
    }

  private def firstBooleanDefinition(): BooleanAttributeDefinition =
    AttributeDefinition.booleanDefinitions match {
      case first :: _ => first
      case Nil        => fail("Expected at least one boolean attribute definition")
    }

  private def firstIntDefinition(): IntAttributeDefinition =
    AttributeDefinition.intDefinitions match {
      case first :: _ => first
      case Nil        => fail("Expected at least one int attribute definition")
    }

  private def firstDecimalDefinition(): BigDecimalAttributeDefinition =
    AttributeDefinition.bigDecimalDefinitions match {
      case first :: _ => first
      case Nil        => fail("Expected at least one decimal attribute definition")
    }
}
