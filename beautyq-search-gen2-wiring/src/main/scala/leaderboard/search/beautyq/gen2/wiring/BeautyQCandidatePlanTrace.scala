package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.gen2.contract.*

/** Deterministic, human-readable diagnostic view of one bound [[CompiledCandidateEvaluation]]: the typed
  * gate outcomes and the resulting eligible semantic text/hard constraints or ineligibility reason.
  * `render` accepts only the evaluation [[BeautyQCandidatePlanCompiler]] itself produced, so a compiled
  * plan and a decision from two different compilations can never be rendered together. Every line derives
  * from the aggregate's own already-bound fields; this trace never re-reads policy vectors, invokes the
  * compiler again, or re-implements a gate predicate.
  */
object BeautyQCandidatePlanTrace {

  def render(evaluation: CompiledCandidateEvaluation): String = {
    val gateLines =
      evaluation.gateResults.zipWithIndex.map { case (CandidateGateResult(gate, outcome), index) =>
        outcome match {
          case CandidateGateOutcome.Passed =>
            s"eligibility.gate[$index] id=${gate.stableId} outcome=passed"
          case CandidateGateOutcome.Rejected(reason) =>
            s"eligibility.gate[$index] id=${gate.stableId} outcome=rejected reason=${reason.stableCode}"
        }
      }

    val semanticPartLines =
      evaluation.semanticTextParts.zipWithIndex.map { case (part, index) =>
        s"semantic.part[$index] id=${part.stableId}"
      }

    val decisionLines =
      evaluation.decision match {
        case CandidatePlanDecision.Eligible(plan) =>
          val hardConstraintLines =
            plan.hardConstraints.zipWithIndex.map { case (constraint, index) =>
              s"decision.hard-constraint[$index] ${PlannedAlgebraTrace.constraint(constraint)}"
            }
          Vector(s"decision.eligible semantic-text=${renderQuoted(plan.semanticText.value)}") ++ hardConstraintLines

        case CandidatePlanDecision.Ineligible(reason) =>
          Vector(s"decision.ineligible reason=${reason.stableCode}")
      }

    (Vector("=== semantic ===") ++ semanticPartLines ++
      Vector("", "=== eligibility ===") ++ gateLines ++
      (Vector("", "=== decision ===") ++ decisionLines)).mkString("\n")
  }

  private def renderQuoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
}
