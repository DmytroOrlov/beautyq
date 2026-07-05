package leaderboard.search.eval

import io.circe.Decoder

/**
 * M19C: pure source-confirmed taxonomy of BeautyQ response components and eval-query coverage.
 *
 * The current `BeautySearchResponse` exposes five response parts (variant carousel, provider
 * carousel, service intent carousel, facets, inferred filters). A future component-level hybrid
 * policy will have to decide, per part, whether ES, Qdrant, both, or neither should own that part.
 * This file does not choose any hybrid policy. It only records, from the listed source files
 * (the M18 dual-engine offline-eval result, the M19A metrics, the
 * `BeautySearchModels` response shape, the `BeautySearchSpecV1` spec, the HTTP contract suite,
 * the eval-query JSON, and the listed code-review/hybrid-plan docs), what is currently
 * source-confirmed and what is still unknown.
 *
 * Boundaries:
 *   - pure data only; no DI, no effects, no backend calls, no HTTP, no plugin, no production
 *     `/beauty-search` route;
 *   - uses only fields present in the listed source files. Fields absent from those files are
 *     marked `Unknown` / `NotAvailable` rather than invented;
 *   - candidate-level evidence (M18 per-backend candidate id sets) is recorded as
 *     candidate-level evidence, not as proof for provider/service/facet/inferred-filter policy;
 *   - the taxonomy is an offline/eval-only surface. It prepares evidence for a future policy; it
 *     does not grant or encode any production behavior.
 */
object M19CBeautyQResponseComponentTaxonomy {

  /**
   * The five response parts in the current `BeautySearchResponse`. The order is the same order
   * used in the response case class so it stays stable and source-confirmable.
   */
  enum BeautyResponseComponentId {
    case VariantCarousel
    case ProviderCarousel
    case ServiceIntentCarousel
    case Facets
    case InferredFilters

    def render: String =
      this match {
        case BeautyResponseComponentId.VariantCarousel       => "variantCarousel"
        case BeautyResponseComponentId.ProviderCarousel      => "providerCarousel"
        case BeautyResponseComponentId.ServiceIntentCarousel => "serviceIntentCarousel"
        case BeautyResponseComponentId.Facets                => "facets"
        case BeautyResponseComponentId.InferredFilters       => "inferredFilters"
      }
  }

  object BeautyResponseComponentId {
    /** Stable order mirroring the `BeautySearchResponse` field order. */
    val stableOrder: List[BeautyResponseComponentId] =
      List(VariantCarousel, ProviderCarousel, ServiceIntentCarousel, Facets, InferredFilters)
  }

  /**
   * Honest tri-state: what evidence the listed M18 result has for this response part.
   * `CandidateLevelOnly` means the M18 result carries backend candidate ids for the part but
   * does not, by itself, prove ownership or correctness of the response part. `NotApplicable`
   * is reserved for parts whose M18 evidence is structurally absent (e.g. facets).
   */
  enum ComponentEvidence {
    case BackendCandidateEvidence
    case CandidateLevelOnly
    case NotApplicable
    case Unknown
  }

  /**
   * Per-component fact sheet. Every field is explicit so unknown facts stay visible.
   *
   * `sourceConfirmed` means the component's identity is present in the listed source files
   * (the `BeautySearchResponse` shape, the spec, or the contract suite). `ownerSourceConfirmed`
   * means the listed source files also identify the current production owner. `evidence*` reflects
   * what the M18/M19 listed evidence actually says about the component, not what a future policy
   * might conclude.
   */
  final case class ResponseComponentFact(
    componentId: BeautyResponseComponentId,
    sourceConfirmed: Boolean,
    sourceEvidenceCitations: List[String],
    productionOwnerLabel: String,
    productionOwnerSourceConfirmed: Boolean,
    sourceDataForBuild: String,
    esEvidence: ComponentEvidence,
    qdrantEvidence: ComponentEvidence,
    candidateLevelOnly: Boolean,
    policyQuestions: List[String],
  )

  /**
   * Source-confirmed per-component fact sheet. All values here come from the listed source files
   * and are recorded as data, not decisions. A future hybrid-policy task will need to read these
   * fact sheets and decide what to do with them; this object does not.
   */
  val ResponseComponentFacts: List[ResponseComponentFact] = List(
    ResponseComponentFact(
      componentId = BeautyResponseComponentId.VariantCarousel,
      sourceConfirmed = true,
      sourceEvidenceCitations = List(
        "BeautySearchModels.scala:120-130 (BeautySearchResponse.variantCarousel: List[VariantSearchResult])",
        "BeautySearchApiHttpContractSuite.scala:39-44 (asserts variantCarousel in response body)",
        "BeautySearchSpecV1.scala:29-42 (CarouselSpec.variantSize = 10, ranking weights)",
        "M19DualEngineOfflineEvalMetrics.scala (per-query es_candidate_ids / qdrant_candidate_ids / simulated_hybrid_candidate_ids)",
      ),
      productionOwnerLabel = "ES lexical backend over the seed-resource catalog snapshot, with deterministic lexical ranking",
      productionOwnerSourceConfirmed = true,
      sourceDataForBuild = "VariantSearchDocument rows flattened from BeautySearchCatalogSnapshot, ranked by the CarouselSpec ranking weights (textScore, serviceBoost, attributeBoost, providerDistance, providerMatchingVariantCount)",
      esEvidence = ComponentEvidence.BackendCandidateEvidence,
      qdrantEvidence = ComponentEvidence.BackendCandidateEvidence,
      candidateLevelOnly = false,
      policyQuestions = List(
        "Should Qdrant semantic candidates supplement the ES variant recall for any class of query, and if so, on which eval evidence?",
        "Should semantic candidates be allowed to reorder or only append to the ES-ranked variant list?",
        "What is the correct handling for variant ids present in both ES and Qdrant?",
      ),
    ),
    ResponseComponentFact(
      componentId = BeautyResponseComponentId.ProviderCarousel,
      sourceConfirmed = true,
      sourceEvidenceCitations = List(
        "BeautySearchModels.scala:91-105 (ProviderSearchResult) and :120-130 (providerCarousel field)",
        "BeautySearchApiHttpContractSuite.scala:40,45 (asserts providerCarousel in response body)",
        "BeautySearchSpecV1.scala:30-34 (CarouselSpec.providerGroupField = masterLocationId, providerSize = 10)",
        "M19DualEngineOfflineEvalMetrics.scala (provider information is NOT a per-query column in the M19 metrics)",
      ),
      productionOwnerLabel = "ES lexical backend; provider carousel is a domain projection over variant candidates grouped by masterLocationId",
      productionOwnerSourceConfirmed = true,
      sourceDataForBuild = "VariantSearchDocument rows from the ES seed-resource catalog snapshot, grouped by masterLocationId per BeautySearchSpecV1.spec.carouselSpec.providerGroupField",
      esEvidence = ComponentEvidence.BackendCandidateEvidence,
      qdrantEvidence = ComponentEvidence.CandidateLevelOnly,
      candidateLevelOnly = false,
      policyQuestions = List(
        "Can the listed eval dataset's expected provider carousel be checked against M18 ES candidate ids without a separate provider-grouping projection step?",
        "What projection step turns a Qdrant candidate id set into a provider carousel, and is that step source-confirmed?",
        "Is the Qdrant provider-carousel signal currently stronger than the M19 candidate-level evidence alone?",
      ),
    ),
    ResponseComponentFact(
      componentId = BeautyResponseComponentId.ServiceIntentCarousel,
      sourceConfirmed = true,
      sourceEvidenceCitations = List(
        "BeautySearchModels.scala:107-118 (ServiceIntentSearchResult) and :120-130 (serviceIntentCarousel field)",
        "BeautySearchApiHttpContractSuite.scala:41,46 (asserts serviceIntentCarousel in response body)",
        "BeautySearchSpecV1.scala:30-34 (CarouselSpec.serviceIntentGroupField = serviceId, serviceIntentSize = 10)",
        "M19DualEngineOfflineEvalMetrics.scala (service-intent information is NOT a per-query column in the M19 metrics)",
      ),
      productionOwnerLabel = "ES lexical backend; service intent carousel is a domain projection over variant candidates grouped by serviceId",
      productionOwnerSourceConfirmed = true,
      sourceDataForBuild = "VariantSearchDocument rows from the ES seed-resource catalog snapshot, grouped by serviceId per BeautySearchSpecV1.spec.carouselSpec.serviceIntentGroupField",
      esEvidence = ComponentEvidence.BackendCandidateEvidence,
      qdrantEvidence = ComponentEvidence.CandidateLevelOnly,
      candidateLevelOnly = false,
      policyQuestions = List(
        "Can the listed eval dataset's expected service-intent carousel be checked against M18 ES candidate ids without a separate service-grouping projection step?",
        "What projection step turns a Qdrant candidate id set into a service-intent carousel, and is that step source-confirmed?",
        "Is the Qdrant service-intent signal currently stronger than the M19 candidate-level evidence alone?",
      ),
    ),
    ResponseComponentFact(
      componentId = BeautyResponseComponentId.Facets,
      sourceConfirmed = true,
      sourceEvidenceCitations = List(
        "BeautySearchModels.scala:44-60, 120-130 (BeautySearchFacet case class and facets field)",
        "BeautySearchApiHttpContractSuite.scala:42 (asserts facets in response body)",
        "BeautySearchSpecV1.scala:43-48 (FacetSpec: enabled, fields, inferredFilterDominanceThreshold, inferredFilterMinCount)",
        "BeautySearchSpecV1.scala:273-305 (facetFields: serviceName terms, categoryName terms, priceFrom ranges, durationMin ranges, enum attribute terms, boolean attribute terms)",
        "docs/search-dsl-hybrid-v1-plan.md:266-277 (\"Qdrant does not produce facets\", \"Qdrant should not be asked to compute exact filters or canonical facet counts in Hybrid V1\")",
        "M19DualEngineOfflineEvalMetrics.scala (no facets column in per-query metrics)",
      ),
      productionOwnerLabel = "ES backend: facets are produced from the spec's FacetSpec against the ES index; Qdrant is not the source of facets in the listed docs",
      productionOwnerSourceConfirmed = true,
      sourceDataForBuild = "ES aggregations over the fields declared in BeautySearchSpecV1.spec.facetSpec.fields (serviceName, categoryName, priceFrom ranges, durationMin ranges, enumAttributes.<code> terms, booleanAttributes.<code> terms)",
      esEvidence = ComponentEvidence.BackendCandidateEvidence,
      qdrantEvidence = ComponentEvidence.NotApplicable,
      candidateLevelOnly = true,
      policyQuestions = List(
        "Can the listed eval dataset evaluate facet correctness at all? (The JSON has no facets expectations per query.)",
        "If Qdrant were ever to surface metadata, would that metadata be allowed to replace ES aggregations? The listed hybrid plan says no.",
        "How would candidate-derived Qdrant metadata be marked as such, distinct from canonical ES facets?",
      ),
    ),
    ResponseComponentFact(
      componentId = BeautyResponseComponentId.InferredFilters,
      sourceConfirmed = true,
      sourceEvidenceCitations = List(
        "BeautySearchModels.scala:35-42, 120-130 (BeautySearchAppliedFilter case class and inferredFilters field)",
        "BeautySearchApiHttpContractSuite.scala:43,47 (asserts inferredFilters in response body and constraint type near_user)",
        "BeautySearchSpecV1.scala:46-48 (inferredFilterDominanceThreshold = 0.70, inferredFilterMinCount = 2)",
        "docs/search-dsl-hybrid-v1-plan.md:266-277, 469-474 (\"Qdrant does not produce inferred filters\", \"Qdrant-only experimental responses may use empty facets and empty inferred filters\")",
        "M19DualEngineOfflineEvalMetrics.scala (no inferred-filters column in per-query metrics)",
      ),
      productionOwnerLabel = "ES / parser-owned: inferred filters come from the parser plus the FacetSpec dominance threshold; Qdrant is not the source of inferred filters in the listed docs",
      productionOwnerSourceConfirmed = true,
      sourceDataForBuild = "BeautySearchFacet values from ES aggregations, reduced via BeautySearchSpecV1.spec.facetSpec.inferredFilterDominanceThreshold and inferredFilterMinCount, paired with the BeautySearchIntentParser NearUser constraint",
      esEvidence = ComponentEvidence.BackendCandidateEvidence,
      qdrantEvidence = ComponentEvidence.NotApplicable,
      candidateLevelOnly = true,
      policyQuestions = List(
        "Can the listed eval dataset evaluate inferred-filter correctness at all? (The JSON has no inferredFilters expectations per query.)",
        "Are inferred filters a candidate-level evidence surface, or do they need their own ES aggregation evidence?",
        "If the parser constraint set changes, how is the inferred-filter policy kept consistent with the spec thresholds?",
      ),
    ),
  )

  /**
   * Per-query coverage derived strictly from the listed eval-query JSON. Fields not present in the
   * JSON (facets expectations, inferredFilters expectations) are recorded as `false` for the
   * has*Expectations flags and `Unknown` for any field whose JSON key was absent.
   *
   * `inform*` flags are derived from which expected fields are present in the JSON plus the
   * source-confirmed fact that facets and inferred filters are not represented in the dataset.
   * They are derived, not invented: a flag is true only if the listed source files (JSON + spec +
   * M19 evidence) actually carry information that can inform the corresponding component.
   */
  final case class EvalQueryCoverage(
    queryId: String,
    queryText: Option[String],
    language: Option[String],
    queryClasses: List[EngineEvalQueryClass],
    rawQueryTypes: List[String],
    hasVariantCarouselExpectations: Boolean,
    hasProviderCarouselExpectations: Boolean,
    hasServiceIntentCarouselExpectations: Boolean,
    hasFacetsExpectations: Boolean,
    hasInferredFiltersExpectations: Boolean,
    hasTopKConstraints: Boolean,
    hasScoring: Boolean,
    variantAcceptableIdsCount: Option[Int],
    providerAcceptableIdsCount: Option[Int],
    serviceIntentAcceptableIdsCount: Option[Int],
    hasNotes: Boolean,
    notesPreview: Option[String],
    informVariantRecall: Boolean,
    informProviderCarousel: Boolean,
    informServiceIntentCarousel: Boolean,
    informFacets: Boolean,
    informInferredFilters: Boolean,
    candidateLevelOnly: Boolean,
  )

  object EvalQueryCoverage {

    /**
     * Build a coverage row from the raw per-query row, using only fields present in the JSON.
     * Missing fields are kept as `None`/`false`/`Nil`; never invented.
     */
    def fromQuery(query: BeautyQEvalQueryJson): EvalQueryCoverage = {
      val hasVariant  = query.expected.exists(_.variantCarousel.isDefined)
      val hasProvider = query.expected.exists(_.providerCarousel.isDefined)
      val hasService  = query.expected.exists(_.serviceIntentCarousel.isDefined)
      val hasFacets   = query.expected.exists(_.facets.isDefined)
      val hasInf      = query.expected.exists(_.inferredFilters.isDefined)
      val hasTopK     = query.expected.exists(_.topKConstraintPresent)
      val hasScoring  = query.scoring.isDefined

      val classes =
        EngineEvalQueryClass.fromQueryTypes(query.queryTypes) match {
          case Right(classes) => classes
          case Left(_)        => Nil
        }

      val informVariant     = hasVariant
      val informProvider    = hasProvider
      val informService     = hasService
      val informFacets      = false
      val informInferred    = false
      val candidateLevelOnly = !hasFacets && !hasInf

      EvalQueryCoverage(
        queryId = query.queryId,
        queryText = query.queryText,
        language = query.language,
        queryClasses = classes,
        rawQueryTypes = query.queryTypes,
        hasVariantCarouselExpectations = hasVariant,
        hasProviderCarouselExpectations = hasProvider,
        hasServiceIntentCarouselExpectations = hasService,
        hasFacetsExpectations = hasFacets,
        hasInferredFiltersExpectations = hasInf,
        hasTopKConstraints = hasTopK,
        hasScoring = hasScoring,
        variantAcceptableIdsCount = query.expected.flatMap(_.variantCarousel).flatMap(_.acceptableVariantIdsCount),
        providerAcceptableIdsCount = query.expected.flatMap(_.providerCarousel).flatMap(_.acceptableProviderLocationIdsCount),
        serviceIntentAcceptableIdsCount = query.expected.flatMap(_.serviceIntentCarousel).flatMap(_.acceptableServiceIdsCount),
        hasNotes = query.notes.nonEmpty,
        notesPreview = query.notes,
        informVariantRecall = informVariant,
        informProviderCarousel = informProvider,
        informServiceIntentCarousel = informService,
        informFacets = informFacets,
        informInferredFilters = informInferred,
        candidateLevelOnly = candidateLevelOnly,
      )
    }
  }

  /**
   * The full taxonomy, including the per-component fact sheet and the per-query eval coverage.
   * This is a pure data structure: no I/O, no DI, no backend, no effect.
   */
  final case class BeautyQResponseComponentTaxonomy(
    components: List[ResponseComponentFact],
    evalQueryCoverage: List[EvalQueryCoverage],
    offlineEvalOnly: Boolean,
    notServingPolicy: Boolean,
  )

  object BeautyQResponseComponentTaxonomy {

    /** Build a taxonomy from the in-memory eval-query coverage rows. */
    def fromEvalQueries(evalQueries: List[BeautyQEvalQueryJson]): BeautyQResponseComponentTaxonomy =
      BeautyQResponseComponentTaxonomy(
        components = ResponseComponentFacts,
        evalQueryCoverage = evalQueries.map(EvalQueryCoverage.fromQuery),
        offlineEvalOnly = true,
        notServingPolicy = true,
      )
  }
}

/**
 * Minimal, tolerant decoder of the listed eval-query JSON. Only the fields the listed source
 * files actually carry are decoded; missing fields stay missing. This decoder is intentionally
 * narrower than the full schema and will not invent facets or inferred-filters entries.
 */
final case class BeautyQEvalQueryJson(
  queryId: String,
  queryText: Option[String],
  language: Option[String],
  queryTypes: List[String],
  notes: Option[String],
  expected: Option[BeautyQEvalQueryExpectedJson],
  scoring: Option[BeautyQEvalQueryScoringJson],
)

final case class BeautyQEvalQueryExpectedJson(
  variantCarousel: Option[BeautyQEvalCarouselJson],
  providerCarousel: Option[BeautyQEvalCarouselJson],
  serviceIntentCarousel: Option[BeautyQEvalCarouselJson],
  facets: Option[io.circe.Json],
  inferredFilters: Option[io.circe.Json],
) {
  def topKConstraintPresent: Boolean =
    List(variantCarousel, providerCarousel, serviceIntentCarousel).flatten.exists(_.topK.isDefined)
}

final case class BeautyQEvalCarouselJson(
  acceptableVariantIdsCount: Option[Int],
  acceptableProviderLocationIdsCount: Option[Int],
  acceptableServiceIdsCount: Option[Int],
  topK: Option[io.circe.Json],
)

object BeautyQEvalQueryJson {

  /** Tolerant decoder: only top-level fields present in the listed JSON are read. */
  implicit val decoder: Decoder[BeautyQEvalQueryJson] =
    Decoder.instance { cursor =>
      for {
        queryId   <- cursor.get[String]("id")
        queryText <- cursor.get[Option[String]]("query")
        language  <- cursor.get[Option[String]]("language")
        queryTypes <- cursor.getOrElse[List[String]]("queryTypes")(Nil)
        notes     <- decodeNotes(cursor.downField("notes"))
        expected  <- cursor.get[Option[BeautyQEvalQueryExpectedJson]]("expected")
        scoring   <- cursor.get[Option[BeautyQEvalQueryScoringJson]]("scoring")
      } yield BeautyQEvalQueryJson(queryId, queryText, language, queryTypes, notes, expected, scoring)
    }

  private def decodeNotes(cursor: io.circe.ACursor): Decoder.Result[Option[String]] =
    if (!cursor.succeeded) Right(None)
    else
      cursor.as[String] match {
        case Right(value) => Right(nonEmpty(value))
        case Left(_) =>
          cursor.as[List[String]].map { values =>
            nonEmpty(values.mkString("; "))
          }
      }

  private def nonEmpty(value: String): Option[String] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) None else Some(trimmed)
  }
}

object BeautyQEvalQueryExpectedJson {

  implicit val decoder: Decoder[BeautyQEvalQueryExpectedJson] =
    Decoder.instance { cursor =>
      for {
        variantCarousel       <- cursor.get[Option[BeautyQEvalCarouselJson]]("variantCarousel")
        providerCarousel      <- cursor.get[Option[BeautyQEvalCarouselJson]]("providerCarousel")
        serviceIntentCarousel <- cursor.get[Option[BeautyQEvalCarouselJson]]("serviceIntentCarousel")
        facets                <- cursor.get[Option[io.circe.Json]]("facets")
        inferredFilters       <- cursor.get[Option[io.circe.Json]]("inferredFilters")
      } yield BeautyQEvalQueryExpectedJson(
        variantCarousel,
        providerCarousel,
        serviceIntentCarousel,
        facets,
        inferredFilters,
      )
    }
}

object BeautyQEvalCarouselJson {

  implicit val decoder: Decoder[BeautyQEvalCarouselJson] =
    Decoder.instance { cursor =>
      for {
        acceptableVariantIds            <- cursor.get[Option[List[String]]]("acceptableVariantIds")
        acceptableProviderLocationIds   <- cursor.get[Option[List[String]]]("acceptableProviderLocationIds")
        acceptableServiceIds            <- cursor.get[Option[List[String]]]("acceptableServiceIds")
        topK                            <- cursor.get[Option[io.circe.Json]]("topK")
      } yield BeautyQEvalCarouselJson(
        acceptableVariantIdsCount = acceptableVariantIds.map(_.size),
        acceptableProviderLocationIdsCount = acceptableProviderLocationIds.map(_.size),
        acceptableServiceIdsCount = acceptableServiceIds.map(_.size),
        topK = topK,
      )
    }
}

object BeautyQEvalQueryScoringJson {

  implicit val decoder: Decoder[BeautyQEvalQueryScoringJson] =
    Decoder.instance { cursor =>
      Right(
        BeautyQEvalQueryScoringJson(
          variantCarousel = cursor.downField("variantCarousel").succeeded,
          providerCarousel = cursor.downField("providerCarousel").succeeded,
          serviceIntentCarousel = cursor.downField("serviceIntentCarousel").succeeded,
        )
      )
    }
}

final case class BeautyQEvalQueryScoringJson(
  variantCarousel: Boolean,
  providerCarousel: Boolean,
  serviceIntentCarousel: Boolean,
)
