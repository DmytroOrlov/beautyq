package leaderboard.search.eval

final case class M9BeautyQSearchEvalQueryDatasetLanguageCounts(
  ru: Int,
  en: Int,
  de: Int,
  mixed: Int,
)

final case class M9BeautyQSearchEvalQueryDatasetCoverage(
  serviceCount: Int,
  enumAttributesCovered: Boolean,
  booleanAttributesCovered: Boolean,
  numericBigDecimalAttributesCovered: Boolean,
)

final case class M9BeautyQSearchEvalQueryDatasetBoundary(
  seedEvalFixtureOnly: Boolean,
  notProductionTelemetry: Boolean,
  notActivationApproval: Boolean,
  realBackendCallsDisabledByDefault: Boolean,
  defaultBeautySearchRouteEsBacked: Boolean,
  qdrantProductionActivationApproved: Boolean,
)

final case class M9BeautyQSearchEvalQueryDatasetMetadata(
  version: Int,
  datasetId: String,
  queryCount: Int,
  languageCounts: M9BeautyQSearchEvalQueryDatasetLanguageCounts,
  targetCarousels: List[String],
  coverage: M9BeautyQSearchEvalQueryDatasetCoverage,
  testUserLocationLabel: String,
  boundary: M9BeautyQSearchEvalQueryDatasetBoundary,
)

object M9BeautyQSearchEvalQueryDataset {

  val ResourceFilename: String = "beautyq_search_eval_queries_v1.json"
  val ResourcePath: String = s"/leaderboard/search/eval/$ResourceFilename"

  val Metadata: M9BeautyQSearchEvalQueryDatasetMetadata =
    M9BeautyQSearchEvalQueryDatasetMetadata(
      version = 1,
      datasetId = "wandsbek_hamburg_beauty_services_seed_ready",
      queryCount = 63,
      languageCounts = M9BeautyQSearchEvalQueryDatasetLanguageCounts(
        ru = 31,
        en = 21,
        de = 6,
        mixed = 5,
      ),
      targetCarousels = List(
        "variantCarousel",
        "providerCarousel",
        "serviceIntentCarousel",
      ),
      coverage = M9BeautyQSearchEvalQueryDatasetCoverage(
        serviceCount = 9,
        enumAttributesCovered = true,
        booleanAttributesCovered = true,
        numericBigDecimalAttributesCovered = true,
      ),
      testUserLocationLabel = "Wandsbek Markt",
      boundary = M9BeautyQSearchEvalQueryDatasetBoundary(
        seedEvalFixtureOnly = true,
        notProductionTelemetry = true,
        notActivationApproval = true,
        realBackendCallsDisabledByDefault = true,
        defaultBeautySearchRouteEsBacked = true,
        qdrantProductionActivationApproved = false,
      ),
    )
}
