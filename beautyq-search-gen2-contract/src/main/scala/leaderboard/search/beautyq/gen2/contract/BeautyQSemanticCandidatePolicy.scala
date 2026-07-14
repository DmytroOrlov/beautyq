package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*

/** BeautyQ's own candidate-ineligibility reason vocabulary. The generic contract carries no reason
  * vocabulary of its own ([[CandidatePlanDecision]] is generic in `Reason`). */
enum BeautyQCandidateIneligibility(val stableCode: String) {
  case NoSemanticQueryText extends BeautyQCandidateIneligibility("no-semantic-query-text")
  case NotFirstPage extends BeautyQCandidateIneligibility("not-first-page")
  case NonDefaultSort extends BeautyQCandidateIneligibility("non-default-sort")
}

/** One semantic-text component source, in the exact order BeautyQ composes embedding text. Each case's
  * `rawComponents` is the one place that source is read; nothing else in this file re-reads
  * `residualText`/`canonicalSemanticLabels` directly. */
enum BeautyQSemanticTextPart(val stableId: String) {
  case ResidualText extends BeautyQSemanticTextPart("residual-text")
  case CanonicalSemanticLabels extends BeautyQSemanticTextPart("canonical-semantic-labels")

  def rawComponents(residualText: Option[String], canonicalSemanticLabels: Vector[CanonicalSemanticLabel]): Vector[String] =
    this match {
      case BeautyQSemanticTextPart.ResidualText            => residualText.toVector
      case BeautyQSemanticTextPart.CanonicalSemanticLabels => canonicalSemanticLabels.map(_.text)
    }
}

/** One candidate-eligibility gate, in the exact order BeautyQ evaluates them. Each case's `passes` predicate
  * and `outcome` conversion are the one place that decides its own fact/reason pair;
  * [[BeautyQSemanticCandidatePolicy.evaluate]] never re-implements a gate's policy. */
enum BeautyQCandidateEligibilityGate(val stableId: String, val ineligibility: BeautyQCandidateIneligibility) {
  case SemanticQueryText extends BeautyQCandidateEligibilityGate("semantic-query-text", BeautyQCandidateIneligibility.NoSemanticQueryText)
  case FirstPage extends BeautyQCandidateEligibilityGate("first-page", BeautyQCandidateIneligibility.NotFirstPage)
  case DefaultSort extends BeautyQCandidateEligibilityGate("default-sort", BeautyQCandidateIneligibility.NonDefaultSort)

  def passes(semanticText: Either[SemanticQueryTextError, SemanticQueryText], firstPage: Boolean, defaultSort: Boolean): Boolean =
    this match {
      case BeautyQCandidateEligibilityGate.SemanticQueryText => semanticText.isRight
      case BeautyQCandidateEligibilityGate.FirstPage         => firstPage
      case BeautyQCandidateEligibilityGate.DefaultSort       => defaultSort
    }

  def outcome(semanticText: Either[SemanticQueryTextError, SemanticQueryText], firstPage: Boolean, defaultSort: Boolean): CandidateGateOutcome[BeautyQCandidateIneligibility] =
    if (passes(semanticText, firstPage, defaultSort)) CandidateGateOutcome.Passed
    else CandidateGateOutcome.Rejected(ineligibility)
}

/** BeautyQ's executable semantic-candidate policy: which parsed-intent facts compose embedding text, in
  * which order, and which facts must hold before a [[CandidatePlan]] is even offered. Contract-owned so
  * it depends only on already-decoded/parsed values ([[ParsedBeautyIntentGen2]]'s own fields,
  * [[SearchPlan]]'s own derived facts) - never on the wiring-owned compiled-plan result type. Static
  * mechanics (Brick 6 retrieval knobs, backend compilation) never belong here.
  */
object BeautyQSemanticCandidatePolicy {

  val semanticTextParts: Vector[BeautyQSemanticTextPart] = Vector(
    BeautyQSemanticTextPart.ResidualText,
    BeautyQSemanticTextPart.CanonicalSemanticLabels,
  )

  val eligibilityGates: Vector[BeautyQCandidateEligibilityGate] = Vector(
    BeautyQCandidateEligibilityGate.SemanticQueryText,
    BeautyQCandidateEligibilityGate.FirstPage,
    BeautyQCandidateEligibilityGate.DefaultSort,
  )

  /** Composes BeautyQ's deterministic semantic embedding text: every [[semanticTextParts]] source's raw
    * components, in part order (residual text, then canonical labels in existing parser order), each
    * normalized through [[BeautyQIntentTextGen2.normalize]], with normalized-empty components discarded,
    * joined with one ASCII space, then validated through [[SemanticQueryText.from]] - the same public
    * constructor any other caller uses. Never deduplicates by text (two labels with different stable keys
    * but equal display text both survive), reorders, or reads filters/facets/sort/geo/the raw request.
    * Delegates every repeated traversal/elimination/construction mechanic to
    * [[SemanticCandidateEvaluation.semanticText]], which returns rather than discards a construction
    * error. */
  def semanticText(
    residualText: Option[String],
    canonicalSemanticLabels: Vector[CanonicalSemanticLabel],
  ): Either[SemanticQueryTextError, SemanticQueryText] =
    SemanticCandidateEvaluation.semanticText[BeautyQSemanticTextPart](
      semanticTextParts,
      _.rawComponents(residualText, canonicalSemanticLabels),
      BeautyQIntentTextGen2.normalize,
    )

  /** Evaluates BeautyQ's complete candidate decision from already-resolved facts in one pass: computes
    * `semanticText` once, then walks the complete declared [[eligibilityGates]] vector - never a subset,
    * never assumed by position - through [[SemanticCandidateEvaluation.evaluate]]. Each gate returns one
    * complete typed outcome; the framework derives the first rejection and the eligible plan from those
    * same stored outcomes. Reordering [[eligibilityGates]] reorders the evaluation and can change which
    * reason a failure reports, with no other change required anywhere else.
    *
    * Returns `Left(CandidateEvaluationError)` - distinct from `BeautyQCandidateIneligibility` and never
    * translated into one - only for a malformed evaluation policy: every declared gate passing while
    * `semanticText` is `Left`. Because `BeautyQCandidateEligibilityGate.SemanticQueryText`'s own predicate
    * checks exactly `semanticText.isRight` (see its `passes` case above), missing semantic text always
    * makes that declared gate fail during the ordinary walk first, reporting its own `ineligibility`
    * (`NoSemanticQueryText`) - read only from that one gate's own declaration, never written again here as
    * a second, detached literal - so this generic error branch is unreachable through this policy's own
    * `eligibilityGates`; it exists only to catch a caller that misdeclares its gate vector. */
  def evaluate[Plan](
    residualText: Option[String],
    canonicalSemanticLabels: Vector[CanonicalSemanticLabel],
    firstPage: Boolean,
    defaultSort: Boolean,
    eligiblePlan: SemanticQueryText => Plan,
  ): Either[CandidateEvaluationError, CandidateEvaluation[BeautyQCandidateEligibilityGate, Plan, BeautyQCandidateIneligibility]] = {
    val text = semanticText(residualText, canonicalSemanticLabels)
    SemanticCandidateEvaluation.evaluate[BeautyQCandidateEligibilityGate, Plan, BeautyQCandidateIneligibility](
      eligibilityGates,
      text,
      (gate, semanticText) => gate.outcome(semanticText, firstPage, defaultSort),
      eligiblePlan,
    )
  }
}
