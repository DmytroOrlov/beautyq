# System Map

## Repository Layout

Implemented/current:

- Root build files: `build.sbt` and `project/plugins.sbt`.
- Main module source: `bifunctor-tagless/src/main/scala/leaderboard`.
- Main resources: `bifunctor-tagless/src/main/resources`, including `common-reference.conf` and the BeautyQ seed JSON.
- Tests: `bifunctor-tagless/src/test/scala/leaderboard`.
- Existing architecture/task docs: `docs/*.md` plus `beautyq_search_eval_plan_v1.md`.
- Exhaustive pass-1 symbol inventory: `docs/codebase-review/INVENTORY.md`.

The repository still has upstream `distage-example` shape in `README.md`, while BeautyQ-specific code and docs are present in `bifunctor-tagless` and `docs`.

## What Starts The App

Implemented/current:

- App launchers are objects in `bifunctor-tagless/src/main/scala/leaderboard/LeaderboardRole.scala`.
- `MainBase` extends `RoleAppMain.LauncherBIO[IO]`, selects plugins with `PluginConfig.cached(pluginsPackage = "leaderboard.plugins")` outside Graal native image, and sets default activation to `Scene.Provided` plus `Mode.Prod`.
- `MainDummy`, `MainProdDocker`, and `MainProd` launch the composite `LeaderboardRole` with different `Repo`/`Scene` activation.
- Per-slice launchers exist for ladder, category, service, master, master-location, master-service-offer, master-service-offer-variant, and profile.
- `GenericLauncher` starts with no required roles; `WiringTest.scala` uses `SpecWiring(GenericLauncher)`.

## Runtime Roles

Implemented/current:

- `LadderRole`, `ProfileRole`, `CategoryRole`, `ServiceRole`, `MasterRole`, `MasterLocationRole`, `MasterServiceOfferRole`, and `MasterServiceOfferVariantRole` each depend on their matching `*Api`, `HttpServer`, and logging.
- `LeaderboardRole` composes the Beauty domain roles for category, service, master, master-location, master-service-offer, and master-service-offer-variant. Its log message also mentions Profile APIs, but its inspected constructor does not visibly include `ProfileRole[F]`.
- Role descriptors are bound in `LeaderboardPlugin.modules.roles` using `makeRole[...]`.

Unclear:

- Whether the missing `ProfileRole[F]` constructor dependency in `LeaderboardRole` is intentional, historical, or an oversight. The `ProfileRole` itself and per-profile launchers exist.

## Distage Plugin Structure

Implemented/current in `bifunctor-tagless/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala`:

- `modules.roles`: binds role services and bundled `help`/`configwriter`.
- `modules.api`: binds Tapir endpoint singletons, all role-backed `*Api` adapters, weak `many[HttpApi[F]]`, `HttpServer.Impl[F]`, and `Ranks.Impl[F]`; each API adapter uses the default `Http4sServerInterpreter` directly.
- `modules.repoDummy`: tagged `Repo.Dummy`; binds in-memory/dummy repositories.
- `modules.repoProd`: tagged `Repo.Prod`; binds Postgres repositories, `SQL.Impl[F]`, `TransactorResource`, and `PortCheck`.
- `modules.seed`: binds `BeautyQSeedLoader.ResourceLoader` and `BeautyQSeedInserter.Impl[F]`.
- `modules.seedProd`: tagged `Mode.Prod`; binds `BeautyQSeedReady.Noop[F]`.
- `modules.seedTest`: tagged `Mode.Test`; binds `BeautyQSeedReady.LoadAndInsert[F]`.
- `modules.configs` and `modules.prodConfigs`: bind Postgres config from `common-reference.conf`.

Integration-test-only/resource plugins:

- `ElasticsearchDockerPlugin.scala` binds `ElasticsearchDocker.Container` and `ElasticsearchPortCfg`.
- `QdrantDockerPlugin.scala` binds `QdrantDocker.Container` and `QdrantPortCfg`.
- These plugins are discoverable under `leaderboard.plugins`, but `LeaderboardPlugin.modules.api` does not bind search services that consume them in production.

## Layer Dependencies

Implemented/current:

```text
Role launcher
  -> Distage plugin modules
    -> HTTP roles
      -> API adapters
        -> repository/service interfaces
          -> dummy or Postgres repositories
            -> SQL/Transactor or in-memory state

Search tests/experiments
  -> seed or repositories
  -> search catalog snapshot
  -> VariantSearchDocument
  -> parser + DSL/spec
  -> in-memory, Elasticsearch interpreter/test client, Qdrant semantic, or hybrid experiment path
```

The runtime HTTP app path does not currently include a search API based on pass-2 searches for `SearchApi`, `/search`, and search bindings in `LeaderboardPlugin.scala`.

## API Layer Structure

Implemented/current:

- API adapters live in `bifunctor-tagless/src/main/scala/leaderboard/api`.
- Pure Tapir endpoint contracts live in `bifunctor-tagless/src/main/scala/leaderboard/http/tapir`.
- `HttpServer` combines the weak set of `HttpApi[F]` implementations.
- Route-level behavior is protected by `*ApiHttpContractSuite.scala` tests.

## Repository Layer Structure

Implemented/current:

- Repository traits and implementations live in `bifunctor-tagless/src/main/scala/leaderboard/repo`.
- Each important domain aggregate has a repository trait and `Dummy`/`Postgres` implementation.
- Postgres schema is created in repository resource constructors with Doobie SQL.
- Parent repository constructor dependencies encode FK table creation order, as documented in `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md`.

## Seed Loading Structure

Implemented/current:

- Seed file: `bifunctor-tagless/src/main/resources/seed/wandsbek_hamburg_beauty_services_seed_ready.json`.
- Decoder: `BeautyQSeedData` in `seed/BeautyQSeedData.scala`.
- Loader: `BeautyQSeedLoader.ResourceLoader` in `seed/BeautyQSeedLoader.scala`.
- Inserter: `BeautyQSeedInserter.Impl` in `seed/BeautyQSeedInserter.scala`.
- Readiness marker: `BeautyQSeedReady.Noop` for `Mode.Prod`, `BeautyQSeedReady.LoadAndInsert` for `Mode.Test`.

Resolved mismatch:

- `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` in `search/document/VariantSearchDocument.scala` now takes `BeautyQSeedReady` directly in its constructor, resolving the previously documented dependency-rule mismatch for seed-json plus repository snapshot paths.

## Search Package Structure

Implemented/current but not production-wired unless noted:

- `leaderboard.search`: input, parsed intent, response DTOs, `BeautySearchBackend`, `BeautySearchService.Impl`.
- `leaderboard.search.dsl`: `BeautySearchSpec`, fields, constraints, facets, vector specs, `BeautySearchSpecV1`.
- `leaderboard.search.document`: catalog snapshots and `VariantSearchDocument`.
- `leaderboard.search.parser`: `BeautySearchIntentParser`.
- `leaderboard.search.inmemory`: pure in-memory backend.
- `leaderboard.search.elasticsearch`: mapping, ingestion, request, and response interpreters.
- `leaderboard.search.qdrant`: Qdrant client/adapters, indexing, semantic candidate search, readiness, experiments, and benchmarks.
- `leaderboard.search.hybrid`: generic hybrid result container, BeautyQ projection/response pipeline, and experimental service/backend.
- `leaderboard.search.semantic` and `leaderboard.search.lexical`: generic retrieval seams.
- `leaderboard.search.eval`: eval suite loader/scorer.

Production-wired/current:

- No `BeautySearchService`, `BeautySearchBackend`, search API, search role, Elasticsearch backend, Qdrant backend, or hybrid service binding was found in `LeaderboardPlugin.scala` or `LeaderboardRole.scala`.

Test-only/integration/manual:

- Elasticsearch real-resource search is exercised through `BeautySearchElasticsearchIntegrationSpec.scala` and `ElasticsearchTestClient.scala`.
- Qdrant real-resource paths are exercised through Docker or env-gated tests, not through production app wiring.
