package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.QdrantCandidatePipelineError
import leaderboard.search.gen2.core.hydration.CandidateHydrationError
import org.scalatest.wordspec.AnyWordSpec

/** Black-box orchestration proofs. Every successful baseline/evaluation pair is
  * produced by the real BeautyQ compiler and lifecycle-bound ES service. */
final class BeautyQSearchOrchestratorSpec extends AnyWordSpec {
  import BeautyQOrchestrationTestKit.*

  "BeautyQSearchOrchestrator.execute" should {
    "reject a different compiler-owned bound plan before calling Qdrant" in {
      val first = eligible()
      val second = ineligible
      BeautyQSearchOrchestrator.execute(
        first.baseline,
        second.evaluation,
        materialized,
        second.embedding,
        second.qdrant,
        first.baselineService,
      ) match {
        case Left(BeautyQSearchOrchestrationError.BoundPlanMismatch(expected, actual)) =>
          assert(expected == first.baseline.boundPlan.identityHash.value)
          assert(actual == second.evaluation.compiled.boundPlan.identityHash.value)
        case other => fail(s"expected bound-plan mismatch, got $other")
      }
    }

    "return a typed ineligible result and preserve the baseline" in {
      val context = ineligible
      BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, materialized, context.embedding, context.qdrant, context.baselineService) match {
        case Right(result) =>
          assert(result.baseline eq context.baseline)
          assert(result.evaluation eq context.evaluation)
          assert(result.status == BeautyQSupplementStatus.Ineligible)
          assert(result.statusCode == BeautyQSupplementStatus.Ineligible.stableCode)
          assert(result.ineligibilityReason.contains(BeautyQCandidateIneligibility.NoSemanticQueryText))
          assert(result.appendedCandidates.isEmpty)
        case Left(error) => fail(s"expected ineligible result, got $error")
      }
    }

    "execute eligible candidates through Qdrant, bound membership and append-only selection" in {
      val context = eligible()
      BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, materialized, context.embedding, context.qdrant, context.baselineService) match {
        case Right(result) =>
          assert(result.baseline eq context.baseline)
          assert(result.evaluation eq context.evaluation)
          assert(result.status == BeautyQSupplementStatus.Supplemented)
          assert(result.appendedCandidates.map(_.id) == Vector(document.variantId))
          assert(result.supplementCount == 1)
        case Left(error) => fail(s"expected supplemented result, got $error")
      }
    }

    "classify an embedding timeout as the exact degradable typed cause" in {
      val context = timedOut
      BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, materialized, context.embedding, context.qdrant, context.baselineService) match {
        case Right(result) =>
          assert(result.status == BeautyQSupplementStatus.SupplementFailed)
          assert(result.degradationReason.contains(BeautyQDegradationReason.EmbeddingTimeout))
          result.degradationCause match {
            case Some(BeautyQQdrantCandidatePipelineError.Qdrant(QdrantCandidatePipelineError.Embedding(BeautyQEmbeddingRequestError.Timeout("embedding timed out")))) => ()
            case other => fail(s"expected exact timeout cause, got $other")
          }
          assert(result.baseline eq context.baseline)
          assert(result.appendedCandidates.isEmpty)
        case Left(error) => fail(s"expected degradable result, got $error")
      }
    }

    "return the typed membership failure without fabricating a supplement" in {
      val context = membershipFailure
      BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, materialized, context.embedding, context.qdrant, context.baselineService) match {
        case Left(BeautyQSearchOrchestrationError.Membership(ElasticsearchBaselineMembershipError.Transport(_))) => ()
        case other => fail(s"expected typed membership transport failure, got $other")
      }
    }

    "keep hydration generation mismatch as a hard typed pipeline failure" in {
      val context = eligible()
      val inconsistentMaterialized = materialized.copy(projectedDocumentsFingerprint = leaderboard.search.gen2.core.materialization.ProjectedDocumentsFingerprint("different-projection"))
      BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, inconsistentMaterialized, context.embedding, context.qdrant, context.baselineService) match {
        case Left(BeautyQSearchOrchestrationError.CandidatePipeline(BeautyQQdrantCandidatePipelineError.Hydration(CandidateHydrationError.ProjectedDocumentsMismatch(expected, actual)))) =>
          assert(expected != actual)
        case other => fail(s"expected hard generation mismatch, got $other")
      }
    }

    "preserve current-page duplicate classification from the append-only policy" in {
      val context = eligible(Vector(document.variantId))
      BeautyQSearchOrchestrator.execute(context.baseline, context.evaluation, materialized, context.embedding, context.qdrant, context.baselineService) match {
        case Right(result) =>
          assert(result.status == BeautyQSupplementStatus.NoAppend)
          result.outcome match {
            case evaluated: BeautyQSearchOrchestrator.Evaluated =>
              assert(evaluated.selection.currentPageDuplicateIds == Vector(document.variantId))
              assert(evaluated.selection.appended.isEmpty)
            case other => fail(s"expected evaluated outcome, got $other")
          }
        case Left(error) => fail(s"expected no-append result, got $error")
      }
    }
  }
}
