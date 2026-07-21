package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.contract.GeoPoint
import leaderboard.search.gen2.qdrant.QdrantCandidatePipelineError
import org.scalatest.wordspec.AnyWordSpec

/** Eval evidence is a derived view over the one compiler-owned orchestrator
  * result. It does not re-run candidate, membership or hydration mechanics. */
final class BeautyQNoHarmSupplementEvidenceSpec extends AnyWordSpec {
  import BeautyQOrchestrationTestKit.*

  "BeautyQNoHarmSupplementEvidence.derive" should {
    "derive ineligible status and reason from execute" in {
      val context = ineligible
      val result = execute(context)
      val evidence = BeautyQNoHarmSupplementEvidence.derive(result)

      assert(evidence.status == BeautyQSupplementStatus.Ineligible)
      assert(evidence.statusCode == BeautyQSupplementStatus.Ineligible.stableCode)
      assert(evidence.ineligibilityReason.contains(BeautyQCandidateIneligibility.NoSemanticQueryText))
      assert(evidence.degradationReason.isEmpty)
      assert(evidence.degradationCause.isEmpty)
      assert(evidence.supplementCount == 0)
      assert(evidence.appendedIds.isEmpty)
    }

    "derive success selection without changing the orchestrator result" in {
      val context = eligible()
      val result = execute(context)
      val before = result.appendedCandidates.map(_.id)
      val evidence = BeautyQNoHarmSupplementEvidence.derive(result)

      assert(evidence.status == result.status)
      assert(evidence.statusCode == result.statusCode)
      assert(evidence.appendedIds == before)
      assert(evidence.supplementCount == result.supplementCount)
      assert(result.appendedCandidates.map(_.id) == before)
    }

    "preserve the exact typed degradable cause" in {
      val context = timedOut
      val result = execute(context)
      val evidence = BeautyQNoHarmSupplementEvidence.derive(result)

      assert(evidence.status == BeautyQSupplementStatus.SupplementFailed)
      assert(evidence.statusCode == BeautyQSupplementStatus.SupplementFailed.stableCode)
      assert(evidence.degradationReason.contains(BeautyQDegradationReason.EmbeddingTimeout))
      evidence.degradationCause match {
        case Some(BeautyQQdrantCandidatePipelineError.Qdrant(QdrantCandidatePipelineError.Embedding(BeautyQEmbeddingRequestError.Timeout("embedding timed out")))) => ()
        case other => fail(s"expected exact timeout cause, got $other")
      }
      assert(evidence.appendedIds.isEmpty)
    }

    "derive membership and duplicate evidence from the evaluated outcome" in {
      val context = eligible(Vector(document.variantId))
      val result = execute(context)
      val evidence = BeautyQNoHarmSupplementEvidence.derive(result)

      assert(evidence.status == BeautyQSupplementStatus.NoAppend)
      assert(evidence.currentPageDuplicateIds == Vector(document.variantId))
      assert(evidence.appendedIds.isEmpty)
      assert(evidence.baselineIds == Vector(document.variantId))
    }

    "produce a machine-readable no-harm gate from the same evidence" in {
      val q006Fixture = BeautyQCutoverQueryFixture.QBroad006ReadyAppendProbe
      val manicureFixture = BeautyQCutoverQueryFixture.ManicureRealRouteProbe
      val q001Fixture = BeautyQCutoverQueryFixture.QBroad001WidenedProbe
      val q003Fixture = BeautyQCutoverQueryFixture.QBroad003WidenedProbe
      val q006 = BeautyQOrchestrationTestKit.eligible(semanticText = q006Fixture.query)
      val manicure = BeautyQOrchestrationTestKit.eligible(
        baselineIds = Vector(document.variantId),
        membershipIds = Some(Vector(document.variantId)),
        semanticText = manicureFixture.query,
      )
      val q001 = BeautyQOrchestrationTestKit.timedOut(q001Fixture.query)
      val q003 = BeautyQOrchestrationTestKit.timedOut(
        q003Fixture.query,
        Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))),
      )
      val gate = BeautyQCutoverGate.evaluate(Vector(
        observation(q006Fixture, q006),
        observation(manicureFixture, manicure),
        observation(q001Fixture, q001),
        observation(q003Fixture, q003),
      ))

      assert(gate.passed)
      assert(gate.metrics.testedQueries == 4)
      assert(gate.metrics.improvedQueries == 1)
      assert(gate.toJson.hcursor.get[Boolean]("passed").exists(identity))
    }
  }

  private def execute(context: Context): BeautyQSearchOrchestrator.Result =
    BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, materialized, context.embedding, context.qdrant, context.baselineService) match {
      case Right(result) => result
      case Left(error)   => fail(s"expected orchestrator result, got $error")
    }

  private def executeApplication(context: Context): BeautyQSearchOrchestrator.Result = {
    val application = BeautyQSearchApplication.make(
      materialized,
      context.baselineService,
      context.embedding,
      context.qdrant,
    )
    application.execute(context.request) match {
      case Right(result) => result
      case Left(error)   => fail(s"expected application result, got $error")
    }
  }

  private def observation(fixture: BeautyQCutoverQueryFixture, context: Context): BeautyQCutoverQueryObservation = {
    val result = executeApplication(context)
    val response = BeautyQSearchResponseGen2Projector.project(result) match {
      case Right(value) => value
      case Left(error) => fail(s"expected projected application response, got $error")
    }
    BeautyQNoHarmSupplementEvidence.fromExecution(result, response) match {
      case Right(evidence) => BeautyQCutoverQueryObservation.fromEvidence(fixture, evidence)
      case Left(error) => fail(s"expected execution evidence, got $error")
    }
  }
}
