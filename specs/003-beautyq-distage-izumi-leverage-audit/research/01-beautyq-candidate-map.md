# BeautyQ-Local Candidate Map (Feature 003, LANE A)

## Artifact self-description

| Field | Value |
| --- | --- |
| `path` | `specs/003-beautyq-distage-izumi-leverage-audit/research/01-beautyq-candidate-map.md` |
| `question` | What current BeautyQ-local infrastructure/mechanics are materially worth comparing against pinned Distage/Izumi capabilities? |
| `writer` | LANE A BeautyQ source mapper (`T002`–`T007`) |
| `consumers` | LANE B1/C/D/E (`T012`+) |
| `staleWhen` | BeautyQ source/build state or the canonical Navigation Entry Point moves off the anchors below |
| `stateId` | `50e7945-clean` (per `research/00-selected-state.md`) |
| `versionRef` | `io.7mind.izumi` `1.2.25` (per `research/00-selected-state.md`) |

Purpose: **mechanical mapping only**. No verdict, no classification, no recommendation.
The five headline labels do not appear as candidate verdicts in this artifact.

### State-drift disclosure (T002 entry check)

`research/00-selected-state.md` records `headSha=50e794584d68768403d5c45d6d7fc21c2db87f6c`
(`stateId=50e7945-clean`). LANE A observed a later `HEAD` caused solely by committing
feature-local research bookkeeping; the only delta was
`A specs/.../research/00-selected-state.md`. Non-research source/build state remained
byte/content-equivalent to the original selected snapshot, so candidate anchors remain
bound to `50e7945-clean`. The later research-only commit is execution bookkeeping, not
source-state drift.

Scope guardrails applied: only BeautyQ-local/BeautyQ-owned mechanics are promoted; generic
`search-gen2-*` kernel/backend types appear only as ownership/boundary context. Deleted
Wave-A/Wave-B ceremony is not reconstructed.

---

## Slice A1 — App Shell / Startup / Composition

Inspected (`leaderboard-app-shell/src/main/scala/**`): `plugins/LeaderboardPlugin.scala`,
`plugins/BeautySearchGen2PluginModules.scala`, `LeaderboardRole.scala`,
`search/gen2/BeautyQSearchGen2Bootstrap.scala`, `search/gen2/BeautyQSearchGen2Startup.scala`,
`search/gen2/BeautyQSupplementStartup.scala`, `search/gen2/BeautyQSearchGen2Runtime.scala`,
`search/gen2/BeautyQSearchGen2HttpService.scala`, `search/gen2/BeautyQGen2EmbeddingClient.scala`,
`config/BeautyQGen2AppShellConfig.scala`, `plugins/{Postgres,Elasticsearch,QdrantGen2}DockerPlugin.scala`,
`seed/BeautyQSeedInserter.scala`.

| Provisional key | Candidate area(s) | Owner module/package | Mechanic summary (no verdict) | `file:line` anchors | Recurrence | Current owner doc/type |
| --- | --- | --- | --- | --- | --- | --- |
| `A1-a` | startup/activation composition | `leaderboard-app-shell` · `leaderboard.plugins` | BeautyQ Gen2 composed into the app as a `PluginDef.include(...)` entry (`BeautySearchGen2PluginModules.api`) alongside the generic role/api modules; the supplement choice is an `Axis` (`BeautyQSupplementStartup` Required/Preferred/Disabled) selected by activation. | `LeaderboardPlugin.scala:28-42`; `BeautySearchGen2PluginModules.scala:39-51`; `BeautyQSupplementStartup.scala:5-9`; `LeaderboardRole.scala:684-688` | same axis realized by three tagged module defs at `BeautySearchGen2PluginModules.scala:105-206` | `BeautyQSearchGen2` (nav); spec |
| `A1-b` | graph-boundary / construction-boundary proofs | `leaderboard-app-shell` · `leaderboard.config` | Boundary validation: raw HOCON-bound config (`RawBeautyQGen2AppShellConfig`) is validated at the DI edge into `BeautyQGen2AppShellConfig`, throwing a typed `BeautyQGen2AppShellConfigException` instead of a distage default error. | `BeautyQGen2AppShellConfig.scala:37-85`; `BeautySearchGen2PluginModules.scala:28-37` | — | `BeautyQGen2AppShellConfig` |
| `A1-c` | lifecycle/readiness state; optional dependency/resource construction | `leaderboard-app-shell` · `leaderboard.plugins`, `leaderboard.seed` | Seed readiness is a `Lifecycle` resource selected by `Mode`/`Scene` axes: `Noop` under `Mode.Prod,Scene.Provided`, `LoadAndInsert` under `Mode.Prod,Scene.Managed` and `Mode.Test`; production startup must not auto-seed an external DB. | `LeaderboardPlugin.scala:155-176`; `BeautyQSeedInserter.scala:50-77` | — | `BeautyQSeedReady`; operations runbook |
| `A1-d` | optional dependency/resource construction; startup/activation composition | `leaderboard-app-shell` · `leaderboard.plugins` | Scene-gated managed Docker resources: `PostgresDocker`/`ElasticsearchDocker`/`QdrantGen2Docker` `ContainerDef`s plus `DockerSupportModule`, with port config derived from the spawned container's `availablePorts` (forces container start before the port binding). | `PostgresDockerPlugin.scala:15-36`; `ElasticsearchDockerPlugin.scala:13-47`; `QdrantGen2DockerPlugin.scala:14-47` | three parallel container modules | operations runbook |
| `A1-e` | graph-boundary / construction-boundary proofs; lifecycle/readiness | `leaderboard-app-shell` · `leaderboard.plugins` | Explicit construction/readiness ordering edge: the `BeautyQSearchSnapshotSource.Postgres` binding takes `BeautyQSeedReady` and discards it with `@unused val _edge = seedReady`, so the snapshot source construct depends on seed readiness. | `BeautySearchGen2PluginModules.scala:56-60` | — | `BeautyQSeedReady` |
| `A1-f` | startup/activation composition; lifecycle/readiness; immutable evidence/source attribution | `leaderboard-app-shell` · `leaderboard.search.gen2` | App-shell startup composition: `BeautyQSearchGen2Bootstrap` performs materialize→baseline/supplement activation and builds `BeautyQSearchStartupEvidence` (snapshot time, durations, activatedAt); `BeautyQSearchGen2Startup` is a `Lifecycle` resource that dispatches on `SupplementStartupPolicy` into healthy/degraded/limited status and emits a startup log line; `BeautyQSearchGen2Runtime` executes and carries `startupStatus`/`startupEvidence`; `BeautyQSearchGen2HttpService.encodeStatus` projects status JSON. | `BeautyQSearchGen2Bootstrap.scala:30-107`; `BeautyQSearchGen2Startup.scala:24-241`; `BeautyQSearchGen2Runtime.scala:14-42`; `BeautyQSearchGen2HttpService.scala:14-141`; `BeautySearchGen2PluginModules.scala:161-205` | healthy/degraded/limited branches share evidence fields | operations runbook; technical spec |

Slice A1 area outcomes:

- startup / activation composition → **MAPPED** (`A1-a`, `A1-d`, `A1-f`).
- graph-boundary / construction-boundary proofs → **MAPPED** (`A1-b`, `A1-e`).
- lifecycle / readiness state → **MAPPED** (`A1-c`, `A1-e`, `A1-f`).
- optional dependency / resource construction → **MAPPED** (`A1-c`, `A1-d`).
- operator-visible degradation / status → **MAPPED** (`A1-f`; JSON status/health projection).
- immutable evidence / source attribution → **MAPPED** (`A1-f`; `BeautyQSearchStartupEvidence`).

---

## Slice A2 — Wiring / Lifecycle

Inspected (`beautyq-search-gen2-wiring/src/main/scala/**`).
Generic `search-gen2-*` types appear only as the boundary BeautyQ composes over.

| Provisional key | Candidate area(s) | Owner module/package | Mechanic summary (no verdict) | `file:line` anchors | Recurrence | Current owner doc/type |
| --- | --- | --- | --- | --- | --- | --- |
| `A2-a` | lifecycle/readiness state; operator-visible degradation/status | `beautyq-search-gen2-wiring` · `...gen2.wiring` | Typed executable supplement-startup policy vocabulary `SupplementStartupPolicy` (`Required`/`Preferred`/`Disabled`) with stable codes, order, default, and a strict `fromStableCode` parser (empty/whitespace/unknown rejected). | `BeautyQSupplementReadinessPolicy.scala:7-41` | `BeautyQServingMode` (`:43-54`) is the paired vocabulary | technical spec; operations runbook |
| `A2-b` | operator-visible degradation/status; immutable evidence/source attribution | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `StartupServingStatus` aggregate binds policy + serving mode + condition + optional typed `Reason` + `restartRequired` + source/projected fingerprints + ES/Qdrant resource references; private constructors via `healthy`/`degraded`/`limited`; `supplementReady` derived. | `BeautyQSupplementReadinessPolicy.scala:56-154` | three status constructors | operations runbook |
| `A2-c` | lifecycle/readiness state; operator-visible degradation/status | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSupplementPolicy` is one executable classifier: pipeline failures and startup-activation failures classified into `Hard`/`Degradable` with `BeautyQDegradationReason` stable codes; also owns the append budget (`MaxAppended=1`, `appendOnly`). | `BeautyQSupplementPolicy.scala:10-243` (`:46-47` budget) | parallel pipeline-vs-startup classification ladders (`:49-233`) | technical spec |
| `A2-d` | optional dependency/resource construction; generation/source identity | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQQdrantRuntime` is the BeautyQ seam that turns reserved resource names into generic `QdrantGenerationLifecycle` / `QdrantCandidateService` configs, returning typed `Either` config errors. | `BeautyQQdrantRuntime.scala:6-24` | used for both lifecycle and candidate service | technical spec |
| `A2-e` | optional dependency/resource construction; lifecycle/readiness | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchGenerationApplication.activate` composes ES compile→activate with Qdrant prepare/embed/activate into an `Activation` aggregate carrying optional `qdrantGeneration` and optional typed `qdrantFailure`; `activateBaselineOnly` omits Qdrant entirely. | `BeautyQSearchGenerationApplication.scala:23-133` | `activate` vs `activateBaselineOnly` | technical spec |
| `A2-f` | optional dependency/resource construction; reusable domain/kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQElasticsearchBaselineService.make` lazily builds the generic `ElasticsearchGenerationLifecycle` + `ElasticsearchBaselineService` from resource names/batching, exposing one BeautyQ-owned baseline execution path (`searchBound`). | `BeautyQElasticsearchBaselineService.scala:27-76` | lifecycle constructed once and reused | technical spec |
| `A2-g` | optional dependency handling; operator-visible degradation/status | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchApplication` carries a private `SupplementCapability` (`Full` with embedding+qdrant, or `Baseline`) chosen at construction; `execute` routes to full orchestration or baseline-only. | `BeautyQSearchApplication.scala:20-88` | two private constructors `make` / `makeBaselineOnly` | technical spec |

Slice A2 area outcomes:

- lifecycle / readiness state → **MAPPED** (`A2-a`, `A2-c`, `A2-e`).
- optional dependency / resource construction → **MAPPED** (`A2-d`, `A2-e`, `A2-f`, `A2-g`).
- operator-visible degradation / status → **MAPPED** (`A2-a`, `A2-b`, `A2-c`, `A2-g`).
- immutable evidence / source attribution → **MAPPED** (`A2-b`).
- generation / source identity → **MAPPED** (`A2-d`).

---

## Slice A3 — Backend / Search / Runtime

BeautyQ-owned adaptation/composition around reusable Gen2 mechanics. Generic
`search-gen2-*` types are context only; promoted sites are BeautyQ-owned.

| Provisional key | Candidate area(s) | Owner module/package | Mechanic summary (no verdict) | `file:line` anchors | Recurrence | Current owner doc/type |
| --- | --- | --- | --- | --- | --- | --- |
| `A3-a` | reusable domain/kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQElasticsearchPolicy` binds the complete ES policy (index declaration, text fields/analyzers, query weights, operator, geo scoring, total-hits, default sort) and exposes `contributions`/`contractFingerprint` delegating to the single `value`. | `BeautyQElasticsearchPolicy.scala:17-67` | five text-field mappings at `:25-31`/`:41-47` | technical spec; domain authoring principles |
| `A3-b` | reusable domain/kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQQdrantPolicy` binds Qdrant policy (declaration, identity, embedding field, vector name, embedding-model identity, distance, retrieval topK). | `BeautyQQdrantPolicy.scala:9-29` | — | technical spec |
| `A3-c` | generation/source identity; ownership/firewall | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchGen2ResourceNames` owns the ES alias / physical index prefix / Qdrant alias / physical collection prefix namespace; names only, no lifecycle/client/config binding. | `BeautyQSearchGen2ResourceNames.scala:6-13` | four derived names | technical spec; operations runbook |
| `A3-d` | reusable domain/kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | Thin ES bindings: `BeautyQElasticsearchGeneration.compile` passes the policy + materialized docs to the generic compiler; `BeautyQElasticsearchBaseline.compileRequest`/`decodeResponse` delegate request compile and response decode to generic compiler/decoder. | `BeautyQElasticsearchGeneration.scala:16-19`; `BeautyQElasticsearchBaseline.scala:21-31` | — | technical spec |
| `A3-e` | reusable domain/kernel boundaries; operator-visible degradation/status | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQQdrantCandidatePipeline` binds the generic candidate pipeline to BeautyQ policy + hydration policy + `BeautyQCandidateProvenance`; private read-only `Result` couples evaluation with eligible/ineligible outcome. | `BeautyQQdrantCandidatePipeline.scala:10-73` | — | technical spec |
| `A3-f` | reusable domain/kernel boundaries; graph-boundary / construction-boundary proofs | `beautyq-search-gen2-wiring` · `...gen2.wiring` | Plan and candidate compilers: `BeautyQSearchPlanCompiler.compile` composes public-input resolution, constraint precedence, kernel prepare/assemble, cursor binding, and returns a private read-only `CompiledBeautyQSearchPlan`; `BeautyQCandidatePlanCompiler.compile` calls the semantic policy's `evaluate` once and returns a private read-only `CompiledCandidateEvaluation`. | `BeautyQSearchPlanCompiler.scala:39-122`; `BeautyQCandidatePlanCompiler.scala:24-60` | — | technical spec |
| `A3-g` | operator-visible degradation/status; immutable evidence/source attribution | `beautyq-search-gen2-wiring` · `...gen2.wiring` | Response projection: `BeautyQSearchResponseGen2Projector.project` derives hits/facets/groups/carousels/applied+suppressed filters/warnings plus `supplementStatus`/`servingMode`/`restartRequired`/degradation reason and `deriveWarnings`; diagnostic traces `BeautyQSearchPlanCompilationTrace`/`BeautyQCandidatePlanTrace` derive human-readable views from bound aggregate fields. | `BeautyQSearchResponseGen2.scala:32-354`; `BeautyQSearchPlanCompilationTrace.scala:14-56`; `BeautyQCandidatePlanTrace.scala:12-50` | two trace renderers | operations runbook |

Slice A3 area outcomes:

- reusable domain / kernel boundaries → **MAPPED** (`A3-a`, `A3-b`, `A3-d`, `A3-e`, `A3-f`).
- operator-visible degradation / status → **MAPPED** (`A3-e`, `A3-g`).
- immutable evidence / source attribution → **MAPPED** (`A3-g`).
- generation / source identity → **MAPPED** (`A3-c`).
- optional dependency / resource construction → **MAPPED** (`A2-d`,`A2-e`,`A2-f`; not re-listed here to avoid duplication).
- ownership / firewall constraints → **MAPPED** (`A3-c`; full treatment in A5).

---

## Slice A4 — Contract / Materialization / Boundaries

Inspected (`beautyq-search-gen2-contract/src/main/scala/**`,
`beautyq-search-gen2-materialization/src/main/scala/**`).

| Provisional key | Candidate area(s) | Owner module/package | Mechanic summary (no verdict) | `file:line` anchors | Recurrence | Current owner doc/type |
| --- | --- | --- | --- | --- | --- | --- |
| `A4-a` | reusable domain/kernel boundaries; graph-boundary / construction-boundary proofs; generation/source identity | `beautyq-search-gen2-contract` · `...gen2.contract` | `BeautyQSearchDeclarations` is the executable contract: `catalog.topology` source tree, `variants.Fields` built with the `searchFields` DSL, `variants.document`, and `request`/`intent`/`plan`/`candidate` branches holding direct typed policy references; `structure`/`renderStructure` derive a view from those values. | `BeautyQSearchDeclarations.scala:22-366` | `structure` (`:314-362`) is derived, not hand-maintained | technical spec; NEW_DOMAIN_ONBOARDING |
| `A4-b` | immutable evidence/source attribution; generation/source identity; graph-boundary proofs | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQSearchSnapshotSource.Postgres` reads every source table under one `SQL.readOnlyRepeatableRead` transaction (not per-repository transactions), validates referential rows, and returns a `VersionedSnapshot` with content fingerprint + `clock.instant()`; a private `SnapshotReadHook` exists only for the concurrent-mutation test. | `BeautyQSearchSnapshotSource.scala:16-228` | neutral `BeautyQSearchSnapshot` value at `BeautyQSearchSnapshot.scala:10-18` | technical spec |
| `A4-c` | immutable evidence/source attribution | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQSnapshotFingerprint` computes an order-independent canonical SHA-256 content fingerprint over source values using the generic canonical token writer; `BeautyQSnapshotCanonicalRows` supplies per-entity sort keys. | `BeautyQSnapshotFingerprint.scala:11-24`; `BeautyQSnapshotCanonicalRows.scala` | — | technical spec |
| `A4-d` | immutable evidence/source attribution; reusable kernel boundaries | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQProjectedDocumentsFingerprint.compute` delegates field traversal/ordering/hashing to the generic `SearchProjectedDocumentsFingerprint` with BeautyQ encoding + projection versions and document declaration; `BeautyQVariantMaterializer.FromSnapshotSource` delegates `load` to the generic `SearchMaterializer`. | `BeautyQProjectedDocumentsFingerprint.scala:10-20`; `BeautyQVariantMaterializer.scala:17-38` | — | technical spec |
| `A4-e` | reusable domain/kernel boundaries; immutable evidence/source attribution | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQVariantProjectionGen2.project` builds indexed lookups, accumulates all per-variant join/invariant/schema errors (no fail-fast), and builds a `VariantSearchDocumentGen2` only on empty error vector; error model in `BeautyQVariantProjectionError`/`BeautyQVariantProjectionErrors`. | `BeautyQVariantProjectionGen2.scala:16-213`; `BeautyQVariantProjectionError.scala` | — | technical spec |
| `A4-f` | reusable domain/kernel boundaries; ownership/firewall | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchGen2` is the canonical Navigation Entry Point: `contract`, `input`, `materialization`, `plan`, `elasticsearch`, `qdrant`, `supplement`, `application` expose typed handlers to the executable declaration. | `BeautyQSearchGen2.scala:11-59` | — | technical spec; domain authoring principles |

Slice A4 area outcomes:

- reusable domain / kernel boundaries → **MAPPED** (`A4-a`, `A4-d`, `A4-e`, `A4-f`).
- immutable evidence / source attribution → **MAPPED** (`A4-b`, `A4-c`, `A4-d`, `A4-e`).
- generation / source identity → **MAPPED** (`A4-a`, `A4-b`).
- graph-boundary / construction-boundary proofs → **MAPPED** (`A4-a`, `A4-b`).
- ownership / firewall constraints → **MAPPED** (`A4-f`; full treatment in A5).

---

## Slice A5 — Build / Firewall / Ownership

Inspected `build.sbt`, `project/*.sbt`, and `repo-core`, `leaderboard-core`, `app-http`,
`app-services` only as graph endpoints/context. Only BeautyQ-specific edges are mapped.

| Provisional key | Candidate area(s) | Owner | Mechanic summary (no verdict) | `file:line` anchors | Recurrence | Current owner doc/type |
| --- | --- | --- | --- | --- | --- | --- |
| `A5-a` | ownership/firewall constraints; build/module boundary mechanics | `build.sbt` | BeautyQ Gen2 independent module DAG: `beautyqSearchGen2Contract → (searchGen2Contract, repoCore, beautyqModel)`; `beautyqSearchGen2Materialization → (…, searchGen2Core, beautyqSearchRepositories, repoCore, beautyqModel)`; `beautyqSearchGen2Wiring → (contract, materialization, searchGen2Core, searchGen2Elasticsearch, searchGen2Qdrant)`; `beautyqSearchGen2Eval`; app-shell/app-http test-scope edges. | `build.sbt:180-252` | app-shell edges at `:242-252` | module firewall spec; technical spec |
| `A5-b` | ownership/firewall constraints | `search-gen2-contract` test | Generic main-source neutrality firewall: no `search-gen2-*` main source may contain `beautyq` (case-insensitive). | `SearchGen2ModuleFirewallSpec.scala:56-67` | `genericModuleDirs` at `:20-21` | module firewall spec |
| `A5-c` | ownership/firewall constraints | `search-gen2-contract` test | SQL confinement firewall: only `beautyq-search-gen2-materialization` may reference `leaderboard.sql.SQL`, and only the exact `leaderboard.sql.SQL` form; all other Gen2 modules must not touch `leaderboard.sql`. | `SearchGen2ModuleFirewallSpec.scala:110-133` | fail-closed import-pattern detector at `:39-54`, `:232-250` | module firewall spec |
| `A5-d` | ownership/firewall constraints | `search-gen2-contract` test | BeautyQ production package ownership firewall: no BeautyQ production source may declare a package under `leaderboard.search.gen2`/`.gen2.*`. | `SearchGen2ModuleFirewallSpec.scala:135-147` | — | module firewall spec |
| `A5-e` | ownership/firewall constraints; build/module boundary mechanics | `search-gen2-contract` test; `build.sbt` | Eval build-dependency isolation firewall: serving Gen2 projects must not depend on `beautyqSearchGen2Eval`; app-shell must reference it exactly once and only as `% "test->test"`. | `SearchGen2ModuleFirewallSpec.scala:79-108`; `build.sbt:242-252` | — | module firewall spec |
| `A5-f` | ownership/firewall constraints; build/module boundary mechanics | `search-gen2-contract` test | Root aggregate coverage firewall: every retained Gen2 and shared project must remain in the `distage-example` aggregate. | `SearchGen2ModuleFirewallSpec.scala:69-77`; `build.sbt:258-279` | `gen2AggregateSbtIds`/`retainedSharedAggregateSbtIds` at `:26-32` | module firewall spec |

Slice A5 area outcomes:

- ownership / firewall constraints → **MAPPED** (`A5-a`–`A5-f`).
- build/module boundary mechanics → **MAPPED** (`A5-a`, `A5-e`, `A5-f`).

---

## T007 — Consolidated candidate index

Duplicate/recurrent mechanics were reconciled across slices:

- Themes A1-f (app-shell startup lifecycle) and A2-b/A2-e (wiring status/activation) are
  distinct owners of the same startup story; kept separate rather than merged because each
  owns an independent source type.
- Themes A2-d/A2-e/A2-f (resource construction seams) and A3 resource-names identity are
  kept in their owner slices; A3 does not re-list A2 resource construction.
- Themes A3-g/A4-e/A4-f (derived views, projection, navigation root) share the
  single-executable-declaration recurrence; anchor list preserves each site.
- A5 firewall rows each own exactly one check family; no duplication.

Stable `candidateId` scheme: `C-NN`, assigned in consolidated order below. Slice keys
(`A<n>-<x>`) are retained as provenance. `stateId` is `50e7945-clean` for every row;
`versionRef` is `io.7mind.izumi 1.2.25` for every framework-adjacent row.

### Consolidated candidates

| candidateId | from | candidate area(s) | owner module/package | anchors | recurrenceSites | currentOwner |
| --- | --- | --- | --- | --- | --- | --- |
| `C-01` | `A1-a` | startup/activation composition | `leaderboard-app-shell` · `leaderboard.plugins` | `LeaderboardPlugin.scala:28-42`; `BeautySearchGen2PluginModules.scala:39-51`; `BeautyQSupplementStartup.scala:5-9`; `LeaderboardRole.scala:684-688` | `BeautySearchGen2PluginModules.scala:105-206` | `BeautyQSearchGen2` |
| `C-02` | `A1-b` | graph-boundary / construction-boundary proofs | `leaderboard-app-shell` · `leaderboard.config` | `BeautyQGen2AppShellConfig.scala:37-85`; `BeautySearchGen2PluginModules.scala:28-37` | — | `BeautyQGen2AppShellConfig` |
| `C-03` | `A1-c` | lifecycle/readiness; optional dependency/resource construction | `leaderboard-app-shell` · `leaderboard.plugins`,`leaderboard.seed` | `LeaderboardPlugin.scala:155-176`; `BeautyQSeedInserter.scala:50-77` | — | `BeautyQSeedReady` |
| `C-04` | `A1-d` | optional dependency/resource construction; startup composition | `leaderboard-app-shell` · `leaderboard.plugins` | `PostgresDockerPlugin.scala:15-36`; `ElasticsearchDockerPlugin.scala:13-47`; `QdrantGen2DockerPlugin.scala:14-47` | three container modules | operations runbook |
| `C-05` | `A1-e` | graph-boundary / construction-boundary proofs; lifecycle/readiness | `leaderboard-app-shell` · `leaderboard.plugins` | `BeautySearchGen2PluginModules.scala:56-60` | — | `BeautyQSeedReady` |
| `C-06` | `A1-f` | startup/activation composition; lifecycle/readiness; immutable evidence | `leaderboard-app-shell` · `leaderboard.search.gen2` | `BeautyQSearchGen2Bootstrap.scala:30-107`; `BeautyQSearchGen2Startup.scala:24-241`; `BeautyQSearchGen2Runtime.scala:14-42`; `BeautyQSearchGen2HttpService.scala:14-141` | healthy/degraded/limited branches | operations runbook |
| `C-07` | `A2-a` | lifecycle/readiness; operator-visible status | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSupplementReadinessPolicy.scala:7-41`; `:43-54` | — | technical spec |
| `C-08` | `A2-b` | operator-visible degradation/status; immutable evidence | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSupplementReadinessPolicy.scala:56-154` | three status constructors | operations runbook |
| `C-09` | `A2-c` | lifecycle/readiness; operator-visible degradation/status | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSupplementPolicy.scala:10-243` | `:46-47`; `:49-233` | technical spec |
| `C-10` | `A2-d` | optional dependency/resource construction; generation/source identity | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQQdrantRuntime.scala:6-24` | lifecycle + candidate service | technical spec |
| `C-11` | `A2-e` | optional dependency/resource construction; lifecycle/readiness | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchGenerationApplication.scala:23-133` | `activate` vs `activateBaselineOnly` | technical spec |
| `C-12` | `A2-f` | optional dependency/resource construction; reusable kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQElasticsearchBaselineService.scala:27-76` | — | technical spec |
| `C-13` | `A2-g` | optional dependency handling; operator-visible degradation/status | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchApplication.scala:20-88` | `make` / `makeBaselineOnly` | technical spec |
| `C-14` | `A3-a` | reusable domain/kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQElasticsearchPolicy.scala:17-67` | `:25-31`; `:41-47` | technical spec |
| `C-15` | `A3-b` | reusable domain/kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQQdrantPolicy.scala:9-29` | — | technical spec |
| `C-16` | `A3-c` | generation/source identity; ownership/firewall | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchGen2ResourceNames.scala:6-13` | four derived names | technical spec |
| `C-17` | `A3-d` | reusable domain/kernel boundaries | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQElasticsearchGeneration.scala:16-19`; `BeautyQElasticsearchBaseline.scala:21-31` | — | technical spec |
| `C-18` | `A3-e` | reusable domain/kernel boundaries; operator-visible degradation | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQQdrantCandidatePipeline.scala:10-73` | — | technical spec |
| `C-19` | `A3-f` | reusable domain/kernel boundaries; construction-boundary proofs | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchPlanCompiler.scala:39-122`; `BeautyQCandidatePlanCompiler.scala:24-60` | — | technical spec |
| `C-20` | `A3-g` | operator-visible degradation/status; immutable evidence | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchResponseGen2.scala:32-354`; `BeautyQSearchPlanCompilationTrace.scala:14-56`; `BeautyQCandidatePlanTrace.scala:12-50` | two trace renderers | operations runbook |
| `C-21` | `A4-a` | reusable domain/kernel boundaries; construction-boundary proofs; generation/source identity | `beautyq-search-gen2-contract` · `...gen2.contract` | `BeautyQSearchDeclarations.scala:22-366` | derived `structure` `:314-362` | technical spec |
| `C-22` | `A4-b` | immutable evidence/source attribution; generation/source identity; construction-boundary proofs | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQSearchSnapshotSource.scala:16-228`; `BeautyQSearchSnapshot.scala:10-18` | — | technical spec |
| `C-23` | `A4-c` | immutable evidence/source attribution | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQSnapshotFingerprint.scala:11-24`; `BeautyQSnapshotCanonicalRows.scala` | — | technical spec |
| `C-24` | `A4-d` | immutable evidence/source attribution; reusable kernel boundaries | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQProjectedDocumentsFingerprint.scala:10-20`; `BeautyQVariantMaterializer.scala:17-38` | — | technical spec |
| `C-25` | `A4-e` | reusable domain/kernel boundaries; immutable evidence | `beautyq-search-gen2-materialization` · `...gen2.materialization` | `BeautyQVariantProjectionGen2.scala:16-213`; `BeautyQVariantProjectionError.scala` | — | technical spec |
| `C-26` | `A4-f` | reusable domain/kernel boundaries; ownership/firewall | `beautyq-search-gen2-wiring` · `...gen2.wiring` | `BeautyQSearchGen2.scala:11-59` | — | technical spec |
| `C-27` | `A5-a` | ownership/firewall; build boundary | `build.sbt` | `build.sbt:180-252` | app-shell `:242-252` | module firewall spec |
| `C-28` | `A5-b` | ownership/firewall | `search-gen2-contract` test | `SearchGen2ModuleFirewallSpec.scala:56-67` | `:20-21` | module firewall spec |
| `C-29` | `A5-c` | ownership/firewall | `search-gen2-contract` test | `SearchGen2ModuleFirewallSpec.scala:110-133` | `:39-54`, `:232-250` | module firewall spec |
| `C-30` | `A5-d` | ownership/firewall | `search-gen2-contract` test | `SearchGen2ModuleFirewallSpec.scala:135-147` | — | module firewall spec |
| `C-31` | `A5-e` | ownership/firewall; build boundary | `search-gen2-contract` test; `build.sbt` | `SearchGen2ModuleFirewallSpec.scala:79-108`; `build.sbt:242-252` | — | module firewall spec |
| `C-32` | `A5-f` | ownership/firewall; build boundary | `search-gen2-contract` test | `SearchGen2ModuleFirewallSpec.scala:69-77`; `build.sbt:258-279` | `:26-32` | module firewall spec |

### Spec candidate-area coverage (all nine)

| Spec candidate area | areaStatus | Basis |
| --- | --- | --- |
| lifecycle / readiness state | `MAPPED` | `C-03`, `C-05`, `C-06`, `C-07`, `C-09`, `C-11` |
| optional dependency / resource construction | `MAPPED` | `C-03`, `C-04`, `C-10`, `C-11`, `C-12`, `C-13` |
| generation / source identity | `MAPPED` | `C-10`, `C-16`, `C-21`, `C-22` |
| graph-boundary or construction-boundary proofs | `MAPPED` | `C-02`, `C-05`, `C-19`, `C-21`, `C-22` |
| operator-visible degradation / status | `MAPPED` | `C-06`, `C-07`, `C-08`, `C-09`, `C-13`, `C-18`, `C-20` |
| immutable evidence / source attribution | `MAPPED` | `C-06`, `C-08`, `C-20`, `C-22`, `C-23`, `C-24`, `C-25` |
| ownership / firewall constraints | `MAPPED` | `C-16`, `C-26`, `C-27`, `C-28`, `C-29`, `C-30`, `C-31`, `C-32` |
| startup / activation composition | `MAPPED` | `C-01`, `C-04`, `C-06` |
| reusable domain / kernel boundaries | `MAPPED` | `C-12`, `C-14`, `C-15`, `C-17`, `C-18`, `C-19`, `C-21`, `C-24`, `C-25`, `C-26` |

No area is `NO_CURRENT_MATERIAL_CANDIDATE` or `REJECTED` at the current state.

### Neutral candidate needs for B1/C (no verdict, no recommendation)

Every material candidate exposes at least one neutral framework capability need. Need
summaries describe the capability/mechanic only; they come from the already-mapped BeautyQ
mechanics and are not classifications, recommendations, or framework findings. B1/C
matches these `needId`s against the B0 surface map.

| needId | candidateId | needSummary |
| --- | --- | --- |
| `N-C01-01` | `C-01` | compose a feature's modules into a larger application graph and select a variant along an activation axis |
| `N-C02-01` | `C-02` | validate typed application configuration at a DI construction boundary and surface a typed domain error |
| `N-C03-01` | `C-03` | provide a readiness-dependent resource whose initialization behavior is selected by mode/scene axes |
| `N-C04-01` | `C-04` | declare managed external-service containers and derive port bindings from the started resource |
| `N-C05-01` | `C-05` | compose an ordering/readiness dependency between DI-provided resources |
| `N-C06-01` | `C-06` | represent application startup serving/degradation state and immutable startup evidence, projected to a public status |
| `N-C07-01` | `C-07` | model a typed startup-policy vocabulary with stable codes, ordering, default, and strict parsing |
| `N-C08-01` | `C-08` | aggregate serving status from policy, mode, condition, typed reason, and source identity, with derived readiness |
| `N-C09-01` | `C-09` | classify runtime/startup failures into hard vs degradable with stable codes and an append budget |
| `N-C10-01` | `C-10` | translate reserved resource names into typed lifecycle/service configurations with typed config errors |
| `N-C11-01` | `C-11` | compose multi-backend activation into an aggregate with optional components and typed partial failure |
| `N-C12-01` | `C-12` | build a reusable backend lifecycle/service lazily from resource names and batching behind one execution path |
| `N-C13-01` | `C-13` | select at construction between a full and a reduced capability and route execution accordingly |
| `N-C14-01` | `C-14` | bind a complete search policy as one executable declaration exposing derived contributions and a fingerprint |
| `N-C15-01` | `C-15` | bind a vector-store policy as one executable declaration |
| `N-C16-01` | `C-16` | own a namespace of derived physical/virtual resource names without binding lifecycle or clients |
| `N-C17-01` | `C-17` | delegate request compilation and response decoding to a generic backend compiler/decoder |
| `N-C18-01` | `C-18` | bind a candidate pipeline to policy and provenance, coupling evaluation with an eligible/ineligible outcome |
| `N-C19-01` | `C-19` | compile a plan by composing input resolution, constraint precedence, kernel preparation, and private result binding |
| `N-C20-01` | `C-20` | project an internal execution result into a public response and derive human-readable diagnostic traces |
| `N-C21-01` | `C-21` | author an executable contract/declaration tree from which structure and views are derived |
| `N-C22-01` | `C-22` | read a consistent snapshot of source tables in one repeatable-read transaction with referential validation and fingerprinting |
| `N-C23-01` | `C-23` | compute an order-independent canonical content fingerprint over domain values |
| `N-C24-01` | `C-24` | compute a projected-document fingerprint and materialize variant documents by delegating traversal/ordering to a generic materializer |
| `N-C25-01` | `C-25` | accumulate all per-variant join/invariant/schema errors without fail-fast and build a document only on empty errors |
| `N-C26-01` | `C-26` | expose a canonical navigation entry point binding typed handlers to an executable declaration |
| `N-C27-01` | `C-27` | express module/dependency DAG ownership edges in the build |
| `N-C28-01` | `C-28` | enforce generic-module main-source neutrality against domain name leakage |
| `N-C29-01` | `C-29` | confine a storage/SQL capability to a single owning module |
| `N-C30-01` | `C-30` | enforce package ownership so domain production code does not declare generic framework packages |
| `N-C31-01` | `C-31` | enforce build-dependency isolation of evaluation/test modules from serving modules |
| `N-C32-01` | `C-32` | enforce root aggregate coverage of retained modules |

### Priority B1 questions (no verdict, no recommendation)

These reference the needs above whose resolution depends on framework knowledge that LANE A
does not inspect; B1/C must resolve them without treating them as findings:

- `N-C05-01`/`N-C03-01`: whether by-name/readiness ordering expresses the `BeautyQSeedReady`
  edge without the `@unused val _edge` idiom.
- `N-C01-01`/`N-C07-01`: whether the axis-tagged policy/dispatch pattern maps to a
  framework-provided composition primitive (versus the current local tagged `ModuleDef`s).
- `N-C02-01`: whether typed boundary validation/exception conversion maps to a framework
  extension surface (versus the local `validateAtBoundary` throw).
- `N-C06-01`/`N-C08-01`/`N-C20-01`: whether startup serving-status/evidence and response
  status projection map onto an existing framework surface; the local types are the source truth.
- `N-C27-01`-`N-C32-01`: whether the compiler-invisible module firewall checks have any
  framework analogue; these are repository-owned test mechanics.
- `N-C21-01`-`N-C25-01`: whether the executable declaration / fingerprint / projection
  boundary has a framework-owned analogue, given the `search-gen2-*` kernel is
  repository-owned, not Distage/Izumi.
- `N-C04-01`: whether `distage-framework-docker` already provides the used
  container/healthcheck mechanics (declared dependency `1.2.25`).

## Validation record (LANE A self-check)

- Every material candidate has current `file:line` anchors resolving at `50e7945-clean`
  (non-research source content matches the original selected snapshot).
- All promoted candidates are BeautyQ-local/BeautyQ-owned; generic `search-gen2-*` and
  `repo-core`/`leaderboard-core`/`app-http`/`app-services` appear only as context/endpoints.
- No five-way classification label appears as a candidate verdict.
- No recommendation or change is stated.
- Deleted Wave-A/Wave-B machinery was not reconstructed; only current source is cited.
- All nine spec candidate areas are accounted for (`MAPPED`).
