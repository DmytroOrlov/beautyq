package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral calibration for the Brick 4G-A candidate algebra, using an article/library document and its
  * own unrelated part/gate/reason vocabulary - never BeautyQ's - per
  * docs/search/DOMAIN_AUTHORING_PRINCIPLES.md's reuse proof requirement.
  */
final class SemanticCandidatePlanSpec extends AnyWordSpec {

  private final case class ArticleDocument(
    id: UUID,
    section: String,
    wordCount: Int,
  )

  private val section =
    field[ArticleDocument, String]("section", _.section).keyword.filterable(FilterOperator.Equal)

  private val wordCount =
    field[ArticleDocument, Int]("wordCount", _.wordCount).integer.filterable(FilterOperator.Range)

  private val validText: SemanticQueryText = SemanticQueryText.from("longform investigative reporting").getOrElse(fail("expected a valid SemanticQueryText fixture"))

  private val sectionConstraint = PlannedConstraint.Terms(section, Set("world"))
  private val wordCountConstraint = PlannedConstraint.NumberRange(wordCount, RangeBounds(Bound.Inclusive(800), Bound.Unbounded))

  // A reason vocabulary with no relation to BeautyQ's, proving CandidatePlanDecision is reason-parametric
  // rather than tied to any one domain's ineligibility enum.
  private enum ArticleIneligibility {
    case NoHeadlineOrTags
    case Retracted
  }

  "SemanticQueryText.from" should {
    "accept non-blank text and preserve it exactly" in {
      SemanticQueryText.from("longform investigative reporting") match {
        case Right(text) => assert(text.value == "longform investigative reporting")
        case Left(error) => fail(s"expected acceptance, got $error")
      }
    }

    "reject an empty string" in {
      assert(SemanticQueryText.from("") == Left(SemanticQueryTextError.EmptyOrBlank))
    }

    "reject a whitespace-only string" in {
      assert(SemanticQueryText.from("   \t\n  ") == Left(SemanticQueryTextError.EmptyOrBlank))
    }

    "preserve leading/trailing whitespace and casing exactly - no trim, lowercase, tokenize, or normalize" in {
      SemanticQueryText.from("  Breaking NEWS   today  ") match {
        case Right(text) => assert(text.value == "  Breaking NEWS   today  ")
        case Left(error) => fail(s"expected acceptance, got $error")
      }
    }

    "reject direct access to the underlying String outside from/value" in {
      assertDoesNotCompile("""validText.length""")
      assertDoesNotCompile("""validText.toUpperCase""")
    }

    "remain the only constructor - no unsafe/bypassing constructor is exposed, public or package-visible" in {
      assertDoesNotCompile("""SemanticQueryText.unsafeFromComponents(Vector("a", "b"))""")
    }
  }

  "CandidatePlanDecision" should {
    "represent an eligible decision carrying its plan, usable at any Reason type" in {
      val plan = CandidatePlan(validText, Vector(sectionConstraint))
      val decision: CandidatePlanDecision[CandidatePlan[ArticleDocument], ArticleIneligibility] = CandidatePlanDecision.Eligible(plan)
      decision match {
        case CandidatePlanDecision.Eligible(value) => assert(value == plan)
        case other                                  => fail(s"expected Eligible, got $other")
      }
    }

    "represent an ineligible decision carrying its reason, usable at any Plan type" in {
      val decision: CandidatePlanDecision[CandidatePlan[ArticleDocument], ArticleIneligibility] = CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted)
      decision match {
        case CandidatePlanDecision.Ineligible(reason) => assert(reason == ArticleIneligibility.Retracted)
        case other                                     => fail(s"expected Ineligible, got $other")
      }
    }
  }

  "CandidatePlan.hardConstraints" should {
    "preserve the exact given order, unchanged" in {
      val plan = CandidatePlan(validText, Vector(sectionConstraint, wordCountConstraint))
      assert(plan.hardConstraints == Vector(sectionConstraint, wordCountConstraint))
    }

    "carry no retrieval knob such as topK, threshold or oversampling" in {
      assertDoesNotCompile("""CandidatePlan(validText, Vector.empty, topK = 10)""")
    }
  }

  // Neutral vocabulary for SemanticCandidateEvaluation, unrelated to BeautyQ's residual-text/canonical-
  // label parts and semantic-query-text/first-page/default-sort gates.
  private enum ArticleSemanticPart {
    case Headline
    case Tags
  }

  private def articleRawComponents(part: ArticleSemanticPart, headline: Option[String], tags: Vector[String]): Vector[String] =
    part match {
      case ArticleSemanticPart.Headline => headline.toVector
      case ArticleSemanticPart.Tags     => tags
    }

  private def articleNormalize(value: String): String = value.trim.toLowerCase

  private enum ArticleEligibilityGate {
    case HasSemanticText
    case NotRetracted
  }

  private def evaluateArticle(
    gates: Vector[ArticleEligibilityGate],
    semanticText: Either[SemanticQueryTextError, SemanticQueryText],
    outcome: ArticleEligibilityGate => CandidateGateOutcome[ArticleIneligibility],
  ): Either[CandidateEvaluationError, CandidateEvaluation[ArticleEligibilityGate, SemanticQueryText, ArticleIneligibility]] =
    SemanticCandidateEvaluation.evaluate(gates, semanticText, (gate, _) => outcome(gate), identity)

  "SemanticCandidateEvaluation.semanticText" should {
    "compose ordered parts into normalized, joined text" in {
      val parts = Vector(ArticleSemanticPart.Headline, ArticleSemanticPart.Tags)
      val result = SemanticCandidateEvaluation.semanticText[ArticleSemanticPart](parts, articleRawComponents(_, Some("Breaking NEWS"), Vector("World", "Politics")), articleNormalize)
      assert(result.map(_.value) == Right("breaking news world politics"))
    }

    "respect the given part order, not a fixed order" in {
      val reversedParts = Vector(ArticleSemanticPart.Tags, ArticleSemanticPart.Headline)
      val result = SemanticCandidateEvaluation.semanticText[ArticleSemanticPart](reversedParts, articleRawComponents(_, Some("Breaking"), Vector("World")), articleNormalize)
      assert(result.map(_.value) == Right("world breaking"))
    }

    "discard a normalized-empty component without discarding the others" in {
      val parts = Vector(ArticleSemanticPart.Headline, ArticleSemanticPart.Tags)
      val result = SemanticCandidateEvaluation.semanticText[ArticleSemanticPart](parts, articleRawComponents(_, Some("   "), Vector("World")), articleNormalize)
      assert(result.map(_.value) == Right("world"))
    }

    "return Left(EmptyOrBlank), never silently produce a value, when every component normalizes to empty" in {
      val parts = Vector(ArticleSemanticPart.Headline, ArticleSemanticPart.Tags)
      val result = SemanticCandidateEvaluation.semanticText[ArticleSemanticPart](parts, articleRawComponents(_, None, Vector.empty), articleNormalize)
      assert(result == Left(SemanticQueryTextError.EmptyOrBlank))
    }

    "return Left(EmptyOrBlank) for a genuinely empty parts vector - an empty/blank component set can never produce a SemanticQueryText" in {
      val result = SemanticCandidateEvaluation.semanticText[ArticleSemanticPart](Vector.empty, articleRawComponents(_, Some("ignored"), Vector.empty), articleNormalize)
      assert(result == Left(SemanticQueryTextError.EmptyOrBlank))
    }
  }

  private val articleText: SemanticQueryText = SemanticQueryText.from("longform reporting").getOrElse(fail("expected a valid SemanticQueryText fixture"))

  "CandidateEvaluation" should {
    "reject construction outside SemanticCandidateEvaluation" in {
      assertDoesNotCompile(
        """new CandidateEvaluation[ArticleEligibilityGate, SemanticQueryText, ArticleIneligibility](Vector.empty, CandidatePlanDecision.Eligible(articleText))"""
      )
    }

    "reject invoking CandidateEvaluation as a factory - no companion apply exists" in {
      assertDoesNotCompile(
        """CandidateEvaluation(Vector.empty, CandidatePlanDecision.Eligible(articleText))"""
      )
    }

    "reject calling .copy on a produced result - no copy method exists" in {
      val gates = Vector(ArticleEligibilityGate.HasSemanticText, ArticleEligibilityGate.NotRetracted)
      val result = evaluateArticle(gates, Right(articleText), _ => CandidateGateOutcome.Passed).getOrElse(fail("expected Right"))
      assert(result.decision == CandidatePlanDecision.Eligible(articleText))
      assertDoesNotCompile("""result.copy(decision = CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted))""")
    }

    "reject subclassing outside SemanticCandidateEvaluation" in {
      assertDoesNotCompile(
        """final class ForgedResult
          |    extends CandidateEvaluation[
          |      ArticleEligibilityGate,
          |      SemanticQueryText,
          |      ArticleIneligibility,
          |    ](
          |      Vector.empty[CandidateGateResult[ArticleEligibilityGate, ArticleIneligibility]],
          |      CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted),
          |    )""".stripMargin
      )
    }
  }

  "SemanticCandidateEvaluation.evaluate" should {
    "pair every declared gate with its own typed outcome, in declared order" in {
      val gates = Vector(ArticleEligibilityGate.HasSemanticText, ArticleEligibilityGate.NotRetracted)
      val result = evaluateArticle(gates, Right(articleText), _ => CandidateGateOutcome.Passed)
      assert(result.map(_.gateResults) == Right(Vector(CandidateGateResult(ArticleEligibilityGate.HasSemanticText, CandidateGateOutcome.Passed), CandidateGateResult(ArticleEligibilityGate.NotRetracted, CandidateGateOutcome.Passed))))
    }

    "return Eligible with the given semantic text when every gate passes" in {
      val gates = Vector(ArticleEligibilityGate.HasSemanticText, ArticleEligibilityGate.NotRetracted)
      val result = evaluateArticle(gates, Right(articleText), _ => CandidateGateOutcome.Passed)
      assert(result.map(_.decision) == Right(CandidatePlanDecision.Eligible(articleText)))
    }

    "return Ineligible with the first failing gate's reason, in gate order" in {
      val gates = Vector(ArticleEligibilityGate.HasSemanticText, ArticleEligibilityGate.NotRetracted)
      val onlyFirstFails: ArticleEligibilityGate => CandidateGateOutcome[ArticleIneligibility] = {
        case ArticleEligibilityGate.HasSemanticText => CandidateGateOutcome.Rejected(ArticleIneligibility.NoHeadlineOrTags)
        case ArticleEligibilityGate.NotRetracted    => CandidateGateOutcome.Passed
      }
      val onlySecondFails: ArticleEligibilityGate => CandidateGateOutcome[ArticleIneligibility] = {
        case ArticleEligibilityGate.HasSemanticText => CandidateGateOutcome.Passed
        case ArticleEligibilityGate.NotRetracted    => CandidateGateOutcome.Rejected(ArticleIneligibility.Retracted)
      }
      val bothFail: ArticleEligibilityGate => CandidateGateOutcome[ArticleIneligibility] = _ => CandidateGateOutcome.Rejected(ArticleIneligibility.Retracted)

      assert(evaluateArticle(gates, Right(articleText), onlyFirstFails).map(_.decision) == Right(CandidatePlanDecision.Ineligible(ArticleIneligibility.NoHeadlineOrTags)))
      assert(evaluateArticle(gates, Right(articleText), onlySecondFails).map(_.decision) == Right(CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted)))
      // When both fail, the first gate in declared order wins - not the second, even though it too fails.
      assert(evaluateArticle(gates, Right(articleText), bothFail).map(_.decision) == Right(CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted)))
    }

    "return the declared rejection when a gate fails even if semantic text construction failed" in {
      val gates = Vector(ArticleEligibilityGate.HasSemanticText, ArticleEligibilityGate.NotRetracted)
      val result = evaluateArticle(gates, Left(SemanticQueryTextError.EmptyOrBlank), _ => CandidateGateOutcome.Rejected(ArticleIneligibility.NoHeadlineOrTags))
      assert(result.map(_.decision) == Right(CandidatePlanDecision.Ineligible(ArticleIneligibility.NoHeadlineOrTags)))
    }

    "produce the domain-owned no-text reason through the declared semantic gate's own ordinary failure, when semantic text is missing" in {
      // HasSemanticText is the one declared gate whose predicate actually rejects missing text; it fails
      // during the ordinary gate walk itself, so its own mapped reason is reported directly - no separate
      // fallback path is ever reached.
      val gates = Vector(ArticleEligibilityGate.HasSemanticText, ArticleEligibilityGate.NotRetracted)
      val onlyHasSemanticTextFails: ArticleEligibilityGate => CandidateGateOutcome[ArticleIneligibility] = {
        case ArticleEligibilityGate.HasSemanticText => CandidateGateOutcome.Rejected(ArticleIneligibility.NoHeadlineOrTags)
        case ArticleEligibilityGate.NotRetracted    => CandidateGateOutcome.Passed
      }
      val result = evaluateArticle(gates, Left(SemanticQueryTextError.EmptyOrBlank), onlyHasSemanticTextFails)
      assert(result.map(_.decision) == Right(CandidatePlanDecision.Ineligible(ArticleIneligibility.NoHeadlineOrTags)))
    }

    "return Left(CandidateEvaluationError), never an invented Reason, when every active gate passes because none of them was declared to track semantic text" in {
      // The semantic-text-tracking gate is entirely absent from `gates` - a misconfigured policy - so the
      // ordinary gate walk alone can never catch the missing text. Unlike the removed dedicated
      // gate-reference fallback parameter, there is no longer any gate reference to derive a Reason from,
      // so the evaluation reports a distinctly-typed policy error instead of inventing or borrowing one.
      val gates = Vector(ArticleEligibilityGate.NotRetracted)
      val result = evaluateArticle(gates, Left(SemanticQueryTextError.EmptyOrBlank), _ => CandidateGateOutcome.Passed)
      assert(result.isLeft)
    }

    "preserve the original SemanticQueryTextError, unmodified, inside CandidateEvaluationError.MissingSemanticTextAfterAllGatesPassed" in {
      val result = evaluateArticle(Vector.empty, Left(SemanticQueryTextError.EmptyOrBlank), _ => CandidateGateOutcome.Passed)
      assert(result == Left(CandidateEvaluationError.MissingSemanticTextAfterAllGatesPassed(SemanticQueryTextError.EmptyOrBlank)))
    }

    "reorder both the decision and the typed gate-result order when the declared gate order changes" in {
      // Exactly one gate fails, independent of position; only its declared position changes between runs.
      val failingIsRetracted: ArticleEligibilityGate => CandidateGateOutcome[ArticleIneligibility] = {
        case ArticleEligibilityGate.HasSemanticText => CandidateGateOutcome.Passed
        case ArticleEligibilityGate.NotRetracted    => CandidateGateOutcome.Rejected(ArticleIneligibility.Retracted)
      }

      val original = Vector(ArticleEligibilityGate.HasSemanticText, ArticleEligibilityGate.NotRetracted)
      val reordered = Vector(ArticleEligibilityGate.NotRetracted, ArticleEligibilityGate.HasSemanticText)

      val originalResult = evaluateArticle(original, Right(articleText), failingIsRetracted).getOrElse(fail("expected Right"))
      val reorderedResult = evaluateArticle(reordered, Right(articleText), failingIsRetracted).getOrElse(fail("expected Right"))

      assert(originalResult.gateResults.map(_.gate) == original)
      assert(reorderedResult.gateResults.map(_.gate) == reordered)
      // NotRetracted fails either way, but only becomes the *first* failure - and therefore the reported
      // reason - once it is declared first.
      assert(originalResult.decision == CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted))
      assert(reorderedResult.decision == CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted))

      // With a second, different failure added, first-declared-position determines which one wins.
      val bothFail: ArticleEligibilityGate => CandidateGateOutcome[ArticleIneligibility] = {
        case ArticleEligibilityGate.HasSemanticText => CandidateGateOutcome.Rejected(ArticleIneligibility.NoHeadlineOrTags)
        case ArticleEligibilityGate.NotRetracted    => CandidateGateOutcome.Rejected(ArticleIneligibility.Retracted)
      }
      val firstWinsWhenFirst = evaluateArticle(original, Right(articleText), bothFail).getOrElse(fail("expected Right"))
      val firstWinsWhenReordered = evaluateArticle(reordered, Right(articleText), bothFail).getOrElse(fail("expected Right"))
      assert(firstWinsWhenFirst.decision == CandidatePlanDecision.Ineligible(ArticleIneligibility.NoHeadlineOrTags))
      assert(firstWinsWhenReordered.decision == CandidatePlanDecision.Ineligible(ArticleIneligibility.Retracted))
    }
  }

}
