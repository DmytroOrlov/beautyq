package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
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
  }

  private def execute(context: Context): BeautyQSearchOrchestrator.Result =
    BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, materialized, context.embedding, context.qdrant, context.baselineService) match {
      case Right(result) => result
      case Left(error)   => fail(s"expected orchestrator result, got $error")
    }
}
