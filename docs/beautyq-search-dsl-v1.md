# BeautyQ Search Architecture

> This document describes the current BeautyQ search implementation state before the planned
> search-contract/module split. Target ownership, module boundaries, and anti-scope-drift rules
> are defined in `docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md`.

## Overview

BeautyQ search describes search semantics as immutable Scala values and interprets that data
structure into multiple backends. The architecture is now model-first and schema-owned: repo entity
metadata, document field ownership, payload specs, intent vocabulary, and runtime configuration all
derive from typed handles and schema objects rather than from a single monolithic spec.

ES remains the primary baseline. Qdrant is a constrained local/test supplement only. There is no
fallback, fusion, or rerank. External route/API JSON contracts, ES request/source/mapping behavior,
and Qdrant payload shape are preserved.

## Layer map

```
repo model graph                         BeautyQRepoGraph, typed RepoEntity/RepoField nodes,
                                         Mirror-derived entity metadata
        │
        ▼
catalog snapshot loading                 BeautySearchCatalogSnapshotLoader.FromRepositories,
                                         seed-scoped loading, preorder traversal,
                                         root exclusion, deduplication
        │
        ▼
materialization-owned document projection SearchDocumentProjection,
                                         BeautyQVariantSearchDocumentMaterialization.project
        │
        ▼
SearchDocumentSpec / SearchField         BeautyQVariantSearchDocumentContract.Fields,
handles                                  selector-derived static fields,
                                         explicit dynamic/computed fields
        │
        ▼
intent vocabulary                        SearchIntentVocabulary, SearchIntentRule,
                                         IntentMatchMode, StructuredAlias,
                                         QueryNoisePhrase, BeautyQSearchIntentVocabulary
        │
        ▼
SearchRuntimeSpec                        aggregates doc schema, query schema,
                                         request/facet/carousel config, payload specs,
                                         embedding config, vector config, runtime metadata;
                                         BeautySearchSpecV1.runtimeSpec wires app-side config
        │
        ▼
generic ES interpreter                   consumes SearchDocumentSpec / SearchRuntimeSpec /
                                         resolved constraints; app-side adapter:
                                         BeautyQElasticsearchInterpreterAdapter
        │
        ▼
generic Qdrant interpreter               generic over document/id/payload specs; BeautyQ
                                         wrappers map generic hits back to existing shapes
        │
        ▼
BeautyQ app-side adapters                query schema resolution, hybrid policy, response
                                         assembly, route/API models, startup wiring
```

## Module ownership (current physical modules)

This is the **current physical module layout**, not the target module split. The target 10-module
split, dependency DAG, and forbidden dependencies are defined in
`docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md`.

| Module | Owns (current) |
|---|---|
| `leaderboard-core` | generic failure types such as `QueryFailure` |
| `search-core` | generic fields, document spec, runtime spec, fingerprinting, document JSON, generic semantic candidate assembly, generic semantic supplement policy |
| `search-elasticsearch` | reusable ES client/interpreter code |
| `search-qdrant` | reusable Qdrant client/interpreter/indexing/semantic-search/compatibility code |
| `bifunctor-tagless` | BeautyQ app-side schemas, adapters, backends, routes, startup/plugin wiring, eval/benchmark code, concrete embedding infrastructure |

Generic modules (`search-core`, `search-elasticsearch`, `search-qdrant`) must not know BeautyQ
names or app types.

## Ownership table (current locations)

Where each kind of search concern currently lives:

| Concern | Current location |
|---|---|
| new repo entity / source / relation | model class + `RepoEntity` / `RepoField` + `BeautyQRepoGraph` (current/legacy compatibility surface) |
| new document field (static/selector-derived) | `BeautyQVariantSearchDocumentContract.Fields` + document spec |
| new dynamic or computed field | explicit computed/dynamic field in `BeautyQVariantSearchDocumentContract` |
| new intent phrase or rule | `BeautyQSearchIntentVocabulary` |
| new query constraint mapping | BeautyQ query schema resolution |
| new payload field | schema-owned `SearchDocumentPayloadSpec` |
| new carousel / ranking / presentation name or default | `BeautyQSearchPresentation` |
| new generic backend behavior | `search-core` runtime metadata + generic interpreter |
| new BeautyQ-specific route / response behavior | app-side adapter / backend / assembler in `bifunctor-tagless` (current/legacy compatibility surface) |

Current legacy locations remain until migration phases remove them.
New target ownership must follow `docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md`.

## BeautySearchSpec / BeautySearchSpecV1

`BeautySearchSpec` and `BeautySearchSpecV1` are **app-side wiring and compatibility aggregates**.
They are not the single source of truth for all search metadata:

- `BeautySearchSpecV1.runtimeSpec` wires BeautyQ app-side config into the generic
  `SearchRuntimeSpec`.
- Document field ownership belongs to `BeautyQVariantSearchDocumentContract.Fields` (in
  `beautyq-search-contract`), not to `BeautySearchSpecV1`.
- Projection is owned by `BeautyQVariantSearchDocumentMaterialization.project` (via
  `SearchDocumentProjection`) in `beautyq-search-materialization`, which projects into the
  contract-shaped `documentSpec` owned by `BeautyQVariantSearchDocumentContract`;
  `VariantSearchDocumentBuilder` in `bifunctor-tagless` is only a compatibility adapter that delegates
  directly to it.
- Generic ES and Qdrant interpreters consume `SearchDocumentSpec` / `SearchRuntimeSpec` / resolved
  constraints. BeautyQ-specific ES compatibility lives in `BeautyQElasticsearchInterpreterAdapter`.

## Repo / data loading

Repo/data loading is model-first. Scala case-class models drive repo entity metadata through
Mirror-derived metadata. Repo field metadata is selector-derived through typed `RepoField` handles.
The BeautyQ catalog graph is currently declared in `BeautyQRepoGraph` through typed entity nodes,
value sources, and relations. `BeautyQRepoGraph` is a current/legacy compatibility surface, not the
future declaration owner — see the legacy compatibility retirement matrix in
`docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md` for its target replacement and removal
conditions.

`BeautySearchCatalogSnapshotLoader.FromRepositories` and seed-scoped loading go through the shared
repo graph/loading layer. Existing loading behavior is preserved: preorder category traversal, root
exclusion, stable parent/child ordering, first-occurrence deduplication, seed-scoped
missing-entity messages, and search projection semantics.

## Materialization-owned document projection

`SearchDocumentProjection` is the projection layer between loaded catalog snapshots and indexed
documents. `BeautyQVariantSearchDocumentMaterialization` in `beautyq-search-materialization` owns
BeautyQ variant projection; the contract-shaped `SearchDocumentSpec` it projects into is owned by
`BeautyQVariantSearchDocumentContract`. The production seed-catalog path uses
`BeautyQVariantSearchDocumentMaterialization.project` directly (via
`BeautySearchCatalogSnapshot.toMaterializationSnapshot`). The former
`BeautyQVariantSearchDocumentSchema` compatibility facade in `bifunctor-tagless` has been deleted; its
last callers now call the materialization projection engine directly.

`VariantSearchDocumentBuilder` remains present in `bifunctor-tagless` as a compatibility adapter
only, delegating directly to `BeautyQVariantSearchDocumentMaterialization.project`.

## Document field ownership

`BeautyQVariantSearchDocumentContract.Fields` owns BeautyQ document field handles:

- Static direct fields use selector-derived `SearchField` helpers.
- Dynamic/computed fields remain explicit.
- External ES/Qdrant field names are derived path views, not independent string ownership.

`SearchDocumentSpec.fields` carry the information interpreters need: field path, field kind,
extraction, semantic tag, searchable/filterable/facetable/sortable flags, and boosts.

## Intent vocabulary

The intent parser is no longer described as being driven by a synonym dictionary. Structured intent
vocabulary replaces that model:

- `SearchIntentVocabulary` — generic vocabulary interface
- `SearchIntentRule` — individual matching rule
- `IntentMatchMode` — how rules match
- `StructuredAlias` — structured alias entry
- `QueryNoisePhrase` — noise phrase entry
- `BeautyQSearchIntentVocabulary` — BeautyQ intent rules

`BeautySearchIntentParser` is pure and deterministic. It normalizes query text, applies
longest-match rule resolution, converts matched vocabulary items into `SearchConstraint`s and soft
boosts, and leaves unmatched text as residual full-text input. Domain knowledge stays in the
vocabulary, not in the parser algorithm.

Do not call this "DSL synonyms" or reference a `SearchSynonym dictionary` as current architecture.

## SearchRuntimeSpec and fingerprint

`SearchRuntimeSpec` is the current runtime/fingerprint aggregation. It aggregates document schema,
query schema, request/facet/carousel config, payload specs, embedding config, vector config, and
runtime metadata. `BeautySearchSpecV1.runtimeSpec` wires BeautyQ app-side config into generic
runtime metadata. `SearchRuntimeSpec` is not the full target search contract — the eventual full
`SearchDomainSpec` target (catalog/document/intent/runtime/response/evaluation sections) is
defined by `docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md`.

`SearchRuntimeFingerprint` derives from runtime schema/config and includes: document field
metadata, query schema mappings, request config, facets, carousel, ranking, payload paths,
embedding source fields, vector config, layered extra sections, and behavior-affecting presentation
metadata such as `geoScoringBoostRole`. Managed local bootstrap uses runtime-derived fingerprint
inputs.

## Qdrant payload ownership

Generic `SearchDocumentPayloadSpec` derives explicit payload maps from schema-owned field paths.
BeautyQ Qdrant payload contract remains exactly: `variantId`, `masterLocationId`, `serviceId`,
`serviceName`. `QdrantDocumentPointBuilder.fromPayloadSpec` is generic. The BeautyQ Qdrant point
builder is a thin adapter.

## Carousel / ranking / presentation metadata

`search-core` has generic `SearchBoostRole`, named `RankingWeight`, named `CarouselLimit`, named
`CarouselGroup`, generic `CarouselSpec`, and generic `RankingSpec`.

BeautyQ presentation names and defaults live app-side in `BeautyQSearchPresentation`:

- roles: `service`, `attribute`, `providerDistance`
- limits: `variantSize`, `providerSize`, `serviceIntentSize`
- groups: `providerGroupField`, `serviceIntentGroupField`
- ranking defaults: `textScoreWeight = 1.0`, `serviceBoostWeight = 2.0`,
  `attributeBoostWeight = 1.5`, `providerDistanceWeight = 1.25`,
  `providerMatchingVariantCountWeight = 0.5`

## Generic ES standard

ES mapping, ingestion, request, and response interpreters consume generic
`SearchDocumentSpec` / `SearchRuntimeSpec` / resolved constraints. BeautyQ-specific ES
compatibility lives in `BeautyQElasticsearchInterpreterAdapter`. Generic ES code must not know
BeautyQ names or app types.

ES remains primary/default. The `ElasticsearchSearchRequestInterpreter` reads behavior-affecting
values from the spec and does not encode BeautyQ semantics in local constants.

## Generic Qdrant standard

Qdrant semantic hit decoding, search, and indexing are generic over document/id/payload specs.
BeautyQ Qdrant wrappers map generic semantic hits back to existing BeautyQ shapes. Qdrant
supplement policy remains app-side for BeautyQ business eligibility. Qdrant is a constrained
local/test supplement only; it is not a fallback, not fusion, not rerank, and not the
production/default route.

## External contract preservation

The following contracts are preserved and must not be claimed changed unless a future task
explicitly changes them:

- Route/API JSON shape is unchanged.
- `SearchConstraint` JSON type strings are unchanged.
- ES request/source/mapping behavior is unchanged.
- Qdrant payload shape (`variantId`, `masterLocationId`, `serviceId`, `serviceName`) is unchanged.

## Testing standard

Focused proof tests cover:

- repo graph loading behavior (entity nodes, value sources, relations, traversal, deduplication)
- schema-owned projection (snapshot → document field derivation)
- document JSON and source JSON roundtrip
- payload derivation from schema-owned `SearchDocumentPayloadSpec`
- typed field handle resolution
- query schema resolution
- intent vocabulary and parser behavior locks
- runtime spec and fingerprint sensitivity (field/config changes trigger fingerprint change)
- generic ES module boundary tests (no BeautyQ names in generic ES code)
- generic Qdrant module boundary tests (no BeautyQ names in generic Qdrant code)
- app-side BeautyQ adapter behavior

Tests are not restricted to pure/in-memory plus ES integration only. Real-resource tests cover
repo graph loading, seed-scoped snapshot loaders, ES interpreter integration, and Qdrant
indexing/search integration as appropriate.

## ES V1 lexical boundary

ES V1 covers:

- direct service queries
- known multilingual structured aliases
- enum/boolean/int/decimal attribute queries
- known typo/noise cleanup
- exact commercial intent phrases
- facets, filters, grouping, and deterministic carousels

ES V1 does not cover:

- broad beauty intent without a stable service/entity signal
- conversational discovery queries
- semantic similarity outside structured intent vocabulary support
- unseen paraphrases that require embeddings

`q_broad_004` and `q_broad_006` remain outside the ES V1 lexical contract. They are candidates
for the Qdrant supplement candidate source, not a Qdrant-only retrieval path.

## Current eval coverage

- Current eval query counts are owned by `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` and
  checked-in eval resources.
- `q_broad_004`, `q_broad_006`: intentionally non-lexical; Qdrant supplement candidates only.

Qdrant supplement candidate eval (manual, environment-gated):

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
  sbt 'project bifunctor-tagless' \
  'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

## Combined architecture status

Per `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`:

- Elasticsearch V1 remains the deterministic lexical/filter/facet baseline and owns default route behavior.
- Qdrant is a constrained supplement candidate source only: it may append at most one candidate by
  default and must not remove/reorder ES baseline ids.
- There is no Qdrant-only search, no fallback, no score fusion, and no rerank.

## B-lite EngineEval model

Full API and metric semantics in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md`.

The pure `EngineEval` comparison model covers B-lite ES-native and Qdrant-native eval:

- `EngineEval.scala`: `EngineEvalEngine`, `EngineEvalQueryClass`, `EngineExpectedRole`,
  `EngineEvalResult`, `EngineEvalComparisonMetrics`, `EngineEvalComparisonMetrics.from(...)`.
- `EngineEvalSpec.scala`: pure metric semantics including duplicate-id behavior.

Key semantics: `EngineEvalQueryClass` is taxonomy metadata, not a current input to
`EngineEvalComparisonMetrics.from(...)`. `EngineExpectedRole` drives first-pass metrics. Metrics
count distinct variant ids by default. `qdrantNoiseCount` counts distinct Qdrant ids only when
expected role is `QdrantShouldStaySilent`.

ES-native and Qdrant-native engines are each designed from their own primitives. Product response
assembly is a projection over engine-native results.
