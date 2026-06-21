# BeautyQ M9 Real-Resource Smoke Runbook (Stub)

## 0. Status and scope

This runbook is **future-only**. It does not approve, request, or trigger any real
ES or Qdrant execution. No real backend call is authorized by this document. It
describes, at a high level, how a future explicit task *would* run resource-smoke
evidence once that task is separately approved.

Default backend truth is unchanged by this runbook:

- default production `/beauty-search` remains ES-backed;
- the Qdrant explicit opt-in route remains disabled by default;
- Qdrant production activation remains **not approved**;
- real backend calls remain disabled by default.

The M9 BeautyQ static scorecard, real-call checkpoint, ES-only smoke plan,
Qdrant-only smoke plan, combined ES/Qdrant comparison plan, real-resource
prerequisites audit, saved evidence schema, deterministic evidence renderer, and
default/no-config artifact are accepted as **offline eval / planning / reporting
contracts only**. None of them is backend quality evidence or activation approval.

The static scorecard verdict is `dataset_static_rows_ready` (63 mapped rows, 3
representative anchors, 60 placeholder-only rows, 0 real/ES/Qdrant backend evidence
rows).

**Execution gate milestone closed as planning/reporting only** (options 142–145B):
a runbook/evidence consistency contract statically checks this runbook against the
saved evidence schema and default/no-config artifact; separate ES-only, Qdrant-only,
and combined ES/Qdrant execution gate designs reach `pending_explicit_execution_task`
only when prerequisites are complete and otherwise block; a deterministic
execution-gate renderer produces a checked-in default/no-config markdown artifact
(`bifunctor-tagless/src/test/resources/leaderboard/search/eval/m9-beautyq-real-resource-execution-gate-default.md`)
rendering blocked/skip evidence for all three gates, never success. See
`docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` section 6.1.9 for detail. No
real ES/Qdrant execution or production activation is implemented or accepted.

## 1. Future modes

This runbook anticipates three future, separately-approved modes:

1. **ES-only resource smoke** — run explicit offline dataset queries against an
   approved offline ES adapter, recording `serving_mode = es_only` /
   `candidate_source = es`.
2. **Qdrant-only resource smoke** — run explicit offline dataset queries against an
   approved offline Qdrant adapter, recording `serving_mode = qdrant_only` /
   `candidate_source = qdrant`.
3. **Combined ES/Qdrant resource comparison** — compare ES-only and Qdrant-only
   results in offline report space only, preserving per-candidate source
   attribution.

None of these modes is implemented as serving behavior. They are offline
evidence-capture modes only.

## 2. Required prerequisites (high level)

For each mode, the following must all be present before any future execution is
considered:

| Prerequisite | ES-only | Qdrant-only | Combined comparison |
|---|---|---|---|
| Accepted static scorecard (`dataset_static_rows_ready`) | required | required | required |
| Accepted real-call checkpoint decision | required | required | required |
| Explicit resource config for the relevant backend | ES config | Qdrant config | both |
| Operator approval | — | — | required |
| Accepted real-resource prerequisites audit | required | required | required |
| Saved evidence schema + deterministic renderer for capture | required | required | required |

The real-resource prerequisites audit separately audits ES-only, Qdrant-only, and
combined ES/Qdrant comparison prerequisites. The saved evidence schema defines
separate ES-only, Qdrant-only, and combined evidence shapes.

## 3. Default / no-config behavior

- Default / no-config **must remain blocked/skip**. The deterministic renderer
  produces a checked-in default/no-config markdown artifact that renders
  blocked/skip evidence, never success evidence.
- Complete prerequisites mean **pending execution only**, not success. A complete
  set of prerequisites renders pending-execution evidence; it is not a quality,
  readiness, or activation claim.

## 4. Future evidence capture

If and when a future explicit task runs a mode:

- evidence must use the **saved evidence schema** and the **deterministic
  renderer / checked-in artifact shape**;
- ES-only and Qdrant-only evidence preserve their respective source/mode
  attribution;
- combined comparison evidence may record:
  - ES candidate ids;
  - Qdrant candidate ids;
  - overlap;
  - misses;
  - unexpected candidates;
  - missing lookup rate;
  - comparison notes.

## 5. Preserved production boundaries

- default `/beauty-search` remains ES-backed;
- the Qdrant opt-in route remains disabled by default;
- Qdrant production activation remains not approved;
- no production route activation;
- no route switch;
- no hybrid serving, fallback, score fusion, reranking, or production telemetry.

## 6. Not allowed by this runbook

This runbook does **not** allow, claim, or imply any of the following:

- no production route activation;
- no serving approval;
- no quality-green claim;
- no default-route switch;
- no ES/Qdrant execution without a future explicit task and operator approval
  (where operator approval is required, e.g. combined comparison).
