package leaderboard.search.beautyq.gen2.wiring.boundary

import org.scalatest.wordspec.AnyWordSpec

/** Black-box compile-negative proofs from a package outside each owner. */
final class BeautyQSupplementBoundarySpec extends AnyWordSpec {
  "StartupServingStatus" should {
    "hide its constructor from external packages" in {
      assertDoesNotCompile("""
        new leaderboard.search.beautyq.gen2.wiring.StartupServingStatus(
          leaderboard.search.beautyq.gen2.wiring.SupplementStartupPolicy.Required,
          leaderboard.search.beautyq.gen2.wiring.BeautyQServingMode.FullSearch,
          \"healthy\",
          Option.empty[leaderboard.search.beautyq.gen2.wiring.StartupServingStatus.Reason],
          false,
          \"src\",
          \"proj\",
          \"es-ref\",
          \"es-target\",
          Option.empty[String],
          Option.empty[String],
        )
      """)
      assertDoesNotCompile("""
        new leaderboard.search.beautyq.gen2.wiring.StartupServingStatus.Reason(
          \"code\",
          \"msg\",
          \"detail\",
          Option.empty[leaderboard.search.beautyq.gen2.wiring.BeautyQSearchGenerationActivationError],
        )
      """)
    }

    "not expose arbitrary-reference test factories" in {
      assertDoesNotCompile("""
        leaderboard.search.beautyq.gen2.wiring.StartupServingStatus.healthyFromReferences(
          leaderboard.search.beautyq.gen2.wiring.SupplementStartupPolicy.Required,
          "src",
          "proj",
          "es-ref",
          "es-target",
          "qdrant-gen",
          "qdrant-col",
        )
      """)
      assertDoesNotCompile("""
        leaderboard.search.beautyq.gen2.wiring.StartupServingStatus.disabledFromReferences(
          "src",
          "proj",
          "es-ref",
          "es-target",
        )
      """)
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
