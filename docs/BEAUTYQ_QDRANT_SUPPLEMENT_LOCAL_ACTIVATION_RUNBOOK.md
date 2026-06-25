# BeautyQ Qdrant supplement local activation runbook

## Purpose

* This is a local/operator runbook for the no-worsening Qdrant supplement path.
* This is **not** a report.
* This is **not** production rollout approval.

## Current boundary

* Default production `POST /beauty-search` remains ES-backed.
* `LeaderboardPlugin` now includes `BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleFromEnvOrThrow()` instead of including `BeautySearchRouteModules.apiElasticsearch` directly (QP10). For an absent/unset `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION` environment value this selects `EsOnlyRollback`, which is exactly `BeautySearchRouteModules.apiElasticsearch` -- the same module included before this seam existed. The default production graph and behavior are unchanged.
* Setting `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=qdrant-supplement-not-ready` or `qdrant-supplement-ready` before launching selects the corresponding supplement module; the caller (launcher/role wiring) must still separately supply the lexical/semantic backend and document lookup bindings, exactly as for `BeautySearchQdrantSupplementActivation.moduleFor(...)`. An unrecognized value throws at plugin-module-composition time (fail closed), never silently selecting ready or falling back to the supplement.
* This runbook does not change runtime behavior beyond the QP10 launcher seam described above.

## Operator values

`BeautySearchQdrantSupplementActivationConfig.fromOperatorValue(...)` accepts:

| Operator value | Selects |
|---|---|
| (absent / `None`) | `es-only-rollback` |
| `es-only-rollback` | `EsOnlyRollback` |
| `qdrant-supplement-not-ready` | `QdrantSupplementNotReady` |
| `qdrant-supplement-ready` | `QdrantSupplementReady` |
| anything else | rejected (`Left`), never selects ready |

There is no env/CLI/HOCON reader yet — this is a pure `Option[String] => Either[...]` parser used directly in tests/REPL/local wiring.

## Preflight meanings

`BeautySearchQdrantSupplementActivationPreflight.preflight(operatorValue, expectedReadiness, observedReadiness)` returns one of:

* `READY_TO_ENABLE` — only when the operator value is the explicit `qdrant-supplement-ready` value **and** the supplied readiness expectation/observed pair is compatible.
* `BLOCKED` — for any of:
  * `ES_ONLY_ROLLBACK_SELECTED` — absent config or explicit `es-only-rollback`.
  * `QDRANT_SUPPLEMENT_NOT_READY_SELECTED` — explicit `qdrant-supplement-not-ready`.
  * `INVALID_OPERATOR_CONFIG` — unrecognized operator value.
  * `READINESS_MISMATCH` — explicit `qdrant-supplement-ready` but the observed Qdrant collection (name, vector name, dimension, distance, or embedding model) does not match the expectation.

The preflight does no I/O: callers supply the readiness expectation/observed values themselves (e.g. from `QdrantCollectionReadinessConfig` and a real `QdrantCollectionIdentity` check, both out of scope here).

## Local smoke commands

Run from the repo root, one sbt invocation per command (do not chain in parallel, per `AGENTS.md`):

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP8QdrantSupplementActivationPreflightSpec'
```

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP6NoWorseningRouteMatrixSpec'
```

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP2NoWorseningRouteProofSpec'
```

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP12LocalLauncherActivationSmokeSpec'
```

## Expected outcomes

* **QP8** passes: preflight labels (`READY_TO_ENABLE`, `BLOCKED`, `ES_ONLY_ROLLBACK_SELECTED`, `QDRANT_SUPPLEMENT_NOT_READY_SELECTED`, `INVALID_OPERATOR_CONFIG`, `READINESS_MISMATCH`) are stable, and `READY_TO_ENABLE` requires compatible readiness on every mismatch axis (collection name, vector name, dimension, distance, embedding model).
* **QP6** passes: the `EsOnlyRollback` / `QdrantSupplementNotReady` / `QdrantSupplementReady` route matrix behaves as expected (200 / 503 / capped-append-of-1), with no fallback and no `AppendAll`.
* **QP2** passes when real ES, real Qdrant, and a real embedding endpoint are available locally: the route/service no-worsening proof still works end to end.
* If QP2 cancels with `QP2_RESOURCE_GATED` (real Qdrant or the embedding endpoint unavailable), that is expected local resource-gating, not a failure — **do not** treat a resource-gated cancel as a production-readiness claim either way.

## Manual rollback

* Select operator value `es-only-rollback` (or leave it unset).
* This maps to `BeautySearchQdrantSupplementActivation.EsOnlyRollback`, i.e. `BeautySearchRouteModules.apiElasticsearch` — the exact module the production graph already uses.
* This is an explicit module selection, not a runtime fallback from a hybrid/Qdrant-serving state.

## Safe not-ready state

* Select operator value `qdrant-supplement-not-ready`.
* In the opt-in route proof, valid requests reject with `503` (`BeautySearchServingGate.enabledNotReady`); malformed requests still get `400` (validation runs before the gate).
* No silent fallback to ES-only and no silent serving of the supplement path.

## Ready state

* Select operator value `qdrant-supplement-ready` only after `BeautySearchQdrantSupplementActivationPreflight.preflight(...)` returns `READY_TO_ENABLE` for your actual observed Qdrant readiness values.
* "Ready" still means the explicit opt-in, no-worsening supplement (`QdrantVariantSupplementPolicy.ExplicitConstraintsFilterPlusTop1`, capped at one append) — not a Qdrant-as-default or hybrid-by-default route.

## What this runbook does not do

* No automatic readiness HTTP call — the preflight takes readiness values as pure input.
* No startup indexing.
* No collection lifecycle manager.
* No alias/blue-green switching.
* No runtime fallback between ES and Qdrant.
* No score fusion or reranking.
* No production rollout claim.
* No route JSON / API change.

## Launcher activation seam (QP10)

* `LeaderboardPlugin` includes `BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleFromEnvOrThrow()`, which reads the `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION` environment value and selects the module via `BeautySearchQdrantSupplementActivationConfig.moduleForOperatorValue(...)`.
* This is a module-selection seam only: it does not itself bind the lexical/semantic backend or document lookup needed for the not-ready/ready states, perform readiness HTTP calls, or change any runtime/route behavior beyond which module is selected.
* Use the preflight/runbook above to validate operator values and readiness before setting this environment value to `qdrant-supplement-ready` for any local run.

## Real-resource activation preflight command (QP11)

* `BeautySearchQdrantSupplementActivationPreflightCommand.run(operatorValue, expectedReadiness, checker)` (and the env-reading convenience `runFromEnv(expectedReadiness, checker)`) is the first real operator command in this path: it reads the selected `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION` value, and -- only for the explicit `qdrant-supplement-ready` value -- runs the existing read-only `QdrantCollectionCompatibilityChecker` (QP5 stack) against the real Qdrant collection, then prints the same stable QP8 result: `READY_TO_ENABLE`, or `BLOCKED` with reason `ES_ONLY_ROLLBACK_SELECTED` / `QDRANT_SUPPLEMENT_NOT_READY_SELECTED` / `INVALID_OPERATOR_CONFIG` / `READINESS_MISMATCH`.
* A missing/unreachable Qdrant collection is reported as `BLOCKED` / `READINESS_MISMATCH`, exactly like a real mismatch -- never `READY_TO_ENABLE`.
* This is a probe only: it performs only the checker's read-only collection-info GET. It never creates/deletes/recreates a collection, never switches a route, never starts indexing, and never auto-activates `qdrant-supplement-ready`.
* Run it as the existing `testOnly`-spec convention (this repo has no standalone CLI/main-class command pattern):

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP11QdrantSupplementRealResourcePreflightSpec'
```

* Expected outcomes: the pure operator-state tests (absent / `es-only-rollback` / `qdrant-supplement-not-ready` / an unrecognized value, plus deterministic compatible/mismatched/missing/unreachable checker fixtures) always pass with no real resource needed. The final real-local-Qdrant test creates one UUID-namespaced test-only collection (mirroring `QdrantCollectionCompatibilityIntegrationSpec`'s lifecycle, deleting it afterwards), checks it as `READY_TO_ENABLE`, then checks the same collection against a deliberately mismatched expectation as `BLOCKED` / `READINESS_MISMATCH`. If the local/Docker-managed Qdrant the `LeaderboardTest` harness provisions is unavailable, that test fails at the harness/Docker level, which is `VERIFICATION BLOCKED` per `AGENTS.md`, not a production-readiness claim either way.

## Local launcher enablement smoke (QP12)

* `QP12LocalLauncherActivationSmokeSpec` is the narrowest local launcher-level smoke for the seam. It drives the launcher entry point (`BeautySearchQdrantSupplementActivationLauncherSeam.selectedModuleFromEnvOrThrow()` for the absent-env default, and the documented operator-value -> module mapping for explicit values) and serves `/beauty-search` over a **real** locally bound HTTP server (`HttpContractTestSupport.observe`: an Ember server on the repo's ephemeral `127.0.0.1:0` local-port convention plus a real Ember client).
* The repo has no process-spawn / fixed-port `./launcher` test convention, so a full `./launcher :leaderboard` subprocess (which needs Docker Postgres + ES) is intentionally not used; the in-process real-server smoke is the established pattern.
* What it proves:
  * absent env -> ES-backed default serves 200 (malformed stays 400), no Qdrant binding required;
  * explicit `es-only-rollback` -> equivalent ES-backed default (200);
  * explicit `qdrant-supplement-not-ready` -> valid request returns 503 over the real server, fail-if-called backends proving no silent fallback;
  * explicit `qdrant-supplement-not-ready`, **composed with the QP13 runtime binding module** -> valid request returns 503 over the real server, with fail-if-called leaf clients (`ElasticsearchJsonClient`, `EmbeddingClient`, `QdrantSearchClient`) proving no silent fallback;
  * explicit `qdrant-supplement-ready` -> the post-QP13 final state. Through the launcher seam **alone** (no QP13 runtime module) it still fails closed naming the three keys (the qualified lexical ES backend `@Id("qdrantSupplementLexicalElasticsearch")`, `SemanticCandidateBackend`, `VariantSearchDocumentLookup`) -- this is now only the selection-only boundary, **not** the final ready state. Composed with `BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(vectorSearchSpec)` and only leaf I/O doubles plus a ready catalog fixture (no test-local binding of the three keys), the ready route serves 200 capped at `ExplicitConstraintsFilterPlusTop1` (one append, never AppendAll), preserving the ES prefix, never duplicating an ES id, and leaving the ES-owned provider carousel untouched by the append;
  * invalid env value -> fails closed at module composition, never selecting ready or the Qdrant supplement.

  The earlier QP12 expectation that `READY_LAUNCHER_BINDINGS_BLOCKED` is the ready state's final outcome no longer holds: the QP13 runtime binding module closes it. QP11 preflight (`READY_TO_ENABLE`) is still required before an operator selects `qdrant-supplement-ready` for any real run.
* Run it as the existing `testOnly`-spec convention:

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP12LocalLauncherActivationSmokeSpec'
```

## Ready-mode runtime binding module (QP13)

* `BeautySearchQdrantSupplementRuntimeBindingModules.supplementRuntimeBindings(vectorSearchSpec)` is the smallest module that closes the exact three keys QP12 reported missing for the ready/not-ready states (`READY_LAUNCHER_BINDINGS_BLOCKED`): the qualified lexical ES backend `BeautySearchBackend[IO] @Id("qdrantSupplementLexicalElasticsearch")`, `SemanticCandidateBackend[IO]`, and `VariantSearchDocumentLookup[IO]`.
* Each is bound to an existing source-confirmed implementation: the lexical leg is the ES-backed `ElasticsearchSearchBackend` over the opt-in module's `BeautySearchSpec` (not in-memory, not Qdrant); the semantic leg is `QdrantSemanticCandidateBackend`/`QdrantSemanticCandidateSearch` (embedding -> Qdrant `points/search`, append-only, no fusion/reranking, no residual-text routing); the document lookup is `InMemoryVariantSearchDocumentLookup` resolving Qdrant candidate variant ids against the ready seed catalog (no invented id translation, Qdrant point-id rules unchanged).
* It binds **no** Qdrant/ES/Llama leaf I/O client, performs no readiness HTTP call, starts no indexing, registers no config, and is **not** included by `LeaderboardPlugin` default modules. The caller/launcher still supplies the leaf collaborators (`ElasticsearchJsonClient`, `EmbeddingClient`, `QdrantSearchClient`) and the seed loader, exactly as the seam already documents; the QP10 seam itself stays selection-only, so the absent-env / `es-only-rollback` default graph never gains a Qdrant edge.
* QP11 preflight (`READY_TO_ENABLE`) remains required before an operator selects `qdrant-supplement-ready`; QP13 changes none of the preflight/readiness lifecycle.
* `QP13QdrantSupplementRuntimeBindingsSpec` proves: with the runtime binding module supplied, the ready route builds and serves over a real local server capped at `ExplicitConstraintsFilterPlusTop1` (one append, never AppendAll); the same bindings under the not-ready gate still reject a valid request with 503 without invoking the leaf collaborators; absent-env / `es-only-rollback` still serves the ES default **without** the runtime module; an invalid env value still fails closed.

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP13QdrantSupplementRuntimeBindingsSpec'
```

## Activation diagnostics (QP14)

* `BeautySearchQdrantSupplementActivationDiagnostics.from(operatorValue, preflightResult)` is a pure operator-visible summary built from the original operator value plus an already-computed QP8 `BeautySearchQdrantSupplementActivationPreflightResult`. There is **no** runtime logging seam in this path (no `LogIO`/`IzLogger`), so this is a pure formatter only — like the existing `ExperimentalHybridRouteDiagnostics`. It performs no I/O, no readiness HTTP call, no route switching, no activation, and emits **no route JSON / API output**.
* It exposes a stable ordered key/value line list (`.lines` / `.renderLines` as `key=value`):

| Key | Value |
|---|---|
| `activation.mode` | `EsOnlyRollback` / `QdrantSupplementNotReady` / `QdrantSupplementReady` / `InvalidConfig` |
| `activation.operatorValue` | the raw operator value, or `<absent>` when unset |
| `activation.parse` | `ACCEPTED` (a mode was selected) / `REJECTED` (fail-closed invalid value) |
| `preflight.status` | `READY_TO_ENABLE` / `BLOCKED` (the QP8 status label) |
| `preflight.reason` | the verbatim QP8 reason (`READY_TO_ENABLE`, `ES_ONLY_ROLLBACK_SELECTED`, `QDRANT_SUPPLEMENT_NOT_READY_SELECTED`, `INVALID_OPERATOR_CONFIG`, `READINESS_MISMATCH`) |
| `preflight.mismatches` | comma-joined mismatch **type** tokens (`COLLECTION_NAME`, `VECTOR_NAME`, `DIMENSION`, `DISTANCE`, `EMBEDDING_MODEL`), or `none`; never the expected/observed payload values |
| `decision.summary` | `READY_TO_ENABLE` / `DEFAULT_ES_ONLY` / `BLOCKED_NOT_READY` / `BLOCKED_READINESS_MISMATCH` / `BLOCKED_INVALID_CONFIG` |

* Invalid operator config is fail-closed: `activation.parse=REJECTED`, `activation.mode=InvalidConfig`, and `QdrantSupplementReady` is never reported as the selected mode.

```bash
sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP14QdrantSupplementActivationDiagnosticsSpec'
```
