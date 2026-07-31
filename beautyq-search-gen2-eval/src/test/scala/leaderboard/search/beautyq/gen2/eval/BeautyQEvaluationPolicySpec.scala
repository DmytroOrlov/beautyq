package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.gen2.eval.EvaluationSurfaceId
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQEvaluationPolicySpec extends AnyWordSpec {
  "BeautyQEvaluationPolicy" should {
    "expose exact active surface order" in {
      val surfaces = BeautyQEvaluationPolicy.activeSurfaces
      assert(surfaces.length == 3)
      assert(surfaces(0).value == "variants")
      assert(surfaces(1).value == "providers")
      assert(surfaces(2).value == "service-intents")
    }

    "expose exact cutoffs 1,3,5,10" in {
      val cutoffs = BeautyQEvaluationPolicy.cutoffs
      assert(cutoffs.values.map(_.value) == Vector(1, 3, 5, 10))
    }

    "own the fixed Q2 execution profile" in {
      assert(BeautyQEvaluationPolicy.CurrentVersion == "beautyq-evaluation-policy-v1")
      assert(BeautyQEvaluationPolicy.pageSize.value == 20)
      assert(BeautyQEvaluationPolicy.warmupPasses == 1)
      assert(BeautyQEvaluationPolicy.measuredPasses == 3)
      assert(BeautyQEvaluationPolicy.concurrency == 1)
    }

    "own typed surface IDs" in {
      assert(BeautyQEvaluationPolicy.Variants.value == "variants")
      assert(BeautyQEvaluationPolicy.Providers.value == "providers")
      assert(BeautyQEvaluationPolicy.ServiceIntents.value == "service-intents")
    }

    "have valid surface IDs" in {
      assert(EvaluationSurfaceId.from(BeautyQEvaluationPolicy.Variants.value).isRight)
      assert(EvaluationSurfaceId.from(BeautyQEvaluationPolicy.Providers.value).isRight)
      assert(EvaluationSurfaceId.from(BeautyQEvaluationPolicy.ServiceIntents.value).isRight)
    }
  }
}
