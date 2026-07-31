package leaderboard.plugins

import distage.StandardAxis.Repo
import distage.config.ConfigModuleDef
import distage.{Axis, ModuleDef}
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
import leaderboard.search.gen2.{BeautyQGen2EmbeddingClient, BeautyQSearchGen2Bootstrap, BeautyQSearchGen2HttpService, BeautyQSearchGen2Runtime, BeautyQSearchGen2Startup, BeautyQSupplementStartup}
import leaderboard.seed.BeautyQSeedReady
import leaderboard.sql.SQL
import logstage.LogIO2
import zio.IO

import scala.annotation.unused

import java.time.{Clock => JClock, Duration}

object BeautySearchGen2PluginModules {

  def appShellConfigModule: ConfigModuleDef = new ConfigModuleDef {
    makeConfig[RawBeautyQGen2AppShellConfig]("beautyq-gen2-app-shell")
  }

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

  def appShellGraph: ModuleDef = new ModuleDef {
    include(commonBaselineGraph)
    include(enabledSupplementGraph(BeautyQSupplementStartup.Required, SupplementStartupPolicy.Required))
    include(enabledSupplementGraph(BeautyQSupplementStartup.Preferred, SupplementStartupPolicy.Preferred))
    include(disabledSupplementGraph)
  }

  private def commonBaselineGraph: ModuleDef = new ModuleDef {
    make[JClock].fromValue(JClock.systemUTC())

    make[BeautyQSearchSnapshotSource.Postgres[IO]].from {
      (seedReady: BeautyQSeedReady, sql: SQL[IO], clock: JClock) =>
        @unused val _edge = seedReady
        new BeautyQSearchSnapshotSource.Postgres[IO](sql, clock)
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

    make[BeautyQSearchApplication].from {
      (startup: BeautyQSearchGen2Startup) => startup.application
    }

    make[BeautyQSearchGen2Runtime].from {
      (startup: BeautyQSearchGen2Startup) => startup.runtime
    }
  }

  private def enabledSupplementGraph(
    choice: Axis.AxisChoice,
    policy: SupplementStartupPolicy,
  ): ModuleDef = new ModuleDef {
    tag(choice)

    make[SupplementStartupPolicy].fromValue(policy)

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
        policy: SupplementStartupPolicy,
        qdrantLifecycle: QdrantGenerationLifecycle,
        embedding: BeautyQGen2EmbeddingClient,
      ) => BeautyQSearchGen2Bootstrap.make(materializer, elasticsearch, policy, qdrantLifecycle, embedding)
    }

    make[BeautyQSearchGen2Startup].fromResource {
      (
        bootstrap: BeautyQSearchGen2Bootstrap,
        qdrantCandidateService: QdrantCandidateService,
        log: LogIO2[IO],
      ) =>
        BeautyQSearchGen2Startup.lifecycle(bootstrap, qdrantCandidateService, log)
    }
  }

  private def disabledSupplementGraph: ModuleDef = new ModuleDef {
    tag(BeautyQSupplementStartup.Disabled)

    make[SupplementStartupPolicy].fromValue(SupplementStartupPolicy.Disabled)

    make[BeautyQSearchGen2Bootstrap].from {
      (
        materializer: BeautyQVariantMaterializer[IO],
        elasticsearch: BeautyQElasticsearchBaselineService,
      ) =>
        BeautyQSearchGen2Bootstrap.makeBaselineOnly(
          materializer,
          elasticsearch,
          SupplementStartupPolicy.Disabled,
        )
    }

    make[BeautyQSearchGen2Startup].fromResource {
      (
        bootstrap: BeautyQSearchGen2Bootstrap,
        log: LogIO2[IO],
      ) =>
        BeautyQSearchGen2Startup.lifecycleBaselineOnly(bootstrap, log)
    }
  }

  def routeComposition: ModuleDef = new ModuleDef {
    make[BeautySearchGen2TapirEndpoints].fromValue(BeautySearchGen2TapirEndpoints)
    make[BeautySearchGen2Service[IO]].from { (runtime: BeautyQSearchGen2Runtime) => new BeautyQSearchGen2HttpService(runtime) }
    make[BeautySearchGen2Api[IO]]
    many[HttpApi[IO]].ref[BeautySearchGen2Api[IO]]
  }
}
