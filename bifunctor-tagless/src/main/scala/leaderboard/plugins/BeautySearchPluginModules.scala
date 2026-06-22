package leaderboard.plugins

import cats.effect.Async
import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.api.{BeautySearchApi, BeautySearchServingGate, EsLifecycleStatusApi, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, EsLifecycleStatusTapirEndpoints}
import leaderboard.search.BeautySearchService
import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessTransition

object BeautySearchPluginModules {
  def api[F[+_, +_]: TagKK: Error2]: ModuleDef =
    // Production/default contribution: delegates to the disabled gate, so `/beauty-search` behavior stays unchanged.
    apiWithServingGate[F](BeautySearchServingGate.disabled)

  // Narrow local/dev/test module surface for selecting an explicit `BeautySearchServingGate` state.
  // The production default (`api[F]`) delegates here with `BeautySearchServingGate.disabled`; this does not
  // introduce environment/config/CLI parsing, change the default backend, activate Qdrant, or add fallback.
  def apiWithServingGate[F[+_, +_]: TagKK: Error2](servingGate: BeautySearchServingGate): ModuleDef = new ModuleDef {
    // Opt-in Beauty search API contribution. Not included by LeaderboardPlugin default modules.
    make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
    // Explicit runtime serving gate selection; defaults to disabled via `api[F]`.
    make[BeautySearchServingGate].fromValue(servingGate)
    make[BeautySearchApi[F]].from {
      (
        service: BeautySearchService[F],
        endpoints: BeautySearchTapirEndpoints,
        gate: BeautySearchServingGate,
        async: Async[F[Throwable, _]],
      ) =>
        new BeautySearchApi[F](service, endpoints, gate)(implicitly[Error2[F]], async)
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
