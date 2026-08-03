# BeautyQ Search Gen2 — Operations

This document is the canonical operator runbook for the implemented BeautyQ Search Gen2 startup modes, status inspection, response-warning interpretation, restart-only recovery, and partial-activation handling. It does not own architecture, business policy, implementation sequencing, or historical rationale. Those remain with the technical specification, executable policy owners, post-cutover plan, and Git history respectively.

Status: **Q1 completed, O0 completed, O1 completed, Q2 active — authorized Q2-B break-glass correction source ready, 96-case visible managed proof green, protected holdout independently replenished and frozen, commit/root evidence/fresh protected acceptance pending, D1 requires second-domain product input**

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

## Protected input authoring and freeze

The protected corpus and policy are created before protected execution through separated
model-assisted author and judge passes, followed by an audit pass that alone may compare their query
inventory with the visible corpus. No external employee is required; the checkout operator approves
the first source-grounded set. The passes cannot inspect protected search output, and evaluation output
must never be used to relabel or tune the same holdout.

The freeze runner executes no search and acquires no Elasticsearch, Qdrant, embedding, application or
startup resource. It strictly validates the corpus and policy, checks every judgment identity against
the canonical typed seed catalog, requires an acceptable variant for every exact-intent case, applies
exact and deterministic NFKC query-leakage audits against visible and protected inputs, binds both
input-file hashes plus both authoring-draft hashes and the canonical source fingerprint, and verifies
the five versioned test resources owned by `beautyq-search-gen2-eval`:

- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json` — protected evaluation input;
- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json` — protected acceptance-gate input;
- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-input-audit-v2.json` — immutable aggregate integrity/provenance evidence;
- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json` — author-pass provenance input;
- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json` — judge-pass provenance input.

All five are tracked test resources available after an ordinary checkout. “Protected” means frozen
and excluded from output-driven tuning; it does not mean confidential. They are not production
`src/main/resources`, and no external restore or CI secret provisioning is required. The audit record
is evidence about validation and freeze, contains no cases or judgments, and is not an acceptance-policy
owner. Exact and normalized duplicate checks detect direct leakage only; they are not semantic-similarity
or fuzzy-search claims. Bootstrap and verify consume the frozen corpus and policy read-only and never
author or modify them.

The exact freeze invocation is:

```bash
sbt --batch --no-global \
  -Dsbt.server=false \
  -Dsbt.server.forcestart=true \
  -Dsbt.ivy.home=target/codex-sbt/ivy2 \
  'leaderboard-app-shell/Test/runMain \
    leaderboard.search.BeautyQProtectedInputFreezeMain \
    --protected-corpus beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json \
    --protected-policy beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json \
    --author-draft beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json \
    --judged-draft beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json \
    --audit-output target/search-gen2/private/beautyq-protected-input-audit-v2.json \
    --source-revision <starting-40-hex-revision> \
    --author-pass-id <stable-author-pass-id> \
    --judge-pass-id <stable-judge-pass-id> \
    --audit-pass-id <stable-audit-pass-id>'
```

The integrity closeout freeze completed with this exact invocation:

```bash
sbt --batch --no-global \
  -Dsbt.server=false \
  -Dsbt.server.forcestart=true \
  -Dsbt.ivy.home=target/codex-sbt/ivy2 \
  'leaderboard-app-shell/Test/runMain \
    leaderboard.search.BeautyQProtectedInputFreezeMain \
    --protected-corpus beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json \
    --protected-policy beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json \
    --author-draft beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json \
    --judged-draft beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json \
    --audit-output target/search-gen2/private/beautyq-protected-input-audit-v2.json \
    --source-revision 3f55a3082d617fcca55be8104111893fb251b906 \
    --author-pass-id q2i-recovery-author-v2 \
    --judge-pass-id q2i-recovery-judge-v2 \
    --audit-pass-id q2i-recovery-audit-v2'
```

The completed freeze produced an aggregate audit binding source revision, typed corpus/policy and
canonical-source fingerprints, input/draft hashes, ordered slice counts, exact-intent completeness,
and catalog/leakage validation outcomes. Those exact values belong to the operator's protected audit
record rather than this public runbook. Protected execution has not occurred, and no accepted manifest
has been generated.

## Canonical Q2-I evaluation resources

The first Q2-I fixture is versioned under `beautyq-search-gen2-eval/src/test/resources` and is
available after an ordinary checkout. The canonical files are:

- `leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json` — protected evaluation input;
- `leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json` — protected acceptance-gate input;
- `leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-input-audit-v2.json` — immutable aggregate integrity/provenance evidence;
- `leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json` — author-pass provenance input;
- `leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json` — judge-pass provenance input.

All five are tracked test resources owned by the BeautyQ evaluation module. They are not production
`src/main/resources`, and no external restore or CI secret provisioning is required. The author and
judged drafts reproduce the separated author/judge provenance used by the freeze audit; they are not
bootstrap runtime inputs. “Protected” is an evaluation-process classification: ordinary development
must not tune labels, thresholds, vocabulary, or search behavior from protected execution output.
Aggregate-only execution and break-glass migration/replenishment rules remain unchanged.

The freeze runner reads the four source inputs from these canonical test-resource paths and writes only
a verification audit under `target/search-gen2/private/beautyq-protected-input-audit-v2.json`. After
freeze, the generated audit must compare byte-for-byte with the tracked canonical audit resource.
Deleting `target` therefore removes only disposable generated output and never destroys the canonical
Q2-I inputs.

## Protected acceptance and first baseline (manual only)

### Phase 1 — protected acceptance and bootstrap candidate only

Begin only after the coordinator has recorded a green root aggregate suite for the committed
supplement-boundary closeout and the tracked canonical test resources have been verified.
Protected acceptance inputs are versioned evaluation resources, not production resources:

- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json`
- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json`
- `beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-input-audit-v2.json`

The test-owned runner is not an auto-discovered suite. Invoke it only from a clean committed
revision with an explicit application revision argument. The manual main installs that value as
`search.gen2.eval.application-revision` inside its forked JVM before constructing the evaluation
environment and restores any prior process value after execution:

```bash
sbt --batch --no-global \
  'leaderboard-app-shell/Test/runMain leaderboard.search.BeautyQProtectedAcceptanceMain \
    --protected-corpus beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json \
    --protected-policy beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json \
    --output-dir target/search-gen2/protected \
    --application-revision <clean-commit>'
```

The runner loads both inputs strictly, proves the visible acceptance gate before executing any
protected case, and uses the same startup/application path for visible and protected evidence.
The protected runner writes exactly three aggregate-only artifacts:

- `beautyq-protected-aggregate.json`
- `beautyq-protected-measurement.json`
- `beautyq-protected-acceptance-gate.json`

It never emits protected case IDs, queries or result IDs. Missing or malformed protected inputs and
unavailable external resources are non-zero operational failures, not synthetic acceptance.

### Latest protected Q2-B disposition

The last correctly attributed protected acceptance used committed revision
`ddc9f2b9bd39abdb08a4eab4a8f2e08043d0c524`, reported `applicationRevisionSource=system-property`,
and was red only on `metric-protected-slice:exact-intent-variants/success/10`. The coordinator then
authorized `q2-break-glass-exact-intent-variants-success-10-v1`. Before disclosure, an isolated
48-case replacement reserve was independently authored, judged, structurally audited, and frozen.
The break-glass owner disclosed only the seven cases contributing to that authorized check, using the
minimal typed top-cutoff projection and without emitting metric values, thresholds, unrelated
identities, or passing protected cases.

All seven disclosed cases are now permanent visible Regression evidence. The BeautyQ vocabulary owns
the source correction, including the appended r089 declaration; generic precedence, ranking, metric,
threshold, and lifecycle owners were not changed. The managed visible communication proof passed with
96 warmup executions and 288 measured executions, including every hard/no-harm gate. Seven replacements
were selected only from the frozen pre-disclosure reserve. The protected corpus was audited and frozen
again at 24 cases with slice counts 8/6/4/3/3.

This correction source has not yet been committed or evaluated through post-correction protected
acceptance. Preserve the earlier RED artifacts as historical evidence. Next, review and commit this
source patch, obtain operator-owned root evidence for that exact commit, and run one fresh correctly
attributed protected acceptance. Bootstrap and candidate generation remain unauthorized until that
fresh protected gate is green; promotion and verify remain later boundaries.

After the protected runner is green, the accepted-baseline runner may bootstrap a candidate from the
same clean committed application revision and verified tracked canonical inputs:

```bash
sbt --batch --no-global \
  'leaderboard-app-shell/Test/runMain leaderboard.search.BeautyQAcceptedBaselineMain \
    --mode bootstrap \
    --protected-corpus beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json \
    --protected-policy beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json \
    --output-dir target/search-gen2/protected \
    --application-revision <clean-commit>'
```

Bootstrap derives `beautyq-accepted-baseline-candidate.json` only after the existing protected gate is
green. It never edits source resources. When it reports `ACCEPTED_BASELINE_CANDIDATE_READY`, preserve
the aggregate artifacts and candidate, then stop for coordinator/operator review. Do not copy the
candidate to the canonical resource in the same delegated task, and do not run verify yet.

### Coordinator/operator candidate review

Review only aggregate-safe evidence:

- application revision;
- schema and policy versions;
- corpus and policy fingerprints;
- protected gate pass/fail codes;
- provenance IDs;
- ordered aggregate observation keys and counts;
- candidate digest;
- absence of identity-level protected fields.

Do not publish metric values, queries, case IDs, result IDs, or judgments. Candidate generation does
not authorize promotion.

### Phase 2 — explicit promotion and verify

Begin this phase only after explicit coordinator/operator approval of the preserved Phase 1 candidate.
Require the same application-revision identity recorded in Phase 1, the same verified tracked canonical
inputs, and the unchanged candidate. Copy it byte-for-byte to the single aggregate-only classpath resource:

`beautyq-search-gen2-eval/src/main/resources/leaderboard/search/beautyq/gen2/eval/beautyq_accepted_evaluation_baseline_v1.json`

Verify byte equality and digest before running focused canonical-resource tests. The promoted resource
necessarily makes the worktree non-clean; no search, evaluation-policy, lifecycle, route or corpus
source may change after Phase 1. Then run the existing accepted-baseline owner independently with
`--mode verify --application-revision <clean-commit>` against the same real evidence path,
application-revision identity, and verified tracked canonical inputs. The verify main uses the same
fork-safe scoped property installation and restoration. Verify loads only the canonical resource, writes
`beautyq-accepted-baseline-verification.json`, and compares ordered aggregate observations and stable
provenance. It does not use these run-specific audit fields as equality requirements: application
revision, Elasticsearch generation reference, Qdrant generation ID, visible report digest, protected
report digest and top-level manifest report digest. It never overwrites the canonical resource. A red
or blocked run produces no verification manifest. Close Q2 documentation only after green verify; no
automated promotion service exists.

## Not implemented

- Hot reconciliation or CDC
- Background recovery or automatic promotion
- Multi-process generation coordination
- Persistent embedding caches
- Automatic Qdrant GC
- Metrics exporter
