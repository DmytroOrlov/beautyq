package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*

/** Typed inbound source identities used by BeautyQ's one executable precedence value. */
enum BeautyQConstraintSource {
  case PublicRequest
  case ParsedIntent
}

/** BeautyQ's business choice for the request value used as a geo filter/sort origin. */
trait BeautyQGeoOriginPolicy {
  def sourcePath: String
  def resolve(request: ValidatedBeautySearchRequestGen2): Option[GeoPoint]
}

object BeautyQGeoOriginPolicy {
  /** Both the public geo filter and the public geo sort resolve their origin from the request's own
    * `userLocation`; there is no server-side default origin and no per-field override. */
  case object RequestUserLocation extends BeautyQGeoOriginPolicy {
    def sourcePath: String = "request.userLocation"
    def resolve(request: ValidatedBeautySearchRequestGen2): Option[GeoPoint] = request.userLocation
  }
}

/** BeautyQ's compact plan-policy declaration: the business choices Brick 4F's generic compilation
  * mechanics need but cannot supply themselves - constraint-source precedence, the geo-origin source,
  * the facet inventory, group policy, plan-mode classification and the default-browse notice.
  * Canonicalization, precedence traversal, geo-clause resolution, facet lookup-map construction and
  * `SearchPlan` assembly/validation are search-gen2-core/contract mechanics this file only refers to
  * ([[leaderboard.search.gen2.core.plan.ConstraintPrecedenceResolver]],
  * [[leaderboard.search.gen2.core.plan.PublicPlanInputResolver]],
  * [[leaderboard.search.gen2.core.plan.SearchPlanCompilationKernel]]), never reimplements. Static
  * declaration validation/unwrapping (`FacetSize.unsafeFrom`, `FacetPlanRegistry.unsafeFrom`) is
  * likewise framework-owned; this file supplies only the business literals.
  */
object BeautyQSearchPlanPolicy {

  private val Fields = BeautyQSearchDeclarations.variants.Fields

  /** Public request constraints outrank parsed intent constraints; `ExplicitUi` and `FacetSelection`
    * provenance (both public-request-sourced) share the same, higher tier. The generic value derives
    * both reviewer-readable rendering and the actual [[ConstraintPriorityTiers]] from these typed cases.
    */
  val constraintPrecedence: ConstraintPrecedence[BeautyQConstraintSource] =
    ConstraintPrecedence.unsafeAbove(BeautyQConstraintSource.PublicRequest, BeautyQConstraintSource.ParsedIntent)

  val geoOriginPolicy: BeautyQGeoOriginPolicy = BeautyQGeoOriginPolicy.RequestUserLocation

  // Gen1 evidence: beautyq-search-contract's BeautySearchSpecV1.facetFields declares serviceName/
  // categoryName as Terms(limit = 10) and priceFrom/durationMin as Ranges with exactly these bucket IDs
  // and thresholds. Gen2 keeps the bucket IDs/thresholds, but facets the stable serviceCode/categoryCode
  // identity fields (not the presentation name fields) and uses the IntervalOverlap price facet over
  // both price fields per docs/gen2/BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md #4.
  private val priceBuckets: Vector[FacetBucket[BigDecimal]] =
    Vector(
      FacetBucket.HalfOpen(FacetBucketId("0-30"), BigDecimal(0), BigDecimal(30)),
      FacetBucket.HalfOpen(FacetBucketId("30-50"), BigDecimal(30), BigDecimal(50)),
      FacetBucket.HalfOpen(FacetBucketId("50-80"), BigDecimal(50), BigDecimal(80)),
      FacetBucket.HalfOpen(FacetBucketId("80-120"), BigDecimal(80), BigDecimal(120)),
      FacetBucket.UpperUnbounded(FacetBucketId("120+"), BigDecimal(120)),
    )

  private val durationBuckets: Vector[FacetBucket[Int]] =
    Vector(
      FacetBucket.HalfOpen(FacetBucketId("0-30"), 0, 30),
      FacetBucket.HalfOpen(FacetBucketId("30-60"), 30, 60),
      FacetBucket.HalfOpen(FacetBucketId("60-90"), 60, 90),
      FacetBucket.HalfOpen(FacetBucketId("90-120"), 90, 120),
      FacetBucket.UpperUnbounded(FacetBucketId("120+"), 120),
    )

  private val declarations: Vector[FacetPlanDeclaration[VariantSearchDocumentGen2]] =
    Vector(
      FacetPlanDeclaration(FacetRequest.Terms(FacetId("service"), Fields.serviceCode, FacetSize.unsafeFrom(10), TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters)),
      FacetPlanDeclaration(FacetRequest.Terms(FacetId("category"), Fields.categoryCode, FacetSize.unsafeFrom(10), TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters)),
      FacetPlanDeclaration(FacetRequest.IntervalOverlap(FacetId("price"), Fields.priceFrom, Fields.priceTo, priceBuckets, FacetCountingPolicy.AllAppliedHardFilters)),
      FacetPlanDeclaration(FacetRequest.NumberRange(FacetId("durationMinutes"), Fields.durationMin, durationBuckets, FacetCountingPolicy.AllAppliedHardFilters)),
    )

  val facetRegistry: FacetPlanRegistry[VariantSearchDocumentGen2] = FacetPlanRegistry.unsafeFrom(declarations)

  /** No group requests: the current public request has no group input. An explicit empty policy, not an
    * omission. */
  val groups: Vector[GroupRequest[VariantSearchDocumentGen2, ?]] = Vector.empty

  val defaultBrowseNotice: PlanDiagnostic = PlanDiagnostic(PlanDiagnosticCode("default-browse"), None)

  /** Classifies a compiled plan's already-resolved pieces into a [[BeautyQSearchPlanMode]]. Receives
    * only already-compiled/resolved values (parsed residual text/soft signals/semantic labels, resolved
    * applied filters, resolved explicit sort) - never the raw request query, requested facets, cursor
    * presence, suppressed-filter count, or matched-rule count alone. Requested facets alone never make a
    * plan structured browse. */
  def classify(
    residualText: Option[String],
    softSignals: Vector[PlannedSignal[VariantSearchDocumentGen2]],
    canonicalSemanticLabels: Vector[CanonicalSemanticLabel],
    appliedFilters: Vector[AppliedFilter[VariantSearchDocumentGen2]],
    sort: Vector[PlannedSort[VariantSearchDocumentGen2]],
  ): BeautyQSearchPlanMode = {
    val isSemanticSearch = residualText.isDefined || softSignals.nonEmpty || canonicalSemanticLabels.nonEmpty
    if (isSemanticSearch) BeautyQSearchPlanMode.SemanticSearch
    else if (appliedFilters.nonEmpty || sort.nonEmpty) BeautyQSearchPlanMode.StructuredBrowse
    else BeautyQSearchPlanMode.DefaultBrowse
  }
}
