package leaderboard.search.beautyq.gen2.wiring.boundary

import leaderboard.search.beautyq.gen2.wiring.*
import org.scalatest.wordspec.AnyWordSpec

/** Black-box compile-negative proofs from a package outside each owner. */
final class BeautyQSupplementBoundarySpec extends AnyWordSpec {
  "BeautyQ readiness" should {
    "expose only policy evaluation" in {
      assertDoesNotCompile("leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementReadinessPolicy.Serving(leaderboard.search.beautyq.gen2.wiring.BeautyQServingMode.FullSearch)")
      assertDoesNotCompile("leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementReadinessPolicy.NotServing(Vector.empty)")
      BeautyQSupplementReadinessPolicy.evaluate(Set.empty) match {
        case _: BeautyQSupplementReadinessPolicy.Result => succeed
      }
    }
  }

  "BeautyQ pipeline disposition" should {
    "hide its implementations from external code" in {
      assertDoesNotCompile("new leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementPolicy.Hard(???)")
      assertDoesNotCompile("new leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementPolicy.Degradable(???, leaderboard.search.beautyq.gen2.wiring.BeautyQDegradationReason.EmbeddingTimeout)")
      assertDoesNotCompile("class Forged extends leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementPolicy.PipelineFailureDisposition")
    }
  }

  "BeautyQ orchestration outcomes" should {
    "be constructible only by the orchestrator owner" in {
      assertDoesNotCompile("new leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrator.Ineligible(leaderboard.search.beautyq.gen2.contract.BeautyQCandidateIneligibility.NoSemanticQueryText)")
      assertDoesNotCompile("class Forged extends leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrator.BeautyQSupplementOutcome")
      assertDoesNotCompile("new leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrator.Result(???, ???, ???)")
    }

    "not expose case-class copy or subclass construction" in {
      assertDoesNotCompile("val outcome: leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrator.Ineligible = ???; outcome.copy(reason = leaderboard.search.beautyq.gen2.contract.BeautyQCandidateIneligibility.NoSemanticQueryText)")
      assertDoesNotCompile("val result: leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrator.Result = ???; result.copy()")
      assertDoesNotCompile("class Forged extends leaderboard.search.beautyq.gen2.wiring.BeautyQSearchOrchestrator.Result(???, ???, ???)")
    }
  }

  "BeautyQ evidence" should {
    "not expose an independent constructor or copy path" in {
      assertDoesNotCompile("new leaderboard.search.beautyq.gen2.eval.BeautyQNoHarmSupplementEvidence.SupplementEvidence(Vector.empty, Vector.empty, 0, leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementStatus.Ineligible, \"ineligible\", None, None, None, Vector.empty, Vector.empty, Vector.empty)")
      assertDoesNotCompile("val evidence: leaderboard.search.beautyq.gen2.eval.BeautyQNoHarmSupplementEvidence.SupplementEvidence = ???; evidence.copy()")
    }
  }
}
