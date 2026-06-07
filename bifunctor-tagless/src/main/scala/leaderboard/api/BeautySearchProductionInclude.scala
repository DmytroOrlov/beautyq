package leaderboard.api

final case class BeautySearchProductionIncludedApis[F[+_, +_]](
  apis: List[HttpApi[F]]
)

object BeautySearchProductionIncludedApis {
  def empty[F[+_, +_]]: BeautySearchProductionIncludedApis[F] =
    BeautySearchProductionIncludedApis(Nil)

  def fromHandle[F[+_, +_]](
    handle: BeautySearchProductionInclusionHandle[F],
  ): BeautySearchProductionIncludedApis[F] =
    BeautySearchProductionIncludedApis(handle.toOption.toList)
}
