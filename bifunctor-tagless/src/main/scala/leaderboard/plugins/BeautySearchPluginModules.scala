package leaderboard.plugins

import cats.effect.Async
import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.api.{BeautySearchApi, BeautySearchServingGate, EsLifecycleStatusApi, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, EsLifecycleStatusTapirEndpoints}
import leaderboard.search.BeautySearchService
import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessTransition

object BeautySearchPluginModules {
  def api[F[+_, +_]: TagKK: Error2]: ModuleDef = new ModuleDef {
    // Opt-in Beauty search API contribution. Not included by LeaderboardPlugin default modules.
    make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
    // Disabled-by-default runtime serving gate: `/beauty-search` behavior stays unchanged.
    make[BeautySearchServingGate].fromValue(BeautySearchServingGate.disabled)
    make[BeautySearchApi[F]].from {
      (
        service: BeautySearchService[F],
        endpoints: BeautySearchTapirEndpoints,
        servingGate: BeautySearchServingGate,
        async: Async[F[Throwable, _]],
      ) =>
        new BeautySearchApi[F](service, endpoints, servingGate)(implicitly[Error2[F]], async)
    }
    many[HttpApi[F]].weak[BeautySearchApi[F]]
  }

  def operatorVisibilityApi[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[EsLifecycleStatusTapirEndpoints].fromValue(EsLifecycleStatusTapirEndpoints)
    make[EsLifecycleStatusApi[F]].from {
      (
        transition: ElasticsearchStartupReadinessTransition,
        endpoints: EsLifecycleStatusTapirEndpoints,
        async: Async[F[Throwable, _]],
      ) =>
        new EsLifecycleStatusApi[F](transition, endpoints)(async)
    }
    many[HttpApi[F]].weak[EsLifecycleStatusApi[F]]
  }
}
