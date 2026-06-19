# Search DSL Qdrant Vector Backend

## 1. Goal

Qdrant is a separate backend for semantic/vector recall in BeautyQ search.

It complements the Elasticsearch V1 lexical baseline. It does not replace it, mutate it, or relax its deterministic contract.

The intended role for Qdrant is to help with broad, conversational, and semantic queries that are intentionally outside the Elasticsearch V1 lexical boundary today, including:

* `q_broad_001`
* `q_broad_002`
* `q_broad_003`
* `q_broad_004`
* `q_broad_005`
* `q_broad_006`

The architectural rule stays the same as the existing search DSL pattern:

* immutable DSL/spec data first
* pure policy and interpreters second
* runtime composition third
* production wiring only after explicit lifecycle, routing, metadata, and rollout decisions

Qdrant should therefore remain another interpreter/composition path for search DSL/spec data, not a BeautyQ-specific side path.

Elasticsearch remains the deterministic lexical/filter/facet baseline.

## 2. Current eval and implementation status

Current eval status:

* Elasticsearch V1 lexical/filter/facet search covers the deterministic lexical baseline.
* Qdrant semantic candidate retrieval covers the broad semantic slice that Elasticsearch intentionally does not close with dictionary hacks.
* generic retrieval/indexing boundaries are present.
* genericity is currently expressed within the existing search DSL input model.
* pure BeautyQ domain-specific projection/merge policy and response adapters are present.
* Env-gated Qdrant plus llama.cpp integration is green locally.
* The named non-production embedding benchmark subset has been expanded beyond only `q_broad_004` and `q_broad_006`; it now uses a small explicit eval-id list for broad-query coverage.
* Full test and env-full test have been user-verified green after the current non-production Qdrant composition work.
* Production hybrid is not implemented.

Current implemented non-production pieces:

* `EmbeddingSpec`
* `VectorSearchSpec`
* `VectorDistance`
* `SearchEmbeddingTextExtractor`
* `QdrantJsonInterpreter`
* `QdrantClient`
* `QdrantSearchClient`
* `QdrantPointUpsertClient`
* `QdrantPointId`
* `QdrantDocumentPointBuilder`
* `QdrantSearchDocumentIndexer`
* `LexicalDocumentHit`
* `LexicalDocumentBackend`
* `SemanticDocumentHit`
* `SemanticDocumentBackend`
* `SemanticDocumentLookup`
* `SemanticCandidateAssembler`
* `SemanticResponseProjector`
* `HybridDocumentRetrievalResult`
* `HybridDocumentRetrievalDiagnostics`
* `QdrantCollectionInfoClient`
* `QdrantCandidateHitDecoder`
* `QdrantSemanticCandidateSearch`
* `QdrantSemanticCandidateBackend`
* `QdrantVariantDocumentPointBuilder`
* `QdrantVariantDocumentIndexer`
* `VariantSearchDocumentSnapshotProvider`
* `QdrantVariantDocumentSnapshotIndexer`
* `QdrantCollectionIdentity`
* `QdrantCollectionInfoDecoder`
* `QdrantCollectionCompatibilityValidator`
* `QdrantCollectionCompatibilityChecker`
* `QdrantCollectionCompatibilityGuard`
* `QdrantCollectionReadinessConfig`
* `QdrantProductionCandidateReadinessStatus`
* `QdrantProductionCandidateReadinessState`
* `QdrantProductionCandidateReadinessReport`
* `QdrantProductionCandidateReadiness`
* `QdrantNonProductionExperimentComposition`
* env-gated Qdrant semantic candidate eval
* env-gated Qdrant collection compatibility smoke
* env-gated guarded snapshot indexing smoke
* env-gated experimental hybrid service smoke

The current path includes a source-backed production-candidate readiness foundation plus non-production runtime experiments.

The readiness foundation is pure and route-independent. It is not production lifecycle completion, production routing, production fallback, or production hybrid wiring.

The real-resource non-production hybrid adapter boundary for future v0 is now recorded at docs level only.
That adapter is not implemented yet.
Its scope is semantic-side only: Qdrant semantic backend, embedding/Llama client, document lookup, readiness/compatibility guard, and explicit manual/test/local invocation.
The Elasticsearch lexical/filter/facet baseline remains separate and injected as the existing lexical backend.
This future v0 is manual/test/local only.
It is not production hybrid, not a `BeautySearchService` replacement, not Qdrant-as-default, and not a combined Qdrant+ES+Llama production module.

Reusable domain seams/status:

* generic Qdrant document indexing seam: done
* Qdrant point-id invariant: done
* generic semantic backend boundary: done
* generic semantic candidate assembly boundary: done
* generic semantic response projector boundary: done
* generic lexical/Elasticsearch result seam: done
* generic hybrid document retrieval seam: done
* ES matched_queries decoding correctness follow-up: done
* benchmark complete-query validation correctness follow-up: done
* domain-specific hybrid projection/merge policy: done for variant policy, hydrated variant projection, and intermediate provider/service projection candidates

### Review follow-ups before projection/merge policy

* Done: ES matched queries decoding now reads real ES `matched_queries`, keeps fallback for legacy/test `_matched_queries`, and documents that generic lexical `matchedFields` currently means ES matched query names / lexical diagnostics, not highlights.
* Done: benchmark validation hardening now fails clearly if a candidate returns fewer results than expected query ids, requires each candidate result set to cover the selected benchmark query ids, and keeps the existing duplicate candidate id / unexpected candidate id / unexpected query id validations.
* Done: the named benchmark subset now includes more explicit eval query ids beyond `q_broad_004` and `q_broad_006`.
* Hybrid diagnostics clarity: review `HybridDocumentRetrievalDiagnostics` executed flags, avoid implying lexical/semantic channels executed when representing lexical-only or semantic-only retrieval, and either pass execution flags explicitly later or keep the current helper scoped to both-channel retrieval.
* Qdrant point id naming/compatibility: clarify that current numeric id support is JVM-safe non-negative `Long`, not the full unsigned 64-bit range, record the possible later rename from `UnsignedLong` to `NonNegativeLong` or equivalent, and note that legacy raw-string `upsertPointJson(id: String, ...)` may remain for compatibility while new generic indexing paths must use `QdrantPointId`.
* Abstraction proliferation guardrail: do not add new generic seams unless they are needed by a second domain proof, projection/merge policy, or a concrete correctness gap.

## 3. Non-goals for now

The current Qdrant work explicitly excludes:

* no Qdrant-as-default
* no production `BeautySearchService` replacement
* no production Distage wiring
* no HTTP/API metadata contract
* no residual-text routing to Qdrant
* no fallback-on-zero-results
* no ES/Qdrant score fusion
* no reranking
* no runtime LLM generation
* no production dependency on local llama.cpp
* no production collection manager
* no destructive recreation of active collections
* no alias or blue/green switching
* no Elasticsearch facet replacement
* no BeautyQ-specific dictionary logic inside Qdrant interpreters
* no eval query ids in main production routing code

## 4. Local prototype commands

These commands are local-only prototype commands for future experimentation:

```bash
~/git/llama.cpp/build/bin/llama-server \
  -m ~/git/Qwen3-Embedding-0.6B-Q8_0.gguf \
  --embedding \
  --pooling last \
  -ub 8192 \
  --port 8081

docker run -p 6333:6333 -p 6334:6334 \
  -v "$HOME/qdrant_storage:/qdrant/storage" \
  qdrant/qdrant
```

These commands are for local experimentation only.

They must not become production runtime assumptions.

The embedding model, pooling mode, vector dimension, vector name, and distance metric must be represented through DSL/spec/config data rather than hidden in local setup.

## 5. Spec-driven architecture

Qdrant support follows the same spec-driven pattern as the Elasticsearch path.

Relevant DSL/spec concepts:

* `VectorDistance`

    * `Cosine`
    * `Dot`
    * `Euclidean`
* `EmbeddingSpec[A]`

    * vector name
    * embedding model name
    * vector dimension
    * distance metric
    * source text field paths
* `VectorSearchSpec`

    * collection name
    * vector name
    * topK
    * optional score threshold
* optional vector config on the domain search spec

    * default should remain disabled / `None`
    * Elasticsearch V1 behavior must remain unchanged

The invariant is the same as for the current Elasticsearch path:

* if Qdrant needs semantics, add them to DSL/spec data first
* do not hardcode BeautyQ services, attributes, query phrases, or dictionary rules in Qdrant clients/interpreters
* keep BeautyQ-specific decisions in `BeautySearchSpecV1`, eval data, or explicit experiment wiring

## 6. Candidate embedding text for BeautyQ

`VariantSearchDocument` embedding text is generated from spec-driven fields.

It may combine selected human-readable fields such as:

* service name
* category name
* normalized enum attributes
* boolean attributes rendered as natural text
* price/duration text if useful
* other explicitly configured text fields

It should not rely on raw IDs.

The important constraint is that embedding text shape must be specified through `EmbeddingSpec`, not hidden in Qdrant ingestion code.

That keeps embedding behavior reviewable, testable, and reusable across domains.

## 7. Collection identity, compatibility, and readiness

Collection identity must be explicit and versioned.

At minimum, identity includes:

* domain name
* search spec version
* purpose or environment suffix
* embedding model id/name
* vector name
* vector dimension
* distance metric

`QdrantCollectionIdentity` renders deterministic collection names from that identity data.

`QdrantCollectionReadinessConfig` derives a single non-production readiness config from:

* `domainName`
* `searchSpecVersion`
* `purpose`
* `EmbeddingSpec`
* `VectorSearchSpec`

It is the current single source of truth for:

* rendered collection name
* `VectorSearchSpec.collectionName`
* `QdrantCollectionCompatibilityExpectation.collectionName`

This prevents the class of errors where one collection is checked for compatibility but another collection is indexed or queried.

### Vector name source of truth

For Qdrant collection identity and compatibility, the named vector used by Qdrant is `VectorSearchSpec.vectorName`.

`EmbeddingSpec` still carries embedding model, dimension, distance, and source text extraction information. If `EmbeddingSpec.vectorName` and `VectorSearchSpec.vectorName` diverge, the Qdrant collection/readiness path treats `VectorSearchSpec.vectorName` as the collection vector name.

This should remain explicit in tests and future wiring.

## 8. Non-production collection readiness flow

Current readiness building blocks:

* `QdrantCollectionIdentity`
* `QdrantCollectionReadinessConfig`
* `QdrantCollectionInfoDecoder`
* `QdrantCollectionCompatibilityValidator`
* `QdrantCollectionCompatibilityChecker`
* `QdrantCollectionCompatibilityGuard`
* `QdrantVariantDocumentSnapshotIndexer.indexCompatibleSnapshot`
* `QdrantSnapshotIndexingCompatibilityGuard`
* `QdrantNonProductionExperimentComposition`

Safe non-production sequence:

1. Choose an explicit domain/search version and purpose.
2. Build `QdrantCollectionReadinessConfig`.
3. Create an isolated non-production Qdrant collection outside production app startup.
4. Build a read-only `QdrantCollectionCompatibilityGuard`.
5. Validate collection info through Qdrant collection-info.
6. Fail fast if collection name, vector name, dimension, distance, or observed embedding metadata is incompatible.
7. Only after the guard succeeds, load the document snapshot.
8. Index the snapshot through `QdrantVariantDocumentSnapshotIndexer.indexCompatibleSnapshot`.
9. Use the same readiness-derived `VectorSearchSpec` for semantic candidate search.
10. Delete temporary collections only in tests or isolated non-production experiments.

The guarded indexer intentionally sequences readiness before data loading:

* `QdrantSnapshotIndexingCompatibilityGuard` carries the expected collection identity and guard.
* `indexCompatibleSnapshot` calls `requireCompatible` first.
* snapshot loading and document upserts happen only after compatibility succeeds.
* compatibility failures stop indexing instead of recreating, mutating, or falling back to another collection.

Compatibility is strict:

* expected dimension comes from `EmbeddingSpec`
* expected distance comes from `EmbeddingSpec`
* collection vector name comes from `VectorSearchSpec`
* collection vector config must match expected vector name, dimension, and distance metric
* optional observed `metadata.embeddingModelName` must match expected embedding model when present
* real Qdrant collection-info may omit collection name; in checker context, expected collection name is used because the request is already scoped to `/collections/${expected.collectionName}`
* if a response explicitly contains a different collection name, `CollectionNameMismatch` must still be reported
* there must be no silent recreate in production-like paths

## 8.5. Production-candidate readiness foundation

`QdrantProductionCandidateReadinessState` records:

* whether Qdrant remains active;
* collection/identity readiness;
* contract parity readiness;
* indexing readiness;
* search readiness;
* quality/eval readiness;
* observability readiness;
* rollback/disable readiness;
* activation-policy readiness.

`QdrantProductionCandidateReadiness.evaluate` derives `QdrantProductionCandidateReadinessReport.productionCandidateReady`. It is true only when Qdrant is active and every required category is explicitly `Ready`.

The conservative default is intentionally incomplete:

* Qdrant is active;
* quality/eval is `NotEvaluated`;
* observability and rollback/disable are `NotConfigured`;
* activation policy is `NotApproved`;
* other unproven categories are `Unknown`;
* `productionCandidateReady` is false.

Collection/identity status can be adapted from the existing compatibility result. The adapter preserves the existing mismatch order and meaning; it does not reimplement collection name, vector name, dimension, distance, or embedding-model checks.

Quality/eval status is now backed by `QdrantProductionCandidateQualityGate`. Its pure `EngineEvalAggregateReport` adapter records baseline/candidate labels, evaluated query count, ES baseline recall, Qdrant candidate recall, and Qdrant noise. An explicit rule sets minimum query count, maximum recall deficit, and maximum noise, and the report records an explicit parity outcome. Passed reports map to `Ready`; failed reports map to `NotReady` with stable reasons; absent reports map to `NotEvaluated`; incomplete evidence maps to `Unknown`.

This foundation requires neither shadow serving nor production traffic mirroring. It does not add a Qdrant serving route, route switch, fallback, score fusion, reranking, `HybridServe`, Qdrant auto-supplement, runtime HTTP gate, or `/beauty-search` behavior change. Broader accepted evidence, configured controls, and activation approval remain future M6 inputs. M7 and M8 remain future-only and conditional.

## 9. Non-production experiment wiring boundary

The current non-production experiment path must remain separate from production search wiring.

Production default:

```text
BeautySearchService
  -> current Elasticsearch-backed search path
```

Non-production experiment boundary:

```text
manual/test/local experiment setup
  -> QdrantCollectionReadinessConfig
  -> QdrantNonProductionExperimentComposition
  -> QdrantNonProductionHybridExperiment
  -> explicit SearchRoutingMetadata
```

This boundary is deliberately outside the production app graph.

### Current wiring decision

There is intentionally no Qdrant hybrid Distage wiring yet.

`LeaderboardPlugin` currently wires roles, APIs, repositories, seed readiness, and config. It now wires the production-exposed seed-resource `/beauty-search` route via the ES-backed seed route backed by `ElasticsearchSearchBackend`. `InMemorySearchBackend` remains rollback/non-default. It still does not wire production Qdrant or hybrid search-service graph. Adding Qdrant hybrid bindings there now would either create an unused module or move the project too close to production hybrid search before lifecycle, metadata, and rollout decisions are complete.

The supported runtime boundary for local/test/manual experiments is `QdrantNonProductionHybridExperiment`. It composes readiness/composition with `ExperimentalBeautySearchService`, but it is not part of the production app graph.

### Allowed in non-production experiments

Allowed in non-production experiments:

* explicit versioned Qdrant collection names
* manual/test setup collection creation
* env-gated local llama.cpp embedding
* env-gated Qdrant Docker/container integration
* guarded snapshot indexing
* explicit `SearchRoutingMetadata`
* fail-fast lexical backends in tests
* direct construction of `QdrantNonProductionHybridExperiment`
* direct construction of `ExperimentalBeautySearchService` in focused tests

Not allowed yet:

* production `BeautySearchService` Qdrant wiring
* Qdrant semantic backend in production default module graph
* automatic snapshot indexing at app startup
* automatic collection create/delete/recreate in production app lifecycle
* alias/blue-green switching
* HTTP/API metadata surface
* fallback-on-zero-results
* residual-text routing
* score fusion/reranking
* Qdrant replacement for Elasticsearch facets/filters

### Future non-production wiring rule

Future non-production wiring, if added, must be behind an explicit named experiment boundary.
The recorded v0 boundary is a real-resource non-production adapter only for the semantic side.
It keeps the Elasticsearch baseline separate and injected.

Acceptable future shapes:

* explicit local experiment module
* explicit test-only experiment axis
* explicit non-production experiment axis/config
* explicit manual/admin experiment task
* explicit local runner flag or explicit non-production invocation object

Unacceptable shapes:

* default `Mode.Prod` wiring
* implicit `Mode.Test` activation without a named experiment boundary
* wiring Qdrant into the production `BeautySearchService`
* automatic production startup indexing
* automatic collection create/delete/recreate in app startup
* production search depending on Qdrant availability
* automatic include from `LeaderboardPlugin`
* HTTP request flag without separate API design

### Allowed experiment bindings

If a future non-production module is added, it may bind only experiment-scoped components such as:

* `QdrantCollectionReadinessConfig`
* `QdrantNonProductionExperimentComposition`
* `QdrantNonProductionHybridExperiment`
* `QdrantSemanticCandidateBackend`
* `QdrantCollectionCompatibilityChecker`
* `QdrantCollectionCompatibilityGuard`
* snapshot provider / upsert / indexer for explicit test or manual setup
* explicit `SearchRoutingMetadata` source for the experiment
* embedding/Llama client for explicit local/test/manual invocation only
* explicit semantic document lookup for the experiment

These bindings must not become the production default graph.
Disabled mode must not include or build Qdrant, embedding/Llama, semantic backend, lookup, indexing, or experiment resources.
Returning `None` after real resources were already constructed is insufficient.
The existing by-name construction-safe factory and fake-only module gating proof already support this invariant, and the future real-resource adapter v0 must preserve it.

### Experiment lifecycle ownership

Collection lifecycle remains outside the production app:

* collection creation is explicit manual/test/local setup only
* collection deletion is test/local cleanup only
* snapshot indexing is explicit manual/local/test action only
* no production startup hook
* no request-time indexing
* no hidden indexing side effect from constructing a service

Versioned collection names remain the current policy.

Alias/blue-green switching, rollback, production collection manager, and destructive recreate policies are deferred production-lifecycle work.
The future v0 adapter may read/check an existing non-production collection and verify compatibility expectation.
It must fail fast if incompatible.
It must not create production collections automatically, alias-swap, run blue/green lifecycle, or delete/recreate collections.
Collection setup remains explicit manual setup, not module startup.

### Snapshot indexing trigger

Snapshot indexing should remain explicit.

Acceptable current/future triggers:

* env-gated integration test setup
* explicit local experiment task
* explicit non-production admin/manual task

Not acceptable yet:

* production app startup hook
* production request-time indexing
* hidden side effect of constructing the search service
* indexing when activation is `Disabled`

Guarded snapshot indexing, if used by future v0, may only be explicit manual/local/test invocation.

### Metadata boundary

Routing metadata remains explicit.

The current experiment may pass:

```scala
SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate))
```

from test/manual harnesses.

This is not yet:

* an HTTP/API field
* residual-text routing
* a production metadata contract
* a fallback trigger

## 10. Future production lifecycle policy

Production lifecycle work remains explicitly out of scope.

Before production rollout, the system would need:

* timeout budget
* retry policy
* production-safe collection manager design
* collection versioning policy
* alias or blue/green switching design
* rollback strategy
* stale snapshot/freshness policy
* Qdrant freshness relative to Elasticsearch
* batching/retry/backpressure/idempotency for indexing
* operational diagnostics
* observability/metrics/tracing
* route observability
* metadata source design
* rollout and kill-switch design
* fallback semantics design
* score calibration/fusion/reranking design, if needed

Production destructive recreation of an active collection is forbidden.

These gaps do not block a manual/local/test v0 adapter, but they do block production promotion.
Any future v0 adapter must report them as unresolved and remain non-production.

Any future alias switch would require:

* full indexing completed
* compatibility checks passed
* eval checks passed
* health checks passed
* rollback path available

## 11. Eval strategy

Qdrant should not be judged by replacing all Elasticsearch eval queries.

Elasticsearch remains responsible for:

* direct lexical queries
* filters
* facets
* exact attributes
* price/duration constraints
* deterministic baseline ranking

Qdrant first targets broad, conversational, and semantic candidates, especially:

* `q_broad_001`
* `q_broad_002`
* `q_broad_003`
* `q_broad_004`
* `q_broad_005`
* `q_broad_006`

The named embedding benchmark subset is still a tiny non-production benchmark/eval subset. Query ids are allowed here, in eval data, tests, benchmark tooling, and documentation examples only; they must not be hardcoded into production routing.

Separate Qdrant eval subsets should stay stabilized before any hybrid production behavior is introduced. This subset expansion does not make the benchmark mature enough for automatic model choice.

Qdrant semantic-candidate eval, benchmark decision output, and env-gated experimental service smoke tests do not by themselves justify fallback, fusion, reranking, production routing, or runtime model switching.

## 12. Relationship to reusable-domain onboarding

Qdrant support should be generic in the same way the Elasticsearch interpreters are intended to be generic.

A new domain should provide:

* a domain flattened document
* a domain `SearchDocumentSpec`
* an optional `EmbeddingSpec`
* optional `VectorSearchSpec`
* a domain `QdrantDocumentPointBuilder`
* domain eval data

The generic document indexing seam now supports:

* domain document + `SearchDocumentSpec`
* `EmbeddingSpec`
* domain `QdrantDocumentPointBuilder`
* injected embedding client
* injected Qdrant point-upsert client

The generic semantic candidate assembly seam now supports:

* domain document ids through `SemanticDocumentHit[Id]`
* domain document hydration through `SemanticDocumentLookup[F, Id, Doc]`
* pure, order-preserving hit/document candidate assembly through `SemanticCandidateAssembler`

The generic semantic response projector boundary now supports:

* domain assembly types as an input boundary
* domain response types as an output boundary
* pure projection through `SemanticResponseProjector[Assembly, Response]`

The generic semantic backend boundary now supports:

* semantic recall input through `UserSearchInput`
* parsed intent input through `ParsedSearchIntent`
* domain document ids through `SemanticDocumentHit[Id]`
* backend-independent semantic hit retrieval through `SemanticDocumentBackend[F, Id]`

The generic lexical/Elasticsearch result seam now supports:

* lexical search input through `UserSearchInput`
* parsed intent input through `ParsedSearchIntent`
* domain document ids through `LexicalDocumentHit[Id]`
* backend-independent lexical score and matched-field hit metadata
* backend-independent lexical hit retrieval through `LexicalDocumentBackend[F, Id]`

The generic hybrid document retrieval seam now supports:

* lexical hits through `LexicalDocumentHit[Id]`
* semantic hits through `SemanticDocumentHit[Id]`
* backend-independent channel result storage through `HybridDocumentRetrievalResult[Id]`
* channel execution diagnostics through `HybridDocumentRetrievalDiagnostics`
* lexical-first distinct document-id diagnostics without ranking claims
* a tiny synthetic second-domain test proof through `HybridGenericSecondDomainProofSpec`

`HybridDocumentRetrievalResult[Id]` holds lexical and semantic hits for the same domain id type.
It preserves lexical hit order, preserves semantic hit order, and keeps lexical and semantic scores separate.
`distinctDocumentIdsInChannelOrder` is a diagnostic/id-list helper only.
`HybridDocumentRetrievalResult[Id]` is a container/diagnostics boundary only.
It is not merge policy, ranking, score fusion, fallback, reranking, or production routing.

This seam only holds separate channel outputs. It does not implement merge policy, score fusion, reranking, fallback, query routing, production hybrid behavior, Elasticsearch behavior changes, or Qdrant behavior changes.

The synthetic second-domain proof is test-level only.
It uses a fake `ArticleId` / `ArticleSearchDocument` domain to show that the existing generic retrieval container and Qdrant indexing boundary can be reused beyond BeautyQ without importing BeautyQ ids, carousels, providers, salons, services, or response adapters.
It does not mean a second production domain implementation exists, and it does not mean a production-generic hybrid engine is complete.
BeautyQ projection, merge, provider/service grouping, and carousel response logic remain domain-specific.

BeautyQ `SemanticCandidateBackend` remains a domain-specific adapter over the generic backend boundary. It keeps the existing `candidates(...)` API and BeautyQ `MasterServiceOfferVariantId` candidate hit shape while exposing generic document hits for reusable semantic infrastructure.

BeautyQ variant, provider, and service carousel projection remains domain-specific. `QdrantCandidateAssembler` and `QdrantCandidateResponseProjector` remain BeautyQ-specific implementations over the reusable seams; the reusable semantic and lexical infrastructure should stop at generic hit boundaries, generic candidate assembly, the generic response projector boundary, and the generic hybrid retrieval container. BeautyQ response shape, grouping, carousel limits, facets, merge policy, and inferred filters stay in BeautyQ-specific projection code.

### BeautyQ hybrid projection/merge policy boundary

The future BeautyQ hybrid output remains `BeautySearchResponse`.

The pure BeautyQ domain-specific hybrid projection/merge policy model now exists as
`BeautyQHybridProjectionPolicy.lexicalFirstSemanticSupplement`.
The pure hydrated variant projection adapter now exists as `BeautyQHybridVariantProjection.project`.
The pure provider/service projection policy model now exists as
`BeautyQHybridProviderServiceProjection.project`.
The pure provider/service response carousel adapter now exists as
`BeautyQHybridResponseAdapter.responseWithProviderServiceCarousels`.
The pure response pipeline adapter now exists as `BeautyQHybridResponsePipeline.projectResponse`.
The non-production BeautyQ hybrid response experiment runner now exists as
`BeautyQNonProductionHybridResponseExperiment`.
The disabled-by-default non-production activation/factory skeleton now exists as
`BeautyQNonProductionHybridExperimentActivation`.

The implemented pure policy is lexical-first semantic supplement:

* Elasticsearch contributes deterministic lexical/filter/facet results.
* Qdrant contributes semantic recall candidates by `MasterServiceOfferVariantId`.
* overlapping variant ids from both channels must be represented once.
* lexical hits are preserved first, with duplicate lexical ids deduplicated by first hit.
* semantic-only ids append after lexical ids, preserving first semantic-hit order.
* both channel diagnostics and channel-local scores are retained separately.
* ES and Qdrant scores are not directly comparable and must not be fused in this design.

The implemented pure hydrated variant projection adapter:

* hydrates only variant candidates from `VariantSearchDocument`
* preserves policy candidate order
* preserves lexical and semantic scores separately
* preserves candidate sources
* fails clearly when a policy candidate has no matching `VariantSearchDocument`

The implemented pure provider/service projection policy:

* creates intermediate provider and service-intent candidates only
* does not build `BeautySearchResponse` provider or service carousels
* groups provider candidates by `masterLocationId`
* groups service-intent candidates by `serviceId`
* preserves group order by first candidate occurrence in policy order
* unions candidate sources inside each group
* counts unique matching variant ids inside each group
* keeps provider sample matching variant ids in policy order with duplicates removed
* uses `representativeDisplayScore` only as a display value, not ranking, fusion, or reranking
* derives `representativeDisplayScore` from the first group candidate with `LexicalThenSemantic`: lexical score when present, otherwise semantic score, otherwise `0.0`

The implemented pure provider/service response carousel adapter:

* builds provider and service carousels from intermediate projection candidates
* applies explicit hybrid carousel limits to `variantCarousel`, `providerCarousel`, and `serviceIntentCarousel`
* derives the default non-production limit policy from `UserSearchInput` and `BeautySearchSpecV1.carouselSpec`: `variantCarousel` uses `min(input.limit, spec.carouselSpec.variantSize)` after normalizing negative limits to empty output, `providerCarousel` uses `spec.carouselSpec.providerSize`, and `serviceIntentCarousel` uses `spec.carouselSpec.serviceIntentSize`
* truncates only after policy/projection order has already been established
* maps `representativeDisplayScore` into `ProviderSearchResult.bestScore` and `ServiceIntentSearchResult.bestScore`
* treats those `bestScore` values as display-only, not fusion, not ranking, not reranking, and not score-calibrated ES/Qdrant comparison
* preserves provider and service carousel order from projection order instead of sorting by score
* does not use limits as ranking, reranking, score sorting, score fusion, fallback, or routing
* limits provider sample matching variant ids to the existing response convention of 3 ids
* keeps `facets` and `inferredFilters` empty because they remain ES/parser-owned in this pure hybrid adapter
* does not call Elasticsearch or Qdrant
* does not route, fallback, or wire production services

The implemented pure response pipeline adapter:

* composes only existing pure pieces: policy -> variant hydration -> provider/service projection -> response adapter
* projects `HybridDocumentRetrievalResult[MasterServiceOfferVariantId]` plus hydrated `VariantSearchDocument` values into `BeautySearchResponse`
* requires explicit `BeautyQHybridResponseCarouselLimits` instead of silently using unlimited response carousels
* returns `BeautySearchResponse` plus combined policy, variant projection, provider/service projection, and response adapter diagnostics
* propagates missing-document `QueryFailure` from variant hydration without building a response
* preserves lexical-first semantic-supplement order from the pure policy
* preserves the separate display score policies already encoded in component outputs
* does not call Elasticsearch, Qdrant, or llama.cpp
* does not do score fusion, reranking, fallback, routing, or production wiring
* does not implement runtime service integration

The implemented non-production BeautyQ hybrid response experiment runner:

* is a library/manual/test/local boundary only
* runs injected `LexicalDocumentBackend`, injected `SemanticDocumentBackend`, and injected `SemanticDocumentLookup`
* hydrates distinct variant ids in lexical-first channel order
* calls the pure `BeautyQHybridResponsePipeline.projectResponse`
* returns `BeautySearchResponse` plus lexical hit count, semantic hit count, distinct id count, and pipeline diagnostics
* propagates backend, lookup, and missing-document failures without fallback or silent dropping
* does not add Distage wiring, HTTP/API, production `BeautySearchService`, routing metadata source, fallback, score fusion, reranking, or collection lifecycle
* remains disabled by default because nothing wires it into the app graph

Provider and service carousels remain BeautyQ-specific projections over hydrated variant, provider, and service data.
They are not raw Qdrant outputs.

Facets and inferred filters remain ES/parser-owned:

* Qdrant does not produce canonical facets.
* Qdrant does not produce inferred filters.
* Qdrant does not own authoritative filter semantics.
* Qdrant-only experiments may return empty facets and empty inferred filters.

Merge must happen at the domain candidate/document id level, then project into BeautyQ carousels.
It must not happen at raw Qdrant point level, inside the Qdrant backend, or inside the Elasticsearch query interpreter.

`HybridDocumentRetrievalResult[MasterServiceOfferVariantId]` is the input container for the BeautyQ policy.
It is not itself the policy.

The implemented pure policy merges only at the `MasterServiceOfferVariantId` candidate level.
It does not fuse scores, rerank, fallback, route queries, produce facets, or produce provider/service carousels.
It does not define production routing.
The hydrated variant projection adapter also does not produce `BeautySearchResponse`, provider carousel, service carousel, facets, inferred filters, score fusion, reranking, fallback, routing, or production wiring.
The provider/service projection policy also does not produce `BeautySearchResponse`, provider carousel, service carousel, facets, inferred filters, score fusion, reranking, fallback, routing, or production wiring.
The response pipeline adapter produces `BeautySearchResponse` only by composing existing pure components; it does not add runtime service integration, Elasticsearch calls, Qdrant calls, llama.cpp calls, score fusion, reranking, fallback, routing, or production wiring.
The non-production response experiment runner can execute injected lexical and semantic document backends plus document lookup and then call that pure pipeline, but it is not production hybrid and remains disabled by default because it has no app-graph wiring.

### Non-production BeautyQ hybrid experiment resource gating

`BeautyQNonProductionHybridResponseExperiment` is present and done as a library/manual/test/local boundary.
`BeautyQNonProductionHybridExperimentActivation` is present and done as a disabled-by-default, construction-safe activation/factory boundary.
It is not app startup wiring, not a replacement for production `BeautySearchService`, and not a production feature.
Its default activation is `Disabled`, and it builds the non-production experiment runner only when explicitly enabled.

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

* implemented in `BeautyQNonProductionHybridExperimentActivation.buildIfEnabled`
* dependency parameters are by-name
* `Disabled` does not evaluate lexical, semantic, Qdrant, lookup, or experiment dependency thunks
* `Enabled` evaluates each dependency factory once when building the runner
* building the runner does not call backend or lookup methods
* fake-only tests also prove an explicit test module/composition boundary: disabled module selection binds only an optional empty runner/root and does not include or build fake semantic/Qdrant/lookup resources; enabled module selection explicitly includes fake lexical, semantic, and lookup resources and builds the experiment runner
* these tests use no real Qdrant/ES/Llama resources

Important limitation:

* the thunk/by-name factory is construction-safe only when callers pass unevaluated constructors/thunks
* the current module proof is fake-only and test-level
* this is not final Distage/module resource gating for real resources
* real non-production Distage/module work must still ensure disabled mode does not include or build the real Qdrant/semantic resource graph at all
* the experiment remains manual/test/local only
* production lifecycle, routing, and metadata remain absent by design

Activation rules:

* activation is explicit and disabled by default
* enabled activation builds only `BeautyQNonProductionHybridResponseExperiment`
* allowed invocation modes are manual task, test setup, and local experiment only
* activation must not be inferred from `Mode.Test` alone
* activation must not be enabled by `Mode.Prod`
* acceptable future wiring shapes are explicit local experiment config, explicit test-only experiment axis, explicit manual/admin task boundary, or explicit non-production module with named activation
* unacceptable shapes are implicit production default, silent `Mode.Test` behavior, HTTP request flag without separate API design, and residual-text-based automatic semantic routing

Wiring boundary:

* future wiring may bind the runner only behind a named non-production boundary
* wiring must use injected lexical backend, semantic backend, and document lookup
* the activation skeleton does not add Distage wiring, `LeaderboardPlugin`, `BeautySearchService`, HTTP/API, production routing, collection lifecycle, or startup indexing
* wiring must not create Qdrant collections
* wiring must not index snapshots on startup
* production search must not depend on Qdrant availability
* production `BeautySearchService` must not change

Routing and metadata boundary:

* the runner takes an already parsed `ParsedSearchIntent`
* the runner does not own parser behavior
* the runner does not own production routing
* routing remains explicit-invocation-only
* the runner does not introduce HTTP/API metadata fields
* the runner does not implement fallback-on-zero-results
* the runner does not implement residual-text routing
* the runner must not be wired into production search

Lifecycle boundary:

* Qdrant collection readiness and snapshot indexing remain explicit setup steps
* collection create/delete/recreate remains outside production app lifecycle
* alias/blue-green lifecycle is not implemented
* production collection manager is not implemented

Response semantics:

* the runner uses the pure `BeautyQHybridResponsePipeline`
* variant, provider, and service carousels are explicitly limited
* truncation happens after policy/projection order is established
* limits do not imply ranking, reranking, score sorting, score fusion, fallback, or routing
* the runner derives `BeautyQHybridResponseCarouselLimits` from `BeautySearchSpecV1.spec` and `UserSearchInput`: variants use `min(input.limit, spec.carouselSpec.variantSize)` with negative values normalized to empty output, providers use `spec.carouselSpec.providerSize`, and services use `spec.carouselSpec.serviceIntentSize`
* a future wiring step must decide whether `BeautySearchSpecV1.spec` remains hardcoded in the experiment or becomes injected/configured
* no-fusion, no-reranking, no-fallback, and no-routing semantics remain unchanged
* `VariantSearchResult.score`, `ProviderSearchResult.bestScore`, and `ServiceIntentSearchResult.bestScore` are display-only in the hybrid adapter
* provider/service `bestScore` values remain display-only where produced by the pure adapter
* facets and inferred filters remain ES/parser-owned

Verification required before any future code wiring:

* normal `sbt test`
* max env full test when llama/Qdrant gates are available
* focused fake-only experiment runner tests
* docs review confirming production guardrails

The construction-safe activation factory with thunked dependencies is implemented.
The tiny synthetic second-domain proof for the generic seams is now implemented at test level.
The optional later Distage/test module adapter must remain disabled by default and must not add production wiring.

Domain point ids must be Qdrant-compatible ids:

* UUID ids render as JSON strings
* unsigned integer ids render as JSON numbers
* arbitrary domain strings must not be used as Qdrant point ids
* arbitrary domain strings such as slugs, service codes, or natural ids belong in payload fields

The Qdrant path should not depend on BeautyQ domain classes except through typed spec/document parameters.

That keeps the vector backend reusable and helps the search DSL evolve into a reusable cross-domain architecture rather than a BeautyQ-only implementation.

BeautyQ candidate grouping and response projection remain domain-specific. `QdrantCandidateAssembler` still owns BeautyQ variant/provider/service grouping, while its generic hit/document assembly step can be reused by other domains.

## 13. Guardrails

* do not change Elasticsearch behavior while adding Qdrant
* do not put BeautyQ dictionary logic in Qdrant clients/interpreters
* do not call the embedding model from the parser
* do not route to Qdrant from residual text alone
* do not add Qdrant fallback in the same change that establishes Qdrant-only eval
* do not introduce hybrid ranking without a separate measured design
* do not use Qdrant to hide bad lexical/dictionary regressions
* if Qdrant needs semantics, add them to DSL/spec data first
* keep Qdrant implementation steps small and independently verifiable
* keep env-gated tests explicit
* keep production default Elasticsearch-only until explicit production design is complete

## 14. Recommended next steps

Immediate next step:

1. small explicit manual adapter/handle skeleton

Immediate code target details:

* keep it explicit manual/test/local only
* prefer a small adapter/handle skeleton before any full Distage/module shape
* no real Qdrant/Llama implementation yet if avoidable
* no `LeaderboardPlugin`
* no `BeautySearchService`
* no startup indexing
* disabled graph must still exclude real-resource bindings

Later:

* keep `LeaderboardPlugin` unchanged while the production search-service graph boundary is still absent
* continue using `QdrantNonProductionHybridExperiment` for manual/test/local experiments
* later: real semantic-side adapter only with Qdrant backend, embedding/Llama client, lookup, and readiness guard
* later: manual local runner with explicit invocation only
* later: larger benchmark taxonomy expansion
* later design metadata source, collection lifecycle, fallback, fusion, reranking, rollout, rollback, freshness, and observability separately

Still not next:

* production Distage wiring
* replacing `BeautySearchService`
* HTTP/API metadata surface
* Qdrant-as-default
* fallback-on-zero-results
* residual-text routing
* score fusion/reranking
* collection manager / alias switching
* startup indexing hook
* production collection manager
* alias/blue-green implementation
* startup auto-indexing
* benchmark decision policy as an automatic model switch
* real Qdrant resource construction in disabled mode

## 15. Living plan and TODOs

Current stage:

* non-production experimental Qdrant path exists
* readiness config, compatibility guard, guarded snapshot indexing, semantic backend, experimental service, and benchmark tooling exist
* generic Qdrant document indexing seam exists and is done
* Qdrant point ids are constrained to UUID or JVM-safe non-negative `Long` ids
* generic semantic candidate assembly boundary exists and is done
* generic semantic response projector boundary exists and is done through `SemanticResponseProjector[Assembly, Response]`
* generic semantic backend boundary exists and is done through `SemanticDocumentBackend[F, Id]`
* generic lexical/Elasticsearch backend result seam exists and is done through `LexicalDocumentHit[Id]` and `LexicalDocumentBackend[F, Id]`
* generic hybrid document retrieval seam exists and is done through `HybridDocumentRetrievalResult[Id]`
* generic hybrid document retrieval diagnostics exist through `HybridDocumentRetrievalDiagnostics`
* tiny synthetic second-domain proof exists and is done through `HybridGenericSecondDomainProofSpec`
* BeautyQ domain-specific hybrid projection/merge policy model exists and is done through `BeautyQHybridProjectionPolicy.lexicalFirstSemanticSupplement`
* BeautyQ pure hybrid policy merges only at `MasterServiceOfferVariantId` candidate level, preserves lexical and semantic scores separately, and does not fuse scores, rerank, fallback, route, produce facets, or produce provider/service carousels
* BeautyQ pure hydrated variant projection adapter exists and is done through `BeautyQHybridVariantProjection.project`
* BeautyQ pure hydrated variant projection adapter hydrates only variant candidates from `VariantSearchDocument`, preserves separate lexical and semantic scores, and fails clearly on missing documents
* BeautyQ pure variant-only response adapter exists and is done through `BeautyQHybridResponseAdapter.variantOnlyResponse`
* BeautyQ pure variant-only response adapter builds `variantCarousel` only from hydrated variant candidates
* BeautyQ pure variant-only response adapter intentionally suppresses `providerCarousel`, `serviceIntentCarousel`, `facets`, and `inferredFilters`
* `VariantSearchResult.score` in the pure hybrid response adapter is display-only under `LexicalThenSemantic`: lexical score when present, otherwise semantic score, otherwise `0.0`
* the pure hybrid response adapter display score is not a fused score, not a ranking score, and is not used to reorder candidates
* BeautyQ pure provider/service projection policy exists and is done through `BeautyQHybridProviderServiceProjection.project`
* BeautyQ pure provider/service projection policy creates intermediate provider/service candidates only, groups by `masterLocationId` and `serviceId`, and preserves group order by first candidate occurrence in policy order
* `representativeDisplayScore` in the pure provider/service projection policy is display-only under `LexicalThenSemantic`, not ranking, fusion, or reranking
* BeautyQ pure provider/service response carousel adapter exists and is done through `BeautyQHybridResponseAdapter.responseWithProviderServiceCarousels`
* BeautyQ explicit hybrid carousel limit policy exists and is done through `BeautyQHybridResponseCarouselLimits`
* BeautyQ hybrid response carousels are explicitly limited: variants use `min(input.limit, spec.carouselSpec.variantSize)` where limits are derived from `BeautySearchSpecV1.spec` and `UserSearchInput`, providers use `spec.carouselSpec.providerSize`, and service intents use `spec.carouselSpec.serviceIntentSize`
* BeautyQ hybrid carousel truncation happens after policy/projection order is established and does not add ranking, reranking, score sorting, score fusion, fallback, or routing
* BeautyQ pure provider/service response carousel adapter maps `representativeDisplayScore` into provider/service `bestScore` fields as display-only values, not fusion, ranking, reranking, or score calibration
* BeautyQ pure provider/service response carousel adapter preserves projection order instead of sorting by score
* BeautyQ pure provider/service response carousel adapter limits provider sample matching variant ids to 3
* BeautyQ pure provider/service response carousel adapter keeps facets and inferred filters empty because those remain ES/parser-owned
* BeautyQ pure response pipeline adapter exists and is done through `BeautyQHybridResponsePipeline.projectResponse`
* BeautyQ pure response pipeline adapter composes only existing pure pieces: policy -> variant hydration -> provider/service projection -> response adapter
* BeautyQ pure response pipeline adapter returns `BeautySearchResponse` plus diagnostics and still does not call ES/Qdrant/llama, fuse scores, rerank, fallback, route, wire production, or implement runtime service integration
* BeautyQ non-production hybrid response experiment runner exists and is done through `BeautyQNonProductionHybridResponseExperiment`
* BeautyQ non-production hybrid response experiment runner is a library/manual/test/local boundary only: it runs injected lexical and semantic document backends plus document lookup and then calls the pure `BeautyQHybridResponsePipeline`
* BeautyQ non-production hybrid response experiment runner does not add Distage wiring, HTTP/API, production `BeautySearchService`, routing metadata source, fallback, score fusion, reranking, collection lifecycle, or default runtime behavior
* BeautyQ disabled-by-default non-production hybrid activation/factory boundary exists and is done through `BeautyQNonProductionHybridExperimentActivation`
* BeautyQ non-production hybrid activation uses a construction-safe thunk/factory boundary: disabled mode does not evaluate dependency thunks, and enabled mode evaluates each dependency factory once when building the runner
* fake-only explicit non-production module gating proof exists and is done: disabled graph excludes fake semantic/Qdrant/lookup resource bindings entirely, while enabled graph explicitly includes fake resources and builds the experiment runner
* this construction-safe factory boundary plus fake-only module proof is not final Distage/module resource gating for real resources; real non-production module work must still ensure disabled mode does not include or build the real Qdrant/semantic resource graph at all
* BeautyQ non-production hybrid activation remains outside Distage, `LeaderboardPlugin`, `BeautySearchService`, HTTP/API, production routing, collection lifecycle, startup indexing, and default runtime behavior
* BeautyQ non-production hybrid activation allows manual task, test setup, and local experiment invocation modes only
* BeautyQ non-production hybrid activation routing remains explicit-invocation-only
* BeautyQ `SemanticCandidateBackend` remains a domain-specific adapter over generic semantic document hits
* BeautyQ variant/provider/service projection remains domain-specific
* `QdrantCandidateAssembler` and `QdrantCandidateResponseProjector` remain BeautyQ-specific implementations over reusable seams
* `QdrantNonProductionHybridExperiment` is the runtime boundary for local/test/manual experiments
* production default remains Elasticsearch-only
* no production hybrid wiring yet
* no real Qdrant hybrid Distage wiring yet
* no final Distage/module resource gating for real resources yet

Immediate next step:

1. optional later: larger benchmark taxonomy expansion

Second-domain proof requirements:

* done: pure-only synthetic proof added
* done: no new generic abstractions were needed

Later pinned TODO:

* done: expand benchmark subset with more explicit eval query ids beyond `q_broad_004` and `q_broad_006`
* later: larger benchmark taxonomy expansion covering hard negatives, near-miss semantic queries, noisy/typo cases, multilingual cases, and broader second-domain eval cases when available

Immediate design/code next step:

* keep any next implementation pure or explicitly non-production, without production wiring
* keep any later Distage/test adapter disabled by default and outside production wiring

Benchmark TODOs:

* done: expand benchmark subset with more explicit eval query ids beyond `q_broad_004` and `q_broad_006`
* use explicit query ids first; do not invent taxonomy until `queryTypes` are standardized
* later include a larger taxonomy for hard negatives, near-miss semantic queries, noisy/typo cases, multilingual cases, lexical-looking/domain-diverse cases, and broader second-domain eval cases when available
* rerun 0.6B vs 4B after subset expansion
* current tiny benchmark verdict remains decision support only; benchmark decision policy is not runtime model switching, and this tiny subset is not mature enough for automatic model choice

Benchmark hardening TODO:

* done: duplicate candidate ids fail clearly before report generation
* done: result query ids without expectations fail clearly in the runner/report path
* done: candidate executor results for the wrong candidate id fail clearly
* done: candidate result sets cover the selected benchmark query ids and fail clearly when they return fewer results than expected
* keep the decision policy documented as a manual evaluation aid, not a production auto-switch

Review follow-up status:

* done: ES `matched_queries` decoding fix/verification
* done: benchmark complete-query validation in the runner/report path
* done: expand benchmark subset with more explicit eval query ids beyond `q_broad_004` and `q_broad_006`
* forbidden production paths remain unchanged
* done: tiny synthetic second-domain proof for generic seams
* done: fake-only explicit non-production module gating proof

Wiring TODO:

* done: record docs boundary for future real-resource non-production hybrid adapter v0
* next: small explicit manual adapter/handle skeleton only
* later: real semantic-side adapter only with Qdrant backend, embedding/Llama client, lookup, and readiness guard
* later: explicit manual/local runner only
* later: lifecycle/freshness/observability design
* much later: production routing/API/metadata design
* keep the Elasticsearch lexical/filter/facet baseline separate and injected as the existing lexical backend
* the future v0 adapter remains manual/test/local only and is not a combined Qdrant+ES+Llama production module
* no production `BeautySearchService` change yet
* no startup auto-indexing
* collection creation remains outside the production app lifecycle
* snapshot indexing remains an explicit test/manual action
* metadata remains explicit and outside HTTP/API for now
* production Distage wiring stays forbidden for now
* Qdrant-as-default stays forbidden for now
* fallback-on-zero-results stays forbidden for now
* residualText -> semantic route stays forbidden for now
* HTTP/API metadata field stays forbidden for now
* score fusion/reranking stays forbidden for now
* production collection manager stays forbidden for now
* alias/blue-green implementation stays forbidden for now
* startup auto-indexing stays forbidden for now
* benchmark decision policy as an automatic model switch stays forbidden for now
* real Qdrant resource construction in disabled mode stays forbidden for now

Acceptance criteria for any future real-resource adapter patch:

* report how `Enabled` is explicitly selected
* report why the disabled graph does not include/build Qdrant/Llama resources
* report where readiness/compatibility is checked
* report whether collection creation is impossible or explicit-only
* report whether snapshot indexing is impossible or explicit-only
* report why no startup indexing occurs
* report why no `BeautySearchService` path changed
* report why no `LeaderboardPlugin` production include changed
* report why benchmark decisions cannot affect runtime model choice
* report what timeout/freshness/observability gaps remain

Watch items:

* env-gated specs may start heavy resources before cancel
* `ExperimentalHybridRouteDiagnostics.reasonCategory` is a string; make it an ADT only if it becomes an API/log contract
* confirm `QdrantNonProductionHybridExperiment.build` keeps spec/readiness embedding/vector config consistent before wiring
* keep benchmark stdout output as manual tooling, not product telemetry
* raw string point-id helpers may remain for compatibility, but new generic indexing paths must use `QdrantPointId`
* `HybridDocumentRetrievalResult` remains a container/diagnostics boundary, not merge policy, ranking, score fusion, fallback, or production routing
