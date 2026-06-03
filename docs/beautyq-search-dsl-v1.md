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

`SearchDocumentSpec.fields` carry the information interpreters need:

- field path
- field kind
- extraction from `VariantSearchDocument`
- semantic tag where needed
- searchable / filterable / facetable / sortable flags
- boosts

This keeps Elasticsearch and in-memory implementations driven by the same metadata.

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
- `ElasticsearchSearchRequestInterpreter`: builds bool query, boosts, facets, and grouping aggregations from spec metadata and parsed intent
- `ElasticsearchSearchResponseInterpreter`: decodes hits and delegates carousel / facet / inferred-filter assembly to shared logic

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
