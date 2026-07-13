package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*

final class ConstraintResolution[Document] private[plan] (
  private val appliedFilters0: Vector[AppliedFilter[Document]],
  private val suppressedFilters0: Vector[SuppressedFilter[Document]],
) {
  def appliedFilters: Vector[AppliedFilter[Document]] = appliedFilters0
  def suppressedFilters: Vector[SuppressedFilter[Document]] = suppressedFilters0
}

sealed trait ConstraintResolutionError

object ConstraintResolutionError {
  final case class ConflictingHigherPriority(
    slot: ConstraintSlot,
    firstIndex: Int,
    secondIndex: Int,
    first: CanonicalConstraint,
    second: CanonicalConstraint,
  ) extends ConstraintResolutionError

  final case class ConflictingLowerPriority(
    slot: ConstraintSlot,
    firstIndex: Int,
    secondIndex: Int,
    first: CanonicalConstraint,
    second: CanonicalConstraint,
  ) extends ConstraintResolutionError
}

/** Resolves hard-constraint precedence between two explicit priority tiers using [[CanonicalConstraintView]]
  * as the only constraint-identity/equivalence mechanism - never `SearchField` equality, field path, or
  * provenance. The caller supplies the two tiers already split by its own domain policy; this resolver
  * never inspects [[ConstraintProvenance]] to infer which tier is higher.
  *
  * Within each tier independently, the first constraint seen in a [[ConstraintSlot]] is that slot's
  * anchor; every later constraint in the same slot is compared only against the anchor (never against
  * each other), and is either an equivalent-duplicate suppression or a conflict. If either tier contains
  * any same-priority conflict, resolution fails with every accumulated conflict (higher-tier conflicts
  * first, each ordered by its own second-occurrence index, then lower-tier conflicts the same way) and
  * produces no partial result. Only once both tiers are internally conflict-free does a surviving lower-
  * tier constraint get compared against the higher tier's applied constraints in the same slot: an
  * equivalent match is suppressed as a duplicate, a non-equivalent match is suppressed as overridden, and
  * an unmatched slot applies. Applied filters are higher-tier constraints in their original order followed
  * by surviving lower-tier constraints in their original order; suppressed filters are ordered the same
  * way, one encounter pass per tier.
  */
object ConstraintPrecedenceResolver {

  private final case class Anchored[Document](sourced: SourcedConstraint[Document], canonical: CanonicalConstraint, slot: ConstraintSlot, index: Int)

  // Suppressions carry their originating index so a tier's same-priority-duplicate suppressions and
  // (for the lower tier) cross-priority suppressions can be merged into one true encounter-order
  // sequence, rather than grouped by which pass produced them.
  private final case class IndexedSuppression[Document](index: Int, suppressed: SuppressedFilter[Document])

  private final case class SamePriorityResult[Document](
    applied: Vector[Anchored[Document]],
    suppressed: Vector[IndexedSuppression[Document]],
    conflicts: Vector[ConstraintResolutionError],
  )

  def resolve[Document](
    higherPriority: Vector[SourcedConstraint[Document]],
    lowerPriority: Vector[SourcedConstraint[Document]],
  ): Either[NonEmptyErrors[ConstraintResolutionError], ConstraintResolution[Document]] = {
    val higherResult = resolveSamePriority(higherPriority, isHigher = true)
    val lowerResult = resolveSamePriority(lowerPriority, isHigher = false)

    NonEmptyErrors.fromVector(higherResult.conflicts ++ lowerResult.conflicts) match {
      case Some(errors) => Left(errors)
      case None =>
        val higherCanonicalBySlot: Map[ConstraintSlot, CanonicalConstraint] =
          higherResult.applied.map(anchor => anchor.slot -> anchor.canonical).toMap

        val (finalLowerApplied, crossSuppressed) =
          lowerResult.applied.foldLeft((Vector.empty[SourcedConstraint[Document]], Vector.empty[IndexedSuppression[Document]])) {
            case ((appliedAcc, suppressedAcc), anchor) =>
              higherCanonicalBySlot.get(anchor.slot) match {
                case Some(higherCanonical) if higherCanonical == anchor.canonical =>
                  (appliedAcc, suppressedAcc :+ IndexedSuppression(anchor.index, SuppressedFilter(anchor.sourced, SuppressionReason.EquivalentDuplicate)))
                case Some(_) =>
                  (appliedAcc, suppressedAcc :+ IndexedSuppression(anchor.index, SuppressedFilter(anchor.sourced, SuppressionReason.OverriddenByHigherPrecedence)))
                case None =>
                  (appliedAcc :+ anchor.sourced, suppressedAcc)
              }
          }

        val appliedFilters =
          (higherResult.applied.map(_.sourced) ++ finalLowerApplied).map(AppliedFilter.apply)

        // Higher-tier suppressions need no merge (same-priority duplicates are its only source). Lower-
        // tier suppressions merge same-priority duplicates and cross-priority suppressions by their
        // shared original index, so the result is one true encounter-order pass over the lower vector.
        val lowerSuppressedFilters = (lowerResult.suppressed ++ crossSuppressed).sortBy(_.index).map(_.suppressed)
        val suppressedFilters = higherResult.suppressed.map(_.suppressed) ++ lowerSuppressedFilters

        Right(new ConstraintResolution(appliedFilters, suppressedFilters))
    }
  }

  // One left-to-right scan per tier: the first constraint in a slot becomes that slot's sole anchor for
  // every later same-slot comparison in this tier, so applied/suppressed/conflict-secondIndex ordering
  // all fall out of simple scan order without a second sort.
  private def resolveSamePriority[Document](
    vector: Vector[SourcedConstraint[Document]],
    isHigher: Boolean,
  ): SamePriorityResult[Document] = {
    val anchored = vector.zipWithIndex.map { case (sourced, index) =>
      val canonical = CanonicalConstraintView(sourced.constraint)
      Anchored(sourced, canonical, CanonicalConstraintView.slot(canonical), index)
    }

    val (_, applied, suppressed, conflicts) =
      anchored.foldLeft((Map.empty[ConstraintSlot, Anchored[Document]], Vector.empty[Anchored[Document]], Vector.empty[IndexedSuppression[Document]], Vector.empty[ConstraintResolutionError])) {
        case ((anchors, appliedAcc, suppressedAcc, conflictAcc), current) =>
          anchors.get(current.slot) match {
            case None =>
              (anchors + (current.slot -> current), appliedAcc :+ current, suppressedAcc, conflictAcc)
            case Some(anchor) if anchor.canonical == current.canonical =>
              (anchors, appliedAcc, suppressedAcc :+ IndexedSuppression(current.index, SuppressedFilter(current.sourced, SuppressionReason.EquivalentDuplicate)), conflictAcc)
            case Some(anchor) =>
              val error =
                if (isHigher) ConstraintResolutionError.ConflictingHigherPriority(current.slot, anchor.index, current.index, anchor.canonical, current.canonical)
                else ConstraintResolutionError.ConflictingLowerPriority(current.slot, anchor.index, current.index, anchor.canonical, current.canonical)
              (anchors, appliedAcc, suppressedAcc, conflictAcc :+ error)
          }
      }

    SamePriorityResult(applied, suppressed, conflicts)
  }
}
