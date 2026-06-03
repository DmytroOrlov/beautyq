# BeautyQ Search DSL V1

## Goal

BeautyQ search V1 describes search semantics as immutable Scala values and interprets that data structure into multiple backends:

- Postgres catalog snapshot -> flattened search documents
- Elasticsearch mapping
- Elasticsearch bulk ingestion payload
- Elasticsearch search request
- Elasticsearch search response -> UI response
- pure in-memory search backend for regression tests

The single source of truth is `leaderboard.search.dsl.BeautySearchSpec`.

## Packages

- `leaderboard.search.dsl`: DSL model, canonical `BeautySearchSpecV1`
- `leaderboard.search.document`: flattened `VariantSearchDocument`, snapshot loaders, builder
- `leaderboard.search.parser`: pure query intent parser driven by DSL synonyms
- `leaderboard.search.interpreter`: backend-agnostic support and response assembly
- `leaderboard.search.elasticsearch`: ES mapping / ingestion / request / response interpreters
- `leaderboard.search.inmemory`: pure in-memory backend
- `leaderboard.search.eval`: eval JSON loader and scorer

## Core Model

`BeautySearchSpec` contains:

- `SearchDocumentSpec[VariantSearchDocument]`
- `SearchSynonym` dictionary
- `CarouselSpec`
- `FacetSpec`
- `SearchRequestSpec`

`SearchDocumentSpec.fields` carry the information interpreters need:

- field path
- field kind
- extraction from `VariantSearchDocument`
- semantic tag where needed
- searchable / filterable / facetable / sortable flags
- boosts

This keeps Elasticsearch and in-memory implementations driven by the same metadata.

`SearchRequestSpec` carries backend request behavior that should stay spec-driven rather than hidden inside Elasticsearch code:

- `hitWindowSize`
- `textOperator`
- `aggregationSize`
- `geoDistanceScale`
- `geoDistanceOffset`
- `geoDistanceDecay`

`ElasticsearchSearchRequestInterpreter` must read those values from the spec and should not encode BeautyQ semantics in local constants.

## Search Document

`VariantSearchDocument` is a flattened search read model for `MasterServiceOfferVariant`.

It includes:

- identifiers for variant / offer / master / location / service / category
- display fields needed in search responses
- geo point and numeric search fields
- typed attribute maps
- denormalized text fields for search

`VariantSearchDocumentBuilder` is pure and does not silently drop rows. Broken joins fail with `QueryFailure.DomainFailure`.

## Canonical Spec

`BeautySearchSpecV1` defines:

- core text fields: `allText`, `serviceText`, `attributeText`, `providerText`, `locationText`
- filter and facet fields for ids, names, numeric values, geo point
- dynamic attribute fields generated from `AttributeDefinition.all`
- deterministic synonym dictionary for the first milestone query set

If a backend needs a new field or rule, it must be added here first.

## Parser

`BeautySearchIntentParser` is pure and deterministic.

It:

- normalizes query text
- applies longest-match synonym resolution
- converts matched dictionary items into `SearchConstraint`s and soft boosts
- leaves unmatched text as residual full-text input

Domain knowledge stays in the synonym dictionary, not in the parser algorithm.

## Interpreters

### Elasticsearch

- `ElasticsearchMappingInterpreter`: derives mapping from `SearchDocumentSpec.fields`
- `ElasticsearchIngestionInterpreter`: builds `_bulk` NDJSON from the same spec
- `ElasticsearchSearchRequestInterpreter`: builds bool query, boosts, facets, and grouping aggregations from spec metadata, parsed intent, and `SearchRequestSpec`
- `ElasticsearchSearchResponseInterpreter`: decodes hits and delegates carousel / facet / inferred-filter assembly to shared logic

The Elasticsearch interpreters must remain mechanical. If a search behavior affects request shape, field selection, aggregation sizing, or geo scoring knobs, the value belongs in the DSL/spec first rather than in an interpreter-local branch or constant.

### In-memory

`InMemorySearchBackend` applies the same parsed intent and shared response assembly without Docker.

This is the main regression harness for search semantics.

## Shared Response Assembly

`SearchResponseAssembler` builds the final `BeautySearchResponse` from scored documents and spec metadata:

- `variantCarousel`
- `providerCarousel`
- `serviceIntentCarousel`
- `facets`
- `inferredFilters`

Grouping and ranking use `CarouselSpec` and `RankingSpec`, not backend-local rules.

## Test Strategy

Pure tests cover:

- dynamic field generation from `AttributeDefinition.all`
- document builder failures on broken joins
- mapping derivation from spec
- request derivation from spec
- parser behavior for the first milestone queries
- in-memory regression against eval subset

Docker-backed Elasticsearch integration tests cover:

- mapping creation
- ingestion count
- first milestone eval subset
- response shape with exactly three carousels

The ES integration suite uses a seed-scoped Postgres snapshot loader so tests stay deterministic even when the shared managed Postgres instance contains unrelated rows from other suites.

## Current Eval Coverage

* **Total eval queries**: 63
* **ES V1 lexical covered queries**: 61
* **Qdrant-only semantic candidates**: 2
* **Qdrant-only ids**:
  * `q_broad_004`
  * `q_broad_006`
* **Combined intent-space coverage**: 63/63

## Qdrant-Only Semantic Candidate Eval

The two intentionally non-lexical eval queries now pass in a separate Qdrant-only semantic candidate run:

* `q_broad_004`: passed via Qdrant-only semantic retrieval
* `q_broad_006`: passed via Qdrant-only semantic retrieval

This run was manual and environment-gated:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
  sbt 'project bifunctor-tagless' \
  'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

The local embedding server remains a manual prerequisite for this eval path. It is not part of normal test execution and is not a production runtime dependency.

## ES V1 Lexical Boundary

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

`q_broad_004` and `q_broad_006` remain outside the ES V1 lexical contract. They are covered by the separate Qdrant-only semantic candidate eval and are not treated as failed lexical coverage work.

## Combined Architecture Status

Current backend roles are intentionally separate:

* Elasticsearch V1 remains the deterministic lexical/filter/facet baseline.
* Qdrant remains a separate semantic recall backend for broad semantic candidates.
* Together they cover the current 63/63 BeautyQ eval intent space.
* No ES/Qdrant fallback has been implemented yet.
* No hybrid ranking/fusion has been implemented yet.
* No reranking has been implemented yet.

### Coverage Rules

New coverage should continue to follow this rule:
1. Add pure/in-memory test first.
2. Then add Elasticsearch integration test in a separate patch.
3. Production changes should be limited to narrow `BeautySearchSpecV1` dictionary/spec data unless a real spec-driven interpreter bug is found.

## Recommended Next Step

1. Add explicit Qdrant quality assertions only after the embedding model and config are stable.
2. Design fallback or hybrid criteria later as a separate measured change.
