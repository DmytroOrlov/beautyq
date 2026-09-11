package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.gen2.contract.PageSize
import leaderboard.search.gen2.eval.{EvaluationSurfaceId, EvaluationCutoffs}

object BeautyQEvaluationPolicy {
  val CurrentVersion: String = "beautyq-evaluation-policy-v1"

  val Variants: EvaluationSurfaceId = EvaluationSurfaceId.from("variants").getOrElse(throw new AssertionError("invalid variants surface id"))
  val Providers: EvaluationSurfaceId = EvaluationSurfaceId.from("providers").getOrElse(throw new AssertionError("invalid providers surface id"))
  val ServiceIntents: EvaluationSurfaceId = EvaluationSurfaceId.from("service-intents").getOrElse(throw new AssertionError("invalid service-intents surface id"))

  val activeSurfaces: Vector[EvaluationSurfaceId] = Vector(
    Variants,
    Providers,
    ServiceIntents,
  )

  val cutoffs: EvaluationCutoffs = EvaluationCutoffs.from(Vector(1, 3, 5, 10)).getOrElse(throw new AssertionError("invalid default cutoffs"))

  val pageSize: PageSize = PageSize.from(20).getOrElse(throw new AssertionError("expected canonical evaluation page size"))

  val warmupPasses: Int = 1
  val measuredPasses: Int = 3
  val concurrency: Int = 1

  // Protected holdout and protected acceptance machinery exist and are tracked, but the gate
  // checks aggregate protected quality, not Qdrant marginal product value; no accepted relevance
  // thresholds currently establish that marginal value.
}
