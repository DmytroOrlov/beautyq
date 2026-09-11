# Feature 003 — Synthesis and Recommendations

## Artifact binding / self-description

| Field | Value |
| --- | --- |
| `path` | `specs/003-beautyq-distage-izumi-leverage-audit/research/05-synthesis-and-recommendations.md` |
| `question` | What evidence-graded, non-authoritative recommendations follow from the current classification, and what is the primary human synthesis of the audit? |
| `writer` | Final synthesis adjudicator (`T018` recommendations; `T019` synthesis) |
| `consumers` | Human decision-maker; later validation (`T020`–`T023`) |
| `staleWhen` | source state (`50e7945-clean`) or resolved framework version (`io.7mind.izumi 1.2.25`) changes, or `research/04` classification state changes |
| `stateId` | `50e7945-clean` |
| `versionRef` | `io.7mind.izumi` `1.2.25` |
| `phase` | Group F only (`T018` then `T019`). Recommendations are materialized before the synthesis. No `T020+`, no implementation, no authorization. |

Authoritative input consumed: `research/04-classified-findings.md` (accepted current
classification state: 32 material candidates; `CLASSIFIED = 31`; `INDETERMINATE = 1` (`C-20`);
`BLOCKED_NEED_EVIDENCE = 0`; `SUPERSEDED = 0`; `WELL_USED = 7`; `UNDERUSED = 0`;
`HARD_TO_DISCOVER = 0`; `MISSING_GENERIC_PRIMITIVE = 0`; `BEAUTYQ_SPECIFIC = 24`). Earlier
artifacts (`00`–`03`) were consulted only for evidence references. No product/framework/build
source was read or changed.

Recommendations are **non-authoritative research output only**. They open no work item, authorize
no change, and are not an accepted architecture.

---

## Recommendations

A recommendation is admitted only where `research/04` already contains enough current evidence for
that exact follow-up. Labels are not converted mechanically into recommendations: `BEAUTYQ_SPECIFIC`,
`NO_RELEVANT_PRIMITIVE`, an interesting nearby framework API, or an unresolved evidence gap does not
by itself produce one. A small set is preferred over speculative output, and zero would be a valid
research result.

### R-01 — `addDependency` helper documentation

| Field | Value |
| --- | --- |
| `recommendationId` | `R-01` |
| `candidateId` | `C-05` |
| `kind` | `DOCS_EXAMPLES` |
| `statement` | A separately authorized documentation follow-up could reasonably consider documenting the public explicit no-parameter ordering helper `addDependency` (`AbstractBindingDefDSL.AddDependencyDSL#addDependency[B]` / `#addDependency(key: DIKey)` / `#addDependencies(keys)`, `ModifyDSLBase#addDependency`) with scaladoc and an official-doc/scaladoc cross-link, so that a developer or agent who needs a pure ordering/readiness edge can discover it from the API surface. **No new framework primitive is required.** BeautyQ is **not** asked to replace its current constructor/function dependency idiom: the existing provider-parameter form already expresses the ordering edge through the primary documented Distage mechanism, and `addDependency` is a semantically equivalent public alternative, not a bypass. |
| `evidenceRefs` | `research/04` C-05 `secondarySignal` and `justification`; `research/03` `## D` C-05 row (`relevantSurfaceIds`: `b0b.lifecycle-core`, `b0b.from-resource`, `b0b.ref-resource`, `b1.lifecycle.dependency-edge`; `discoverability = DISCOVERABLE`; `publicDiscoveryPath` "Docs/scaladoc for provider/constructor dependency injection; `addDependency` is discoverable only from the DSL source"; `remedyCandidates = DOCS`); `research/02` B1 `b1.lifecycle.dependency-edge` (`distage-core-api_3-1.2.25-sources.jar :: izumi/distage/model/definition/dsl/AbstractBindingDefDSL.scala:236-248, 306-329`; `ModuleDefDSL.scala:202-206`); BeautyQ anchor `BeautySearchGen2PluginModules.scala:56-60`. |
| `confidence` | Medium. Positive evidence: the helper is a real public `1.2.25` surface (`b1.lifecycle.dependency-edge`), the capability it expresses is genuinely used at `C-05`, and Phase D recorded `DOCS` as the only remedy candidate on a real surface. Uncertainty/limitation: the helper's missing scaladoc/doc link is established, but the *value* of adding it is a judgment not measured against a real external consumer; the candidate-level capability is already `DISCOVERABLE` via constructor injection, so the gap is a narrow helper-level discovery wrinkle, not a blocked capability. |
| `secondConsumer` | Not required for `DOCS_EXAMPLES`. (No primitive is proposed; no second consumer is claimed.) |
| `genericityResult` | Not applicable. No `MISSING_GENERIC_PRIMITIVE` and no framework primitive/API change is proposed, so the eight-condition gate is not triggered. |
| `nonAuthoritative` | `true` |

**Supporting candidate/classification.** `C-05` is `CLASSIFIED` / `WELL_USED` (Phase C `DIRECT_USE` FULL;
Phase D `DISCOVERABLE`). The recommendation does not contest that classification; it records the one
retained `secondarySignal` on that row.

**Positive evidence, uncertainty, and why this is a recommendation rather than an accepted change.**
`research/04` states the explicit no-parameter alternative `addDependency` is public but has no
scaladoc/doc link, and Phase D records `remedyCandidates = DOCS` on the real `1.2.25` surface. That is
enough current evidence for a documentation/discovery follow-up. It is **not** enough evidence for a
code/API change, and no change of any kind is authorized here: the record is informational only, and the
capability itself is already discoverable.

---

### Not recommendation-ready

#### `C-20` — Response projection and diagnostic traces

- Research state: `INDETERMINATE`, no headline label. No recommendation is admitted.
- Exact missing evidence (`research/04` C-20 `missingEvidence`): a targeted exact-version
  (`io.7mind.izumi 1.2.25`) search of the `b0c.diagnostic`/rendering packages for a **generic
  domain-result trace/render surface**, or source confirmation that only DI-graph rendering
  (`DepTreeRenderer.scala`, `DIPlanCompactFormatter.scala`, `DIRendering.scala`) exists.
- Why no recommendation yet: the competing interpretations are (a) the diagnostic-trace portion is
  domain-owned and the `b0c.diagnostic` similarity is abstraction-mismatched, versus (b) a real generic
  domain-result trace/render surface exists in the exact version that Phase C/D did not exhaustively
  search. Surface **existence itself** is unresolved, so admitting either a docs recommendation or a
  framework API/code recommendation (or forcing `BEAUTYQ_SPECIFIC`) would fabricate a conclusion from
  uncertainty. The candidate stays unresolved until the named evidence resolves it.

### T018 Summary

- Total Recommendation records: **1**.
- `DOCS_EXAMPLES` count: **1** (`R-01`).
- `FRAMEWORK_API_CODE` count: **0** (**FRAMEWORK_API_CODE recommendations: none.**).
- Candidates referenced: **`C-05`** (and `C-20` only in the non-recommendation "Not
  recommendation-ready" entry).
- Not-recommendation-ready candidates: **`C-20`** (INDETERMINATE).
- Any primitive proposal: **none**.
- Every primitive proposal has `secondConsumer` + `genericityResult`: **vacuously satisfied** (no
  primitive proposal exists).

No work item is opened; no implementation task is created; feature 004 is not referenced as an
authorization path.

---

## Synthesis

This synthesis is the primary human deliverable. It summarizes the accepted evidence in
`research/04` (with references to `00`–`03`) and answers the eight standing questions in order. It is
self-contained for the main conclusions and does not duplicate all 32 classified records. Binding:
`stateId = 50e7945-clean`, `versionRef = io.7mind.izumi 1.2.25`.

### Q1 — What does Distage/Izumi 1.2.25 already provide?

The pinned version was verified as resolved (declared `1.2.25` == resolved `1.2.25` for the
compile/runtime artifacts and the Test-scope testkit, `research/00`). The pinned-version
capabilities materially relevant to this audit include the following. BeautyQ directly consumes
a subset identified in Q2; comparison-only / abstraction-mismatched surfaces are distinguished
below. Listing a capability here does not imply BeautyQ uses it.

- **Module composition** — `ModuleBase` / `Module.make/from/empty`, `++`, `.merge`, `.overrideLeft`,
  `overriddenBy` (`b0a.module-compose`).
- **Plugin loading/composition** — `PluginBase`, `PluginDef`, `PluginConfig`, `PluginLoader`,
  `LoadedPlugins`, `PluginMergeStrategy`, `StaticPluginLoader` (`b0a.plugin`).
- **Role-based app composition/launch** — `RoleAppMain` (+ `LauncherBIO`/`LauncherCats`/`LauncherIdentity`),
  `RoleDescriptor`, `RoleModuleDef.makeRole`, `RoleTag` (`b0a.role`).
- **Activation / axes / tag propagation** — `Axis`, `AxisChoice`, `Activation`, `StandardAxis.{Mode,Repo,World,Scene}`;
  `BindingTag.AxisTag`, `confined`/`exposed`; `IncludesDSL#include` with outer axis-tag propagation and
  explicit `TagMergePolicy` (`b0a.activation-axis`, `b0a.binding-tag`, `b0a.include-tags`).
- **Lifecycle** — `Lifecycle.make`/combinators, cats/ZIO/ZManaged adapters, `Lifecycle.Basic`
  (`b0b.lifecycle-core`, `b0b.lifecycle-adapters`).
- **Resource/effect construction** — `.fromResource(...)` (instance, `Functoid`, class-constructor) and
  `.fromEffect(...)`, plus `.refResource`/`.refEffect` for fresh acquisitions (`b0b.from-resource`,
  `b0b.from-effect`, `b0b.ref-resource`).
- **Explicit dependency/ordering edge** — `addDependency[B]` / `addDependency(key: DIKey)` /
  `addDependencies(keys)` express a pure ordering/readiness edge without a constructor parameter
  (`b1.lifecycle.dependency-edge`). The framework itself uses the `@unused` constructor-parameter idiom
  for an intentional `IntegrationCheck` edge (`DockerClientWrapper.scala:117`).
- **Managed Docker** — `ContainerDef` / `ContainerDef.make[F]` / `ContainerResource` (extends
  `Lifecycle.Basic`), `DockerSupportModule`, and `ContainerHealthCheck`
  (`portCheck`/`httpGetCheck`/`httpsGetCheck`/`postgreSqlProtocolCheck`/`exitCodeCheck`) which gates
  acquisition (`b0d.docker-container`, `b0d.docker-health`).
- **Typed config** — `ConfigModuleDef` (`makeConfig`/`makeConfigNamed`/`makeConfigWithDefault`/`wireConfig`,
  `.fromConfig`), `DIConfigReader`/`DIConfigMeta`, `ConfTag`, `AppConfig`, and the typed decode-failure
  `DIConfigReadException` (`b0d.config-def`, `b1.config.typed-decode-error`).
- **Graph / plan / integration surfaces** — `PlanVerifier`/`Injector.verify`, `PlanCheck`
  (compile-time + runtime), `PlanTopology`/`GraphQueries`, weak sets (`.weakSet`,
  `ReferenceImpl(weak=true)`), `Roots`, `IntegrationCheck`/`ResourceCheck`, and the diagnostic rendering
  package `b0c.diagnostic` (`DepTreeRenderer`, `DIPlanCompactFormatter`, `DIRendering`).

**Considered but abstraction-mismatched (did not lift into a BeautyQ need).** `b0c.integration-check`
is a provisioning/readiness gate, not an application serving-status or failure-classification algebra
(`C-06`/`C-08`/`C-09`); `b0c.plan-verify`/`b0c.plan-check` validate the DI graph, not domain search-plan
compilation or sbt build policy (`C-19`, `C-27`, `C-31`); `LocatorPrivacy`/`Id`/`.named` govern DI
locator identity/visibility, not source/module confinement or backend resource names (`C-16`, `C-29`,
`C-30`); `DIError.aggregateErrors`/`ProvisioningFailure.AggregateFailure` aggregate DI errors, not
domain projection errors (`C-25`); `b0c.diagnostic` renders DI-graph diagnostics, a different
abstraction/domain from either the public response projection or an unresolved generic domain-result
trace (`C-20`); `b0a.binding-dsl` can bind an arbitrary value but supplies none of the declaration/
derivation/fingerprint/compiler/materialization semantics (`C-14`–`C-18`, `C-21`–`C-26`). This bounded
search also recorded negative observations: no dedicated generic runtime "status/degradation" trait and
no fingerprint surface were found in the inspected modules (both recorded as bounded misses, not
absence claims).

### Q2 — What does BeautyQ use well?

Seven candidates are `WELL_USED` (`C-01`, `C-02`, `C-03`, `C-04`, `C-05`, `C-07`, `C-11`), all Phase C
`DIRECT_USE` and Phase D `DISCOVERABLE`. The pattern is consistent: BeautyQ substantially uses Distage
for the things Distage actually owns, and keeps product-domain policy local.

- **Composition** — `C-01`: `LeaderboardPlugin extends PluginDef` includes generic module sets and
  `BeautySearchGen2PluginModules.api`; supplement variants are `ModuleDef`s carrying
  `tag(choice: Axis.AxisChoice)` and are selected by `BeautyQSupplementStartup extends Axis` +
  `Activation`. Distage's plugin/module/axis/activation machinery is used directly; only axis/choice
  names and which variants exist remain BeautyQ policy.
- **Typed config validation** — `C-02`: `appShellConfigModule` binds via
  `ConfigModuleDef.makeConfig[...]("beautyq-gen2-app-shell")` and Distage decodes directly; the extra
  `validateAtBoundary` positive-value/Qdrant-work-policy rule is legitimate domain boundary policy that
  no framework surface claims.
- **Lifecycle/readiness** — `C-03`: readiness is a framework `Lifecycle` resource bound with
  `.fromResource[BeautyQSeedReady.Noop/LoadAndInsert[F]]` and selected by the standard `Mode`/`Scene`
  axes.
- **Managed Docker** — `C-04`: each plugin includes `DockerSupportModule` and binds containers with
  `.fromResource(<Def>.make[...])`; ES/Qdrant defs extend `ContainerDef` and declare
  `ContainerHealthCheck.httpGetCheck`; ports come from `availablePorts`.
- **Ordering edge** — `C-05`: the binding function `(seedReady: BeautyQSeedReady, sql: SQL[IO], clock: JClock) => ...`
  already makes `BeautyQSeedReady` an ordinary constructor/function dependency of
  `BeautyQSearchSnapshotSource.Postgres`, a first-class Distage ordering mechanism. **Nuance:** the
  explicit no-parameter `addDependency` helper is an equivalent alternative, **not** evidence of
  bypass — the current form is the primary framework mechanism, and `@unused` only suppresses a warning.
- **Typed startup policy vocabulary** — `C-07`: variant selection uses `Axis`/`Activation`/`tag(choice)`;
  stable codes, ordering, `Default`, and the strict `fromStableCode` parser are domain-owned.
- **Multi-backend activation aggregate** — `C-11`: `activate` composes ES and Qdrant activation into an
  `Activation` aggregate exposed as a Distage resource via `BeautyQSearchGen2Startup.lifecycle`
  (`Lifecycle.LiftF`) bound with `.fromResource`; the optional-component aggregate and typed partial
  failure are domain remainder.

### Q3 — What did BeautyQ rebuild unnecessarily?

Answering directly from accepted evidence: **nothing.** No current candidate was classified `UNDERUSED`.
Phase C recorded `LOCAL_DUPLICATION_OR_BYPASS = 0` and `MISSING_COMPOSITION = 0`; the audit therefore
established no evidence-backed unnecessary rebuild/bypass of a relevant Distage capability.

This is a searched result, not a skipped hypothesis. The calibration in `research/04` actively tested the
strongest bypass/duplication probe — `C-05`'s `@unused val _edge = seedReady` plus the existence of
`addDependency` — and defeated it: the constructor dependency already expresses the edge with the primary
framework mechanism, `addDependency` is semantically equivalent rather than bypassed, and replacing the
current form would remove only one unused parameter and one `@unused val` (cosmetic). Secondary probes
`C-02`, `C-09`, `C-11`/`C-12` were likewise counterexamined and did not survive. The zero is evidence,
not a softened "possible duplication."

### Q4 — Which misses were discoverability problems?

At candidate level, **`HARD_TO_DISCOVER = 0`.** Phase D found `DISCOVERABLE = 7`, `HARD_TO_DISCOVER = 0`,
`INDETERMINATE = 1` (`C-20`), `N_A = 24`. The seven discoverable candidates are the seven `WELL_USED`
ones (`C-01`, `C-02`, `C-03`, `C-04`, `C-05`, `C-07`, `C-11`); the 24 `N_A` rows have no relevant
surface, so discoverability is not the question there.

Preserved `C-05` nuance: the ordering capability actually needed is discoverable through documented
constructor/provider dependency injection, while the explicit `addDependency` helper itself has a
narrower documentation weakness (no scaladoc/doc link). That secondary signal is **not** a
candidate-level `HARD_TO_DISCOVER` headline finding; it is the sole input to recommendation `R-01`.
`C-20` is `INDETERMINATE` because the existence and the discoverability of a possible generic
domain-result trace/render surface cannot yet be separated.

### Q5 — Which generic primitives are actually missing?

**No candidate passed the required `MISSING_GENERIC_PRIMITIVE` gate** (`MISSING_GENERIC_PRIMITIVE = 0`;
no candidate has `genericityResult.overall = ALLOWED`). The strongest hypotheses failed as follows:

- **Status/degradation family (`C-06`, `C-08`, `C-09`)** — genericity screen run and rejected on decisive failing conditions:
  `recurrence = FAIL` (a repeated BeautyQ startup semantic pattern is not an independent second
  consumer), `noProductSemantics = FAIL` (serving modes, ES/Qdrant identity, degradation reasons,
  BeautyQ error ADTs), `secondConsumer = FAIL`, and the natural central owner is the application layer,
  not Distage. The nearest framework surfaces (`IntegrationCheck`/`ResourceCheck`,
  `NonCriticalIntegrationFailure`) are provisioning/readiness gates, a different abstraction.
- **Resource/config/name family (`C-10`, `C-16`)** — rejected: targets repository-kernel Qdrant config
  types and backend resource names; `noProductSemantics = FAIL`, natural owner is the repository search
  kernel, `secondConsumer = FAIL`.
- **Search/domain-kernel family (`C-21`–`C-26`, plus `C-13`–`C-19`)** — rejected: de-named, these remain
  repository `search-gen2-*` kernel mechanics (executable declaration/fingerprint/materialization/navigation
  and compile/evaluate outcomes); binding arbitrary Scala values through DI is not framework ownership;
  `noProductSemantics = FAIL`, natural owner is the repository kernel, `secondConsumer = FAIL`.
- **Build/firewall family (`C-27`–`C-32`)** — rejected: the enforcement ideas protect the sbt project DAG,
  generic-module neutrality, SQL/module confinement, package ownership, eval classpath isolation, and
  aggregate membership; their natural owner is the sbt/repository build/test layer, not the DI framework,
  and `PlanCheck`/`LocatorPrivacy`/DI `Roots` are different properties.

Scope note: this claims no missing primitive **for this audit and this selected source/version**. It does
not assert that Distage/Izumi "has no missing primitives in general."

### Q6 — What should remain BeautyQ-specific?

Twenty-four candidates are `BEAUTYQ_SPECIFIC` (correctly belonging to the
BeautyQ/application/repository/domain/build layer rather than Distage/Izumi). Grouped coherently:

- **Serving/degradation/status domain model** — `C-06`, `C-08`, `C-09`.
- **Backend/resource-name/config adaptation** — `C-10`, `C-12`, `C-16`.
- **Runtime capability/result routing where application-owned** — `C-13`, `C-18`.
- **Search policy/declaration/compiler/projection/materialization/fingerprint mechanics** — `C-14`,
  `C-15`, `C-17`, `C-19`, `C-21`, `C-22`, `C-23`, `C-24`, `C-25`.
- **Canonical domain navigation** — `C-26`.
- **Repository/build/firewall enforcement** — `C-27`, `C-28`, `C-29`, `C-30`, `C-31`, `C-32`.

Important wording: "should remain local" means the evidence supports BeautyQ/application/repository/
search-kernel/build ownership **instead of** Distage/Izumi ownership. It is **not** an implementation
freeze and **not** a broad code-quality endorsement. `C-20` is deliberately **not** among these 24: it
carries no headline label and remains `INDETERMINATE`.

### Q7 — Which actual recommendations are docs/examples vs framework API/code?

From the actual `T018` Recommendation records materialized above:

- `DOCS_EXAMPLES`: **1** — `R-01` (candidate `C-05`).
- `FRAMEWORK_API_CODE`: **0** — **none**, and explicitly stated: no candidate passed the
  primitive/API-change gate (the accepted classification contains `MISSING_GENERIC_PRIMITIVE = 0`,
  `UNDERUSED = 0`, and `MISSING_COMPOSITION = 0`, and no recommendation-specific basis in `research/04`
  supports reopening that classification).

`R-01` is the only recommendation, and it explicitly states **no new framework primitive is required**.

### Q8 — Which recommendations have enough evidence for separately authorized follow-up?

Using the actual recommendation set:

- **`R-01` (`DOCS_EXAMPLES`, `C-05`)** — evidence/confidence is sufficient that a **separately authorized
  documentation follow-up could reasonably be considered**. The real `1.2.25` surface is established
  (`b1.lifecycle.dependency-edge`), Phase D recorded `remedyCandidates = DOCS`, and the row is a
  `WELL_USED`/`DISCOVERABLE` candidate whose only retained gap is helper-level doc discoverability.
  Confidence is Medium because the value of the doc addition is a judgment, not a measured demand.
  "Enough evidence for separately authorized follow-up" does **not** mean approved, accepted, scheduled,
  or implementation-authorized. No feature/work item is opened here.
- **`C-20`** — **not recommendation-ready.** No recommendation is admitted until the named missing
  evidence (targeted exact-version `b0c.diagnostic`/rendering search for a generic domain-result
  trace/render surface, or source confirmation that only DI-graph rendering exists) resolves the
  existence question.

### Synthesis conclusion

- **Framework use.** BeautyQ is already using the relevant Distage/Izumi primitives well in the seven
  places where the framework actually owns the capability (`WELL_USED = 7`: composition, typed config
  binding, lifecycle/readiness, managed Docker, ordering edge, typed activation vocabulary, resource-
  backed activation aggregate).
- **Unnecessary rebuilds.** The audit found no evidence-backed underuse and no rebuild/bypass:
  `UNDERUSED = 0`, `LOCAL_DUPLICATION_OR_BYPASS = 0`, `MISSING_COMPOSITION = 0`, with the strongest
  probes actively counterexamined.
- **Discoverability.** No candidate-level hard-to-discover miss: `HARD_TO_DISCOVER = 0`; one narrow
  helper-level documentation wrinkle (`C-05` → `R-01`).
- **Missing generic primitives.** No accepted missing generic primitive: `MISSING_GENERIC_PRIMITIVE = 0`;
  the status/degradation, resource/config/name, search/domain-kernel, and build/firewall families all
  failed the genericity gate on `noProductSemantics`/natural-owner/`secondConsumer` grounds.
- **Local ownership.** The large majority of mapped mechanics belong to the product/repository/search-kernel/
  build layer rather than Distage (`BEAUTYQ_SPECIFIC = 24`).
- **Unresolved.** One explicit unresolved research state remains: `C-20` (`INDETERMINATE`), with the
  exact missing evidence named and no fabricated label or recommendation.

Any recommendation set is therefore **narrow** (one docs-only record) rather than a refactoring program.
This is an evidence-supported result, not a narrow scope that skipped the hypothesis: all three
change-oriented classes (`UNDERUSED`, `HARD_TO_DISCOVER`, `MISSING_GENERIC_PRIMITIVE`) were actively
searched and remained zero, and no positive result was manufactured to balance the distribution. This
synthesis does **not** claim "nothing can improve"; it reports what the evidence at this state and
version supports.

---

## Validation: Evidence

T020 scope: source-anchor resolution, version binding, and negative-claim discipline.
Method: anchors were resolved against the canonical evaluated source commit
`50e794584d68768403d5c45d6d7fc21c2db87f6c` via read-only `git show`; framework
surfaces were re-read from the local exact-version source jars already resolved for
this build (`io.7mind.izumi 1.2.25`). No latest/`master`/model-memory evidence was used.

1. **BeautyQ source anchors resolve.** PASS. 41 cited BeautyQ source files plus
   `build.sbt` were resolved at the canonical commit; every cited `file:line` range lies
   within the file length at that commit (e.g. `BeautySearchGen2PluginModules.scala:56-60`
   is exactly the `@unused val _edge = seedReady` ordering edge; `LeaderboardPlugin.scala:28-42`
   is the `PluginDef.include(...)` block; `LeaderboardPlugin.scala:155-176` is the
   `seedProd`/`seedManaged`/`seedTest` `fromResource` bindings; `LeaderboardRole.scala:684-688`
   is the `Activation` boot override; `build.sbt:4-5` is `V.distage = "1.2.25"` /
   `V.logstage = distage`; `build.sbt:24-29` the `io.7mind.izumi` artifacts;
   `build.sbt:180-252`/`:242-252`/`:258-279` the Gen2 DAG, app-shell edges, and aggregate).
   Spot-checked content matched each substantive finding (config `makeConfig`,
   `ContainerDef`/`availablePorts`, serving-status constructors, `fromStableCode`,
   `SupplementCapability`, `BeautyQSearchResponseGen2Projector.project`/`deriveWarnings`,
   the trace `render` methods, the firewall checks).
2. **Framework claims bind to resolved `1.2.25`.** PASS. `distage-core-api_3`,
   `distage-extension-config_3`, `distage-framework-docker_3`, `distage-core_3` `1.2.25`
   sources jars are present/reopenable; `research/00` records the declared==resolved
   observation for the same version.
3. **Positive surfaces reopenable at exact version.** PASS. Verified directly, e.g.
   `AbstractBindingDefDSL.scala:236-248,306-329` (`addDependency`/`addDependencies`),
   `ModuleDefDSL.scala:216-222` (`.fromEffect`) and `:259-318` (`.fromResource`/`.refResource`),
   and `DockerClientWrapper.scala:117` (`@unused check: DockerIntegrationCheck[F]`).
4. **Bounded negative/non-match claims record their basis.** PASS. `research/02` records
   `LF-1`..`LF-6` search scopes and residual uncertainty; `research/03` records an
   `evidenceGap`/bounded residual per affected candidate; `research/04` records
   bounded-absence evidence per `SEARCH_BOUNDED_ABSENCE` row.
5. **`MISSING_GENERIC_PRIMITIVE = 0` is a classification result, not a universal claim.**
   PASS. It is produced by the genericity screen with recorded failing conditions, and
   `research/05` Q5 explicitly scopes it to "this audit and this selected source/version."
6. **Unresolved existence remains unresolved.** PASS. `C-20` is `INDETERMINATE` with
   `missingEvidence` and `wouldUnblock` recorded; no label was fabricated.
7. **No lone accidental site created `MISSING_GENERIC_PRIMITIVE`.** PASS. No candidate
   carries that headline; the hypothesis was actively screened and rejected for the
   status/degradation, resource/config/name, search/domain-kernel, and build/firewall
   families.

Exception (advisory, non-failing): the secondary reference `core-api :: ModuleDefDSL.scala:202-206`
on `b1.lifecycle.dependency-edge` resolves to the `.using` contrast methods, which is
consistent with that row's declared contrast apiNames; the substantive `addDependency`
positive surface anchor (`AbstractBindingDefDSL.scala:236-248,306-329`) resolves exactly.
No decision-critical anchor fails to resolve or contradicts its stated finding.

## Validation: Classification

T021 scope: current research-state integrity (`research/04`). PASS.

- `C-01`..`C-32` each appear exactly once: 32 header records, 32 summary rows, 0 missing,
  0 duplicate.
- Status counts: `CLASSIFIED = 31`, `INDETERMINATE = 1`, `BLOCKED_NEED_EVIDENCE = 0`,
  `SUPERSEDED = 0` (sum 32).
- Headline counts among `CLASSIFIED`: `WELL_USED = 7`, `BEAUTYQ_SPECIFIC = 24`,
  `UNDERUSED = 0`, `HARD_TO_DISCOVER = 0`, `MISSING_GENERIC_PRIMITIVE = 0` (sum 31).
- Every `CLASSIFIED` row carries exactly one headline label from the five-way vocabulary;
  the single `INDETERMINATE` row (`C-20`) carries no headline label.
- `C-20` records exact `missingEvidence` and `wouldUnblock`; no label was fabricated.
- No `SUPERSEDED` label is consumed as current; no `BLOCKED_NEED_EVIDENCE` row exists.
- `HARD_TO_DISCOVER` headline: none, so no real pinned-version surface requirement is unmet.
- `MISSING_GENERIC_PRIMITIVE` headline: none, so the `genericityResult` + second-consumer
  requirement is vacuous and correctly untriggered.
- Phase-D vocabulary (`DISCOVERABLE`/`N_A`) is not counted as a headline.

Integrity exceptions: none.

## Validation: Synthesis

T022 scope: calibration, synthesis completeness, recommendation discipline. PASS.

1. `research/05` explicitly answers all eight standing questions (`### Q1`..`### Q8`
   present and populated) and is self-contained for the main conclusions.
2. `## Recommendations` occurs before `## Synthesis`.
3. Actual recommendation records: exactly one (`R-01`) with unique `recommendationId`,
   valid `candidateId` (`C-05`), valid `kind` (`DOCS_EXAMPLES`), `evidenceRefs`,
   `confidence`, and `nonAuthoritative = true`.
4. `DOCS_EXAMPLES` record explicitly states no new framework primitive is required.
5. `FRAMEWORK_API_CODE` count is 0; no primitive proposal exists, so the
   `secondConsumer`/`genericityResult` naming requirement is vacuously satisfied.
6. Q7 and Q8 summarize the actual recommendation set (1 `DOCS_EXAMPLES`, 0
   `FRAMEWORK_API_CODE`), not hypotheticals.
7. `R-01` explicitly states BeautyQ is not asked to replace its constructor/function
   dependency idiom with `addDependency`.
8. `C-20` remains not recommendation-ready, with the exact missing evidence named.
9. No recommendation is phrased as accepted, implemented, approved, scheduled, or as an
   opened work item; the only occurrences of those words are explicit negations.
10. The zero change-oriented distribution carries explicit symmetric calibration: all
    three change-oriented classes were actively searched and rejected.
11. The synthesis truthfully reports `WELL_USED = 7`, `BEAUTYQ_SPECIFIC = 24`,
    `INDETERMINATE = 1`, and change-oriented headline labels = 0.

Recommendation invariants: PASS.

## Validation: State Integrity

T023 scope: read-only Git truth and mutation attribution. PASS. Commands run:
`git rev-parse HEAD`, `git status --short`, `git diff --stat`, `git diff --cached --stat`,
`git diff --check`, `git diff --cached --check`, `git log`/`git show`/`git reflog` (read-only).
No ref/index/history mutation was performed by this validator.

- **Canonical evaluated source state.** `50e794584d68768403d5c45d6d7fc21c2db87f6c`,
  `stateId = 50e7945-clean`, per `research/00` (original T001 record: worktree clean,
  empty porcelain, empty diff/cached diff).
- **Current HEAD.** `7a92069ef156cc777234952970a43000b6121f61` (`2026-09-10-19-40-42`),
  whose diff from the canonical commit is exactly the five added research files
  `research/00`..`research/04` (2087 insertions, no other paths).
- **Original run-start Git truth.** At run open HEAD was `50e794584d...` with a clean
  worktree/index and no pre-existing human-owned dirty state. This is distinct from the
  later current state.
- **Later human-owned research bookkeeping.** HEAD advanced by one human-authored commit
  (`Dmytro Orlov`) adding `research/00`..`research/04`; `research/05` is present in the
  index as an added file (`A`) and is additionally modified in the worktree (`M`).
  Reflog shows prior commit/reset entries on feature-local research artifacts. These are
  human-owned research bookkeeping, not "pre-existing run-start state" and not agent Git
  mutation.
- **Current status/index truth.** `git status --short` reports only
  `AM specs/003-.../research/05-synthesis-and-recommendations.md`. The index contains a
  staged version of `research/05`; the worktree carries the additional validation edits.
  This is reported exactly, without relabeling human staging as agent-owned.
- **Unexpected source/build/framework changes.** None.
  `git diff --stat 50e794584d... HEAD -- ':!specs/003-beautyq-distage-izumi-leverage-audit/research/**'`
  is empty, and no production/test/build/project/framework file is modified or staged.
  Canonical source identity remains the evidence binding.

State-integrity exceptions: none. No manifest/digest/freeze artifact was created.
