# M21 Post-Defer Inventory (S)

Status read-only inventory taken at `DeferProductionActivation`. Classifies M18–M21
search/eval/control code, tests, and the route-proof gap **before** any further M20/M21
implementation. This document adds no serving behavior and approves no activation.

Source truth was sufficient: **no `NEED_BUNDLE`**. Every classification below is grounded in the
files listed in the task; nothing required browsing broader repo code.

---

## 1. Summary table — M18–M21 source files

| File | Classification | Unique source truth it owns | Tests | Clears an M21 blocker? | Safe to delete now? | Must replace before deletion |
|------|----------------|------------------------------|-------|------------------------|---------------------|------------------------------|
| `M18DualEngineOfflineEval.scala` | `goal_moving_evidence` | The only surface that actually **executes** real ES (`LexicalDocumentBackend`) and real Qdrant (`SemanticDocumentBackend`) retrieval over the same eval queries; per-backend candidate rows w/ native score + matched fields; per-leg latency measurement; Qdrant resource-gate w/ exact missing prerequisites; structural ES/Qdrant separation invariant. | `M18DualEngineOfflineEvalSpec`, `M18DualEngineOfflineEvalEsRealLegSpec`, `M18DualEngineOfflineEvalQdrantRealLegSpec` | **Partial.** Backs the `m18RealEsAndQdrantEvidenceExists` accepted-state fact and provides real-backend execution + latency. Does **not** clear `runtime_hybrid_execution_proof_absent` (offline/eval, not serving) nor `production_latency_failure_mode_evidence_absent` (offline, not production). | No | n/a (no replacement) |
| `M19IBeautyQComponentCombinationPolicyScaffold.scala` | `safety_guardrail` | `ComponentCombinationPolicy` honesty flags (`offlineEvalOnly`, `notServingPolicy`, `doesNotApproveHybrid`, `qdrantDoesNotOwnFacets`, `qdrantDoesNotOwnInferredFilters`) — the policy boundary M20A/M20C/M21 consume as evidence-only. | `M19IBeautyQComponentCombinationPolicyScaffoldSpec` | No (it *blocks*: keeps Qdrant out of serving/facets/inferred-filters). | No | n/a (consumed by M20/M21) |
| `M19CBeautyQResponseComponentTaxonomy.scala` | `reporting_or_projection` | Source-confirmed taxonomy of response components + eval-query coverage; BeautyQ eval JSON codecs. | `M19CBeautyQResponseComponentTaxonomySpec` | No | No | Coverage view consumers (M19D/H) must keep their own copy of any taxonomy they need |
| `M19DBeautyQCoverageGapModel.scala` | `reporting_or_projection` | Per-query → coverage-gap aggregation view. | `M19DBeautyQCoverageGapModelSpec` | No | No | Would absorb M19E proposal if consolidated |
| `M19EBeautyQEvalDatasetExpansionProposal.scala` | `roadmap_only` | Proposal text for expanding eval dataset coverage. | `M19EBeautyQEvalExpansionProposalSpec` | No | After consolidation | Fold the proposal into `M19DBeautyQCoverageGapModel` (it already lists the gaps) |
| `M19FBeautyQEvalComponentExpectationSchema.scala` | `reporting_or_projection` | Tolerant eval-query schema for carrying component-level expectations. | `M19FBeautyQEvalComponentExpectationSchemaSpec` | No | No | Dataset readers depend on the schema |
| `M19DualEngineOfflineEvalMetrics.scala` (M19A) | `reporting_or_projection` | Pure metrics (candidate sets, lookup counts, latency aggregates) over `M18DualEngineOfflineEvalResult`. | `M19DualEngineOfflineEvalMetricsSpec` | No (reshapes M18 evidence) | No | M19B/H reports read these metrics |
| `M19DualEngineOfflineEvalEvidenceReport.scala` (M19B) | `removed_V_cleanup` | Was a readable evidence-report artifact over M19A metrics (no live consumers — only its own spec referenced it). | (deleted) | No | **DELETED in V** | Superseded by `M19DualEngineOfflineEvalMetrics.scala` (canonical metrics), `M18DualEngineOfflineEval.scala` (real execution rows), and the runtime scorecard (`RuntimeEsQdrantScorecardProofSpec.scala`). Doc citations in `M19C` updated to point to `M19DualEngineOfflineEvalMetrics`. |
| `M19HBeautyQComponentCoverageEvidenceReport.scala` | `reporting_or_projection` (retained) | Coverage evidence over populated component expectations. **Active non-test consumer:** `M19IBeautyQComponentCombinationPolicyScaffold` reads `ComponentCoverageEvidence` directly in production source (`M19I:8,132,163`) and its spec. Deleting M19H would break the M19I scaffold, which is itself a safety guardrail on the protected list. | `M19HBeautyQComponentCoverageEvidenceReportSpec` | No | **No (live consumer)** | M19H is consumed by M19I; cannot be removed. The remaining overlap with M19C/M19D is real but removing it would orphan M19I. |
| `M20ControlledHybridServingSkeleton.scala` (M20A) | `safety_guardrail` | Disabled-by-default control surface; every dangerous flag (fallback/fusion/reranking/auto-Qdrant/route-switch) fixed `false` by smart constructors; `servingApproved`/`qdrantProductionActivationApproved` pinned `false`. | `M20ControlledHybridServingSkeletonSpec` | No (it *prevents* serving/activation) | No | n/a (wrapped by M20B/M20C/M21) |
| `M20BControlledHybridServingOperationalControl.scala` | `safety_guardrail` | Kill switch, source-confirmed rollback target, readiness-cannot-enable-serving invariant, honest `ModuleProofScope.InMemoryOnly`, operator status surface. | `M20BControlledHybridServingOperationalControlSpec` | No (prevents activation) | No | n/a (wrapped) |
| `M20CControlledHybridServingCloseout.scala` | `safety_guardrail` | Closeout invariant: closes M20 **only** as `ClosedAsDisabledControlledSurface`; refuses to close if proof scope is overstated to `FullProductionGraph`. | `M20CControlledHybridServingCloseoutSpec` | No (gate that refuses overstatement) | No | n/a (consumed by M21) |
| `M21ProductionActivationDecisionPackage.scala` | `safety_guardrail` (+ reporting) | The activation **verdict** + `ActivationGate` (all `false`) + the canonical ordered blocker list; `approvalAllowed` is unreachable while the wrapped closeout pins both approvals `false`. | `M21ProductionActivationDecisionPackageSpec` | No — it **is** the defer decision; it holds activation off and *records* the blockers rather than clearing any. | No | n/a (source of the blocker list everything else reads) |
| `M21PostDeferralActivationBlockerActionPlan.scala` | `roadmap_only` | Re-renders each M21 blocker as an ordered action-plan item (AP1–AP7) with risk/sequence. Adds **no** new source truth. | `M21PostDeferralActivationBlockerActionPlanSpec` | **No** — explicitly clears no blocker by itself (`noPlanItemApprovesActivation`, `noPlanItemChangesProductionBehavior` are constant `true`). | After consolidation | Its ordering is derivable from `M21ProductionActivationDecisionPackage.blockers`; fold the burn-down ordering there and delete this layer + spec |

---

## 2. Blocker mapping (current M21 `blockers`, in source order)

The verdict is `DeferProductionActivation`. `ActivationGate.currentlyNoneSatisfied` leaves all gates
`false`, so all seven blockers stand. Where each is owned / what would clear it:

| M21 blocker | Owned/encoded by | What clears it |
|-------------|------------------|----------------|
| `full_production_route_module_proof_or_accepted_replacement_absent` | M20B `ModuleProofScope` + M21 `routeProofScopeInMemoryOnly` | A full **production** ES route/module proof (real ES cluster, non-empty serving), or an explicitly accepted replacement. **See §4.** |
| `runtime_hybrid_execution_proof_absent` | M21 gate `runtimeHybridExecutionProof=false` | Runtime hybrid execution proven strictly **behind** the disabled controlled surface (no route switch). M18 is offline-only and does not satisfy this. |
| `operator_rollout_rollback_proof_insufficient_for_activation` | M20B records only an in-memory rollback target | Activation-grade rollout/rollback proof with kill switch retained. |
| `production_latency_failure_mode_evidence_absent` | M21 gate `productionLatencyFailureModeEvidence=false` | Measured production latency + failure-mode behaviour. (M18 measures only offline leg latency.) |
| `component_policy_gaps_remain_provider_service_facets_inferred_filters` | M19I policy (`NeedsMoreEvidence` provider/service; facets/inferred-filters current-owner only) | Offline/eval policy-as-data closing the provider/service/facet/inferred-filter gaps. Policy work only — not a route activation. |
| `serving_approval_still_absent` | M20A pins `servingApproved=false` (read through M20B/M20C/M21) | A separate, explicit reviewer serving-approval decision. |
| `qdrant_production_activation_approval_still_absent` | M20A pins `qdrantProductionActivationApproved=false` | A separate, explicit reviewer Qdrant production-activation decision. |

---

## 3. Test classification (M18–M21 + route proof)

**(a) Goal-moving — directly move toward production activation**
- `M18DualEngineOfflineEvalEsRealLegSpec` — real ES leg execution.
- `M18DualEngineOfflineEvalQdrantRealLegSpec` — real Qdrant leg execution.
- `BeautySearchProductionRouteExposureSpec` — proves the default `/beauty-search` route is ES-backed through the real `apiElasticsearch` production module graph + real http4s + real ES JSON client (against a stub ES — see §4). Strongest existing route proof.

**(b) Safety tests — assert nothing dangerous is enabled**
- `M20ControlledHybridServingSkeletonSpec`, `M20BControlledHybridServingOperationalControlSpec`, `M20CControlledHybridServingCloseoutSpec`.
- `M21ProductionActivationDecisionPackageSpec` (approval impossible; all dangerous flags false; route stays unchanged).
- `M19IBeautyQComponentCombinationPolicyScaffoldSpec` (Qdrant doesn't own facets/inferred-filters; not a serving policy).
- `BeautySearchOptInRouteModuleSpec` (Qdrant opt-in stays a separate non-default module; requires M6/M7 readiness + serving approval before route wiring; no shadow/mirror/production traffic).

**(c) Pure report/model tests**
- `M18DualEngineOfflineEvalSpec` (runner over stub backends).
- `M19DualEngineOfflineEvalMetricsSpec`, `M19CBeautyQResponseComponentTaxonomySpec`, `M19DBeautyQCoverageGapModelSpec`, `M19FBeautyQEvalComponentExpectationSchemaSpec`, `M19HBeautyQComponentCoverageEvidenceReportSpec`. (M19B spec deleted in V — see §5.)

**(d) Green but NOT goal-progress evidence**
- `M21PostDeferralActivationBlockerActionPlanSpec` — asserts roadmap shape/order only; clears no blocker. **Also contains an unsafe `.head` extraction** (`plan.items.head` via `ordered.head.*`, spec lines ~52–57) — the fragility flagged in the task's R draft.
- `M19EBeautyQEvalExpansionProposalSpec` — proposal text shape only.
- The M20*/M21 safety specs are green and necessary, but they assert *absence* of activation, not progress toward it.

---

## 4. Route-proof section — default `/beauty-search`

**What proof exists.** `BeautySearchProductionRouteExposureSpec` builds the **real production route
module** `BeautySearchRouteModules.apiElasticsearch` through a live distage `Injector().produce(...)`
graph (`buildProductionApiGraphRouteProbe`), then serves an actual `POST /beauty-search` request
through the real http4s `HttpApp` and the real Elasticsearch JSON client. It asserts:
- exactly one `BeautySearchApi` in the HttpApi set, ES-backed;
- the operator/lifecycle endpoint is **absent by default** and present only on explicit opt-in;
- seed-only, not-production lifecycle metadata + readiness state;
- the route stays ES-backed "until separate production-route activation is approved";
- (recording variant) the request actually hits an ES `_search` path — i.e. real client wiring, not a stub service.
Serving-gate behaviour (disabled / enabled-not-ready→503 / enabled-ready) is proven by the
`seedCatalogElasticsearchWithServingGate` evidence harness in the same spec.

**Is the proof in-memory-only or full production graph?** **Neither label is exact, and this is the
key finding.** The proof exercises the *real production module DI graph + real http4s routing + real
ES JSON client*, which is **stronger** than the `ModuleProofScope.InMemoryOnly` that M20B/M20C/M21
conservatively record. **But** the ES backend is an in-process `com.sun.net.httpserver` stub returning
`{"hits":{"hits":[]}}` (`withZeroHitEsServer`) — so it is **not** a full production graph against a
real ES cluster, proves only empty-result serving, and carries no latency/failure-mode evidence.

**Exact files/specs that prove it.**
- `bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchProductionRouteExposureSpec.scala`
- `bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchProductionRouteSpecSupport.scala` (`buildProductionApiGraphRouteProbe`, `withZeroHitEsServer`, `withRecordingZeroHitEsServer`)
- `bifunctor-tagless/src/main/scala/leaderboard/plugins/BeautySearchRouteModules.scala` (`apiElasticsearch` → `seedCatalogElasticsearch`)
- `bifunctor-tagless/src/main/scala/leaderboard/api/BeautySearchServingGate.scala` (disabled-by-default gate)
- `bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchOptInRouteModuleSpec.scala` (Qdrant opt-in stays a separate non-default module)

**Exact missing proof to clear the first M21 blocker (AP1,
`full_production_route_module_proof_or_accepted_replacement_absent`).** A route proof of the default
`apiElasticsearch` graph against a **real Elasticsearch** (e.g. docker/Testcontainers, as already
used by `*ElasticsearchIntegrationSpec`/`QdrantDockerSmokeSpec`) returning **non-empty** results —
proving real result serving (not the zero-hit stub) and capturing latency — **or** an explicitly
accepted replacement-evidence artifact that raises the recorded proof scope past `in_memory_only`
without overstating it. This same real-ES proof begins to clear AP4 (latency/failure-mode evidence).
It requires **no** change to production route modules and keeps the default route ES-backed.

---

## 5. Top deletion / consolidation candidates

1. **`M21PostDeferralActivationBlockerActionPlan.scala` + `M21PostDeferralActivationBlockerActionPlanSpec.scala`** (`roadmap_only`) — clears no blocker; pure restatement of `M21ProductionActivationDecisionPackage.blockers`; spec has the unsafe `.head`. *Replace:* fold burn-down ordering into the M21 decision package, then delete.
2. **`M19EBeautyQEvalDatasetExpansionProposal.scala`** (`roadmap_only`) — proposal already derivable from the M19D gap model. *Replace:* absorb into `M19DBeautyQCoverageGapModel`.
3. **`M19DualEngineOfflineEvalEvidenceReport.scala` (M19B)** ↔ **`M19HBeautyQComponentCoverageEvidenceReport.scala`** — overlapping offline-eval evidence reports. *Replace:* merge into one evidence report over M19A metrics + component coverage. **V status:** M19B **DELETED** (no live non-test consumer; only its own spec and stale string citations in `M19C` referenced it; M19C citations updated to point to `M19DualEngineOfflineEvalMetrics`). M19H **RETAINED** — it is actively consumed by `M19IBeautyQComponentCombinationPolicyScaffold` (a safety guardrail on the protected list), so it cannot be removed.
4. **`M19HBeautyQComponentCoverageEvidenceReport.scala`** — coverage evidence overlaps M19C taxonomy + M19D gap model. *Replace:* single coverage projection. **V status:** **RETAINED** for the reason in (3) — M19I reads `ComponentCoverageEvidence` in production source. Overlap with M19C/M19D accepted as a non-removable cost of keeping M19I intact.
5. **Nested report case classes `ActivationDecisionReport` (M21) vs `CloseoutReport` (M20C)** — restate the same safety-flag set at two layers. *Replace:* one shared safety-flag projection consumed by both.

> **V update (post-M21 inventory):** `M19DualEngineOfflineEvalEvidenceReport.scala` (M19B) and its spec were deleted in V — the M19A metrics (`M19DualEngineOfflineEvalMetrics.scala`) plus the runtime scorecard (`RuntimeEsQdrantScorecardProofSpec.scala`) now own the runtime evidence surface for offline-eval. M19H retained as a live M19I consumer. See §5 entry 3 for the M19H/M19I linkage.

---

## 6. Recommended next commit (after this inventory)

**Clear AP1, the first standing blocker — test/evidence only.** Add a real-Elasticsearch route proof
for the default `apiElasticsearch` `/beauty-search` graph (docker/Testcontainers pattern already in
the repo) that serves **non-empty** results and records latency, raising the route proof past the
zero-hit stub. This is the single change that materially advances production activation; further
roadmap/decision layers (e.g. another M21-style plan) do not clear any blocker.

Constraints for that commit: do **not** change production route modules, do **not** add
fallback/fusion/reranking/shadow/mirror/route-switch, keep the default route ES-backed, keep the M20
controlled surface disabled, and do **not** approve activation. It clears AP1 and seeds AP4
(latency/failure-mode) without touching the safety guardrails.
