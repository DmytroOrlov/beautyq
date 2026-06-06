# Search DSL Qdrant Vector Backend

## 1. Goal

Qdrant is a separate backend for semantic/vector recall in BeautyQ search.

It complements the Elasticsearch V1 lexical baseline. It does not replace it, mutate it, or relax its current deterministic contract.

The intended role for Qdrant is to help with broad, conversational, and semantic queries that are intentionally outside the Elasticsearch V1 lexical boundary today, including:

- `q_broad_004`
- `q_broad_006`

The architectural rule should stay the same as the existing tapir-like search DSL pattern:

- immutable DSL/spec data first
- interpreters second
- no hidden BeautyQ-specific search logic inside backend interpreters

Qdrant should therefore become another interpreter of the search DSL/spec rather than a BeautyQ-specific side path.

Elasticsearch remains the deterministic lexical/filter/facet baseline.

## 2. Current Eval Status

The current BeautyQ eval status is:

* Elasticsearch V1 lexical/filter/facet search covers 61/63 eval queries.
* `q_broad_004` passes via Qdrant-only semantic retrieval.
* `q_broad_006` passes via Qdrant-only semantic retrieval.
* ES plus Qdrant therefore cover 63/63 eval intent space.
* This is not hybrid search yet.

The Qdrant-only semantic candidate eval was run manually with a local embedding server:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
  sbt 'project bifunctor-tagless' \
  'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

That run is environment-gated and should remain separate from normal default test execution.

## 3. Non-goals for now

The first Qdrant design note explicitly excludes the following:

- no hybrid ranking yet
- no ES/Qdrant fusion yet
- no reranking yet
- no runtime LLM generation
- no changes to Elasticsearch interpreters
- no production dependency on the local embedding server yet
- no Qdrant fallback, even though separate Qdrant eval coverage now exists
- no BeautyQ-specific dictionary logic inside Qdrant interpreters

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

They should not become production runtime assumptions in this docs-only task.

The embedding model, pooling mode, vector dimension, and distance metric must later be represented in DSL/spec data rather than hidden in ad-hoc local setup.

## 5. Spec-driven architecture

Future Qdrant support should add DSL/spec data first and interpreters second.

Proposed future DSL/spec concepts:

- `VectorDistance`
  - `Cosine`
  - `Dot`
  - `Euclidean`
- `EmbeddingSpec[A]`
  - vector name
  - embedding model name
  - vector dimension
  - distance metric
  - source text field paths
  - source text extraction strategy
- `VectorSearchSpec`
  - collection name
  - topK
  - optional score threshold
  - vector name
- optional vector config on the domain search spec
  - default should be disabled / `None`
  - Elasticsearch V1 behavior must remain unchanged

Future Qdrant interpreters should read that spec rather than infer behavior from BeautyQ-specific branches:

- `QdrantCollectionInterpreter`
- `QdrantIngestionInterpreter`
- `QdrantSearchRequestInterpreter`
- `QdrantSearchResponseInterpreter`

The invariant is the same as for the current Elasticsearch path:

- if Qdrant needs semantics, add them to the DSL/spec first
- do not hardcode BeautyQ services, attributes, query phrases, or dictionary rules in Qdrant interpreters

## 6. Candidate embedding text for BeautyQ

A future `VariantSearchDocument` embedding text could combine selected human-readable fields such as:

- service name
- category name
- normalized enum attributes
- boolean attributes rendered as natural text
- price/duration text if useful
- master/location text only if intentionally wanted for semantic retrieval
- not raw IDs

The important constraint is that this text shape must be specified through `EmbeddingSpec`, not hidden in Qdrant ingestion code.

That keeps embedding behavior reviewable, testable, and reusable across domains.

## 7. Collection lifecycle and versioning policy

Current status is intentionally non-production:

- Qdrant search/indexing helpers exist.
- `VariantSearchDocumentSnapshotProvider` exists.
- `QdrantVariantDocumentSnapshotIndexer` exists.
- the env-gated experimental service spec exists
- no production collection lifecycle exists

Current non-production readiness building blocks:

- `QdrantCollectionIdentity`
- `QdrantCollectionInfoDecoder`
- `QdrantCollectionCompatibilityValidator`
- `QdrantCollectionCompatibilityChecker`
- `QdrantCollectionCompatibilityGuard`
- `QdrantVariantDocumentSnapshotIndexer.indexCompatibleSnapshot`
- `QdrantSnapshotIndexingCompatibilityGuard`
- env-gated guarded snapshot indexing integration smoke

Safe non-production collection readiness flow:

1. choose an explicit versioned collection name
2. create an isolated collection outside production lifecycle
3. build `QdrantCollectionCompatibilityExpectation`
4. run the read-only compatibility guard
5. only after compatibility succeeds, load the snapshot
6. index the snapshot through `QdrantVariantDocumentSnapshotIndexer`
7. delete temp collections only in tests or non-production experiments

Collection identity must be explicit and versioned. At minimum it should include:

- domain/search spec version
- embedding model id or name
- vector dimension
- vector name
- distance metric
- optional environment or purpose suffix

Example versioned collection name:

- `beautyq-v1-qwen3-embedding-0_6b-1024-default-cosine-exp`

Compatibility must be strict:

- expected dimension comes from `EmbeddingSpec`
- collection vector config must match the expected dimension, vector name, and distance metric
- mismatch must fail fast
- there must be no silent recreate in production-like paths

Non-production policy:

- tests may create unique temporary collections
- tests may delete and recreate isolated collections
- experiments may use explicit versioned collection names

Forbidden for now:

- no production collection manager
- no destructive recreate of active collections
- no alias or blue-green switching
- no production Distage wiring
- no Qdrant-as-default
- no fallback-on-zero-results
- no score fusion or reranking
- no HTTP/API metadata surface
- no Elasticsearch facet replacement

Future production policy:

- no destructive recreate of an active collection
- alias and blue-green collection management are deferred
- any future alias switch requires full indexing plus eval and health checks first
- a rollback strategy is required before production rollout

Next code step:

- either a non-production explicit experiment composition helper
- or config-only types for experiment collection readiness
- still no production wiring

## 8. Safe implementation sequence

Future implementation should follow this exact sequence.

1. Add DSL data model only:
   - `VectorDistance`
   - `EmbeddingSpec`
   - `VectorSearchSpec`
   - no clients
   - no network calls
   - no Docker
2. Add tiny pure tests:
   - construct vector specs
   - verify `BeautySearchSpecV1` has vector backend disabled by default
   - verify Elasticsearch tests still compile/pass
3. Add embedding text extraction:
   - driven by `EmbeddingSpec`
   - no HTTP calls yet
   - test with one `VariantSearchDocument`
4. Add Qdrant collection JSON generation:
   - no network calls
   - purely generate request JSON from spec
5. Add Qdrant Docker smoke test:
   - only create/delete collection
   - no embedding model yet
6. Add local embedding HTTP client:
   - behind a local/test interface
   - no production dependency
   - verify against the local `llama.cpp` embedding server only in local/integration tests
7. Add Qdrant ingestion:
   - generate embeddings for `VariantSearchDocument`
   - upsert vectors with payload containing ids needed for response interpretation
8. Add Qdrant query test:
   - start with `q_broad_004` and `q_broad_006`
   - compare raw vector recall
   - do not fuse with ES yet
9. Compare ES vs Qdrant on eval:
   - ES remains lexical baseline
   - Qdrant is measured separately
   - no hybrid/fusion until metrics justify it
10. Only later consider hybrid:
   - ES constraints + Qdrant recall
   - or Qdrant fallback for semantic candidates
   - or reranking
   - each as separate measured changes

## 9. Eval strategy

Qdrant should not be judged by replacing all Elasticsearch eval queries.

Elasticsearch should remain responsible for direct lexical, filter, facet, and attribute queries.

Qdrant should first target broad, conversational, and semantic candidates, especially:

- `q_broad_004`
- `q_broad_006`

Separate Qdrant eval subsets should be added and stabilized before any hybrid behavior is introduced.

Qdrant now has a green semantic-candidate eval slice for `q_broad_004` and `q_broad_006`, but that does not by itself justify fallback, fusion, or reranking.

## 10. Guardrails

- do not change Elasticsearch behavior while adding Qdrant
- do not put BeautyQ dictionary logic in Qdrant interpreters
- do not call the embedding model from the parser
- do not add Qdrant as a fallback in the same change that establishes Qdrant-only eval
- do not introduce hybrid ranking in the first Qdrant implementation
- do not use Qdrant to hide bad lexical/dictionary regressions
- if Qdrant needs semantics, add them to the DSL/spec first
- keep Qdrant implementation steps small and independently verifiable
- add explicit Qdrant quality assertions only after the embedding model and config are stable

## 11. Relationship to reusable-domain onboarding

Qdrant support should be generic in the same way the Elasticsearch interpreters are intended to be generic.

A new domain should provide:

- a domain flattened document
- a domain `SearchDocumentSpec`
- an optional `EmbeddingSpec`
- domain eval data

The Qdrant interpreters should not depend on BeautyQ domain classes except through typed spec/document parameters.

That keeps the vector backend reusable and helps the search DSL evolve into a reusable cross-domain architecture rather than a BeautyQ-only implementation.

## 12. Recommended Next Step

1. Stabilize the embedding model/config and then add explicit Qdrant quality assertions for the semantic candidate slice.
2. Design fallback, hybrid, or reranking criteria later as a separate measured change.
