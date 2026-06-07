package leaderboard.plugins

import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.search.BeautySearchService

object BeautySearchPluginModules {
  def api[F[+_, +_]: TagKK: Error2]: ModuleDef = new ModuleDef {
    // Opt-in Beauty search API contribution. Not included by LeaderboardPlugin default modules.
    make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
    make[BeautySearchApi[F]].from {
      (
        service: BeautySearchService[F],
        endpoints: BeautySearchTapirEndpoints,
        tapirHttpSupport: TapirHttpSupport[F],
      ) =>
        new BeautySearchApi[F](service, endpoints, tapirHttpSupport)
    }
    many[HttpApi[F]].weak[BeautySearchApi[F]]
  }
}
