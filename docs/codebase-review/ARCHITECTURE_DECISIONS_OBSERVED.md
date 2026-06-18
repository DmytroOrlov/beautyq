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

- Qdrant and hybrid paths are implemented as non-production/manual/local/test/experimental boundaries, not production app behavior.

Evidence:

- `docs/search-dsl-qdrant-vector-backend.md` says production hybrid is not implemented and Qdrant is not production lifecycle/routing/fallback.
- `docs/search-dsl-hybrid-v1-plan.md` says Hybrid V1 is a non-production foundation.
- Class names include `QdrantNonProductionExperiment*`, `BeautyQNonProductionHybrid*`, and `ExperimentalBeautySearchService`.
- `LeaderboardPlugin.scala` does not bind Qdrant/hybrid search services.

Consequences:

- Qdrant/hybrid docs must label current status carefully.
- Production rollout needs separate lifecycle, activation, routing, freshness, and kill-switch design.

What not to infer:

- Passing Qdrant tests does not make Qdrant the production backend.
- Hybrid projection tests do not implement production fallback or score fusion.

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

## Decision 10: Current ES lifecycle state is seed-only and non-serving

Statement:

- Current Elasticsearch lifecycle metadata and production-readiness state remain seed-only internal seams until a production lifecycle contract is implemented.

Evidence:

- `ElasticsearchSeedIndexReadiness.lifecycleMetadata` sets `EagerSeedIndexPreparation` plus `SeedOnlyNotProductionLifecycle`.
- `ElasticsearchSeedSearchComposition.lifecycleMetadata` forwards that metadata.
- `ElasticsearchProductionReadinessState.seedOnly` derives explicit current values: `NotEnforced`, `NotConfigured`, `NotTracked`, `EagerSeedPreparationOnly`, `NotConfigured`, and `NotExposed`.
- `ElasticsearchSeedSearchComposition.productionReadinessState` forwards the derived state, and focused route/module specs prove it is materialized through the ES-backed route graphs without changing route behavior.
- `ElasticsearchLifecycleStatusResponse.from` provides a pure non-serving projection with local Circe encoding; it is not bound into route graphs.
- `ElasticsearchStartupReadinessTransition` provides a pure prepared/operation-failure shape with `ElasticsearchStartupServingDecision.NotEnforced`; prepared values derive the same non-serving response as direct state projection, while failures expose no lifecycle metadata or status response. It is now bound into route graphs through `BeautySearchCatalogBackendModules.seedResourceElasticsearch` from `ElasticsearchSeedSearchComposition.startupReadinessTransition`, but the binding remains non-serving and does not gate startup or route behavior.
- `ElasticsearchStartupReadinessStatusResponse` provides a pure non-serving startup status projection from prepared and failed transitions. Prepared projections include the nested lifecycle status response; failed projections expose operation/message only without lifecycle metadata or status response. Local Circe encoding is provided. The projection is not DI-bound or HTTP-exposed. Cross-model consistency coverage proves field-level agreement across readiness state, lifecycle response, startup transition, startup status projection, and composition-derived projections.

Consequences:

- Current route-graph state coverage proves DI availability; separate pure transition coverage proves preparation-result and status-projection alignment. These seams make the missing capabilities explicit but do not enforce startup readiness or implement replacement, freshness, refresh triggers, rollback, or operator-visible production lifecycle status.
- Any operator-facing lifecycle exposure remains unimplemented until an endpoint path and operator policy are separately approved.
- M5 is closed as a bounded startup-readiness lifecycle checkpoint. Full ES production lifecycle remains incomplete.

What not to infer:

- Do not infer production readiness enforcement, refresh/replacement/rollback policy, route switch, fallback, score fusion, reranking, `HybridServe`, or Qdrant auto-supplement from these internal seams.

## Decision 11: ES lifecycle visibility remains non-serving until serving-gate policy is approved

Statement:

- Current ES lifecycle metadata, startup readiness transition, and startup status projection remain non-serving visibility seams until an explicit serving-gate policy is approved and implemented.

Evidence:

- `ElasticsearchProductionReadinessState.seedOnly` records `NotEnforced` serving readiness, `NotConfigured` replacement, `NotTracked` freshness, `EagerSeedPreparationOnly` refresh, `NotConfigured` rollback, and `NotExposed` operator visibility.
- `ElasticsearchStartupReadinessTransition` records `NotEnforced` serving decision for both prepared and failed transitions.
- `ElasticsearchStartupReadinessStatusResponse` provides a pure non-serving startup status projection that is not DI-bound or HTTP-exposed.
- `ES_STARTUP_SERVING_GATE_DESIGN.md` documents the serving-gate policy design with five policy choices (fail closed until prepared, fail fast on preparation failure, continue serving with seed-only status, serve stale/previous index, operator override) that must be approved before enforcement.
- No endpoint, route path, HTTP status policy, or operator policy is implemented.
- No serving-readiness enforcement exists.
- M4 is closed; M5 is closed as a bounded startup-readiness lifecycle checkpoint. Full ES production lifecycle remains incomplete.
- The ES operator visibility track is now source-confirmed in `ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`. Design A (expose current prepared/seed-only status from constructed route graph) is the recommended next step. Design B (startup failure via bootstrap-level state) and Design C (richer status after replacement/freshness/rollback) remain future.

Source-confirmed seam analysis (see `ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md`):

- `BeautySearchCatalogBackendModules.seedResourceElasticsearch` eagerly runs `ElasticsearchSeedSearchComposition.build` via `unsafe.run`. If `build` fails, DI graph construction fails and no route is constructed. If `build` succeeds, the DI-bound transition is always `Prepared`.
- The smallest candidate enforcement seam is `BeautySearchApi.serverLogic` (`BeautySearchApi.scala:21-29`), but enforcement is currently impossible because the DI-bound transition is always `Prepared`.
- App-start fail-closed behavior is implicitly implemented by the eager composition pattern. Runtime route gate requires a different source seam.
- Spec-only route-level tests are the recommended next step before any enforcement code.

Operator visibility source-confirmed facts (see `ES_OPERATOR_VISIBILITY_SOURCE_CONFIRMATION.md`):

- `ElasticsearchLifecycleStatusResponse` and `ElasticsearchStartupReadinessStatusResponse` are implemented as pure non-serving models with Circe encoders. They can be exposed later without new ES calls.
- The `Prepared` variant is always reachable from the DI-bound transition.
- Cross-model consistency is proven by `ElasticsearchReadinessConsistencySpec`.
- Failed transition projection shape is available from pure tests but unreachable from the DI-bound transition.
- Replacement, freshness, refresh, rollback, and operator override data are not available from current source models.
- The likely future endpoint seam follows existing Tapir/http4s patterns.
- Policy decisions required: endpoint path, auth/operator access model, response status code policy.

Design A endpoint policy drafted (see `ES_OPERATOR_VISIBILITY_ENDPOINT_POLICY.md`):

- Draft recommends `GET /ops/beauty-search/lifecycle` as endpoint path.
- Draft recommends disabled-by-default auth policy.
- Draft recommends `200 OK` for successful retrieval with lifecycle status in body.
- Draft recommends `ElasticsearchStartupReadinessStatusResponse` (always `Prepared` variant) as response shape.
- All draft recommendations remain unapproved.

Consequences:

- The serving-gate design is documented but not enforced.
- Any enforcement implementation requires explicit approval of a serving-gate policy choice.
- The recommended default is `fail closed until prepared`, but it is not implemented.
- App-start fail-closed is implicitly implemented by eager composition; it is now test-covered by `ElasticsearchAppStartServingGateSpec` (composition-level and DI-graph-level). Runtime route gate requires a new source seam.
- Operator visibility is source-confirmed and ready for design-only policy work (endpoint path, auth, HTTP status).

What not to infer:

- Do not infer that the serving-gate design constitutes enforcement or that any policy choice is approved.
- Do not infer production lifecycle completion from the existence of the serving-gate design document.
- Do not infer that the implicit app-start fail-closed behavior is an approved production lifecycle policy.
- Do not infer that operator visibility is implemented from the source confirmation; it remains a future track requiring policy approval.

## Decision 12: M5 closed as bounded startup-readiness lifecycle checkpoint

Statement:

- M5 is closed as a bounded startup-readiness lifecycle checkpoint covering app-start fail-closed, prepared-serving, non-serving lifecycle seams, DI/rooting, failure classification, and consistency coverage. Full ES production lifecycle remains incomplete and moves to named future tracks.

Evidence:

- `docs/codebase-review/M5_ES_LIFECYCLE_CHECKPOINT.md` documents the closeout checkpoint, bounded M5 definition, source/test evidence, and future ES lifecycle tracks.
- `ElasticsearchAppStartServingGateSpec` proves app-start fail-closed behavior (composition-level and DI-graph-level) and prepared-serving behavior.
- `ElasticsearchReadinessConsistencySpec` proves cross-model field-level consistency.
- `ElasticsearchProductionReadinessState.seedOnly` records `NotEnforced`, `NotConfigured`, `NotTracked`, `EagerSeedPreparationOnly`, `NotConfigured`, `NotExposed`.
- `ElasticsearchLifecycleStatusResponse` has `productionLifecycleComplete = false`.
- `ES_STARTUP_SERVING_GATE_DESIGN.md` documents five policy choices that must be approved before enforcement.
- `ES_STARTUP_SERVING_GATE_SOURCE_CONFIRMATION.md` source-confirms the implementation slice analysis and completes the bounded M5 decision.

Consequences:

- M5 is closed with a precise bounded definition that does not claim full production lifecycle completion.
- Runtime route gate, operator endpoint, replacement, freshness, refresh, rollback, dashboard, and full production lifecycle verification move to named future tracks.
- The ES operator visibility track, ES runtime serving-gate track, and ES replacement/freshness/rollback track are independent of Qdrant/hybrid roadmap milestones (M6/M7/M8).

What not to infer:

- Do not infer production lifecycle completion from M5 closure.
- Do not infer that runtime serving-gate enforcement, operator-visible endpoint, replacement, freshness, refresh, or rollback are implemented.
- Do not infer that the implicit app-start fail-closed behavior is an approved production lifecycle policy.
