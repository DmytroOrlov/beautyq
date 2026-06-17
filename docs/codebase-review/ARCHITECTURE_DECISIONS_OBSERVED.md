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
- `TapirHttpSupportContractSuite.scala` pins shared Tapir behavior.
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
