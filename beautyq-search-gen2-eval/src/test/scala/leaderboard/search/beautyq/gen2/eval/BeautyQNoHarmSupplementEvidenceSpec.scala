package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.contract.GeoPoint
import leaderboard.search.gen2.qdrant.QdrantCandidatePipelineError
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQNoHarmSupplementEvidenceSpec extends AnyWordSpec {
  import BeautyQOrchestrationTestKit.*

  "BeautyQNoHarmSupplementEvidence.fromExecution" should {
    "derive ineligible status and reason from the same application result and projection" in {
      val context = ineligible
      val (result, response) = executeApplication(context)
      val evidence = BeautyQNoHarmSupplementEvidence.fromExecution(result, response) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected execution evidence, got $error")
      }

      assert(evidence.status == BeautyQSupplementStatus.Ineligible)
      assert(evidence.statusCode == BeautyQSupplementStatus.Ineligible.stableCode)
      assert(evidence.ineligibilityReason.contains(BeautyQCandidateIneligibility.NoSemanticQueryText))
      assert(evidence.degradationReason.isEmpty)
      assert(evidence.degradationCause.isEmpty)
      assert(evidence.supplementCount == 0)
      assert(evidence.appendedIds.isEmpty)
    }

    "derive success selection and exact baseline prefix preservation" in {
      val context = eligible()
      val (result, response) = executeApplication(context)
      val before = result.appendedCandidates.map(_.id)
      val evidence = BeautyQNoHarmSupplementEvidence.fromExecution(result, response) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected execution evidence, got $error")
      }

      assert(evidence.status == result.status)
      assert(evidence.statusCode == result.statusCode)
      assert(evidence.appendedIds == before)
      assert(evidence.supplementCount == result.supplementCount)
      assert(result.appendedCandidates.map(_.id) == before)
      assert(evidence.resultIds == response.hits.map(_.id))
      assert(evidence.baselineOwnedComponentsPreserved)
    }

    "preserve the exact typed degradable cause from fromExecution" in {
      val context = timedOut
      val (result, response) = executeApplication(context)
      val evidence = BeautyQNoHarmSupplementEvidence.fromExecution(result, response) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected execution evidence, got $error")
      }

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
      val (result, response) = executeApplication(context)
      val evidence = BeautyQNoHarmSupplementEvidence.fromExecution(result, response) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected execution evidence, got $error")
      }

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
      val gate = BeautyQCutoverGate.evaluate(
        BeautyQServingMode.FullSearch,
        Vector(
          observation(q006Fixture, q006),
          observation(manicureFixture, manicure),
          observation(q001Fixture, q001),
          observation(q003Fixture, q003),
        ),
      )

      assert(gate.passed)
      assert(gate.metrics.testedQueries == 4)
      assert(gate.metrics.improvedQueries == 1)
      assert(gate.toJson.hcursor.get[Boolean]("passed").exists(identity))
    }

    "keep the accepted evidence entry point as the only public construction path" in {
      assertDoesNotCompile(
        """{
          |  val result: BeautyQSearchOrchestrator.Result = ???
          |  BeautyQNoHarmSupplementEvidence.derive(result)
          |}""".stripMargin
      )
    }
  }

  private def executeApplication(context: Context): (BeautyQSearchOrchestrator.Result, BeautyQSearchResponseGen2) = {
    val application = BeautyQSearchApplication.make(
      materialized,
      context.baselineService,
      context.embedding,
      context.qdrant,
    )
    val result = application.execute(context.request) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected application result, got $error")
    }
    val response = BeautyQSearchResponseGen2Projector.projectWithoutStatus(result) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected projected application response, got $error")
    }
    (result, response)
  }

  private def observation(fixture: BeautyQCutoverQueryFixture, context: Context): BeautyQCutoverQueryObservation = {
    val (result, response) = executeApplication(context)
    val evidence = BeautyQNoHarmSupplementEvidence.fromExecution(result, response) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected execution evidence, got $error")
    }
    BeautyQCutoverQueryObservation.fromEvidence(fixture, evidence)
  }
}
