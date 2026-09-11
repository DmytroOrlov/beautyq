# Feature 003 — Adversarial Classified Findings

## Binding / method

| Field | Value |
| --- | --- |
| `path` | `specs/003-beautyq-distage-izumi-leverage-audit/research/04-classified-findings.md` |
| `question` | What is the current research state per material candidate and, when CLASSIFIED, its single evidence-backed headline label? |
| `writer` | Adversarial classifier (`T016` + `T017`) |
| `consumers` | `T018`–`T023` (synthesis/validation) |
| `staleWhen` | source state or resolved framework version changes |
| `stateId` | `50e7945-clean` |
| `versionRef` | `io.7mind.izumi` `1.2.25` |
| `phase` | Phase E only (classification + calibration + integrity check). No recommendation, no implementation, no `T018+`. |

Inputs consumed: `research/00-selected-state.md`, `research/01-beautyq-candidate-map.md`,
`research/02-framework-capability-map.md`, `research/03-comparison-and-discoverability.md`,
and the feature-local contracts `data-model.md`, `plan.md`, `tasks.md`, `spec.md`.

Accepted Phase C (source-level comparison, unchanged): `DIRECT_USE = 7` (`C-01`,`C-02`,`C-03`,`C-04`,`C-05`,`C-07`,`C-11`);
`LOCAL_DUPLICATION_OR_BYPASS = 0`; `MISSING_COMPOSITION = 0`; `NO_RELEVANT_PRIMITIVE = 25`.
Accepted Phase D (discoverability, unchanged): `DISCOVERABLE = 7`; `HARD_TO_DISCOVER = 0`; `INDETERMINATE = 1` (`C-20`); `N_A = 24`.

State model: each `C-01`..`C-32` carries exactly one current research state from
`CLASSIFIED` / `BLOCKED_NEED_EVIDENCE` / `INDETERMINATE` / `SUPERSEDED`. `CLASSIFIED` rows carry
exactly one headline label from `WELL_USED` / `UNDERUSED` / `HARD_TO_DISCOVER` /
`MISSING_GENERIC_PRIMITIVE` / `BEAUTYQ_SPECIFIC`. `BLOCKED_NEED_EVIDENCE` and `INDETERMINATE`
rows carry no headline label. No sixth label is invented. `BEAUTYQ_SPECIFIC` is read per the
data model as "correctly belongs at the BeautyQ/application/repository/domain layer rather than
in Distage/Izumi", so repository-owned search-kernel and build/test mechanics qualify.

Method: each tempting label was challenged with the label-specific counterexample search.
`MISSING_GENERIC_PRIMITIVE` was seriously considered for the status/degradation family
(`C-06`,`C-08`,`C-09`), the resource/config/name family (`C-10`,`C-16`), the search/domain
kernel family (`C-21`..`C-26`), and the build/firewall family (`C-27`..`C-32`); it was defeated
by the genericity filter or a decisive early failing condition in every case. `C-20` retains
`INDETERMINATE` because the generic diagnostic-trace surface-existence question remains
unresolved. Classification is evidence, not authorization.

---

## Classified / Current Research States

### Summary table

| candidateId | needId | status | headlineLabel | phaseC | phaseD |
| --- | --- | --- | --- | --- | --- |
| `C-01` | `N-C01-01` | `CLASSIFIED` | `WELL_USED` | `DIRECT_USE` | `DISCOVERABLE` |
| `C-02` | `N-C02-01` | `CLASSIFIED` | `WELL_USED` | `DIRECT_USE` | `DISCOVERABLE` |
| `C-03` | `N-C03-01` | `CLASSIFIED` | `WELL_USED` | `DIRECT_USE` | `DISCOVERABLE` |
| `C-04` | `N-C04-01` | `CLASSIFIED` | `WELL_USED` | `DIRECT_USE` | `DISCOVERABLE` |
| `C-05` | `N-C05-01` | `CLASSIFIED` | `WELL_USED` | `DIRECT_USE` | `DISCOVERABLE` |
| `C-06` | `N-C06-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-07` | `N-C07-01` | `CLASSIFIED` | `WELL_USED` | `DIRECT_USE` | `DISCOVERABLE` |
| `C-08` | `N-C08-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-09` | `N-C09-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-10` | `N-C10-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-11` | `N-C11-01` | `CLASSIFIED` | `WELL_USED` | `DIRECT_USE` | `DISCOVERABLE` |
| `C-12` | `N-C12-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-13` | `N-C13-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-14` | `N-C14-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-15` | `N-C15-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-16` | `N-C16-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-17` | `N-C17-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-18` | `N-C18-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-19` | `N-C19-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-20` | `N-C20-01` | `INDETERMINATE` | *(none)* | `NO_RELEVANT_PRIMITIVE` | `INDETERMINATE` |
| `C-21` | `N-C21-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-22` | `N-C22-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-23` | `N-C23-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-24` | `N-C24-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-25` | `N-C25-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-26` | `N-C26-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-27` | `N-C27-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-28` | `N-C28-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-29` | `N-C29-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-30` | `N-C30-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-31` | `N-C31-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |
| `C-32` | `N-C32-01` | `CLASSIFIED` | `BEAUTYQ_SPECIFIC` | `NO_RELEVANT_PRIMITIVE` | `N_A` |

---

### Direct-use group (WELL_USED)

#### C-01 — Plugin/module/activation composition
- candidateId / needId: `C-01` / `N-C01-01`
- status / headlineLabel: `CLASSIFIED` / `WELL_USED`
- phaseC / phaseD: `DIRECT_USE` (FULL) / `DISCOVERABLE`
- beautyqEvidence: `LeaderboardPlugin.scala:28-42`; `BeautySearchGen2PluginModules.scala:39-51`; `BeautyQSupplementStartup.scala:5-9`; `LeaderboardRole.scala:684-688`
- frameworkEvidence: `b0a.plugin`, `b0a.module-compose`, `b0a.include-tags`, `b0a.role`, `b0a.activation-axis`, `b0a.binding-tag` (all `1.2.25`; `extension-plugins :: PluginDef.scala`/`PluginConfig.scala`; `core-api :: Module.scala`/`ModuleBase.scala`; `dsl/IncludesDSL.scala`; `framework :: RoleAppMain.scala`; `Axis.scala`/`Activation.scala`/`StandardAxis.scala`; `BindingTag.scala`)
- justification: `LeaderboardPlugin extends PluginDef` and `include`s the generic module sets plus `BeautySearchGen2PluginModules.api`; the three supplement variants are `ModuleDef`s carrying `tag(choice: Axis.AxisChoice)` and are selected by `BeautyQSupplementStartup extends Axis` + `Activation`. This is Distage's intended plugin/module/axis/activation composition used directly; the only remainder is BeautyQ axis/choice/policy naming.
- secondarySignal: none
- counterexampleSearch: hunted for a locally authored selector or a bypass of `include`/`Activation`; found the tagged `ModuleDef`s use Distage's own `AxisTag`+`Activation` mechanism, not a reimplementation, and the product remainder (axis/choice names, which three variants exist) is legitimate domain policy.
- genericityResult: not required (headline is not `MISSING_GENERIC_PRIMITIVE`).
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-02 — Typed app-shell config validation
- candidateId / needId: `C-02` / `N-C02-01`
- status / headlineLabel: `CLASSIFIED` / `WELL_USED`
- phaseC / phaseD: `DIRECT_USE` (PARTIAL) / `DISCOVERABLE`
- beautyqEvidence: `BeautyQGen2AppShellConfig.scala:37-85`; `BeautySearchGen2PluginModules.scala:28-37`
- frameworkEvidence: `b0d.config-def` (`extension-config :: ConfigModuleDef.scala`, `model/ConfTag.scala`, `codec/DIConfigReader.scala`); `b1.config.typed-decode-error` (`DIConfigReader.scala:85-100`; `DIConfigReadException.scala:5`) — all `1.2.25`
- justification: `appShellConfigModule` binds the raw HOCON value via `ConfigModuleDef.makeConfig[...]("beautyq-gen2-app-shell")`; `distage-extension-config` performs the decode directly. The extra `validateAtBoundary` positive-value/Qdrant-work-policy rule is a distinct, legitimate domain boundary policy that no framework surface claims.
- secondarySignal: the typed domain `BeautyQGen2AppShellConfigError`/`...Exception` remainder is domain-owned and intentionally local (not a second headline label).
- counterexampleSearch: adversarially considered `MISSING_COMPOSITION` — could the typed error be a bypass of a framework validation surface? No: the config *binding/decoding* is direct framework use, and `DIConfigReadException` covers decode failure only, not domain positive-value validation.
- genericityResult: not required.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-03 — Lifecycle/readiness selected by Mode/Scene
- candidateId / needId: `C-03` / `N-C03-01`
- status / headlineLabel: `CLASSIFIED` / `WELL_USED`
- phaseC / phaseD: `DIRECT_USE` (FULL) / `DISCOVERABLE`
- beautyqEvidence: `LeaderboardPlugin.scala:155-176`; `BeautyQSeedInserter.scala:50-77`
- frameworkEvidence: `b0b.lifecycle-core` (`fundamentals-bio :: Lifecycle.scala`); `b0b.from-resource` (`core-api :: dsl/ModuleDefDSL.scala:259-318`, `dsl/LifecycleAdapters.scala`); `b0a.activation-axis` (`Axis.scala`/`Activation.scala`/`StandardAxis.scala`); `b0c.integration-check`; `b1.lifecycle.dependency-edge` — all `1.2.25`
- justification: readiness is a framework `Lifecycle` resource bound with `.fromResource[BeautyQSeedReady.Noop/LoadAndInsert[F]]` and selected by the standard `Mode`/`Scene` axes. The seed-load/insertion mechanics and the readiness meaning are product-specific but sit on direct framework use.
- secondarySignal: none
- counterexampleSearch: tested whether readiness gating locally reimplements a framework feature; it does not — the resource is a framework `Lifecycle` selected by standard axes.
- genericityResult: not required.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-04 — Managed Docker modules
- candidateId / needId: `C-04` / `N-C04-01`
- status / headlineLabel: `CLASSIFIED` / `WELL_USED`
- phaseC / phaseD: `DIRECT_USE` (FULL) / `DISCOVERABLE`
- beautyqEvidence: `PostgresDockerPlugin.scala:15-36`; `ElasticsearchDockerPlugin.scala:13-47`; `QdrantGen2DockerPlugin.scala:14-47`
- frameworkEvidence: `b0d.docker-container` (`framework-docker :: ContainerDef.scala`, `DockerContainer.scala:38-*`, `impl/ContainerResource.scala`, `modules/DockerSupportModule.scala`); `b0d.docker-health` (`healthcheck/ContainerHealthCheck.scala`); `b0b.from-resource` — all `1.2.25`
- justification: each plugin includes `DockerSupportModule` and binds the container with `.fromResource(<Def>.make[...])`; `ElasticsearchDocker`/`QdrantGen2Docker` extend `ContainerDef` and declare `ContainerHealthCheck.httpGetCheck`; port bindings read the started container's `availablePorts`. These are the `distage-framework-docker` mechanics used directly; image/env/port choices are app/infra policy.
- secondarySignal: none
- counterexampleSearch: tested whether health-check or port wiring locally rebuilt `ContainerHealthCheck`/`availablePorts`; it does not.
- genericityResult: not required.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-05 — `BeautyQSeedReady` ordering edge
- candidateId / needId: `C-05` / `N-C05-01`
- status / headlineLabel: `CLASSIFIED` / `WELL_USED`
- phaseC / phaseD: `DIRECT_USE` (FULL) / `DISCOVERABLE`
- beautyqEvidence: `BeautySearchGen2PluginModules.scala:56-60`
- frameworkEvidence: `b1.lifecycle.dependency-edge` (`core-api :: dsl/AbstractBindingDefDSL.scala:236-248, 306-329`; `dsl/ModuleDefDSL.scala:202-206`); `b0b.lifecycle-core`; `b0b.from-resource`; `b0b.ref-resource` — all `1.2.25`
- justification: the binding function `(seedReady: BeautyQSeedReady, sql: SQL[IO], clock: JClock) => ...` already makes `BeautyQSeedReady` an ordinary constructor/function dependency of `BeautyQSearchSnapshotSource.Postgres`, which is a first-class Distage ordering mechanism; `@unused val _edge = seedReady` only suppresses an unused-parameter warning. The framework itself uses the same `@unused` constructor-parameter idiom for an intentional `IntegrationCheck` edge (`DockerClientWrapper.scala:117`).
- secondarySignal: the explicit no-parameter alternative `addDependency` (`AbstractBindingDefDSL.AddDependencyDSL`) is public but has no scaladoc/doc link; this is a narrow helper-level discoverability wrinkle (Phase-D `DOCS` remedy candidate), not a candidate-level `HARD_TO_DISCOVER` and not a second headline label.
- counterexampleSearch: adversarially tested `LOCAL_DUPLICATION_OR_BYPASS` and `MISSING_COMPOSITION` via `addDependency`; both were defeated — `addDependency` is semantically equivalent but the current constructor dependency already expresses the edge with the primary framework mechanism, and replacing it would remove one unused parameter and one `@unused val` (cosmetic only). Hunted for a documented product reason the local form exists; the form is a framework idiom, not a local reinvention.
- genericityResult: not required. (The `addDependency` helper already exists in the pinned framework, so this is not a missing-primitive question at all.)
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-07 — Typed startup policy vocabulary
- candidateId / needId: `C-07` / `N-C07-01`
- status / headlineLabel: `CLASSIFIED` / `WELL_USED`
- phaseC / phaseD: `DIRECT_USE` (PARTIAL) / `DISCOVERABLE`
- beautyqEvidence: `BeautyQSupplementReadinessPolicy.scala:7-41` and `:43-54`; `BeautyQSupplementStartup.scala:5-9`
- frameworkEvidence: `b0a.activation-axis`; `b0a.binding-tag` (`BindingTag.scala`, `dsl/Tagging.scala`); `b0a.include-tags` (`dsl/IncludesDSL.scala`) — all `1.2.25`
- justification: the startup variant is selected through `BeautyQSupplementStartup extends Axis` + `Activation` and bound under `tag(choice)` with `make[SupplementStartupPolicy].fromValue(policy)`; Distage typed variant selection/composition is used directly. Stable codes, `ordered`, `Default`, and the strict `fromStableCode` parser are domain-owned.
- secondarySignal: none
- counterexampleSearch: tested whether `fromStableCode`/`stableCode` duplicates a framework code parser; no framework surface parses domain policy codes, and the axis selection is used directly.
- genericityResult: not required.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-11 — Multi-backend activation aggregate
- candidateId / needId: `C-11` / `N-C11-01`
- status / headlineLabel: `CLASSIFIED` / `WELL_USED`
- phaseC / phaseD: `DIRECT_USE` (PARTIAL) / `DISCOVERABLE`
- beautyqEvidence: `BeautyQSearchGenerationApplication.scala:23-133`; binding at `BeautySearchGen2PluginModules.scala:172-179` and `:199-205`
- frameworkEvidence: `b0b.lifecycle-core`; `b0b.from-resource` (`dsl/ModuleDefDSL.scala:259-318`); `b0b.from-effect` (`dsl/ModuleDefDSL.scala:216-222`) — all `1.2.25`
- justification: `activate` composes ES compile/activate with Qdrant prepare/embed/activate into an `Activation` aggregate; the app-shell exposes the startup aggregate as a Distage resource via `BeautyQSearchGen2Startup.lifecycle` (a `Lifecycle.LiftF`) bound with `.fromResource`. The framework-addressable resource-construction/release portion is direct use; the optional-component aggregate and typed partial failure are domain remainder.
- secondarySignal: none
- counterexampleSearch: tested `LOCAL_DUPLICATION_OR_BYPASS`; `Lifecycle` does not model an optional-field aggregate or typed partial failure, and the aggregate is pure domain logic over repository-kernel types; the startup resource itself is a direct `Lifecycle` binding.
- genericityResult: not required.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

---

### Serving / degradation / status family

#### C-06 — Startup serving status / evidence / response projection
- candidateId / needId: `C-06` / `N-C06-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSearchGen2Bootstrap.scala:30-107`; `BeautyQSearchGen2Startup.scala:24-241`; `BeautyQSearchGen2Runtime.scala:14-42`; `BeautyQSearchGen2HttpService.scala:14-141`
- frameworkEvidence / bounded absence: no relevant `1.2.25` surface; B1 bounded search (LF-3) over `core`, `core-api`, `framework`, `framework-docker`, `testkit-core` found only test-runner `TestStatus` and provisioning `OpStatus`; nearest `b0c.integration-check` (`IntegrationCheck.scala`, `NonCriticalIntegrationFailure.scala`) is a provisioning readiness gate, considered and abstraction-mismatched.
- justification: the capability is application-level serving state, immutable startup evidence, and public status projection (`BeautyQSearchStartupEvidence`, `StartupServingStatus`, `encodeStatus`). Distage/Izumi's concern is DI graph construction, lifecycle, and readiness gating, not an application serving-status algebra. De-named, a "serving status" model could exist, but its natural central owner is an application/http-health layer, not the DI framework; binding arbitrary values through DI does not make status algebra a DI primitive.
- secondarySignal: none
- counterexampleSearch: hunted for a generic framework status/degradation primitive; checked `IntegrationCheck`/`ResourceCheck`/`NonCriticalIntegrationFailure` and the diagnostic renderers — none represents app serving state or immutable startup evidence. Because a generic status primitive was tempting, the genericity screen was run.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected. Decisive failing conditions: `recurrence = FAIL` (the only occurrence is the single BeautyQ startup story; `C-08`/`C-09` are the same BeautyQ pattern, not an independent second consumer), `noProductSemantics = FAIL` (status carries serving modes, ES/Qdrant references, degradation reasons), `secondConsumer = FAIL` (no consumer other than BeautyQ is named), and the natural central owner is the application layer rather than Distage. Because these conditions fail, the missing-generic hypothesis is not materially plausible; a full eight-condition `ALLOWED` is not available. Residual bounded-search uncertainty (a differently named mechanism could exist) is the same non-blocking Phase-C residual and does not defeat the natural-owner conclusion.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-08 — Serving-status aggregate
- candidateId / needId: `C-08` / `N-C08-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSupplementReadinessPolicy.scala:56-154`
- frameworkEvidence / bounded absence: no relevant `1.2.25` surface; B1 nearest `b0c.integration-check` (readiness gate) considered, abstraction-mismatched. Same bounded residual as `C-06`.
- justification: `StartupServingStatus` binds policy + serving mode + `condition` + optional typed `Reason` + `restartRequired` + source/projected fingerprints + ES/Qdrant references with private `healthy`/`degraded`/`limited` constructors and derived `supplementReady`. This is the serving-status algebra of the BeautyQ application; its natural owner is the application/domain layer, not Distage.
- secondarySignal: none
- counterexampleSearch: same check as `C-06`; no framework status-aggregation surface exists, and `IntegrationCheck`/`ResourceCheck` gate provisioning rather than aggregate serving status. Genericity screen run because a generic status primitive was tempting.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected. Decisive failing conditions: `recurrence = FAIL` (same single BeautyQ startup pattern as `C-06`/`C-09`), `noProductSemantics = FAIL` (typed `Reason`, serving modes, backend identity), `secondConsumer = FAIL`, and natural central owner is not Distage. The missing-generic hypothesis is not materially plausible.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-09 — Hard/degradable failure policy
- candidateId / needId: `C-09` / `N-C09-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSupplementPolicy.scala:10-243` (`:46-47`, `:49-233`)
- frameworkEvidence / bounded absence: no relevant positive surface; nearest `b0c.integration-check` (`IntegrationCheck.scala`, `NonCriticalIntegrationFailure.scala`) considered, abstraction-mismatched. No evidence gap recorded in Phase C/D.
- justification: `BeautyQSupplementPolicy` classifies BeautyQ's own error ADTs (`BeautyQQdrantCandidatePipelineError`, `BeautyQSearchGenerationActivationError`, `QdrantGenerationLifecycleError`, `Gen2HttpTransportError`) into `Hard`/`Degradable` with `BeautyQDegradationReason` stable codes, and owns the append budget through `AppendOnlySupplementPolicy`. `NonCriticalIntegrationFailure` marks a provisioner integration/readiness failure, not a general runtime/startup failure classifier, and BeautyQ is not an `IntegrationCheck` producer at this site.
- secondarySignal: none
- counterexampleSearch: tested `LOCAL_DUPLICATION_OR_BYPASS` (does BeautyQ rebuild `NonCriticalIntegrationFailure`? no — different stage and error domain) and `MISSING_COMPOSITION` (no `IntegrationCheck` producer is involved). Genericity screen run because a generic failure-classification primitive was tempting.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected. Decisive failing conditions: `recurrence = FAIL` (same single BeautyQ policy site), `noProductSemantics = FAIL` (BeautyQ error ADTs and degradation codes), `secondConsumer = FAIL`, and the classification ladder plus append budget is domain policy, not infrastructural mechanics with a natural Distage owner. Missing-generic is not materially plausible.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-20 — Response projection and diagnostic traces
- candidateId / needId: `C-20` / `N-C20-01`
- status / headlineLabel: `INDETERMINATE` / *(none)*
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `INDETERMINATE`
- beautyqEvidence: `BeautyQSearchResponseGen2.scala:32-354`; `BeautyQSearchPlanCompilationTrace.scala:14-56`; `BeautyQCandidatePlanTrace.scala:12-50`
- frameworkEvidence / bounded absence: B1 found no generic surface for public response projection; nearest `b0c.diagnostic` (`DepTreeRenderer.scala`, `DIPlanCompactFormatter.scala`, `DIRendering.scala`) renders DI-graph diagnostics, a different abstraction/domain.
- justification: the candidate combines two parts. The public-response-projection portion is clearly application/domain-owned. The diagnostic-trace portion is adjacent to the framework's real `b0c.diagnostic` rendering subsystem, and Phase D is `INDETERMINATE` precisely because a generic domain-result trace/render surface existence is unresolved: existing evidence neither establishes such a surface nor rules one out. Because the combined candidate contains a material part whose surface existence is unresolved, a single headline label cannot be chosen without curing the uncertainty by assertion.
- competingInterpretations: (a) `BEAUTYQ_SPECIFIC` if the diagnostic-trace portion is domain-owned and the `b0c.diagnostic` similarity is abstraction-mismatched (as Phase C assessed for the response-projection portion); (b) potentially a real framework trace/render surface if the exact-version `b0c.diagnostic`/rendering packages contain a generic domain-result trace renderer that Phase C/D did not exhaustively search.
- missingEvidence: targeted exact-version (`io.7mind.izumi 1.2.25`) search of the `b0c.diagnostic`/rendering packages for a generic domain-result trace/render surface, or source confirmation that only DI-graph rendering exists.
- wouldUnblock: that targeted search result would separate (a) from (b); until then the candidate stays `INDETERMINATE` with no headline label. No label is fabricated to fill the slot.
- secondarySignal: none
- genericityResult: not assigned. (No `MISSING_GENERIC_PRIMITIVE` is claimed, so the full eight-condition gate is not triggered; the `MISSING_GENERIC_PRIMITIVE` hypothesis is not the competing interpretation here — surface existence itself is unresolved.)
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

---

### Resource / config / name family

#### C-10 — Resource-name → typed runtime config
- candidateId / needId: `C-10` / `N-C10-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQQdrantRuntime.scala:6-24`
- frameworkEvidence / bounded absence: no relevant `1.2.25` surface; `.fromResource` (binds acquisition only) and config decoding (`DIConfigReadException`) are abstraction-mismatched nearest surfaces (B1).
- justification: `BeautyQQdrantRuntime.lifecycle`/`candidateService` translate `BeautyQSearchGen2ResourceNames` into repository-kernel `QdrantGenerationLifecycleConfig.create` / `QdrantCandidateServiceConfig.create`, returning typed `Either` errors. The translation targets repository search-kernel config types; its natural owner is the repository kernel, not Distage, and DI ownership of construction does not make name→config translation a DI primitive.
- secondarySignal: none
- counterexampleSearch: tested `LOCAL_DUPLICATION_OR_BYPASS` against `fromResource` and config surfaces; neither translates names into typed backend configs. Considered `MISSING_GENERIC_PRIMITIVE`; rejected by genericity screen.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected before a full filter: `noProductSemantics = FAIL` (targets repository-kernel Qdrant config types), the natural central owner is the repository search kernel rather than Distage, and no independent `secondConsumer` is evidenced. Missing-generic is not materially plausible.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-12 — Reusable backend lifecycle/service construction
- candidateId / needId: `C-12` / `N-C12-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQElasticsearchBaselineService.scala:27-76`
- frameworkEvidence / bounded absence: no positive surface; Phase C showed the constructed lifecycle is the repository-owned `search-gen2-elasticsearch` `ElasticsearchGenerationLifecycle` (a plain class, not a Distage `Lifecycle`), and the app-shell uses an ordinary `make[...].from` singleton binding. B1's `b0b.lifecycle-core`/`from-resource` PARTIAL match did not survive source inspection.
- justification: `make` is a pure factory constructing a repository-kernel lifecycle + baseline service from resource names and batching; the semantics (name→config translation, request batching, the single `searchBound` path) are repository-kernel/domain. The natural owner is the repository's `search-gen2-*` kernel rather than Distage.
- secondarySignal: none
- counterexampleSearch: tested `b0b.from-resource`/`b0b.lifecycle-core` (not used here) and `b0a.binding-dsl` (assembles the value but adds no backend-lifecycle/config/batching capability). Considered `MISSING_GENERIC_PRIMITIVE`; rejected.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL` (repository search-kernel lifecycle/batching), natural central owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-16 — Resource-name namespace
- candidateId / needId: `C-16` / `N-C16-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSearchGen2ResourceNames.scala:6-13`
- frameworkEvidence / bounded absence: no relevant `1.2.25` surface; B1 notes `b0d.ownership-aliases` (`Id`, `.named`) concerns DI locator identity/visibility, a different abstraction.
- justification: the type owns the ES alias / physical index prefix / Qdrant alias / physical collection prefix namespace as names only. No framework surface derives backend physical/virtual resource names; DI `Id`/`.named` address locator identity, not external resource names. The natural owner is the repository/backend layer.
- secondarySignal: none
- counterexampleSearch: tested `b0d.ownership-aliases`; DI identifiers are not backend resource names. Considered `MISSING_GENERIC_PRIMITIVE` (resource-name derivation); rejected.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected before a full filter: `noProductSemantics = FAIL` (backend resource naming is infra/product policy), natural central owner is not Distage, and `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

---

### Search / domain kernel family

The generic `search-gen2-*` kernel is repository-owned infrastructure, not Distage/Izumi. Distage's ability
to bind arbitrary Scala values does not imply framework ownership of these domain/kernel mechanics; the
natural generic owner is the repository search kernel.

#### C-13 — Full vs baseline capability
- candidateId / needId: `C-13` / `N-C13-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSearchApplication.scala:20-88`
- frameworkEvidence / bounded absence: no positive surface; B1 nearest `b0a.activation-axis`/`b0a.binding-tag` considered and not materially applicable to this construction boundary; `b0b.optional-set` not counted (the local form is an optional result, not an injected empty/set contribution).
- justification: `BeautyQSearchApplication` holds a private `SupplementCapability` (`Full`/`Baseline`) chosen at construction from the outcome of a lifecycle acquisition effect, then `execute` routes accordingly. Activation selects bindings at graph-construction time and cannot select a post-acquisition runtime capability; the private capability ADT and routing are domain behavior.
- secondarySignal: none
- counterexampleSearch: tested `b0a.activation-axis`/`b0a.binding-tag` (activation selects bindings at graph construction, not a post-activation runtime outcome) and `b0b.optional-set`; the activation-axis interpretation was defeated.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` not pursued as materially plausible: the mechanic is a domain capability ADT with `noProductSemantics = FAIL` and its natural owner is the application/search layer.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-14 — Elasticsearch policy as executable declaration
- candidateId / needId: `C-14` / `N-C14-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQElasticsearchPolicy.scala:17-67` (`:25-31`, `:41-47`)
- frameworkEvidence / bounded absence: no positive surface; B1 nearest `b0a.binding-dsl` considered, abstraction-mismatched. The declaration is built with the repository-owned `search-gen2-elasticsearch` `ElasticsearchIndexPolicy.unsafeFrom`/`ElasticsearchPolicy.unsafeFrom`.
- justification: the Need — an executable search-policy declaration with derived contributions and a contract fingerprint — is repository-owned; the declaration DSL and fingerprint are `search-gen2-*`, and the natural owner is the repository search kernel. The DI binding DSL is a different abstraction and is not the source of declaration/derivation/fingerprint semantics.
- secondarySignal: none
- counterexampleSearch: tested `LOCAL_DUPLICATION_OR_BYPASS` against `b0a.binding-dsl`; the DSL would only assemble the object and provides none of the declaration/derivation/fingerprint semantics, and it is not exercised at this site.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL` (search policy vocabulary), natural central owner is the repository `search-gen2-*` kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-15 — Qdrant policy as executable declaration
- candidateId / needId: `C-15` / `N-C15-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQQdrantPolicy.scala:9-29`
- frameworkEvidence / bounded absence: no positive surface; B1 nearest `b0a.binding-dsl` considered, abstraction-mismatched; declaration built with repository-owned `search-gen2-qdrant` `QdrantPolicy.unsafeFrom`.
- justification: the declaration-owned semantics (identity, embedding field, vector name, model identity, distance, retrieval) are repository-owned; binding a value is not declaration semantics. Natural owner is the repository search kernel.
- secondarySignal: none
- counterexampleSearch: tested `LOCAL_DUPLICATION_OR_BYPASS` against `b0a.binding-dsl`; binding an object is not the declaration semantics.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL`, natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-17 — Delegate to generic backend compiler/decoder
- candidateId / needId: `C-17` / `N-C17-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQElasticsearchGeneration.scala:16-19`; `BeautyQElasticsearchBaseline.scala:21-31`
- frameworkEvidence / bounded absence: no positive surface; Distage provides no generic backend request compiler/response decoder; `b0a.binding-dsl` can bind such a compiler but does not compile/decode.
- justification: the delegates are repository-owned `search-gen2-elasticsearch` `ElasticsearchGenerationCompiler.compile` / `ElasticsearchSearchRequestCompiler` / `ElasticsearchSearchResponseDecoder`. Natural owner is the repository backend kernel, not Distage.
- secondarySignal: none
- counterexampleSearch: tested `b0a.binding-dsl`; DI assembly is not backend compilation/decoding.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL` (backend compiler/decoder is search-domain), natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-18 — Candidate pipeline binding + evaluation outcome
- candidateId / needId: `C-18` / `N-C18-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQQdrantCandidatePipeline.scala:10-73`
- frameworkEvidence / bounded absence: no positive surface; B1 nearest `b0a.binding-dsl` considered, abstraction-mismatched. The pipeline composes repository-owned `QdrantCandidatePipeline.execute`/`CandidateHydrator.hydrate`.
- justification: the private `Result` couples `CompiledCandidateEvaluation` with an ineligibility reason or hydrated candidate; coupling evaluation to a domain eligible/ineligible outcome is repository/domain behavior. Natural owner is the repository search kernel.
- secondarySignal: none
- counterexampleSearch: tested `LOCAL_DUPLICATION_OR_BYPASS` against `b0a.binding-dsl`; binding a value is not coupling evaluation to an outcome.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL`, natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-19 — Plan/candidate compilation composition
- candidateId / needId: `C-19` / `N-C19-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSearchPlanCompiler.scala:39-122`; `BeautyQCandidatePlanCompiler.scala:24-60`
- frameworkEvidence / bounded absence: no positive surface; Distage plan surfaces `b0c.plan-verify`/`b0c.plan-check` address DI-graph construction/verification, not domain search-plan compilation.
- justification: `compile` composes repository-owned `PublicPlanInputResolver`, `SearchPlanCompilationKernel`, `SearchCursorEnvelope`, `BeautyQSearchPlanPolicy` and returns private compiled results. This is domain search-plan compilation; natural owner is the repository search kernel.
- secondarySignal: none
- counterexampleSearch: tested `b0c.plan-verify`/`b0c.plan-check`; they verify the DI graph, not a domain search plan.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL`, natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-21 — Executable contract/declaration tree
- candidateId / needId: `C-21` / `N-C21-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSearchDeclarations.scala:22-366` (derived `structure` `:314-362`)
- frameworkEvidence / bounded absence: no positive surface; `b0a.binding-dsl`/`b0a.module-compose` are DI declarations, a different abstraction; the declaration DSL (`repo.catalog`, `searchFields`) and `structure`/`renderStructure` are repository-owned.
- justification: the executable contract tree is built from the repository-owned `search-gen2-*` topology/field DSL with derived `structure`/`renderStructure`; `ModuleDef` is an executable DI declaration, not a product-domain contract tree with derived views. Natural owner is the repository search kernel.
- secondarySignal: none
- counterexampleSearch: checked `b0a.binding-dsl`/`b0a.module-compose`/`b0a.plugin`; they compose DI bindings, not a domain contract tree with derived structure.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL` (product-domain declaration tree), de-naming does not yield a Distage-owned infrastructure API, natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-22 — Consistent snapshot transaction
- candidateId / needId: `C-22` / `N-C22-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSearchSnapshotSource.scala:16-228`; `BeautyQSearchSnapshot.scala:10-18`
- frameworkEvidence / bounded absence: no positive surface; `b0b.from-resource` only binds resource acquisition.
- justification: the snapshot uses a repository-owned doobie `SQL.readOnlyRepeatableRead` transaction, referential validation, and a content fingerprint; natural owner is the persistence/materialization layer, not Distage.
- secondarySignal: none
- counterexampleSearch: tested `b0b.from-resource`; it binds acquisition only and does not provide transactional snapshots.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL` (DB persistence semantics), natural owner is not Distage, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-23 — Order-independent canonical fingerprint
- candidateId / needId: `C-23` / `N-C23-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSnapshotFingerprint.scala:11-24`; `BeautyQSnapshotCanonicalRows.scala`
- frameworkEvidence / bounded absence: no positive surface; B1 found no framework fingerprint surface.
- justification: the fingerprint uses the repository-owned `CanonicalFingerprint.sha256HexTokens`/`CanonicalSnapshot.encode` token writer with `BeautyQSnapshotCanonicalRows` declaring the participating fields. Natural owner is the repository materialization kernel, not Distage.
- secondarySignal: none
- counterexampleSearch: no nearby Distage surface even approximately computes domain fingerprints.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL` (domain canonical fingerprint policy), natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-24 — Projected-document fingerprint and materialization
- candidateId / needId: `C-24` / `N-C24-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQProjectedDocumentsFingerprint.scala:10-20`; `BeautyQVariantMaterializer.scala:17-38`
- frameworkEvidence / bounded absence: no positive surface; delegation targets repository-owned `search-gen2-core` `SearchProjectedDocumentsFingerprint`/`SearchMaterializer`.
- justification: field traversal/ordering/hashing and variant materialization are repository-kernel mechanics; natural owner is the `search-gen2-*` kernel.
- secondarySignal: none
- counterexampleSearch: tested `b0b.from-resource`/materialization-adjacent surfaces; the materializer is repository-owned and no Distage analogue exists.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL`, natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-25 — Accumulating per-variant projection errors
- candidateId / needId: `C-25` / `N-C25-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQVariantProjectionGen2.scala:16-213`; `BeautyQVariantProjectionError.scala`
- frameworkEvidence / bounded absence: no positive surface; B1 nearest `DIError.aggregateErrors`/`ProvisioningFailure.AggregateFailure` aggregates DI planning/provisioning errors, a different domain.
- justification: per-variant join/invariant/schema error accumulation and document-build-on-empty are repository-kernel domain mechanics.
- secondarySignal: none
- counterexampleSearch: tested `DIError`/`ProvisioningFailure.AggregateFailure`; they aggregate DI errors, a different domain.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL` (projection error domain), natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-26 — Canonical navigation entry point
- candidateId / needId: `C-26` / `N-C26-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `BeautyQSearchGen2.scala:11-59`
- frameworkEvidence / bounded absence: no positive surface; `b0a.module-compose`/`b0a.binding-dsl` compose DI bindings, not a domain navigation facade.
- justification: `BeautyQSearchGen2` exposes typed handlers over `BeautyQSearchDeclarations`; this is a domain navigation facade. Natural owner is the repository search kernel.
- secondarySignal: none
- counterexampleSearch: tested DI module/binding composition; it organizes DI bindings, not a domain navigation root.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics = FAIL`, natural owner is the repository kernel, `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

---

### Build / firewall family

These are repository-owned sbt/build/test mechanics. PlanCheck/LocatorPrivacy/DI `Roots` are not the
same property: they constrain the DI graph, not the sbt project graph, source text, or classpath.

#### C-27 — Build/module dependency DAG
- candidateId / needId: `C-27` / `N-C27-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `build.sbt:180-252` (app-shell edges `:242-252`)
- frameworkEvidence / bounded absence: no positive surface; `b0c.plan-check`/`b0c.plan-verify` address DI-graph correctness, not sbt build dependency policy.
- justification: the module DAG is expressed by sbt `dependsOn`; the natural owner is the sbt build definition, not Distage/Izumi.
- secondarySignal: none
- counterexampleSearch: tested `PlanCheck`/`PlanVerifier`; they validate the DI graph, not the sbt project graph.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: `noProductSemantics` is not the issue, but the natural central owner is the build tool, and a Distage-owned sbt-DAG enforcer would not be a DI primitive; `secondConsumer = FAIL` (within this repository).
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-28 — Generic main-source neutrality firewall
- candidateId / needId: `C-28` / `N-C28-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `SearchGen2ModuleFirewallSpec.scala:56-67`
- frameworkEvidence / bounded absence: no positive surface; `b0c.plan-check` is DI-graph-only.
- justification: a repository-owned scalatest walks `search-gen2-*` main sources and fails on domain-name leakage. Natural owner is the repository build/test layer.
- secondarySignal: none
- counterexampleSearch: tested `PlanCheck`; it cannot see source text or module ownership.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: natural owner is the repository test/build layer; `noProductSemantics = FAIL` (the check is about repository module naming); `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-29 — SQL confinement firewall
- candidateId / needId: `C-29` / `N-C29-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `SearchGen2ModuleFirewallSpec.scala:110-133`
- frameworkEvidence / bounded absence: no positive surface; `b0c.diagnostic` `LocatorPrivacy` governs DI runtime visibility, not source/module confinement.
- justification: a repository-owned import-pattern detector confines `leaderboard.sql.SQL` to one module. Natural owner is the repository build/test layer.
- secondarySignal: none
- counterexampleSearch: tested `LocatorPrivacy`; it governs DI runtime binding visibility, a different layer from source/module confinement.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: natural owner is the repository test layer; `noProductSemantics = FAIL`; `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-30 — BeautyQ production package ownership firewall
- candidateId / needId: `C-30` / `N-C30-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `SearchGen2ModuleFirewallSpec.scala:135-147`
- frameworkEvidence / bounded absence: no positive surface.
- justification: a repository-owned scalatest fails if BeautyQ production source declares a package under `leaderboard.search.gen2`/`.gen2.*`. Natural owner is the repository build/test layer.
- secondarySignal: none
- counterexampleSearch: tested `LocatorPrivacy`/package aliases; they concern DI visibility/naming, not source package ownership.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: natural owner is the repository test layer; `noProductSemantics = FAIL`; `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-31 — Eval build-dependency isolation firewall
- candidateId / needId: `C-31` / `N-C31-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `SearchGen2ModuleFirewallSpec.scala:79-108`; `build.sbt:242-252`
- frameworkEvidence / bounded absence: no positive surface.
- justification: a repository-owned scalatest over `build.sbt` text enforces that serving Gen2 projects do not depend on `beautyqSearchGen2Eval`. Natural owner is the repository build/test layer.
- secondarySignal: none
- counterexampleSearch: tested `PlanCheck`; it checks the DI graph, not the sbt classpath.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: natural owner is the build/test layer; `noProductSemantics = FAIL`; `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

#### C-32 — Root aggregate coverage firewall
- candidateId / needId: `C-32` / `N-C32-01`
- status / headlineLabel: `CLASSIFIED` / `BEAUTYQ_SPECIFIC`
- phaseC / phaseD: `NO_RELEVANT_PRIMITIVE` / `N_A`
- beautyqEvidence: `SearchGen2ModuleFirewallSpec.scala:69-77`; `build.sbt:258-279`
- frameworkEvidence / bounded absence: no positive surface.
- justification: a repository-owned scalatest reads the `distage-example` aggregate block and fails if a retained Gen2/shared project is missing. Natural owner is the repository build/test layer.
- secondarySignal: none
- counterexampleSearch: tested graph `Roots`/`PlanCheck`; DI graph roots and DI-graph checks are unrelated to sbt aggregate membership.
- genericityScreen: `MISSING_GENERIC_PRIMITIVE` rejected: natural owner is the build/test layer; `noGeneric` is not the issue, but this is sbt aggregate policy; `secondConsumer = FAIL`.
- stateId / versionRef: `50e7945-clean` / `io.7mind.izumi 1.2.25`

---

## T016 Mechanical Summary

### Research states

| Research state | Count |
| --- | --- |
| `CLASSIFIED` | 31 |
| `BLOCKED_NEED_EVIDENCE` | 0 |
| `INDETERMINATE` | 1 |
| `SUPERSEDED` | 0 |
| **sum** | **32** |

### Headline distribution (among CLASSIFIED)

| Headline label | Count |
| --- | --- |
| `WELL_USED` | 7 |
| `UNDERUSED` | 0 |
| `HARD_TO_DISCOVER` | 0 |
| `MISSING_GENERIC_PRIMITIVE` | 0 |
| `BEAUTYQ_SPECIFIC` | 24 |
| **headline sum** | **31** |

### Candidates without a headline label

- `C-20` — status `INDETERMINATE`. The candidate combines a domain-owned public-response projection
  with a diagnostic-trace portion whose generic exact-version surface existence is unresolved; a single
  headline label cannot be chosen without curing that uncertainty by assertion.

### `MISSING_GENERIC_PRIMITIVE` candidates

- None. No candidate passed the required gate (accepted pinned-version absence evidence **and** an
  eight-condition `overall = ALLOWED` with a named real/plausible second consumer).

### Candidates where missing-generic was seriously considered then rejected

- Full genericity screen (decisive failing conditions recorded): `C-06`, `C-08`, `C-09`.
- Decisive early failing conditions recorded: `C-10`, `C-16`, `C-21`, `C-22`, `C-23`, `C-24`, `C-25`,
  `C-26`, `C-27`, `C-28`, `C-29`, `C-30`, `C-31`, `C-32`; also `C-12`, `C-13`, `C-14`, `C-15`, `C-17`,
  `C-18`, `C-19`.
- In every case the missing-generic hypothesis was defeated by `noProductSemantics = FAIL`, a natural
  central owner outside Distage/Izumi (repository `search-gen2-*` kernel, application layer, persistence
  layer, or sbt/build test layer), and/or no evidenced independent second consumer.

### Candidates where `secondarySignal` exists

- `C-05` — the explicit no-parameter `addDependency` helper lacks scaladoc/doc link (Phase-D `DOCS`
  remedy candidate). This remains a secondary signal only; the candidate-level ordering capability is
  `DISCOVERABLE` via constructor DI and the headline stays `WELL_USED`.
- `C-02` — the typed domain validation remainder (`BeautyQGen2AppShellConfigError`/`...Exception`) is
  domain-owned and intentionally local. Secondary context only; not a second headline label.

---

## Calibration

### Scope of the calibration

Change-oriented headline labels (calibration definition): `UNDERUSED`, `HARD_TO_DISCOVER`,
`MISSING_GENERIC_PRIMITIVE`. Non-change headline outcomes: `WELL_USED`, `BEAUTYQ_SPECIFIC`.
`INDETERMINATE`/`BLOCKED_NEED_EVIDENCE` are research states, not headline outcomes.

Final T016 distribution: `WELL_USED = 7`, `BEAUTYQ_SPECIFIC = 24`, `UNDERUSED = 0`,
`HARD_TO_DISCOVER = 0`, `MISSING_GENERIC_PRIMITIVE = 0`, with `INDETERMINATE = 1` (`C-20`).

The distribution is **not** one-sided in the "already correct vs keep local" sense: both
`WELL_USED` (7) and `BEAUTYQ_SPECIFIC` (24) are non-zero, so calibration items 1 and 2 do not
apply. It **is** one-sided in that **all change-oriented labels are zero**, so calibration item 3
applies and is recorded below: the audit must demonstrate it genuinely hunted bypass/duplication/
missing composition, discoverability failure, and genuinely missing generic primitives.

This zero-change result is consistent with the accepted inputs, not an artifact of a one-sided
search: Phase C found `LOCAL_DUPLICATION_OR_BYPASS = 0` and `MISSING_COMPOSITION = 0`, and Phase D
found `HARD_TO_DISCOVER = 0`. No quota or desired distribution exists. The task is to show the
hunt was real; Section "Calibration probes" does so.

### Calibration probes

For each probe: opposite outcome searched, strongest candidate(s), evidence considered, and why the
change-oriented candidate did or did not survive.

1. **Bypass / duplication / missing composition** (opposite outcome: `UNDERUSED`).
   - Strongest probe: `C-05` — the `@unused val _edge = seedReady` line and the existence of the
     no-parameter `addDependency` helper looked like a possible local workaround or missing
     composition.
     - Evidence considered: Phase C C-05 record; `b1.lifecycle.dependency-edge`
       (`AbstractBindingDefDSL.scala:236-248,306-329`); the framework's own `@unused` integration-check
       idiom at `DockerClientWrapper.scala:117`.
     - Why it did not survive: the current binding function already declares `BeautyQSeedReady` as an
       ordinary constructor/function dependency, which is Distage's primary ordering mechanism;
       `@unused` only suppresses a warning; `addDependency` is an alternative, not a bypassed or
       duplicated capability. Hunting a documented/product reason for the local mechanism found a
       framework idiom, not a local reinvention of a different primitive.
   - Secondary probes: `C-02` (typed config error — no framework domain-validation surface exists to
     bypass); `C-09` (`NonCriticalIntegrationFailure` — different stage/error domain, not a rebuilt
     framework marker); `C-11`/`C-12` (no framework primitive models the aggregate/backend semantics).

2. **Discoverability failure** (opposite outcome: `HARD_TO_DISCOVER`).
   - Strongest probe: `C-05` — the explicit `addDependency` helper is public but has no scaladoc/doc
     link, the single genuine discoverability wrinkle in the accepted inputs (Phase-D `remedyCandidates = DOCS`).
     - Evidence considered: Phase-D C-05 row; `AbstractBindingDefDSL.AddDependencyDSL` source placement;
       `ModuleDef` scaladoc for constructor/provider injection.
     - Why it did not survive at headline level: the candidate-level capability actually needed is the
       ordering/readiness edge, and that is discoverable through normal documented constructor
       dependency injection. The narrower helper-level docs weakness is retained as `secondarySignal`
       on `C-05` and is not promoted to a candidate-level headline. This follows the explicit
       instruction not to convert the C-05 helper observation into candidate-level `HARD_TO_DISCOVER`.
   - Other probes: `C-01` (three-tagged-`ModuleDef` assembly has no single exact example, but each
     primitive is documented and conventional); `C-02` (`ConfigModuleDef` has no scaladoc, but API
     names/package are conventional and in-framework usage exists); `C-04` (no doc link, but bundled
     template + `ContainerDef` scaladoc); `C-06`/`C-08` (`N_A`, no relevant surface — discoverability
     is not the question). None rose to a real-surface-with-materially-weak-discovery path.

3. **Genuinely missing generic primitives** (opposite outcome: `MISSING_GENERIC_PRIMITIVE`).
   - Strongest probes: the status/degradation family (`C-06`, `C-08`, `C-09`), the resource/config/name
     family (`C-10`, `C-16`), the search/domain-kernel family (`C-21`..`C-26`), and the build/firewall
     family (`C-27`..`C-32`).
     - `C-06`/`C-08`/`C-09`: a generic runtime serving/degradation primitive was run through the
       genericity screen. Decisive failing conditions: `recurrence = FAIL` (the occurrences are the one
       BeautyQ startup story; a repeated BeautyQ-specific semantic pattern is not an independent second
       consumer), `noProductSemantics = FAIL` (serving modes, ES/Qdrant identity, degradation reasons,
       BeautyQ error ADTs), `secondConsumer = FAIL`, and the natural central owner is the application
       layer. Missing-generic defeated.
     - `C-10`/`C-16`: de-named, the mechanics target repository-kernel Qdrant config types and backend
       resource names; `noProductSemantics = FAIL`, natural owner is the repository search kernel,
       `secondConsumer = FAIL`. Missing-generic defeated.
     - `C-21`..`C-26`: de-named, the executable declaration/fingerprint/materialization/navigation
       mechanics remain repository `search-gen2-*` kernel mechanics; Distage binding arbitrary Scala
       values is not framework ownership. `noProductSemantics = FAIL`, natural central owner is the
       repository kernel, `secondConsumer = FAIL`. Missing-generic defeated.
     - `C-27`..`C-32`: although the architectural-enforcement ideas are reusable in the abstract, they
       protect the sbt project DAG, generic-module neutrality, SQL/module confinement, package
       ownership, eval classpath isolation, and aggregate membership. Their natural owner is the
       build/test layer, not Distage/Izumi; PlanCheck/LocatorPrivacy/DI `Roots` are different
       properties. Missing-generic defeated.
   - `C-20` also had a possible missing/real-surface question (diagnostic traces), but because surface
     existence itself is unresolved it was **not** converted into a fabricated missing-primitive or a
     fabricated `BEAUTYQ_SPECIFIC`; it is retained as `INDETERMINATE` with no headline label.

### Outcome

- Opposite outcome classes explicitly searched: `UNDERUSED`, `HARD_TO_DISCOVER`,
  `MISSING_GENERIC_PRIMITIVE` — all three change-oriented classes.
- No positive change-oriented result was manufactured to balance the distribution.
- **Any T016 classification changed during T017?** No. Calibration found each T016 label supported by
  the accepted inputs; `C-20` remained `INDETERMINATE`; no downgrade or revision was required.

---

## T017 Downgrade / State-Integrity Check

- Every `CLASSIFIED` candidate has exactly one headline label: **yes** (31 rows; 7 `WELL_USED` +
  24 `BEAUTYQ_SPECIFIC`).
- Every `BLOCKED_NEED_EVIDENCE` / `INDETERMINATE` candidate has **no** headline label: **yes**
  (`C-20` carries an empty headline label; no `BLOCKED_NEED_EVIDENCE` rows).
- No current row has two headline labels: **yes**.
- No headline `MISSING_GENERIC_PRIMITIVE` lacks `genericityResult.overall = ALLOWED`: **vacuously
  satisfied** (no `MISSING_GENERIC_PRIMITIVE` rows).
- Every headline `MISSING_GENERIC_PRIMITIVE` names a second consumer: **vacuous** (none).
- Every headline `HARD_TO_DISCOVER` has a real pinned `1.2.25` surface: **vacuous** (none).
- No historical/`SUPERSEDED` label is consumed as current: **yes** (no `SUPERSEDED` rows).
- No Phase-D-level `HARD_TO_DISCOVER` spelling was counted as a headline: **yes** — Phase D
  `HARD_TO_DISCOVER = 0`; no D value was promoted.
- Label totals reconcile with research-state totals: **yes** — 31 `CLASSIFIED` (7 + 24) + 1
  `INDETERMINATE` = 32.
- No label was added to fill a slot: **yes** — `C-20` is deliberately left unlabeled.

No T016 label was shown unsupported, so no downgrade/revise action was required.

---

## Validation (mechanical, T016 + T017 scope)

- Exactly **32** current candidate records; `C-01`..`C-32` each appear once; no duplicate IDs.
- Statuses use only `CLASSIFIED` / `BLOCKED_NEED_EVIDENCE` / `INDETERMINATE` / `SUPERSEDED`.
- Every `CLASSIFIED` row carries exactly one headline label from the five-way vocabulary; every
  `INDETERMINATE` row carries none.
- Every row cites BeautyQ evidence and framework or bounded-absence evidence.
- Every row carries `stateId = 50e7945-clean` and `versionRef = io.7mind.izumi 1.2.25`.
- No `MISSING_GENERIC_PRIMITIVE` row exists, so the full genericity gate/second-consumer requirement
  is not triggered; candidates where it was considered are screened and recorded.
- No `HARD_TO_DISCOVER` headline exists, so no existing-surface requirement is unmet.
- Calibration exists (required because all change-oriented labels are zero).
- No recommendation content is present in this artifact: no recommendation entries, no accepted
  architecture, no migration or implementation plan, and no downstream feature is opened.
- Nothing in `research/00`, `research/01`, `research/02`, `research/03`, `research/05`, `spec.md`,
  `plan.md`, `tasks.md`, `data-model.md`, source, build, project, or framework files was modified by
  this phase.
