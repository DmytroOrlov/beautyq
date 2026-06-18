package leaderboard.plugins

import cats.effect.Async
import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.search.BeautySearchService

object BeautySearchPluginModules {
  def api[F[+_, +_]: TagKK: Error2]: ModuleDef = new ModuleDef {
    // Opt-in Beauty search API contribution. Not included by LeaderboardPlugin default modules.
    make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
    make[BeautySearchApi[F]].from {
      (
        service: BeautySearchService[F],
        endpoints: BeautySearchTapirEndpoints,
        async: Async[F[Throwable, _]],
      ) =>
        new BeautySearchApi[F](service, endpoints)(implicitly[Error2[F]], async)
    }
    many[HttpApi[F]].weak[BeautySearchApi[F]]
  }
}
