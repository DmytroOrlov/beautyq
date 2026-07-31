# BeautyQ Search Gen2 — Operations

This document is the canonical operator runbook for the implemented BeautyQ Search Gen2 startup modes, status inspection, response-warning interpretation, restart-only recovery, and partial-activation handling. It does not own architecture, business policy, implementation sequencing, or historical rationale. Those remain with the technical specification, executable policy owners, post-cutover plan, and Git history respectively.

Status: **Q1 completed, O0 completed, O1 completed, Q2 active — explicit quality-policy decision required, D1 requires second-domain product input**

## Supplement startup policy

`SupplementStartupPolicy` is BeautyQ wiring-owned. The activation choice is applied before DI planning. Disabled is selected before provisioning and its retained managed graph does not contain Qdrant or embedding resources.

### Launcher commands

```bash
# Required is the default
./launcher -u scene:managed :leaderboard

# Explicit optional-Qdrant startup
./launcher -u scene:managed -u supplement-startup:preferred :leaderboard

# Intentional supplement kill switch
./launcher -u scene:managed -u supplement-startup:disabled :leaderboard
```

### Startup modes

| Policy | Behavior | Condition | Status endpoint |
|---|---|---|---|
| `required` (default) | Qdrant/embedding failure fails startup | no route published | no endpoint |
| `preferred` | eligible transport/unavailability degrades to baseline-only | `degraded` | HTTP 200 |
| `disabled` | baseline-only serving; supplement resources are excluded at plan time | `limited` | HTTP 200 |

`BeautyQSupplementStartup` is the app-shell Distage activation adapter. The choice is applied before planning; Disabled is selected before provisioning and its retained managed graph does not contain Qdrant or embedding resources.

### Immutable startup state

Startup produces one final `StartupServingStatus` that is fixed for the process lifetime.

- `healthy` — full search, supplement ready, `restartRequired=false`
- `degraded` — baseline-only due to preferred-mode supplement unavailability, `restartRequired=true`
- `limited` — baseline-only due to operator-disabled supplement, `restartRequired=true`

No mode promotes itself after startup. Dependency recovery does not auto-promote. Restart is required for policy change or dependency recovery.

## Operator status endpoint

```
GET /beauty-search/status
```

Returns HTTP 200 whenever a startup object exists.

```json
{
  "live": true,
  "ready": true,
  "condition": "healthy|degraded|limited",
  "startupPolicy": "required|preferred|disabled",
  "servingMode": "full_search|baseline_only",
  "restartRequired": false,
  "reason": null,
  "observedAt": "...",
  "snapshot": {
    "capturedAt": "...",
    "sourceRevision": "...",
    "sourceContentFingerprint": "...",
    "projectedDocumentsFingerprint": "...",
    "ageSeconds": 0
  },
  "startupDurations": {
    "materializationNanos": 0,
    "activationNanos": 0
  },
  "activeGenerations": {
    "activatedAt": "...",
    "ageSeconds": 0,
    "elasticsearch": {
      "reference": "...",
      "physicalTarget": "..."
    },
    "qdrant": {
      "generationId": "...",
      "physicalCollection": "..."
    }
  }
}
```

For baseline-only status, `activeGenerations.qdrant` is JSON null.

## Response warnings

Every search response includes `servingMode`, `restartRequired`, and `warnings` fields.

| Startup state | Request outcome | Response warning | `restartRequired` |
|---|---|---|---|
| healthy `full_search` | success/no candidates | none | `false` |
| healthy `full_search` | request supplement failure | request failure code | `false` |
| degraded `baseline_only` | supplement not executed | `qdrant_supplement_unavailable` | `true` |
| limited `baseline_only` | supplement not executed | `qdrant_supplement_operator_disabled` | `true` |

### Warning codes

- `qdrant_supplement_unavailable` — Qdrant supplement was unavailable at startup; the complete Elasticsearch baseline was returned; restart is required
- `qdrant_supplement_operator_disabled` — Qdrant supplement was disabled by operator policy; the complete Elasticsearch baseline was returned; restart is required
- Request-time codes use the existing typed supplement outcome reason code

## Partial cross-backend activation

Activation order: prepare Qdrant → activate ES → activate Qdrant.

If ES has switched and final Qdrant activation fails:
- `required`: startup fails, no route published
- `preferred`: eligible transport/unavailability degrades; malformed/incompatible/ambiguous state fails hard

No automatic ES rollback. The operator fixes the typed cause and restarts. Deterministic lifecycle reconverges on the same generation identities.

### Partial-activation inspection

```bash
curl -sS 'http://<elasticsearch>/_alias/beautyq_variant_gen2'
curl -sS 'http://<qdrant>/aliases'
curl -sS 'http://<application>/beauty-search/status'
```

### Recovery procedure

1. Stop or fence other serving/startup processes
2. Inspect exact ES alias target
3. Inspect exact Qdrant alias target
4. Retain the typed startup failure
5. Do not manually delete or wildcard resources
6. Do not roll ES back automatically
7. Fix the typed cause
8. Restart
9. Verify both exact aliases and healthy status

## Restart-only recovery

- Required is default — forced by activation default.
- Preferred is explicit optional-Qdrant startup.
- Disabled is the kill switch.
- No mode promotes without restart.
- No retry, polling, background recovery, automatic promotion, rollback, or cross-backend transaction exists.

## Qdrant cleanup (operator-owned)

Automatic GC is not implemented. Exact cleanup is allowed only during a quiescent maintenance
window with every Search Gen2 activation process stopped or an equivalent administrative fence held
for the complete inventory/review/deletion interval:

1. Stop every activation-capable Search Gen2 process or establish the administrative activation fence.
2. Read and record the exact current active Qdrant alias target.
3. List physical collections for inventory only; never treat the listing as deletion authority.
4. Ignore every collection outside the exact configured Gen2 physical prefix.
5. Read each exact candidate's persisted collection metadata.
6. Decode that metadata through the existing Search Gen2 Qdrant generation-metadata codec.
7. Recompute the generation identity and deterministic physical collection name.
8. Reject missing, malformed, unsupported, or mismatched metadata.
9. Exclude the collection targeted by the current active alias.
10. Exclude every collection still building or otherwise in progress, including one whose exact
    point-count or lifecycle evidence is incomplete.
11. Record the complete exact candidate inventory at T0 and delete nothing.
12. Wait at least 24 hours while the activation fence remains enforceable.
13. Repeat the complete active-alias, metadata, identity, name, and lifecycle-status dry run at T1.
14. Select a candidate only when its exact generation identity and physical name are byte-for-byte
    the same at T0 and T1. The 24 hours is the interval between complete observations; no collection
    creation timestamp is assumed or invented.
15. Reread the current active alias immediately before each deletion and abort if it changed,
    disappeared, or became ambiguous.
16. Delete only the individually approved exact physical collection name.
17. Never use prefix deletion, wildcard deletion, `_all`, or inferred ownership.
18. Verify the current active alias and perform an active Search Gen2 query after deletion; record
    successful absence, typed failure, or retry state.
19. Release the administrative fence or restart the stopped serving processes before resuming traffic.

Database changes become visible after the coordinated restart; this is restart-only freshness, not
CDC lag. A failed or changed identity remains for a later fenced retry, and no online automatic
cleaner is permitted.

## Not implemented

- Hot reconciliation or CDC
- Background recovery or automatic promotion
- Multi-process generation coordination
- Persistent embedding caches
- Automatic Qdrant GC
- Metrics exporter
