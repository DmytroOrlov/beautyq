package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.{PublicOperator as _, *}
import leaderboard.search.gen2.core.plan.*
import org.scalatest.wordspec.AnyWordSpec

/** BeautyQSearchPlanCompiler proofs built exclusively from real, validated requests
  * (`BeautySearchRequestGen2.validate`) and real parsed intents (`BeautyQIntentParserGen2.parse`) - never
  * a directly-constructed `ValidatedBeautySearchRequestGen2`/`ParsedBeautyIntentGen2`.
  */
final class BeautyQSearchPlanCompilerSpec extends AnyWordSpec {

  private val Fields = BeautyQSearchDeclarations.variants.Fields
  private val vocabulary = BeautyQIntentVocabulary.value
  private val page = PageRequest(None, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))
  private val berlin = GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))

  private def rawRequest(
    query: Option[String],
    filters: Vector[PublicFilterInput],
    requestedFacets: Vector[FacetId],
    sort: Vector[BeautySortInput],
    userLocation: Option[GeoPoint],
    pageRequest: PageRequest,
  ): BeautySearchRequestGen2 =
    BeautySearchRequestGen2(query, filters, requestedFacets, sort, pageRequest, userLocation)

  private def validate(request: BeautySearchRequestGen2): ValidatedBeautySearchRequestGen2 =
    BeautySearchRequestGen2.validate(request) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture request failed to validate: ${errors.toVector}")
    }

  private def parse(request: ValidatedBeautySearchRequestGen2): ParsedBeautyIntentGen2 =
    BeautyQIntentParserGen2.parse(request, vocabulary) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture intent failed to parse: ${errors.toVector}")
    }

  private def build(
    query: Option[String] = None,
    filters: Vector[PublicFilterInput] = Vector.empty,
    requestedFacets: Vector[FacetId] = Vector.empty,
    sort: Vector[BeautySortInput] = Vector.empty,
    userLocation: Option[GeoPoint] = None,
    pageRequest: PageRequest = page,
  ): (ValidatedBeautySearchRequestGen2, ParsedBeautyIntentGen2) = {
    val validated = validate(rawRequest(query, filters, requestedFacets, sort, userLocation, pageRequest))
    (validated, parse(validated))
  }

  private def compile(request: ValidatedBeautySearchRequestGen2, intent: ParsedBeautyIntentGen2): CompiledBeautyQSearchPlan =
    BeautyQSearchPlanCompiler.compile(request, intent) match {
      case Right(value) => value
      case Left(errors) => fail(s"expected compilation success, got ${errors.toVector}")
    }

  private def compileErrors(request: ValidatedBeautySearchRequestGen2, intent: ParsedBeautyIntentGen2): Vector[BeautyQSearchPlanCompileError] =
    BeautyQSearchPlanCompiler.compile(request, intent) match {
      case Left(errors) => errors.toVector
      case Right(value) => fail(s"expected compilation failure, got $value")
    }

  private def transportCursor(bound: BoundSearchPlan[VariantSearchDocumentGen2], backendState: String): SearchCursor =
    SearchCursor.fromTransport(SearchCursorEnvelope.issue(bound, backendState).opaqueValue)

  private def expectCursorMismatch(
    label: String,
    first: BeautySearchRequestGen2,
    changed: BeautySearchRequestGen2,
  ): Unit = {
    val firstValidated = validate(first)
    val firstIntent = parse(firstValidated)
    val firstCompiled = compile(firstValidated, firstIntent)
    val cursor = transportCursor(firstCompiled.boundPlan, s"transport.$label")
    val changedWithCursor = changed.copy(page = changed.page.copy(cursor = Some(cursor)))
    val changedValidated = validate(changed)
    val changedIntent = parse(changedValidated)
    val changedWithCursorValidated = validate(changedWithCursor)
    val changedWithCursorIntent = parse(changedWithCursorValidated)
    val expectedChanged = compile(changedValidated, changedIntent)
    assert(
      compileErrors(changedWithCursorValidated, changedWithCursorIntent) ==
        Vector(BeautyQSearchPlanCompileError.CursorValidationFailed(SearchCursorError.PlanIdentityMismatch(
          expected = expectedChanged.boundPlan.identityHash,
          actual = firstCompiled.boundPlan.identityHash,
        ))),
      label,
    )
    (): Unit
  }

  private def distanceFilter(meters: String): PublicFilterInput = PublicFilterInput(PublicFieldName("distanceMeters"), PublicOperator.WithinDistance, PublicFilterValue.Scalar(meters), None)
  private def serviceFilter(value: String): PublicFilterInput = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar(value), None)

  "CompiledBeautyQSearchPlan" should {
    "reject construction outside BeautyQSearchPlanCompiler" in {
      assertDoesNotCompile("""new BeautyQSearchPlanCompiler.CompiledBeautyQSearchPlan(null, BeautyQSearchPlanMode.DefaultBrowse, Vector.empty, Vector.empty)""")
    }

    "reject subclassing outside BeautyQSearchPlanCompiler" in {
      assertDoesNotCompile("""final class ForgedPlan extends BeautyQSearchPlanCompiler.CompiledBeautyQSearchPlan""")
    }
  }

  "BoundSearchPlan" should {
    "reject direct construction outside the framework owner" in {
      assertDoesNotCompile(
        """new BoundSearchPlan[VariantSearchDocumentGen2](null, null, null, None)"""
      )
    }

    "reject subclassing outside the framework owner" in {
      assertDoesNotCompile(
        """final class ForgedBound extends BoundSearchPlan[VariantSearchDocumentGen2](null, null, null, None)"""
      )
    }

    "reject the framework factory and case-class-style copying outside the framework owner" in {
      assertDoesNotCompile(
        """BoundSearchPlan.create[VariantSearchDocumentGen2](null, null, null, None)"""
      )
      assertDoesNotCompile(
        """def forge(bound: BoundSearchPlan[VariantSearchDocumentGen2]) = bound.copy(plan = bound.plan)"""
      )
    }
  }

  // ---- Geo behavior ----

  "geo behavior" should {
    "resolve a radius filter with a user location" in {
      val (request, intent) = build(filters = Vector(distanceFilter("2000")), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.map(_.source.constraint) == Vector(PlannedConstraint.GeoDistanceFilter(Fields.location, berlin, Distance(2000))))
    }

    "fail with the exact filter index/name when a radius filter has no user location" in {
      val (request, intent) = build(filters = Vector(distanceFilter("2000")), userLocation = None)
      val errors = compileErrors(request, intent)
      assert(errors == Vector(BeautyQSearchPlanCompileError.PublicInputResolutionFailed(PublicPlanInputResolutionError.MissingLocationForFilter(0, PublicFieldName("distanceMeters")))))
    }

    "resolve a geo sort with a user location" in {
      val (request, intent) = build(sort = Vector(BeautySortInput(PublicSortName("distanceMeters"), SortDirection.Asc)), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(result.plan.sort == Vector(PlannedSort.GeoDistance(Fields.location, berlin, SortDirection.Asc)))
    }

    "fail with the exact sort index/name when a geo sort has no user location" in {
      val (request, intent) = build(sort = Vector(BeautySortInput(PublicSortName("distanceMeters"), SortDirection.Asc)), userLocation = None)
      val errors = compileErrors(request, intent)
      assert(errors == Vector(BeautyQSearchPlanCompileError.PublicInputResolutionFailed(PublicPlanInputResolutionError.MissingLocationForSort(0, PublicSortName("distanceMeters")))))
    }

    "accumulate multiple missing filter/sort locations in exact order" in {
      val (request, intent) =
        build(
          filters = Vector(distanceFilter("2000"), distanceFilter("500")),
          sort = Vector(BeautySortInput(PublicSortName("distanceMeters"), SortDirection.Desc)),
          userLocation = None,
        )
      val errors = compileErrors(request, intent)
      assert(
        errors ==
          Vector(
            BeautyQSearchPlanCompileError.PublicInputResolutionFailed(PublicPlanInputResolutionError.MissingLocationForFilter(0, PublicFieldName("distanceMeters"))),
            BeautyQSearchPlanCompileError.PublicInputResolutionFailed(PublicPlanInputResolutionError.MissingLocationForFilter(1, PublicFieldName("distanceMeters"))),
            BeautyQSearchPlanCompileError.PublicInputResolutionFailed(PublicPlanInputResolutionError.MissingLocationForSort(0, PublicSortName("distanceMeters"))),
          )
      )
    }

    "add no filter, sort, or signal when coordinates alone are present with no geo request" in {
      val (request, intent) = build(query = Some("маникюр"), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(result.plan.softSignals.isEmpty)
      assert(!result.plan.appliedFilters.exists(_.source.constraint match {
        case PlannedConstraint.GeoDistanceFilter(_, _, _) => true
        case _ => false
      }))
      assert(!result.plan.sort.exists {
        case PlannedSort.GeoDistance(_, _, _) => true
        case _ => false
      })
    }

    "produce exactly one GeoProximitySignal for NearUser with location" in {
      val (request, intent) = build(query = Some("рядом"), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(result.plan.softSignals == Vector(PlannedSignal.GeoProximitySignal(Fields.location, berlin)))
    }

    "retain the NearUser signal and hard filter together when both a general near-me query and an explicit radius filter are present" in {
      val (request, intent) = build(query = Some("маникюр рядом"), filters = Vector(distanceFilter("1500")), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(result.plan.softSignals == Vector(PlannedSignal.GeoProximitySignal(Fields.location, berlin)))
      assert(result.plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.GeoDistanceFilter(Fields.location, berlin, Distance(1500))))
    }

    "retain the NearUser signal and geo sort together when both are present" in {
      val (request, intent) = build(query = Some("маникюр рядом"), sort = Vector(BeautySortInput(PublicSortName("distanceMeters"), SortDirection.Asc)), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(result.plan.softSignals == Vector(PlannedSignal.GeoProximitySignal(Fields.location, berlin)))
      assert(result.plan.sort == Vector(PlannedSort.GeoDistance(Fields.location, berlin, SortDirection.Asc)))
    }

    "never infer a radius filter from NearUser alone" in {
      val (request, intent) = build(query = Some("рядом"), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(!result.plan.appliedFilters.exists(_.source.constraint match {
        case PlannedConstraint.GeoDistanceFilter(_, _, _) => true
        case _ => false
      }))
    }
  }

  // ---- Public hard constraints ----

  "public hard constraints" should {
    "apply an ExplicitUi filter" in {
      val (request, intent) = build(filters = Vector(serviceFilter("manicure")))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters == Vector(AppliedFilter(SourcedConstraint(PlannedConstraint.Terms(Fields.serviceCode, Set(ServiceCodeOf("manicure"))), ConstraintProvenance.ExplicitUi))))
    }

    "apply a FacetSelection filter with its exact selection ID provenance" in {
      val filter = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), Some(FacetSelectionId("sel-1")))
      val (request, intent) = build(filters = Vector(filter))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.headOption.getOrElse(fail("expected one applied filter")).source.provenance == ConstraintProvenance.FacetSelection(FacetSelectionId("sel-1")))
    }

    "treat ExplicitUi and FacetSelection as equal priority: the first of two equivalent public values wins" in {
      val explicit = serviceFilter("manicure")
      val selected = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), Some(FacetSelectionId("sel-2")))
      val (request, intent) = build(filters = Vector(explicit, selected))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.size == 1)
      assert(result.plan.appliedFilters.headOption.getOrElse(fail("expected one applied filter")).source.provenance == ConstraintProvenance.ExplicitUi)
      assert(result.plan.diagnostics.suppressedFilters.headOption.getOrElse(fail("expected one suppressed filter")).reason == SuppressionReason.EquivalentDuplicate)
    }

    "report a conflict with exact indexes and canonical values for two non-equivalent public values in one slot" in {
      val (request, intent) = build(filters = Vector(serviceFilter("manicure"), serviceFilter("pedicure")))
      val errors = compileErrors(request, intent)
      errors match {
        case Vector(BeautyQSearchPlanCompileError.PlanCompilationFailed(SearchPlanCompilationError.ConstraintResolutionFailed(conflict: ConstraintResolutionError.ConflictingHigherPriority))) =>
          assert(conflict.firstIndex == 0)
          assert(conflict.secondIndex == 1)
          assert(conflict.slot == ConstraintSlot.Terms(Fields.serviceCode.id))
        case other => fail(s"expected one ConflictingHigherPriority, got $other")
      }
    }

    "let different public slots coexist" in {
      val (request, intent) = build(filters = Vector(serviceFilter("manicure"), distanceFilter("2000")), userLocation = Some(berlin))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.size == 2)
    }
  }

  // ---- Parsed hard constraints ----

  "parsed hard constraints" should {
    "apply a unique parsed constraint after the public constraint in a different slot" in {
      // "шелак" (r022) parses to exactly one hard action - an enum-attribute constraint - a genuinely
      // different slot from the public serviceCode filter, so both survive unconflicted.
      val (request, intent) = build(query = Some("шелак"), filters = Vector(serviceFilter("manicure")))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.Terms(Fields.serviceCode, Set(ServiceCodeOf("manicure")))))
      assert(result.plan.appliedFilters.exists(af => af.source.provenance == ConstraintProvenance.ParsedHard && af.source.constraint == PlannedConstraint.Terms(Fields.enumAttributesByCode("nail_coating_type"), Set("shellac"))))
    }

    "suppress a parsed constraint equivalent to the applied public constraint as EquivalentDuplicate" in {
      // "ресницы" (r006) parses to exactly one hard action - Terms(serviceCode, lashes) - so pairing it
      // with an equal public serviceCode filter isolates the same-slot/same-value duplicate case.
      val (request, intent) = build(query = Some("ресницы"), filters = Vector(serviceFilter("lashes")))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.size == 1)
      assert(result.plan.diagnostics.suppressedFilters.exists(sf => sf.reason == SuppressionReason.EquivalentDuplicate && sf.source.provenance == ConstraintProvenance.ParsedHard))
    }

    "suppress a parsed constraint non-equivalent to the applied public constraint as OverriddenByHigherPrecedence" in {
      // "ресницы" (r006) parses to exactly one hard action - Terms(serviceCode, lashes) - so pairing it
      // with a different public serviceCode filter isolates the same-slot/non-equivalent override case.
      val (request, intent) = build(query = Some("ресницы"), filters = Vector(serviceFilter("pedicure")))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.map(_.source.constraint) == Vector(PlannedConstraint.Terms(Fields.serviceCode, Set(ServiceCodeOf("pedicure")))))
      assert(result.plan.diagnostics.suppressedFilters.exists(sf => sf.reason == SuppressionReason.OverriddenByHigherPrecedence && sf.source.provenance == ConstraintProvenance.ParsedHard))
    }

    "apply a parsed constraint in a slot different from any public constraint" in {
      val (request, intent) = build(query = Some("гель наращивание ногтей"), filters = Vector(serviceFilter("manicure")))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.exists(_.source.constraint == PlannedConstraint.Terms(Fields.enumAttributesByCode("nail_coating_type"), Set("gel"))))
    }

    "suppress a later parsed duplicate within the parsed vector, keeping the first" in {
      // "маникюр" and a canonical alias both resolve to the manicure service action; the parser
      // deduplicates matched rule actions before compiling, so only one Terms(serviceCode) constraint is
      // ever produced for this query - proving parsed-tier same-priority dedup end to end.
      val (request, intent) = build(query = Some("маникюр обычный маникюр"))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.count(_.source.constraint == PlannedConstraint.Terms(Fields.serviceCode, Set(ServiceCodeOf("manicure")))) == 1)
    }

    "keep parsed provenance as ParsedHard, distinct from unaffected public provenance" in {
      val (request, intent) = build(query = Some("шелак"), filters = Vector(serviceFilter("manicure")))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.exists(af => af.source.constraint == PlannedConstraint.Terms(Fields.serviceCode, Set(ServiceCodeOf("manicure"))) && af.source.provenance == ConstraintProvenance.ExplicitUi))
      assert(result.plan.appliedFilters.exists(af => af.source.provenance == ConstraintProvenance.ParsedHard))
    }

    "produce applied filters with public constraints before parsed constraints" in {
      val (request, intent) = build(query = Some("шелак"), filters = Vector(serviceFilter("manicure")))
      val result = compile(request, intent)
      assert(result.plan.appliedFilters.headOption.getOrElse(fail("expected a public applied filter")).source.provenance == ConstraintProvenance.ExplicitUi)
      assert(result.plan.appliedFilters(1).source.provenance == ConstraintProvenance.ParsedHard)
    }

    "produce suppressed filters deterministically across repeated compilations of the same input" in {
      val (request, intent) = build(query = Some("маникюр"), filters = Vector(serviceFilter("pedicure")))
      val first = compile(request, intent)
      val second = compile(request, intent)
      assert(first.plan.diagnostics.suppressedFilters == second.plan.diagnostics.suppressedFilters)
    }
  }

  // ---- Constraint shapes ----

  "constraint shapes" should {
    "compile a service Terms constraint" in {
      val (request, intent) = build(filters = Vector(serviceFilter("lashes")))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.Terms(Fields.serviceCode, Set(ServiceCodeOf("lashes")))))
    }

    "compile a category Terms constraint" in {
      val filter = PublicFilterInput(PublicFieldName("category"), PublicOperator.Equal, PublicFilterValue.Scalar("nails"), None)
      val (request, intent) = build(filters = Vector(filter))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.Terms(Fields.categoryCode, Set(CategoryCodeOf("nails")))))
    }

    "compile an enum attribute Terms constraint" in {
      val (request, intent) = build(query = Some("шелак"))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.Terms(Fields.enumAttributesByCode("nail_coating_type"), Set("shellac"))))
    }

    "compile a boolean attribute Terms constraint" in {
      val (request, intent) = build(query = Some("реснички 2д корр"))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.Terms(Fields.booleanAttributesByCode("with_correction"), Set(true))))
    }

    "compile an int attribute NumberRange constraint" in {
      val (request, intent) = build(query = Some("6 сеансов"))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.NumberRange(Fields.intAttributesByCode("session_count"), RangeBounds(Bound.Inclusive(6), Bound.Inclusive(6)))))
    }

    "compile a decimal attribute filter through the public registry" in {
      val filter = PublicFilterInput(PublicFieldName("attribute.decimal.deposit_amount"), PublicOperator.GreaterThanOrEqual, PublicFilterValue.Scalar("10"), None)
      val (request, intent) = build(filters = Vector(filter))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.NumberRange(Fields.decimalAttributesByCode("deposit_amount"), RangeBounds(Bound.Inclusive(BigDecimal(10)), Bound.Unbounded))))
    }

    "compile a price IntervalOverlap constraint from budget text" in {
      val (request, intent) = build(query = Some("маникюр under 50"))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.IntervalOverlap(Fields.priceFrom, Fields.priceTo, RangeBounds(Bound.Unbounded, Bound.Inclusive(BigDecimal(50))))))
    }

    "compile a duration NumberRange constraint through the public registry" in {
      val filter = PublicFilterInput(PublicFieldName("durationMinutes"), PublicOperator.LessThanOrEqual, PublicFilterValue.Scalar("60"), None)
      val (request, intent) = build(filters = Vector(filter))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.NumberRange(Fields.durationMin, RangeBounds(Bound.Unbounded, Bound.Inclusive(60)))))
    }

    "compile a geo radius GeoDistanceFilter constraint" in {
      val (request, intent) = build(filters = Vector(distanceFilter("3000")), userLocation = Some(berlin))
      assert(compile(request, intent).plan.appliedFilters.map(_.source.constraint).contains(PlannedConstraint.GeoDistanceFilter(Fields.location, berlin, Distance(3000))))
    }
  }

  // ---- Plan sections ----

  "plan sections" should {
    "take residual text only from the parsed intent, never the raw request query" in {
      val (request, intent) = build(query = Some("нечто маникюр непонятное"))
      val result = compile(request, intent)
      assert(result.plan.residualText == intent.residualText)
      assert(result.plan.residualText != request.query)
    }

    "preserve soft signal order" in {
      val (request, intent) = build(query = Some("рядом"), userLocation = Some(berlin))
      assert(compile(request, intent).plan.softSignals == intent.softSignals)
    }

    "preserve sort order" in {
      val sorts = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc), BeautySortInput(PublicSortName("durationMinutes"), SortDirection.Desc))
      val (request, intent) = build(sort = sorts)
      assert(compile(request, intent).plan.sort == Vector(PlannedSort.FieldValue(Fields.priceFrom, SortDirection.Asc), PlannedSort.FieldValue(Fields.durationMin, SortDirection.Desc)))
    }

    "preserve page size" in {
      val (request, intent) = build()
      assert(compile(request, intent).plan.page.size == page.size)
    }

    "carry the page value through unchanged after cursor binding" in {
      val (request, intent) = build()
      assert(compile(request, intent).plan.page == request.page)
    }

    "validate a real second-page cursor against the compiled plan identity" in {
      val (firstRequest, firstIntent) = build(query = Some("ресницы"))
      val first = compile(firstRequest, firstIntent)
      val cursor = transportCursor(first.boundPlan, "elasticsearch.search-after.v1")
      val (secondRequest, secondIntent) = build(query = Some("ресницы"), pageRequest = page.copy(cursor = Some(cursor)))

      val result = compile(secondRequest, secondIntent)
      assert(!result.boundPlan.isFirstPage)
      assert(result.boundPlan.backendState.map(_.opaqueValue) == Some("elasticsearch.search-after.v1"))
    }

    "reject a cursor issued for a different compiled plan" in {
      val (firstRequest, firstIntent) = build(query = Some("ресницы"))
      val first = compile(firstRequest, firstIntent)
      val cursor = transportCursor(first.boundPlan, "state")
      val (changedRequest, changedIntent) = build(query = Some("маникюр"), pageRequest = page.copy(cursor = Some(cursor)))
      val (changedFirstRequest, changedFirstIntent) = build(query = Some("маникюр"))
      val changedFirst = compile(changedFirstRequest, changedFirstIntent)

      assert(
        compileErrors(changedRequest, changedIntent) ==
          Vector(BeautyQSearchPlanCompileError.CursorValidationFailed(SearchCursorError.PlanIdentityMismatch(
            expected = changedFirst.boundPlan.identityHash,
            actual = first.boundPlan.identityHash,
          )))
      )
    }

    "reject cursor mismatches for every BeautyQ plan identity input" in {
      expectCursorMismatch(
        "query",
        rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, None, page),
        rawRequest(Some("маникюр"), Vector.empty, Vector.empty, Vector.empty, None, page),
      )
      expectCursorMismatch(
        "filter",
        rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, None, page),
        rawRequest(Some("ресницы"), Vector(serviceFilter("manicure")), Vector.empty, Vector.empty, None, page),
      )
      expectCursorMismatch(
        "sort",
        rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, None, page),
        rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc)), None, page),
      )
      expectCursorMismatch(
        "facet",
        rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, None, page),
        rawRequest(Some("ресницы"), Vector.empty, Vector(FacetId("service")), Vector.empty, None, page),
      )
      val largerPage = PageSize.from(25) match {
        case Right(value) => PageRequest(None, value)
        case Left(error)  => fail(s"expected a valid alternate PageSize, got $error")
      }
      expectCursorMismatch(
        "page-size",
        rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, None, page),
        rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, None, largerPage),
      )
    }

    "reject a cursor bound under a different contract version through the production compiler" in {
      val raw = rawRequest(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, None, page)
      val request = validate(raw)
      val intent = parse(request)
      val first = compile(request, intent)
      val alternateView = CanonicalPlanView[VariantSearchDocumentGen2](
        PlanContractFingerprint.compute(
          PlanContractVersion("beautyq-variant-search-v2"),
          BeautyQSearchDeclarations.variants.document,
          contributions = Map.empty,
        )
      )
      val alternateBound = SearchCursorEnvelope.bind(first.plan, alternateView) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected alternate contract version to bind, got $error")
      }
      val cursor = SearchCursor.fromTransport(SearchCursorEnvelope.issue(alternateBound, "transport.contract-version").opaqueValue)
      val changedRaw = raw.copy(page = raw.page.copy(cursor = Some(cursor)))
      val changedRequest = validate(changedRaw)
      val changedIntent = parse(changedRequest)

      assert(
        compileErrors(changedRequest, changedIntent) ==
          Vector(BeautyQSearchPlanCompileError.CursorValidationFailed(SearchCursorError.PlanIdentityMismatch(
            expected = first.boundPlan.identityHash,
            actual = alternateBound.identityHash,
          )))
      )
    }

    "return a typed cursor error for a malformed transport token" in {
      val (request, intent) = build(query = Some("ресницы"), pageRequest = page.copy(cursor = Some(SearchCursor.fromTransport("malformed"))))
      assert(
        compileErrors(request, intent) ==
          Vector(BeautyQSearchPlanCompileError.CursorValidationFailed(SearchCursorError.MalformedEnvelope(1)))
      )
    }

    "preserve requested facet order" in {
      val (request, intent) = build(requestedFacets = Vector(FacetId("durationMinutes"), FacetId("service")))
      assert(compile(request, intent).plan.facets.map(_.id) == Vector(FacetId("durationMinutes"), FacetId("service")))
    }

    "omit unrequested facets" in {
      val (request, intent) = build(requestedFacets = Vector(FacetId("service")))
      assert(compile(request, intent).plan.facets.map(_.id) == Vector(FacetId("service")))
    }

    "compile with empty groups" in {
      val (request, intent) = build()
      assert(compile(request, intent).plan.groups.isEmpty)
    }

    "attach resolver suppressions to plan diagnostics" in {
      val (request, intent) = build(query = Some("маникюр"), filters = Vector(serviceFilter("manicure")))
      assert(compile(request, intent).plan.diagnostics.suppressedFilters.nonEmpty)
    }

    "produce a successful result that itself passes SearchPlan.validate" in {
      val (request, intent) = build(query = Some("маникюр"))
      assert(SearchPlan.validate(compile(request, intent).plan).isRight)
    }

    "return no later errors when geo resolution (Gate 1) fails" in {
      val (request, intent) = build(filters = Vector(distanceFilter("2000"), serviceFilter("manicure"), serviceFilter("pedicure")), userLocation = None)
      // The two service filters above would themselves conflict at Gate 2 (this Brick's own compilation
      // kernel) if ever reached; proving exactly one Gate 1 error and nothing else demonstrates Gate 1
      // short-circuits before Gate 2 ever runs.
      val errors = compileErrors(request, intent)
      assert(errors.size == 1)
      errors match {
        case Vector(BeautyQSearchPlanCompileError.PublicInputResolutionFailed(_)) => ()
        case other => fail(s"expected one public-input error, got: $other")
      }
    }

    "return no compiled result when plan compilation (Gate 2) fails" in {
      val (request, intent) = build(filters = Vector(serviceFilter("manicure"), serviceFilter("pedicure")))
      val errors = compileErrors(request, intent)
      assert(errors.forall {
        case BeautyQSearchPlanCompileError.PlanCompilationFailed(_) => true
        case _ => false
      })
    }
  }

  // ---- Modes ----

  "modes" should {
    "classify an empty request as DefaultBrowse" in {
      val (request, intent) = build()
      assert(compile(request, intent).mode == BeautyQSearchPlanMode.DefaultBrowse)
    }

    "classify a facet-only request as DefaultBrowse" in {
      val (request, intent) = build(requestedFacets = Vector(FacetId("service")))
      assert(compile(request, intent).mode == BeautyQSearchPlanMode.DefaultBrowse)
    }

    "classify a filter-only request as StructuredBrowse" in {
      val (request, intent) = build(filters = Vector(serviceFilter("manicure")))
      assert(compile(request, intent).mode == BeautyQSearchPlanMode.StructuredBrowse)
    }

    "classify a sort-only request as StructuredBrowse" in {
      val (request, intent) = build(sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc)))
      assert(compile(request, intent).mode == BeautyQSearchPlanMode.StructuredBrowse)
    }

    "classify a residual-text-only query as SemanticSearch" in {
      val (request, intent) = build(query = Some("something entirely unmatched"))
      assert(intent.residualText.isDefined)
      assert(compile(request, intent).mode == BeautyQSearchPlanMode.SemanticSearch)
    }

    "classify a NearUser-only request as SemanticSearch" in {
      val (request, intent) = build(query = Some("рядом"), userLocation = Some(berlin))
      assert(compile(request, intent).mode == BeautyQSearchPlanMode.SemanticSearch)
    }

    "classify a semantic-label-only intent as SemanticSearch" in {
      val (request, intent) = build(query = Some("ногти"))
      assert(intent.canonicalSemanticLabels.nonEmpty)
      assert(compile(request, intent).mode == BeautyQSearchPlanMode.SemanticSearch)
    }

    "attach exactly one default-browse notice for DefaultBrowse" in {
      val (request, intent) = build()
      val result = compile(request, intent)
      assert(result.plan.diagnostics.notices == Vector(BeautyQSearchPlanPolicy.defaultBrowseNotice))
    }

    "attach no default-browse notice for non-default modes" in {
      val (request, intent) = build(filters = Vector(serviceFilter("manicure")))
      val result = compile(request, intent)
      assert(!result.plan.diagnostics.notices.contains(BeautyQSearchPlanPolicy.defaultBrowseNotice))
    }

    "never produce a SystemDefault-provenance filter" in {
      val (request, intent) = build()
      assert(!compile(request, intent).plan.appliedFilters.exists(_.source.provenance == ConstraintProvenance.SystemDefault))
    }

    "never produce a fake sort for DefaultBrowse" in {
      val (request, intent) = build()
      assert(compile(request, intent).plan.sort.isEmpty)
    }
  }

  // Small local helpers to build stable business codes without importing model-layer construction
  // directly into this wiring-layer spec's fixture surface.
  private def ServiceCodeOf(value: String) = Fields.serviceCode.codec.decodeCanonical(value).getOrElse(fail(s"invalid fixture ServiceCode '$value'"))
  private def CategoryCodeOf(value: String) = Fields.categoryCode.codec.decodeCanonical(value).getOrElse(fail(s"invalid fixture CategoryCode '$value'"))
}
