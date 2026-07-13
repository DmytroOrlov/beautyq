# BeautyQ Search Gen2 — evidence-backed review

Status: **static review of clean `HEAD`**  
Reviewed commit: `e1a9f03a8204ef54d2f82cae5b2e88946816fdc8`  
Evidence bundle: `beautyq-search-gen2-review-20260710-084938-29407.zip`  
Scope: source, tests, docs, build topology and recent Git history. No `sbt`, Docker, Elasticsearch, Qdrant, Postgres or HTTP probes were executed.

## Executive conclusion

The original architectural review is directionally correct. The repository already contains strong reusable pieces — typed document fields, explicit projection, generic ES/Qdrant JSON interpreters, catalog graph materialization, parser vocabulary, runtime fingerprinting and a measured no-harm supplement gate — but these pieces are not yet one executable, lossless Gen2 contract.

The central issue is not “missing DSL syntax”. It is that the same business behavior is still represented by several partially overlapping models and hand-written adapters:

1. normalized catalog topology;
2. repository loading/materialization;
3. denormalized search document;
4. document fields and payloads;
5. intent vocabulary and parser;
6. query-schema resolution;
7. ES request JSON;
8. ES hit-only response decoding;
9. BeautyQ in-memory response assembly;
10. Qdrant candidate retrieval and post-hydration eligibility;
11. a separate descriptive `SearchDomainSpec` projection;
12. readiness/eval/activation scaffolding.

Gen2 should therefore be framed as an **executable-contract and semantic-convergence migration**, not as a builder refactor.

## Review of the original claims

| Claim | Verdict | Evidence / correction |
|---|---|---|
| The system is not one business tree interpreted into all backends | Confirmed | `BeautyQSearchDeclarations` currently owns catalog + `VariantDocument`, while intent, facets, runtime, response and quality remain elsewhere. `beautyq-search-contract/.../BeautyQSearchDeclarations.scala:17-125`; `BeautySearchSpecV1.scala:6-73`. |
| Catalog declaration is not the full persistence model | Confirmed | The declaration contains read topology only. Seed insert order is separately hand-written. `BeautyQSearchDeclarations.scala:19-31`; `leaderboard-app-shell/.../BeautyQSeedInserter.scala:28-38`. |
| Seed-backed indexing does not round-trip through SQL/repositories | Confirmed | `fromSeedLoader` projects a snapshot constructed directly from `BeautyQSeedData`. `BeautySearchCatalogBackendModules.scala:141-157`. A repository-backed snapshot loader exists but is not used by this seed path. `BeautyQSearchCatalogSnapshotLoader.scala:28-54`. |
| Document projection is explicit business logic and should remain explicit | Confirmed | Joins, cross-master invariant, schema validation and text construction are hand-written. `BeautyQVariantSearchDocumentMaterialization.scala:49-127,130-183`. |
| Typed `Fields` are the strongest current source of truth | Confirmed with limitation | Fields carry extraction, semantic tags, flags, boost and analyzer. `SearchDsl.scala:77-88`. Mapping only consumes kind/analyzer, while query and response paths consume other subsets. `ElasticsearchMappingInterpreter.scala:20-47`. |
| Qdrant is a candidate source with a small payload | Confirmed | Payload is only `variantId`, `masterLocationId`, `serviceId`, `serviceName`. `BeautyQSearchDeclarations.scala:109-110`. Search JSON has vector, limit, payload flag and threshold, but no filter. `QdrantJsonInterpreter.scala:27-39`. |
| Parser does not use catalog dynamically | Confirmed | Parser consumes static `BeautySearchSpec.intentVocabulary`; no repository/catalog dependency. `BeautySearchIntentParser.scala:8-45`. |
| Public query names are largely metadata today | Confirmed | `SearchQuerySchema.field/fieldsByName` have no production caller outside tests; serving uses `resolve`, `facetConstraint` and `geoScoringField`. `SearchDsl.scala:404-415`; repository-wide production reference scan. |
| ES sends aggregations but response path ignores them | Confirmed | Request includes `aggs` and `track_total_hits`. `ElasticsearchSearchRequestInterpreter.scala:27-32,55-92`. Response decoder reads only hit score/source/matched queries. `ElasticsearchSearchResponseInterpreter.scala:26-57`. |
| Facets and carousels are recomputed from hit documents | Confirmed | `ElasticsearchSearchBackend` converts decoded hits to `ScoredDocument` and calls `SearchResponseAssembler`. `ElasticsearchSearchBackend.scala:54-70`. Facets/groups are counted in memory. `SearchResponseAssembler.scala:36-115,166-246`. |
| Request lacks structured filters | Confirmed | `UserSearchInput` contains only `query`, coordinates and `limit`. `BeautySearchModels.scala:13-18`. |
| Qdrant constraints are post-filtered | Confirmed | Candidates are retrieved first, hydrated, then checked against explicit constraints; top-1 is selected afterward. `QdrantVariantSupplementPolicy.scala:54-80,99-117`. |
| `SearchDomainSpec` is not the executable root | Confirmed, wording corrected | A full `SearchDomainSpec` is now formally assembled and marked ready. `BeautyQSearchDomainContract.scala:97-122`; `BeautyQSearchDomainSpecReadiness.scala:35-49`. However, it is a lossy descriptive projection and has no serving consumer. Intent mapping explicitly drops constraints/boosts/requires/excludes. `BeautyQSearchIntentSectionContract.scala:6-13`. Response mapping cannot represent two grouping policies. `BeautyQSearchResponsePolicyContract.scala:6-12,49-72`. Generic fields retain only name/kind. `search-contract-core/.../SearchField.scala:36-43`. |
| Historical/eval layers dominate the codebase | Confirmed quantitatively | `beautyq-search-wiring` has 23,817 main-source lines; 17,094 are under `search/eval`. 45 `M*.scala` files account for 15,617 lines. `leaderboard-app-shell` has 89,743 test lines. |

## Additional gaps found during verification

### G-01 — Backend response algebra is incomplete

Severity: **P0**

`ElasticsearchSearchRequestInterpreter` requests:

- exact total hits;
- facet aggregations;
- grouping aggregations.

`ElasticsearchSearchResponseInterpreter` discards all three and returns only hit documents. Consequently:

- facet counts are bounded by `hitWindowSize = 256`;
- provider/service grouping counts are bounded by the same window;
- total result count is unavailable to the API;
- ES performs work that serving ignores.

This is the highest-value first Gen2 vertical because it creates the missing backend result contract.

### G-02 — Search-index data is not guaranteed to reflect persisted state

Severity: **P0**

The local seed path performs two parallel actions from the same resource:

```text
seed -> SQL inserts
seed -> in-memory snapshot -> search documents -> ES/Qdrant
```

It does not prove:

```text
SQL/repositories -> catalog snapshot -> search documents -> indexes
```

A generic repository-backed loader exists, so the migration does not require inventing the loading model. It requires changing the index source and adding parity/freshness evidence.

### G-03 — No closed UI facet/filter loop

Severity: **P0**

The response exposes facets, but the request cannot return selected facets as typed filters. Public query field names therefore do not yet define an actual client contract.

The missing loop is:

```text
facet descriptor -> client selection -> typed request filter
-> domain constraint -> backend filter -> selected/applied filter response
```

### G-04 — Constraint semantics diverge between paths

Severity: **P0**

Examples:

1. `SearchConstraint.PriceRange` resolves to a range over `priceFrom` only. `BeautyQSearchDeclarations.scala:152-155`.
2. Qdrant supplement eligibility uses interval overlap across `priceFrom` and `priceTo`. `QdrantVariantSupplementPolicy.scala:109-116`.
3. In-memory range matching uses inclusive `min <= value <= max`. `SearchSpecSupport.scala:116-120` and `SearchResponseAssembler.scala:223-227`.
4. ES aggregation JSON carries only `from`/`to`; the DSL does not encode boundary inclusivity. `ElasticsearchSearchRequestInterpreter.scala:69-83`.
5. Geo scoring is activated by the presence of coordinates, while `NearUser` itself resolves to an empty hard-filter clause. `ElasticsearchSearchRequestInterpreter.scala:94-145`.

Gen2 must define semantics once before adding more backends.

### G-05 — `SearchDomainSpec` readiness overstates executability

Severity: **P1**

The current readiness test proves that every descriptive section has been populated, not that one contract can be compiled into serving behavior.

Losses include:

- field extractor;
- field semantic;
- searchable/filterable/facetable/sortable flags;
- boost and analyzer;
- intent constraints and contextual rules;
- two independent carousel grouping policies;
- backend-specific mapping/query policy;
- request and response codecs;
- result assembly behavior.

The name “full generic SearchDomainSpec declared” is true structurally, but can be misread as “full executable domain contract complete”.

### G-06 — Backend capability flags are descriptive booleans

Severity: **P1**

`SearchBackendCapabilities` declares only broad booleans. It cannot express:

- which constraint types a backend can compile;
- which payload fields are indexed;
- supported facet/group modes;
- pagination model;
- score semantics;
- degradation behavior;
- hydration requirement.

Gen2 needs compiler capabilities and explicit unsupported-result errors, not only capability labels.

### G-07 — Field flags are not enforced as invariants

Severity: **P1**

A `SearchField` can be marked `filterable`, `facetable` or `sortable`, but current builders/interpreters do not reject an incompatible use. Mapping also does not derive backend policy from these flags beyond kind/analyzer.

Required Gen2 checks include:

- a facet must reference a facetable field;
- a range constraint must reference a numeric/range-capable field;
- sort must reference a sortable field;
- Qdrant filter projection must include every field used by a Qdrant-compiled hard constraint;
- searchable text must have backend text policy.

### G-08 — Duplicate ES BeautyQ adaptation paths

Severity: **P1**

`ElasticsearchSearchBackend` and `BeautyQElasticsearchInterpreterAdapter` both:

- resolve constraints;
- construct `ElasticsearchSearchInput`;
- decode hits;
- create `ScoredDocument`;
- invoke `SearchResponseAssembler`.

Evidence: `ElasticsearchSearchBackend.scala:27-70`; `BeautyQElasticsearchInterpreterAdapter.scala:29-73,85-95`.

This makes parity dependent on duplicate maintenance.

### G-09 — Qdrant filtering cannot be complete with current payload

Severity: **P1**

The payload omits category, price, duration, geo and dynamic attributes. Even after adding a generic Qdrant filter interpreter, most BeautyQ constraints cannot be pushed down until the payload/index contract expands.

### G-10 — Index lifecycle is bootstrap-oriented, not synchronization-oriented

Severity: **P1**

Managed-local bootstrap can reuse or rebuild resources, but the rebuild path deletes and recreates the index/collection. Production/provided startup intentionally does not index. There is no versioned index generation, alias switch, source version, incremental update, outbox or CDC boundary.

Gen2 should first introduce a versioned snapshot/reindex contract; CDC can remain a later implementation.

### G-11 — Generic DSL risks becoming a lowest-common-denominator

Severity: **P1**

Current `SearchRuntimeSpec` combines common document/query/facet/carousel data with optional embedding/vector specs. ES-specific analysis/rescore/highlighting/search-after and Qdrant-specific payload indexes/filter/oversampling policies have no typed owner.

The solution is not raw JSON in business code. It is a common semantic plan plus typed backend policy sections.

### G-12 — Compatibility aliases still obscure ownership

Severity: **P2**

Recent commits centralized canonical declarations, but production code still commonly reaches them through:

- `BeautyQCatalogDeclaration`;
- `BeautyQVariantSearchDocumentContract`;
- top-level `BeautyQSearchDeclarations.querySchema`.

For example, `BeautyQCatalogGraph` imports the catalog facade and `BeautySearchSpecV1` imports the document facade. This is acceptable during migration but should have an explicit deletion milestone.

### G-13 — Applied filter naming is misleading

Severity: **P2**

`BeautySearchResponse.inferredFilters` includes explicit parser constraints first and inferred filters second. `SearchResponseAssembler.scala:117-135`; `BeautySearchModels.scala:271-279`.

The internal Gen2 name should be `appliedFilters`, with origin metadata. V1 JSON can keep `inferredFilters` through an adapter until an API version change.

### G-14 — Intent vocabulary is coupled to display names

Severity: **P2**

Service/category constraints use names, so renames can drift across seed/catalog, vocabulary and eval data. Gen2 should resolve aliases to stable service/category IDs or codes, then use names only for display.

### G-15 — Pagination, sorting and result totals are absent from the public request/response

Severity: **P2**

The current API supports only `limit`. This blocks reliable browsing and makes backend-specific pagination impossible to model.

## Current architecture that should be preserved

Gen2 must not discard the strongest Gen1 decisions:

1. **Normalized catalog and denormalized search document remain separate.**
2. **Projection remains explicit business code.** Macro derivation may remove mechanical selectors but must not infer joins/text composition/invariants.
3. **Generic backend modules remain domain-free.** No BeautyQ names in ES/Qdrant compilers.
4. **Typed field handles remain canonical.** No raw field-path duplication in domain policies.
5. **ES remains baseline during migration.** Qdrant cannot remove/reorder baseline results until a separately approved policy says otherwise.
6. **Quality gates remain evidence, not route activation.**
7. **V1 route JSON remains stable until a versioned API migration.**

## Recommended target boundary

```text
BeautyQSearchDomainV2
├── catalog
│   ├── topology
│   └── snapshotSource
└── searches
    └── variants
        ├── document
        │   ├── fields
        │   ├── projection
        │   └── payload projections
        ├── request
        │   ├── public fields
        │   ├── structured filters
        │   ├── parser
        │   └── plan compiler
        ├── response
        │   ├── hits
        │   ├── facets
        │   ├── groups
        │   └── applied-filter policy
        ├── backends
        │   ├── elasticsearch policy
        │   └── qdrant policy
        └── quality
            ├── corpus
            ├── metrics
            └── gates
```

The root is executable only when interpreters consume these exact typed sections. Any flattened/descriptive documentation view must be generated from this root and must not become a second source of truth.

## Decision

Proceed with Gen2 in small vertical slices. Do **not** begin by replacing every V1 DTO or introducing a large builder. The first implementation milestone should be typed ES response decoding and a generic `BackendSearchResult`, because it closes a real correctness gap while establishing the abstraction all later iterations need.
