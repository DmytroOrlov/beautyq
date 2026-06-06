# Search DSL Qdrant Vector Backend

## 1. Goal

Qdrant is a separate backend for semantic/vector recall in BeautyQ search.

It complements the Elasticsearch V1 lexical baseline. It does not replace it, mutate it, or relax its deterministic contract.

The intended role for Qdrant is to help with broad, conversational, and semantic queries that are intentionally outside the Elasticsearch V1 lexical boundary today, including:

* `q_broad_004`
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
* Env-gated Qdrant plus llama.cpp integration is green locally.
* Full test and env-full test have been user-verified green after the current non-production Qdrant composition work.
* This is still not production hybrid search.

Current implemented non-production pieces:

* `EmbeddingSpec`
* `VectorSearchSpec`
* `VectorDistance`
* `SearchEmbeddingTextExtractor`
* `QdrantJsonInterpreter`
* `QdrantClient`
* `QdrantSearchClient`
* `QdrantPointUpsertClient`
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
* `QdrantNonProductionExperimentComposition`
* env-gated Qdrant semantic candidate eval
* env-gated Qdrant collection compatibility smoke
* env-gated guarded snapshot indexing smoke
* env-gated experimental hybrid service smoke

The current path is best described as a non-production experimental readiness foundation.

It is not production lifecycle, not production routing, not production fallback, and not production hybrid wiring.

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

## 9. Non-production experiment wiring boundary

The current non-production experiment path should remain separate from production search wiring.

Production default:

```text
BeautySearchService
  -> current Elasticsearch-backed search path
```

Non-production experiment path:

```text
explicit test/config/axis setup
  -> QdrantCollectionReadinessConfig
  -> QdrantNonProductionExperimentComposition
  -> ExperimentalBeautySearchService
  -> explicit SearchRoutingMetadata
```

Allowed in non-production experiments:

* explicit versioned Qdrant collection names
* manual/test setup collection creation
* env-gated local llama.cpp embedding
* env-gated Qdrant Docker/container integration
* guarded snapshot indexing
* explicit `SearchRoutingMetadata`
* fail-fast lexical backends in tests
* direct construction of `ExperimentalBeautySearchService`

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

### Activation boundary

Future non-production wiring, if added, must be behind an explicit activation boundary.

Acceptable future shapes:

* a test-only axis
* a non-production experiment axis
* an explicit local experiment module
* an explicit manual task/test setup

Unacceptable shapes:

* enabled by `Mode.Prod` default
* implicit through `Mode.Test` without a named experiment boundary
* automatic production startup indexing
* production search path depending on Qdrant availability

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

### Metadata source

Routing metadata is currently explicit and test/eval supplied.

Future experiment metadata may come from a non-production method, local test setup, or explicit internal call surface, but it is not yet an HTTP/API contract.

Residual text alone must not route to Qdrant.

## 10. Future production lifecycle policy

Production lifecycle work remains explicitly out of scope.

Before production rollout, the system would need:

* production-safe collection manager design
* collection versioning policy
* alias or blue/green switching design
* rollback strategy
* stale snapshot/freshness policy
* batching/retry/backpressure/idempotency for indexing
* operational diagnostics
* route observability
* metadata source design
* rollout and kill-switch design
* fallback semantics design
* score calibration/fusion/reranking design, if needed

Production destructive recreation of an active collection is forbidden.

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

* `q_broad_004`
* `q_broad_006`

Separate Qdrant eval subsets should stay stabilized before any hybrid production behavior is introduced.

Qdrant semantic-candidate eval and env-gated experimental service smoke tests do not by themselves justify fallback, fusion, reranking, or production routing.

## 12. Relationship to reusable-domain onboarding

Qdrant support should be generic in the same way the Elasticsearch interpreters are intended to be generic.

A new domain should provide:

* a domain flattened document
* a domain `SearchDocumentSpec`
* an optional `EmbeddingSpec`
* optional `VectorSearchSpec`
* domain eval data

The Qdrant path should not depend on BeautyQ domain classes except through typed spec/document parameters.

That keeps the vector backend reusable and helps the search DSL evolve into a reusable cross-domain architecture rather than a BeautyQ-only implementation.

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

1. Add a lightweight experimental route diagnostics model before any non-production wiring.

Then:

2. Design non-production experiment activation/axis in detail.
3. Add non-production wiring only behind that explicit boundary.
4. Keep production `BeautySearchService` unchanged.
5. Later design metadata source, collection lifecycle, fallback, fusion, reranking, rollout, and rollback separately.

Still not next:

* production Distage wiring
* HTTP/API metadata surface
* Qdrant-as-default
* fallback-on-zero-results
* residual-text routing
* score fusion/reranking
* collection manager / alias switching
* startup indexing hook
