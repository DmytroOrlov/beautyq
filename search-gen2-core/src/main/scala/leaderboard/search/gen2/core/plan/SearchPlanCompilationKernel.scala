package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*

final case class SearchPlanCompilationInput[Document](
  constraintPriorityTiers: ConstraintPriorityTiers[Document],
  residualText: Option[String],
  softSignals: Vector[PlannedSignal[Document]],
  sort: Vector[PlannedSort[Document]],
  page: PageRequest,
  facets: Vector[FacetRequest[Document]],
  groups: Vector[GroupRequest[Document, ?]],
)

sealed trait SearchPlanCompilationError

object SearchPlanCompilationError {
  final case class ConstraintResolutionFailed(error: ConstraintResolutionError) extends SearchPlanCompilationError
  final case class InvalidSearchPlan(error: SearchPlanError) extends SearchPlanCompilationError
}

/** A framework-owned result of resolving one exact [[SearchPlanCompilationInput]]. Its constructor is
  * opaque to callers, so the resolution cannot be paired with another input or manually substituted.
  * A domain may inspect the resolution and then provide one final notices vector to [[assemble]]. */
final class PreparedSearchPlanCompilation[Document] private[plan] (
  private val input: SearchPlanCompilationInput[Document],
  val resolution: ConstraintResolution[Document],
) {
  def assemble(notices: Vector[PlanDiagnostic]): Either[NonEmptyErrors[SearchPlanCompilationError], SearchPlan[Document]] =
    SearchPlanCompilationKernel.assemble(this, notices)

  private[plan] def compilationInput: SearchPlanCompilationInput[Document] = input
}

/** The one reusable assembly point from resolved inbound pieces to a validated [[SearchPlan]]. A domain
  * never assembles or validates a `SearchPlan` by hand: it supplies one [[ConstraintPriorityTiers]],
  * its residual text, soft signals, sort, page, facets and groups. The framework resolves those tiers,
  * then assembles exactly one final notices vector supplied by the caller of [[PreparedSearchPlanCompilation.assemble]].
  *
  * Exposed as a prepared value so a domain can derive its own mode/notices from the resolved applied
  * filters before final assembly, rather than mutating an already-validated plan afterward:
  *
  *   1. [[prepare]] runs precedence resolution alone and returns an opaque
  *      [[PreparedSearchPlanCompilation]] (applied/suppressed filters), so a domain can classify its
  *      own plan mode from `prepared.resolution.appliedFilters`;
  *   2. [[PreparedSearchPlanCompilation.assemble]] assembles the `SearchPlan` from that exact
  *      resolution and one final notices vector, then validates the result. The value this returns on
  *      success is `SearchPlan.validate`'s own return value, never mutated afterward.
  *
  * [[compile]] composes both stages for callers with no notice to derive from resolved output. The
  * kernel knows nothing about any domain's plan modes, semantic labels, matched rule IDs, user-location
  * source, public names, or browse-default classification - those stay domain policy.
  */
object SearchPlanCompilationKernel {

  def prepare[Document](input: SearchPlanCompilationInput[Document]): Either[NonEmptyErrors[SearchPlanCompilationError], PreparedSearchPlanCompilation[Document]] =
    ConstraintPrecedenceResolver.resolve(input.constraintPriorityTiers.higher, input.constraintPriorityTiers.lower) match {
      case Left(errors)      => Left(wrapConstraintErrors(errors))
      case Right(resolution) => Right(new PreparedSearchPlanCompilation(input, resolution))
    }

  private[plan] def assemble[Document](
    prepared: PreparedSearchPlanCompilation[Document],
    notices: Vector[PlanDiagnostic],
  ): Either[NonEmptyErrors[SearchPlanCompilationError], SearchPlan[Document]] = {
    val input = prepared.compilationInput
    val resolution = prepared.resolution
    val plan =
      SearchPlan(
        residualText = input.residualText,
        appliedFilters = resolution.appliedFilters,
        softSignals = input.softSignals,
        sort = input.sort,
        page = input.page,
        facets = input.facets,
        groups = input.groups,
        diagnostics = PlanDiagnostics(resolution.suppressedFilters, notices),
      )

    SearchPlan.validate(plan) match {
      case Left(errors)     => Left(wrapPlanErrors(errors))
      case Right(validPlan) => Right(validPlan)
    }
  }

  def compile[Document](
    input: SearchPlanCompilationInput[Document],
    notices: Vector[PlanDiagnostic] = Vector.empty,
  ): Either[NonEmptyErrors[SearchPlanCompilationError], SearchPlan[Document]] =
    prepare(input).flatMap(_.assemble(notices))

  private def wrapConstraintErrors(errors: NonEmptyErrors[ConstraintResolutionError]): NonEmptyErrors[SearchPlanCompilationError] =
    NonEmptyErrors.fromHead(
      SearchPlanCompilationError.ConstraintResolutionFailed(errors.head),
      errors.tail.map(SearchPlanCompilationError.ConstraintResolutionFailed.apply),
    )

  private def wrapPlanErrors(errors: NonEmptyErrors[SearchPlanError]): NonEmptyErrors[SearchPlanCompilationError] =
    NonEmptyErrors.fromHead(
      SearchPlanCompilationError.InvalidSearchPlan(errors.head),
      errors.tail.map(SearchPlanCompilationError.InvalidSearchPlan.apply),
    )
}
