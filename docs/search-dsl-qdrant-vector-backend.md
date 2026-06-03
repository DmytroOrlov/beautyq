# Search DSL Qdrant Vector Backend

## 1. Goal

Qdrant is a future second backend for semantic/vector recall in BeautyQ search.

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

## 2. Non-goals for now

The first Qdrant design note explicitly excludes the following:

- no hybrid ranking yet
- no ES/Qdrant fusion yet
- no reranking yet
- no runtime LLM generation
- no changes to Elasticsearch interpreters
- no production dependency on the local embedding server yet
- no Qdrant fallback until Qdrant has separate eval coverage
- no BeautyQ-specific dictionary logic inside Qdrant interpreters

## 3. Local prototype commands

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

## 4. Spec-driven architecture

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

## 5. Candidate embedding text for BeautyQ

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

## 6. Safe implementation sequence

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

## 7. Eval strategy

Qdrant should not be judged by replacing all Elasticsearch eval queries.

Elasticsearch should remain responsible for direct lexical, filter, facet, and attribute queries.

Qdrant should first target broad, conversational, and semantic candidates, especially:

- `q_broad_004`
- `q_broad_006`

Separate Qdrant eval subsets should be added before any hybrid behavior is introduced.

Qdrant should not become a fallback path until it has its own green eval tests.

## 8. Guardrails

- do not change Elasticsearch behavior while adding Qdrant
- do not put BeautyQ dictionary logic in Qdrant interpreters
- do not call the embedding model from the parser
- do not add Qdrant as a fallback until separate Qdrant eval exists
- do not introduce hybrid ranking in the first Qdrant implementation
- do not use Qdrant to hide bad lexical/dictionary regressions
- if Qdrant needs semantics, add them to the DSL/spec first
- keep Qdrant implementation steps small and independently verifiable

## 9. Relationship to reusable-domain onboarding

Qdrant support should be generic in the same way the Elasticsearch interpreters are intended to be generic.

A new domain should provide:

- a domain flattened document
- a domain `SearchDocumentSpec`
- an optional `EmbeddingSpec`
- domain eval data

The Qdrant interpreters should not depend on BeautyQ domain classes except through typed spec/document parameters.

That keeps the vector backend reusable and helps the search DSL evolve into a reusable cross-domain architecture rather than a BeautyQ-only implementation.
