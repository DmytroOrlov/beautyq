package leaderboard.search

import leaderboard.search.eval.{
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchOfflineRoutingPolicy,
  M10BeautyQSearchQueryCategory,
  M10BeautyQSearchQueryClassification,
  M10BeautyQSearchQueryClassificationInput,
  M10BeautyQSearchQuerySignal,
}
import org.scalatest.wordspec.AnyWordSpec

final class M10BeautyQSearchQueryClassificationSpec extends AnyWordSpec {

  private val forbiddenClaims: List[String] = List(
    "hybrid serving",
    "score fusion",
    "reranking",
    "fallback",
    "production telemetry",
    "route switch",
    "route activation",
    "quality green",
    "production ready",
    "production readiness",
    "serving approval",
    "activation approved",
    "/beauty-search",
  )

  "M10BeautyQSearchQueryClassification" should {

    "represent every classification category across the representative examples" in {
      val represented = M10BeautyQSearchQueryClassification.RepresentativeResults.map(_.category).toSet

      M10BeautyQSearchQueryCategory.stableOrder.foreach { category =>
        assert(represented.contains(category), s"category not represented: ${category.render}")
      }
    }

    "classify the three accepted anchors deterministically" in {
      val anchors = M10BeautyQSearchQueryClassification.AnchorQueryIds
      assert(anchors == List("q_nails_001", "q_nails_003", "q_noise_005"))

      val expected = Map(
        "q_nails_001" -> M10BeautyQSearchQueryCategory.MixedIntent,
        "q_nails_003" -> M10BeautyQSearchQueryCategory.AttributeFilterIntent,
        "q_noise_005" -> M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent,
      )

      anchors.foreach { id =>
        val input = M10BeautyQSearchQueryClassification.RepresentativeInputs
          .find(_.queryId == id)
          .getOrElse(fail(s"missing anchor input $id"))
        val first = M10BeautyQSearchQueryClassification.classify(input)
        val second = M10BeautyQSearchQueryClassification.classify(input)

        assert(first == second)
        assert(first.category == expected(id))
      }
    }

    "route every noisy/ambiguous/non-beauty query away from backend execution" in {
      val noisy = M10BeautyQSearchQueryClassification.RepresentativeResults
        .filter(_.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)

      assert(noisy.nonEmpty)
      noisy.foreach { result =>
        assert(result.isNoise)
        val intent = M10BeautyQSearchOfflineRoutingPolicy.strategyIntentFor(result)
        assert(
          intent == M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked ||
            intent == M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise,
          s"noisy query routed to backend: ${result.queryId} -> ${intent.render}",
        )
        assert(!intent.isBackendCandidateRetrievalIntent)
      }
    }

    "classify an empty-signal query as noise rather than a backend intent" in {
      val result = M10BeautyQSearchQueryClassification.classify(
        M10BeautyQSearchQueryClassificationInput("q_empty", "", Nil),
      )

      assert(result.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(result.isNoise)
      assert(!result.manualReviewEligible)
    }

    "treat mixed intent as a mixed category without any production hybrid claim" in {
      val mixed = M10BeautyQSearchQueryClassification.RepresentativeResults
        .find(_.queryId == "q_nails_001")
        .getOrElse(fail("missing q_nails_001"))

      assert(mixed.category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(mixed.intentSignals.contains(M10BeautyQSearchQuerySignal.Service))
      assert(mixed.intentSignals.contains(M10BeautyQSearchQuerySignal.Attribute))
      assert(!mixed.rationale.toLowerCase.contains("hybrid"))
    }

    "carry no forbidden production/hybrid/fallback/fusion/reranking/telemetry claims in rationales" in {
      val rationales = M10BeautyQSearchQueryClassification.RepresentativeResults.map(_.rationale)

      rationales.foreach { rationale =>
        val lower = rationale.toLowerCase
        forbiddenClaims.foreach { claim =>
          assert(!lower.contains(claim), s"forbidden claim '$claim' in rationale: $rationale")
        }
      }
    }
  }
}
