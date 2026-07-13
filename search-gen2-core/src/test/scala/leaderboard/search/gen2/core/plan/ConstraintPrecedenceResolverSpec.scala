package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral fixture (department/supplier/warehouse/stockMin) proving precedence resolution, deduplication
  * and conflict detection are independent of any one domain's document shape or field vocabulary.
  */
final class ConstraintPrecedenceResolverSpec extends AnyWordSpec {

  private final case class InventoryDocument(id: UUID, department: String, supplier: String, warehouse: String, stockMin: Int)

  private val department = field[InventoryDocument, String]("department", _.department).keyword.filterable(FilterOperator.Equal, FilterOperator.In)
  private val supplier = field[InventoryDocument, String]("supplier", _.supplier).keyword.filterable(FilterOperator.Equal, FilterOperator.In)
  private val warehouse = field[InventoryDocument, String]("warehouse", _.warehouse).keyword.filterable(FilterOperator.Equal, FilterOperator.In)
  private val stockMin = field[InventoryDocument, Int]("stockMin", _.stockMin).integer.filterable(FilterOperator.Range)

  private val higherProvenance = ConstraintProvenance.ExplicitUi
  private val lowerProvenance = ConstraintProvenance.ParsedHard

  private def terms(searchField: SearchField[InventoryDocument, String], value: String, provenance: ConstraintProvenance): SourcedConstraint[InventoryDocument] =
    SourcedConstraint(PlannedConstraint.Terms(searchField, Set(value)), provenance)

  private def canonical(sourced: SourcedConstraint[InventoryDocument]): CanonicalConstraint = CanonicalConstraintView(sourced.constraint)

  "ConstraintPrecedenceResolver.resolve" should {

    "apply one higher-priority constraint with no lower-priority input" in {
      val higher = terms(department, "shoes", higherProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(higher), Vector.empty) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(higher)))
          assert(resolution.suppressedFilters.isEmpty)
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "suppress an equivalent higher-priority duplicate as EquivalentDuplicate, keeping the first" in {
      val first = terms(department, "shoes", higherProvenance)
      val duplicate = terms(department, "shoes", higherProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(first, duplicate), Vector.empty) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(first)))
          assert(resolution.suppressedFilters == Vector(SuppressedFilter(duplicate, SuppressionReason.EquivalentDuplicate)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "report ConflictingHigherPriority for a non-equivalent higher-priority duplicate in the same slot" in {
      val first = terms(department, "shoes", higherProvenance)
      val second = terms(department, "apparel", higherProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(first, second), Vector.empty) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(ConstraintResolutionError.ConflictingHigherPriority(ConstraintSlot.Terms(FieldId("department")), 0, 1, canonical(first), canonical(second)))
          )
        case Right(value) => fail(s"expected conflict, got $value")
      }
    }

    "accumulate multiple higher-priority conflicts, each against the slot's first occurrence" in {
      val first = terms(department, "shoes", higherProvenance)
      val second = terms(department, "apparel", higherProvenance)
      val third = terms(department, "electronics", higherProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(first, second, third), Vector.empty) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                ConstraintResolutionError.ConflictingHigherPriority(ConstraintSlot.Terms(FieldId("department")), 0, 1, canonical(first), canonical(second)),
                ConstraintResolutionError.ConflictingHigherPriority(ConstraintSlot.Terms(FieldId("department")), 0, 2, canonical(first), canonical(third)),
              )
          )
        case Right(value) => fail(s"expected conflicts, got $value")
      }
    }

    "apply one unique lower-priority constraint after a different-slot higher-priority constraint" in {
      val higher = terms(department, "shoes", higherProvenance)
      val lower = terms(supplier, "acme", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(higher), Vector(lower)) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(higher), AppliedFilter(lower)))
          assert(resolution.suppressedFilters.isEmpty)
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "suppress an equivalent lower-priority duplicate as EquivalentDuplicate, keeping the first for cross-priority comparison" in {
      val first = terms(supplier, "acme", lowerProvenance)
      val duplicate = terms(supplier, "acme", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector.empty, Vector(first, duplicate)) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(first)))
          assert(resolution.suppressedFilters == Vector(SuppressedFilter(duplicate, SuppressionReason.EquivalentDuplicate)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "report ConflictingLowerPriority for a non-equivalent lower-priority duplicate in the same slot" in {
      val first = terms(supplier, "acme", lowerProvenance)
      val second = terms(supplier, "globex", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector.empty, Vector(first, second)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(ConstraintResolutionError.ConflictingLowerPriority(ConstraintSlot.Terms(FieldId("supplier")), 0, 1, canonical(first), canonical(second)))
          )
        case Right(value) => fail(s"expected conflict, got $value")
      }
    }

    "accumulate multiple lower-priority conflicts, each against the slot's first occurrence" in {
      val first = terms(supplier, "acme", lowerProvenance)
      val second = terms(supplier, "globex", lowerProvenance)
      val third = terms(supplier, "initech", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector.empty, Vector(first, second, third)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                ConstraintResolutionError.ConflictingLowerPriority(ConstraintSlot.Terms(FieldId("supplier")), 0, 1, canonical(first), canonical(second)),
                ConstraintResolutionError.ConflictingLowerPriority(ConstraintSlot.Terms(FieldId("supplier")), 0, 2, canonical(first), canonical(third)),
              )
          )
        case Right(value) => fail(s"expected conflicts, got $value")
      }
    }

    "suppress a lower-priority constraint equivalent to an applied higher-priority constraint as EquivalentDuplicate" in {
      val higher = terms(department, "shoes", higherProvenance)
      val lower = terms(department, "shoes", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(higher), Vector(lower)) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(higher)))
          assert(resolution.suppressedFilters == Vector(SuppressedFilter(lower, SuppressionReason.EquivalentDuplicate)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "suppress a lower-priority constraint non-equivalent to an applied higher-priority constraint as OverriddenByHigherPrecedence" in {
      val higher = terms(department, "shoes", higherProvenance)
      val lower = terms(department, "apparel", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(higher), Vector(lower)) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(higher)))
          assert(resolution.suppressedFilters == Vector(SuppressedFilter(lower, SuppressionReason.OverriddenByHigherPrecedence)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "let constraints in different slots all coexist without suppression or conflict" in {
      val higher = terms(department, "shoes", higherProvenance)
      val lowerTerms = terms(supplier, "acme", lowerProvenance)
      val lowerRange = SourcedConstraint(PlannedConstraint.NumberRange(stockMin, RangeBounds(Bound.Inclusive(1), Bound.Unbounded)), lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(higher), Vector(lowerTerms, lowerRange)) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(higher), AppliedFilter(lowerTerms), AppliedFilter(lowerRange)))
          assert(resolution.suppressedFilters.isEmpty)
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "produce applied filters in exact order: unique higher-priority constraints in request order, then unique lower-priority survivors in parser order" in {
      val higherA = terms(department, "shoes", higherProvenance)
      val higherB = terms(supplier, "acme", higherProvenance)
      val lowerSurvivor = SourcedConstraint(PlannedConstraint.NumberRange(stockMin, RangeBounds(Bound.Inclusive(1), Bound.Unbounded)), lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(higherA, higherB), Vector(lowerSurvivor)) match {
        case Right(resolution) => assert(resolution.appliedFilters == Vector(AppliedFilter(higherA), AppliedFilter(higherB), AppliedFilter(lowerSurvivor)))
        case Left(errors)      => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "produce suppressed filters in exact order: higher-tier encounter order, then one merged lower-tier encounter order across same-priority and cross-priority reasons" in {
      val h1 = terms(department, "shoes", higherProvenance)
      val h2 = terms(warehouse, "south", higherProvenance)
      // Lower vector, by original index: 0=cross-duplicate, 1=anchor(applied), 2=same-priority-duplicate-of-1, 3=cross-overridden.
      val l0 = terms(department, "shoes", lowerProvenance)
      val l1 = terms(supplier, "acme", lowerProvenance)
      val l2 = terms(supplier, "acme", lowerProvenance)
      val l3 = terms(warehouse, "north", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(h1, h2), Vector(l0, l1, l2, l3)) match {
        case Right(resolution) =>
          assert(resolution.appliedFilters == Vector(AppliedFilter(h1), AppliedFilter(h2), AppliedFilter(l1)))
          assert(
            resolution.suppressedFilters ==
              Vector(
                SuppressedFilter(l0, SuppressionReason.EquivalentDuplicate),
                SuppressedFilter(l2, SuppressionReason.EquivalentDuplicate),
                SuppressedFilter(l3, SuppressionReason.OverriddenByHigherPrecedence),
              )
          )
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "preserve each constraint's own provenance through both applied and suppressed filters" in {
      val facetSelectionProvenance = ConstraintProvenance.FacetSelection(FacetSelectionId("sel-1"))
      val higher = terms(department, "shoes", facetSelectionProvenance)
      val lowerDuplicate = terms(department, "shoes", ConstraintProvenance.ParsedSoft)
      ConstraintPrecedenceResolver.resolve(Vector(higher), Vector(lowerDuplicate)) match {
        case Right(resolution) =>
          resolution.appliedFilters match {
            case Vector(applied) => assert(applied.source.provenance == facetSelectionProvenance)
            case other => fail(s"expected one applied filter, got $other")
          }
          resolution.suppressedFilters match {
            case Vector(suppressed) => assert(suppressed.source.provenance == ConstraintProvenance.ParsedSoft)
            case other => fail(s"expected one suppressed filter, got $other")
          }
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "return no partial result: a conflict anywhere prevents any applied/suppressed output, even alongside otherwise-valid different-slot constraints" in {
      val conflictingFirst = terms(department, "shoes", higherProvenance)
      val conflictingSecond = terms(department, "apparel", higherProvenance)
      val unrelatedLower = terms(supplier, "acme", lowerProvenance)
      ConstraintPrecedenceResolver.resolve(Vector(conflictingFirst, conflictingSecond), Vector(unrelatedLower)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(ConstraintResolutionError.ConflictingHigherPriority(ConstraintSlot.Terms(FieldId("department")), 0, 1, canonical(conflictingFirst), canonical(conflictingSecond)))
          )
        case Right(value) => fail(s"expected no partial success, got $value")
      }
    }
  }
}
