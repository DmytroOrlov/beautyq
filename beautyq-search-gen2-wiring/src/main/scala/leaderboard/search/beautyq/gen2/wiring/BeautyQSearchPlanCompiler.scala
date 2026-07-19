package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.*

type CompiledBeautyQSearchPlan = BeautyQSearchPlanCompiler.CompiledBeautyQSearchPlan

/** BeautyQ's typed plan-compilation failures. Each case wraps one reusable component's own error type
  * unchanged, preserving its exact filter/sort indexes and names rather than reconstructing them; none
  * of these cases duplicate a generic error case with no added BeautyQ-specific context. */
sealed trait BeautyQSearchPlanCompileError

object BeautyQSearchPlanCompileError {
  final case class PublicInputResolutionFailed(error: PublicPlanInputResolutionError[PublicFieldName, PublicSortName]) extends BeautyQSearchPlanCompileError
  final case class PlanCompilationFailed(error: SearchPlanCompilationError) extends BeautyQSearchPlanCompileError
  final case class CursorValidationFailed(error: SearchCursorError) extends BeautyQSearchPlanCompileError
}

/** Thin composition layer combining an already-validated public request
  * ([[ValidatedBeautySearchRequestGen2]]) and an already-parsed intent ([[ParsedBeautyIntentGen2]]) into
  * one validated [[CompiledBeautyQSearchPlan]]. It re-decodes nothing and re-parses no query text; every
  * mechanic - canonical constraint conversion, precedence resolution, geo-clause resolution, facet
  * assembly and validation - is delegated to search-gen2-core/contract. This object
  * only assembles domain values and applies [[BeautyQSearchPlanPolicy]]'s explicit choices, including its
  * typed constraint precedence and geo-origin values; callers cannot replace those policies per call.
  *
  * Gate order: resolve public geo input first (Gate 1, via `geoOriginPolicy`). The trusted request already
  * carries the facet requests resolved by public validation. Gate 2 prepares constraint precedence, classifies the plan mode
  * and derives one final notices vector from that prepared resolution's own applied filters - never from
  * a post-assembly plan - then assembles and validates the final `SearchPlan`. The value returned as
  * `CompiledBeautyQSearchPlan.plan` is exactly `SearchPlan.validate`'s own return value; nothing copies
  * or mutates it afterward. Gate 4 binds that plan to the declaration-derived cursor identity before
  * exposing it through the compiler-owned `boundPlan`; later pagination code consumes that binding rather
  * than inferring page state from raw cursor presence. A `DefaultBrowse` result has
  * [[BeautyQSearchPlanPolicy.defaultBrowseNotice]] among the notices assembled into the plan before that
  * validation; every other mode assembles no extra notice.
  */
object BeautyQSearchPlanCompiler {

  private lazy val canonicalPlanView: CanonicalPlanView[VariantSearchDocumentGen2] =
    CanonicalPlanView(BeautyQElasticsearchPolicy.contractFingerprint)

  private object FilterView extends PublicFilterPlanView[DecodedPublicFilter, VariantSearchDocumentGen2, PublicFieldName] {
    def name(value: DecodedPublicFilter): PublicFieldName = value.publicName
    def clause(value: DecodedPublicFilter): PublicFilterClause[VariantSearchDocumentGen2] = value.clause
    def provenance(value: DecodedPublicFilter): ConstraintProvenance = value.provenance
  }

  private object SortView extends PublicSortPlanView[DecodedBeautySortInput, VariantSearchDocumentGen2, PublicSortName] {
    def name(value: DecodedBeautySortInput): PublicSortName = value.name
    def clause(value: DecodedBeautySortInput): PublicSortClause[VariantSearchDocumentGen2] = value.clause
  }

  def compile(
    request: ValidatedBeautySearchRequestGen2,
    intent: ParsedBeautyIntentGen2,
  ): Either[NonEmptyErrors[BeautyQSearchPlanCompileError], CompiledBeautyQSearchPlan] =
    PublicPlanInputResolver.resolve(request.filters, request.sort, BeautyQSearchPlanPolicy.geoOriginPolicy.resolve(request), FilterView, SortView) match {
      case Left(errors) => Left(wrap(errors)(BeautyQSearchPlanCompileError.PublicInputResolutionFailed.apply))

      case Right(resolvedInput) =>
        val tiers = BeautyQSearchPlanPolicy.constraintPrecedence.tiers {
          case BeautyQConstraintSource.PublicRequest => resolvedInput.constraints
          case BeautyQConstraintSource.ParsedIntent  => intent.hardConstraints
        }

        val compilationInput =
          SearchPlanCompilationInput(
            constraintPriorityTiers = tiers,
            residualText = intent.residualText,
            softSignals = intent.softSignals,
            sort = resolvedInput.sort,
            page = request.page,
            facets = request.facets,
            groups = BeautyQSearchPlanPolicy.groupsFor(intent.softSignals),
          )

        SearchPlanCompilationKernel.prepare(compilationInput) match {
          case Left(errors) => Left(wrap(errors)(BeautyQSearchPlanCompileError.PlanCompilationFailed.apply))

          case Right(prepared) =>
            val mode =
              BeautyQSearchPlanPolicy.classify(
                residualText = intent.residualText,
                softSignals = intent.softSignals,
                canonicalSemanticLabels = intent.canonicalSemanticLabels,
                appliedFilters = prepared.resolution.appliedFilters,
                sort = resolvedInput.sort,
              )

            val notices = if (mode == BeautyQSearchPlanMode.DefaultBrowse) Vector(BeautyQSearchPlanPolicy.defaultBrowseNotice) else Vector.empty

            prepared.assemble(notices) match {
              case Left(errors) => Left(wrap(errors)(BeautyQSearchPlanCompileError.PlanCompilationFailed.apply))
              case Right(plan)  =>
                SearchCursorEnvelope.bind(plan, canonicalPlanView) match {
                  case Left(error)    => Left(single(error)(BeautyQSearchPlanCompileError.CursorValidationFailed.apply))
                  case Right(boundPlan) => Right(new CompiledBeautyQSearchPlan(boundPlan, mode, intent.canonicalSemanticLabels, intent.matchedRuleIds))
                }
            }
        }
    }

  /** Final read-only result owned by this compiler. A private constructor and no companion object
    * make the compiler the only production construction path; the bound plan/cursor context is the
    * single source for both the exposed plan and later pagination state. */
  final class CompiledBeautyQSearchPlan private[BeautyQSearchPlanCompiler] (
    val boundPlan: BoundSearchPlan[VariantSearchDocumentGen2],
    val mode: BeautyQSearchPlanMode,
    val canonicalSemanticLabels: Vector[CanonicalSemanticLabel],
    val matchedRuleIds: Vector[IntentRuleId],
  ) {
    def plan: SearchPlan[VariantSearchDocumentGen2] = boundPlan.plan
  }

  private def wrap[A, B](errors: NonEmptyErrors[A])(f: A => B): NonEmptyErrors[B] =
    NonEmptyErrors.fromHead(f(errors.head), errors.tail.map(f))

  private def single[A, B](value: A)(f: A => B): NonEmptyErrors[B] =
    NonEmptyErrors.fromHead(f(value), Vector.empty)
}
