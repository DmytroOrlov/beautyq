# BeautyQ Search Gen2 — evidence-backed review

Status: **source review retained; delivery strategy corrected for side-by-side Gen2**
Source evidence commit: `e1a9f03a8204ef54d2f82cae5b2e88946816fdc8`
Planning revision baseline: clean `923cdfac` (the intervening change contains only the four Gen2 documents)
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

Gen2 should therefore be framed as an **independent executable-contract rebuild with semantic convergence**, not as a builder refactor and not as a gradual V1 runtime migration.

The correct delivery model is side-by-side construction in a new module DAG, followed by one final cutover and Gen1 deletion.

## Review of the original claims

| Claim | Verdict | Evidence / correction |
|---|---|---|
| The system is not one business tree interpreted into all backends | Confirmed | The Gen1 `beautyq-search-contract` `BeautyQSearchDeclarations` owns catalog + `VariantDocument`, while intent, facets, runtime, response and quality remain elsewhere. `beautyq-search-contract/.../BeautyQSearchDeclarations.scala:17-125`; `BeautySearchSpecV1.scala:6-73`. |
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

This is a high-value Gen2 correctness vertical, but it is not the first code change. The module firewall, generic role-specific result algebra and BeautyQ Gen2 root must exist first. Elasticsearch then implements `FullSearchResult`; Qdrant does not share that output contract.

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

A generic repository-backed loader exists in Gen1 and proves the loading concept, but Gen2 must implement its own repository-backed consistent snapshot in the new materialization module. The Gen2 index source is repository state from the beginning; no V1 index-source migration is required.

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

For example, `BeautyQCatalogGraph` imports the catalog facade and `BeautySearchSpecV1` imports the document facade. These aliases remain a Gen1 concern while Gen2 is built independently. They are removed together with the rest of Gen1 in the final cutover change set, not gradually deprecated through Gen2 adapters.

### G-13 — Applied filter naming is misleading

Severity: **P2**

`BeautySearchResponse.inferredFilters` includes explicit parser constraints first and inferred filters second. `SearchResponseAssembler.scala:117-135`; `BeautySearchModels.scala:271-279`.

The Gen2 name is `appliedFilters`, with origin metadata. Gen2 defines its own request/response contract directly; no V1 codec adapter is required before the final cutover.

### G-14 — Intent vocabulary is coupled to display names

Severity: **P2**

Service/category constraints use names, so renames can drift across seed/catalog, vocabulary and eval data. Gen2 should resolve aliases to stable service/category IDs or codes, then use names only for display.

### G-15 — Pagination, sorting and result totals are absent from the public request/response

Severity: **P0**

The current API supports only `limit`. This blocks reliable browsing and makes backend-specific pagination impossible to model. Pagination is also inseparable from supplement correctness: without a cursor and an explicit first-page policy, a semantically appended result may reappear on a later Elasticsearch page or violate a non-relevance sort.


### G-16 — The original implementation plan encodes the wrong delivery strategy

Severity: **P0 planning blocker**

The first plan describes vertical ownership migration inside Gen1: shadow decoding, V1-to-V2 adapters, rollback bindings, compatibility aliases and late root creation. That is appropriate for a live production migration, but it is unnecessary here and delays the architectural result.

Gen2 must be built in new modules while V1 remains unchanged. There is exactly one migration moment: final application cutover plus Gen1 deletion.

### G-17 — Gen2 cannot be placed in Gen1 modules

Severity: **P0 planning blocker**

Adding `SearchPlan`, result algebra and compilers to `search-contract-core`, `search-core`, `search-elasticsearch`, `search-qdrant` and existing BeautyQ modules creates a hidden dependency on the architecture that Gen2 is supposed to replace.

A separate sbt DAG and an automated module/import firewall are required before semantic implementation begins.

### G-18 — One backend result type would collapse different backend roles

Severity: **P0 design blocker**

Elasticsearch owns a full baseline result: hits, totals, facets, groups and page state. Qdrant owns candidate IDs and semantic scores only.

A single `BackendSearchResult` either becomes a lowest-common-denominator structure or accumulates permanently unsupported sections. Gen2 needs `FullSearchResult` and `CandidateSearchResult`, with a compiled `CandidatePlan` for Qdrant.

### G-19 — Price facets require interval-overlap semantics

Severity: **P0 semantic blocker**

If offers are intervals `[priceFrom, priceTo]`, a half-open bucket `[min,max)` matches with:

```text
priceFrom < max AND priceTo >= min
```

A normal range aggregation over `priceFrom` is incorrect. Gen2 needs an explicit `IntervalOverlapFacet` compiled to a filters aggregation in Elasticsearch and two payload conditions in Qdrant.

### G-20 — Geo behavior is three operations, not one optional-radius constraint

Severity: **P0 semantic blocker**

Proximity scoring, radius filtering and distance sorting have different user-visible meanings. Coordinates alone must not activate any of them. `NearUser` should produce an explicit soft proximity signal; hard radius and distance sort remain explicit request operations.

### G-21 — Existing ES group aggregation is insufficient for UI carousels

Severity: **P0 design blocker**

A plain `terms` aggregation returns only group key and count. BeautyQ provider/service carousels also require representative document data, best score, sample IDs and potentially distance/proximity metrics.

Group semantics and ordering must be declared separately and compiled to `top_hits`/`top_metrics` plus required metrics, or to a dedicated secondary group query. This is not merely a response-decoder task.

### G-22 — Snapshot versioning needs transactional consistency and stable content identity

Severity: **P0 correctness blocker**

A `loadVersioned()` method does not guarantee that multiple repository reads observe one database state. Gen2 needs one repeatable-read snapshot boundary and separate metadata for content fingerprint, source revision and capture time. `capturedAt` must not affect the content fingerprint.

### G-23 — Quality data must not become part of the serving root/classpath

Severity: **P1**

The visible domain tree may reference corpus IDs, metrics and gates, but the corpus, reports and historical tooling belong in `beautyq-search-gen2-eval`. Otherwise Gen2 repeats the Gen1 problem where evaluation history dominates runtime ownership.

## Current architecture that should be preserved

Gen2 must not discard the strongest Gen1 decisions:

1. **Normalized catalog and denormalized search document remain separate.**
2. **Projection remains explicit business code.** Macro derivation may remove mechanical selectors but must not infer joins/text composition/invariants.
3. **Generic backend modules remain domain-free.** No BeautyQ names in ES/Qdrant compilers.
4. **Typed field handles remain canonical.** No raw field-path duplication in domain policies.
5. **Elasticsearch remains the full-result baseline.** Qdrant is candidate-only and cannot remove or reorder baseline hits under the initial policy.
6. **Quality gates remain evidence, not route activation.**
7. **V1 remains untouched until the one final cutover.** V1 behavior is historical evidence, not a mandatory Gen2 semantic oracle.

## Recommended target boundary

### Independent module DAG

```text
search-gen2-contract
        ↓
search-gen2-core
   ┌────┴────────────────┐
   ↓                     ↓
search-gen2-elasticsearch  search-gen2-qdrant

beautyq-search-gen2-contract
        ↓
beautyq-search-gen2-materialization
        ↓
beautyq-search-gen2-wiring

beautyq-search-gen2-eval
```

No Gen2 module may depend on a Gen1 search module. Reusable clients must live in a neutral transport module or be implemented independently in Gen2.

### Executable BeautyQ root

```text
BeautyQSearchDeclarations
├── catalog
│   ├── topology
│   ├── snapshotPolicy
│   └── validation
└── variants
    ├── identity
    ├── Fields
    ├── document
    ├── request
    ├── intent
    ├── plan
    ├── facets
    ├── groups
    ├── response
    ├── backends
    │   ├── elasticsearch
    │   └── qdrant
    └── quality references
```

The root is executable only when Gen2 runtime services consume these exact typed sections. Any descriptive documentation view is generated from the root and cannot become a second source of truth.

The quality branch stores references and gate policy; corpora and reports remain in the eval module.

## Decision

Proceed with an independent side-by-side Gen2 build.

The implementation order is:

1. accept semantics and establish the module/import firewall;
2. build the generic Gen2 algebra in new modules;
3. create the BeautyQ Gen2 root early;
4. implement repository snapshot and explicit projection;
5. implement the real Gen2 request, parser and plan compiler;
6. complete the ES full-result vertical;
7. complete the Qdrant candidate vertical;
8. implement baseline-plus-supplement orchestration;
9. run and evaluate Gen2 independently;
10. perform one cutover and delete Gen1.

Do not add Gen2 abstractions to Gen1 modules, do not create production V1-to-V2 adapters, and do not switch runtime ownership incrementally.
