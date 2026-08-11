# BeautyQ Search Gen2 — post-cutover quality and operations plan

Status: **Q1, O0 and O1 are complete. Q2 is active. D1 has not started.**

Owner: post-cutover quality, bounded operational hardening, and eval-first second-domain delivery.
The [technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) remains the owner of current
implemented architecture. This plan owns only the selected work below; source and focused tests become
the authority as each item is implemented.

## 1. Current source-confirmed remaining work

The native Gen2 route, module cutover, evaluation foundation, supplement startup policy, operator
status, request budgets, bounded Qdrant work, and managed restart evidence are complete.

- Q2 remains active pending exact-revision evidence and fresh protected acceptance; rotation-6 source/replenishment/freeze is complete.
- D1 has not started and still requires second-domain product input.
- successful Qdrant generations are intentionally not deleted automatically.
- Deferred capabilities remain outside approved current scope; see
  [Technical Specification accepted limits](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#accepted-limits).

## 2. Selected decisions

1. **Restart-driven refresh.** A process uses one immutable source materialization. Database changes
   become searchable after a coordinated restart; no hot reconciler or CDC is introduced.
2. **Coordinated deployment.** The initial supported topology is one process or a coordinated
   stop/start. A future multi-replica rollout must use an explicit blue/green drain contract before it
   may switch generations independently.
3. **Explicit supplement startup policy.** `SupplementStartupPolicy` is closed over `required`,
   `preferred`, and `disabled`. Supplement unavailability may produce `baseline_only` serving only
   under `preferred`; `required` is the default and `disabled` is the operator kill switch. The
   distinct `ServingMode` is closed over `full_search` and `baseline_only`. Readiness never promotes
   itself after startup; recovery or a policy change requires restart.
4. **Fail-closed hard activation.** Incompatible identity, malformed embedding output, and corrupted
   persisted state remain hard failures. No compensating alias mutation is added without a
   cross-backend coordination protocol.
5. **One corpus owner.** The canonical BeautyQ corpus moves to
   `beautyq-search-gen2-eval/src/main/resources`; the root and legacy app-shell copies disappear.
6. **Selective Gen1 eval migration.** Preserve the corpus, labels, useful scorer/runner semantics, and
   real-backend evidence patterns. Do not restore M9–M21 planning-as-code, static evidence façades, or
   obsolete route/activation policy.
7. **Thin reusable eval kernel.** A domain-neutral `search-gen2-eval` project may own only stable case
   identity, deterministic run mechanics, ranked metric mathematics, comparison, and report encoding.
   Domain corpus schemas, request construction, labels, slices, thresholds, and gates remain in each
   domain's eval project.
8. **Hybrid label model with explicit completeness.** Each case declares `JudgmentMode.Exhaustive` or
   `JudgmentMode.Partial`, hard acceptable/forbidden evidence, and optional graded relevance gain.
   Unknown results are non-relevant only under exhaustive judgment; partial judgment reports them as
   unjudged. Hard violations never disappear inside an average relevance score.
9. **Layered metrics with domain-owned cutoffs.** Each domain owns the ordered `K` values and gate
   applicability. The initial surface includes Success/MRR over declared acceptable evidence;
   Precision/Recall/NDCG only when judgment coverage makes the formula honest; forbidden hits,
   unjudged rate, zero/low results, duplicates, slice coverage, and append-only no-harm evidence.
10. **Tiered execution.** Pull requests run corpus decoding, validation, pure metrics, and a small
    managed-resource smoke. The complete corpus runs on demand, before release, and in a scheduled
    managed-resource job; it is not forced into every focused edit.
11. **Eval-first second domain.** A second domain starts with anchor queries and the simplest baseline.
    Every vertical capability slice extends and reruns evaluation; the domain is not implemented
    end-to-end before its first measurement.
12. **Public request budget.** One BeautyQ-owned `BeautyQSearchRequestBudget` declares bounded page
    size, query size, cursor transport size, and filter/facet/sort counts. Existing owners enforce the
    applicable limit: app-http owns raw body/string/cursor transport, BeautyQ public-input validation
    owns query and collection counts, and plan/domain validation owns page and semantic bounds.
    Generic types accept a declared maximum only where that boundary already belongs; no generic
    budget engine is introduced.
13. **Bounded Qdrant work.** Embedding and point upsert use configured bounded batch size and maximum
    in-flight work while preserving deterministic point IDs and order. No alias switches before all
    batches and the exact point count succeed; a failed batch leaves the generation inactive, and a
    restart idempotently repeats or reuses the same deterministic generation. Persistent embedding
    caches/checkpoints remain deferred until scale and model-identity requirements justify them.
14. **No automatic Qdrant deletion.** Exact operator-owned cleanup remains the initial policy and is
    documented. The runbook is dry-run by default, accepts only deterministic names with exact Gen2
    metadata, excludes the current alias target and building/in-progress collections, requires a
    minimum age, rereads the alias immediately before exact deletion, and never deletes by prefix
    alone. Without a lifecycle fence, cleanup runs only in a quiescent maintenance window in which all
    serving/startup processes are stopped or activation is administratively fenced until deletion
    completes. An offline command may be added when accumulation becomes material; automatic GC
    requires a fence/lease or equivalent coordination proof.
15. **Separate startup and request visibility.** Immutable `StartupServingStatus` owns snapshot,
    active generations, startup policy, serving mode, condition, startup reason and restart
    requirement. The existing orchestrator-owned request supplement outcome owns per-request
    `supplement_added`, `no_candidates`, or `supplement_failed` facts and exact request failure reason.
    Operator status derives only from startup status; each search response derives warnings from both
    owners without mutating either.
16. **Delete obsolete evidence.** After useful facts are migrated, stale M9–M14 resources and manual
    validation reports are deleted. Git history remains the archive.

## 3. Supplement startup and serving contract

The following delivery decisions are closed:

- `SupplementStartupPolicy` is closed over `required`, `preferred`, and `disabled`.
- `required` is the default.
- `disabled` must be effective before supplement resource construction; the disabled DI branch must
  reach baseline readiness without constructing, probing or activating Qdrant/embedding resources.
- Startup serving state is immutable for the process lifetime; no mode promotes itself after startup.
- Recovery or a policy change requires a coordinated restart; no automatic promotion exists.
- The initial supported topology is one process or a coordinated stop/start.

The technical specification owns implemented architecture, invariants, fail-closed rules, partial
cross-backend activation semantics, Kubernetes-facing probe interpretation and response-warning
matrices. Operations owns launcher commands, status inspection and recovery procedures.

See:
- [Technical Specification §13](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#13-baseline-plus-supplement-orchestration) for architecture and invariants;
- [Operations](BEAUTYQ_SEARCH_GEN2_OPERATIONS.md) for commands, status and recovery procedures.

## 4. Evaluation ownership

The reusable eval project owns mechanics only:

```text
stable case identity
-> deterministic run order
-> ranked observation
-> pure metric formulas
-> comparison and aggregation
-> deterministic report encoding
```

Each domain eval project owns:

```text
corpus and corpus version
-> strict domain decoder
-> request construction
-> expected/forbidden entities and optional relevance gains
-> language/intent/boundary slices
-> production-application adapter
-> accepted thresholds and stop conditions
```

The production serving graph has no eval dependency.

### Judgment and metric applicability

Each case explicitly declares:

```text
judgmentMode = exhaustive | partial
acceptable identities
forbidden identities
optional relevance gain per judged identity
```

Under `exhaustive`, an unlisted result is non-relevant and the acceptable set is complete for recall.
Under `partial`, an unlisted result is `unjudged`, never silently relevant or non-relevant. The report
always carries judged/unjudged coverage.

- forbidden-hit, duplicate, zero/low-result, structural and no-harm checks run wherever their required
  evidence exists;
- Success@K and MRR are computed against declared acceptable evidence when that set is non-empty and
  are named/interpreted as such;
- Recall@K requires exhaustive acceptable judgments;
- ordinary Precision@K requires exhaustive judgments; a partial explicit pool may report
  `JudgedPrecision@K` together with unjudged rate, but must not masquerade as full precision;
- ordinary NDCG@K requires graded exhaustive judgments for the relevant evaluation universe and an
  explicitly defined ideal ranking; a closed judged pool may report only `PooledNDCG@K`, together
  with the pool fingerprint and coverage, and complete judgment of returned top-K alone is
  insufficient for ordinary NDCG;
- every `K`, minimum judged coverage and gate threshold is domain-owned policy, never selected inside
  the generic metric kernel.

Raw rankings are preserved for duplicate diagnostics. Any duplicate public stable result identity
fails the structural acceptance gate. Relevance metrics deduplicate by first occurrence only to provide
diagnostic evidence for that invalid run; they cannot satisfy a release gate. Result identity is
domain-owned public hit identity, which for BeautyQ is the variant identity.

### Historical BeautyQ Q1 migration

Q1 moved the then-89-query visible corpus into the BeautyQ eval project after strict decoding and
validation:

- non-empty unique stable query IDs and explicit active order;
- query text, optional location and public request inputs;
- references to the exact materialized snapshot;
- acceptable and forbidden result identities;
- optional relevance gains;
- language, query-class and boundary slices;
- corpus version and deterministic content fingerprint.

Q1 generated corpus counts and validation reports from the decoded owner and retired superseded
generated reports and corpus locations. The cutover probes remain as a small smoke subset derived
from the canonical corpus or explicitly identified as synthetic operational probes; they are not the
complete quality corpus.

### Generated reports and accepted baselines

The canonical corpus and typed domain evaluation policy are the only operator-approved quality inputs.
Protected input authoring, recovery, replenishment and freeze are owned by Q2-I below.
Development/regression per-query reports and protected-holdout aggregate/slice verdicts live under
`target/search-gen2/` and CI artifacts; the standard protected artifact does not encode case
identities, and no report is copied back into source.

If release comparison needs a checked-in accepted baseline, it is one compact typed manifest, not a
second full report. It contains corpus and snapshot/data fingerprints, metric-schema and evaluation
policy versions, application revision, embedding provider/model/revision/dimension identity, report
digest, and only the approved aggregate/slice observations needed for comparison. Thresholds remain
owned by the typed domain policy rather than being redefined by the manifest.

The manifest is generated from a real report through
`BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun`, strictly decoded through the generic
accepted-baseline codec, and loaded from one canonical BeautyQ eval resource. A separate verifier
compares ordered aggregate observations and rejects when stable corpus, policy, snapshot or model
provenance no longer matches. These run-specific audit fields are reported but are not equality
requirements: application revision, Elasticsearch generation reference, Qdrant generation ID,
visible report digest, protected report digest and top-level manifest report digest. Updating labels necessarily changes the
corpus fingerprint and requires an explicit label-review diff. No manually edited count/report may
become a second authority.

### What happens when the first honest metrics are red

The first Gen2 run establishes evidence; it is not made green by changing labels or thresholds after
seeing output.

1. Validate that labels still describe the current BeautyQ seed and Gen2 public result units.
2. Separate corpus/schema errors from search errors.
3. Treat hard-constraint, ownership, malformed-result, and append-only no-harm failures as release
   blockers. Restart with `SupplementStartupPolicy.Disabled` if the supplement is implicated.
4. Development and regression failures report exact query IDs and slices, then classify the owning
   seam: corpus label, projection, vocabulary/intent, Elasticsearch policy, Qdrant eligibility, or
   append policy. Correct only that executable owner.
5. Protected-holdout execution reports aggregate/slice verdicts by default, not case identities. A
   failing holdout blocks acceptance; ordinary tuning does not inspect its cases.
6. Revealing protected cases requires an explicit break-glass label review. Every revealed case moves
   permanently into the regression set. Independently authored, unused, operator-approved cases may
   replenish the protected holdout only after canonical catalog identity validation and a reproducible
   freeze audit. A reserve discovered invalid before holdout reuse is rejected and follows the protected
   input recovery contract above.
7. Rerun development/regression evidence and then the replenished protected holdout without changing
   its labels or thresholds in response to output.
8. Run the manual bootstrap from the exact explicit immutable application-source identity. Preserve the
   aggregate-only candidate and stop for separate coordinator/operator review; do not promote it in
   the same delegated task, check in the full report, or hand-copy its counts.
9. Only after explicit approval, promote the reviewed candidate byte-for-byte to the one canonical
   eval resource and run manual verify as a separate phase. Require strict canonical-resource
   decoding, no failed stable verification checks, and compatible ordered aggregate observations,
   identities, scopes, and counts. Metric deltas classified as informational by the verifier do not
   independently fail verification.

Poor relevance does not by itself prove that the Gen2 architecture is wrong. It proves that a specific
business policy or retrieval configuration needs measured correction. Hard semantic or no-harm
violations do block continued rollout.

### Q2-I — Protected Input Authoring and Freeze

Q2-I is the pre-execution owner for the first protected inputs. A source-grounded author pass
creates unused requests without seeing the visible corpus, intent aliases or search output. A separate
judge pass assigns partial typed judgments from the canonical catalog without search output. Only the
later audit pass compares against visible regression evidence and the intent vocabulary, rejects exact
and NFKC/lowercase/whitespace-normalized query leakage, validates typed result identities and required
slices, then freezes the corpus and policy with an aggregate-only audit record.

The checkout operator is the approval authority; no external employee or separately hired reviewer is
required. “Synthetic acceptance data” means labels or thresholds derived from search output. It does
not include source-grounded model-assisted cases authored before execution and independently audited
against typed source/catalog evidence. Bootstrap and verify remain read-only consumers: they may never
author, relabel or modify protected inputs. Revealed protected cases still move permanently into the
visible regression set and must be replaced by independently authored, unused, operator-approved
cases before the holdout is reused.

Tracked protected inputs live under
`beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/`, with
author and judged drafts under the adjacent `provenance/` directory. A valid freeze binds the source
revision, typed corpus/policy and canonical catalog fingerprints, exact input hashes, separated pass
identities, ordered slice counts, catalog-identity validation and query-disjointness checks.
Transient break-glass and freeze evidence paths are owned by BEAUTYQ_SEARCH_GEN2_OPERATIONS.md.

“Protected” is an evaluation-process classification, not a confidentiality or storage classification.
The fixture may not be relabeled or tuned after protected output is inspected. A case deliberately
exposed for diagnosis moves permanently to the visible regression corpus. If its pre-disclosure
replacement reserve later fails structural or catalog validation, the reserve is rejected and an
explicitly authorized recovery pass must rebuild the author, judge and audit chain without access to
protected queries or search output.

Rotation-5 completed a deterministic source-side freeze of its protected inputs. Rotation-6 has
now permanently migrated its authorized disclosed exact-intent cases, replenished the protected
input set from a fresh catalog-bound reserve, and completed deterministic audit/freeze. The
resulting inputs must not be used for protected acceptance until this cumulative patch is committed
and exact-revision root evidence is obtained.

### Q2-A — Gen1→Gen2 Migration Scope-Drift Audit

Status: **COMPLETED**

Q2-A is a coordinator-owned historical audit record, not an executable runtime owner. It covers the
Gen1 dependency, removal and canonical-owner scope of its recorded commit window; it does not certify
later protected-input authoring, replenishment or freeze validity. The canonical accepted-baseline
resource remains absent until manual promotion.

### Q2-B — Protected bootstrap and candidate review

Status: **RECOVERY ROTATION 6 SOURCE AND FREEZE COMPLETE; FRESH PROTECTED ACCEPTANCE REQUIRED**

Fresh protected acceptance produced a RED that triggered bounded rotation-6
recovery. Rotation-6 authorization and authorized disclosure are complete;
the complete disclosed exact-intent slice is now visible Regression evidence.
The shared typed semantic correction, catalog-bound replacement reserve,
replenishment and deterministic freeze are complete in the resulting recovery source.
No fresh protected acceptance has run against this resulting source; bootstrap,
candidate generation, promotion and verify have not run. Q2 is not complete.

Exact revision, failed-check code, corpus count, hashes and generated artifact
facts belong to the authorization and evidence owners.

Candidate review — after recovery source is committed and exact-revision root
evidence is obtained, after a fresh protected acceptance produces GREEN, and
after bootstrap produces a candidate — the coordinator/operator reviews the
candidate's application revision, schema and policy versions, corpus and policy
fingerprints, gate pass/fail codes, provenance IDs, ordered aggregate observation
keys and counts, candidate digest, and absence of protected identity fields.
Queries, case/result identities, judgments, and metric values remain private.

### Q2-C — Explicit promotion, verify, and Q2 closeout

Status: **PENDING ON Q2-B APPROVAL**

Only explicit coordinator/operator approval of the Q2-B candidate starts Q2-C. Phase 1 records the
explicit immutable application-source identity. Promotion copies the preserved candidate byte-for-byte
into the one canonical eval resource, so the worktree then necessarily contains that reviewed tracked
resource. No search, evaluation-policy, lifecycle, route or corpus source may change between review
and verify. Q2-C verifies digest/equality, runs the focused canonical-resource proofs, and performs an
independent real verify with the same application-revision identity and verified tracked canonical
inputs. Q2 documentation may close only after green verify. Candidate generation does not authorize promotion,
and no automated promotion service is introduced.

## 5. Eval-first second-domain workflow

Before its first backend-rich implementation, a second domain must provide:

1. 10–20 independently authored, operator-approved anchor queries across its important slices;
2. hard acceptable/forbidden evidence and optional graded relevance;
3. a development set, a protected holdout excluded from ordinary policy tuning and changed only by an
   explicit label-review procedure, and a permanent bug-regression set;
4. the simplest executable baseline and its first report;
5. one vertical capability at a time, with corpus and report updates in the same change;
6. domain-owned thresholds only after observed baseline evidence exists.

The second domain is the first real second consumer of `search-gen2-eval`. If it needs different business
labels, that vocabulary stays domain-owned; the generic project changes only for truly shared
mathematical/run mechanics.

Detailed query-level reports are available for development/regression sets. Protected-holdout reports
are aggregate/slice-only unless the break-glass migration-and-replenishment procedure above is used.

## 6. Delivery sequence

### Delivery milestones and Git evidence boundaries

The five named patches in this plan — Q1, O0, Q2, O1 and D1 — are cohesive delivery milestones, not
a promise that each milestone maps to exactly one Git commit. Q1, O0 and O1 are complete; the remaining
approved product scope is Q2 and D1.

Remaining approved boundaries:

- Q2 bounded recovery source/replenishment/freeze (when required by protected RED);
- fresh protected acceptance (operator execution, not a Git boundary);
- candidate bootstrap/review (operator/generated evidence);
- Q2 candidate promotion;
- Q2 verify closeout;
- D1 product/evaluation contract;
- D1 backend-rich source;
- D1 acceptance closeout;
- conditional neutral generic-kernel extension, only after a source-confirmed
  reusable gap.

Operational executions and root evidence are not themselves Git commits. Evidence produced for a given
source identity must not be attributed to an amended or otherwise changed source revision.

This accounting does not expand the approved scope. Deferred capabilities remain outside approved
current scope; see [Technical Specification accepted limits](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#accepted-limits).

### Patch Q1 — executable evaluation foundation **COMPLETED**

Completed:
- added the thin domain-neutral eval project and neutral mathematical proofs;
- moved and strictly decoded the BeautyQ corpus;
- removed corpus duplicates, the stale validation report, and obsolete M9–M14 artifacts after migration;
- implemented explicit exhaustive/partial judgment semantics, domain-owned cutoffs, metric applicability, and pure metric formulas;
- defined generated report ownership, ordered scope/cutoff aggregates, protected aggregate-only encoding,
  strict canonical corpus/fingerprint handling, and the optional typed accepted-baseline manifest;
- updated module firewall/build-DAG proofs;
- corrected the technical specification's current quality claims.

The canonical visible corpus contains regression evidence, not a protected holdout.

### Patch O0 — supplement startup policy and safe kill switch **O0 COMPLETED**

- add closed `SupplementStartupPolicy.Required|Preferred|Disabled`, defaulting to `Required`;
- derive separate `ServingMode.FullSearch|BaselineOnly`;
- make the disabled DI branch reach baseline readiness without constructing, probing or activating
  Qdrant/embedding resources, while retaining hard ES/snapshot/integrity dependencies;
- preserve one immutable startup status and the existing per-request supplement outcome as separate
  owners;
- expose structured startup logging, operator status, public serving mode and safe response warnings;
- keep permitted baseline serving Kubernetes-ready and hard required-mode failure not-ready;
- require restart for recovery or policy change; never auto-promote after dependency recovery;
- prove the partial ES/Qdrant activation outcome and document exact alias inspection/retry procedure.

Disabled is selected before provisioning and its retained managed graph does not contain Qdrant or embedding resources. Executable retained-plan proofs cover Required, Preferred and Disabled, including exclusion of the managed Qdrant container from the Disabled graph.

### Patch Q2 — measured BeautyQ Gen2 report and correction gate **ACTIVE**

- execute the complete corpus through native Gen2 application/projector owners;
- produce deterministic per-query reports for development/regression and aggregate/slice-only
  protected-holdout verdicts;
- run one deterministic full-corpus warmup pass followed by three measured full-corpus passes at
  concurrency one; report application-execution p50/p95 over the real ES/Qdrant/embedding path,
  excluding process startup/materialization, and report backend stages only where typed observations
  already exist;
- record warmup/measured counts, concurrency, cold-versus-steady boundary, corpus/snapshot
  fingerprints, CPU/OS/JDK identity, backend versions and embedding model identity. Latency is
  initially report-only, and cross-environment regression gates are forbidden until a measurement
  protocol and domain thresholds are separately approved;
- retain the four-query real-resource smoke separately;
- keep development/regression details visible, keep protected-holdout output aggregate/slice-only,
  and generate the first accepted manifest only after protected acceptance;
- use `BeautyQAcceptedBaselineMain` only in manual `bootstrap` or `verify` mode; the canonical
  manifest is eval-owned, aggregate-only and never overwritten by a runner;
- stop for a bounded corrective patch if hard/no-harm gates fail;
- do not tune labels or thresholds to make existing output green.

Execution outcomes, corpus sizes, revision bindings and generated artifact hashes are recorded in
Q2 evidence reports rather than this plan; exact revision, failed-check code, counts, hashes and
run artifacts belong to the authorization and evidence owners.
The latest completed bounded recovery has satisfied the catalog-bound recovery and deterministic freeze/audit contract.
Protected acceptance remains behind operator-owned root evidence for an explicit immutable
application-source identity; bootstrap remains behind a green protected gate.

### Patch O1 — remaining bounded operational hardening

Status: **completed**.

- add BeautyQ request-budget values and enforce each at its existing HTTP/input/plan boundary;
- batch Qdrant embedding/upsert with bounded in-flight work, deterministic order, exact-count
  completion, inactive-on-failure semantics and idempotent restart;
- record startup materialization/activation duration, snapshot capture time, source
  revision/fingerprint, active generation time and generation age; under restart-only refresh this is
  freshness evidence, not CDC lag;
- document dry-run-first exact-metadata Qdrant cleanup inside a quiescent, activation-fenced
  maintenance window and the restart-only refresh/runbook;
- prove changed snapshot plus restart activates both backend generations.

The cleanup procedure is owned by the canonical
[`BEAUTYQ_SEARCH_GEN2_OPERATIONS.md`](BEAUTYQ_SEARCH_GEN2_OPERATIONS.md) runbook. It is a fenced,
two-observation dry-run protocol; no automatic GC or unpersisted creation-time assumption exists.

A global Qdrant threshold must not be introduced merely to hide overlapping measured score ranges.
Intent-policy corrections remain domain-owned, while generic ranking and supplement contracts remain
unchanged. Protected acceptance machinery provides strict corpus/policy decoding, shared
visible/protected execution, aggregate-only report encoding, a typed protected gate and a
candidate-baseline adapter. Q2 closeout requires green protected acceptance, candidate promotion and verify
(see Q2-B above for current state). Accepted-manifest generation remains behind a
green protected gate.

The protected runner and bootstrap do not create or modify those inputs; missing or unverified inputs
remain an operational failure at those later stages, not a requirement for an external employee.

### Patch D1 — eval-first second-domain vertical

This begins only after the product identity and source topology of the second domain are supplied. It
starts with its corpus and simplest baseline, not with a copied BeautyQ module tree. Every subsequent
domain slice carries its quality evidence.

Hot reconciliation, CDC, automatic readiness promotion, multi-process generation coordination,
persistent embedding caches, and automatic Qdrant GC remain outside approved current scope; see
[Technical Specification accepted limits](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#accepted-limits).
