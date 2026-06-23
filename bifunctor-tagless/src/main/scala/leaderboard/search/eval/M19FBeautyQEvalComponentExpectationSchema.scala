package leaderboard.search.eval

import io.circe.{Decoder, Json}

/**
 * M19F: pure, tolerant eval-query schema support for component-level BeautyQ response
 * expectations.
 *
 * M19E proved (from the M19C/M19D coverage-gap chain) that provider carousel, service intent
 * carousel, facets, and inferred filters need explicit eval expectation support before a
 * component-level hybrid policy could be chosen. M19F makes the eval dataset *capable of
 * carrying* that evidence: it adds an optional, source-confirmed expectation schema plus a
 * tolerant decoder, and a coverage view that distinguishes three honest states per component.
 *
 * It does NOT invent seed expected values. The only expectation shapes decoded here are the ones
 * the listed response model (`BeautySearchModels`) confirms:
 *   - provider carousel  : `ProviderSearchResult` { masterLocationId, matchingVariantCount,
 *                          sampleMatchingVariantIds } grouped by masterLocationId
 *                          (BeautySearchSpecV1.carouselSpec.providerGroupField);
 *   - service carousel   : `ServiceIntentSearchResult` { serviceId, matchingVariantCount }
 *                          grouped by serviceId
 *                          (BeautySearchSpecV1.carouselSpec.serviceIntentGroupField);
 *   - facets             : `BeautySearchFacet` { fieldPath, values: [{ value, count }] };
 *   - inferred filters   : `BeautySearchAppliedFilter` { constraint, explicit }.
 *
 * Boundaries:
 *   - pure data only; no DI, no effects, no backend calls, no HTTP, no plugin, no production
 *     `/beauty-search` route;
 *   - the decoder is tolerant: the existing checked-in 63-query JSON still decodes; absent fields
 *     stay `None` / `AbsentFromDataset`, never false evidence;
 *   - synthetic fixture JSON may carry the new fields to prove decoding and coverage propagation;
 *   - this is an offline/eval-only surface. It does not choose a hybrid policy, does not claim a
 *     component policy is ready, and does not claim Qdrant can own facets or inferred filters.
 */
object M19FBeautyQEvalComponentExpectationSchema {

  /**
   * Honest tri-state availability of a component expectation field, computed for one query
   * relative to the whole dataset.
   *
   *   - `AbsentFromDataset`         : the schema supports the field, but no query in the dataset
   *                                   carries it (e.g. facets in the current checked-in JSON);
   *   - `SupportedButAbsentInQuery` : the schema supports the field and at least one query carries
   *                                   it, but *this* query does not;
   *   - `PresentWithExpectations`   : this query carries expectations for the field.
   */
  enum ExpectationFieldAvailability {
    case AbsentFromDataset
    case SupportedButAbsentInQuery
    case PresentWithExpectations
  }

  object ExpectationFieldAvailability {
    val stableOrder: List[ExpectationFieldAvailability] =
      List(AbsentFromDataset, SupportedButAbsentInQuery, PresentWithExpectations)

    /** Derive the tri-state from per-query presence and whether any query in the dataset carries it. */
    def of(presentInQuery: Boolean, presentSomewhereInDataset: Boolean): ExpectationFieldAvailability =
      if (presentInQuery) PresentWithExpectations
      else if (presentSomewhereInDataset) SupportedButAbsentInQuery
      else AbsentFromDataset
  }

  // --- Source-confirmed expectation shapes (BeautySearchModels) ---------------------------------

  /** One facet value, source-confirmed: `BeautySearchFacetValue` { value, count }. */
  final case class FacetValueExpectation(value: String, count: Option[Int])

  /** One facet field, source-confirmed: `BeautySearchFacet` { fieldPath, values }. */
  final case class FacetExpectation(fieldPath: String, values: List[FacetValueExpectation])

  /**
   * One inferred-filter expectation, source-confirmed: `BeautySearchAppliedFilter`
   * { constraint, explicit }. `constraint` is kept as raw JSON because `SearchConstraint` is not
   * one of the listed source files; only the `constraint`/`explicit` keys are source-confirmed.
   */
  final case class InferredFilterExpectation(constraint: Option[Json], explicit: Option[Boolean])

  /**
   * One sample provider, source-confirmed subset of `ProviderSearchResult`. All fields are
   * optional so a fixture may carry only the parts it can confirm; nothing is invented.
   */
  final case class ProviderMatchSample(
    masterLocationId: Option[String],
    matchingVariantCount: Option[Int],
    sampleMatchingVariantIds: List[String],
  )

  /**
   * Provider carousel expectation. `expectedGroupingField` is the proposed new field
   * (BeautySearchSpecV1.carouselSpec.providerGroupField = "masterLocationId"); it stays `None`
   * when absent. `acceptableProviderLocationIds` is already present in the checked-in JSON.
   */
  final case class ProviderCarouselExpectation(
    acceptableProviderLocationIds: List[String],
    expectedGroupingField: Option[String],
    samples: List[ProviderMatchSample],
  )

  /** One sample service, source-confirmed subset of `ServiceIntentSearchResult`. */
  final case class ServiceMatchSample(
    serviceId: Option[String],
    matchingVariantCount: Option[Int],
  )

  /**
   * Service intent carousel expectation. `expectedGroupingField` is the proposed new field
   * (BeautySearchSpecV1.carouselSpec.serviceIntentGroupField = "serviceId").
   */
  final case class ServiceIntentCarouselExpectation(
    acceptableServiceIds: List[String],
    expectedGroupingField: Option[String],
    samples: List[ServiceMatchSample],
  )

  /**
   * Optional component-level expectations for one query. Every component is `Option`: absent means
   * the query's JSON does not carry that expectation, not that the expectation is empty/false.
   */
  final case class ComponentExpectations(
    provider: Option[ProviderCarouselExpectation],
    serviceIntent: Option[ServiceIntentCarouselExpectation],
    facets: Option[List[FacetExpectation]],
    inferredFilters: Option[List[InferredFilterExpectation]],
  )

  object ComponentExpectations {
    val empty: ComponentExpectations = ComponentExpectations(None, None, None, None)
  }

  /** One decoded eval query carrying only its id and its optional component expectations. */
  final case class EvalQueryComponentExpectations(
    queryId: String,
    expectations: ComponentExpectations,
  )

  // --- Tolerant decoders ------------------------------------------------------------------------

  implicit val facetValueDecoder: Decoder[FacetValueExpectation] =
    Decoder.instance { c =>
      for {
        value <- c.get[String]("value")
        count <- c.get[Option[Int]]("count")
      } yield FacetValueExpectation(value, count)
    }

  implicit val facetDecoder: Decoder[FacetExpectation] =
    Decoder.instance { c =>
      for {
        fieldPath <- c.get[String]("fieldPath")
        values    <- c.getOrElse[List[FacetValueExpectation]]("values")(Nil)
      } yield FacetExpectation(fieldPath, values)
    }

  implicit val inferredFilterDecoder: Decoder[InferredFilterExpectation] =
    Decoder.instance { c =>
      for {
        constraint <- c.get[Option[Json]]("constraint")
        explicit   <- c.get[Option[Boolean]]("explicit")
      } yield InferredFilterExpectation(constraint, explicit)
    }

  implicit val providerMatchSampleDecoder: Decoder[ProviderMatchSample] =
    Decoder.instance { c =>
      for {
        masterLocationId         <- c.get[Option[String]]("masterLocationId")
        matchingVariantCount     <- c.get[Option[Int]]("matchingVariantCount")
        sampleMatchingVariantIds <- c.getOrElse[List[String]]("sampleMatchingVariantIds")(Nil)
      } yield ProviderMatchSample(masterLocationId, matchingVariantCount, sampleMatchingVariantIds)
    }

  implicit val providerExpectationDecoder: Decoder[ProviderCarouselExpectation] =
    Decoder.instance { c =>
      for {
        ids      <- c.getOrElse[List[String]]("acceptableProviderLocationIds")(Nil)
        grouping <- c.get[Option[String]]("expectedGroupingField")
        samples  <- c.getOrElse[List[ProviderMatchSample]]("sampleAcceptableProviders")(Nil)
      } yield ProviderCarouselExpectation(ids, grouping, samples)
    }

  implicit val serviceMatchSampleDecoder: Decoder[ServiceMatchSample] =
    Decoder.instance { c =>
      for {
        serviceId            <- c.get[Option[String]]("serviceId")
        matchingVariantCount <- c.get[Option[Int]]("matchingVariantCount")
      } yield ServiceMatchSample(serviceId, matchingVariantCount)
    }

  implicit val serviceExpectationDecoder: Decoder[ServiceIntentCarouselExpectation] =
    Decoder.instance { c =>
      for {
        ids      <- c.getOrElse[List[String]]("acceptableServiceIds")(Nil)
        grouping <- c.get[Option[String]]("expectedGroupingField")
        samples  <- c.getOrElse[List[ServiceMatchSample]]("sampleAcceptableServices")(Nil)
      } yield ServiceIntentCarouselExpectation(ids, grouping, samples)
    }

  implicit val componentExpectationsDecoder: Decoder[ComponentExpectations] =
    Decoder.instance { c =>
      for {
        provider <- c.get[Option[ProviderCarouselExpectation]]("providerCarousel")
        service  <- c.get[Option[ServiceIntentCarouselExpectation]]("serviceIntentCarousel")
        facets   <- c.get[Option[List[FacetExpectation]]]("facets")
        inferred <- c.get[Option[List[InferredFilterExpectation]]]("inferredFilters")
      } yield ComponentExpectations(provider, service, facets, inferred)
    }

  /**
   * Tolerant per-query decoder. Reads only `id` and the optional `expected` object; a query
   * without an `expected` object decodes to `ComponentExpectations.empty`. No field is invented.
   */
  implicit val evalQueryComponentExpectationsDecoder: Decoder[EvalQueryComponentExpectations] =
    Decoder.instance { c =>
      for {
        queryId  <- c.get[String]("id")
        expected <- c.getOrElse[Option[ComponentExpectations]]("expected")(None)
      } yield EvalQueryComponentExpectations(queryId, expected.getOrElse(ComponentExpectations.empty))
    }

  // --- Coverage view ----------------------------------------------------------------------------

  /** Per-query component-expectation availability, all four components in honest tri-state. */
  final case class ComponentExpectationCoverageRow(
    queryId: String,
    provider: ExpectationFieldAvailability,
    serviceIntent: ExpectationFieldAvailability,
    facets: ExpectationFieldAvailability,
    inferredFilters: ExpectationFieldAvailability,
  )

  /**
   * Dataset-level component-expectation coverage. The `*PresentInDataset` flags record whether the
   * schema-supported field is exercised by at least one query; the per-query rows then distinguish
   * `SupportedButAbsentInQuery` from `AbsentFromDataset`.
   */
  final case class ComponentExpectationCoverage(
    rows: List[ComponentExpectationCoverageRow],
    providerPresentInDataset: Boolean,
    serviceIntentPresentInDataset: Boolean,
    facetsPresentInDataset: Boolean,
    inferredFiltersPresentInDataset: Boolean,
    offlineEvalOnly: Boolean,
    notServingPolicy: Boolean,
    doesNotApproveHybrid: Boolean,
    qdrantDoesNotOwnFacets: Boolean,
    qdrantDoesNotOwnInferredFilters: Boolean,
  )

  object ComponentExpectationCoverage {

    /**
     * Build the coverage view from decoded per-query expectations. Dataset-level presence is
     * computed first, then each per-query row is reduced to the honest tri-state. No field is
     * invented; the M19D-honesty flags (Qdrant does not own facets / inferred filters) are carried
     * through unchanged.
     */
    def fromQueries(queries: List[EvalQueryComponentExpectations]): ComponentExpectationCoverage = {
      val providerAny = queries.exists(_.expectations.provider.isDefined)
      val serviceAny  = queries.exists(_.expectations.serviceIntent.isDefined)
      val facetsAny   = queries.exists(_.expectations.facets.isDefined)
      val inferredAny = queries.exists(_.expectations.inferredFilters.isDefined)

      val rows = queries.map { q =>
        ComponentExpectationCoverageRow(
          queryId = q.queryId,
          provider = ExpectationFieldAvailability.of(q.expectations.provider.isDefined, providerAny),
          serviceIntent = ExpectationFieldAvailability.of(q.expectations.serviceIntent.isDefined, serviceAny),
          facets = ExpectationFieldAvailability.of(q.expectations.facets.isDefined, facetsAny),
          inferredFilters = ExpectationFieldAvailability.of(q.expectations.inferredFilters.isDefined, inferredAny),
        )
      }

      ComponentExpectationCoverage(
        rows = rows,
        providerPresentInDataset = providerAny,
        serviceIntentPresentInDataset = serviceAny,
        facetsPresentInDataset = facetsAny,
        inferredFiltersPresentInDataset = inferredAny,
        offlineEvalOnly = true,
        notServingPolicy = true,
        doesNotApproveHybrid = true,
        qdrantDoesNotOwnFacets = true,
        qdrantDoesNotOwnInferredFilters = true,
      )
    }
  }
}
