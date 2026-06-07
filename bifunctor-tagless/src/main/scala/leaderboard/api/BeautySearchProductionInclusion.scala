package leaderboard.api

sealed trait BeautySearchProductionInclusionActivation extends Product with Serializable

object BeautySearchProductionInclusionActivation {
  case object Disabled extends BeautySearchProductionInclusionActivation
  case object Enabled extends BeautySearchProductionInclusionActivation

  val default: BeautySearchProductionInclusionActivation = Disabled
}

final case class BeautySearchProductionInclusionHandle[F[+_, +_]](
  api: Option[BeautySearchApi[F]]
) {
  def isEnabled: Boolean = api.isDefined
  def toOption: Option[BeautySearchApi[F]] = api
}

object BeautySearchProductionInclusionHandle {
  def disabled[F[+_, +_]]: BeautySearchProductionInclusionHandle[F] =
    BeautySearchProductionInclusionHandle(None)

  // Disabled-by-default production inclusion boundary only; this does not wire the production app route.
  def buildIfEnabled[F[+_, +_]](
    activation: BeautySearchProductionInclusionActivation,
    api: => BeautySearchApi[F],
  ): BeautySearchProductionInclusionHandle[F] =
    activation match {
      case BeautySearchProductionInclusionActivation.Disabled =>
        disabled[F]
      case BeautySearchProductionInclusionActivation.Enabled =>
        val builtApi = api
        BeautySearchProductionInclusionHandle(Some(builtApi))
    }
}
