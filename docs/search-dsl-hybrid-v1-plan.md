# Search DSL Hybrid V1 Plan

## 1. Goal

This plan records a non-production hybrid-search foundation, not a production hybrid rollout.
The reached non-production manual real-resource runner remains manual/test/local only, not a combined Qdrant+ES+Llama production module.
For the broader ES + Qdrant + hybrid retrieval end-state roadmap, see `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`.

Hybrid V1 should combine Elasticsearch lexical precision with Qdrant semantic recall only at explicit non-production boundaries.

## 1.5. B-lite Strategic Direction

B-lite remains the current strategic direction after the B2 hidden control-plane module proof, but the current-state status now lives elsewhere: use `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` for accepted production truth, `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md` for the future-serving phase ladder, and `docs/local/M8_M9_TELEMETRY_AND_OFFLINE_EVAL_PLAN.md` for M8/M9 pure slices plus the planned backend-runner seam.

What matters in this plan:

* production serving remains unchanged while hybrid work stays offline/eval-only;
* runtime hybrid module expansion is paused;
* simulated hybrid remains benchmark/eval only;
* any later hybrid eval execution must flow through the planned offline M9 backend-runner seam before any serving change is considered.

Guardrails:

* Pause runtime hybrid module expansion until ES/Qdrant eval comparison is improved.
* ES and Qdrant may advance in eval/benchmark together, but production serving remains sequential.
* No Qdrant auto-supplement / HybridServe from benchmark alone.
* Simulated hybrid belongs to offline benchmark/eval, not route/runtime behavior.
* InMemorySearchBackend is now a rollback/regression/pure backend. It is not an in-memory Elasticsearch and must not be treated as ES scoring/order/analyzer oracle.
* Elasticsearch should be designed from ES primitives: mappings, analyzers, bool/filter/range/geo queries, aggregations/facets, scoring/boosting.
* Qdrant should be designed from Qdrant primitives: embedding text, model identity, dimension, distance, topK, scoreThreshold, payload filters, missing lookup handling, semantic complement/noise.
* Product response projection adapts engine-native results into `BeautySearchResponse`; engines must not be forced to mimic the current in-memory backend.

It should not replace the Elasticsearch V1 path. Elasticsearch is the intended lexical retrieval baseline for text search, structured filters, facets, and exact/range/geo constraints. Product response assembly remains a projection layer over engine-native results.
That lexical baseline remains separate and injected as the existing lexical backend.

Qdrant should add recall for the narrow semantic gap already proven by the current Qdrant-only eval slice:

- `q_broad_001`
- `q_broad_002`
- `q_broad_003`
- `q_broad_004`
- `q_broad_005`
- `q_broad_006`

Qdrant must not become responsible for canonical facets, exact filters, price and duration constraints, or final production ranking in V1.

The architecture should stay tapir-like and spec-driven:

- pure search contracts and routing metadata first
- backend interpreters second
- domain examples in eval/spec data
- no hidden BeautyQ-specific query branches inside Elasticsearch or Qdrant interpreters

The first hybrid path should therefore be an explicitly measured extension of the existing search DSL architecture, not an ad-hoc fallback bolted onto production search.

## 2. Current Baseline

The baseline is now short:

* ES remains the default production lexical route.
* Qdrant remains a separate semantic candidate path.
* Hybrid work remains non-production and eval-only.
* The generic hybrid retrieval seam is a container/diagnostic boundary only; it does not imply fallback, score fusion, reranking, or route switching.
* The real-resource runner and smokes remain manual/test/local only.

The Qdrant semantic quality gate remains intentionally environment-gated:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true
```

This gate proves semantic candidate quality for the two broad queries. It does not by itself justify changing production routing.

## 3. Backend Responsibilities

Elasticsearch owns deterministic lexical search behavior:

- exact service constraints
- exact attribute constraints
- enum filters
- boolean filters
- numeric filters
- price filters
- duration filters
- geo constraints where they are currently ES-owned
- canonical facets
- deterministic lexical ranking
- standard three-carousel response assembly when lexical intent is clear

Qdrant owns semantic recall behavior:

- broad and conversational discovery
- semantic candidate variant ids
- candidate ids that can be hydrated into BeautyQ domain projections
- discovery cases where the lexical dictionary should not be expanded

Qdrant must not own production-critical deterministic behavior in V1:

- canonical facets
- exact attribute filtering
- price filtering
- duration filtering
- hard-negative disambiguation
- final production ranking

The practical rule is: Qdrant can propose candidates, but ES/spec-driven domain assembly remains the contract owner for deterministic search behavior.

## 4. Query Routing Signals

Hybrid V1 should route by explicit, inspectable parser/spec signals rather than backend-local query text branches.

### ES-only

Queries should stay ES-only when they contain clear lexical or structured intent:

- explicit service intent
- explicit attribute or filter constraints
- price constraints
- duration constraints
- body-area constraints
- known dictionary or synonym matches
- hard-negative or noise queries already covered by ES eval
- parser output with strong explicit constraints

This protects the current 61-query lexical baseline from being diluted by semantic fallback.

### Qdrant Candidate Route for Broad Semantic Queries

Queries may be eligible for the Qdrant candidate route only when lexical intent is weak and semantic discovery is the measured goal:

- no explicit service constraints
- no explicit attribute constraints
- no exact filter constraints
- broad beauty or discovery language
- similarity to the broad eval examples `q_broad_001` through `q_broad_006` for coverage only
- high residual text may be useful as diagnostic/eval context, but it is not a production routing trigger

Residual text alone must never route a query to Qdrant.

The first implementation should not make Qdrant a generic "unknown query" default. The current benchmark subset expansion is still non-production benchmark/eval infrastructure, not production routing evidence.

The query ids `q_broad_001` through `q_broad_006` may appear in eval data, tests, benchmark tooling, and documentation. They must not be hardcoded in production routing logic.

Benchmark decision policy is a manual evaluation aid, not runtime model switching. This tiny subset expansion does not make the benchmark mature enough for automatic model choice.

### Fallback

Fallback-on-zero-results is forbidden in the current design.

Future fallback, if considered, must be designed separately after the BeautyQ projection/merge policy is pure-tested.
It must be more conservative than the broad semantic Qdrant candidate route and must not be smuggled into the first policy patch.

Fallback remains blocked when the query is explicitly constrained or known to be a hard negative:

- no fallback for zero ES hits
- no fallback for explicit service constraints
- no fallback for exact attribute/filter constraints
- no fallback for price or duration filters
- no fallback for hard-negative/noise queries already covered by ES
- no fallback just because a query has residual text

Residual text alone must never route a query to Qdrant. Residual text can be used for diagnostics or eval context only, not as a production routing trigger.

## 5. Minimal V1 Behavior

The safest first implementation should keep the existing ES path unchanged.

Hybrid V1 should add a separate experimental service or method rather than replacing `BeautySearchService`.

The first behavior should be:

- keep current production ES behavior unchanged
- route only the eval-covered broad-query shape to the Qdrant candidate route, using explicit metadata from a future safe source rather than eval ids or residual text
- return Qdrant candidate ids instead of final ranked production results
- assemble domain documents into the existing three-carousel shapes where possible
- avoid score fusion
- avoid reranking
- avoid changing Elasticsearch interpreters
- avoid changing `BeautySearchSpecV1` dictionary data to force broad query matching
- avoid production rollout until eval gates are green

This creates a measured experimental path without weakening the existing lexical baseline.

## 6. Candidate Combination Strategy

### Option A: Qdrant-only semantic response for broad queries

Qdrant handles the narrow broad-query class and returns semantic candidates without combining with ES hits.

Benefits:

- smallest behavior change
- easiest to evaluate
- avoids score normalization problems
- avoids hiding ES regressions

Costs:

- response facets may be limited or candidate-derived
- behavior is useful only for broad discovery queries
- not a general hybrid strategy

### Option B: Qdrant candidate ids assembled through existing response assembler

Qdrant returns candidate ids. Existing domain document loading and response assembly build the same carousel shapes where possible.

Benefits:

- preserves current response contract shape
- keeps Qdrant as recall-only
- reuses existing domain assembly logic
- avoids Qdrant becoming the owner of facets or filtering

Costs:

- requires careful definition of candidate ordering
- may need explicit handling when candidate ids do not fill all carousel types
- candidate-derived facets must not be presented as canonical ES facets

### Option C: ES plus Qdrant union with deterministic ordering

ES hits and Qdrant candidates are unioned, deduplicated, and ordered by a deterministic rule.

Benefits:

- can preserve ES precision while adding semantic recall
- prepares for later measured hybrid behavior

Costs:

- introduces ranking and ordering questions early
- risks weakening lexical queries if applied too broadly
- can hide ES zero-hit regressions if used as automatic fallback

### Option D: Later measured score fusion or reranking

ES scores and Qdrant scores are fused, or a separate reranker orders combined candidates.

Benefits:

- may improve final ordering after candidate quality is proven

Costs:

- requires score calibration or a reranking model
- increases explainability risk
- creates a larger production behavior change
- is premature before V1 candidate routing is measured

### Recommendation

V1 should use Option B for the experimental path: Qdrant candidate ids assembled through existing domain response assembly.

This is safer than raw Qdrant-only responses because it preserves the existing response shape where possible. It is safer than ES plus Qdrant union because it avoids early score fusion, ranking changes, and fallback behavior that could hide ES regressions.

Option A can remain a local diagnostic mode. Option C should wait until broad-query candidate assembly is green. Option D should wait until separate score-fusion or reranking design and eval exist.

## 7. Facets and Inferred Filters

Elasticsearch remains canonical for facets.

Qdrant-only broad results may later expose limited metadata derived from returned candidates, but those values are not equivalent to ES index-wide aggregations.

If candidate-derived facets or inferred filters are added later, they should be explicitly marked as candidate-derived:

- they describe only the returned semantic candidate set
- they must not be presented as canonical result-space facets
- they must not replace ES aggregations for structured lexical queries

Qdrant should not be asked to compute exact filters or canonical facet counts in Hybrid V1.

## 8. BeautyQ Hybrid Projection/Merge Policy

This section defines the BeautyQ-specific policy boundary.
The pure policy model now exists as `BeautyQHybridProjectionPolicy.lexicalFirstSemanticSupplement`.
The pure hydrated variant projection adapter now exists as `BeautyQHybridVariantProjection.project`.
The pure variant-only `BeautySearchResponse` adapter now exists as `BeautyQHybridResponseAdapter.variantOnlyResponse`.
The pure provider/service projection policy model now exists as `BeautyQHybridProviderServiceProjection.project`.
The pure provider/service response carousel adapter now exists as
`BeautyQHybridResponseAdapter.responseWithProviderServiceCarousels`.
The pure response pipeline adapter now exists as `BeautyQHybridResponsePipeline.projectResponse`.
The non-production BeautyQ hybrid response experiment runner now exists as
`BeautyQNonProductionHybridResponseExperiment`.
The disabled-by-default non-production activation/factory skeleton now exists as
`BeautyQNonProductionHybridExperimentActivation`.

It does not implement production hybrid.
It does not change production `BeautySearchService`.
It does not define HTTP/API metadata.
It composes pure retrieval projection into `BeautySearchResponse`.
The experiment runner is fake-testable/manual-library boundary only, not production hybrid.
It runs injected lexical and semantic document backends plus document lookup and then calls the pure pipeline.

### Non-production activation and wiring boundary

`BeautyQNonProductionHybridResponseExperiment` exists, but it is not app wiring.
It is a library/manual/test/local boundary for explicit experiments.
`BeautyQNonProductionHybridExperimentActivation` exists as a disabled-by-default, construction-safe activation/factory boundary.
It builds this non-production experiment runner only when explicitly enabled.
It is not app wiring, not production hybrid, and does not replace production `BeautySearchService`.
Production `BeautySearchService` remains unchanged.

Current shape:

```scala
final class BeautyQNonProductionHybridResponseExperiment[F[+_, +_]: Error2](
  lexicalBackend: LexicalDocumentBackend[F, MasterServiceOfferVariantId],
  semanticBackend: SemanticDocumentBackend[F, MasterServiceOfferVariantId],
  documentLookup: SemanticDocumentLookup[F, MasterServiceOfferVariantId, VariantSearchDocument],
) {
  def search(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): F[QueryFailure, BeautyQNonProductionHybridResponseExperimentResult]
}
```

Construction-safe thunk/factory boundary status:

- implemented in `BeautyQNonProductionHybridExperimentActivation.buildIfEnabled`
- dependency parameters are by-name
- `Disabled` does not evaluate lexical, semantic, Qdrant, lookup, or experiment dependency thunks
- `Enabled` evaluates each dependency factory once when building the runner
- building the runner does not call backend or lookup methods
- fake-only tests also prove an explicit test module/composition boundary:
  disabled module selection binds only an optional empty runner/root and does not include or build fake semantic/Qdrant/lookup resources;
  enabled module selection explicitly includes fake lexical, semantic, and lookup resources and builds the experiment runner
- these tests use no real Qdrant/ES/Llama resources

Important limitation:

- the thunk/by-name factory is construction-safe only when callers pass unevaluated constructors/thunks
- the current module proof is fake-only and test-level
- this is not final Distage/module resource gating for real resources
- real non-production Distage/module work must still ensure disabled mode does not include or build the real Qdrant/semantic resource graph at all
- the experiment remains manual/test/local only
- production lifecycle, routing, and metadata remain absent by design
- returning `None` after real resources were already constructed would be insufficient
- real-resource adapter v0 must preserve the disabled-graph invariant: disabled mode must not include or build Qdrant, embedding/Llama, semantic backend, lookup, indexing, or experiment resources
It is not the final resource-gating mechanism.

Activation decisions:

- activation is explicit and disabled by default
- enabled activation builds only `BeautyQNonProductionHybridResponseExperiment`
- allowed invocation modes are manual task, test setup, and local experiment only
- activation must not be inferred from `Mode.Test` alone
- activation must not be enabled by `Mode.Prod`
- future module work must remain explicit non-production/test-only and disabled by default
- acceptable future wiring shapes are explicit config/env/manual test parameter, explicit local runner flag, explicit test-only experiment axis, explicit manual/admin task boundary, or explicit non-production invocation object/module with named activation
- unacceptable shapes are implicit production default, silent `Mode.Test` behavior, HTTP request flag without separate API design, and residual-text-based automatic semantic routing

Wiring decisions:

- future wiring may bind the runner only behind a named non-production boundary
- the lexical side remains separate and injected as the existing Elasticsearch baseline backend
- the semantic side of future v0 is the only real-resource target: Qdrant backend, embedding/Llama client, document lookup, and readiness/compatibility guard
- wiring must use injected lexical backend, semantic backend, and document lookup
- the activation skeleton does not add Distage wiring, `LeaderboardPlugin`, `BeautySearchService`, HTTP/API, production routing, collection lifecycle, or startup indexing
- wiring must not create Qdrant collections
- wiring must not index snapshots on startup
- production search must not depend on Qdrant availability
- production `BeautySearchService` must not change

Routing and metadata decisions:

- the runner takes already parsed `ParsedSearchIntent`
- the runner does not own parser behavior
- the runner does not own production routing
- routing remains explicit-invocation-only
- the runner does not introduce HTTP/API metadata fields
- the runner does not implement fallback-on-zero-results
- the runner does not implement residual-text routing
- the runner must not be wired into production search

Lifecycle and response decisions:

- Qdrant collection readiness and snapshot indexing remain explicit setup steps
- future v0 may read/check an existing non-production collection and verify compatibility expectation
- future v0 must fail fast on collection identity/version, vector size/distance, embedding/vector config, or collection compatibility mismatch
- future v0 must not silently fallback to Elasticsearch, create collections silently, or switch model automatically
- collection create/delete/recreate remains outside production app lifecycle
- collection setup remains explicit manual setup, not module startup
- alias/blue-green lifecycle and production collection manager are not implemented
- no startup indexing
- no indexing as a side effect of module inclusion or runner resolution
- no indexing when activation is `Disabled`
- guarded snapshot indexing may only be explicit manual/local/test invocation
- the runner uses the pure `BeautyQHybridResponsePipeline`
- explicit hybrid carousel limits exist through `BeautyQHybridResponseCarouselLimits`
- variant, provider, and service carousels are explicitly limited after policy/projection order is established
- the non-production experiment currently derives `BeautyQHybridResponseCarouselLimits` from `BeautySearchSpecV1.spec` and `UserSearchInput`: variants use `min(input.limit, spec.carouselSpec.variantSize)` with negative values normalized to empty output, providers use `spec.carouselSpec.providerSize`, and service intents use `spec.carouselSpec.serviceIntentSize`
- this remains acceptable for manual/local v0
- future production design must still decide whether `BeautySearchSpecV1.spec` remains hardcoded in the experiment or becomes injected/configured/versioned per route/parser contract
- limits make the pure pipeline closer to the response contract, but the pipeline remains response-shaped and non-production rather than production-ready
- no-fusion, no-reranking, no-fallback, and no-routing semantics remain unchanged
- truncation happens after policy/projection order is established, with no score sorting before truncation
- provider/service `bestScore` values remain display-only where produced by the pure adapter
- provider `sampleMatchingVariantIds` remains capped at 3
- facets and inferred filters remain ES/parser-owned

Before any future code wiring, require normal `sbt test`, max env full test when llama/Qdrant gates are available, focused fake-only experiment runner tests, and docs review confirming production guardrails.

The construction-safe activation factory with thunked dependencies is implemented.
The tiny synthetic second-domain proof for the generic seams is now implemented at test level.
The non-production real-resource Qdrant/hybrid manual runner milestone is reached.
Production hybrid remains out of scope.

### Output Shape

The final BeautyQ hybrid output remains `BeautySearchResponse`.

The variant carousel is the primary merge surface:

- lexical ES hits and semantic Qdrant hits are combined by `MasterServiceOfferVariantId`
- ids appearing in both channels are represented once
- channel diagnostics and channel-local scores may be retained for inspection
- ES scores and Qdrant scores are not directly comparable
- score fusion is not defined or implemented here

The hydrated variant projection adapter is not full response projection:

- it hydrates only variant candidates from `VariantSearchDocument`
- it preserves separate lexical and semantic scores
- it does not fuse scores
- it fails clearly when a policy candidate has no matching document
- it does not build `BeautySearchResponse`, provider carousel, service carousel, facets, or inferred filters

The variant-only response adapter is not full response parity:

- it builds only `variantCarousel`
- it does not build provider or service carousels
- it does not produce facets or inferred filters
- it uses `LexicalThenSemantic` only as a display score policy for `VariantSearchResult.score`
- the display score is not a fused score, not a ranking score, and is not used to reorder candidates
- it does not expose separate lexical or semantic scores in the production response shape

The provider/service projection policy is also not full response parity:

- it creates intermediate provider and service-intent candidates only
- it groups provider candidates by `masterLocationId`
- it groups service-intent candidates by `serviceId`
- group order follows first candidate occurrence in policy order
- `representativeDisplayScore` is display-only under `LexicalThenSemantic`, not ranking or fusion
- it does not build provider or service response carousels

The pure provider/service response carousel adapter is still not production hybrid:

- it builds provider and service carousels from intermediate projection candidates
- it preserves provider and service carousel order from projection order
- it maps `representativeDisplayScore` into `ProviderSearchResult.bestScore` and `ServiceIntentSearchResult.bestScore`
- those `bestScore` values are display-only, not fusion, not ranking, not reranking, and not score-calibrated ES/Qdrant comparison
- it limits provider sample matching variant ids to the existing response convention of 3 ids
- it does not sort provider or service carousels by `bestScore`
- it does not provide score calibration
- it does not call Elasticsearch or Qdrant
- it does not route, fallback, or wire production services

Provider and service carousels remain BeautyQ-specific projections over hydrated variant, provider, and service data.
They are not raw Qdrant outputs.

Facets and inferred filters remain ES/parser-owned:

- Qdrant does not produce facets
- Qdrant does not produce inferred filters
- Qdrant does not own authoritative filter semantics
- Qdrant-only experimental responses may use empty facets and empty inferred filters

### Merge Boundary

Merge happens at the domain candidate/document id level, then projects into BeautyQ carousels.

Merge must not happen:

- at raw Qdrant point level
- inside the Qdrant backend
- inside the Elasticsearch query interpreter
- as production route switching

`HybridDocumentRetrievalResult[MasterServiceOfferVariantId]` is input to the BeautyQ policy.
It is a container and diagnostic boundary only, not the policy itself.

A tiny synthetic second-domain test proof now exists in `HybridGenericSecondDomainProofSpec`.
It uses a fake `ArticleId` domain to show that the generic retrieval container and generic Qdrant indexing seam can be reused at test/proof level outside BeautyQ.
This is not a second production domain implementation, and it does not mean a production-generic hybrid engine is complete.
BeautyQ-specific projection, carousel, routing, lifecycle, and metadata work remain separate future work.

### Channel Responsibilities

Elasticsearch is the intended lexical retrieval baseline for:

- filters
- facets
- exact lexical behavior
- canonical structured constraints
- normal response assembly for lexical paths

Qdrant remains semantic recall only:

- it proposes semantic variant candidates
- it does not replace ES filters or facets
- it does not own inferred filters
- its scores are channel-local diagnostics

### Overlap and Ordering

Implemented policy:

- `lexicalFirstSemanticSupplement` emits lexical candidates first
- semantic-only candidates append after lexical candidates
- overlap collapses to one candidate by `MasterServiceOfferVariantId`
- duplicate lexical ids use the first lexical hit
- duplicate semantic ids use the first semantic hit
- lexical and semantic scores are preserved separately
- candidate sources record lexical, semantic, or both
- no score fusion, reranking, fallback, query routing, provider/service carousel production, facets, or inferred filters are implemented

### Forbidden In This Design

- fallback-on-zero-results
- residual-text semantic routing
- production route switch
- score fusion/reranking
- replacing ES facets/filters
- Qdrant-as-default
- HTTP/API metadata contract
- startup indexing
- production collection lifecycle

### Experiment Output Expectations

An acceptable non-production experiment may produce variant candidates from semantic recall.

Provider and service carousels must remain domain projections over hydrated variant, provider, and service data.

Empty facets and empty inferred filters are acceptable for a Qdrant-only experimental path.

Production response parity is not required yet.

## 9. Eval Gates Before Production

Production rollout should require these gates before any hybrid or fallback path becomes user-facing:

- ES-only regression suite remains green at `61/63`.
- Qdrant-only semantic candidate quality assertions remain green.
- Hybrid/fallback tests cover the broad eval examples first, including `q_broad_004` and `q_broad_006`.
- Lexical queries do not regress.
- Hard-negative queries do not regress.
- Explicitly constrained queries do not route to Qdrant fallback.
- Broad semantic queries are not fixed by `BeautySearchSpecV1` dictionary hacks.
- Qdrant fallback does not hide ES regressions.

The Qdrant quality gate should remain explicit and environment-gated:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true
```

Hybrid tests should prove routing behavior as well as result quality. A broad query passing through Qdrant is not enough if lexical or hard-negative queries also start routing to Qdrant unintentionally.

## 10. Implementation Sequence

Future implementation should be split into small patches:

1. Done: add pure BeautyQ hybrid projection/merge policy model/tests only.
2. Done: add pure BeautyQ provider/service projection policy model/tests only.
3. Done: add pure provider/service response carousel adapter behavior, still without production wiring.
4. Done: docs/design non-production resource-gating boundary.
5. Done: construction-safe hybrid experiment activation factory.
6. Done: test(search) tiny synthetic second-domain proof for generic seams.
7. Done: fake-only explicit non-production Distage/test module gating proof.
8. Done: benchmark subset expansion with more explicit eval query ids beyond `q_broad_004` and `q_broad_006`.
9. Done: docs record real-resource non-production hybrid adapter boundary.
10. Done: non-production real-resource Qdrant/hybrid manual runner milestone reached (manual adapter/handle, env-gated indexing and retrieval smokes, Distage module-shape proofs).
11. Done: production-hybrid control-plane v0 — value/decision types, readiness/diagnostics interfaces, `BeautySearchHybridDecisionEvaluator[F]` with conservative policy (`SeedCatalogOnly` default, `HybridShadow` diagnostics-only, `HybridServe` requires `Ready`, `NotReady` → `UseSeedCatalogOnly`); covered by `BeautySearchHybridControlPlaneSpec`; not production wiring.
12. Current: B-lite — pure `EngineEval` comparison model implemented (`EngineEval.scala`, `EngineEvalSpec.scala`); M-ESQ-EVAL started, not complete; EngineEval pure/report/assembly support exists, Qdrant benchmark run-output JSON markers exist, manual EngineEval saved-report assembly exists, manual EngineEval saved-report comparison exists; M9 saved report rendering and static/in-memory runner skeleton exist for caller-supplied inputs only; current next step is operational saved-output/runbook/evidence workflow: run/collect concrete ES + selected Qdrant artifacts, assemble EngineEval aggregate reports, compare saved reports, and use evidence for later shadow/hybrid decisions; still offline/eval only; runtime hybrid remains paused.
13. M-ESQ-EVAL: ES-native eval, Qdrant-native eval, simulated hybrid (offline only), overlap/complement/noise metrics.
14. Later design: lifecycle/freshness/observability.
15. Much later design: production routing/API/metadata, and only later any production lifecycle/ranking decisions.
16. Later: larger benchmark taxonomy expansion covering hard negatives, near-miss semantic queries, noisy/typo cases, multilingual cases, and broader second-domain eval cases when available.

The second-domain proof is done.
The fake-only explicit module gating proof is done.
The non-production real-resource Qdrant/hybrid manual runner is done.
Any next implementation patch should keep the same boundary: pure or explicitly non-production only, and no new generic abstractions unless a concrete gap appears.

It must not include:

- backend calls
- `BeautySearchService` changes
- `QdrantClient` calls
- production routing changes
- Elasticsearch interpreter changes
- Qdrant retrieval changes
- Distage wiring
- HTTP/API changes
- ranking changes
- score fusion
- reranking
- fallback
- production routing change
- real Qdrant resource construction in disabled mode

Any future real-resource adapter patch must report:

- how `Enabled` is explicitly selected
- why the disabled graph does not include/build Qdrant/Llama resources
- where readiness/compatibility is checked
- whether collection creation is impossible or explicit-only
- whether snapshot indexing is impossible or explicit-only
- why no startup indexing occurs
- why no `BeautySearchService` path changed
- why no `LeaderboardPlugin` production include changed
- why benchmark decisions cannot affect runtime model choice
- what timeout/freshness/observability gaps remain

## 11. Generic and Domain Reuse Implications

Hybrid routing should be described in DSL/spec terms where possible.

Reusable concepts should be generic:

- lexical confidence
- explicit constraints
- residual text
- semantic candidate eligibility
- hard-negative classification
- backend routing decision
- fallback eligibility

Domain-specific broad-query examples belong in domain eval/spec data.

For BeautyQ, `q_broad_001` through `q_broad_006` are examples of the broad semantic benchmark/eval class. They should guide eval coverage, benchmark examples, and documentation examples, not become hardcoded backend behavior.

Backend interpreters should stay generic:

- Elasticsearch interpreters consume lexical/filter/facet specs.
- Qdrant interpreters consume vector/search specs.
- Hybrid routing consumes parser/spec output and future explicit routing metadata.
- Hybrid retrieval containers stay separate from domain-specific merge policy.

A second domain should be able to reuse:

- ES lexical backend
- Qdrant semantic backend
- routing concepts
- eval workflow
- hybrid retrieval containers and diagnostics

The second domain should provide its own:

- flattened search document
- domain spec
- dictionary
- semantic embedding text spec
- eval dataset
- routing eval cases
- projection/merge policy

This keeps the search DSL direction generic while allowing BeautyQ to remain the first measured domain.

## 12. Risks and Anti-patterns

The main risks are caused by using semantic fallback too broadly.

Avoid these anti-patterns:

- broad fallback hiding ES regressions
- Qdrant providing canonical facets
- Qdrant bypassing explicit filters
- expanding `BeautySearchSpecV1` dictionary to force broad semantic queries
- introducing runtime LLM generation
- routing hard-negative queries to Qdrant just because they have residual text
- making Qdrant the default for all unknown queries without eval evidence
- implementing ES/Qdrant hybrid before this design is reviewed
- changing Elasticsearch interpreters to accommodate Qdrant
- adding score fusion before candidate routing is measured
- adding reranking before candidate routing is measured

Hybrid V1 should stay narrow: prove the two known broad semantic gaps, preserve the ES baseline, and keep the backend interpreters mechanical.

## 13. Experimental Routing Metadata Note

`ExperimentalHybridRouteDecider` takes an injected metadata provider because the current codebase has no production-safe source of `SearchRoutingMetadata`.
At present, metadata is only supplied from tests and eval scaffolding, and `BeautySearchBackend.search(input, intent)` does not carry routing metadata through the production path.

That is intentional. `BeautySearchService.Impl` must stay parser plus backend delegation, and the user-facing search input does not yet have an explicit experiment or routing field.

Current state is still experimental and test-scoped. The existing pieces are useful seams, but they are not a production rollout path:

- `QdrantCandidateHitDecoder`
- `SemanticCandidateHit` / `SemanticCandidateBackend`
- `QdrantSearchClient` / `QdrantPointUpsertClient` ports
- `QdrantSemanticCandidateSearch`
- `QdrantSemanticCandidateBackend`
- `QdrantVariantDocumentPointBuilder`
- `QdrantVariantDocumentIndexer`
- `QdrantSemanticCandidateEvalSpec` reuses shared eval helpers
- an env-gated explicit experimental service integration spec exists
- the explicit metadata route is still experimental and test-only

These pieces are not production-ready yet because the runtime lifecycle and operational contracts are still missing:

- timeout budget
- retry policy
- a production-safe `SearchRoutingMetadata` source
- runtime `VariantSearchDocument` snapshot/freshness lifecycle
- Qdrant freshness relative to Elasticsearch
- a production `VariantSearchDocumentLookup`
- a production Qdrant collection lifecycle
- a collection versioning and vector-dimension compatibility policy
- a production indexing lifecycle with batching, retry, and backpressure
- route-decision observability and diagnostics
- observability/metrics/tracing
- defined fallback semantics
- kill switch semantics
- rollback behavior
- score calibration, score fusion, or reranking policy
- Elasticsearch facet/filter parity for any hybrid user-facing path
- a rollout strategy

Until those gaps are closed, the following remain explicitly forbidden:

- Qdrant-as-default
- residual-text routing
- eval query ids in main code
- fallback-on-zero-results
- `BeautySearchService` replacement
- production Distage wiring
- `LeaderboardPlugin` production path changes
- HTTP/API metadata surface
- runtime model switch from benchmark decisions
- startup auto-indexing
- alias/blue-green lifecycle
- Elasticsearch facet replacement by the Qdrant path

Safe future metadata sources are limited to:

- explicit experiment metadata
- a future API or config source that does not derive from eval query ids
- a future parser signal, but only if it is separately proven safe and stable

Unsafe metadata sources are:

- residual text alone
- hardcoded `q_broad_004` / `q_broad_006` ids
- Qdrant-as-default
- fallback-on-zero-results
- broad dictionary hacks in `BeautySearchSpecV1`

Current implementation ladder:

1. `SearchBackendRouter`
2. `SemanticCandidateBackend`
3. `VariantSearchDocumentLookup`
4. `QdrantCandidateAssembler`
5. `QdrantCandidateResponseProjector`
6. `ExperimentalHybridSearchBackend`
7. `ExperimentalHybridRouteDecider`
8. Pure `BeautyQHybridProjectionPolicy`
9. Pure `BeautyQHybridVariantProjection`
10. Pure `BeautyQHybridResponseAdapter` variant-only response projection
11. Pure `BeautyQHybridProviderServiceProjection` intermediate provider/service candidates
12. Pure `BeautyQHybridResponseAdapter.responseWithProviderServiceCarousels` provider/service response carousel projection
13. Pure `BeautyQHybridResponsePipeline.projectResponse` retrieval-to-response composition
14. Non-production `BeautyQNonProductionHybridResponseExperiment` injected-backend response experiment runner
15. Disabled-by-default, construction-safe `BeautyQNonProductionHybridExperimentActivation` activation/factory boundary
16. Fake-only explicit non-production Distage/test module gating proof

What is still missing before runtime hybrid:

- a real explicit metadata source
- a production-safe provider for `SearchRoutingMetadata`
- a disabled-by-default provider that keeps routing on `ElasticsearchOnly` unless explicitly enabled
- production hybrid design (lifecycle, routing, freshness, observability, kill-switch, collection management)

The recommended later step may be benchmark subset expansion.
Production hybrid remains later and needs separate design before any production resources are included.
Keep the provider absent until a real explicit metadata source exists. When one is added, it should default to `ElasticsearchOnly` and require explicit opt-in to route anything else.
