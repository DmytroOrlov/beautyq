package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral inventory fixture proving the generic SearchPlan compilation kernel - constraint resolution
  * plus SearchPlan assembly/validation - is independent of any one domain's document shape, plan mode,
  * or public-facing naming.
  */
final class SearchPlanCompilationKernelSpec extends AnyWordSpec {

  private final case class InventoryDocument(id: UUID, department: String, supplier: String, stockMin: Int)

  private enum InputSource(val stableId: String) extends ConstraintSourceIdentity {
    case Higher extends InputSource("higher")
    case Lower  extends InputSource("lower")
  }

  private val precedence = ConstraintPrecedence.unsafeAbove(InputSource.Higher, InputSource.Lower)

  private val department = field[InventoryDocument, String]("department", _.department).keyword.filterable(FilterOperator.Equal, FilterOperator.In).sortable(SortMode.Value).facetable(FacetMode.Terms)
  private val stockMin = field[InventoryDocument, Int]("stockMin", _.stockMin).integer.filterable(FilterOperator.Range).facetable(FacetMode.Range)
  private val notFilterableSupplier = field[InventoryDocument, String]("supplier2", _.supplier).keyword

  private val page = PageRequest(None, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))

  private def input(
    higherPriority: Vector[SourcedConstraint[InventoryDocument]] = Vector.empty,
    lowerPriority: Vector[SourcedConstraint[InventoryDocument]] = Vector.empty,
    sort: Vector[PlannedSort[InventoryDocument]] = Vector.empty,
    facets: Vector[FacetRequest[InventoryDocument]] = Vector.empty,
  ): SearchPlanCompilationInput[InventoryDocument] =
    SearchPlanCompilationInput(
      precedence.tiers {
        case InputSource.Higher => higherPriority
        case InputSource.Lower  => lowerPriority
      },
      residualText = None,
      softSignals = Vector.empty,
      sort = sort,
      page = page,
      facets = facets,
      groups = Vector.empty,
    )

  "SearchPlanCompilationKernel.compile" should {
    "compile a neutral domain's constraints into a validated SearchPlan successfully" in {
      val higher = SourcedConstraint(PlannedConstraint.Terms(department, Set("tools")), ConstraintProvenance.ExplicitUi)
      SearchPlanCompilationKernel.compile(input(higherPriority = Vector(higher))) match {
        case Right(plan) => assert(plan.appliedFilters == Vector(AppliedFilter(higher)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "attach the resolver's suppressions to the compiled plan's diagnostics" in {
      val higher = SourcedConstraint(PlannedConstraint.Terms(department, Set("tools")), ConstraintProvenance.ExplicitUi)
      val lowerDuplicate = SourcedConstraint(PlannedConstraint.Terms(department, Set("tools")), ConstraintProvenance.ParsedHard)
      SearchPlanCompilationKernel.compile(input(higherPriority = Vector(higher), lowerPriority = Vector(lowerDuplicate))) match {
        case Right(plan) => assert(plan.diagnostics.suppressedFilters == Vector(SuppressedFilter(lowerDuplicate, SuppressionReason.EquivalentDuplicate)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "preserve caller-supplied notices alongside resolver suppressions" in {
      val notice = PlanDiagnostic(PlanDiagnosticCode("caller-notice"), None)
      SearchPlanCompilationKernel.compile(input(), Vector(notice)) match {
        case Right(plan) => assert(plan.diagnostics.notices == Vector(notice))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "preserve sort, facet and group input order in the compiled plan" in {
      val sort = Vector(PlannedSort.FieldValue(department, SortDirection.Asc))
      val facets = Vector(
        FacetRequest.Terms(FacetId("department"), department, FacetSize.from(5).getOrElse(fail("expected a valid FacetSize")), TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters),
        FacetRequest.NumberRange(FacetId("stock"), stockMin, Vector(FacetBucket.UpperUnbounded(FacetBucketId("any"), 0)), FacetCountingPolicy.AllAppliedHardFilters),
      )
      SearchPlanCompilationKernel.compile(input(sort = sort, facets = facets)) match {
        case Right(plan) =>
          assert(plan.sort == sort)
          assert(plan.facets == facets)
          assert(plan.groups.isEmpty)
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "gate on constraint resolution before SearchPlan validation: a resolver conflict is reported without ever reaching plan validation" in {
      val first = SourcedConstraint(PlannedConstraint.Terms(department, Set("tools")), ConstraintProvenance.ExplicitUi)
      val conflicting = SourcedConstraint(PlannedConstraint.Terms(department, Set("parts")), ConstraintProvenance.ExplicitUi)
      // notFilterableSupplier would also fail SearchPlan.validate if reached; the conflict must be
      // reported instead of an InvalidSearchPlan error, proving the resolution gate runs first.
      val alsoInvalidSort = Vector(PlannedSort.FieldValue(notFilterableSupplier, SortDirection.Asc))
      SearchPlanCompilationKernel.compile(input(higherPriority = Vector(first, conflicting), sort = alsoInvalidSort)) match {
        case Left(errors) =>
          assert(errors.toVector.size == 1)
          errors.toVector.headOption.getOrElse(fail("expected one compilation error")) match {
            case SearchPlanCompilationError.ConstraintResolutionFailed(_) => succeed
            case other                                                     => fail(s"expected ConstraintResolutionFailed, got $other")
          }
        case Right(value) => fail(s"expected failure, got $value")
      }
    }

    "wrap every resolver conflict as ConstraintResolutionFailed" in {
      val first = SourcedConstraint(PlannedConstraint.Terms(department, Set("tools")), ConstraintProvenance.ExplicitUi)
      val second = SourcedConstraint(PlannedConstraint.Terms(department, Set("parts")), ConstraintProvenance.ExplicitUi)
      val third = SourcedConstraint(PlannedConstraint.Terms(department, Set("garden")), ConstraintProvenance.ExplicitUi)
      SearchPlanCompilationKernel.compile(input(higherPriority = Vector(first, second, third))) match {
        case Left(errors) =>
          assert(errors.toVector.size == 2)
          assert(errors.toVector.forall {
            case SearchPlanCompilationError.ConstraintResolutionFailed(_) => true
            case _ => false
          })
        case Right(value) => fail(s"expected failures, got $value")
      }
    }

    "wrap every SearchPlan validation error as InvalidSearchPlan, once resolution itself succeeds" in {
      val unsupportedSort = Vector(PlannedSort.FieldValue(notFilterableSupplier, SortDirection.Asc))
      SearchPlanCompilationKernel.compile(input(sort = unsupportedSort)) match {
        case Left(errors) =>
          assert(errors.toVector.size == 1)
          errors.toVector.headOption.getOrElse(fail("expected one compilation error")) match {
            case SearchPlanCompilationError.InvalidSearchPlan(SearchPlanError.InvalidSort(0, _)) => succeed
            case other                                                                            => fail(s"expected InvalidSearchPlan(InvalidSort), got $other")
          }
        case Right(value) => fail(s"expected failure, got $value")
      }
    }

    "never return an invalid plan: a SearchPlan.validate failure is always surfaced as Left" in {
      val duplicateFacetId = Vector(
        FacetRequest.Terms(FacetId("dup"), department, FacetSize.from(5).getOrElse(fail("expected a valid FacetSize")), TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters),
        FacetRequest.NumberRange(FacetId("dup"), stockMin, Vector(FacetBucket.UpperUnbounded(FacetBucketId("any"), 0)), FacetCountingPolicy.AllAppliedHardFilters),
      )
      assert(SearchPlanCompilationKernel.compile(input(facets = duplicateFacetId)).isLeft)
    }

    "accept groups and facets purely as caller-owned inputs, with no kernel-side construction" in {
      SearchPlanCompilationKernel.compile(input()) match {
        case Right(plan) =>
          assert(plan.groups.isEmpty)
          assert(plan.facets.isEmpty)
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }
  }
}
