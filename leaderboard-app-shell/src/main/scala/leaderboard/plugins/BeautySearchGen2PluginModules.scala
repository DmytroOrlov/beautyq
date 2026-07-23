package leaderboard.plugins

import distage.StandardAxis.Repo
import distage.config.ConfigModuleDef
import distage.ModuleDef
import leaderboard.api.{BeautySearchGen2Api, BeautySearchGen2Service, HttpApi}
import leaderboard.config.{BeautyQGen2AppShellConfig, ElasticsearchPortCfg, QdrantGen2PortCfg, RawBeautyQGen2AppShellConfig}
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshot, BeautyQSearchSnapshotSource, BeautyQVariantMaterializer, SnapshotLoadError}
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.embedding.LlamaCppEmbeddingClientConfig
import leaderboard.search.gen2.core.materialization.SearchSnapshotSource
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*
import leaderboard.search.gen2.{BeautyQGen2EmbeddingClient, BeautyQSearchGen2Bootstrap, BeautyQSearchGen2HttpService, BeautyQSearchGen2Runtime, BeautyQSearchGen2Startup}
import leaderboard.sql.SQL
import zio.IO

import java.time.{Clock => JClock, Duration}

/** The canonical Gen2 route composition. Included by [[LeaderboardPlugin]] as the default
  * `/beauty-search` route. The Brick 8A executable graph composes the production components in one
  * startup resource and binds the trusted application, readiness, and runtime derived from that
  * same activation; the HTTP route stays unavailable until the resource acquire succeeds. */
object BeautySearchGen2PluginModules {

  /** Raw HOCON-bound config; validation runs at the DI boundary (see [[validatedAppShellConfigModule]])
    * so a non-positive timeout or batching value produces a typed
    * [[leaderboard.config.BeautyQGen2AppShellConfigError]] rather than a downstream IllegalStateException. */
  def appShellConfigModule: ConfigModuleDef = new ConfigModuleDef {
    makeConfig[RawBeautyQGen2AppShellConfig]("beautyq-gen2-app-shell")
  }

  /** Boundary validation: the only path that turns the HOCON-bound raw config into the validated
    * [[BeautyQGen2AppShellConfig]]. Downstream bindings depend only on the validated value. */
  def validatedAppShellConfigModule: ModuleDef = new ModuleDef {
    include(appShellConfigModule)
    make[BeautyQGen2AppShellConfig].from { (raw: RawBeautyQGen2AppShellConfig) =>
      BeautyQGen2AppShellConfig.validateAtBoundary(raw)
    }
  }

  def api: ModuleDef = new ModuleDef {
    tag(Repo.Prod)
    include(BeautySearchGen2PluginModules.validatedAppShellConfigModule)
    include(BeautySearchGen2PluginModules.appShellGraph)
    include(BeautySearchGen2PluginModules.routeComposition)
  }

  /** The Brick 8A native application graph: one startup resource owns the
    * load/materialize/activate sequence; the application and runtime derive from that single
    * trusted activation. No second snapshot, no second materialization, no second backend
    * activation, and no independent readiness computation are reachable.
    *
    * Each typed backend client (Elasticsearch / Qdrant / embedding) is constructed at the edge
    * from its own endpoint and timeout values, so no raw unnamed Gen2 HTTP-client binding is
    * registered more than once. */
  def appShellGraph: ModuleDef = new ModuleDef {
    make[JClock].fromValue(JClock.systemUTC())

    make[BeautyQSearchSnapshotSource.Postgres[IO]].from {
      (sql: SQL[IO], clock: JClock) => new BeautyQSearchSnapshotSource.Postgres[IO](sql, clock)
    }

    make[SearchSnapshotSource[IO, SnapshotLoadError, BeautyQSearchSnapshot]].from {
      (postgres: BeautyQSearchSnapshotSource.Postgres[IO]) => postgres
    }

    make[BeautyQVariantMaterializer[IO]].from {
      (source: SearchSnapshotSource[IO, SnapshotLoadError, BeautyQSearchSnapshot]) =>
        new BeautyQVariantMaterializer.FromSnapshotSource[IO](source)
    }

    make[ElasticsearchGen2JsonClient].from {
      (esPort: ElasticsearchPortCfg, cfg: BeautyQGen2AppShellConfig) =>
        val endpoint = ElasticsearchGen2Endpoint
          .fromString(s"http://${esPort.host}:${esPort.port}")
          .getOrElse(throw new IllegalStateException(s"managed Gen2 Elasticsearch endpoint must be valid: ${esPort.host}:${esPort.port}"))
        val transport = ElasticsearchGen2TransportConfig
          .create(endpoint, Duration.ofMillis(cfg.connectTimeout.toMillis), Duration.ofMillis(cfg.requestTimeout.toMillis))
          .getOrElse(throw new IllegalStateException("managed Gen2 Elasticsearch transport config is invalid"))
        ElasticsearchGen2JsonClient.jdk(transport)
    }

    make[ElasticsearchBulkBatchingPolicy].from {
      (cfg: BeautyQGen2AppShellConfig) =>
        ElasticsearchBulkBatchingPolicy
          .create(cfg.bulkMaxActions, cfg.bulkMaxBytes)
          .getOrElse(throw new IllegalStateException(s"managed Gen2 Elasticsearch batching is invalid: actions=${cfg.bulkMaxActions} bytes=${cfg.bulkMaxBytes}"))
    }

    make[BeautyQElasticsearchBaselineService].from {
      (client: ElasticsearchGen2JsonClient, clock: JClock, batching: ElasticsearchBulkBatchingPolicy) =>
        BeautyQElasticsearchBaselineService
          .make(client, clock, batching)
          .getOrElse(throw new IllegalStateException("managed Gen2 Elasticsearch baseline service init failed"))
    }

    make[QdrantGen2Client].from {
      (qdrantPort: QdrantGen2PortCfg, cfg: BeautyQGen2AppShellConfig) =>
        val endpoint = Gen2HttpEndpoint
          .fromString(s"http://${qdrantPort.host}:${qdrantPort.port}")
          .getOrElse(throw new IllegalStateException(s"managed Gen2 Qdrant endpoint must be valid: ${qdrantPort.host}:${qdrantPort.port}"))
        val transport = Gen2HttpTransportConfig
          .create(endpoint, Duration.ofMillis(cfg.connectTimeout.toMillis), Duration.ofMillis(cfg.requestTimeout.toMillis))
          .getOrElse(throw new IllegalStateException("managed Gen2 Qdrant transport config is invalid"))
        QdrantGen2Client.fromTransport(Gen2JsonHttpClient.jdk(transport))
    }

    make[QdrantGenerationLifecycle].from { (client: QdrantGen2Client) =>
      BeautyQQdrantRuntime
        .lifecycle(client)
        .getOrElse(throw new IllegalStateException("managed Gen2 Qdrant lifecycle init failed"))
    }

    make[QdrantCandidateService].from { (client: QdrantGen2Client) =>
      BeautyQQdrantRuntime
        .candidateService(client)
        .getOrElse(throw new IllegalStateException("managed Gen2 Qdrant candidate service init failed"))
    }

    make[BeautyQGen2EmbeddingClient].from {
      (cfg: BeautyQGen2AppShellConfig, embedding: LlamaCppEmbeddingClientConfig) =>
        val endpoint = Gen2HttpEndpoint
          .fromString(embedding.baseUrl)
          .getOrElse(throw new IllegalStateException(s"managed Gen2 embedding endpoint must be valid: ${embedding.baseUrl}"))
        val transport = Gen2HttpTransportConfig
          .create(endpoint, Duration.ofMillis(cfg.connectTimeout.toMillis), Duration.ofMillis(cfg.requestTimeout.toMillis))
          .getOrElse(throw new IllegalStateException("managed Gen2 embedding transport config is invalid"))
        BeautyQGen2EmbeddingClient.fromTransport(Gen2JsonHttpClient.jdk(transport), embedding.endpointPath)
    }

    make[BeautyQSearchGen2Bootstrap].from {
      (
        materializer: BeautyQVariantMaterializer[IO],
        elasticsearch: BeautyQElasticsearchBaselineService,
        qdrantLifecycle: QdrantGenerationLifecycle,
        embedding: BeautyQGen2EmbeddingClient,
      ) => BeautyQSearchGen2Bootstrap.make(materializer, elasticsearch, qdrantLifecycle, embedding)
    }

    make[BeautyQSearchGen2Startup].fromResource {
      (
        bootstrap: BeautyQSearchGen2Bootstrap,
        qdrantCandidateService: QdrantCandidateService,
      ) =>
        BeautyQSearchGen2Startup.lifecycle(bootstrap, qdrantCandidateService)
    }

    make[BeautyQSearchApplication].from {
      (startup: BeautyQSearchGen2Startup) => startup.application
    }

    make[BeautyQSearchGen2Runtime].from {
      (startup: BeautyQSearchGen2Startup) => startup.runtime
    }
  }

  /** The route adapter binds only after the startup resource acquire succeeds, so the HTTP route
    * stays unavailable while bootstrap, activation, or readiness is still in progress. */
  def routeComposition: ModuleDef = new ModuleDef {
    make[BeautySearchGen2TapirEndpoints].fromValue(BeautySearchGen2TapirEndpoints)
    make[BeautySearchGen2Service[IO]].from { (runtime: BeautyQSearchGen2Runtime) => new BeautyQSearchGen2HttpService(runtime) }
    make[BeautySearchGen2Api[IO]]
    many[HttpApi[IO]].ref[BeautySearchGen2Api[IO]]
  }
}
