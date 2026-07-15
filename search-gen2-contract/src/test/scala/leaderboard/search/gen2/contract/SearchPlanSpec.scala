package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral two-shape calibration for the complete Brick 4B [[SearchPlan]] contract. CatalogDocument (a
  * commerce/catalog shape) exercises Terms/NumberRange/IntervalOverlap facets, a Terms group with
  * multi-field representative projection and best-score ordering, and non-empty diagnostics.
  * VenueDocument (a venue/event shape, a different document phantom type) exercises the geo
  * signal/filter/sort triad, a geo-ordered group with an identity-only representative, and cursor
  * carriage. Neither reuses BeautyQ names such as service, category-as-BeautyQ-means-it, master,
  * priceFrom or priceTo.
  */
final class SearchPlanSpec extends AnyWordSpec {

  private final case class CatalogDocument(
    id: UUID,
    department: String,
    seller: String,
    title: String,
    stockMin: Int,
    stockMax: Int,
    rating: Int,
  )

  private final case class VenueDocument(
    id: UUID,
    category: String,
    location: GeoPoint,
  )

  private val department =
    field[CatalogDocument, String]("department", _.department).keyword
      .filterable(FilterOperator.Equal)
      .facetable(FacetMode.Terms)
      .groupable(GroupMode.Terms)

  private val seller = field[CatalogDocument, String]("seller", _.seller).keyword
  private val title  = field[CatalogDocument, String]("title", _.title).text

  private val stockMin =
    field[CatalogDocument, Int]("stockMin", _.stockMin).integer.filterable(FilterOperator.Range).facetable(FacetMode.Range)

  private val stockMax =
    field[CatalogDocument, Int]("stockMax", _.stockMax).integer.filterable(FilterOperator.Range).facetable(FacetMode.Range)

  private val rating =
    field[CatalogDocument, Int]("rating", _.rating).integer
      .filterable(FilterOperator.Range)
      .facetable(FacetMode.Range)
      .sortable(SortMode.Value)

  private val category =
    field[VenueDocument, String]("category", _.category).keyword
      .filterable(FilterOperator.Equal)
      .facetable(FacetMode.Terms)
      .groupable(GroupMode.Terms)

  private val location =
    field[VenueDocument, GeoPoint]("location", _.location).geoPoint.filterable(FilterOperator.GeoDistance).sortable(SortMode.Distance)

  private val origin = GeoPoint(BigDecimal("1.5"), BigDecimal("2.5"))

  private val validPageSize  = PageSize.from(20).getOrElse(fail("expected a valid PageSize"))
  private val validFacetSize = FacetSize.from(10).getOrElse(fail("expected a valid FacetSize"))
  private val validGroupSize = GroupSize.from(5).getOrElse(fail("expected a valid GroupSize"))

  private val bestScoreMetricId   = GroupMetricId("bestScore")
  private val minDistanceMetricId = GroupMetricId("minDistance")

  private val explicitDepartmentFilter =
    AppliedFilter(SourcedConstraint(PlannedConstraint.Terms(department, Set("electronics")), ConstraintProvenance.ExplicitUi))

  private val ratingSort = PlannedSort.FieldValue(rating, SortDirection.Desc)

  private val stockBuckets: Vector[FacetBucket[Int]] =
    Vector(FacetBucket.HalfOpen(FacetBucketId("low"), 0, 50), FacetBucket.UpperUnbounded(FacetBucketId("high"), 50))

  private val ratingBuckets: Vector[FacetBucket[Int]] =
    Vector(FacetBucket.HalfOpen(FacetBucketId("poor"), 1, 3), FacetBucket.UpperUnbounded(FacetBucketId("good"), 3))

  private val departmentTermsFacet =
    FacetRequest.Terms(FacetId("department"), department, validFacetSize, TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters)

  private val ratingRangeFacet =
    FacetRequest.NumberRange(FacetId("rating"), rating, ratingBuckets, FacetCountingPolicy.AllAppliedHardFilters)

  private val stockIntervalFacet =
    FacetRequest.IntervalOverlap(FacetId("stock"), stockMin, stockMax, stockBuckets, FacetCountingPolicy.AllAppliedHardFilters)

  private val sellersGroup =
    GroupRequest(
      GroupId("sellers"),
      department,
      validGroupSize,
      RepresentativeRequest.Fields(Vector(seller, title)),
      Vector(GroupMetricRequest.BestScore(bestScoreMetricId)),
      Vector(GroupOrder.Metric(bestScoreMetricId, SortDirection.Desc), GroupOrder.Key(SortDirection.Asc)),
      GroupPrecisionPolicy.RequireExact,
    )

  private val suppressedClearanceFilter =
    SuppressedFilter(
      SourcedConstraint(PlannedConstraint.Terms(department, Set("clearance")), ConstraintProvenance.ParsedHard),
      SuppressionReason.OverriddenByHigherPrecedence,
    )

  private val lowStockNotice = PlanDiagnostic(PlanDiagnosticCode("low-stock"), Some("only 2 sellers matched"))

  private def basePlan(
    residualText: Option[String] = Some("wireless headphones"),
    appliedFilters: Vector[AppliedFilter[CatalogDocument]] = Vector(explicitDepartmentFilter),
    softSignals: Vector[PlannedSignal[CatalogDocument]] = Vector.empty,
    sort: Vector[PlannedSort[CatalogDocument]] = Vector(ratingSort),
    page: PageRequest = PageRequest(None, validPageSize),
    facets: Vector[FacetRequest[CatalogDocument]] = Vector(departmentTermsFacet, ratingRangeFacet, stockIntervalFacet),
    groups: Vector[GroupRequest[CatalogDocument, ?]] = Vector(sellersGroup),
    diagnostics: PlanDiagnostics[CatalogDocument] = PlanDiagnostics(Vector(suppressedClearanceFilter), Vector(lowStockNotice)),
  ): SearchPlan[CatalogDocument] =
    SearchPlan(residualText, appliedFilters, softSignals, sort, page, facets, groups, diagnostics)

  private val venueCursor = SearchCursor.fromTransport("venue-cursor-token")

  private val venuePlan: SearchPlan[VenueDocument] =
    SearchPlan(
      residualText = None,
      appliedFilters = Vector(
        AppliedFilter(
          SourcedConstraint(PlannedConstraint.GeoDistanceFilter(location, origin, Distance(2000)), ConstraintProvenance.FacetSelection(FacetSelectionId("sel-1")))
        )
      ),
      softSignals = Vector(PlannedSignal.GeoProximitySignal(location, origin)),
      sort = Vector(PlannedSort.GeoDistance(location, origin, SortDirection.Asc)),
      page = PageRequest(Some(venueCursor), validPageSize),
      facets = Vector(FacetRequest.Terms(FacetId("category"), category, validFacetSize, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)),
      groups = Vector(
        GroupRequest(
          GroupId("venues"),
          category,
          validGroupSize,
          RepresentativeRequest.IdentityOnly(),
          Vector(GroupMetricRequest.MinGeoDistance(minDistanceMetricId, location, origin)),
          Vector(GroupOrder.Metric(minDistanceMetricId, SortDirection.Asc), GroupOrder.Key(SortDirection.Asc)),
          GroupPrecisionPolicy.AllowApproximate,
        )
      ),
      diagnostics = PlanDiagnostics.empty[VenueDocument],
    )

  "SearchPlan.hardConstraints" should {
    "derive exactly the constraints of the applied filters, in the same order" in {
      val secondFilter =
        AppliedFilter(SourcedConstraint(PlannedConstraint.NumberRange(rating, RangeBounds(Bound.Inclusive(3), Bound.Unbounded)), ConstraintProvenance.ParsedHard))
      val plan = basePlan(appliedFilters = Vector(explicitDepartmentFilter, secondFilter))
      assert(plan.hardConstraints == Vector(explicitDepartmentFilter.source.constraint, secondFilter.source.constraint))
    }
  }

  "SearchPlan construction" should {
    "expose no independent hardConstraints field to copy, since it is always derived from appliedFilters" in {
      assertDoesNotCompile("""basePlan().copy(hardConstraints = Vector.empty)""")
    }
  }

  "ConstraintProvenance" should {
    "preserve ExplicitUi provenance on a SourcedConstraint" in {
      assert(explicitDepartmentFilter.source.provenance == ConstraintProvenance.ExplicitUi)
    }

    "preserve FacetSelection provenance with its stable selection ID" in {
      ConstraintProvenance.FacetSelection(FacetSelectionId("sel-1")) match {
        case ConstraintProvenance.FacetSelection(id) => assert(id == FacetSelectionId("sel-1"))
        case other                                    => fail(s"expected FacetSelection, got: $other")
      }
    }

    "keep ParsedHard, ParsedSoft and SystemDefault as distinct values" in {
      val values: Set[ConstraintProvenance] = Set(ConstraintProvenance.ParsedHard, ConstraintProvenance.ParsedSoft, ConstraintProvenance.SystemDefault)
      assert(values.size == 3)
    }
  }

  "SuppressedFilter" should {
    "retain its source constraint, provenance and reason" in {
      val source     = SourcedConstraint(PlannedConstraint.Terms(department, Set("clearance")), ConstraintProvenance.ParsedHard)
      val suppressed = SuppressedFilter(source, SuppressionReason.OverriddenByHigherPrecedence)
      assert(suppressed.source == source)
      assert(suppressed.source.constraint == source.constraint)
      assert(suppressed.source.provenance == ConstraintProvenance.ParsedHard)
      assert(suppressed.reason == SuppressionReason.OverriddenByHigherPrecedence)
    }
  }

  "SearchPlan.withoutCursor" should {
    "clear only the cursor, preserving every other field exactly" in {
      val cursor = SearchCursor.fromTransport("token")
      val plan   = basePlan(page = PageRequest(Some(cursor), validPageSize))
      val result = plan.withoutCursor

      assert(result.page == PageRequest(None, validPageSize))
      assert(result.residualText == plan.residualText)
      assert(result.appliedFilters == plan.appliedFilters)
      assert(result.softSignals == plan.softSignals)
      assert(result.sort == plan.sort)
      assert(result.facets == plan.facets)
      assert(result.groups == plan.groups)
      assert(result.diagnostics == plan.diagnostics)
    }
  }

  "SearchPlan.validate index tracking" should {
    "retain the applied-filter index on a constraint validation error" in {
      val invalidFilter = AppliedFilter(SourcedConstraint(PlannedConstraint.Terms(title, Set("x")), ConstraintProvenance.ExplicitUi))
      val plan           = basePlan(appliedFilters = Vector(explicitDepartmentFilter, invalidFilter))
      SearchPlan.validate(plan) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                SearchPlanError.InvalidConstraint(1, PlanConstraintError.UnsupportedFilterOperator(title.id, SearchFieldKind.Text, Vector(FilterOperator.Equal, FilterOperator.In)))
              )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "retain the sort index on a sort validation error" in {
      val invalidSort = PlannedSort.FieldValue(stockMin, SortDirection.Asc)
      val plan         = basePlan(sort = Vector(ratingSort, invalidSort))
      SearchPlan.validate(plan) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(SearchPlanError.InvalidSort(1, PlanConstraintError.UnsupportedSortMode(stockMin.id, SearchFieldKind.Integer, SortMode.Value))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "retain the facet index on a facet validation error" in {
      val invalidFacet = FacetRequest.Terms(FacetId("seller"), seller, validFacetSize, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val plan          = basePlan(facets = Vector(departmentTermsFacet, invalidFacet))
      SearchPlan.validate(plan) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(SearchPlanError.InvalidFacet(1, FacetRequestError.UnsupportedFacetMode(FacetId("seller"), seller.id, SearchFieldKind.Keyword, FacetMode.Terms)))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "retain the group index on a group validation error" in {
      val invalidGroup =
        GroupRequest(
          GroupId("sellersInvalid"),
          seller,
          validGroupSize,
          RepresentativeRequest.IdentityOnly(),
          Vector.empty,
          Vector(GroupOrder.Key(SortDirection.Asc)),
          GroupPrecisionPolicy.RequireExact,
        )
      val plan = basePlan(groups = Vector(sellersGroup, invalidGroup))
      SearchPlan.validate(plan) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(SearchPlanError.InvalidGroup(1, GroupRequestError.UnsupportedGroupMode(GroupId("sellersInvalid"), seller.id, SearchFieldKind.Keyword)))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "SearchPlan.validate duplicate IDs" should {
    "report a duplicated FacetId exactly once" in {
      val duplicateRatingFacet = ratingRangeFacet.copy(id = departmentTermsFacet.id)
      val plan                 = basePlan(facets = Vector(departmentTermsFacet, duplicateRatingFacet, stockIntervalFacet))
      SearchPlan.validate(plan) match {
        case Left(errors) => assert(errors.toVector == Vector(SearchPlanError.DuplicateFacetId(departmentTermsFacet.id, firstIndex = 0, duplicateIndex = 1)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "report every later duplicated FacetId in vector encounter order with both indexes" in {
      val zebraFacet1 = departmentTermsFacet.copy(id = FacetId("zebra"))
      val zebraFacet2 = ratingRangeFacet.copy(id = FacetId("zebra"))
      val alphaFacet1 = stockIntervalFacet.copy(id = FacetId("alpha"))
      val alphaFacet2 = departmentTermsFacet.copy(id = FacetId("alpha"))
      val facets: Vector[FacetRequest[CatalogDocument]] = Vector(zebraFacet1, zebraFacet2, alphaFacet1, alphaFacet2)
      val plan = basePlan(facets = facets)
      SearchPlan.validate(plan) match {
        case Left(errors) =>
          assert(
            errors.toVector == Vector(
              SearchPlanError.DuplicateFacetId(FacetId("zebra"), firstIndex = 0, duplicateIndex = 1),
              SearchPlanError.DuplicateFacetId(FacetId("alpha"), firstIndex = 2, duplicateIndex = 3),
            )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "report a duplicated GroupId exactly once" in {
      val plan = basePlan(groups = Vector(sellersGroup, sellersGroup))
      SearchPlan.validate(plan) match {
        case Left(errors) => assert(errors.toVector == Vector(SearchPlanError.DuplicateGroupId(sellersGroup.id)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "report multiple duplicated GroupIds sorted by canonical GroupId value, independent of vector position" in {
      val zebraGroup1 = sellersGroup.copy(id = GroupId("zebra"))
      val zebraGroup2 = sellersGroup.copy(id = GroupId("zebra"))
      val alphaGroup1 = sellersGroup.copy(id = GroupId("alpha"))
      val alphaGroup2 = sellersGroup.copy(id = GroupId("alpha"))
      val plan         = basePlan(groups = Vector(zebraGroup1, zebraGroup2, alphaGroup1, alphaGroup2))
      SearchPlan.validate(plan) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(SearchPlanError.DuplicateGroupId(GroupId("alpha")), SearchPlanError.DuplicateGroupId(GroupId("zebra"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "SearchPlan.validate global error order" should {
    "accumulate constraint, sort, facet, duplicate-facet, group and duplicate-group errors in exactly that section order" in {
      val invalidFilter         = AppliedFilter(SourcedConstraint(PlannedConstraint.Terms(title, Set("x")), ConstraintProvenance.ExplicitUi))
      val invalidSort           = PlannedSort.FieldValue(stockMin, SortDirection.Asc)
      val invalidFacet          = FacetRequest.Terms(FacetId("seller"), seller, validFacetSize, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val duplicateRatingFacet  = ratingRangeFacet.copy(id = departmentTermsFacet.id)
      val invalidGroup =
        GroupRequest(
          GroupId("invalidSellers"),
          seller,
          validGroupSize,
          RepresentativeRequest.IdentityOnly(),
          Vector.empty,
          Vector(GroupOrder.Key(SortDirection.Asc)),
          GroupPrecisionPolicy.RequireExact,
        )

      val plan =
        basePlan(
          appliedFilters = Vector(invalidFilter),
          sort = Vector(invalidSort),
          facets = Vector(departmentTermsFacet, invalidFacet, duplicateRatingFacet),
          groups = Vector(sellersGroup, sellersGroup, invalidGroup),
        )

      SearchPlan.validate(plan) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                SearchPlanError.InvalidConstraint(0, PlanConstraintError.UnsupportedFilterOperator(title.id, SearchFieldKind.Text, Vector(FilterOperator.Equal, FilterOperator.In))),
                SearchPlanError.InvalidSort(0, PlanConstraintError.UnsupportedSortMode(stockMin.id, SearchFieldKind.Integer, SortMode.Value)),
                SearchPlanError.InvalidFacet(1, FacetRequestError.UnsupportedFacetMode(FacetId("seller"), seller.id, SearchFieldKind.Keyword, FacetMode.Terms)),
                SearchPlanError.DuplicateFacetId(departmentTermsFacet.id, firstIndex = 0, duplicateIndex = 2),
                SearchPlanError.InvalidGroup(2, GroupRequestError.UnsupportedGroupMode(GroupId("invalidSellers"), seller.id, SearchFieldKind.Keyword)),
                SearchPlanError.DuplicateGroupId(sellersGroup.id),
              )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "SearchPlan.validate on a fully valid plan" should {
    "return the exact original plan unchanged" in {
      val plan = basePlan()
      assert(SearchPlan.validate(plan) == Right(plan))
    }

    "ignore diagnostics content entirely, even when it carries a suppressed filter built from an invalid constraint" in {
      val plan =
        basePlan(diagnostics =
          PlanDiagnostics(
            Vector(SuppressedFilter(SourcedConstraint(PlannedConstraint.Terms(title, Set("bogus")), ConstraintProvenance.ParsedHard), SuppressionReason.EquivalentDuplicate)),
            Vector(PlanDiagnostic(PlanDiagnosticCode("anything"), None)),
          )
        )
      assert(SearchPlan.validate(plan) == Right(plan))
    }
  }

  "SearchPlanTrace.render" should {
    "render the exact golden trace for the CatalogDocument (Shape A) plan" in {
      val trace = SearchPlanTrace.render(basePlan())
      assert(
        trace ==
          Vector(
            "plan.residual-text=\"wireless headphones\"",
            "plan.applied-filter[0] provenance=ExplicitUi constraint.terms field=department:string values=[electronics]",
            "plan.sort[0] sort.field-value field=rating:int direction=Desc",
            "plan.page cursor=absent size=20",
            "plan.facet[0] facet.terms id=department field=department:string size=10 order=CountDescThenKeyAsc counting=AllAppliedHardFilters",
            "plan.facet[1] facet.number-range id=rating field=rating:int counting=AllAppliedHardFilters buckets=[poor=[inclusive(1), exclusive(3)], good=[inclusive(3), unbounded]]",
            "plan.facet[2] facet.interval-overlap id=stock from=stockMin:int to=stockMax:int counting=AllAppliedHardFilters buckets=[low=[inclusive(0), exclusive(50)], high=[inclusive(50), unbounded]]",
            "plan.group[0] group id=sellers key=department:string size=5 representative=fields[seller:string, title:string] metrics=[best-score(bestScore)] order=[metric(bestScore Desc), key(Asc)] precision=RequireExact",
            "plan.diagnostics.suppressed-filter[0] provenance=ParsedHard constraint.terms field=department:string values=[clearance] reason=OverriddenByHigherPrecedence",
            "plan.diagnostics.notice[0] code=low-stock detail=\"only 2 sellers matched\"",
          ).mkString("\n")
      )
    }

    "render the exact golden trace for the VenueDocument (Shape B) plan" in {
      val trace = SearchPlanTrace.render(venuePlan)
      assert(
        trace ==
          Vector(
            "plan.residual-text=absent",
            "plan.applied-filter[0] provenance=FacetSelection(sel-1) constraint.geo-distance-filter field=location:geo-point origin=1.5,2.5 radius=2000",
            "plan.soft-signal[0] signal.geo-proximity field=location:geo-point origin=1.5,2.5",
            "plan.sort[0] sort.geo-distance field=location:geo-point origin=1.5,2.5 direction=Asc",
            "plan.page cursor=present size=20",
            "plan.facet[0] facet.terms id=category field=category:string size=10 order=KeyAsc counting=AllAppliedHardFilters",
            "plan.group[0] group id=venues key=category:string size=5 representative=identity-only metrics=[min-geo-distance(minDistance field=location:geo-point origin=1.5,2.5)] order=[metric(minDistance Asc), key(Asc)] precision=AllowApproximate",
          ).mkString("\n")
      )
    }

    "never expose cursor contents in the rendered plan, only presence" in {
      val trace = SearchPlanTrace.render(venuePlan)
      assert(!trace.contains("venue-cursor-token"))
      assert(trace.contains("cursor=present"))
    }

    "never leak function/object identity (no '@'-style default toString) into the rendered plan" in {
      val catalogTrace = SearchPlanTrace.render(basePlan())
      val venueTrace    = SearchPlanTrace.render(venuePlan)
      assert(!catalogTrace.contains("@"))
      assert(!venueTrace.contains("@"))
    }
  }
}
