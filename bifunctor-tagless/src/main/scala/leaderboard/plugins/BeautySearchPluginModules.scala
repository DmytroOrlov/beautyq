package leaderboard.plugins

import cats.effect.Async
import distage.{Id, ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.api.{BeautySearchApi, BeautySearchServingGate, EsLifecycleStatusApi, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, EsLifecycleStatusTapirEndpoints}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.hybrid.{ExperimentalHybridSearchBackend, QdrantVariantSupplementPolicy}
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.routing.SearchBackendRoute
import leaderboard.search.semantic.{SemanticCandidateBackend, VariantSearchDocumentLookup}
import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessTransition
import leaderboard.search.{BeautySearchBackend, BeautySearchService}

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

  // Disabled-by-default, explicit opt-in supplement service: wraps an already-bound ES lexical
  // backend with a no-worsening Qdrant variant supplement policy (`QdrantVariantSupplementPolicy`).
  // The lexical Elasticsearch backend (qualified `@Id("qdrantSupplementLexicalElasticsearch")`),
  // the semantic candidate backend, and the document lookup must be supplied by whichever module
  // assembles this one; this module does not bind ES/Qdrant client or indexing infrastructure, and
  // is not included by `api[F]` / `LeaderboardPlugin` default modules.
  def qdrantVariantSupplementExplicitOptIn[F[+_, +_]: TagKK: Error2](
    supplementPolicy: QdrantVariantSupplementPolicy
  ): ModuleDef = new ModuleDef {
    make[BeautySearchSpec].fromValue(BeautySearchSpecV1.spec)
    make[BeautySearchIntentParser].from((spec: BeautySearchSpec) => new BeautySearchIntentParser(spec))
    make[BeautySearchBackend[F]].from {
      (
        spec: BeautySearchSpec,
        lexicalBackend: BeautySearchBackend[F] @Id("qdrantSupplementLexicalElasticsearch"),
        semanticBackend: SemanticCandidateBackend[F],
        documentLookup: VariantSearchDocumentLookup[F],
      ) =>
        new ExperimentalHybridSearchBackend[F](
          spec,
          lexicalBackend,
          (_, _) => SearchBackendRoute.ElasticsearchWithQdrantVariantSupplement,
          semanticBackend,
          documentLookup,
          supplementPolicy,
        )
    }
    make[BeautySearchService[F]].from {
      (parser: BeautySearchIntentParser, backend: BeautySearchBackend[F]) =>
        new BeautySearchService.Impl[F](parser, backend)
    }
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
