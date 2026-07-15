package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

/** Pins BeautyQ's semantic-candidate policy against independent expected literals, proving semantic-text
  * composition and gate precedence directly from typed facts - never through a compiled plan or a forged
  * cursor, per Brick 4G-A's own scope (`NotFirstPage`'s full precedence position is proven here at the
  * policy level; the wiring suite proves the cursor-bearing `NotFirstPage` path end to end).
  */
final class BeautyQSemanticCandidatePolicySpec extends AnyWordSpec {

  private def label(stableKey: String, text: String): CanonicalSemanticLabel =
    CanonicalSemanticLabel.from(stableKey, text).getOrElse(fail(s"expected a valid CanonicalSemanticLabel fixture ($stableKey, $text)"))

  private val someText: SemanticQueryText = SemanticQueryText.from("lashes").getOrElse(fail("expected a valid SemanticQueryText fixture"))

  "BeautyQSemanticCandidatePolicy.semanticTextParts" should {
    "declare ResidualText then CanonicalSemanticLabels" in {
      assert(BeautyQSemanticCandidatePolicy.semanticTextParts == Vector(BeautyQSemanticTextPart.ResidualText, BeautyQSemanticTextPart.CanonicalSemanticLabels))
      assert(BeautyQSemanticCandidatePolicy.semanticTextParts.map(_.stableId) == Vector("residual-text", "canonical-semantic-labels"))
    }
  }

  "BeautyQSemanticCandidatePolicy.eligibilityGates" should {
    "declare SemanticQueryText, FirstPage then DefaultSort with their ineligibility mapping" in {
      assert(
        BeautyQSemanticCandidatePolicy.eligibilityGates ==
          Vector(BeautyQCandidateEligibilityGate.SemanticQueryText, BeautyQCandidateEligibilityGate.FirstPage, BeautyQCandidateEligibilityGate.DefaultSort)
      )
      assert(BeautyQSemanticCandidatePolicy.eligibilityGates.map(_.stableId) == Vector("semantic-query-text", "first-page", "default-sort"))
      assert(
        BeautyQSemanticCandidatePolicy.eligibilityGates.map(_.ineligibility) ==
          Vector(BeautyQCandidateIneligibility.NoSemanticQueryText, BeautyQCandidateIneligibility.NotFirstPage, BeautyQCandidateIneligibility.NonDefaultSort)
      )
      assert(BeautyQSemanticCandidatePolicy.eligibilityGates.map(_.ineligibility.stableCode) == Vector("no-semantic-query-text", "not-first-page", "non-default-sort"))
    }

  }

  "BeautyQSemanticCandidatePolicy.semanticText" should {
    "return Left(EmptyOrBlank) when residual text and canonical labels are both empty - never silently produce a value" in {
      assert(BeautyQSemanticCandidatePolicy.semanticText(None, Vector.empty) == Left(SemanticQueryTextError.EmptyOrBlank))
    }

    "use normalized residual text alone when no labels are present" in {
      assert(BeautyQSemanticCandidatePolicy.semanticText(Some("  Breaking NEWS   Today  "), Vector.empty).map(_.value) == Right("breaking news today"))
    }

    "use normalized, joined label text alone when no residual text is present" in {
      val labels = Vector(label("service:lashes", "Lashes"), label("service:brows", "Brows"))
      assert(BeautyQSemanticCandidatePolicy.semanticText(None, labels).map(_.value) == Right("lashes brows"))
    }

    "join residual text before labels, in that order" in {
      val labels = Vector(label("service:lashes", "Lashes"))
      assert(BeautyQSemanticCandidatePolicy.semanticText(Some("cheap"), labels).map(_.value) == Right("cheap lashes"))
    }

    "preserve label order and duplicate label text when stable keys differ" in {
      val labels = Vector(label("category:a", "Nails"), label("category:b", "Nails"))
      assert(BeautyQSemanticCandidatePolicy.semanticText(None, labels).map(_.value) == Right("nails nails"))
    }

    "discard a normalized-empty component without discarding the others" in {
      val labels = Vector(label("service:lashes", "Lashes"))
      assert(BeautyQSemanticCandidatePolicy.semanticText(Some("..."), labels).map(_.value) == Right("lashes"))
    }

    "return Left(EmptyOrBlank) when every component normalizes to empty" in {
      assert(BeautyQSemanticCandidatePolicy.semanticText(Some("   ..."), Vector.empty) == Left(SemanticQueryTextError.EmptyOrBlank))
    }
  }

  private def evaluateOrFail(
    residualText: Option[String],
    canonicalSemanticLabels: Vector[CanonicalSemanticLabel],
    firstPage: Boolean,
    defaultSort: Boolean,
  ): CandidateEvaluation[BeautyQCandidateEligibilityGate, SemanticQueryText, BeautyQCandidateIneligibility] =
    BeautyQSemanticCandidatePolicy.evaluate(residualText, canonicalSemanticLabels, firstPage, defaultSort, identity)
      .getOrElse(fail("expected Right - BeautyQ's declared SemanticQueryText gate always tracks semanticText.isRight"))

  "BeautyQSemanticCandidatePolicy.evaluate" should {
    "return Ineligible(NoSemanticQueryText) when semantic text is absent, regardless of page or sort" in {
      assert(evaluateOrFail(None, Vector.empty, firstPage = true, defaultSort = true).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText))
      assert(evaluateOrFail(None, Vector.empty, firstPage = false, defaultSort = false).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText))
    }

    "produce NoSemanticQueryText through the declared SemanticQueryText gate itself failing, in the ordinary gate walk - not a detached fallback" in {
      val result = evaluateOrFail(None, Vector.empty, firstPage = true, defaultSort = true)
      assert(result.gateResults.contains(CandidateGateResult(BeautyQCandidateEligibilityGate.SemanticQueryText, CandidateGateOutcome.Rejected(BeautyQCandidateIneligibility.NoSemanticQueryText))))
      assert(result.gateResults.collectFirst { case CandidateGateResult(gate, CandidateGateOutcome.Rejected(_)) => gate.ineligibility } == Some(BeautyQCandidateIneligibility.NoSemanticQueryText))
      assert(result.decision == CandidatePlanDecision.Ineligible(BeautyQCandidateEligibilityGate.SemanticQueryText.ineligibility))
    }

    "declare eligibilityGates containing the exact SemanticQueryText gate whose own ineligibility is NoSemanticQueryText - the only place that reason is written" in {
      assert(BeautyQSemanticCandidatePolicy.eligibilityGates.contains(BeautyQCandidateEligibilityGate.SemanticQueryText))
      assert(BeautyQCandidateEligibilityGate.SemanticQueryText.ineligibility == BeautyQCandidateIneligibility.NoSemanticQueryText)
    }

    "never return Left(CandidateEvaluationError) - the declared SemanticQueryText gate's own predicate always tracks semanticText.isRight, so missing text always fails that gate first, before evaluate's generic policy-error branch could ever be reached" in {
      val results =
        for {
          residualText <- Vector(None, Some("lashes"), Some("   "))
          firstPage    <- Vector(true, false)
          defaultSort  <- Vector(true, false)
        } yield BeautyQSemanticCandidatePolicy.evaluate(residualText, Vector.empty, firstPage, defaultSort, identity)
      assert(results.forall(_.isRight))
    }

    "return Ineligible(NotFirstPage) when semantic text is present but the page is not first, with default sort" in {
      assert(evaluateOrFail(Some("lashes"), Vector.empty, firstPage = false, defaultSort = true).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NotFirstPage))
    }

    "return Ineligible(NonDefaultSort) when semantic text is present and the page is first, but sort is non-default" in {
      assert(evaluateOrFail(Some("lashes"), Vector.empty, firstPage = true, defaultSort = false).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NonDefaultSort))
    }

    "return Eligible carrying the exact computed semantic text when every gate passes" in {
      assert(evaluateOrFail(Some("lashes"), Vector.empty, firstPage = true, defaultSort = true).decision == CandidatePlanDecision.Eligible(someText))
    }

    "resolve full failure precedence from facts alone, in gate order" in {
      assert(evaluateOrFail(None, Vector.empty, firstPage = false, defaultSort = false).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText))
      assert(evaluateOrFail(Some("lashes"), Vector.empty, firstPage = false, defaultSort = false).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NotFirstPage))
      assert(evaluateOrFail(Some("lashes"), Vector.empty, firstPage = true, defaultSort = false).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NonDefaultSort))
      assert(evaluateOrFail(Some("lashes"), Vector.empty, firstPage = true, defaultSort = true).decision == CandidatePlanDecision.Eligible(someText))
    }

    "pair every declared gate with its own pass/fail result, in declared order, for one full pass" in {
      val result = evaluateOrFail(Some("lashes"), Vector.empty, firstPage = false, defaultSort = false)
      assert(result.gateResults.map(_.gate) == BeautyQSemanticCandidatePolicy.eligibilityGates)
      assert(
        result.gateResults ==
          Vector(
            CandidateGateResult(BeautyQCandidateEligibilityGate.SemanticQueryText, CandidateGateOutcome.Passed),
            CandidateGateResult(BeautyQCandidateEligibilityGate.FirstPage, CandidateGateOutcome.Rejected(BeautyQCandidateIneligibility.NotFirstPage)),
            CandidateGateResult(BeautyQCandidateEligibilityGate.DefaultSort, CandidateGateOutcome.Rejected(BeautyQCandidateIneligibility.NonDefaultSort)),
          )
      )
    }

    "derive the decision's reported reason from the same gate results it returns" in {
      val result = evaluateOrFail(Some("lashes"), Vector.empty, firstPage = false, defaultSort = false)
      val firstFailingGate = result.gateResults.collectFirst { case CandidateGateResult(gate, CandidateGateOutcome.Rejected(_)) => gate }.getOrElse(fail("expected at least one failing gate"))
      assert(result.decision == CandidatePlanDecision.Ineligible(firstFailingGate.ineligibility))
    }
  }
}
