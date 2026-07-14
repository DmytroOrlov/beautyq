package leaderboard.search.gen2.contract

/** A validated, non-blank semantic embedding query. Opaque rather than a wrapped case class so no
  * `copy` or other String-level operation is available outside this defining scope. The public
  * construction boundary is [[SemanticQueryText.from]] and the public read boundary is the `value`
  * extension; semantic-text composition uses the same boundary rather than a second ad-hoc constructor.
  */
opaque type SemanticQueryText = String

sealed trait SemanticQueryTextError

object SemanticQueryTextError {
  case object EmptyOrBlank extends SemanticQueryTextError
}

object SemanticQueryText {

  /** Rejects empty and whitespace-only input; otherwise preserves `value` exactly. Normalization,
    * trimming, and text composition are domain policy, never this constructor's concern.
    */
  def from(value: String): Either[SemanticQueryTextError, SemanticQueryText] =
    if (value.trim.isEmpty) Left(SemanticQueryTextError.EmptyOrBlank) else Right(value)

  extension (text: SemanticQueryText) def value: String = text
}

/** A backend-neutral semantic candidate request: validated embedding text plus the same hard constraints
  * already applied to the originating [[SearchPlan]] (see `SearchPlan.hardConstraints`), unchanged. Carries
  * no retrieval-configuration value of any kind - compiling a `CandidatePlan` into a concrete backend
  * request is later, separate backend-module policy, not this contract.
  */
final case class CandidatePlan[Document](
  semanticText: SemanticQueryText,
  hardConstraints: Vector[PlannedConstraint[Document]],
)

/** A backend-neutral eligibility decision, generic in both the successful plan shape and the domain's own
  * ineligibility-reason vocabulary. This contract carries no reason vocabulary of its own - `Reason` is
  * supplied entirely by each domain's own type, so a second domain never inherits another domain's
  * reasons.
  */
sealed trait CandidatePlanDecision[+Plan, +Reason]

object CandidatePlanDecision {
  final case class Eligible[Plan](plan: Plan) extends CandidatePlanDecision[Plan, Nothing]
  final case class Ineligible[Reason](reason: Reason) extends CandidatePlanDecision[Nothing, Reason]
}

/** The complete typed result of one gate evaluation. A domain supplies either a successful outcome or
  * its own ineligibility reason in one value; the generic evaluator never recomputes either half. */
enum CandidateGateOutcome[+Reason] {
  case Passed
  case Rejected(reason: Reason)
}

/** One declared gate's typed outcome, in declared order. The aggregate evaluator stores these values and
  * derives the final decision from the first rejected outcome, so trace evidence and business reason
  * cannot be produced by separate callbacks. */
final case class CandidateGateResult[Gate, Reason](gate: Gate, outcome: CandidateGateOutcome[Reason])

/** The complete, single-pass result of evaluating one candidate decision: every declared gate's typed
  * outcome in declared order and the resulting plan/decision. A read-only alias for
  * [[SemanticCandidateEvaluation.Result]], whose constructor is private to
  * [[SemanticCandidateEvaluation]] itself: there is no public `apply`, `copy`, companion factory, or
  * subclassing path, so no caller can construct or copy an internally inconsistent aggregate. The
  * semantic-text construction error is returned as [[CandidateEvaluationError]] rather than stored as a
  * second, independently mutable fact.
  */
type CandidateEvaluation[Gate, Plan, Reason] = SemanticCandidateEvaluation.Result[Gate, Plan, Reason]

/** A generic candidate-evaluation policy error, distinct from both [[CandidatePlanDecision]] and any
  * domain's own ineligibility-reason vocabulary - it reports a malformed evaluation policy, never an
  * ordinary business outcome. */
enum CandidateEvaluationError {

  /** Every declared gate passed, yet `semanticText` was `Left` - so no declared gate's own predicate
    * actually rejected the missing/blank semantic text that was given. This means the supplied gate
    * vector omitted (or misdeclared) a gate meant to track `semanticText`; it is not an ordinary domain
    * ineligibility; there is no gate to derive a `Reason` from, since by construction every gate that ran
    * passed. Carries the original [[SemanticQueryTextError]] unmodified, never discarded. */
  case MissingSemanticTextAfterAllGatesPassed(error: SemanticQueryTextError)
}

/** Reusable, domain-neutral candidate-evaluation mechanics. A domain owns component extraction,
  * normalization, gate predicates, gate order and reason values; this object only executes the repeated
  * traversal/elimination/construction/collection/first-failure mechanics over whatever typed parts/gates
  * the domain declares - in the domain's own declared order, never assumed by position.
  */
object SemanticCandidateEvaluation {

  /** The concrete, read-only implementation backing the [[CandidateEvaluation]] alias. Its primary
    * constructor is private to this object, so only [[evaluate]] below can ever produce one; being
    * `final` with no companion `apply`/`copy`, there is no public construction, factory-call, copying, or
    * subclassing path anywhere else - a caller may only read the already-bound gate evidence and decision. */
  final class Result[Gate, Plan, Reason] private[SemanticCandidateEvaluation] (
    val gateResults: Vector[CandidateGateResult[Gate, Reason]],
    val decision: CandidatePlanDecision[Plan, Reason],
  )

  /** Traverses `parts` in the given order, extracts each part's raw components, normalizes every
    * component, discards components that normalize to blank, joins the survivors with one ASCII space,
    * and validates the result through [[SemanticQueryText.from]] - the same public constructor any other
    * caller uses, never a second unsafe path. When no component survives elimination, the joined string is
    * empty and `from` itself reports `Left(SemanticQueryTextError.EmptyOrBlank)`; that error is returned,
    * never discarded into a bare `None`.
    */
  def semanticText[Part](
    parts: Vector[Part],
    rawComponents: Part => Vector[String],
    normalize: String => String,
  ): Either[SemanticQueryTextError, SemanticQueryText] = {
    val components = parts.flatMap(rawComponents).map(normalize).filter(_.trim.nonEmpty)
    SemanticQueryText.from(components.mkString(" "))
  }

  /** Traverses the complete `gates` vector exactly once, in the given declared order. `evaluateGate`
    * returns the gate's complete typed outcome in one call; the evaluator never separately asks for a
    * pass flag and a reason. Reordering `gates` reorders both `gateResults` and which rejection reports.
    *
    * When any declared gate fails, the decision is `Ineligible` with the first failing gate's mapped
    * reason, in declared order - regardless of `semanticText`, and the result is always `Right`. When
    * every declared gate passes: if `semanticText` is `Right`, the decision is `Eligible` with the
    * `eligiblePlan` built from that text, and the result is `Right`; if `semanticText` is `Left`, then none of the gates that ran was
    * actually declared to reject missing semantic text (otherwise it would itself have failed above), so
    * there is no gate to derive a `Reason` from - this is a malformed evaluation policy, not a domain
    * business outcome, and is reported as
    * `Left(CandidateEvaluationError.MissingSemanticTextAfterAllGatesPassed(error))`, carrying the original
    * `SemanticQueryTextError` unmodified rather than inventing a `Reason` or throwing.
    */
  def evaluate[Gate, Plan, Reason](
    gates: Vector[Gate],
    semanticText: Either[SemanticQueryTextError, SemanticQueryText],
    evaluateGate: (Gate, Either[SemanticQueryTextError, SemanticQueryText]) => CandidateGateOutcome[Reason],
    eligiblePlan: SemanticQueryText => Plan,
  ): Either[CandidateEvaluationError, CandidateEvaluation[Gate, Plan, Reason]] = {
    val gateResults = gates.map(gate => CandidateGateResult(gate, evaluateGate(gate, semanticText)))

    gateResults.collectFirst { case CandidateGateResult(_, CandidateGateOutcome.Rejected(reason)) => reason } match {
      case Some(reason) =>
        Right(new Result(gateResults, CandidatePlanDecision.Ineligible(reason)))
      case None =>
        semanticText match {
          case Right(text) => Right(new Result(gateResults, CandidatePlanDecision.Eligible(eligiblePlan(text))))
          case Left(error) => Left(CandidateEvaluationError.MissingSemanticTextAfterAllGatesPassed(error))
        }
    }
  }
}
