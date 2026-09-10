# Feature 003 — Phase C Source-Level Comparison (C1 + C2)

## Artifact self-description

| Field | Value |
| --- | --- |
| `path` | `specs/003-beautyq-distage-izumi-leverage-audit/research/03-comparison-and-discoverability.md` |
| `question` | For each BeautyQ candidate C-01..C-32, how does the actual current BeautyQ-local source mechanic relate to its nearest exact-version Distage/Izumi surface? |
| `writer` | Feature 003 source-level comparison reviewer (`T013`–`T014`) |
| `consumers` | `T015` (discoverability), `T016`/`T017` (adversarial classification), `T020`–`T023` (validation) |
| `staleWhen` | `research/01` or `research/02` is superseded, the BeautyQ source state moves, or the resolved framework version changes |
| `stateId` | `50e7945-clean` |
| `versionRef` | `io.7mind.izumi` `1.2.25` |
| `phase` | Phase C only. Section `## D` (discoverability) is owned by `T015` and is intentionally absent. |

## Binding and method

- Evaluated BeautyQ source binding: `headSha=50e794584d68768403d5c45d6d7fc21c2db87f6c`, `stateId=50e7945-clean` (`research/00`).
- Framework binding: `io.7mind.izumi` `1.2.25` (declared == resolved == verified, `research/00`).
- Current repository `HEAD` is later only because feature-local `research/*` artifacts were committed by the human; `git diff --stat 50e7945… HEAD -- ':!specs/003-beautyq-distage-izumi-leverage-audit/research/**'` is empty, so the source/build/project/framework content under comparison is unchanged.
- Every candidate reopened its own `research/01` `file:line` anchors and only its relevant `research/02` exact-version evidence, then inspected actual composition/API usage.
- Outcomes are Phase-C comparison outcomes only: `DIRECT_USE` | `LOCAL_DUPLICATION_OR_BYPASS` | `MISSING_COMPOSITION` | `NO_RELEVANT_PRIMITIVE`. No discoverability verdict and no Phase-E headline label appears here.
- `frameworkSurfaceIds` lists the semantically relevant framework surface(s); it is empty `[]` where no relevant surface exists (`NO_RELEVANT_PRIMITIVE`). Where B1 had a matched surface that source inspection shows is abstraction-mismatched, the considered surface is named in `comparisonBasis` as `B1 nearest … (considered, not materially relevant)`.
- Reference form for exact-version framework evidence is as fixed in `research/02`: `CS/io/7mind/izumi/<artifact>_3/1.2.25/<artifact>_3-1.2.25-sources.jar :: <scala-source-path>` (cache root `CS = ~/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2`).

---

## C1 — App Shell / Wiring / Runtime Comparisons

One record per candidate `C-01`..`C-20`, each exactly once.

### C-01 — Plugin/module/activation composition

- **candidateId**: `C-01`
- **needId**: `N-C01-01`
- **B1 evidenceState / coverage**: `MATCHED` / `FULL`
- **frameworkSurfaceIds**: `b0a.plugin`, `b0a.module-compose`, `b0a.include-tags`, `b0a.role`, `b0a.activation-axis`, `b0a.binding-tag`
- **BeautyQ source anchors**: `leaderboard-app-shell/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala:28-42`; `.../leaderboard/plugins/BeautySearchGen2PluginModules.scala:39-51`; `.../leaderboard/search/gen2/BeautyQSupplementStartup.scala:5-9`; `.../leaderboard/LeaderboardRole.scala:684-688`
- **exact-version framework evidence refs**: `b0a.plugin` (`extension-plugins :: izumi/distage/plugins/PluginDef.scala`, `PluginConfig.scala`); `b0a.module-compose` (`core-api :: izumi/distage/model/definition/Module.scala`, `ModuleBase.scala`); `b0a.include-tags` (`core-api :: izumi/distage/model/definition/dsl/IncludesDSL.scala`); `b0a.role` (`framework :: izumi/distage/roles/RoleAppMain.scala`, `RoleAppBootModule.scala`); `b0a.activation-axis` (`core-api :: izumi/distage/model/definition/Axis.scala`, `Activation.scala`, `StandardAxis.scala`); `b0a.binding-tag` (`core-api :: izumi/distage/model/definition/BindingTag.scala`) — all `1.2.25`
- **comparisonOutcome**: `DIRECT_USE`
- **comparisonBasis**: `LeaderboardPlugin extends PluginDef` and `include`s the generic module sets plus `BeautySearchGen2PluginModules.api`. `BeautySearchGen2PluginModules.api` is a `ModuleDef` carrying `tag(Repo.Prod)` and `include(...)`; `appShellGraph` includes `enabledSupplementGraph(BeautyQSupplementStartup.Required, …)` and `…(Preferred, …)` plus `disabledSupplementGraph`, each a `ModuleDef` tagged with `tag(choice: Axis.AxisChoice)`. `BeautyQSupplementStartup extends Axis` with `Required`/`Preferred`/`Disabled` choices. `LeaderboardRole.MainBase extends RoleAppMain.LauncherBIO[IO]` and in `roleAppBootOverrides` binds `Activation` (adding `BeautyQSupplementStartup -> Required` to the default activation). This is Distage's intended plugin/module/axis/activation composition model used directly.
- **localSemanticsNotCoveredByFramework**: the axis and choice names, the `SupplementStartupPolicy` values, and which three module variants exist are BeautyQ product policy.
- **strongestCounterexampleChecked**: tested whether the three tagged `ModuleDef`s rebuild a generic selector. They do not: each uses `tag(choice)` (an `AxisTag`) alongside `Activation`, i.e. Distage's own activation mechanism, not a locally authored selector.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-02 — Typed app-shell config validation

- **candidateId**: `C-02`
- **needId**: `N-C02-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `b0d.config-def`, `b1.config.typed-decode-error`
- **BeautyQ source anchors**: `leaderboard-app-shell/src/main/scala/leaderboard/config/BeautyQGen2AppShellConfig.scala:37-85`; `.../leaderboard/plugins/BeautySearchGen2PluginModules.scala:28-37`
- **exact-version framework evidence refs**: `b0d.config-def` (`extension-config :: izumi/distage/config/ConfigModuleDef.scala`, `model/ConfTag.scala`, `codec/DIConfigReader.scala`); `b1.config.typed-decode-error` (`CS/io/7mind/izumi/distage-extension-config_3/1.2.25/distage-extension-config_3-1.2.25-sources.jar :: izumi/distage/config/codec/DIConfigReader.scala:85-100`; `…/config/model/exceptions/DIConfigReadException.scala:5`) — all `1.2.25`
- **comparisonOutcome**: `DIRECT_USE`
- **comparisonBasis**: `appShellConfigModule` is a `ConfigModuleDef` that binds `RawBeautyQGen2AppShellConfig` with `makeConfig[...]("beautyq-gen2-app-shell")`; `validatedAppShellConfigModule` then binds `BeautyQGen2AppShellConfig` with `.from { raw => BeautyQGen2AppShellConfig.validateAtBoundary(raw) }`. HOCON decode is direct `distage-extension-config` use. `validateAtBoundary` applies a domain positive-value/Qdrant-policy rule and throws the typed `BeautyQGen2AppShellConfigException` carrying `BeautyQGen2AppShellConfigError`. Per B1, `DIConfigReadException` is a decode error, not a general domain-validation hook; the domain rule is a distinct, legitimate boundary policy.
- **localSemanticsNotCoveredByFramework**: the positive-duration/batch validation rules, the Qdrant work-policy consistency check, and the `BeautyQGen2AppShellConfigError`/`…Exception` typed vocabulary.
- **strongestCounterexampleChecked**: adversarially considered `MISSING_COMPOSITION` — could the typed error be a bypass of a framework validation surface? No: the config *binding/decoding* uses the framework surface directly, and no framework surface provides domain positive-value validation; `DIConfigReadException` covers decode failure only.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-03 — Lifecycle/readiness selected by Mode/Scene

- **candidateId**: `C-03`
- **needId**: `N-C03-01`
- **B1 evidenceState / coverage**: `MATCHED` / `FULL`
- **frameworkSurfaceIds**: `b0b.lifecycle-core`, `b0b.from-resource`, `b0b.from-effect`, `b0a.activation-axis`, `b1.lifecycle.dependency-edge`, `b0c.integration-check`
- **BeautyQ source anchors**: `leaderboard-app-shell/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala:155-176`; `.../leaderboard/seed/BeautyQSeedInserter.scala:50-77`
- **exact-version framework evidence refs**: `b0b.lifecycle-core` (`fundamentals-bio :: izumi/functional/lifecycle/Lifecycle.scala`); `b0b.from-resource` (`core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:259-318`; `dsl/LifecycleAdapters.scala`); `b0a.activation-axis` (`core-api :: izumi/distage/model/definition/{Axis,Activation,StandardAxis}.scala`) — all `1.2.25`
- **comparisonOutcome**: `DIRECT_USE`
- **comparisonBasis**: `seedProd` tags `Mode.Prod, Scene.Provided` and binds `BeautyQSeedReady` via `.fromResource[BeautyQSeedReady.Noop[F]]`; `seedManaged` tags `Mode.Prod, Scene.Managed` and `seedTest` tags `Mode.Test`, each binding `.fromResource[BeautyQSeedReady.LoadAndInsert[F]]`. `Noop`/`LoadAndInsert` extend `Lifecycle.LiftF`. `Mode`/`Scene` are `StandardAxis`. The readiness-dependent resource and its mode/scene selection use `Lifecycle`/`fromResource` + activation directly.
- **localSemanticsNotCoveredByFramework**: the seed-load/insertion mechanics (file loading, deterministic ordering, the process-wide `Semaphore` lock) and the meaning of `BeautyQSeedReady` are product-specific.
- **strongestCounterexampleChecked**: tested whether readiness gating locally reimplements a framework feature; it does not — the resource is a framework `Lifecycle` selected by standard axes.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-04 — Managed Docker modules

- **candidateId**: `C-04`
- **needId**: `N-C04-01`
- **B1 evidenceState / coverage**: `MATCHED` / `FULL`
- **frameworkSurfaceIds**: `b0d.docker-container`, `b0d.docker-health`, `b0b.from-resource`
- **BeautyQ source anchors**: `leaderboard-app-shell/src/main/scala/leaderboard/plugins/PostgresDockerPlugin.scala:15-36`; `.../plugins/ElasticsearchDockerPlugin.scala:13-47`; `.../plugins/QdrantGen2DockerPlugin.scala:14-47`
- **exact-version framework evidence refs**: `b0d.docker-container` (`framework-docker :: izumi/distage/docker/ContainerDef.scala`, `DockerContainer.scala:38-*`, `impl/ContainerResource.scala`, `modules/DockerSupportModule.scala`); `b0d.docker-health` (`framework-docker :: izumi/distage/docker/healthcheck/ContainerHealthCheck.scala`); `b0b.from-resource` (`core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:259-318`) — all `1.2.25`
- **comparisonOutcome**: `DIRECT_USE`
- **comparisonBasis**: each plugin is a `PluginDef` whose `dockerModule` tags `Scene.Managed`, includes `DockerSupportModule[F[Throwable, _]]`, and binds the container with `.fromResource(<Def>.make[F[Throwable, _]])`. `ElasticsearchDocker` and `QdrantGen2Docker` extend `ContainerDef` and declare `healthCheck = ContainerHealthCheck.httpGetCheck(primaryPort)` with `healthCheckMaxAttempts = 180`; the port config bindings read `docker.availablePorts.first(...)` (binding the started container's actual port). These are the `distage-framework-docker` `ContainerDef`/`DockerContainer`/`ContainerHealthCheck` mechanics used directly.
- **localSemanticsNotCoveredByFramework**: image/registry/env/port choices and the dockered-service selection are app/infra policy.
- **strongestCounterexampleChecked**: tested whether health-check or port wiring locally rebuilt `ContainerHealthCheck`/`availablePorts`; it does not.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-05 — `BeautyQSeedReady` ordering edge

- **candidateId**: `C-05`
- **needId**: `N-C05-01`
- **B1 evidenceState / coverage**: `MATCHED` / `FULL`
- **frameworkSurfaceIds**: `b0b.lifecycle-core`, `b0b.from-resource`, `b0b.ref-resource`, `b1.lifecycle.dependency-edge`
- **BeautyQ source anchors**: `leaderboard-app-shell/src/main/scala/leaderboard/plugins/BeautySearchGen2PluginModules.scala:56-60`
- **exact-version framework evidence refs**: `b1.lifecycle.dependency-edge` (`CS/io/7mind/izumi/distage-core-api_3/1.2.25/distage-core-api_3-1.2.25-sources.jar :: izumi/distage/model/definition/dsl/AbstractBindingDefDSL.scala:236-248, 306-329`; `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:202-206`); `b0b.lifecycle-core` (`fundamentals-bio :: izumi/functional/lifecycle/Lifecycle.scala`) — all `1.2.25`
- **comparisonOutcome**: `DIRECT_USE`
- **comparisonBasis**: the binding function `(seedReady: BeautyQSeedReady, sql: SQL[IO], clock: JClock) => …` already makes `BeautyQSeedReady` a dependency of `BeautyQSearchSnapshotSource.Postgres`; the ordering/readiness edge is expressed by ordinary constructor/function dependency injection, a first-class Distage mechanism. The `@unused val _edge = seedReady` line only suppresses the compiler's unused-parameter warning. `b1.lifecycle.dependency-edge`'s `addDependency[BeautyQSeedReady]` is an alternative DSL that would express the same edge without the unused parameter; the current code neither reimplements nor bypasses `addDependency`. B1 also records the framework itself using the same `@unused` constructor-parameter idiom for an intentional `IntegrationCheck` edge (`framework-docker :: …/impl/DockerClientWrapper.scala:117`).
- **localSemanticsNotCoveredByFramework**: the readiness meaning of `BeautyQSeedReady` (seed data present before snapshot read) and its binding policy are BeautyQ/domain semantics.
- **strongestCounterexampleChecked**: adversarially tested `LOCAL_DUPLICATION_OR_BYPASS` and `MISSING_COMPOSITION`: (1) is the local code product policy? no, it is an ordering mechanism; (2) thin adapter? no; (3) does `addDependency` preserve the exact semantics? yes, but so does the constructor dependency already in use; (4) different ownership layer? no; (5) would replacing it reduce meaningful complexity? it would remove one unused parameter and one `@unused val`, a cosmetic gain only; (6) required to expose stable product types? no; (7) is `addDependency` merely nearby? it is semantically equivalent, but the current mechanism is the primary framework mechanism rather than a bypass. The counterexample defeats the change-oriented reading, so the outcome stays `DIRECT_USE`.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-06 — Startup serving status / evidence / response projection

- **candidateId**: `C-06`
- **needId**: `N-C06-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `leaderboard-app-shell/src/main/scala/leaderboard/search/gen2/BeautyQSearchGen2Bootstrap.scala:30-107`; `.../BeautyQSearchGen2Startup.scala:24-241`; `.../BeautyQSearchGen2Runtime.scala:14-42`; `.../BeautyQSearchGen2HttpService.scala:14-141`
- **exact-version framework evidence refs**: none positive; B1 bounded search over `core`, `core-api`, `framework`, `framework-docker`, `testkit-core` found only test-runner `TestStatus` and provisioning `OpStatus`. B1 nearest: `b0c.integration-check` (`core-api :: izumi/distage/model/provisioning/IntegrationCheck.scala`, `…/exceptions/runtime/NonCriticalIntegrationFailure.scala`) — considered, not materially relevant.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchGen2Bootstrap.activate` builds `BeautyQSearchStartupEvidence` (snapshot time, durations, activatedAt); `BeautyQSearchGen2Startup` dispatches on `SupplementStartupPolicy` into healthy/degraded/limited `StartupServingStatus` and logs; `BeautyQSearchGen2Runtime` carries `startupStatus`/`startupEvidence`; `BeautyQSearchGen2HttpService.encodeStatus` projects status JSON. This is application-level serving state/evidence. `b0c.integration-check` is a DI provisioning readiness gate at a different abstraction and is neither used nor substitutable here; no dedicated runtime serving/degradation/evidence surface was established at `1.2.25`.
- **localSemanticsNotCoveredByFramework**: the entire serving-status/evidence/status-JSON model.
- **strongestCounterexampleChecked**: checked whether `IntegrationCheck`/`ResourceCheck`/`NonCriticalIntegrationFailure` or the diagnostic renderers (`b0c.diagnostic`: `DepTreeRenderer`/`DIPlanCompactFormatter`/`DIRendering`) enforce the same property. None represents app serving state or immutable startup evidence; the diagnostic surfaces render DI graphs.
- **evidenceGap**: inherited bounded-search residual — a differently named `1.2.25` mechanism could still represent app serving status; unresolved. Not `BLOCKED_NEED_EVIDENCE`; the Phase-C outcome stands within the bounded exact-version search.
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-07 — Typed startup policy vocabulary

- **candidateId**: `C-07`
- **needId**: `N-C07-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `b0a.activation-axis`, `b0a.binding-tag`, `b0a.include-tags`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSupplementReadinessPolicy.scala:7-41` and `:43-54`; `leaderboard-app-shell/src/main/scala/leaderboard/search/gen2/BeautyQSupplementStartup.scala:5-9`
- **exact-version framework evidence refs**: `b0a.activation-axis` (`core-api :: izumi/distage/model/definition/Axis.scala`, `Activation.scala`, `StandardAxis.scala`); `b0a.binding-tag` (`core-api :: izumi/distage/model/definition/BindingTag.scala`, `dsl/Tagging.scala`); `b0a.include-tags` (`core-api :: izumi/distage/model/definition/dsl/IncludesDSL.scala`) — all `1.2.25`
- **comparisonOutcome**: `DIRECT_USE`
- **comparisonBasis**: the app-shell selects the startup variant through `BeautyQSupplementStartup extends Axis` and `Activation` (C-01), and binds `SupplementStartupPolicy` into the graph under `tag(choice)` with `make[SupplementStartupPolicy].fromValue(policy)`; `BeautyQServingMode` is the paired vocabulary. Distage `Axis`/`Activation`/`AxisTag` directly provide the typed variant selection and composition. The stable codes, explicit `ordered`, `Default`, and the strict `fromStableCode` parser of `SupplementStartupPolicy` are domain-owned.
- **localSemanticsNotCoveredByFramework**: the stable-code vocabulary, explicit ordering, default selection, and strict parser semantics (rejecting empty/whitespace/unknown codes).
- **strongestCounterexampleChecked**: tested whether `fromStableCode`/`stableCode` duplicates a framework code parser; no framework surface parses domain policy codes, and the axis selection is used directly.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-08 — Serving-status aggregate

- **candidateId**: `C-08`
- **needId**: `N-C08-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSupplementReadinessPolicy.scala:56-154`
- **exact-version framework evidence refs**: none positive; B1 nearest `b0c.integration-check` (readiness gate) considered, not materially relevant.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `StartupServingStatus` binds policy + serving mode + `condition` + optional typed `Reason` + `restartRequired` + source/projected fingerprints + ES/Qdrant resource references, with private `healthy`/`degraded`/`limited` constructors and derived `supplementReady`. No dedicated `1.2.25` serving-status aggregation surface was established; the nearest framework mechanism is a readiness gate, a different abstraction.
- **localSemanticsNotCoveredByFramework**: the whole aggregate, its typed `Reason`, and the derived readiness.
- **strongestCounterexampleChecked**: same check as `C-06`; no framework status-aggregation surface exists, and `IntegrationCheck`/`ResourceCheck` gate provisioning rather than aggregate serving status.
- **evidenceGap**: inherited bounded-search residual (same as `C-06`), non-blocking.
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-09 — Hard/degradable failure policy

- **candidateId**: `C-09`
- **needId**: `N-C09-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSupplementPolicy.scala:10-243`
- **exact-version framework evidence refs**: none positive; B1 nearest `b0c.integration-check` (`core-api :: izumi/distage/model/provisioning/IntegrationCheck.scala`; `…/exceptions/runtime/NonCriticalIntegrationFailure.scala`) considered, abstraction-mismatched.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSupplementPolicy` classifies BeautyQ's own error ADTs (`BeautyQQdrantCandidatePipelineError`, `BeautyQSearchGenerationActivationError`, `QdrantGenerationLifecycleError`, `Gen2HttpTransportError`) into `Hard`/`Degradable` with `BeautyQDegradationReason` stable codes, and owns the append budget through the repository-owned `AppendOnlySupplementPolicy`. `b0c.integration-check`'s `NonCriticalIntegrationFailure` marks a provisioner integration/readiness failure; it is not a general runtime/startup failure classifier and BeautyQ is not an `IntegrationCheck` producer at this site. Per B1 the framework marker is only a hard-vs-non-critical integration distinction; BeautyQ's classifier is not duplication of it.
- **localSemanticsNotCoveredByFramework**: the runtime/startup classification ladders, the stable degradation-code vocabulary, and the append budget.
- **strongestCounterexampleChecked**: tested `LOCAL_DUPLICATION_OR_BYPASS` (does BeautyQ rebuild `NonCriticalIntegrationFailure`? no — different stage, different error domain) and `MISSING_COMPOSITION` (is the marker required here? no; the classifier operates over domain pipeline/activation errors, and no `IntegrationCheck` producer is involved).
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-10 — Resource-name → typed runtime config

- **candidateId**: `C-10`
- **needId**: `N-C10-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQQdrantRuntime.scala:6-24`
- **exact-version framework evidence refs**: none positive; B1 notes `.fromResource` (`b0b.from-resource`) and config decoding (`b0d.config-def`, `b1.config.typed-decode-error`) as abstraction-mismatched nearest surfaces.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQQdrantRuntime.lifecycle`/`candidateService` turn `BeautyQSearchGen2ResourceNames` into repository-kernel `QdrantGenerationLifecycleConfig.create` / `QdrantCandidateServiceConfig.create`, returning typed `Either` config errors. No `1.2.25` framework surface translates reserved resource names into typed lifecycle/service configurations; `.fromResource` only binds acquisition and config decoding surfaces HOCON `DIConfigReadException`, a different abstraction.
- **localSemanticsNotCoveredByFramework**: resource-name→config translation and the typed config-construction errors.
- **strongestCounterexampleChecked**: tested `LOCAL_DUPLICATION_OR_BYPASS` against `fromResource` and config surfaces; neither translates names into typed backend configs, so no duplication.
- **evidenceGap**: inherited bounded-search residual — a differently named surface for resource-name→typed-config translation could exist in an uninspected module; unresolved, non-blocking.
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-11 — Multi-backend activation aggregate

- **candidateId**: `C-11`
- **needId**: `N-C11-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `b0b.lifecycle-core`, `b0b.from-resource`, `b0b.from-effect`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSearchGenerationApplication.scala:23-133`; binding at `leaderboard-app-shell/src/main/scala/leaderboard/plugins/BeautySearchGen2PluginModules.scala:172-179` and `:199-205`
- **exact-version framework evidence refs**: `b0b.lifecycle-core` (`fundamentals-bio :: izumi/functional/lifecycle/Lifecycle.scala`); `b0b.from-resource` (`core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:259-318`); `b0b.from-effect` (`core-api :: …/dsl/ModuleDefDSL.scala:216-222`) — all `1.2.25`
- **comparisonOutcome**: `DIRECT_USE`
- **comparisonBasis**: `activate` composes ES compile→activate with Qdrant prepare/embed/activate into an `Activation` aggregate carrying optional `qdrantGeneration` and optional typed `qdrantFailure`; `activateBaselineOnly` omits Qdrant. The aggregate is a pure domain `Either` orchestration over repository-kernel lifecycle objects, while the app-shell exposes the startup aggregate as a Distage resource: `BeautyQSearchGen2Startup.lifecycle` returns `Lifecycle.LiftF`, bound with `.fromResource` (`BeautySearchGen2PluginModules.scala:172-179`, `:199-205`). The framework-addressable resource-construction/release portion directly uses `Lifecycle`/`fromResource`.
- **localSemanticsNotCoveredByFramework**: the `Activation` aggregate's optional-component model, its typed partial-failure outcome, and the ES/Qdrant/embedding orchestration order.
- **strongestCounterexampleChecked**: tested `LOCAL_DUPLICATION_OR_BYPASS`; `Lifecycle` does not model an optional-field aggregate or typed partial failure, and the aggregate is pure domain logic over repository-kernel types. The startup resource itself is a direct `Lifecycle` binding, so `DIRECT_USE` survives.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-12 — Reusable backend lifecycle/service construction

- **candidateId**: `C-12`
- **needId**: `N-C12-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQElasticsearchBaselineService.scala:27-76`
- **exact-version framework evidence refs**: none positive. B1 matched `b0b.lifecycle-core`/`b0b.from-resource`; source inspection shows the constructed lifecycle is the repository-owned `search-gen2-elasticsearch` `ElasticsearchGenerationLifecycle` (a plain class, not a Distage `Lifecycle`), and the binding uses ordinary `make[...].from` singleton binding.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQElasticsearchBaselineService.make` is a pure factory constructing `ElasticsearchGenerationLifecycle` + `ElasticsearchBaselineService` from resource names and the batching policy; the app-shell binds the result as a DI singleton (`make[BeautyQElasticsearchBaselineService].from { … }`) and exposes one `searchBound` execution path. The Need's semantics (resource-name→config translation, request batching, single BeautyQ execution path) are repository-kernel/domain. No Distage `Lifecycle`/`fromResource` is used at this site, and even the generic DI binding surface does not provide backend lifecycle/config/batching semantics, so no Distage primitive materially addresses the Need. B1's `Lifecycle`/`fromResource` PARTIAL match does not survive source inspection.
- **localSemanticsNotCoveredByFramework**: resource-name→config translation, batching policy, and the single `searchBound` execution path.
- **strongestCounterexampleChecked**: tested `b0b.from-resource`/`b0b.lifecycle-core` (not used here) and `b0a.binding-dsl` (assembles the value but adds no backend-lifecycle/config/batching capability); neither materially addresses the Need, so the outcome stays `NO_RELEVANT_PRIMITIVE`.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-13 — Full vs baseline capability

- **candidateId**: `C-13`
- **needId**: `N-C13-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSearchApplication.scala:20-88`
- **exact-version framework evidence refs**: none positive; B1 nearest `b0a.activation-axis`/`b0a.binding-tag` considered, not materially applicable to this construction boundary. `b0b.optional-set` not counted (B1: the local mechanism is an optional result, not an injected empty/set contribution).
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchApplication` holds a private `SupplementCapability` (`Full` with embedding + Qdrant, or `Baseline`) chosen at construction; `execute` routes to full orchestration or baseline-only. The Full/Baseline choice is made at runtime by `BeautyQSearchGen2Startup.constructStartup`/`constructBaselineStartup` from the *outcome* of `bootstrap.activate` (policy `Required`/`Preferred` plus `activation.qdrantFailure`), then stored in the private ADT. The DI activation axis (`BeautyQSupplementStartup`) selects the module graph but cannot select this capability, which depends on an effect run during lifecycle acquisition; the private capability ADT is runtime behavior not replaceable by `Axis`.
- **localSemanticsNotCoveredByFramework**: the private capability ADT and the execution routing between full/baseline paths.
- **strongestCounterexampleChecked**: tested `b0a.activation-axis`/`b0a.binding-tag` (activation selects bindings at graph construction, not a post-activation runtime outcome) and `b0b.optional-set` (the local form is an optional result, not an injected empty/set contribution); the activation-axis interpretation is defeated, so the outcome is `NO_RELEVANT_PRIMITIVE`.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-14 — Elasticsearch policy as executable declaration

- **candidateId**: `C-14`
- **needId**: `N-C14-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQElasticsearchPolicy.scala:17-67`
- **exact-version framework evidence refs**: none positive; B1 nearest `b0a.binding-dsl` (`core-api :: izumi/distage/model/definition/ModuleDef.scala`, `dsl/ModuleDefDSL.scala`, `dsl/AbstractBindingDefDSL.scala`, `Bindings.scala`) considered, abstraction-mismatched.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQElasticsearchPolicy` is a compile-time Scala object whose `index`/`value` are built with the repository-owned `search-gen2-elasticsearch` `ElasticsearchIndexPolicy.unsafeFrom`/`ElasticsearchPolicy.unsafeFrom`; `contributions`/`contractFingerprint` delegate to `value`. The object is referenced directly (e.g. by `BeautyQElasticsearchGeneration.compile`, `BeautyQSearchPlanCompiler`, `BeautyQElasticsearchBaseline`), not DI-bound. The Need — an executable search-policy declaration with derived contributions and a contract fingerprint — is repository-owned and has no Distage analogue; the binding DSL is DI assembly and does not provide declaration/derivation/fingerprint semantics. Per the Phase-C semantics, "Distage can bind this object" is not duplication of the declaration.
- **localSemanticsNotCoveredByFramework**: the complete ES policy declaration and its derived contributions/contract fingerprint.
- **strongestCounterexampleChecked**: tested `LOCAL_DUPLICATION_OR_BYPASS` against `b0a.binding-dsl`; the DSL would only assemble the object into the graph and provides none of the declaration/derivation/fingerprint semantics, and it is not even exercised at this site.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-15 — Qdrant policy as executable declaration

- **candidateId**: `C-15`
- **needId**: `N-C15-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQQdrantPolicy.scala:9-29`
- **exact-version framework evidence refs**: none positive; B1 nearest `b0a.binding-dsl` considered, abstraction-mismatched.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQQdrantPolicy.policy` is a compile-time object built with the repository-owned `search-gen2-qdrant` `QdrantPolicy.unsafeFrom`, binding identity/embedding field/vector name/model identity/distance/retrieval. The declaration-owned semantics (identity, contributions, derived payload/index mechanics) are repository-owned; the DI binding DSL is a different abstraction and is not the source of those semantics.
- **localSemanticsNotCoveredByFramework**: the vector-store policy declaration semantics.
- **strongestCounterexampleChecked**: tested `LOCAL_DUPLICATION_OR_BYPASS` against `b0a.binding-dsl`; binding an object is not the declaration semantics, so no duplication.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-16 — Resource-name namespace

- **candidateId**: `C-16`
- **needId**: `N-C16-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSearchGen2ResourceNames.scala:6-13`
- **exact-version framework evidence refs**: none positive; B1 notes `b0d.ownership-aliases` (`Id`, `.named`) concerns DI identifiers/visibility, a different abstraction.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchGen2ResourceNames` owns the ES alias / physical index prefix / Qdrant alias / physical collection prefix namespace as names only. No `1.2.25` framework surface derives backend physical/virtual resource names; DI `Id`/`.named` address locator identity and visibility, not external resource names.
- **localSemanticsNotCoveredByFramework**: the resource-name namespace and its derivations.
- **strongestCounterexampleChecked**: tested `b0d.ownership-aliases`; DI identifiers are not backend resource names, so no duplication/omission.
- **evidenceGap**: inherited bounded-search residual — a differently named resource-name derivation surface is unresolved, non-blocking.
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-17 — Delegate to generic backend compiler/decoder

- **candidateId**: `C-17`
- **needId**: `N-C17-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQElasticsearchGeneration.scala:16-19`; `.../BeautyQElasticsearchBaseline.scala:21-31`
- **exact-version framework evidence refs**: none positive; Distage provides no generic backend request compiler/response decoder; `b0a.binding-dsl` can bind such a compiler but does not compile/decode.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQElasticsearchGeneration.compile` delegates to the repository-owned `ElasticsearchGenerationCompiler.compile`, and `BeautyQElasticsearchBaseline.compileRequest`/`decodeResponse` delegate to `ElasticsearchSearchRequestCompiler`/`ElasticsearchSearchResponseDecoder`. These are repository-owned `search-gen2-elasticsearch` mechanics, not Distage.
- **localSemanticsNotCoveredByFramework**: request compilation and response decoding composition.
- **strongestCounterexampleChecked**: tested `b0a.binding-dsl`; DI assembly is not backend compilation/decoding.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-18 — Candidate pipeline binding + evaluation outcome

- **candidateId**: `C-18`
- **needId**: `N-C18-01`
- **B1 evidenceState / coverage**: `MATCHED` / `PARTIAL`
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQQdrantCandidatePipeline.scala:10-73`
- **exact-version framework evidence refs**: none positive; B1 nearest `b0a.binding-dsl` considered, abstraction-mismatched.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQQdrantCandidatePipeline` is a compile-time object composing the repository-owned `QdrantCandidatePipeline.execute` and `CandidateHydrator.hydrate` bound to BeautyQ policy/hydration/provenance, with a private `Result` coupling `CompiledCandidateEvaluation` with either the ineligibility reason or the hydrated candidate result. No Distage surface couples evaluation to an eligible/ineligible domain outcome; the binding DSL is DI assembly only.
- **localSemanticsNotCoveredByFramework**: the pipeline binding, the `BeautyQCandidateProvenance`/hydration policy, and the coupled evaluation/outcome result.
- **strongestCounterexampleChecked**: tested `LOCAL_DUPLICATION_OR_BYPASS` against `b0a.binding-dsl`; binding a value is not the same capability as coupling evaluation to an outcome, so no duplication.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-19 — Plan/candidate compilation composition

- **candidateId**: `C-19`
- **needId**: `N-C19-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSearchPlanCompiler.scala:39-122`; `.../BeautyQCandidatePlanCompiler.scala:24-60`
- **exact-version framework evidence refs**: none positive; Distage plan surfaces `b0c.plan-verify`/`b0c.plan-check` address DI-graph construction/verification, not domain search-plan compilation.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchPlanCompiler.compile` composes the repository-owned `PublicPlanInputResolver`, `SearchPlanCompilationKernel`, `SearchCursorEnvelope`, and `BeautyQSearchPlanPolicy`, returning a private `CompiledBeautyQSearchPlan`; `BeautyQCandidatePlanCompiler.compile` calls `BeautyQSemanticCandidatePolicy.evaluate` once and returns a private `CompiledCandidateEvaluation`. No Distage primitive addresses domain search-plan compilation.
- **localSemanticsNotCoveredByFramework**: the plan-compilation composition, constraint precedence, kernel preparation, and private result binding.
- **strongestCounterexampleChecked**: tested `b0c.plan-verify`/`b0c.plan-check`; they verify the DI graph, not a domain search plan.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-20 — Response projection and diagnostic traces

- **candidateId**: `C-20`
- **needId**: `N-C20-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSearchResponseGen2.scala:32-354`; `.../BeautyQSearchPlanCompilationTrace.scala:14-56`; `.../BeautyQCandidatePlanTrace.scala:12-50`
- **exact-version framework evidence refs**: none positive; B1 nearest `b0c.diagnostic` (`core-api :: izumi/distage/model/plan/repr/DepTreeRenderer.scala`, `repr/DIPlanCompactFormatter.scala`, `repr/DIRendering.scala`) renders DI-graph diagnostics, a different abstraction/domain.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchResponseGen2Projector.project` derives hits/facets/groups/carousels/applied+suppressed filters/warnings plus `supplementStatus`/`servingMode`/`restartRequired`/degradation reason from the bound aggregate; `deriveWarnings` maps startup/degradation state; the two trace renderers derive human-readable views from bound fields. No generic `1.2.25` surface projects a domain execution result into a public response; the diagnostic renderers render DI graphs.
- **localSemanticsNotCoveredByFramework**: the public response projection and the two diagnostic trace renderers.
- **strongestCounterexampleChecked**: tested `b0c.diagnostic`; DI-graph diagnostics are not app response projection, so no duplication.
- **evidenceGap**: inherited bounded-search residual — the diagnostic-trace portion could have a differently named surface; unresolved, non-blocking.
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

---

## C2 — Contract / Materialization / Ownership Comparisons

One record per candidate `C-21`..`C-32`, each exactly once.

### C-21 — Executable contract/declaration tree

- **candidateId**: `C-21`
- **needId**: `N-C21-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-contract/src/main/scala/leaderboard/search/beautyq/gen2/contract/BeautyQSearchDeclarations.scala:22-366`
- **exact-version framework evidence refs**: none positive; `b0a.binding-dsl`/`b0a.module-compose` are DI declarations, a different abstraction; the declaration DSL and `structure`/`renderStructure` are repository-owned.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchDeclarations` is an executable contract tree built from the repository-owned `repo.catalog(...)` topology DSL and the `searchFields[Document]` DSL, with `request`/`intent`/`plan`/`candidate` branches holding direct typed policy references and a derived `structure`/`renderStructure`. `ModuleDef` is an executable DI declaration but not a product-domain contract/declaration tree with derived structure/views; the declaration semantics live in the repository-owned `search-gen2-*` kernel, not Distage.
- **localSemanticsNotCoveredByFramework**: the whole executable declaration tree and its derived structure view.
- **strongestCounterexampleChecked**: checked `b0a.binding-dsl`/`b0a.module-compose`/`b0a.plugin`; they compose DI bindings, not a domain contract tree with derived structure, so no duplication.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-22 — Consistent snapshot transaction

- **candidateId**: `C-22`
- **needId**: `N-C22-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-materialization/src/main/scala/leaderboard/search/beautyq/gen2/materialization/BeautyQSearchSnapshotSource.scala:16-228`; `.../BeautyQSearchSnapshot.scala:10-18`
- **exact-version framework evidence refs**: none positive; `b0b.from-resource` only binds resource acquisition.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchSnapshotSource.Postgres` reads every source table under one repository-owned `SQL.readOnlyRepeatableRead` transaction (doobie), validates referential rows, and returns a repository-kernel `VersionedSnapshot` with a content fingerprint and `clock.instant()`; the private `SnapshotReadHook` exists only for the concurrent-mutation test. No `1.2.25` framework surface provides snapshot transactions, referential validation, or fingerprinting.
- **localSemanticsNotCoveredByFramework**: the snapshot transaction, referential validation, and fingerprinting.
- **strongestCounterexampleChecked**: tested `b0b.from-resource`; it binds acquisition only and does not provide transactional snapshots, so no duplication.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-23 — Order-independent canonical fingerprint

- **candidateId**: `C-23`
- **needId**: `N-C23-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-materialization/src/main/scala/leaderboard/search/beautyq/gen2/materialization/BeautyQSnapshotFingerprint.scala:11-24`; `.../BeautyQSnapshotCanonicalRows.scala`
- **exact-version framework evidence refs**: none positive; B1 found no framework fingerprint surface.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSnapshotFingerprint.compute` uses the repository-owned `CanonicalFingerprint.sha256HexTokens`/`CanonicalSnapshot.encode` token writer; `BeautyQSnapshotCanonicalRows` declares which persisted fields participate. No `1.2.25` framework surface computes order-independent canonical content fingerprints of domain values.
- **localSemanticsNotCoveredByFramework**: the canonical content fingerprint and per-entity source-field policy.
- **strongestCounterexampleChecked**: no nearby Distage surface even approximately computes domain fingerprints; none exists to duplicate.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-24 — Projected-document fingerprint and materialization

- **candidateId**: `C-24`
- **needId**: `N-C24-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-materialization/src/main/scala/leaderboard/search/beautyq/gen2/materialization/BeautyQProjectedDocumentsFingerprint.scala:10-20`; `.../BeautyQVariantMaterializer.scala:17-38`
- **exact-version framework evidence refs**: none positive; the materializer is the repository-owned `search-gen2-core` `SearchMaterializer`.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQProjectedDocumentsFingerprint.compute` delegates field traversal/ordering/hashing to the repository-owned `SearchProjectedDocumentsFingerprint` with BeautyQ encoding/projection versions and the document declaration; `BeautyQVariantMaterializer.FromSnapshotSource.load` delegates to the repository-owned `SearchMaterializer`. No `1.2.25` framework surface provides projected-document fingerprinting or materialization.
- **localSemanticsNotCoveredByFramework**: the projected-document fingerprint binding and the variant materialization boundary.
- **strongestCounterexampleChecked**: tested `b0b.from-resource`/materialization-adjacent surfaces; the materializer is repository-owned and no Distage analogue exists.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-25 — Accumulating per-variant projection errors

- **candidateId**: `C-25`
- **needId**: `N-C25-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-materialization/src/main/scala/leaderboard/search/beautyq/gen2/materialization/BeautyQVariantProjectionGen2.scala:16-213`; `.../BeautyQVariantProjectionError.scala`
- **exact-version framework evidence refs**: none positive; B1 nearest `DIError.aggregateErrors`/`ProvisioningFailure.AggregateFailure` aggregates DI planning/provisioning errors, a different domain.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQVariantProjectionGen2.project` builds indexed lookups, accumulates all per-variant join/invariant/schema errors without fail-fast, and builds a `VariantSearchDocumentGen2` only on an empty error vector; the error model is the repository-owned `NonEmptyErrors[BeautyQVariantProjectionError]`. Distage aggregates DI errors, not domain projection errors.
- **localSemanticsNotCoveredByFramework**: per-variant join/invariant/schema error accumulation and document build-on-empty.
- **strongestCounterexampleChecked**: tested `DIError`/`ProvisioningFailure.AggregateFailure`; they aggregate DI errors, a different domain, so no duplication.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-26 — Canonical navigation entry point

- **candidateId**: `C-26`
- **needId**: `N-C26-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `beautyq-search-gen2-wiring/src/main/scala/leaderboard/search/beautyq/gen2/wiring/BeautyQSearchGen2.scala:11-59`
- **exact-version framework evidence refs**: none positive; `b0a.module-compose`/`b0a.binding-dsl` compose DI bindings, not a domain navigation facade.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: `BeautyQSearchGen2` exposes `contract`, `input`, `materialization`, `plan`, `elasticsearch`, `qdrant`, `supplement`, `application` as typed handlers over `BeautyQSearchDeclarations`. This is a domain navigation facade; no `1.2.25` framework surface binds typed domain handlers to a declaration.
- **localSemanticsNotCoveredByFramework**: the canonical navigation entry point and handler binding.
- **strongestCounterexampleChecked**: tested DI module/binding composition; it organizes DI bindings, not a domain navigation root, so no duplication.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-27 — Build/module dependency DAG

- **candidateId**: `C-27`
- **needId**: `N-C27-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `build.sbt:180-252` (BeautyQ Gen2 module DAG; app-shell edges `:242-252`)
- **exact-version framework evidence refs**: none positive; `b0c.plan-check`/`b0c.plan-verify` address DI-graph correctness, not sbt build dependency policy.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: the BeautyQ Gen2 module DAG is expressed by sbt `dependsOn` (`beautyqSearchGen2Contract → searchGen2Contract, repoCore, beautyqModel`; `beautyqSearchGen2Materialization → …, searchGen2Core, beautyqSearchRepositories, repoCore, beautyqModel`; `beautyqSearchGen2Wiring → contract, materialization, searchGen2Core, searchGen2Elasticsearch, searchGen2Qdrant`; `beautyqSearchGen2Eval`; app-shell test-scope edges). No Distage/Izumi surface expresses sbt build/module ownership edges.
- **localSemanticsNotCoveredByFramework**: the sbt module dependency DAG.
- **strongestCounterexampleChecked**: tested `PlanCheck`/`PlanVerifier`; they validate the DI graph, not the sbt project graph, so no framework substitute exists.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-28 — Generic main-source neutrality firewall

- **candidateId**: `C-28`
- **needId**: `N-C28-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2ModuleFirewallSpec.scala:56-67`
- **exact-version framework evidence refs**: none positive; `b0c.plan-check` (`framework :: izumi/distage/framework/PlanCheck.scala`) is DI-graph-only.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: the firewall is a repository-owned scalatest that walks `search-gen2-*` main sources and fails if any contains `beautyq` (case-insensitive) after comment stripping. No `1.2.25` framework surface enforces main-source domain-name neutrality; `PlanCheck` validates DI wiring.
- **localSemanticsNotCoveredByFramework**: the source-text neutrality firewall.
- **strongestCounterexampleChecked**: tested `PlanCheck`; it cannot see source text or module ownership, so no framework substitute exists.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-29 — SQL confinement firewall

- **candidateId**: `C-29`
- **needId**: `N-C29-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2ModuleFirewallSpec.scala:110-133`
- **exact-version framework evidence refs**: none positive; `b0c.diagnostic` `LocatorPrivacy` (`core-api :: izumi/distage/model/definition/LocatorPrivacy.scala`) governs DI runtime visibility, not source/module confinement.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: the firewall is a repository-owned scalatest import-pattern detector: only `beautyq-search-gen2-materialization` may reference `leaderboard.sql.SQL`, and only the exact form. No `1.2.25` framework surface confines a storage/SQL capability to a source module.
- **localSemanticsNotCoveredByFramework**: the source-text SQL confinement firewall.
- **strongestCounterexampleChecked**: tested `LocatorPrivacy`; it governs DI runtime binding visibility, a different layer from source/module confinement, so no duplication/omission.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-30 — BeautyQ production package ownership firewall

- **candidateId**: `C-30`
- **needId**: `N-C30-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2ModuleFirewallSpec.scala:135-147`
- **exact-version framework evidence refs**: none positive.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: the firewall is a repository-owned scalatest that fails if any BeautyQ production source declares a package under `leaderboard.search.gen2`/`.gen2.*`. No `1.2.25` framework surface enforces source package ownership.
- **localSemanticsNotCoveredByFramework**: the package-declaration ownership firewall.
- **strongestCounterexampleChecked**: tested `LocatorPrivacy`/package aliases; they concern DI visibility/naming, not source package ownership.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-31 — Eval build-dependency isolation firewall

- **candidateId**: `C-31`
- **needId**: `N-C31-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2ModuleFirewallSpec.scala:79-108`; `build.sbt:242-252`
- **exact-version framework evidence refs**: none positive.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: the firewall is a repository-owned scalatest over `build.sbt` text: serving Gen2 projects must not depend on `beautyqSearchGen2Eval`; `leaderboard-app-shell` must reference it exactly once and only as `% "test->test"`. No `1.2.25` framework surface enforces build classpath isolation.
- **localSemanticsNotCoveredByFramework**: the eval build-dependency isolation firewall.
- **strongestCounterexampleChecked**: tested `PlanCheck`; it checks the DI graph, not the sbt classpath, so no framework substitute exists.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

### C-32 — Root aggregate coverage firewall

- **candidateId**: `C-32`
- **needId**: `N-C32-01`
- **B1 evidenceState / coverage**: `SEARCH_BOUNDED_ABSENCE` / —
- **frameworkSurfaceIds**: `[]`
- **BeautyQ source anchors**: `search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2ModuleFirewallSpec.scala:69-77`; `build.sbt:258-279`
- **exact-version framework evidence refs**: none positive.
- **comparisonOutcome**: `NO_RELEVANT_PRIMITIVE`
- **comparisonBasis**: the firewall is a repository-owned scalatest that reads the `distage-example` aggregate block and fails if any retained Gen2/shared project is missing. No `1.2.25` framework surface enforces sbt aggregate membership.
- **localSemanticsNotCoveredByFramework**: the root aggregate coverage firewall.
- **strongestCounterexampleChecked**: tested graph `Roots`/`PlanCheck`; DI graph roots are unrelated to sbt aggregate membership, so no duplication/omission.
- **evidenceGap**: none
- **stateId / versionRef**: `50e7945-clean` / `io.7mind.izumi 1.2.25`

---

## Phase C Mechanical Summary

### Distribution

| Outcome | Count |
| --- | --- |
| `DIRECT_USE` | 7 |
| `LOCAL_DUPLICATION_OR_BYPASS` | 0 |
| `MISSING_COMPOSITION` | 0 |
| `NO_RELEVANT_PRIMITIVE` | 25 |
| **sum of comparison outcomes** | **32** |
| evidence gaps (carried forward, non-blocking) | 5 |

`DIRECT_USE` (7): `C-01`, `C-02`, `C-03`, `C-04`, `C-05`, `C-07`, `C-11`.
`NO_RELEVANT_PRIMITIVE` (25): `C-06`, `C-08`, `C-09`, `C-10`, `C-12`, `C-13`, `C-14`, `C-15`, `C-16`, `C-17`, `C-18`, `C-19`, `C-20`, `C-21`, `C-22`, `C-23`, `C-24`, `C-25`, `C-26`, `C-27`, `C-28`, `C-29`, `C-30`, `C-31`, `C-32`.

### Change-oriented candidates

None. No candidate was assessed `LOCAL_DUPLICATION_OR_BYPASS` or `MISSING_COMPOSITION`.

The only candidate whose change-oriented interpretation was seriously tested was `C-05` (the `@unused val _edge` readiness edge). The strongest alternatives were `LOCAL_DUPLICATION_OR_BYPASS` and `MISSING_COMPOSITION` via `b1.lifecycle.dependency-edge` (`addDependency`); both were fully counterexamined and defeated because the current code already expresses the ordering edge with ordinary constructor dependency injection (a first-class Distage mechanism), and `@unused` only suppresses an unused-parameter warning. All other candidates were either direct framework use around legitimate product wrappers or genuinely outside Distage's abstraction (repository-owned `search-gen2-*` kernel, domain model, or build/test mechanics).

### Candidates with evidence gaps

Five candidates carry an inherited bounded-search evidence gap (a differently named `1.2.25` surface could exist; unresolved), each non-blocking and each still assessed `NO_RELEVANT_PRIMITIVE` within the bounded exact-version search:

- `C-06` — serving state / immutable startup evidence.
- `C-08` — serving-status aggregation.
- `C-10` — resource-name→typed-config translation.
- `C-16` — resource-name namespace derivation.
- `C-20` — response projection / diagnostic traces.

No candidate is `BLOCKED_NEED_EVIDENCE`; Phase C does not introduce blocked rows.

---

## Validation record (mechanical, Phase C scope)

- `C-01`..`C-32` appear exactly once each as `ComparisonRecord`s: **32 records, 0 missing, 0 duplicate** (`C1` = 20, `C2` = 12).
- Every `needId` exists in `research/01`: `N-C01-01`..`N-C32-01` (32 unique).
- Every cited `frameworkSurfaceId` exists in `research/02` (`b0a.*`, `b0b.*`, `b0c.*`, `b0d.*`, `b1.*`); empty lists are used only for `NO_RELEVANT_PRIMITIVE`.
- Every framework claim is version-bound to `io.7mind.izumi 1.2.25`; no latest/`master` evidence is used.
- Every BeautyQ claim cites a `research/01`-anchored `file:line`.
- No `LOCAL_DUPLICATION_OR_BYPASS` or `MISSING_COMPOSITION` row exists; therefore no change-oriented row lacks an adversarial check. `strongestCounterexampleChecked` is recorded for all 32 records.
- No Phase-D discoverability verdict and no Phase-E headline classification label appears in this artifact.
- No recommendation or proposed change appears in this artifact.
- Nothing in `research/00`, `research/01`, `research/02`, source, build, project, or framework files was modified by this phase.


---

## D — Discoverability

Phase-D assessment owned by `T015`. This is the **discoverability field only**
(`DISCOVERABLE` / `HARD_TO_DISCOVER` / `INDETERMINATE` / `N_A`), **not** the Phase-E
headline classification. No five-way label, no genericity filter, and no recommendation
appears here. `HARD_TO_DISCOVER` here is the Phase-D data-model value at most; no such
row exists, so no Phase-E headline spelling is emitted.

**Question.** For each `C-01`..`C-32`: if a real, semantically relevant exact-version
Distage/Izumi `1.2.25` surface exists, could a competent developer or agent reasonably
discover and compose it from public API names, package/object placement, scaladoc,
framework-provided examples/tests, and public convenience aliases/facades?

**Existence gate.** Discoverability is assessed only where Phase C established a
materially relevant surface. Where Phase C concluded `NO_RELEVANT_PRIMITIVE` because the
nearest framework API is a different abstraction, this section records `N_A`; absence or
unresolved existence is **not** discoverability evidence. B1 partial/nearest matches that
Phase C already discarded as abstraction-mismatched (`IntegrationCheck`, `Lifecycle`,
`Axis`, the binding DSL) are not resurrected as discoverability targets.

**Evidence order used (exact `1.2.25` only).** Public API names and package/object
placement (exact-version source jars, extraction `target/b0-src/`), API
scaladoc/comments, framework-provided examples/bundled definitions, and public aliases
and facades; deeper implementation source only to confirm a surface exists. No
latest/`master` evidence is used.

**Counterexample check.** For every row this section considered the standard checks:
public alias/facade missed, whether the API name is conventional for DI/resource users,
exact-version scaladoc example, framework test/example demonstrating composition, whether
the surface was only found by grep because the search was poor, and whether BeautyQ's own
direct use already indicates a normal discovery path. The strongest counterexample
considered is recorded in `discoverabilityBasis` for each non-`N_A` row.

**Scope note.** This section is appended by `T015` and is now the Phase-D owner of
`## D`; it supersedes the Phase-C-time scope statements that section `## D` was
intentionally absent and that no Phase-D verdict appears in this artifact. The `## C1`
and `## C2` comparison records are unchanged.

### Phase D rows

One row per `C-01`..`C-32`, each with a single Phase-D value. `relevantSurfaceIds` is `[]`
for `N_A` rows because Phase C established no materially relevant surface.

| candidateId | relevantSurfaceIds | discoverability | discoverabilityBasis | publicDiscoveryPath | remedyCandidates | evidenceGap | stateId/versionRef |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `C-01` | `b0a.plugin`, `b0a.module-compose`, `b0a.include-tags`, `b0a.role`, `b0a.activation-axis`, `b0a.binding-tag` | `DISCOVERABLE` | Public top-level types with scaladoc examples and official-doc links: `PluginDef` (include/plugin example; `distage-framework#plugins`), `PluginBase`, `PluginConfig`, `RoleAppMain` (full launcher example; `#roles`), `Activation` (example; `basics#activation-axis`), `ModuleDef` (exhaustive binding-authoring catalog incl. `include` and `tagged`), `StandardAxis.Scene` (explicitly describes `distage-framework-docker` managed scenes). Strongest counterexample: no single exact-version example shows the exact three-tagged-`ModuleDef` + `Activation` assembly, but each primitive (`include`, `tagged`, `Activation`) is documented and conventional, so correct composition is reachable. | Official docs (Basics → Activation Axis; distage-framework → Plugins/Roles) plus scaladoc on `ModuleDef`/`PluginDef`/`RoleAppMain`/`Activation`. | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-02` | `b0d.config-def`, `b1.config.typed-decode-error` | `DISCOVERABLE` | Public trait `izumi.distage.config.ConfigModuleDef` exposes conventionally named `makeConfig` / `makeConfigNamed` / `makeConfigWithDefault` / `wireConfig` and the `.fromConfig` extension; the required implicits `DIConfigReader` / `DIConfigMeta` are the standard "how is this decoded" clue; the framework itself uses `makeConfigWithDefault` in `DockerSupportModule.Configs`. Strongest counterexample: exact-version `ConfigModuleDef` has no scaladoc or doc link, but the public package and method naming are the obvious search target. Domain validation (`validateAtBoundary`) is not a framework surface and is excluded from this discovery question. | Public package `izumi.distage.config`; conventionally named API; in-framework usage in `DockerSupportModule`. | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-03` | `b0b.lifecycle-core`, `b0b.from-resource`, `b0b.from-effect`, `b0a.activation-axis`, `b1.lifecycle.dependency-edge`, `b0c.integration-check` | `DISCOVERABLE` | `Lifecycle` scaladoc gives `make`/`Lifecycle.Basic`/`Lifecycle.Make`/`Lifecycle.LiftF` plus cats `Resource` / scoped ZIO / `ZManaged` conversions with examples; `.fromResource` scaladoc gives a `cats.effect.Resource` example and `@see` to `Lifecycle`/`cats.effect.Resource`; `Activation` and `StandardAxis` document mode/scene axes. Strongest counterexample: there is no single combined Mode/Scene + `fromResource` example, but the managed-resource and activation pieces are each documented and compose by convention. | Docs (Basics → Lifecycle/Resources) plus scaladoc on `Lifecycle` and `ModuleDefDSL.fromResource`. | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-04` | `b0d.docker-container`, `b0d.docker-health`, `b0b.from-resource` | `DISCOVERABLE` | `ContainerDef` scaladoc shows the intended `make[Def.Container].fromResource { Def.make[F].dependOnContainer(...) }` binding and the `distage.type` label cleanup; `ContainerHealthCheck` exposes conventionally named factories (`postgreSqlProtocolCheck`, `httpGetCheck`, `httpsGetCheck`, `exitCodeCheck`, `portCheck`); `ContainerResource extends Lifecycle.Basic`, making `.fromResource` the documented binding path; the framework ships a commented `bundled/PostgresDocker` template and a `PostgresDockerModule` example. Strongest counterexample: no official-doc link appears in the exact-version docker sources, but the API names plus the bundled example make the managed-container path direct. | Public package `izumi.distage.docker`; `ContainerDef` scaladoc example; `bundled/PostgresDocker` template. | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-05` | `b0b.lifecycle-core`, `b0b.from-resource`, `b0b.ref-resource`, `b1.lifecycle.dependency-edge` | `DISCOVERABLE` | The capability needed by C-05 is an ordering/readiness edge between DI-provided resources, and that is expressed by ordinary constructor/function dependency injection, the first-class documented Distage mechanism (`ModuleDef` scaladoc: `make[X].from { y: Y => new X(y) }`). Nuance and strongest counterexample: the explicit no-parameter alternative `addDependency` is public (`AbstractBindingDefDSL.AddDependencyDSL`, `ModifyDSLBase#addDependency`) but has no scaladoc or doc link, so that specific helper is harder to discover on its own; the framework itself uses the `@unused` constructor-parameter idiom for an intentional `IntegrationCheck` edge. The candidate-level value describes the ordering capability actually needed, which is discoverable; the helper's weaker discoverability does not make the candidate hard to discover, and no claim about BeautyQ correctness is made. | Docs/scaladoc for provider/constructor dependency injection; `addDependency` is discoverable only from the DSL source. | `DOCS` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-06` | `[]` | `N_A` | Phase C established no semantically relevant Distage surface: the nearest framework mechanism `b0c.integration-check` is a provisioning readiness gate, not application serving state or immutable startup evidence. The abstraction mismatch is decisive, so discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | Phase-C bounded-search residual (non-blocking): a differently named `1.2.25` mechanism could in principle represent app serving status; not an established surface. | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-07` | `b0a.activation-axis`, `b0a.binding-tag`, `b0a.include-tags` | `DISCOVERABLE` | Framework typed selection/composition is public and documented: `Axis`/`AxisChoice` (name/value conventions), `Activation` (example plus `basics#activation-axis`), `StandardAxis`, `BindingTag.AxisTag`, and scaladoc'd `IncludesDSL#include` explaining tag/axis merge policy. Strongest counterexample: the stable-code vocabulary, ordering, default, and strict parser are BeautyQ semantics and no framework parser is expected; only typed axis selection is in question, and it is discoverable. | Docs `basics#activation-axis` plus scaladoc on `Activation`/`IncludesDSL`/`BindingTag`. | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-08` | `[]` | `N_A` | Phase C established no semantically relevant Distage surface for serving-status aggregation; the nearest mechanism `b0c.integration-check` gates provisioning, a different abstraction. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | Phase-C bounded-search residual (non-blocking), same as `C-06`. | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-09` | `[]` | `N_A` | Phase C discarded `b0c.integration-check` (`NonCriticalIntegrationFailure` is an integration/readiness marker) as abstraction-mismatched to a general runtime/startup failure classifier. No relevant surface; discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-10` | `[]` | `N_A` | Phase C discarded `.fromResource` and config decoding as abstraction-mismatched to resource-name→typed-config translation; the translation is repository/domain-owned. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | Phase-C bounded-search residual (non-blocking): a differently named translation surface could exist in an uninspected module; not an established surface. | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-11` | `b0b.lifecycle-core`, `b0b.from-resource`, `b0b.from-effect` | `DISCOVERABLE` | The resource-construction portion uses `Lifecycle` and `.fromResource`, both documented with examples (including a cats `Resource` example), and `fromEffect`, also documented with an example. Strongest counterexample: the aggregate/optional-component/partial-failure portion is domain-owned and outside this candidate's discovery question, so it is not penalized. | Docs (Basics → Lifecycle) plus `ModuleDef`/`ModuleDefDSL` scaladoc. | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-12` | `[]` | `N_A` | Phase C discarded `Lifecycle`/`.fromResource`; no Distage primitive provides backend lifecycle, config, or batching semantics. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-13` | `[]` | `N_A` | Phase C discarded `Axis`/binding tags: activation selects bindings at graph construction, not a post-acquisition runtime capability ADT. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-14` | `[]` | `N_A` | Phase C discarded the binding DSL: DI assembly does not provide executable-declaration, derivation, or fingerprint semantics. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-15` | `[]` | `N_A` | Same abstraction mismatch as `C-14` for the vector-store policy; binding a value is not declaration semantics. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-16` | `[]` | `N_A` | Phase C discarded `b0d.ownership-aliases` (`Id`/`.named` address DI identifier identity/visibility); the external resource-name namespace is domain-owned. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | Phase-C bounded-search residual (non-blocking): a differently named resource-name derivation surface is unresolved; not an established surface. | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-17` | `[]` | `N_A` | No generic backend request-compiler/response-decoder surface exists; binding a compiler through DI is assembly only. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-18` | `[]` | `N_A` | The binding DSL assembles DI bindings; it does not couple evaluation to an eligible/ineligible domain outcome. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-19` | `[]` | `N_A` | `b0c.plan-verify`/`b0c.plan-check` verify the DI graph, not domain search-plan compilation. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-20` | `[]` | `INDETERMINATE` | Phase C recorded a bounded-search residual specifically for the diagnostic-trace portion, which is adjacent to the framework's `b0c.diagnostic` rendering subsystem; existing evidence neither establishes a generic domain-result trace/render surface nor rules one out, so discoverability cannot yet be separated from absence. The public-response-projection portion is clearly domain-owned and not the source of this uncertainty. | not yet determinable: the exact-version `b0c.diagnostic`/rendering packages were not exhaustively searched for a generic domain-result trace renderer. | `NONE` | surface existence remains unresolved; discoverability cannot yet be separated from absence (diagnostic-trace portion). | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-21` | `[]` | `N_A` | `b0a.binding-dsl`/`b0a.module-compose` compose DI declarations; no framework surface provides a product-domain executable contract tree with derived structure/views. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-22` | `[]` | `N_A` | No framework surface provides source-table snapshot transactions, referential validation, or fingerprinting; `.fromResource` binds acquisition only. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-23` | `[]` | `N_A` | No framework surface computes order-independent canonical content fingerprints of domain values. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-24` | `[]` | `N_A` | No framework surface provides projected-document fingerprinting or materialization; the materializer is repository-owned. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-25` | `[]` | `N_A` | `DIError.aggregateErrors`/`ProvisioningFailure.AggregateFailure` aggregate DI planning/provisioning errors, a different domain from per-variant projection error accumulation. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-26` | `[]` | `N_A` | No framework surface binds typed domain handlers to a declaration; module/binding composition organizes DI bindings, not a domain navigation facade. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-27` | `[]` | `N_A` | No framework surface expresses sbt build/module dependency-DAG ownership; `b0c.plan-check`/`plan-verify` are DI-graph-only. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-28` | `[]` | `N_A` | Main-source neutrality against domain-name leakage is a repository-owned test mechanic; no framework analogue exists. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-29` | `[]` | `N_A` | `b0c.diagnostic` `LocatorPrivacy` governs DI runtime visibility, not source/SQL confinement; no framework analogue exists. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-30` | `[]` | `N_A` | Source package-ownership enforcement is a repository-owned test mechanic; no framework analogue exists. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-31` | `[]` | `N_A` | Build-dependency isolation of eval/test modules is a repository build/test mechanic; no framework analogue exists. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |
| `C-32` | `[]` | `N_A` | Root aggregate coverage of retained modules is a repository build/test mechanic; no framework analogue exists. Discoverability is not applicable. | not applicable (no relevant surface). | `NONE` | none | `50e7945-clean` / `io.7mind.izumi 1.2.25` |

### Phase D Mechanical Summary

Distribution of the single Phase-D value per candidate (each candidate appears exactly
once; sum = 32):

| Phase-D value | Count |
| --- | --- |
| `DISCOVERABLE` | 7 |
| `HARD_TO_DISCOVER` | 0 |
| `INDETERMINATE` | 1 |
| `N_A` | 24 |
| **sum** | **32** |

- `DISCOVERABLE` (7): `C-01`, `C-02`, `C-03`, `C-04`, `C-05`, `C-07`, `C-11`.
- `HARD_TO_DISCOVER` (0): none. The Phase-D field `HARD_TO_DISCOVER` is used only where a
  real relevant surface exists but public discovery is materially weak; no candidate met
  that bar.
- `INDETERMINATE` (1): `C-20` — surface existence for the diagnostic-trace portion is
  unresolved; discoverability cannot yet be separated from absence. Evidence that would
  resolve it: a targeted exact-version search of the `b0c.diagnostic`/rendering packages
  for a generic domain-result trace renderer, or source confirmation that only DI-graph
  rendering exists.
- `N_A` (24): `C-06`, `C-08`, `C-09`, `C-10`, `C-12`, `C-13`, `C-14`, `C-15`, `C-16`,
  `C-17`, `C-18`, `C-19`, `C-21`, `C-22`, `C-23`, `C-24`, `C-25`, `C-26`, `C-27`,
  `C-28`, `C-29`, `C-30`, `C-31`, `C-32`.
- `phaseCContradiction`: none. No Phase-D evidence showed that Phase C overlooked a real
  relevant surface; no `## C1`/`## C2` outcome was changed.

### Phase D validation record (mechanical)

- `C-01`..`C-32` appear exactly once in `## D`: **32 rows, 0 missing, 0 duplicate**.
- Every C candidate carries exactly one Phase-D value from `DISCOVERABLE` /
  `HARD_TO_DISCOVER` / `INDETERMINATE` / `N_A`.
- `HARD_TO_DISCOVER` count is **0**, so no row asserts a hard-to-discover surface; every
  `HARD_TO_DISCOVER` would have required a real `relevantSurfaceId`.
- `N_A` is used only where Phase C's accepted comparison outcome is
  `NO_RELEVANT_PRIMITIVE` with an abstraction-mismatched nearest surface; no real
  surface's discoverability was the question on those rows.
- The single `INDETERMINATE` (`C-20`) names the missing evidence explicitly.
- `remedyCandidates` values used: `NONE` and one `DOCS` (`C-05`, scoped to the explicit
  `addDependency` helper). No `HELPER_FACADE`, `NAMING`, or `EXAMPLES` is asserted.
- No Phase-E headline classification, no genericity filter, no second-consumer
  adjudication, and no recommendation appears in `## D`.
- No broad framework reinventory was performed: only the seven `DIRECT_USE` candidates
  and the five carried evidence-gap candidates were re-examined for discoverability.
