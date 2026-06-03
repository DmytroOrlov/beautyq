# Search DSL Domain Onboarding

## Goal

This document describes how to reuse the current search DSL and interpreter stack for a new domain without copying domain logic into backend interpreters.

The working rule is simple:

- describe search semantics as immutable Scala values
- keep interpreters mechanical
- if an interpreter needs domain/search behavior, add that behavior to the DSL/spec first

BeautyQ is the first concrete domain using this pattern. The stack is intentionally tapir-like in architecture: a small immutable description layer, plus multiple interpreters driven by that description.

## Generic Engine Pieces

These pieces should stay domain-agnostic even if some current type names still contain `Beauty`:

- `SearchField`
- `SearchFieldKind`
- `SearchFieldSemantic`
- `SearchDocumentSpec`
- `SearchConstraint`
- `SearchSynonym`
- `FacetSpec`
- `RankingSpec`
- `SearchRequestSpec`
- `BeautySearchSpec`
- `ElasticsearchMappingInterpreter`
- `ElasticsearchIngestionInterpreter`
- `ElasticsearchSearchRequestInterpreter`
- `ElasticsearchSearchResponseInterpreter`
- `InMemorySearchBackend`
- eval assertion helpers such as `BeautySearchEvalTestSupport`

The invariant for all of them:

- if a backend interpreter needs domain/search semantics, put it into the DSL/spec first
- interpreters should mechanically consume the spec
- backend constants that affect search behavior belong in `SearchRequestSpec`, not inside the Elasticsearch interpreter
- field mapping, ingestion, filters, facets, boosts, searchable fields, and semantic constraint translation must come from `SearchField` and `SearchDocumentSpec`

In practice that means:

- mappings come from `SearchDocumentSpec.fields`
- ingestion comes from `SearchField.extract`
- full-text fields and boosts come from `SearchField.searchable` and `SearchField.boost`
- filter translation comes from `SearchField.semantic`
- facet generation comes from `FacetSpec`
- request behavior knobs come from `SearchRequestSpec`
- in-memory and Elasticsearch paths should share the same search semantics, even if their scoring details differ

## Domain Pieces

A new domain should provide its own domain-specific layer on top of the generic engine:

- a flattened search document analogous to `VariantSearchDocument`
- a document builder from the domain source of truth
- a canonical domain search spec analogous to `BeautySearchSpecV1`
- field definitions with extractors
- semantic markers for service/category/provider/price/geo or other domain concepts
- dictionary and synonym data
- result projection and carousel/grouping config
- eval dataset and milestone subsets
- pure in-memory tests
- Elasticsearch integration tests

The point is not to make the domain disappear. The point is to keep domain knowledge in one explicit place instead of spreading it through parser branches and backend-specific code.

## Reusable-Domain Note

For a new domain, do not expect the DSL to remove the need for domain eval and dictionary work.

The reusable part is:

* `SearchDocumentSpec`
* `SearchField` metadata
* mapping/ingestion/request/response interpreters
* in-memory backend
* eval workflow
* pure-first then backend verification

The domain-specific part is:

* flattened search document
* domain spec
* dictionary/synonyms
* eval dataset
* carousel/result projection

## BeautyQ Happy Path

This is the sequence that worked for BeautyQ and should be reused for a new domain.

### Step 1

Identify the primary searchable or buyable unit.

For BeautyQ this was `MasterServiceOfferVariant`.

### Step 2

Build one flattened search document with enough data for:

- the primary result carousel
- provider/grouped results
- service/navigation results
- facets
- inferred filters
- geo ranking
- text search

For BeautyQ this became `VariantSearchDocument`.

### Step 3

Describe all fields in `SearchDocumentSpec`.

Do not let Elasticsearch infer domain structure from ad-hoc request code.

### Step 4

Add dynamic domain attributes through a registry.

For BeautyQ this was `AttributeDefinition.all`.

### Step 5

Create the canonical domain spec.

For BeautyQ this was `BeautySearchSpecV1`.

### Step 6

Add deterministic dictionary and synonym data.

Do not use runtime LLM calls for query interpretation.

### Step 7

Use the same spec for:

- mapping
- ingestion
- search request generation
- response interpretation
- in-memory backend behavior

### Step 8

Add invariant tests proving interpreters are spec-driven:

- mapping changes when a synthetic field is added
- ingestion uses `SearchField.extract`
- request uses searchable fields and boosts from spec
- facets come from `FacetSpec`
- constraint translation uses field semantics, not literal paths
- parser uses synonym data, not hardcoded tokens

### Step 9

Add eval milestones in increasing difficulty:

- easiest direct queries
- second milestone
- hard-negative or ambiguity queries
- then broader, noisier, and more conversational queries

### Step 10

Run each milestone first in-memory, then through Elasticsearch.

That keeps search semantics debuggable before backend-specific behavior enters the picture.

## Anti-Patterns

The following are explicitly forbidden:

- hardcoding domain service names inside Elasticsearch interpreters
- hardcoding attribute lists inside interpreters
- adding query-specific `if query contains ...` branches to backend interpreters
- adding ranking behavior only to Elasticsearch but not to in-memory/spec
- adding dictionary behavior inside parser code instead of spec data
- using LLM calls in runtime search
- adding backend-specific constants that affect search semantics without putting them in `SearchRequestSpec`

If one of these feels convenient, it usually means the DSL/spec is missing a piece of information.

## BeautyQ Status

### Current Eval Coverage

* **Total eval queries**: 63
* **ES V1 lexical covered queries**: 61
* **Qdrant-only semantic candidates**: 2
* **Qdrant-only ids**:
  * `q_broad_004`
  * `q_broad_006`
* **Combined intent-space coverage**: 63/63

### Qdrant-Only Semantic Candidate Eval

The two broad semantic candidates now pass in a separate Qdrant-only eval:

* `q_broad_004`: passed via Qdrant-only semantic retrieval
* `q_broad_006`: passed via Qdrant-only semantic retrieval

Manual command:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
  sbt 'project bifunctor-tagless' \
  'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

This run is manual and environment-gated. It should not be treated as a default production-like test path.

### ES V1 Lexical Boundary

ES V1 is intended to cover:

* direct service queries
* known multilingual synonyms
* enum/boolean/int/decimal attribute queries
* known typo/noise cleanup
* exact commercial intent phrases
* facets, filters, grouping, and deterministic carousels

ES V1 should not be forced to cover:

* broad beauty intent without a stable service/entity signal
* conversational discovery queries
* semantic similarity without dictionary support
* unseen paraphrases that require embeddings

`q_broad_004` and `q_broad_006` remain outside the ES lexical contract. They are covered by a separate Qdrant-only semantic candidate eval, not by Elasticsearch.

### Combined Architecture Status

* Elasticsearch remains the deterministic lexical baseline.
* Qdrant remains a separate semantic recall backend.
* Together they cover 63/63 of the current BeautyQ eval intent space.
* No fallback, hybrid ranking, or reranking has been implemented yet.

### Coverage Rules

New coverage should continue to follow this rule:
1. Add pure/in-memory test first.
2. Then add Elasticsearch integration test in a separate patch.
3. Production changes should be limited to narrow `BeautySearchSpecV1` dictionary/spec data unless a real spec-driven interpreter bug is found.

The next likely work is:
* Extending eval coverage further
* Adding explicit Qdrant quality assertions after model/config stabilization
* Designing fallback or hybrid criteria later as a separate measured change
* Extracting remaining BeautyQ names from generic pieces if and when a second domain is introduced

## Naming Note

Some core names are still BeautyQ-shaped:

- `BeautySearchSpec`
- `BeautySearchResponse`
- `BeautySearchIntentParser`
- `BeautySearchSpecV1`

That is acceptable for V1 because only one domain exists today.

If a second domain is introduced, these names should be revisited at that time. For example:

- `BeautySearchSpec` could become `SearchSpec`
- `BeautySearchResponse` could become a more generic search response plus a domain projection layer

Do not rename them yet. The rename becomes justified when a second domain actually appears, not before.
