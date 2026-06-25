# BeautyQ Current State and Handoff

Canonical handoff for new chats. Read this first, then follow links for deeper detail. Other docs should point here instead of restating this status block.

## 1. Final current state

* Production `POST /beauty-search` is ES-backed by default, exposed by `LeaderboardPlugin` through `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`. Backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`. `seedCatalogInMemory` / `InMemorySearchBackend` remain rollback/non-default.
* `BeautySearchServingGate.disabled` is the production/default gate. Runtime behavior:

  | Gate state | Valid request | Invalid request |
  |---|---|---|
  | `disabled` (default) | `200 OK`, existing ES-backed behavior | `400` before the gate |
  | `enabledNotReady` | `503` | `400` before the gate |
  | `enabledReady` | `200 OK`, existing ES-backed behavior | `400` before the gate |

* Explicit local/dev/test selector surface, which does not change the production default: `BeautySearchPluginModules.apiWithServingGate[F](servingGate)`, `BeautySearchRouteModules.seedCatalogElasticsearchWithServingGate(servingGate)`.
* Executable evidence ids in `BeautySearchProductionRouteExposureSpec` (table-driven; supersedes the former manual smoke checklist): `gate_disabled_valid_request` -> 200 OK, `gate_enabled_not_ready_valid_request` -> 503, `gate_enabled_ready_valid_request` -> 200 OK, `gate_enabled_not_ready_invalid_request` -> 400.
* `ElasticsearchOperatorVisibilityEndpointPolicySpec` has zero pending tests.
* Qdrant explicit opt-in (`BeautySearchRouteModules.apiQdrantExplicitOptIn`) is implemented but disabled by default and lives outside the default `apiElasticsearch` graph. Qdrant production activation is **not approved**.
* For local operator preflight/smoke checks on the no-worsening Qdrant supplement activation selector, see `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_ACTIVATION_RUNBOOK.md`.
* Not approved / not enabled, by design: default route switch, production fallback, runtime serving fallback by default, hybrid serving, score fusion, reranking execution, production telemetry, and any production-readiness / route-activation / serving-approval claim.
* **Accepted final checkpoint (M17A):** `sbt test` ran `1693` tests, `1693` succeeded, `0` failed, `0` aborted, `1` canceled, `0` pending — all tests passed. Every earlier full-suite snapshot is historical and superseded by this checkpoint.
* **M17 scope correction:** M17 closed the gate/foundation/evidence track described above — default ES-backed `/beauty-search`, `BeautySearchServingGate.disabled` as the default gate, the local/dev/test selector surface, executable gate evidence, and the 1693/1693 full-suite checkpoint. It did **not** close the original user goal of a generic search DSL with configurable ES+Qdrant ingest, ES-native retrieval, Qdrant-native retrieval, metrics for combining them, and one maximally accurate combined response. That goal remains open; see the corrected roadmap (M18–M21) in `docs/codebase-review/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`, including its "M18–M21 Acceptance Gates" section — docs-only, schema-only, and full-suite-green-alone work cannot close any of M18, M19, M20, or M21.
* **M21 verdict: defer.** No default route switch is approved. No runtime hybrid serving proof exists. The next blocker is **AP1**: a default route/module proof against a real Elasticsearch cluster with non-empty results and latency evidence. M18–M21 milestone labels must not be treated as "hybrid done" — M18 produced real ES and Qdrant candidate evidence, M19 produced offline metrics and component-level policy-as-data, M20 closed as a disabled controlled surface only, and M21 deferred pending AP1 clearance.
* All formerly-pending test expectations (`BeautySearchOptInRouteModuleSpec`, `BeautySearchProductionRouteExposureSpec`, `ElasticsearchOperatorVisibilityEndpointPolicySpec`) are now active tests. The per-expectation owner/approval map lives in `docs/codebase-review/06-tests-and-contracts.md`.
* The single canceled test is expected manual/resource-gated coverage, not a blocker.
* There is no real production traffic in this project context; shadow serving and traffic mirroring are future-only and non-blocking for readiness.

## 2. How we got here

| Milestone | What it established |
|---|---|
| M5 | ES startup-readiness lifecycle checkpoint closed; ES seed route preserved as the default. Replacement/freshness/rollback and full lifecycle operations remain separate, unstarted future tracks. |
| M6/M7 | Qdrant production-candidate readiness and activation/source-confirmation planning closed as pure offline foundations — no serving, no route, but the foundation the opt-in route is built on. |
| M8/M9 | Telemetry-schema and offline-eval-harness contracts (shared vocabulary, saved-report format, backend-runner interface, gated smoke-evidence scaffolding) closed as pure/offline planning — no telemetry emission, no real backend execution. |
| M10–M12 | Query classification, candidate-generation, and fusion/reranking eval work closed as offline planning/reporting only — established that combined retrieval and reranking were planning-only, never production serving. |
| M13 | Controlled opt-in route/module planning closed; preserved the ES default and explicitly denied production activation. |
| M14 | Implemented the disabled-by-default `BeautySearchServingGate` runtime route gate and the local/dev-only fallback policy (`BeautySearchLocalDevOnlyFallbackPolicy`). |
| M16 | Added the explicit serving-gate selector surface (`apiWithServingGate` / `seedCatalogElasticsearchWithServingGate`) and the executable evidence harness, superseding the former manual smoke checklist. |
| M17 | Recorded the final accepted full-suite checkpoint (see section 1). |

The explicit opt-in Qdrant route additionally requires an enabled config gate, approved no-regression evidence, observability/rollback evidence, and separate route/serving approval before assembly. See `docs/codebase-review/QDRANT_PRODUCTION_ACTIVATION_DECISION_CRITERIA.md` for the stricter, still-unsatisfied production-activation criteria, and `docs/codebase-review/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md` for the full end-state roadmap. The roadmap's hybrid-production phases that used to sit at M14–M17 were renumbered to M18–M21 to remove the milestone-number collision with the M14/M16 gate-and-evidence track above; the two tracks were previously sharing numbers by coincidence, not relationship.

## 3. Current production `/beauty-search`

```text
LeaderboardPlugin
→ modules.apiBase[IO] for base/core APIs
→ BeautySearchRouteModules.apiElasticsearch
→ BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured
→ ElasticsearchClientModules.portConfigured
→ BeautySearchRouteModules.seedCatalogElasticsearch
→ BeautySearchCatalogBackendModules.seedResourceElasticsearch
→ BeautyQSeedLoader.ResourceLoader
→ BeautySearchReadyCatalogDocuments
→ ElasticsearchSeedIndexInitializer
→ ElasticsearchSearchBackend
→ BeautySearchService.Impl
→ BeautySearchApi
```

* Backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`: lexical/simple/catalog-first, not Qdrant, not hybrid, not fresh/repository-backed production catalog lifecycle. `ElasticsearchPortCfg` is loaded from config section `"elasticsearch"`.
* `modules.api[F]` remains rollback/legacy composition (`apiBase[F]` + `seedCatalogInMemory[F]`); `seedCatalogInMemory` remains available as rollback/non-default.
* Full lifecycle operations (runtime rebuild/refresh, replacement activation, rollback, disable/kill-switch, operator-triggered lifecycle commands) remain entirely future-only; none exist today. `ElasticsearchSeedLifecycleMetadata` / `ElasticsearchProductionReadinessState` are non-serving DI/readiness seams, not a lifecycle implementation — they record serving readiness `NotEnforced`, replacement `NotConfigured`, freshness `NotTracked`, refresh `EagerSeedPreparationOnly`, rollback `NotConfigured`, and operator visibility `NotExposed`.
* Operator-visible lifecycle status (`GET /ops/beauty-search/lifecycle`) exists only through the explicit opt-in/internal module `BeautySearchRouteModules.apiElasticsearchWithOperatorVisibility`; it is absent from the default graph and from `seedCatalogInMemory`.
* Any future default `/beauty-search` switch requires its own explicit production-route activation approval, default graph exposure plan, rollback/disable plan, observability/status evidence, route exposure tests, and full-suite verification — no hidden fallback, fusion, reranking, shadow serving, or mirroring is approved as part of that boundary.

## 4. Request contract characterization

Documented as characterized, not as a desired final contract:

* Public request contract `BeautySearchRequestContract`: `query` must be non-blank; `limit` must be within `1..MaxLimit` (`MinLimit = 1`, and `MaxLimit` is source-backed by `BeautySearchSpecV1.spec.carouselSpec.variantSize`); optional latitude/longitude must be within `MinLatitude..MaxLatitude` / `MinLongitude..MaxLongitude` and are independently optional.
* Empty/whitespace queries, non-positive/over-max limits, and out-of-range coordinates return structured JSON `400 BadRequest` with stable `code`/`message` fields before any service/Elasticsearch call.
* Malformed JSON / empty body / wrong field type / missing required fields use Tapir defaults and also return `400 BadRequest` before service/Elasticsearch calls.
* Backend/query failures return `500 InternalServerError` with an empty body.
* No maximum query length is enforced.
* Full request/response examples: `docs/codebase-review/04-api-and-http-contracts.md`. Regression checklist: `docs/codebase-review/06-tests-and-contracts.md`.

## 5. Search backend roles

* `ElasticsearchSearchBackend` is the current production lexical retrieval backend (seed-backed).
* `InMemorySearchBackend` is a rollback/regression/pure backend, not current production default, and not an ES scoring/order/analyzer oracle.
* Elasticsearch is the intended lexical baseline (text, structured filters, facets, exact/range/geo constraints). Qdrant is semantic recall/complement candidate only.
* Product response assembly is projection over engine-native results.

## 6. M-ESQ-EVAL

**M-ESQ-EVAL** = measured Elasticsearch-native + Qdrant-native evaluation comparison. Offline/eval-only; the production `/beauty-search` route remains the ES seed route regardless of eval outcomes.

* `roleDeltas:`, `queryDeltas:`, query-class classification/sidecars, and `classDeltas:` are part of the current expanded checkpoint; class-sidecar replay is validated for `benchmark-small -> benchmark-large`.
* Saved aggregate JSON schema, `EngineEvalAggregateReport`, and `EngineEvalReportJson` remain unchanged.
* No route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement follows from benchmark output — it is decision support, not production automation.
* Detailed contracts/evidence: `docs/codebase-review/06-tests-and-contracts.md`. Milestone positioning: `docs/codebase-review/07-current-gaps-and-roadmap.md`. Local operator procedure: `docs/local/M_ESQ_EVAL_EVIDENCE_RUN_TEMPLATE.md`.

## 7. Product search north star

Build useful free-string BeautyQ search that turns user text into domain-aware product results by combining:

* DSL/spec-owned domain semantics for fast adaptation to new domains;
* Elasticsearch strengths for lexical precision, filters, facets, exact/range/geo constraints, analyzers, aggregations, and deterministic baseline retrieval;
* Qdrant strengths for semantic recall/complement on broad, conversational, and vague queries;
* explicit eval/benchmark reporting before any production hybrid or routing decision.

### Forbidden paths

No production hybrid; no route switch from benchmark alone; no Qdrant auto-supplement; no `HybridServe` from benchmark alone; no Qdrant-as-default; no startup indexing; no fallback; no score fusion/reranking; no production collection lifecycle manager / alias / blue-green; no production kill-switch integration yet; no freshness/reindex production policy yet; no public response schema changes unless explicitly requested; no forcing ES/Qdrant to mimic `InMemorySearchBackend` (rollback backend only).

## BeautyQ Hybrid North Star

We are **not** building hybrid search by faith.

* Elasticsearch remains the reliable primary path.
* Qdrant may contribute **only** when runtime evidence shows useful variant recall improvement without unacceptable noise.
* Evidence must stay close to the runtime path: real ES, real Qdrant, canonical expected ids where canonical queries are used, overlap, complement, noise, lookup status, and latency.
* No full-collection recall-floor artifacts.
* No response policy unless measured gates preserve useful complement while controlling hard-negative and ambiguous noise.
* No new report/projection layer unless it replaces or deletes an older one.
* The canonical runtime evidence owner is `RuntimeEsQdrantScorecardProofSpec.scala`.

**Current status after L2:** policy remains blocked. L2 found a measurement-promising but not policy-ready tradeoff — `scoreThreshold 0.50` preserves useful semantic complement and silences hard-negative noise but does not reduce ambiguous noise, while `0.65`/`0.75` silence ambiguous noise but drop the useful semantic complement. Next work must either tighten the measured gate or remove/consolidate redundant evidence layers; it must not select response policy on current evidence.

**Anti-noise rule:** A new report/projection layer is forbidden by default. It is allowed only when it becomes the single canonical owner for a decision surface or replaces/deletes an older layer.

### Reusable domain method

The BeautyQ-specific thresholds (e.g. `0.50`, `0.60`, `0.62`, `0.65`, `0.75`) are **not** the transferable asset. They were fit to BeautyQ's canonical eval set, embedding model, and source text, and must not be reused blindly in another domain.

The transferable asset is the operational method used to evaluate whether Qdrant can safely supplement an existing ES-primary search path:

1. Keep ES as the reliable primary path; never let an unproven supplement displace it.
2. Add Qdrant only as an additive supplement, never a replacement or fallback.
3. Prove structural no-harm (the supplement does not corrupt or destabilize the primary result set) before making any semantic claims.
4. Run the full canonical domain eval set, not a sample or a synthetic subset.
5. Measure appended acceptable ids versus appended unacceptable ids, not an aggregate score.
6. Classify the harm type for unacceptable appends, e.g. same-category wrong-attribute, wrong category, duplicate, cap issue, source-text issue, model issue, or parser/constraint issue.
7. Only then choose the next axis to adjust: constraints, query/document text redesign, embedding model comparison, or no policy. Do not adjust thresholds or caps as a first move when the harm classification points elsewhere — BeautyQ's Y0D/Y0E rounds showed that tightening append count and changing the source field did not fix the underlying candidate-source problem.

This method, not the BeautyQ threshold values, should transfer to future domains. This is anti-noise guidance: a future domain should start from this method rather than recreating an M18-M21 / Y0A-Y0E-style report chain from scratch.

This method does not guarantee a domain will reach a safe Qdrant supplement policy — it only structures the evidence needed to decide. For BeautyQ, following it left policy blocked.

### BeautyQ runtime supplement evidence ledger

This is a durable summary of the BeautyQ runtime Qdrant supplement tuning loop, not a new report layer. Exact verification counts and detailed per-query tables remain in the measurement specs/reports (Y0A, Y0C-Y0K), not here.

**Worked:**

* ES-first + Qdrant-additive seam can preserve structural response ownership.
* ES prefix/order and non-variant components stay preserved in measurements.
* Qdrant can be safely evaluated as a semantic candidate source without production enablement.

**Almost worked:**

* `explicit_constraints_filter_plus_top1` was the strongest runtime supplement gate found.
* It reduced harm to zero in the measured canonical run.
* It preserved `q_broad_006`.
* It lost `q_lashes_008`.
* Therefore it is measurement-useful but not Y1-ready.

**Did not work as a policy path:**

* threshold tuning;
* append cap/topK tightening;
* embedding source-field selection;
* larger embedding model size;
* document-side key-value semantic text variants;
* query-side constraint tags / remainingText tags / oracle query-type tags.

**Decision:**

* Y1 remains blocked.
* The BeautyQ runtime supplement tuning loop is stopped at Y0K.
* Do not keep trying thresholds, caps, source fields, model size, or simple text/tag variants without new source truth.
* Qdrant remains non-production/eval/semantic-candidate evidence.

**For future domains:**

* Transfer the method, not BeautyQ thresholds.
* Start with ES primary + Qdrant additive seam.
* Prove structural no-harm.
* Run the full canonical domain eval.
* Measure appended acceptable/unacceptable ids.
* Classify harm type.
* Try parser/intent constraints before tuning thresholds/caps.
* Stop early when recovery only appears under harmful gates.

## 8. Verification and testing protocol

* Use the Constructive test taxonomy: pure model/metric logic is `Contractual + Blackbox + Atomic`; in-process service/module seams are `Contractual + Blackbox + Group`; real ES/Qdrant/Llama/Docker/HTTP is `Communication`, resource-backed auto-gated/cancelable (unavailable resources cancel with reason).
* Full `sbt test` is user responsibility unless explicitly delegated.

## 9. Where to read next

* `AGENTS.md` — stable repo guardrails
* `docs/codebase-review/README.md` — architecture entrypoint
* `docs/codebase-review/05-search-and-retrieval-architecture.md` — search DSL, ES, Qdrant, hybrid, eval
* `docs/codebase-review/07-current-gaps-and-roadmap.md` — current gaps and roadmap
* `docs/codebase-review/INVENTORY.md` — active inventory
* `docs/search-dsl-hybrid-v1-plan.md` — hybrid V1 plan and B-lite strategy
* `docs/beautyq-search-dsl-v1.md` — search DSL V1 and eval coverage
* `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` — coordinator workflow, prompt packaging, source-truth gating, closeout, docs ownership, and model recommendation guidance

Future work should move in larger code/test chunks that unlock downstream behavior, and update docs inside the same delivery rather than as standalone docs-only cleanup unless the roadmap is inconsistent.
