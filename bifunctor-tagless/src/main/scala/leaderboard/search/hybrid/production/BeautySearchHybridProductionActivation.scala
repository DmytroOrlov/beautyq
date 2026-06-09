package leaderboard.search.hybrid.production

import leaderboard.search.hybrid.control.{BeautySearchHybridDecisionEvaluator, BeautySearchHybridServingPolicy}

sealed trait BeautySearchHybridProductionActivation

object BeautySearchHybridProductionActivation {
  case object Disabled extends BeautySearchHybridProductionActivation

  final case class Enabled(
    policy: BeautySearchHybridServingPolicy,
  ) extends BeautySearchHybridProductionActivation

  val default: BeautySearchHybridProductionActivation = Disabled
}

final case class BeautySearchHybridProductionHandle[F[+_, +_]](
  evaluator: Option[BeautySearchHybridDecisionEvaluator[F]],
)

object BeautySearchHybridProductionHandle {
  def disabled[F[+_, +_]]: BeautySearchHybridProductionHandle[F] =
    BeautySearchHybridProductionHandle(None)

  def enabled[F[+_, +_]](
    evaluator: BeautySearchHybridDecisionEvaluator[F],
  ): BeautySearchHybridProductionHandle[F] =
    BeautySearchHybridProductionHandle(Some(evaluator))
}
