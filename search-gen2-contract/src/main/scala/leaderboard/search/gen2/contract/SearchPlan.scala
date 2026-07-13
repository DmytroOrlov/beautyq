package leaderboard.search.gen2.contract

/** Trusted precedence of one constraint, per docs/gen2/BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md #7/#8.
  * `FacetSelection` carries its validated stable selection identity rather than a parallel optional ID,
  * so a selection can never exist without one. Only Brick 4D's trust boundary may construct
  * `ParsedHard`/`ParsedSoft`/`SystemDefault`; this contract only carries the result of that decision.
  */
sealed trait ConstraintProvenance extends Product with Serializable

object ConstraintProvenance {
  case object ExplicitUi extends ConstraintProvenance
  final case class FacetSelection(id: FacetSelectionId) extends ConstraintProvenance
  case object ParsedHard extends ConstraintProvenance
  case object ParsedSoft extends ConstraintProvenance
  case object SystemDefault extends ConstraintProvenance
}

final case class SourcedConstraint[Document](
  constraint: PlannedConstraint[Document],
  provenance: ConstraintProvenance,
)

/** The two already-ordered tiers a domain's own constraint-source policy produces: `higher` outranks
  * `lower`. A domain policy is the only place that decides which of its inbound sources plays which
  * role; the generic precedence resolver never inspects [[ConstraintProvenance]] to infer it.
  */
final class ConstraintPriorityTiers[Document] private[contract] (
  higher0: Vector[SourcedConstraint[Document]],
  lower0: Vector[SourcedConstraint[Document]],
) {
  val higher: Vector[SourcedConstraint[Document]] = higher0
  val lower: Vector[SourcedConstraint[Document]] = lower0
}

final case class AppliedFilter[Document](source: SourcedConstraint[Document])

enum SuppressionReason {
  case OverriddenByHigherPrecedence
  case EquivalentDuplicate
}

/** A constraint that was considered but did not become an [[AppliedFilter]]. This contract only
  * preserves the result of that decision losslessly; precedence, conflict detection and deduplication
  * are Brick 4F responsibilities.
  */
final case class SuppressedFilter[Document](
  source: SourcedConstraint[Document],
  reason: SuppressionReason,
)

final case class PlanDiagnosticCode(value: String)

final case class PlanDiagnostic(
  code: PlanDiagnosticCode,
  detail: Option[String],
)

final case class PlanDiagnostics[Document](
  suppressedFilters: Vector[SuppressedFilter[Document]],
  notices: Vector[PlanDiagnostic],
)

object PlanDiagnostics {
  def empty[Document]: PlanDiagnostics[Document] = PlanDiagnostics(Vector.empty, Vector.empty)
}

/** An executable typed runtime value, not itself canonical identity - Brick 4C's `PlanIdentity` derives
  * its own value-only projection directly from typed plan values, never from this type's equality or
  * from [[SearchPlanTrace]]'s generated strings.
  *
  * Stores applied sourced filters once (`appliedFilters`) rather than a second, parallel
  * `hardConstraints` vector, so execution constraints and provenance reporting cannot drift apart;
  * [[hardConstraints]] is a derived view, never independently constructed state.
  */
final case class SearchPlan[Document](
  residualText: Option[String],
  appliedFilters: Vector[AppliedFilter[Document]],
  softSignals: Vector[PlannedSignal[Document]],
  sort: Vector[PlannedSort[Document]],
  page: PageRequest,
  facets: Vector[FacetRequest[Document]],
  groups: Vector[GroupRequest[Document, ?]],
  diagnostics: PlanDiagnostics[Document],
) {
  def hardConstraints: Vector[PlannedConstraint[Document]] = appliedFilters.map(_.source.constraint)

  def withoutCursor: SearchPlan[Document] = copy(page = page.withoutCursor)
}

sealed trait SearchPlanError

object SearchPlanError {
  final case class InvalidConstraint(index: Int, error: PlanConstraintError) extends SearchPlanError
  final case class InvalidSort(index: Int, error: PlanConstraintError) extends SearchPlanError
  final case class InvalidFacet(index: Int, error: FacetRequestError) extends SearchPlanError
  final case class InvalidGroup(index: Int, error: GroupRequestError) extends SearchPlanError
  final case class DuplicateFacetId(id: FacetId, firstIndex: Int, duplicateIndex: Int) extends SearchPlanError
  final case class DuplicateGroupId(id: GroupId) extends SearchPlanError
}

object SearchPlan {

  /** Checks every applied constraint, sort, facet and group against its own validator, plus facet/group
    * ID uniqueness. Returns `plan` unchanged on success, matching [[PlannedConstraint.validate]]'s
    * idiom. Never reorders successful plan vectors, normalizes residual text, deduplicates constraints,
    * resolves precedence or adds default browse behavior - those are Brick 4F responsibilities.
    */
  def validate[Document](plan: SearchPlan[Document]): Either[NonEmptyErrors[SearchPlanError], SearchPlan[Document]] =
    NonEmptyErrors.fromVector(violations(plan)) match {
      case Some(errors) => Left(errors)
      case None         => Right(plan)
    }

  // Fixed section order: applied constraints, sorts, facets, duplicate facet IDs, groups, duplicate
  // group IDs. Within each element, its own validator's declared error order is preserved.
  private def violations[Document](plan: SearchPlan[Document]): Vector[SearchPlanError] = {
    val constraintErrors =
      plan.hardConstraints.zipWithIndex.flatMap { case (constraint, index) =>
        PlannedConstraint.validate(constraint) match {
          case Left(errors) => errors.toVector.map(error => SearchPlanError.InvalidConstraint(index, error))
          case Right(_)     => Vector.empty
        }
      }

    val sortErrors =
      plan.sort.zipWithIndex.flatMap { case (sort, index) =>
        PlannedSort.validate(sort) match {
          case Left(errors) => errors.toVector.map(error => SearchPlanError.InvalidSort(index, error))
          case Right(_)     => Vector.empty
        }
      }

    val facetErrors =
      plan.facets.zipWithIndex.flatMap { case (facet, index) =>
        FacetRequest.validate(facet) match {
          case Left(errors) => errors.toVector.map(error => SearchPlanError.InvalidFacet(index, error))
          case Right(_)     => Vector.empty
        }
      }

    val duplicateFacetIdErrors =
      duplicateFacetIds(plan.facets)

    val groupErrors =
      plan.groups.zipWithIndex.flatMap { case (group, index) =>
        GroupRequest.validate(group) match {
          case Left(errors) => errors.toVector.map(error => SearchPlanError.InvalidGroup(index, error))
          case Right(_)     => Vector.empty
        }
      }

    val duplicateGroupIdErrors =
      duplicateIds(plan.groups.map(_.id))(_.value).map(SearchPlanError.DuplicateGroupId.apply)

    constraintErrors ++ sortErrors ++ facetErrors ++ duplicateFacetIdErrors ++ groupErrors ++ duplicateGroupIdErrors
  }

  // Each later duplicate is reported once in declaration encounter order, retaining the first and
  // duplicate positions so diagnostics remain deterministic and actionable.
  private def duplicateFacetIds[Document](facets: Vector[FacetRequest[Document]]): Vector[SearchPlanError] = {
    val (_, errors) =
      facets.zipWithIndex.foldLeft((Map.empty[FacetId, Int], Vector.empty[SearchPlanError])) {
        case ((firstIndexes, errors), (facet, index)) =>
          firstIndexes.get(facet.id) match {
            case Some(firstIndex) => (firstIndexes, errors :+ SearchPlanError.DuplicateFacetId(facet.id, firstIndex, index))
            case None             => (firstIndexes.updated(facet.id, index), errors)
          }
      }
    errors
  }

  private def duplicateIds[Id](ids: Vector[Id])(canonicalValue: Id => String): Vector[Id] =
    ids.groupBy(identity).collect { case (id, occurrences) if occurrences.length > 1 => id }.toVector.sortBy(canonicalValue)
}
