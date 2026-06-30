package leaderboard.search.dsl

import leaderboard.model.*
import leaderboard.search.document.{BeautyQVariantSearchDocumentSchema, VariantSearchDocument}

object BeautySearchSpecV1 {
  val QdrantPayloadSpecName: String = "qdrant"

  private val Fields = BeautyQVariantSearchDocumentSchema.Fields

  lazy val runtimeSpec: SearchRuntimeSpec[VariantSearchDocument] =
    spec.runtimeSpec(
      Map(QdrantPayloadSpecName -> BeautyQVariantSearchDocumentSchema.qdrantPayloadSpec)
    )

  lazy val spec: BeautySearchSpec = BeautySearchSpec(
    variantDocument = BeautyQVariantSearchDocumentSchema.documentSpec,
    intentVocabulary = BeautyQSearchIntentVocabulary.vocabulary,
    carouselSpec = CarouselSpec(
      variantSize = 10,
      providerSize = 10,
      serviceIntentSize = 10,
      providerGroupField = Fields.masterLocationId,
      serviceIntentGroupField = Fields.serviceId,
      ranking = RankingSpec(
        textScoreWeight = 1.0,
        serviceBoostWeight = 2.0,
        attributeBoostWeight = 1.5,
        providerDistanceWeight = 1.25,
        providerMatchingVariantCountWeight = 0.5,
      ),
    ),
    facetSpec = FacetSpec(
      enabled = true,
      fields = facetFields,
      inferredFilterDominanceThreshold = BigDecimal("0.70"),
      inferredFilterMinCount = 2,
    ),
    requestSpec = SearchRequestSpec(
      hitWindowSize = 256,
      textOperator = TextOperator.And,
      aggregationSize = 20,
      geoDistanceScale = "5km",
      geoDistanceOffset = "0km",
      geoDistanceDecay = 0.5d,
    ),
    querySchema = BeautyQVariantSearchDocumentSchema.querySchema,
  )

  private val facetFields: List[FacetField[VariantSearchDocument]] =
    List(
      FacetField(Fields.serviceName, FacetFieldMode.Terms, limit = 10),
      FacetField(Fields.categoryName, FacetFieldMode.Terms, limit = 10),
      FacetField(
        Fields.priceFrom,
        FacetFieldMode.Ranges(
          List(
            FacetRangeBucket("0-30", max = Some(BigDecimal(30))),
            FacetRangeBucket("30-50", min = Some(BigDecimal(30)), max = Some(BigDecimal(50))),
            FacetRangeBucket("50-80", min = Some(BigDecimal(50)), max = Some(BigDecimal(80))),
            FacetRangeBucket("80-120", min = Some(BigDecimal(80)), max = Some(BigDecimal(120))),
            FacetRangeBucket("120+", min = Some(BigDecimal(120))),
          )
        )
      ),
      FacetField(
        Fields.durationMin,
        FacetFieldMode.Ranges(
          List(
            FacetRangeBucket("0-30", max = Some(BigDecimal(30))),
            FacetRangeBucket("30-60", min = Some(BigDecimal(30)), max = Some(BigDecimal(60))),
            FacetRangeBucket("60-90", min = Some(BigDecimal(60)), max = Some(BigDecimal(90))),
            FacetRangeBucket("90-120", min = Some(BigDecimal(90)), max = Some(BigDecimal(120))),
            FacetRangeBucket("120+", min = Some(BigDecimal(120))),
          )
        )
      ),
    ) ++ AttributeDefinition.enumDefinitions.flatMap { definition =>
      Fields.enumAttributesByCode.get(definition.code).map(FacetField(_, FacetFieldMode.Terms, limit = definition.values.size))
    } ++ AttributeDefinition.booleanDefinitions.flatMap { definition =>
      Fields.booleanAttributesByCode.get(definition.code).map(FacetField(_, FacetFieldMode.Terms, limit = 2))
    }
}
