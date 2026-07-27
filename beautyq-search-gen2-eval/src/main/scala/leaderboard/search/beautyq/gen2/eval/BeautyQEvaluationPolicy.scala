package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.gen2.eval.{EvaluationSurfaceId, EvaluationCutoffs}

object BeautyQEvaluationPolicy {
  val Variants: EvaluationSurfaceId = EvaluationSurfaceId.from("variants").getOrElse(throw new AssertionError("invalid variants surface id"))
  val Providers: EvaluationSurfaceId = EvaluationSurfaceId.from("providers").getOrElse(throw new AssertionError("invalid providers surface id"))
  val ServiceIntents: EvaluationSurfaceId = EvaluationSurfaceId.from("service-intents").getOrElse(throw new AssertionError("invalid service-intents surface id"))

  val activeSurfaces: Vector[EvaluationSurfaceId] = Vector(
    Variants,
    Providers,
    ServiceIntents,
  )

  val cutoffs: EvaluationCutoffs = EvaluationCutoffs.from(Vector(1, 3, 5, 10)).getOrElse(throw new AssertionError("invalid default cutoffs"))

  // No thresholds accepted in Q1
  // No protected holdout accepted in Q1
}
