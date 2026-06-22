# Architecture Decisions Observed

Only decisions supported by inspected code/docs/tests are listed here.

## Decision 1: Runtime Wiring Uses Distage Roles And Plugin Modules

Statement:

- Application startup is organized around Distage roles, launchers, and plugin modules.

Evidence:

- `LeaderboardRole.scala` defines role classes, role descriptors, launcher objects, and `MainBase`.
- `LeaderboardPlugin.scala` binds roles, APIs, repositories, seed resources, and configs.

Consequences:

- Runtime availability depends on Distage graph bindings and role selection.
- API slices are role-backed.

What not to infer:

- Source package existence alone does not mean a component is runtime-wired.

## Decision 2: Domain Model Uses Master And MasterLocation, Not First-Class Salon

Statement:

- Current code models providers as `Master` plus `MasterLocation`; no first-class `Salon` model is present.

Evidence:

- `model/package.scala` defines `Master` and `MasterLocation`.
- Targeted search found no main source `Salon` model/repository/API.
- Search projections use `masterId`, `masterName`, `masterLocationId`, and `locationName` in `BeautySearchModels.scala` and `VariantSearchDocument.scala`.

Consequences:

- Architecture docs should use current code names.
- Product “salon” language needs a mapping decision before introducing a new abstraction.

What not to infer:

- Do not assume `Salon` exists or is interchangeable with `MasterLocation` without a product/code decision.

## Decision 3: MasterServiceOfferVariant Is The Primary Buyable/Search Unit

Statement:

- Search and eval center on `MasterServiceOfferVariant`.

Evidence:

- `beautyq_search_eval_plan_v1.md` says the primary buyable unit is `MasterServiceOfferVariant`.
- `VariantSearchDocumentBuilder` builds `VariantSearchDocument` rows from `masterServiceOfferVariants`.
- `VariantSearchResult` carries `variantId`, offer/location/master/service/category ids, price, duration, attributes, score, and distance.

Consequences:

- Search result architecture starts from variants, then groups/projects provider and service-intent carousels.

What not to infer:

- This does not prove appointment availability or scheduling exists.

## Decision 4: Postgres Schema Lives In Repository Resource Constructors

Statement:

- Current schema creation is embedded in `repo.*.Postgres` resource constructors.

Evidence:

- Repository files contain `create table if not exists` statements.
- Pass 1 found no standalone `.sql` migration files under `bifunctor-tagless/src`.
- `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md` documents this as current project behavior.

Consequences:

- Resource graph dependencies preserve FK startup order.
- Repository constructor dependencies may be semantically necessary even when values are marked `@unused`.

What not to infer:

- Textual binding order in `ModuleDef` is not a sequencing guarantee.

## Decision 5: HTTP Contracts Are Protected By Route-Level Tests

Statement:

- Route-level HTTP contract tests are the source of truth for API behavior.

Evidence:

- `*ApiHttpContractSuite.scala` files exist for Beauty entities, ladder, and profile.
- The focused `*ApiHttpContractSuite.scala` suites pin default Tapir/http4s route behavior for each adapter.
- Existing focused tests and route-level HTTP contract tests are the strongest evidence for current observed API behavior.

Consequences:

- API migrations should start with or preserve contract tests.
- Docs should not override contract tests.

What not to infer:

- Tapir defaults are not automatically the project contract.

## Decision 6: Search Semantics Are Intended To Live In DSL/Spec Data

Statement:

- Search behavior is represented through DSL/spec metadata rather than backend-local BeautyQ branches.

Evidence:

- `docs/beautyq-search-dsl-v1.md` states `BeautySearchSpec` is the single source of truth.
- `SearchDsl.scala` defines fields, constraints, facets, ranking, request specs, and vector specs.
- `BeautySearchSpecV1.scala` defines canonical fields, dynamic attributes, dictionary/synonyms, and carousel config.
- Elasticsearch interpreters consume `BeautySearchSpec` and `SearchDocumentSpec`.

Consequences:

- Backend changes should be mechanical interpretations of spec data where possible.
- Dictionary fixes belong in `BeautySearchSpecV1`, not ES/Qdrant clients.

What not to infer:

- A spec-driven interpreter stack does not mean search is production-exposed.

## Decision 7: Qdrant And Hybrid Are Non-Production / Experimental

Statement:

- Qdrant and hybrid paths are implemented as non-production/manual/local/test/experimental boundaries, not production app behavior. Qdrant remains an active production-candidate target, but shadow-first is not required and real shadow serving / traffic mirroring are not active objectives.

Evidence:

- `docs/search-dsl-qdrant-vector-backend.md` says production hybrid is not implemented and Qdrant is not production lifecycle/routing/fallback.
- `QdrantProductionCandidateReadiness.scala` provides the closed pure M6 readiness state/report/policy foundation with conservative defaults and no route integration.
- `QdrantProductionCandidateIndexingReadiness.scala` adapts document-count, collection-identity, and embedding/vector evidence into indexing readiness without running Qdrant.
- `QdrantProductionCandidateSearchReadiness.scala` adapts semantic search, assembly, projection, and BeautySearch contract-parity evidence into search readiness without serving.
- `QdrantProductionCandidateQualityGate.scala` provides source-backed offline quality/parity evidence from `EngineEvalAggregateReport`, explicit thresholds, stable failure reasons, and readiness-status mapping.
- `QdrantProductionCandidateActivationPolicy.scala` provides explicit candidate activation approval, scope, control/evidence requirements, blocking reasons, and readiness-status mapping without route integration.
- `QdrantProductionCandidateObservabilityReadiness.scala` and `QdrantProductionCandidateRollbackReadiness.scala` provide pure source-backed report evidence, deterministic blocking reasons, and readiness-status mapping without traffic mirroring or serving approval.
- `QdrantProductionCandidateM6CloseoutSpec` plus the focused readiness, indexing/search, quality, activation, and controls specs prove every readiness category must be explicitly ready, all adapters compose, Qdrant must be active, and shadow/traffic-mirroring/serving behavior is not required.
- `docs/search-dsl-hybrid-v1-plan.md` says Hybrid V1 is a non-production foundation.
- Class names include `QdrantNonProductionExperiment*`, `BeautyQNonProductionHybrid*`, and `ExperimentalBeautySearchService`.
- `LeaderboardPlugin.scala` does not bind Qdrant/hybrid search services.

Consequences:

- Qdrant/hybrid docs must label current status carefully.
- The pure production-candidate report is a prerequisite model, not serving approval.
- Candidate-readiness activation approval is distinct from route/serving approval; production-route activation remains outside the implemented policy.
- Production rollout still needs broader accepted evidence, configured controls, explicit serving approval, routing, and lifecycle policy. The pure observability and rollback/disable reports do not approve serving.
- Future hybrid work is conditional on direct Qdrant production-candidate readiness and explicit serving policy approval.
- M6 is closed as the Qdrant production-candidate readiness foundation. The M7 activation planning/source-confirmation foundation is closed without serving implementation. M8 controlled hybrid serving remains future-only and conditional.
- `QdrantProductionCandidateActivationPlanning.scala` requires the closed M6 report, a `Ready` activation-policy report, and serving-scope config/no-regression/observability/rollback/approval prerequisites before a separate implementation decision can be considered.
- `QdrantProductionCandidateActivationConfigApproval.scala` keeps the M7 config gate disabled by default, separates no-regression evidence from its approval, and translates only complete approved evidence into the existing planning prerequisites. It does not load runtime config or approve serving.
- The source-confirmed future opt-in seam is a separate route module outside default `apiElasticsearch`; its pending contract consumes `QdrantProductionCandidateActivationConfigApproval` for the disabled-default config gate and separately approved no-regression evidence, plus the remaining M7 prerequisites and separate route/serving approval. `LeaderboardPlugin` continues to include only the ES route module. No Qdrant route or route switch exists.
- `QdrantProductionCandidateM7CloseoutSpec` is the pure aggregate closeout evidence. It does not approve an opt-in route, production activation, or hybrid serving.
- The canonical per-expectation pending map for these route-boundary expectations lives in `docs/codebase-review/06-tests-and-contracts.md`.

What not to infer:

- Passing Qdrant tests does not make Qdrant the production backend.
- Hybrid projection tests do not implement production fallback or score fusion.
- Candidate-readiness activation does not approve production-route activation.

Related design boundary:

- Implemented search service/backend classes do not by themselves imply a production API boundary.
- Production Beauty search still requires explicit route/binding, with route-level contract tests defining the HTTP contract before any runtime backend is treated as production-exposed.

## Decision 8: Benchmark Decision Policy Is Decision Support

Statement:

- Benchmark verdicts are not production automation.

Evidence:

- `docs/search-dsl-qdrant-vector-backend.md` and `docs/search-dsl-hybrid-v1-plan.md` warn against automatic model switching.
- `QdrantEmbeddingBenchmarkDecisionPolicy.scala` defines verdicts, but no production consumer was found in `LeaderboardPlugin.scala` or runtime roles.

Consequences:

- Benchmark reports can inform humans, but should not silently alter runtime routing.

What not to infer:

- A `CandidateWorthSwitching` verdict does not imply the app will switch models.

## Decision 9: Generic Retrieval Seams Do Not Replace BeautyQ Projection Policy

Statement:

- Generic lexical/semantic/hybrid seams are separate from BeautyQ-specific projection/merge policy.

Evidence:

- Generic seams live in `search/lexical`, `search/semantic`, and `HybridDocumentRetrievalResult.scala`.
- BeautyQ-specific policy/projection lives in `BeautyQHybridProjectionPolicy.scala`, `BeautyQHybridVariantProjection.scala`, `BeautyQHybridProviderServiceProjection.scala`, and `BeautyQHybridResponseAdapter.scala`.
- `HybridGenericSecondDomainProofSpec.scala` uses fake article ids/documents to show generic reuse without BeautyQ response adapters.

Consequences:

- Generic containers do not decide final BeautyQ ranking/projection.
- BeautyQ response assembly remains domain-specific.

What not to infer:

- Generic retrieval seams are not production adapters by themselves.

## Decision 10: ES startup-readiness lifecycle is non-serving; the production route gate is implemented separately

Statement:

- Current Elasticsearch startup-readiness lifecycle metadata (`ElasticsearchSeedLifecycleMetadata`, `ElasticsearchProductionReadinessState`, `ElasticsearchStartupReadinessTransition`, `ElasticsearchStartupReadinessStatusResponse`) remains seed-only and non-serving. Design A operator visibility (`GET /ops/beauty-search/lifecycle`) is implemented as an explicit opt-in/internal endpoint, absent from the default ES route graph and from `seedCatalogInMemory`. M5 is closed as a bounded startup-readiness lifecycle checkpoint; replacement/freshness/rollback and full lifecycle operations remain separate future tracks.
- Separately, the runtime route gate for `/beauty-search` itself is implemented as `BeautySearchServingGate` (M14B), disabled by default: disabled -> 200 OK existing ES-backed behavior; enabled-not-ready -> HTTP 503; enabled-ready -> 200 OK; invalid request -> 400 before the gate. `ElasticsearchOperatorVisibilityEndpointPolicySpec` has zero pending tests following M14B/M14C.

Evidence and full M14-M17 statement: `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` sections 1-2.

What not to infer:

- Do not infer production lifecycle completion (replacement/freshness/rollback/full lifecycle operations) from the M5 closeout or the M14B gate.
- Do not infer that the M14B gate changes default behavior; it is `disabled` by default and the default `/beauty-search` route is unchanged.
- Do not infer default/public exposure of the operator visibility endpoint; it remains explicit opt-in/internal only.

## Decision 13: Qdrant closeouts stop at approval-request readiness, not serving approval

Statement:

- M6 is closed as the Qdrant production-candidate readiness foundation, and M7 is closed as the activation/source-confirmation and serving-policy planning foundation. The next boundary is approval-request readiness for a future disabled-by-default opt-in route only; implementation approval and production route activation remain absent.

Evidence:

- `QdrantProductionCandidateM6CloseoutSpec` closes the pure readiness foundation.
- `QdrantProductionCandidateM7CloseoutSpec` closes the pure planning/source-confirmation foundation.
- `QdrantProductionCandidateServingApprovalRequestSpec` proves the current evidence package is ready to request explicit approval only.
- `LeaderboardPlugin` still includes only the ES-backed route through `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.

Consequences:

- `/beauty-search` remains ES-backed.
- Any future Qdrant route must be a separate opt-in module and requires separate implementation approval.
- No serving, route switch, hybrid serving, fallback, score fusion, reranking, shadow serving, or traffic mirroring is approved by these closeouts.

What not to infer:

- Do not infer Qdrant implementation approval from approval-request readiness.
- Do not infer production route activation approval from M6 or M7 closure.
