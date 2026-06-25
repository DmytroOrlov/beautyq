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
