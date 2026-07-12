package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

object PlanIdentityFixtures {
  final case class InventoryDocument(
    department: String,
    availabilityStart: Int,
    availabilityEnd: Int,
    quality: Int,
    supplier: String,
    headline: String,
  )

  final case class TrailDocument(
    trailType: String,
    difficulty: Int,
    coordinates: GeoPoint,
    region: String,
  )

  val alternateStringCodec: SearchValueCodec[String] = new SearchValueCodec[String] {
    def typeId: SearchValueTypeId = SearchValueTypeId("alternate-string")
    def encodeCanonical(value: String): String = value
    def decodeCanonical(value: String): Either[SearchValueDecodeError, String] = Right(value)
  }

  private def field[Document, Value](
    id: String,
    path: String,
    extract: Document => Option[Value],
  )(using codec: SearchValueCodec[Value]): SearchFieldKindBuilder[Document, Value] =
    computedField(id, path)(extract)

  val department =
    field[InventoryDocument, String]("department", "department", document => Some(document.department))
      .keyword
      .filterable(FilterOperator.Equal, FilterOperator.In)
      .facetable(FacetMode.Terms)

  val alternateDepartment =
    computedField[InventoryDocument, String]("department", "inventory.department.v2")(document => Some(document.department))
      .keyword.filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms)

  val departmentWithDifferentSemantic =
    field[InventoryDocument, String]("department", "department", document => Some(document.department))
      .keyword.filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms).withSemantic("alternate-department")

  val departmentWithDifferentCapabilities =
    field[InventoryDocument, String]("department", "department", document => Some(document.department)).keyword.filterable(FilterOperator.Equal)

  val departmentWithDifferentExtractor =
    field[InventoryDocument, String]("department", "department", document => document.department match {
      case value => Some(value)
    }).keyword.filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms)

  val departmentWithDifferentCodecType =
    computedField[InventoryDocument, String]("department", "department")(document => Some(document.department))(using alternateStringCodec)
      .keyword.filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms)

  val departmentWithDifferentId =
    field[InventoryDocument, String]("department-v2", "department", document => Some(document.department))
      .keyword.filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms)

  val availabilityStart =
    field[InventoryDocument, Int]("availabilityStart", "availabilityStart", document => Some(document.availabilityStart))
      .integer
      .filterable(FilterOperator.Range)
      .facetable(FacetMode.Range)

  val availabilityEnd =
    field[InventoryDocument, Int]("availabilityEnd", "availabilityEnd", document => Some(document.availabilityEnd))
      .integer
      .filterable(FilterOperator.Range)
      .facetable(FacetMode.Range)

  val quality =
    field[InventoryDocument, Int]("quality", "quality", document => Some(document.quality))
      .integer
      .filterable(FilterOperator.Range)
      .sortable(SortMode.Value)
      .facetable(FacetMode.Range)

  val supplier =
    field[InventoryDocument, String]("supplier", "supplier", document => Some(document.supplier))
      .keyword
      .facetable(FacetMode.Terms)
      .groupable(GroupMode.Terms)

  val headline = field[InventoryDocument, String]("headline", "headline", document => Some(document.headline)).text.searchable

  val trailType =
    field[TrailDocument, String]("trailType", "trailType", document => Some(document.trailType))
      .keyword
      .facetable(FacetMode.Terms)

  val difficulty = field[TrailDocument, Int]("difficulty", "difficulty", document => Some(document.difficulty)).integer

  val coordinates =
    field[TrailDocument, GeoPoint]("coordinates", "coordinates", document => Some(document.coordinates))
      .geoPoint
      .filterable(FilterOperator.GeoDistance)
      .sortable(SortMode.Distance)

  val region = field[TrailDocument, String]("region", "region", document => Some(document.region)).keyword.facetable(FacetMode.Terms).groupable(GroupMode.Terms)

  val pageA = PageSize.from(25) match {
    case Right(value) => value
    case Left(error)  => throw new AssertionError(s"fixture page size failed: $error")
  }

  val pageB = PageSize.from(20) match {
    case Right(value) => value
    case Left(error)  => throw new AssertionError(s"fixture page size failed: $error")
  }

  private val inventoryBuckets: Vector[FacetBucket[Int]] = Vector(
    FacetBucket.HalfOpen(FacetBucketId("q-low"), 0, 3),
    FacetBucket.UpperUnbounded(FacetBucketId("q-high"), 3),
  )

  private val availabilityBuckets: Vector[FacetBucket[Int]] = Vector(
    FacetBucket.HalfOpen(FacetBucketId("short"), 0, 10),
    FacetBucket.UpperUnbounded(FacetBucketId("long"), 10),
  )

  val planA: SearchPlan[InventoryDocument] = SearchPlan(
    residualText = Some("  In Stock\n"),
    appliedFilters = Vector(
      AppliedFilter(SourcedConstraint(PlannedConstraint.Terms(department, Set("wholesale", "retail")), ConstraintProvenance.ExplicitUi)),
      AppliedFilter(SourcedConstraint(PlannedConstraint.NumberRange(quality, RangeBounds(Bound.Inclusive(2), Bound.Exclusive(5))), ConstraintProvenance.ParsedHard)),
      AppliedFilter(SourcedConstraint(PlannedConstraint.IntervalOverlap(availabilityStart, availabilityEnd, RangeBounds(Bound.Inclusive(10), Bound.Exclusive(20))), ConstraintProvenance.SystemDefault)),
    ),
    softSignals = Vector.empty,
    sort = Vector(PlannedSort.FieldValue(quality, SortDirection.Desc)),
    page = PageRequest(None, pageA),
    facets = Vector(
      FacetRequest.Terms(FacetId("supplier-facet"), supplier, right(FacetSize.from(12)), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters),
      FacetRequest.NumberRange(FacetId("quality-facet"), quality, inventoryBuckets, FacetCountingPolicy.AllAppliedHardFilters),
      FacetRequest.IntervalOverlap(FacetId("availability-facet"), availabilityStart, availabilityEnd, availabilityBuckets, FacetCountingPolicy.AllAppliedHardFilters),
    ),
    groups = Vector(
      GroupRequest(
        GroupId("supplier-group"),
        supplier,
        right(GroupSize.from(5)),
        RepresentativeRequest.Fields(Vector(headline, quality)),
        Vector(GroupMetricRequest.BestScore(GroupMetricId("best"))),
        Vector(
          GroupOrder.Metric(GroupMetricId("best"), SortDirection.Desc),
          GroupOrder.MatchingDocumentCount(SortDirection.Desc),
          GroupOrder.Key(SortDirection.Asc),
        ),
        GroupPrecisionPolicy.RequireExact,
      )
    ),
    diagnostics = PlanDiagnostics(
      suppressedFilters = Vector(
        SuppressedFilter(
          SourcedConstraint(PlannedConstraint.Terms(department, Set("legacy")), ConstraintProvenance.FacetSelection(FacetSelectionId("selection-a"))),
          SuppressionReason.EquivalentDuplicate,
        )
      ),
      notices = Vector(PlanDiagnostic(PlanDiagnosticCode("fixture.notice"), Some("diagnostic only"))),
    ),
  )

  val planB: SearchPlan[TrailDocument] = SearchPlan(
    residualText = None,
    appliedFilters = Vector(
      AppliedFilter(SourcedConstraint(PlannedConstraint.GeoDistanceFilter(coordinates, GeoPoint(BigDecimal("50.1"), BigDecimal("30.2")), Distance(BigDecimal("2500"))), ConstraintProvenance.ExplicitUi))
    ),
    softSignals = Vector(PlannedSignal.GeoProximitySignal(coordinates, GeoPoint(BigDecimal("50.0"), BigDecimal("30.0")))),
    sort = Vector(PlannedSort.GeoDistance(coordinates, GeoPoint(BigDecimal("50.0"), BigDecimal("30.0")), SortDirection.Asc)),
    page = PageRequest(None, pageB),
    facets = Vector(FacetRequest.Terms(FacetId("trail-type-facet"), trailType, right(FacetSize.from(8)), TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters)),
    groups = Vector(
      GroupRequest(
        GroupId("region-group"),
        region,
        right(GroupSize.from(3)),
        RepresentativeRequest.IdentityOnly(),
        Vector(GroupMetricRequest.MinGeoDistance(GroupMetricId("nearest"), coordinates, GeoPoint(BigDecimal("50.0"), BigDecimal("30.0")))),
        Vector(GroupOrder.Metric(GroupMetricId("nearest"), SortDirection.Asc), GroupOrder.Key(SortDirection.Asc)),
        GroupPrecisionPolicy.AllowApproximate,
      )
    ),
    diagnostics = PlanDiagnostics.empty,
  )

  val viewA: CanonicalPlanView[InventoryDocument] = CanonicalPlanView(ContractFingerprint("inventory-contract-v1"))
  val viewB: CanonicalPlanView[TrailDocument] = CanonicalPlanView(ContractFingerprint("trail-contract-v1"))

  def right[A, B](value: Either[A, B]): B =
    value match {
      case Right(result) => result
      case Left(error)   => throw new AssertionError(s"fixture construction failed: $error")
    }
}

object PlanIdentityAssertions {
  def assertIdentityChanges(label: String, base: PlanIdentity, changed: PlanIdentity): Unit = {
    assert(base != changed, s"$label should change PlanIdentity")
    assert(PlanIdentityHash.compute(base) != PlanIdentityHash.compute(changed), s"$label should change PlanIdentityHash")
    (): Unit
  }

  def assertIdentityUnchanged(label: String, first: PlanIdentity, second: PlanIdentity): Unit = {
    assert(first == second, s"$label should preserve PlanIdentity")
    assert(PlanIdentityHash.compute(first) == PlanIdentityHash.compute(second), s"$label should preserve PlanIdentityHash")
    (): Unit
  }
}

final class CanonicalPlanViewSpec extends AnyWordSpec {
  import PlanIdentityFixtures.*
  import PlanIdentityAssertions.*

  private def first[A](values: Vector[A]): A =
    values match {
      case value +: _ => value
      case _          => fail("expected a non-empty identity vector")
    }

  private def planWithDepartmentField(field: SearchField[InventoryDocument, String]): SearchPlan[InventoryDocument] = {
    val applied = first(planA.appliedFilters)
    planA.copy(
      appliedFilters = planA.appliedFilters.updated(
        0,
        applied.copy(source = applied.source.copy(constraint = PlannedConstraint.Terms(field, Set("wholesale", "retail"))))
      )
    )
  }

  "CanonicalPlanView" should {
    "project two unrelated valid plan shapes into value-only identities" in {
      val inventory = viewA.identityOf(planA)
      val trail = viewB.identityOf(planB)

      assert(inventory.hardConstraints match {
        case Vector(_: CanonicalConstraint.Terms, _: CanonicalConstraint.NumberRange, _: CanonicalConstraint.IntervalOverlap) => true
        case _ => false
      })
      assert(inventory.facets match {
        case Vector(_: CanonicalFacetRequest.Terms, _: CanonicalFacetRequest.NumberRange, _: CanonicalFacetRequest.IntervalOverlap) => true
        case _ => false
      })
      assert(inventory.groups.size == 1)
      assert(trail.hardConstraints == Vector(CanonicalConstraint.GeoDistanceFilter(FieldId("coordinates"), CanonicalGeoPoint("50.1,30.2"), CanonicalDistance("2500"))))
      assert(trail.softSignals == Vector(CanonicalSignal.GeoProximity(FieldId("coordinates"), CanonicalGeoPoint("50,30"))))
      assert(trail.sort == Vector(CanonicalSort.GeoDistance(FieldId("coordinates"), CanonicalGeoPoint("50,30"), SortDirection.Asc)))
    }

    "sort Terms encoded values while preserving every other declared vector order" in {
      val identity = viewA.identityOf(planA)
      first(identity.hardConstraints) match {
        case CanonicalConstraint.Terms(_, values) => assert(values == Vector("retail", "wholesale"))
        case other                                => fail(s"expected terms constraint, got $other")
      }
      assert(identity.hardConstraints match {
        case Vector(_: CanonicalConstraint.Terms, _: CanonicalConstraint.NumberRange, _: CanonicalConstraint.IntervalOverlap) => true
        case _ => false
      })
      assert(identity.facets.map(_.id.value) == Vector("supplier-facet", "quality-facet", "availability-facet"))
      val group = first(identity.groups)
      assert(group.metrics.map(_.id.value) == Vector("best"))
      assert(group.order match {
        case Vector(_: CanonicalGroupOrder.Metric, _: CanonicalGroupOrder.MatchingDocumentCount, _: CanonicalGroupOrder.Key) => true
        case _ => false
      })
    }

    "preserve query presence and exact trusted text without normalizing it" in {
      val absent = viewB.identityOf(planB.copy(residualText = None))
      val present = viewB.identityOf(planB.copy(residualText = Some("  Trail\n")))
      assert(absent.normalizedQuery.isEmpty)
      assert(present.normalizedQuery == Some(NormalizedQueryText("  Trail\n")))
    }

    "exclude field path independently" in {
      assertIdentityUnchanged("field path", viewA.identityOf(planA), viewA.identityOf(planWithDepartmentField(alternateDepartment)))
    }

    "exclude field semantic independently" in {
      assertIdentityUnchanged("field semantic", viewA.identityOf(planA), viewA.identityOf(planWithDepartmentField(departmentWithDifferentSemantic)))
    }

    "exclude field capabilities independently" in {
      assertIdentityUnchanged("field capabilities", viewA.identityOf(planA), viewA.identityOf(planWithDepartmentField(departmentWithDifferentCapabilities)))
    }

    "exclude extractor function identity independently" in {
      assertIdentityUnchanged("field extractor", viewA.identityOf(planA), viewA.identityOf(planWithDepartmentField(departmentWithDifferentExtractor)))
    }

    "exclude SearchValueTypeId independently when canonical values remain equal" in {
      assertIdentityUnchanged("field codec type ID", viewA.identityOf(planA), viewA.identityOf(planWithDepartmentField(departmentWithDifferentCodecType)))
    }

    "include FieldId independently of path and other field metadata" in {
      assertIdentityChanges("field ID", viewA.identityOf(planA), viewA.identityOf(planWithDepartmentField(departmentWithDifferentId)))
    }

    "compile only valid plans and return the original SearchPlan errors" in {
      val invalid = planB.copy(sort = Vector(PlannedSort.FieldValue(trailType, SortDirection.Asc)))
      PlanIdentityCompiler.compile(invalid, viewB) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(SearchPlanError.InvalidSort(0, PlanConstraintError.UnsupportedSortMode(trailType.id, trailType.kind, SortMode.Value))))
        case Right(identity) => fail(s"expected invalid plan, got $identity")
      }
    }
  }
}
