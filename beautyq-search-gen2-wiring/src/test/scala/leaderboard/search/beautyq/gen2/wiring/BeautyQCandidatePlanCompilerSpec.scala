package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.{PublicOperator as _, *}
import leaderboard.search.gen2.core.plan.*
import org.scalatest.wordspec.AnyWordSpec

/** BeautyQCandidatePlanCompiler proofs built exclusively from real, validated requests
  * (`BeautySearchRequestGen2.validate`), real parsed intents (`BeautyQIntentParserGen2.parse`) and real
  * compiled plans (`BeautyQSearchPlanCompiler.compile`) - never a directly-constructed
  * `CompiledBeautyQSearchPlan`, whose constructor stays private to `BeautyQSearchPlanCompiler`; pagination
  * eligibility is derived from its framework-owned bound cursor context.
  * `NotFirstPage` now has an end-to-end proof from a valid bound cursor; full gate-order precedence is
  * also proven at the policy level in `BeautyQSemanticCandidatePolicySpec`. `BeautyQCandidatePlanCompiler.compile` returns
  * `Either[CandidateEvaluationError, CompiledCandidateEvaluation]`; every fixture here always resolves
  * `Right`, since a real compiled plan's `SemanticQueryText` gate genuinely tracks `semanticText.isRight` -
  * `CandidateEvaluationError`'s own reachability is proven generically in `SemanticCandidatePlanSpec`
  * against a deliberately misconfigured neutral policy, not against this domain's real pipeline.
  */
final class BeautyQCandidatePlanCompilerSpec extends AnyWordSpec {

  private val vocabulary = BeautyQIntentVocabulary.value
  private val page = PageRequest(None, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))

  private def rawRequest(
    query: Option[String] = None,
    filters: Vector[PublicFilterInput] = Vector.empty,
    sort: Vector[BeautySortInput] = Vector.empty,
    pageRequest: PageRequest = page,
  ): BeautySearchRequestGen2 =
    BeautySearchRequestGen2(query, filters, Vector.empty, sort, pageRequest, None)

  private def compiled(request: BeautySearchRequestGen2): CompiledBeautyQSearchPlan = {
    val validated = BeautySearchRequestGen2.validate(request) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture request failed to validate: ${errors.toVector}")
    }
    val intent = BeautyQIntentParserGen2.parse(validated, vocabulary) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture intent failed to parse: ${errors.toVector}")
    }
    BeautyQSearchPlanCompiler.compile(validated, intent) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture failed to compile: ${errors.toVector}")
    }
  }

  private def compileOrFail(compiled: CompiledBeautyQSearchPlan): CompiledCandidateEvaluation =
    BeautyQCandidatePlanCompiler.compile(compiled) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected Right, got Left($error)")
    }

  private def serviceFilter(value: String): PublicFilterInput = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar(value), None)

  "CompiledCandidateEvaluation" should {
    "reject construction outside BeautyQCandidatePlanCompiler" in {
      assertDoesNotCompile(
        """new BeautyQCandidatePlanCompiler.CompiledCandidateEvaluation(null, null, null)"""
      )
    }

    "reject subclassing outside BeautyQCandidatePlanCompiler" in {
      assertDoesNotCompile("""final class ForgedEvaluation extends BeautyQCandidatePlanCompiler.CompiledCandidateEvaluation(null, null, null)""")
    }
  }

  "BeautyQCandidatePlanCompiler.compile" should {
    "return Ineligible(NoSemanticQueryText) for an empty default-browse request" in {
      assert(compileOrFail(compiled(rawRequest())).decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText))
    }

    "return Ineligible(NoSemanticQueryText) for a filter-only request with no query text" in {
      val result = compileOrFail(compiled(rawRequest(filters = Vector(serviceFilter("manicure")))))
      assert(result.decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText))
    }

    "return Ineligible(NotFirstPage) for a valid decoded second-page cursor" in {
      val first = compiled(rawRequest(query = Some("ресницы")))
      val cursor = SearchCursor.fromTransport(SearchCursorEnvelope.issue(first.boundPlan, "elasticsearch.search-after.v1").opaqueValue)
      val second = compiled(rawRequest(query = Some("ресницы"), pageRequest = page.copy(cursor = Some(cursor))))

      compileOrFail(second).decision match {
        case CandidatePlanDecision.Ineligible(reason) => assert(reason == BeautyQCandidateIneligibility.NotFirstPage)
        case other                                    => fail(s"expected NotFirstPage, got $other")
      }
    }

    "return Ineligible(NonDefaultSort) when semantic text is present but an explicit sort is set" in {
      val result = compileOrFail(compiled(rawRequest(query = Some("ресницы"), sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc)))))
      assert(result.decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NonDefaultSort))
    }

    "return Ineligible(NoSemanticQueryText), not NonDefaultSort, when both semantic text is absent and sort is explicit" in {
      val result = compileOrFail(compiled(rawRequest(sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc)))))
      assert(result.decision == CandidatePlanDecision.Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText))
    }

    "produce an eligible plan from residual text alone, with no hard constraints" in {
      val result = compileOrFail(compiled(rawRequest(query = Some("something entirely unmatched"))))
      result.decision match {
        case CandidatePlanDecision.Eligible(plan) =>
          assert(plan.semanticText.value == "something entirely unmatched")
          assert(plan.hardConstraints.isEmpty)
        case other => fail(s"expected Eligible, got $other")
      }
    }

    "produce an eligible plan carrying the compiled plan's exact hard constraints, in order" in {
      val fixture = compiled(rawRequest(query = Some("маникюр under 50")))
      compileOrFail(fixture).decision match {
        case CandidatePlanDecision.Eligible(plan) =>
          assert(fixture.plan.hardConstraints.size >= 2)
          assert(plan.hardConstraints == fixture.plan.hardConstraints)
        case other => fail(s"expected Eligible, got $other")
      }
    }

    "derive its eligible semantic text from BeautyQSemanticCandidatePolicy.semanticText, not a separate computation" in {
      val fixture = compiled(rawRequest(query = Some("ногти")))
      val expectedText = BeautyQSemanticCandidatePolicy.semanticText(fixture.plan.residualText, fixture.canonicalSemanticLabels)
      compileOrFail(fixture).decision match {
        case CandidatePlanDecision.Eligible(plan) => assert(Right(plan.semanticText) == expectedText)
        case other                                 => fail(s"expected Eligible, got $other")
      }
    }

    "bind the exact compiled plan and typed gate results the trace renders, from one call" in {
      val fixture = compiled(rawRequest(query = Some("ресницы")))
      val result = compileOrFail(fixture)
      assert(result.compiled eq fixture)
      assert(result.semanticTextParts eq BeautyQSemanticCandidatePolicy.semanticTextParts)
      assert(result.semanticTextParts.map(_.stableId) == Vector("residual-text", "canonical-semantic-labels"))
      assert(
        result.gateResults ==
          Vector(
            CandidateGateResult(BeautyQCandidateEligibilityGate.SemanticQueryText, CandidateGateOutcome.Passed),
            CandidateGateResult(BeautyQCandidateEligibilityGate.FirstPage, CandidateGateOutcome.Passed),
            CandidateGateResult(BeautyQCandidateEligibilityGate.DefaultSort, CandidateGateOutcome.Passed),
          )
      )
    }

    "derive the decision's reported reason from the same bound gate results the trace renders" in {
      val fixture = compiled(rawRequest(sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc))))
      val result = compileOrFail(fixture)
      val firstFailingGate = result.gateResults.collectFirst { case CandidateGateResult(gate, CandidateGateOutcome.Rejected(_)) => gate }.getOrElse(fail("expected at least one failing gate"))
      assert(result.decision == CandidatePlanDecision.Ineligible(firstFailingGate.ineligibility))
    }

    "never return Left(CandidateEvaluationError) for a real compiled plan - its SemanticQueryText gate always tracks semanticText.isRight" in {
      val fixtures =
        Vector(
          rawRequest(),
          rawRequest(query = Some("ресницы")),
          rawRequest(sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc))),
        )
      assert(fixtures.map(request => BeautyQCandidatePlanCompiler.compile(compiled(request))).forall(_.isRight))
    }
  }
}
