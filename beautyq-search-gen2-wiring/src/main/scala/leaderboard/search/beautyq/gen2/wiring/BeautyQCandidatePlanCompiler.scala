package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*

type CompiledCandidateEvaluation = BeautyQCandidatePlanCompiler.CompiledCandidateEvaluation

/** Compiles a trusted, already-validated [[CompiledBeautyQSearchPlan]] into one bound
  * [[CompiledCandidateEvaluation]]. Re-decodes nothing and re-parses no query text: semantic-text
  * composition and gate order/predicates belong entirely to [[BeautyQSemanticCandidatePolicy]], and this
  * object calls its `evaluate` exactly once - never separately recomputing gate results - to obtain the
  * typed gate outcomes and final decision together. This object only derives the two tautological facts
  * the policy cannot derive itself - first-page and default-sort - directly from the compiled plan's own
  * fields, and supplies the framework-owned eligible-plan builder carrying
  * `compiled.plan.hardConstraints` through unchanged.
  *
  * `evaluate` can itself report [[CandidateEvaluationError]] - a malformed generic evaluation policy, never
  * a BeautyQ business outcome - and `compile` propagates it through unchanged via `Either.map`, rather than
  * hiding, reinterpreting, or mapping it onto a [[BeautyQCandidateIneligibility]]. A
  * [[CompiledCandidateEvaluation]] is only ever constructed for the `Right` case, so
  * [[BeautyQCandidatePlanTrace]] - which accepts only that bound type - can never be asked to render a
  * malformed-policy error as business ineligibility.
  */
object BeautyQCandidatePlanCompiler {

  def compile(compiled: CompiledBeautyQSearchPlan): Either[CandidateEvaluationError, CompiledCandidateEvaluation] = {
    val firstPage = compiled.boundPlan.isFirstPage
    val defaultSort = compiled.plan.sort.isEmpty
    BeautyQSemanticCandidatePolicy
      .evaluate(
        compiled.plan.residualText,
        compiled.canonicalSemanticLabels,
        firstPage,
        defaultSort,
        text => CandidatePlan(text, compiled.plan.hardConstraints),
      )
      .map(evaluation =>
        new CompiledCandidateEvaluation(
          compiled,
          evaluation,
          BeautyQSemanticCandidatePolicy.semanticTextParts,
        )
      )
  }

  /** Final read-only result owned by this compiler. It keeps one compiled-plan reference and one generic
    * evaluation produced by the same call; public views delegate to that aggregate rather than storing
    * independently forgeable semantic text, facts, gate results and decision. A private constructor and
    * no public factory make this compiler the only production construction path. */
  final class CompiledCandidateEvaluation private[BeautyQCandidatePlanCompiler] (
    val compiled: CompiledBeautyQSearchPlan,
    private val evaluation: CandidateEvaluation[BeautyQCandidateEligibilityGate, CandidatePlan[VariantSearchDocumentGen2], BeautyQCandidateIneligibility],
    val semanticTextParts: Vector[BeautyQSemanticTextPart],
  ) {
    def gateResults: Vector[CandidateGateResult[BeautyQCandidateEligibilityGate, BeautyQCandidateIneligibility]] = evaluation.gateResults

    def decision: CandidatePlanDecision[CandidatePlan[VariantSearchDocumentGen2], BeautyQCandidateIneligibility] = evaluation.decision
  }

}
