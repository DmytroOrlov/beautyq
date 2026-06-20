# Qdrant Explicit Opt-In Route Smoke Checklist

This is an operator/developer smoke checklist for `BeautySearchRouteModules.apiQdrantExplicitOptIn`.
It is optional, resource-gated, and separate from production route activation.
The separate activation gate is documented in `docs/local/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md`.

## 1. Scope And Non-Goals

Scope:

- Assemble the explicit opt-in Qdrant route path in a bounded, repeatable way.
- Confirm the prerequisite evidence package is present before route assembly.
- Keep the default production `POST /beauty-search` route ES-backed.
- Treat seed/eval metrics as offline decision support only.

Non-goals:

- No default route switch.
- No production route activation.
- No hybrid serving.
- No fallback.
- No score fusion.
- No reranking.
- No shadow serving.
- No traffic mirroring.
- No Docker requirement for normal docs validation.

## 2. Required Evidence Before Opt-In Assembly

Before assembling `BeautySearchRouteModules.apiQdrantExplicitOptIn`, confirm:

- M6 readiness evidence is ready.
- M7 activation/source-confirmation evidence is ready.
- Disabled-by-default config approval is ready.
- No-regression evidence is approved.
- Offline eval evidence is present and reviewed.
- Observability/status evidence is present.
- Rollback/disable control evidence is present.
- Separate route/serving approval exists for the explicit opt-in route only.

Record the source of each item. If any item is missing, stop before assembly.

## 3. Module Assembly Names

The explicit opt-in assembly path uses these module names:

- `BeautySearchRouteModules.apiQdrantExplicitOptIn`
- `BeautySearchRouteModules.seedCatalogQdrantExplicitOptIn`
- `BeautySearchCatalogBackendModules.seedResourceQdrantExplicitOptIn`

The default production path remains:

- `BeautySearchRouteModules.apiElasticsearch`
- `BeautySearchRouteModules.seedCatalogElasticsearch`
- `BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured`

## 4. What Focused Specs Prove

Use focused specs to prove the bounded facts that matter:

- `BeautySearchOptInRouteModuleSpec.scala`: the explicit opt-in route stays separate from the default ES route and is prerequisite-gated.
- `BeautySearchProductionRouteExposureSpec.scala`: default `POST /beauty-search` remains ES-backed.
- `QdrantProductionCandidateServingApprovalRequestSpec.scala`: the evidence package is ready to ask for approval, but does not itself approve production activation.
- `QdrantProductionCandidateQualityGateSpec.scala` and related Qdrant readiness/eval specs: offline evidence is interpretable and bounded.

If a focused spec fails, do not broaden the slice. Fix the smallest missing prerequisite or stop.

## 5. What Full `sbt test` Proves

The full suite is the wide compatibility check, not a production-activation signal. Keep the exact counts and pending-map interpretation in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` and `docs/codebase-review/06-tests-and-contracts.md`; this checklist only relies on that recorded green state as opt-in-readiness evidence, not as production-route approval.

## 6. Seed/Eval Metrics Interpretation

Read curated seed/eval metrics as offline decision support, not production telemetry.

Use:

- curated canonical seed queries;
- representative seed/eval fixture queries;
- regression cases;
- edge cases;
- negative cases.

Do not:

- infer real production traffic shape;
- claim shadow-serving usefulness without real production traffic;
- treat offline metrics as a production rollout decision;
- treat offline metrics as route-activation approval.

## 7. Optional Resource-Gated Smoke

Run this only if local resources are intentionally available.
Do not start Docker or other services just to validate the docs path.

Suggested record format:

```text
Command:
<exact command>

Result:
<pass / fail / canceled / blocked>

Notes:
<what was available, what was missing, what evidence was recorded>
```

Suggested smoke steps:

1. Assemble the explicit opt-in route module in a focused spec run.
2. Confirm the default ES route still serves `POST /beauty-search`.
3. If local Qdrant and embedding resources are already available, run the resource-gated Qdrant smoke and record the exact command/result.

If local resources are not already available, stop and record the cancellation reason instead of provisioning them.

## 8. Stop Conditions

Stop immediately if any of the following is true:

- Required evidence is missing.
- The no-regression or quality gate fails.
- The route prerequisite check rejects the assembly.
- The default graph exposes Qdrant unexpectedly.
- The default `/beauty-search` behavior changes.
- The route assembly would require production activation approval that has not been granted.

## 9. Forbidden Without Separate Approval

Do not treat this checklist as approval for:

- production route activation;
- a default route switch;
- default graph exposure changes in `LeaderboardPlugin.modules.apiBase[IO]` or `BeautySearchRouteModules.apiElasticsearch`;
- hybrid serving;
- fallback;
- score fusion;
- reranking;
- shadow serving;
- traffic mirroring;
- any `/beauty-search` default behavior change.
