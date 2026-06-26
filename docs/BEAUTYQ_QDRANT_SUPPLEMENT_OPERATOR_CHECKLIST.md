# BeautyQ Qdrant supplement operator checklist

## Scope

- Manual/operator checklist for the no-worsening Qdrant supplement path.
- Not a production rollout approval.
- Not a claim that Qdrant is the default backend.

## Before selecting ready

- QP11 preflight must return `READY_TO_ENABLE`.
- QP12 local launcher smoke must pass.
- QP14 diagnostics must pass.
- Future domain acceptance must start from a measured local gate: at least one positive supplement improvement, zero ES-baseline regression unless an explicit harm budget is approved, no default route switch, and no Qdrant-as-default/fallback/fusion/rerank.
- Default/rollback path must remain ES-backed.
- Invalid activation must fail closed.
- Not-ready must return 503 with no fallback.
- Full `sbt test` green may be recorded only as user-reported QP15 evidence, not as a requirement for every operator toggle.

## Env values

- `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=es-only-rollback`
- `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=qdrant-supplement-not-ready`
- `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=qdrant-supplement-ready`

## Commands

- Preflight: `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP11QdrantSupplementRealResourcePreflightSpec'`
- Smoke: `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP12LocalLauncherActivationSmokeSpec'`
- Diagnostics: `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP14QdrantSupplementActivationDiagnosticsSpec'`

## Enable ready

- Do not set `qdrant-supplement-ready` unless QP11 reports `READY_TO_ENABLE`.
- If preflight reports `BLOCKED`, do not enable ready.

## Rollback

- Set `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=es-only-rollback`.
- Rollback is explicit ES-only module selection, not runtime fallback.

## Safe not-ready

- Set `BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION=qdrant-supplement-not-ready`.
- Valid requests should reject with 503 in the selected supplement route.
- No silent fallback.

## Diagnostics labels

- `activation.mode`
- `activation.operatorValue`
- `activation.parse`
- `preflight.status`
- `preflight.reason`
- `preflight.mismatches`
- `decision.summary`
- `READY_TO_ENABLE`
- `BLOCKED`
- `ES_ONLY_ROLLBACK_SELECTED`
- `QDRANT_SUPPLEMENT_NOT_READY_SELECTED`
- `INVALID_OPERATOR_CONFIG`
- `READINESS_MISMATCH`

## Blocked outcomes

- `READINESS_MISMATCH`: fix collection/vector/dimension/distance/model mismatch; do not recreate production-like collection silently.
- `INVALID_OPERATOR_CONFIG`: fix env value; do not fallback to ready.
- `QP15_PROCESS_CURL_BLOCKED_NEEDS_PORT_SOURCE`: process/curl path is not source-confirmed; use the source-confirmed real-socket smoke until a separate task adds a fixed-port convention.

## Forbidden claims

- Qdrant is not the default backend.
- No fallback.
- No score fusion/reranking.
- No startup indexing.
- No production collection manager.
- No alias/blue-green.
- No route JSON/API change.
- Benchmark output is not an automatic rollout signal.
