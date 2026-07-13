# BeautyQ Search Framework Gen2 — side-by-side build plan

Status: **approved delivery strategy; implementation in progress**
Planning unit: one reviewable brick, usually one commit or a small cohesive commit series
Migration moments: exactly one — the final cutover

Normative companion documents:

- [technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) for end-state types, data paths,
  backend roles and Definition of Done;
- [semantics ADR](BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md) for accepted business/search decisions;
- [evidence-backed Gen1 review](BEAUTYQ_SEARCH_GEN2_REVIEW.md) for the source-confirmed problems Gen2
  addresses;
- [reusable-framework scope](SEARCH_GEN2_FRAMEWORK_SCOPE.md) for what `search-gen2-contract`/
  `search-gen2-core` actually support beyond the BeautyQ extraction, calibrated by an executable
  non-BeautyQ tracer - supported shapes, tracked gaps, resolved checkpoints and non-goals.
- [domain authoring principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md) for the repository-wide
  business-policy, reuse, and executable-source-of-truth acceptance contract.

## Current implementation state

This section is the single authoritative live status owner for Gen2 implementation. Update it in
the same commit that starts, completes, blocks, or materially re-scopes a brick. Root and docs-index
summaries must remain short and point here.

- **Overall:** implementation in progress
- **Active brick:** Brick 4G — semantic query text, `CandidatePlan` and cursor validation over the
  compiled `SearchPlan`
- **Completed bricks:**
  - Brick 0 — module DAG and firewall
  - Brick 1 — generic field/document declaration kernel
  - Brick 2 — stable BeautyQ identities and executable Variant declaration root
  - Brick 3 — consistent repository snapshot and explicit projection
- **Completed cross-cutting slices:**
  - Commit 3C — low-boilerplate domain authoring DSL
  - Commit 3D — reusable materialization kernel extraction
  - Commit 3E — canonical source-row authoring and generic result aliases
  - Commit 3F — cross-brick reusable authoring audit closeout
  - Commit 3G — reusable-framework generality checkpoint: a non-BeautyQ-shaped executable tracer
    exercised through the complete declaration/materialization path owned by `search-gen2-contract`/
    `search-gen2-core`; see
    [reusable-framework scope](SEARCH_GEN2_FRAMEWORK_SCOPE.md) for the resulting gap ledger
  - Brick 4A — constraint, signal and sort algebra: `PlannedConstraint`/`PlannedSignal`/`PlannedSort`
    plus `Bound`/`RangeBounds`, with deterministic diagnostics and an explicit-enum-label algebra trace
  - Brick 4B — `SearchPlan`, facets, groups, page, provenance and diagnostics: typed facet/group/page/
    provenance/diagnostic values, `SearchPlan` validation and its diagnostic generated view
  - Brick 4C — `PlanIdentity` and the cursor envelope: the value-only projection from a validated
    `SearchPlan`, versioned canonical identity encoding, the SHA-256 `PlanIdentityHash`, and the
    versioned cursor envelope
  - Brick 4D+4E — public input and intent interpretation: generic public-input registries and intent
    matching primitives, plus the BeautyQ public input contract, stable-code vocabulary and
    deterministic intent adapter; request and parsed intent remain separate inbound values until 4F
  - Brick 4F — plan compilation: generic canonical-constraint/precedence/geo-input-resolution/
    facet-registry/plan-compilation mechanics, plus the BeautyQ plan policy, compiler and diagnostic
    trace that compose them into a validated `SearchPlan[VariantSearchDocumentGen2]`; semantic query
    text, `CandidatePlan` and cursor validation remain out of scope until 4G

  Compact Brick 4 status:

  ```text
  4A completed
  4B completed
  4C completed
  4D completed
  4E completed
  4F completed
  4G active
  ```
- **Authoring facade:** `BeautyQSearchDeclarations` is the canonical Gen2 business entry point.
  Read `catalog`, then `variants.Fields`, `variants.document`, `variants.request`, `variants.intent`
  and `variants.plan`; projection/materialization stays in its owning module because the DAG must not
  reverse-depend from the contract layer.
- **Next action:** Brick 4G — compile semantic query text and `CandidatePlan` from the validated
  `SearchPlan`, and validate inbound cursors against `PlanIdentity`; backend/route wiring remain later
  bricks
- **Blockers:** none

Every brick that adds domain policy or reusable mechanics must satisfy the
[Domain Authoring Principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md). A brick is not
framework-complete merely because BeautyQ works: domain differences must remain explicit, reusable
mechanics must be reused or extracted, neutral proof must exist, and generated views must derive
from one executable declaration. If the current implementation still has an extraction gap, name its
owner and next boundary in this plan or in the framework-scope ledger instead of claiming the gap is
already generic.

Future brick closeouts add this compact authoring result:

```text
Domain policy added:
Framework mechanics reused or extracted:
Neutral proof:
Canonical entry-point update:
Derived-view proof:
```

Brick 3 is delivered in `beautyq-search-gen2-materialization`: the PostgreSQL source reads all required
tables through one repeatable-read, read-only boundary, reuses the repository decoders for stored
schemas and variant attributes, rejects orphan/invalid stored rows instead of silently skipping them,
and emits a versioned source snapshot with a canonical content fingerprint. The explicit Variant
projection validates joins/schema ownership, resolves stable codes,
normalizes attributes/text, accumulates deterministic errors, orders documents canonically, and emits a
projected-document fingerprint from the declared `variants.document.allFields`. No Gen1 materialization,
seed-as-serving-source, backend, request, or runtime wiring was added. Brick 3's reusable boundary is
now closed: `search-gen2-core` owns snapshot/fingerprint/materialization mechanics, typed canonical-row
authoring, snapshot-product traversal, canonical indexing and ordered token assembly. The contract DSL
owns exhaustive product coverage and one structural renderer/root tree. BeautyQ owns transaction-local
SQL acquisition, snapshot shape, persisted-data validation, source field/value selection, joins and
projection errors, attribute/text policy and encoding/version constants.

Commit 3G addressed a different risk than 3D-3F: 3D-3F extracted mechanics that BeautyQ had already
proven for one domain, but a kernel extracted from exactly one consumer naturally takes that
consumer's shape. 3G added a deliberately non-BeautyQ-shaped executable tracer
(`search-gen2-core/src/test/scala/leaderboard/search/gen2/core/tracer/LibraryTracerDomain.scala`) and
ran it through the complete declaration/materialization path owned by the two generic Search Gen2
modules. It expanded the supported kernel shapes with `Long`/date-time values, dynamic `Text` families
and singleton snapshot sources. It also discovered a genuine Scala/JVM nested-object initialization
hazard in the `Fields`/outer-alias authoring pattern, avoided it in the supported tracer shape and
recorded it as an open authoring hazard because the framework does not yet prevent it constructively.
It left two other gaps deliberately open rather than freehanding under time pressure:
a multi-valued (`Vector[A]`) field (intersects Brick 4's constraint algebra) and a canonical-String-
wrapper base that is reusable in name only (`private[model]`, outside the two generic modules' scope).
The tracer is an executable calibration fixture, not a second production consumer. It deliberately
does not duplicate catalog topology: the neutral catalog algebra is a separate `repo-core` boundary
proven by its own contract suites, while `search-gen2-core` must not acquire a reverse dependency on
`repo-core`. Full detail, evidence and the current gap ledger live in
[`SEARCH_GEN2_FRAMEWORK_SCOPE.md`](SEARCH_GEN2_FRAMEWORK_SCOPE.md); this paragraph is a pointer, not a
duplicate status owner.

## Coordinator starting model

The next coordinator must preserve this distinction:

1. `catalog` describes normalized persisted business facts and source topology;
2. `variants.document` is an explicit denormalized read model built from a consistent snapshot;
3. `variants.intent` and `SearchPlan` describe requested operations over document fields and are not
   another stored copy of the catalog.

`BeautyQSearchDeclarations` is one business-visible facade with two top-level branches:

```text
BeautyQSearchDeclarations
├── catalog       normalized topology/snapshot requirements
└── variants      document-centred search contract plus executable request/intent inventories
```

The shared bricks across the boundary are stable typed values (`ServiceCode`, `CategoryCode`, IDs,
money, duration and geo values), not normalized entity case classes or paths. Explicit projection
connects snapshot entities to `VariantSearchDocumentGen2`. Stable codes connect catalog identity,
document values and intent vocabulary.

Within the search branch, `BeautyQSearchDeclarations.variants.Fields` is the semantic hub. The exact
typed handles declared there are reused by document, constraints, facets, groups, sorts, ES policy,
Qdrant payload/filter policy and response descriptions. No downstream section recreates a field or
derives a public query name from `SearchField.path`.

The completed 4D+4E inbound boundary is visible under the same branch: `variants.request` exposes
the authoritative public filter/sort/facet registries and `variants.intent` exposes the validated
stable-code vocabulary. Generic lookup/operator gating, ordered sort/facet inventories and the
longest-match/contextual/overlay engine live in `search-gen2-contract`; BeautyQ declares only public
names, typed field policy, stable actions, aliases and domain translation/label policy. The validated
request assigns `ExplicitUi`/`FacetSelection` provenance on the server; the parser emits `ParsedHard`
constraints, canonical semantic labels and the one `GeoProximitySignal`. Noise without contextual
requirements remains in the independent matching phase; the Gen2-only NearUser rule is an explicit
semantic overlay with a hair-removal exclusion. Neither branch constructs a `SearchPlan`, resolves
geo origins, applies precedence or talks to a backend.

The canonical root intentionally stops at the contract-module boundary. Projection and materialization
are implemented in `beautyq-search-gen2-materialization`, whose next files to read are
`BeautyQSearchSnapshotSource`, `BeautyQSnapshotCanonicalRows` and `BeautyQVariantProjectionGen2`;
they consume this root but cannot be nested
under it without reversing the module DAG. This is a dependency boundary, not a second business root.

The implementation must not begin by rebuilding catalog topology as an isolated vertical. Catalog
cannot determine document joins, normalization, text/embedding composition, price-overlap semantics,
geo operations, public names or backend capabilities. The visible catalog branch is added early via
the neutral `repo-core` algebra, while the first search DSL establishes the document/field kernel.

## Completed opening commit sequence

The opening implementation stages were deliberately smaller than the later bricks so every diff was
human reviewable. Stage 3 was split into two commits because shared schema/persistence changes and
the BeautyQ declaration root are different risk layers.

### Commit 1 — module DAG and firewall (`Brick 0`)

- add the eight empty/minimal Gen2 projects;
- enforce allowed dependencies and forbidden Gen1 imports;
- reject BeautyQ names in generic modules and eval dependencies from serving;
- reserve separate Gen2 backend namespaces;
- change no search behavior.

The review artifact is the explicit module graph plus focused positive/negative firewall proof.

### Commit 2 — generic field/document declaration kernel (`Brick 1`)

Add only the minimum domain-neutral algebra needed to describe a typed search document:

- `FieldId`, `FieldPath`, value codecs and `FieldCapabilities`;
- `SearchField[Document, Value]`;
- immutable additive field/document builders that finish as ordinary contract values;
- capability/uniqueness/order validation;
- deterministic human-readable structural rendering;
- neutral sample-document tests, including wrong-document compile rejection.

Do not add BeautyQ, ES/Qdrant JSON, request parsing, `SearchPlan`, backend result types, cursor logic or
supplement policy in this commit.

### Commit 3A — shared stable identities (`Brick 2` prerequisite)

- add accepted `ServiceCode`/`CategoryCode` model, persistence, seed and repository changes;
- add uniqueness/unknown-code and explicit shared API-schema assertions;
- change no Gen1 search declaration or runtime behavior;
- add no Gen2 root or search field declaration.

This commit is reviewed as a shared domain/persistence/API schema change, not as a search DSL diff.

### Commit 3B — BeautyQ root and complete Variant fields (`Brick 2` declaration slice)

- create `BeautyQSearchDeclarations` with visible `catalog` and `variants` branches;
- add `VariantSearchDocumentGen2`;
- declare the complete `variants.Fields` set and document declaration using the generic kernel;
- generate the deterministic BeautyQ declaration tree and assert the same structure directly;
- add no parser, backend client or runtime route.

The initial root contains only real implemented sections. Future branches are added additively; do not
insert fake placeholder values merely to render the final target tree.

### Order after `Fields`

After Commit 3B, proceed in this dependency order:

```text
stable business identities
  -> VariantDocument.Fields
  -> document declaration
  -> consistent snapshot and explicit projection
  -> public request declarations
  -> intent vocabulary and resolver
  -> normalized SearchPlan and PlanIdentity
  -> facets, groups and sorts
  -> Elasticsearch full-result vertical
  -> Qdrant candidate-only vertical
  -> baseline-plus-supplement orchestration
  -> public response and independent application composition
```

Generic query/plan/result primitives are added just before the first domain or backend slice that uses
them and are proved with neutral fixtures. This avoids a speculative, oversized generic-algebra diff
while preserving the accepted end-state types in the technical specification.

Each new stage adds two readable proofs:

1. structural assertions over typed values and invariants;
2. a deterministic generated tree/table or scenario trace that a business reviewer can read.

Scenario traces grow along the executable path:

```text
public request -> parsed intent -> SearchPlan -> ES FullPlan / Qdrant CandidatePlan
               -> typed backend results -> BeautyQ response
```

Generated views are derived from executable declarations. They never become a manually maintained
second source of truth.

## Gen1 evidence and reuse map

Gen1 is evidence, not a dependency shortcut. Every reference below has one of three meanings:

- **direct reuse** — the code already lives in an allowed neutral/shared module;
- **evidence only** — read behavior and tests, then implement the accepted Gen2 contract in new
  modules without importing the Gen1 symbol;
- **extract first** — reuse is allowed only after moving a genuinely neutral transport boundary into
  a new neutral module; the extraction must not pull a Gen1 interpreter or BeautyQ policy with it.

An unlabelled Gen1 source reference never authorizes a dependency. The module/import firewall remains
authoritative.

### Allowed direct reuse

| Existing neutral source | Gen2 use | Limit |
|---|---|---|
| [`repo-core` catalog declaration and relation algebra](../../repo-core/src/main/scala/leaderboard/repo/RepoGraph.scala) | `beautyq-search-gen2-contract.catalog` and materialized relation evidence | Reuse the algebra, not the Gen1 `beautyq-search-contract` declarations or `BeautyQCatalogGraph`. |
| [`CatalogLoadedGraph`](../../repo-core/src/main/scala/leaderboard/repo/CatalogLoadedGraph.scala) and [`CatalogSnapshotAssembly`](../../repo-core/src/main/scala/leaderboard/repo/CatalogSnapshotAssembly.scala) | load a declared relation tuple and assemble typed snapshot values | These helpers do not create a repeatable-read transaction; Gen2 must provide that boundary. |
| [`RepoSnapshotProjection`](../../repo-core/src/main/scala/leaderboard/repo/RepoSnapshotProjection.scala) | pure indexing, required joins, invariant checks and deterministic root projection | Reuse helpers; keep BeautyQ joins, normalization and text construction explicit in Gen2 materialization. |
| `beautyq-model` and `beautyq-search-repositories` | shared domain values, accepted stable codes and repository interfaces | These are approved shared foundations, not permission to import any BeautyQ Gen1 search module. |

Primary neutral proofs to read before changing catalog/materialization behavior:

- [`CatalogRelationEvidenceDerivationSpec`](../../repo-core/src/test/scala/leaderboard/repo/CatalogRelationEvidenceDerivationSpec.scala);
- [`CatalogLoadedGraphSpec`](../../repo-core/src/test/scala/leaderboard/repo/CatalogLoadedGraphSpec.scala);
- [`CatalogSnapshotAssemblySpec`](../../repo-core/src/test/scala/leaderboard/repo/CatalogSnapshotAssemblySpec.scala);
- [`RepoSnapshotProjectionSpec`](../../repo-core/src/test/scala/leaderboard/repo/RepoSnapshotProjectionSpec.scala);
- [`GraphLoadingSpec`](../../repo-core/src/test/scala/leaderboard/repo/GraphLoadingSpec.scala).

### Evidence-only reading by implementation stage

| Stage | Read before coding | What to preserve or deliberately replace |
|---|---|---|
| Brick 0 firewall | [`SearchModuleBoundaryGuardrailSpec`](../../search-contract-core/src/test/scala/leaderboard/search/contract/SearchModuleBoundaryGuardrailSpec.scala), [`BeautySearchAppGraphBoundarySpec`](../../leaderboard-app-shell/src/test/scala/leaderboard/search/BeautySearchAppGraphBoundarySpec.scala) | Preserve the idea of executable positive/negative boundaries and targeted test modules. Do not copy the large Gen1 forbidden list blindly or include a whole production plugin graph. |
| Brick 1 field/document kernel | [`SearchDsl.SearchField`](../../search-core/src/main/scala/leaderboard/search/dsl/SearchDsl.scala), [`SearchFieldDerivationSpec`](../../search-core/src/test/scala/leaderboard/search/SearchFieldDerivationSpec.scala), [`SearchQuerySchemaBuilderSpec`](../../search-core/src/test/scala/leaderboard/search/SearchQuerySchemaBuilderSpec.scala) | Preserve typed document ownership, immutable fluent accumulation, field order, explicit public names and wrong-document compile rejection. Replace boolean capabilities and path-based identity with the accepted Gen2 types. |
| Brick 2 BeautyQ root/fields | [`BeautyQSearchDeclarations`](../../beautyq-search-contract/src/main/scala/leaderboard/search/beautyq/contract/BeautyQSearchDeclarations.scala), [`VariantSearchDocument`](../../beautyq-search-contract/src/main/scala/leaderboard/search/document/VariantSearchDocument.scala), [`BeautyQCatalogDeclarationSpec`](../../beautyq-search-contract/src/test/scala/leaderboard/search/beautyq/contract/BeautyQCatalogDeclarationSpec.scala), [`BeautyQDocumentContractSpec`](../../beautyq-search-contract/src/test/scala/leaderboard/search/beautyq/contract/BeautyQDocumentContractSpec.scala), [Gen1 DSL ownership notes](../beautyq-search-dsl-v1.md) | Use as the catalog/field/value/text/payload inventory and historical ownership evidence. Do not import aliases, copy name-based service/category identity or preserve V1 shape when the ADR intentionally changes it. |
| Brick 3 snapshot/projection | [`BeautyQCatalogGraph`](../../beautyq-search-materialization/src/main/scala/leaderboard/repo/BeautyQCatalogGraph.scala), [`BeautyQSearchCatalogSnapshotLoader`](../../beautyq-search-materialization/src/main/scala/leaderboard/search/document/BeautyQSearchCatalogSnapshotLoader.scala), [`BeautyQVariantSearchDocumentMaterialization`](../../beautyq-search-materialization/src/main/scala/leaderboard/search/document/BeautyQVariantSearchDocumentMaterialization.scala), [`BeautyQRepoGraphLoaderSpec`](../../leaderboard-app-shell/src/test/scala/leaderboard/repo/BeautyQRepoGraphLoaderSpec.scala), [`BeautyQVariantSearchDocumentContractProjectionSpec`](../../leaderboard-app-shell/src/test/scala/leaderboard/search/BeautyQVariantSearchDocumentContractProjectionSpec.scala) | Preserve the pure-declaration/materialized-evidence split, explicit joins, cross-owner checks, schema validation, attribute normalization and text-composition evidence. Import only the neutral `repo-core` algebra; replace Gen1 BeautyQ graph/loader classes and separate repository reads with the Gen2 root plus one consistent snapshot boundary. |
| Brick 4 request/intent/plan | [`BeautyQSearchIntentVocabulary`](../../beautyq-search-contract/src/main/scala/leaderboard/search/dsl/BeautyQSearchIntentVocabulary.scala), [`BeautySearchIntentParser`](../../beautyq-search-wiring/src/main/scala/leaderboard/search/parser/BeautySearchIntentParser.scala), [`SearchSpecSupport`](../../beautyq-search-wiring/src/main/scala/leaderboard/search/interpreter/SearchSpecSupport.scala), [`BeautySearchSpecV1`](../../beautyq-search-contract/src/main/scala/leaderboard/search/dsl/BeautySearchSpecV1.scala) | Preserve proven aliases and scenario evidence where still valid. Replace display-name identity, implicit provenance, `priceFrom`-only ranges and coordinate-driven implicit geo behavior with the accepted ADR. |
| Brick 5 Elasticsearch | [`ElasticsearchMappingInterpreter`](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchMappingInterpreter.scala), [`ElasticsearchSearchRequestInterpreter`](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchSearchRequestInterpreter.scala), [`ElasticsearchSearchResponseInterpreter`](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchSearchResponseInterpreter.scala) and their [focused tests](../../search-elasticsearch/src/test/scala/leaderboard/search/ElasticsearchSearchRequestInterpreterSpec.scala) | Reuse JSON shapes only as evidence. Gen2 must add capability validation, exact mapping policy, interval-overlap facets, explicit geo operations, cursor/search-after, complete totals/facets/groups decoding and typed missing-section failures. |
| Brick 6 Qdrant | [`QdrantDocumentPointBuilder`](../../search-qdrant/src/main/scala/leaderboard/search/qdrant/QdrantDocumentPointBuilder.scala), [`QdrantJsonInterpreter`](../../search-qdrant/src/main/scala/leaderboard/search/qdrant/QdrantJsonInterpreter.scala), [`QdrantCollectionCompatibilityValidator`](../../search-qdrant/src/main/scala/leaderboard/search/qdrant/QdrantCollectionCompatibilityValidator.scala) and [focused JSON tests](../../search-qdrant/src/test/scala/leaderboard/search/QdrantJsonInterpreterSpec.scala) | Preserve typed point IDs, named-vector/config compatibility and deterministic payload encoding ideas. Replace the unfiltered request and sparse payload with declared payload indexes and hard-filter pushdown from `CandidatePlan`. |
| Brick 7 orchestration | [`SemanticSupplementPolicy`](../../search-core/src/main/scala/leaderboard/search/semantic/SemanticSupplementPolicy.scala), [`SemanticSupplementPolicySpec`](../../search-core/src/test/scala/leaderboard/search/SemanticSupplementPolicySpec.scala), [`QdrantVariantSupplementPolicy`](../../beautyq-search-wiring/src/main/scala/leaderboard/search/hybrid/QdrantVariantSupplementPolicy.scala), [`QdrantVariantSupplementPolicySpec`](../../leaderboard-app-shell/src/test/scala/leaderboard/search/QdrantVariantSupplementPolicySpec.scala) | Preserve the measured prefix-preserving, deduplicating, cap-limited invariants and interval-overlap business evidence. Reimplement them in Gen2 with first-page/default-sort eligibility, baseline membership, explicit result provenance and the accepted failure policy. |
| Brick 8 evaluation/cutover evidence | [`RuntimeEsQdrantScorecardProofSpec`](../../leaderboard-app-shell/src/test/scala/leaderboard/search/RuntimeEsQdrantScorecardProofSpec.scala), [supplement architecture](../SEARCH_SUPPLEMENT_ARCHITECTURE.md), [local supplement gate](../BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md) | Reuse query cases, metrics and proven no-harm intent as evidence. Do not import the historical serving/eval scaffolding or treat Gen1 route activation as Gen2 semantics. |

### Known Gen1 traps that Gen2 must not reproduce

| Trap | Source evidence | Gen2 rule |
|---|---|---|
| Descriptive root overstates executability | [`SearchDomainSpec`](../../search-contract-core/src/main/scala/leaderboard/search/contract/SearchDomainSpec.scala) contains descriptive sections without parser/compiler behavior. | The executable BeautyQ root references the exact typed values consumed by runtime services; generated documentation is a view, not another contract. |
| Field flags are metadata, not enforced capabilities | [`SearchField`](../../search-core/src/main/scala/leaderboard/search/dsl/SearchDsl.scala) carries booleans while current mapping consumes mainly kind/analyzer. | Gen2 capability sets are validated at every filter/facet/sort/group/payload use. |
| Public names can drift from paths | V1's [`SearchQuerySchema` builder and test](../../search-core/src/test/scala/leaderboard/search/SearchQuerySchemaBuilderSpec.scala) correctly prove a public name can differ from `field.path`. | Preserve this decision: every public ID is explicit; only direct Scala document selectors may derive a storage/document path. |
| Price semantics diverge | [`BeautySearchSpecV1`](../../beautyq-search-contract/src/main/scala/leaderboard/search/dsl/BeautySearchSpecV1.scala) facets/ranges use only `priceFrom`, while [`QdrantVariantSupplementPolicy`](../../beautyq-search-wiring/src/main/scala/leaderboard/search/hybrid/QdrantVariantSupplementPolicy.scala) checks interval overlap with `priceFrom` and `priceTo`. | One `IntervalOverlap` semantic compiles to both backends and to facet buckets. |
| Geo meaning is implicit and collapsed | [`ElasticsearchSearchRequestInterpreter`](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchSearchRequestInterpreter.scala) enables scoring from coordinates and resolves geo constraints to an empty filter clause. | Keep `GeoProximitySignal`, `GeoDistanceFilter` and `GeoDistanceSort` distinct; coordinates alone do not activate any of them. |
| ES implementation is incomplete for a full UI loop | The current [request interpreter](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchSearchRequestInterpreter.scala) uses plain terms groups and one-field range facets; the [response interpreter](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchSearchResponseInterpreter.scala) decodes hits only. | `FullSearchResult` must include totals, facets, groups and page state, with requested missing sections treated as typed errors. |
| Qdrant filtering is not pushed down | [`QdrantJsonInterpreter.searchRequestJson`](../../search-qdrant/src/main/scala/leaderboard/search/qdrant/QdrantJsonInterpreter.scala) has vector/limit/payload but no filter; the current BeautyQ payload is sparse. | `CandidatePlan` hard constraints compile to Qdrant filters backed by declared payload fields/indexes; hydration checking is only an assertion. |
| Repository loading is not snapshot consistency | [`BeautyQSearchCatalogSnapshotLoader.FromRepositories`](../../beautyq-search-materialization/src/main/scala/leaderboard/search/document/BeautyQSearchCatalogSnapshotLoader.scala) performs a sequence of repository loads without owning one transaction boundary. | Gen2 owns one repeatable-read/equivalent snapshot boundary and separately records source revision, content fingerprint and capture time. |
| A deterministic hash can still omit implementation behavior | [`SearchRuntimeFingerprint`](../../search-core/src/main/scala/leaderboard/search/dsl/SearchRuntimeFingerprint.scala) is a useful canonical-JSON precedent but explicitly cannot introspect dynamic functions and relies on extra sections. | Gen2 identity includes projected documents plus explicit projection, compiler/index-format and embedding versions. |
| Compatibility facades obscure ownership | [`BeautyQVariantSearchDocumentContract`](../../beautyq-search-contract/src/main/scala/leaderboard/search/document/BeautyQVariantSearchDocumentContract.scala) and top-level aliases exist for Gen1 compatibility. | Gen2 has one canonical root and no compatibility facade until the single final cutover, where Gen1 is deleted. |

### Transport extraction rule

Current ES/Qdrant HTTP clients live inside forbidden Gen1 backend modules. They are **extract-first**
candidates, not direct dependencies. Before extraction, compare:

- [`ElasticsearchJsonClient`](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchJsonClient.scala) and [`ElasticsearchHttpJsonClient`](../../search-elasticsearch/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchHttpJsonClient.scala);
- [`QdrantClient`](../../search-qdrant/src/main/scala/leaderboard/search/qdrant/QdrantClient.scala) and [`EmbeddingClient`](../../search-qdrant/src/main/scala/leaderboard/search/embedding/EmbeddingClient.scala).

An extraction is accepted only when the new module contains transport concerns alone, its focused
client tests move with it, neither Gen1 interpreter nor BeautyQ policy enters the module, and Gen2 no
longer needs any dependency on a forbidden Gen1 search project. Reimplementing the minimal client in
the Gen2 backend remains valid when extraction would broaden scope.

## Delivery strategy

Gen2 is built independently beside Gen1.

```text
Gen1 search runtime stays unchanged and runnable
(shared Service/Category APIs receive accepted additive code fields)
        │
        ├── Brick 0 ... Brick 8 build and prove Gen2
        │   in new modules and separate resources
        │
        └── Brick 9 performs one atomic cutover
            and removes Gen1
```

“Iterative” means building Gen2 by small independently reviewable bricks. It does not mean gradually moving V1 runtime ownership into Gen2.

Before Brick 9, rollback means deleting/reverting additive Gen2 work without changing V1. Brick 9 is reverted as one cutover change set.

## Global diff rules

### Allowed before cutover

- new Gen2 sbt projects and source trees;
- `build.sbt` additions required to declare those projects;
- additive stable-code changes in shared BeautyQ model/SQL/seed/repositories;
- the accepted additive Service/Category API schema change caused by exposing those shared code fields before cutover;
- mechanical compile fixes in non-Gen2 callers caused only by those shared domain additions, without changing V1 search semantics or ownership;
- new neutral transport/client modules;
- independent Gen2 runner/role/endpoint;
- Gen2-only test resources and backend namespaces;
- documentation and evaluation artifacts.

### Forbidden before cutover

- importing Gen2 from V1 search modules;
- importing V1 search modules from Gen2;
- V1-to-V2 production adapters;
- shadow decoding inside the V1 backend;
- switching V1 facet/group/index ownership incrementally;
- deprecating/removing V1 aliases before the final cutover;
- sharing V1 ES index aliases or Qdrant collections with Gen2;
- adding Gen2 evaluation corpora/reports to the serving classpath.

## Brick 0 — Accept semantics and establish the module firewall

### Purpose

Prevent the parallel architecture from inheriting ambiguous semantics or accidental Gen1 dependencies.

### Diff

- accept the Gen2 semantics ADR;
- add the new sbt project DAG with empty/minimal source roots;
- add module dependency checks;
- add forbidden-import checks;
- define approved neutral dependencies and package prefixes;
- reserve separate Gen2 ES alias and Qdrant collection naming conventions;
- add a short semantic delta ledger test fixture.

### New projects

```text
search-gen2-contract
search-gen2-core
search-gen2-elasticsearch
search-gen2-qdrant
beautyq-search-gen2-contract
beautyq-search-gen2-materialization
beautyq-search-gen2-wiring
beautyq-search-gen2-eval
```

A separate neutral transport project may be added when required.

`beautyq-search-gen2-contract` explicitly depends on `repo-core` because the executable root owns catalog topology through the repository catalog declaration algebra. Gen2 does not introduce a duplicate catalog DSL.

### Proof

- sbt project graph matches the technical specification;
- no Gen2 project depends on a forbidden Gen1 project;
- generic Gen2 modules contain no BeautyQ import/name;
- serving modules do not depend on eval;
- firewall fails on a deliberately injected forbidden fixture;
- ADR has no unresolved P0 item.

### Expected diff shape

```text
build.sbt                                           additive project declarations
project/...                                         firewall helper/check
search-gen2-*/src/...                               minimal package markers/tests
beautyq-search-gen2-*/src/...                       minimal package markers/tests
docs/gen2/...                                       accepted semantics/docs
```

### Rollback

Remove the new project declarations, source roots and checks. Gen1 is unchanged.

## Brick 1 — Implement the generic field/document declaration kernel

### Purpose

Create the smallest backend-neutral typed language needed for the first visible search-document DSL,
without BeautyQ or backend leakage.

### Diff

In `search-gen2-contract` add:

- typed `FieldId`, `FieldPath`, value codecs and capabilities;
- `SearchField[Document, Value]` with an explicit stable ID and typed extractor;
- immutable additive field/document declaration builders;
- an ordinary final contract value independent of builder construction;
- deterministic field order;
- uniqueness and capability validation primitives;
- a deterministic human-readable structural view generated from the final declaration.

In `search-gen2-core` add:

- document declaration validation and structural rendering support that cannot live in the pure
  contract module;
- no request, plan, backend or supplement behavior.

### Forbidden

- BeautyQ declarations;
- ES/Qdrant JSON;
- constraints, request parsing, `SearchPlan`, backend result types or cursor logic;
- public field/query names derived from extractor or storage paths;
- generic result types with optional unsupported sections;
- compatibility types copied from Gen1 solely to preserve V1 shape.

### Proof

- neutral sample-document tests prove the generic boundary;
- builder preserves exact declared field order and typed handles;
- explicit field IDs may deliberately differ from document/storage paths;
- a field for another document type does not compile;
- duplicate field IDs and invalid capability declarations fail deterministically;
- generated structural output is deterministic and matches direct structural assertions;
- invalid field capability references fail deterministically;
- no generic source or fixture contains a BeautyQ symbol.

### Expected diff shape

```text
search-gen2-contract/src/main/...                   field/document declaration kernel
search-gen2-contract/src/test/...                   neutral declaration fixtures
search-gen2-core/src/main/...                       validation/rendering support if required
search-gen2-core/src/test/...                       deterministic structural proofs
```

### Rollback

Delete the field/document kernel implementation. Gen1 is unchanged.

## Brick 2 — Create the BeautyQ Gen2 root, identities and document branch

### Purpose

Make the executable BeautyQ root the first domain-level source of truth, not a late migration wrapper.

Brick 2 is delivered as Commit 3A (shared stable-identity schema) followed by Commit 3B (Gen2
declaration root). They are one planned brick but separate review/risk layers.

### Diff

In shared BeautyQ domain/persistence code, add:

- `ServiceCode` and `CategoryCode` directly to the shared models (the accepted choice instead of a Gen2 sidecar identity model);
- fresh-schema SQL columns and uniqueness constraints; this repository has no persistent production
  BeautyQ database, so Commit 3A intentionally added no forward migration, legacy backfill or
  migration framework;
- seed values;
- repository codecs/queries;
- additive validation tests;
- explicit API/schema assertions documenting that Service/Category derived JSON may expose the new code fields before cutover.

In `beautyq-search-gen2-contract`, add:

```text
BeautyQSearchDeclarations
├── catalog
└── variants
    ├── identity
    ├── Fields
    └── document
```

This is the real initial root, not a placeholder rendering of the final tree. Request, intent, plan,
facets, groups, response, backends and quality references are added when their executable sections
exist.

Declare:

- `VariantSearchDocumentGen2`;
- the complete typed `Fields` set including IDs, stable codes and all values required by the accepted
  projection/search design;
- the document declaration built from those exact `Fields.*` handles;
- field capabilities required by later text/filter/facet/group/sort/payload uses, without adding
  backend JSON or runtime compilers;
- the deterministic generated BeautyQ document tree.

### Delivered state

Brick 2 is implemented as the executable root shown above. `variants.Fields` owns 24 required static
fields and 20 optional dynamic attribute fields: 44 handles in total, with `variantId` used once as
the document identity and the remaining 43 handles preserved in declaration order as ordinary
fields. The root contains no request, intent, plan, facet, group, response, backend or quality
placeholder branches. These concrete counts refine the delivered proof without changing the planned
dependency order after `Fields`.

### Forbidden

- dependency on any Gen1 search contract (including the old `beautyq-search-contract` declarations);
- behavioral changes to V1 search declarations, parser, ranking, indexing or response assembly;
- describing Gen1 as byte-for-byte unchanged outside search: the additive shared domain/API code-field change is intentional and must be documented;
- copying the lossy `SearchDomainSpec` projection as the Gen2 root;
- fake placeholder root branches for work that has not been implemented;
- deriving public request/filter/facet names from field paths;
- embedding eval corpora in the contract module;
- implementing backend clients.

### Proof

- root reachability test finds every currently implemented executable section;
- generated document view is deterministic and agrees with direct structural assertions;
- stable code uniqueness and unknown-code validation pass;
- every field ID is explicit and unique;
- the declaration preserves the intended field order and exact `Fields.*` identities;
- documented future facet/sort/group/filter/payload uses are supported by the declared capabilities;
- no field declaration is recreated outside `variants.Fields`.

### Expected diff shape

```text
beautyq-model/...                                   additive code types/fields
repository fresh-schema DDL/seed/...               additive codes
beautyq-search-gen2-contract/src/main/...           new executable root
beautyq-search-gen2-contract/src/test/...           declaration validation
```

### Rollback

Revert additive codes and delete the Gen2 contract module implementation. No V1 search ownership changed.

## Brick 3 — Build consistent repository snapshot and explicit projection

### Purpose

Produce deterministic Gen2 documents from persisted repository state rather than directly from seed resources.

### Diff

In `beautyq-search-gen2-materialization` add:

- one repeatable-read snapshot boundary;
- `VersionedSnapshot` with content fingerprint, optional source revision and capture time;
- canonical snapshot serialization/hash;
- explicit BeautyQ projection;
- joins and invariant validation;
- stable-code resolution;
- deterministic document ordering;
- projection error model;
- optional seed-vs-repository comparison test helper;
- explicit seed write dependency validation if seed ordering is addressed here.

### Forbidden

- using Gen1 materialization classes at runtime;
- direct seed projection as the Gen2 serving source;
- including `capturedAt` in content fingerprint;
- inferring projection text/business meaning from catalog shape.

### Proof

- all repository reads share one transaction/snapshot;
- unchanged data loaded twice yields the same content fingerprint;
- a concurrent mutation fixture cannot produce a torn snapshot;
- current seed inserted into repositories projects deterministically;
- missing or cross-owner entities fail before indexing;
- stable codes resolve to the expected IDs;
- content change alters fingerprint.

### Expected diff shape

```text
beautyq-search-gen2-materialization/src/main/...    snapshot + projection
beautyq-search-gen2-materialization/src/test/...    repository/invariant tests
```

### Rollback

Delete the new materialization implementation. Gen1 indexing remains untouched.

### Reusable boundary

`search-gen2-core` owns versioned snapshot identities, the generic
snapshot-source contract, canonical fingerprint framing, declaration-driven
projected-document fingerprinting and load/project/fingerprint orchestration.

A domain materialization module owns transaction acquisition, its snapshot
shape, source token selection, persisted-data validation, joins, projection
errors and business text construction. A domain must not import these
primitives from another domain module.

### Golden fingerprint policy

The checked-in golden source/projected fingerprint assertions (`BeautyQSnapshotFingerprintSpec`,
`BeautyQProjectedDocumentsFingerprintSpec`) exist to prove the Commit 3D-3F kernel extraction preserved
exact BeautyQ byte output during a mechanical refactor. That is their origin, not a promise that these
bytes are permanently frozen: this repository has no persistent production BeautyQ database (the same
fact Commit 3A's schema note already relies on), so there is no deployed index whose reuse depends on
byte-for-byte fingerprint stability across commits.

Treat every golden fingerprint as a **reviewed change-detector**, not immutable output:

- a golden value may be deliberately re-baselined in a commit whose message states the reason (e.g. an
  accepted encoding-version bump), the same way `projectionFormatVersion`/`backendCompilerVersion`
  bumps are already normal per the technical specification's identity rules (§9.3);
- a golden value must never be re-baselined silently to make an unrelated change pass;
- an *unexplained* golden diff remains a real regression signal and blocks the patch exactly as before.

`CanonicalFingerprint.rowWithSortParts`'s "historical grouped sort key" special case remains tracked
cleanup: it was kept during 3D-3F specifically to preserve BeautyQ's pre-extraction byte output. Do not
mix its removal or an encoding-version bump into Brick 4's request/intent/plan algebra. Remove it only
in a dedicated reviewed encoding cleanup once all callers and the replacement canonical-row policy are
source-confirmed; keep a BeautyQ-local shim only if an accepted domain policy still needs that exact
grouping. Until then it remains an explicit low-level escape hatch, not a canonical onboarding API.

## Brick 4 — Implement Gen2 request, intent and plan compilation

### Purpose

Create the real Gen2 input contract and compile it directly into accepted semantics.

### Reusable-authoring rule for this brick (two shapes, no speculative features)

Bricks 1-3's reusable layers (field/document declaration, materialization kernel) were each built
BeautyQ-first and only proven generic later, by a Commit 3G tracer domain built after the fact. That
sequencing worked, but it means every generic type in those layers took BeautyQ's shape by default and
had to be corrected retroactively (see `SEARCH_GEN2_FRAMEWORK_SCOPE.md`'s gap ledger). Brick 4 introduces
a materially larger generic surface - `Bound`/`RangeBounds`, constraint/signal/sort/facet/group types,
`SearchPlan`, `PlanIdentity` - where the same mistake would be far more expensive to correct after ES/
Qdrant compilers in Bricks 5-6 have already been built against it.

Brick 4 must not repeat the BeautyQ-first-extract-later sequencing. Every generic algebra type that a
real BeautyQ requirement introduces (a constraint kind, signal, sort mode, facet/group request shape or
`PlanIdentity` component) must be exercised from its first commit by BeautyQ and by a tracer/neutral
fixture using a genuinely different combination (e.g. interval overlap on a non-price pair, geo on a
non-master location, grouping by something other than a provider). The fixture calibrates the API
shape; it is not permission to invent a product feature solely to satisfy a numeric consumer count. A
generic type with only the BeautyQ usage and no explicit single-consumer status is a reject/continue
signal under the coordinator's business-authoring gate.

This does not relax the plan's existing YAGNI discipline (§"Global diff rules", the standing rule
against "a speculative, oversized generic-algebra diff"): the BeautyQ requirement justifies the
feature; the second executable shape challenges its reusable representation. A neutral fixture alone
never justifies expanding production vocabulary.

### Diff

In `search-gen2-contract` add the generic query-semantic algebra immediately before its first BeautyQ
use:

- explicit `Bound`/`RangeBounds`;
- terms, number-range, interval-overlap and geo-distance-filter constraints;
- separate geo proximity signal and distance sort;
- filter/facet/group/sort/page IDs and inputs;
- the `SearchPlan` contract itself, `SourcedConstraint`/`ConstraintPriorityTiers` and `PublicSortClause`;
- diagnostics, precision and typed unsupported-capability errors required by planning.

In `search-gen2-core` add:

- plan normalization and capability validation;
- deterministic plan fingerprinting;
- value-only `PlanIdentity`/`CanonicalPlanView` and cursor envelope validation based on `PlanIdentity`;
- Brick 4F's own resolution/compilation mechanics (`CanonicalConstraintView`, `ConstraintSlot`,
  `ConstraintPrecedenceResolver`, `PublicPlanInputResolver`, `SearchPlanCompilationKernel`);
- neutral fixtures for every added semantic distinction.

In `beautyq-search-gen2-contract` and `beautyq-search-gen2-wiring` add:

- `BeautySearchRequestGen2` and its validated inbound form;
- one unified public `filters` collection without trusted internal provenance;
- requested facet IDs;
- sort inputs;
- `PageRequest(cursor, size)`;
- optional user location;
- public filter decoder that receives field, operator and value;
- intent vocabulary keyed by stable service/category codes;
- parser output with hard constraints, the one geo soft signal, residual text and canonical semantic labels;
- deterministic request and parsed-intent traces;
- request + parser -> normalized `SearchPlan` compiler, precedence, browse defaults, semantic text and
  candidate eligibility (Brick 4F/4G, not part of the completed 4D+4E slice);

`CandidatePlan`/`CandidatePlanDecision` are introduced here because semantic-text and eligibility
compilation produce them. Purpose-specific `FullPlan`/`FullSearchResult` and
`CandidateSearchResult` are introduced in the focused ES and Qdrant bricks respectively. Do not add
speculative backend sections merely to make the core algebra appear complete.

### Forbidden

- V1 request adapter in main/runtime source;
- separate `selectedFacets` and `filters` authorities;
- top-level `limit` in addition to page size;
- implicit geo activation from coordinates;
- semantic parity assertions for intentional ADR deltas.

### Proof

- filter field/operator/value round trips;
- public clients cannot submit `ParsedHard`, `ParsedSoft` or `SystemDefault` provenance;
- selected facet provenance is assigned server-side after presentation ID validation and survives plan compilation;
- explicit UI filters override parsed constraints;
- conflicts produce stable errors/diagnostics;
- `NearUser` produces only a proximity signal;
- hard radius and distance sort require coordinates;
- cursor invalidates on `PlanIdentity`/sort/fingerprint mismatch without recursively including the current cursor;
- semantic text is deterministic residual-plus-canonical-label output;
- filter-only/default-browse requests are supplement-ineligible with `NoSemanticQueryText`;
- supplement is ineligible beyond page one or under non-relevance sort;
- golden plans are deterministic.
- neutral generic tests cover every constraint/signal/sort and canonical-identity distinction without
  BeautyQ names.

### Expected diff shape

```text
search-gen2-contract/...                            query/plan semantic algebra
search-gen2-core/...                                plan validation/identity/cursor primitives
beautyq-search-gen2-contract/...request|intent|plan new contracts/declarations
beautyq-search-gen2-wiring/...                      parser/compiler
beautyq-search-gen2-*/src/test/...                  semantic fixtures
```

### Rollback

Delete the Gen2 request/parser/compiler. Gen1 route remains unchanged.

## Brick 5 — Build the complete Elasticsearch Gen2 vertical

### Purpose

Make Elasticsearch the independent owner of the full Gen2 baseline result.

### Brick 5A — Mapping, ingestion, hits, totals and facets

#### Diff

In `search-gen2-elasticsearch` add:

- neutral ES transport/client if not already separate;
- purpose-specific `FullPlan` and `FullSearchResult` contracts if not already required by a prior
  executable slice;
- mapping compiler;
- document ingestion compiler;
- versioned physical index and stable alias lifecycle;
- `SearchPlan -> ES request` compiler for text, constraints, geo, sort and cursor;
- exact total tracking;
- terms/range/interval-overlap facet aggregations;
- response decoder for hits, totals, facets and page state;
- typed errors for missing/unknown sections.

In BeautyQ Gen2 wiring add:

- index build from repository-backed Gen2 documents;
- validation/count/fingerprint checks;
- independent Gen2 alias names;
- BeautyQ full-result-to-response projection without groups initially.

#### Proof

- mapping golden tests;
- interval-overlap filter and facet boundary tests;
- exact totals beyond 256 hits;
- facet counts over the complete result set;
- cursor pagination with deterministic tie-breakers;
- failed build leaves old Gen2 alias active;
- successful build atomically activates validated generation.

### Brick 5B — Provider and service groups/carousels

#### Diff

- implement group request compilation with representative data and declared metrics;
- decode `GroupResult` including count, representative, best score and optional proximity;
- implement deterministic ordering from the accepted group policy;
- project groups into BeautyQ provider/service carousels.

A dedicated secondary ES query is allowed if it gives clearer exact semantics than one complex aggregation request.

#### Forbidden

- pretending a plain `terms` bucket contains representative document data;
- silently accepting approximate counts where exact fixture policy is required;
- moving group ownership to in-memory hit-window recounting.

#### Proof

- representative document fixture tests;
- matching count fixtures with more than the hit window;
- best-score and stable-key order fixtures;
- geo-active group ordering fixture when proximity is declared;
- group precision is surfaced.

### Expected diff shape

```text
search-gen2-elasticsearch/src/main/...              new compiler/decoder/lifecycle
search-gen2-elasticsearch/src/test/...              golden + integration tests
beautyq-search-gen2-wiring/...                      ES service/projection
```

### Rollback

Remove the independent Gen2 ES vertical/resources. V1 ES remains active and untouched.

## Brick 6 — Build the complete Qdrant Gen2 candidate vertical

### Purpose

Provide filtered semantic candidates without forcing Qdrant into a full-result role.

### Diff

In `search-gen2-qdrant` add:

- neutral Qdrant transport/client if not already separate;
- purpose-specific `CandidateSearchResult` and candidate diagnostics contracts;
- collection compiler and lifecycle;
- embedding/vector configuration;
- typed payload projection;
- payload index creation/validation;
- point ingestion;
- `CandidatePlan -> Qdrant request` compiler;
- terms, numeric, interval-overlap and explicit geo radius filters;
- topK/threshold/oversampling policy;
- candidate response decoder.

In BeautyQ Gen2 wiring add:

- embedding source projection;
- independent Gen2 collection names;
- candidate hydration against the Gen2 document view;
- post-hydration assertion and diagnostics.

### Forbidden

- Qdrant facet/group/page result placeholders;
- primary filtering only after hydration;
- dependence on Gen1 Qdrant interpreter or supplement policy;
- route activation.

### Proof

- payload schema/index validation;
- golden filter JSON for every supported hard constraint;
- interval-overlap price filter boundaries;
- candidate outside unfiltered topK is retrieved after filter pushdown;
- zero post-hydration constraint violations;
- deterministic candidate decoding and deduplication.

### Expected diff shape

```text
search-gen2-qdrant/src/main/...                     new compiler/decoder/lifecycle
search-gen2-qdrant/src/test/...                     golden + integration tests
beautyq-search-gen2-wiring/...                      embedding/hydration service
```

### Rollback

Remove the independent Gen2 Qdrant vertical/resources. V1 remains untouched.

## Brick 7 — Implement baseline-plus-supplement orchestration

### Purpose

Combine role-specific backend outputs under the narrow accepted no-harm policy.

### Diff

In `search-gen2-core` add generic orchestration primitives where domain-neutral.

In `beautyq-search-gen2-wiring` add BeautyQ policy:

- ES baseline `FullSearchResult`;
- Qdrant `CandidateSearchResult`;
- first-page/default-relevance eligibility;
- max-one append-only selection;
- baseline current-page deduplication;
- baseline full-match membership guard;
- hard-constraint assertion;
- provenance;
- separate baseline total and supplement count;
- fixed startup/readiness policy: ES baseline required, Qdrant outage exposes explicit `baseline_only` mode and `supplementReady = false`;
- fixed request-time policy: Qdrant timeout/transport/backend failure returns unchanged ES output with `supplement_failed` and a stable reason code;
- explicit degradation diagnostics, with no hidden retry or fallback candidate backend.

### Forbidden

- score fusion;
- reranking;
- fallback ownership;
- supplement under price/duration/distance sort;
- changing ES facets/groups/totals to include the supplement implicitly.

### Proof

- ES prefix and order are unchanged;
- no ES result is removed;
- no candidate appears on a later baseline page;
- supplement runs only on eligible page/sort;
- ES baseline or document-lookup failure prevents serving readiness;
- Qdrant startup failure keeps baseline serving explicit but fails full-search/cutover readiness;
- request-time Qdrant failure returns the unchanged baseline with `supplement_failed`;
- plan/capability errors and hydration invariant violations are not degraded into baseline-only success;
- no-hard-constraint-violation gate is green;
- provenance and supplement count are correct.

### Expected diff shape

```text
search-gen2-core/...                                generic orchestration types
beautyq-search-gen2-wiring/...                      BeautyQ policy/service
beautyq-search-gen2-eval/...                        no-harm fixtures/report
```

### Rollback

Disable/delete Gen2 supplement orchestration; independent Gen2 ES baseline remains available. V1 remains untouched.

## Brick 8 — Provide independent Gen2 application composition and cutover evidence

### Purpose

Run Gen2 end to end beside V1 without integrating it into the V1 backend.

### Diff

- add a separate local/test role, command or versioned endpoint;
- wire Gen2 repositories, snapshot, projection, ES, Qdrant and orchestration;
- use separate backend resources;
- add end-to-end request/response tests;
- build the evaluation corpus and cutover report in `beautyq-search-gen2-eval`;
- document operational startup/build/activation commands;
- generate a machine-readable cutover gate result.

The application shell may depend on both V1 and Gen2 only to expose separate compositions. Gen2 modules themselves remain isolated.

### Forbidden

- V1 request fan-out to Gen2;
- V1 shadow adapters;
- shared index alias/collection;
- production/default route ownership switch;
- serving dependency on eval.

### Proof

- independent Gen2 endpoint/role passes end-to-end tests;
- semantic delta ledger fixtures pass;
- exact facet and group authoritative fixtures pass;
- pagination/supplement no-duplicate tests pass;
- relevance/no-harm/latency/freshness gates meet accepted thresholds;
- module firewall is clean;
- serving readiness and full-search readiness expose the accepted ES/Qdrant distinction;
- deletion inventory proves no required behavior exists only in Gen1.

### Expected diff shape

```text
beautyq-search-gen2-wiring/...                      independent composition/endpoint
beautyq-search-gen2-eval/...                        corpus, reports, gate
leaderboard-app-shell or new runner module/...      separate role only
docs/gen2/...                                       runbook and cutover evidence
```

### Rollback

Remove/disable the independent Gen2 role and resources. V1 remains the default.

## Brick 9 — One final cutover and Gen1 deletion

### Purpose

Make Gen2 the only search architecture in one reviewed and revertible change set.

### Entry conditions

All Brick 8 gates are green and attached to the cutover review.

The cutover review must include:

- module firewall report;
- end-to-end report;
- semantic delta ledger results;
- ES total/facet/group/page evidence;
- Qdrant filter/hydration evidence and full-search readiness evidence;
- supplement no-harm/no-duplicate evidence;
- index/collection activation runbook;
- complete Gen1 deletion inventory;
- rollback command/change-set reference.

### Diff

As one atomic merge/change set:

1. switch default application binding/route to Gen2;
2. switch operational aliases/resources according to the runbook;
3. remove V1 search route and runtime wiring;
4. remove V1 contracts, materialization and backend interpreters;
5. remove compatibility aliases and lossy descriptive adapters;
6. remove old search sbt projects and dependencies;
7. remove or archive historical runtime eval/readiness scaffolding;
8. remove temporary side-by-side endpoint/role names;
9. rename Gen2 public artifacts to the canonical unversioned names where desired;
10. update all architecture/onboarding/runbook documentation.

### Forbidden

- new relevance features;
- new semantic decisions;
- unreviewed API changes outside the accepted Gen2 contract;
- retaining a hidden dependency on any deleted Gen1 project;
- splitting ownership switch and V1 deletion across a long migration period.

### Proof

- clean build and all Gen2 tests pass after Gen1 project removal;
- repository-wide import/dependency scan finds no Gen1 search references;
- default route returns Gen2 response;
- active ES/Qdrant resources match Gen2 fingerprints;
- smoke tests pass from a clean environment;
- documentation names only Gen2 as current architecture;
- revert of the complete cutover change set restores the previous V1 state if needed.

### Expected diff shape

```text
app bindings/routes                                  ownership switch
search-* Gen1 modules                               deleted
beautyq-search-* Gen1 modules                       deleted
historical compatibility/eval scaffolding           deleted or archived
Gen2 module/version names                           canonicalized if chosen
docs                                                 rewritten as current state
```

### Rollback

Revert the entire cutover change set and restore the previous backend aliases/resources using the recorded runbook. Do not attempt a partial source rollback.

## Review checklist for every pre-cutover brick

1. Does the diff live only in new Gen2 modules or explicitly approved shared model/persistence files?
2. Did any Gen2 project gain a Gen1 search dependency or import?
3. Did a generic module gain a BeautyQ symbol?
4. Did serving gain a dependency on eval/report code?
5. Is the behavior defined by the accepted ADR rather than inferred from V1?
6. Are all unsupported backend capabilities typed errors?
7. Are fingerprints/cursors deterministic and covered by tests?
8. Can the brick be reverted without changing V1 runtime?
9. Are new backend resources namespaced as Gen2?
10. Does the diff avoid unrelated cleanup?
11. Does every document-centred policy reuse the canonical `variants.Fields` handle instead of
    recreating a field or deriving a public name from its path?
12. Does the diff include both structural assertions and a deterministic human-readable generated
    view or scenario trace for the changed declaration stage?
13. Can a business reviewer explain the changed data/intent/backend path from the test names and
    expected values without reading interpreter internals?
14. Were the stage's Gen1 references read using the evidence/reuse classification, with no
    evidence-only or extract-first symbol imported across the firewall?

## Suggested commit naming

```text
search-gen2: establish module firewall and project DAG
search-gen2-contract: add typed field and document declaration kernel
beautyq-model: add stable service and category codes
beautyq-search-gen2: add domain root and variant fields
beautyq-search-gen2: materialize consistent repository snapshots
search-gen2: add query plan algebra and BeautyQ intent compilation
search-gen2-es: implement full baseline vertical
search-gen2-es: implement typed group projections
search-gen2-qdrant: implement filtered candidate vertical
beautyq-search-gen2: add no-harm supplement orchestration
beautyq-search-gen2: add independent application composition
search: cut over to Gen2 and remove Gen1
```

## Implemented opening sequence and current handoff

The implemented opening sequence is:

1. Brick 0 established the isolated Gen2 module DAG and automated firewalls without search behavior.
2. Brick 1 added the generic typed field/document kernel. Validation and structural rendering fit in
   the pure contract module; `search-gen2-core` contains the sibling-module compile proof rather than
   duplicated production support.
3. Commit 3A added shared stable codes through the model, fresh-schema persistence, seed,
   repositories and API schema without a Gen2 declaration.
4. Commit 3B added the real `BeautyQSearchDeclarations` root containing
   `catalog` and `variants.identity/Fields/document`, without request, backend or runtime work.
5. Brick 3 added the repository-backed consistent snapshot, explicit validated Variant projection and
   source/projected fingerprints in the side-by-side materialization module. Its focused pure suites and
   the PostgreSQL repeatable-read/seed/torn-snapshot proof pass; backend and serving wiring remain out of
   scope.
6. Commit 3D extracted the domain-neutral snapshot/fingerprint/materialization mechanics from
   `beautyq-search-gen2-materialization` into `search-gen2-core`.
7. Commit 3E closes the authoring boundary: `BeautyQSnapshotCanonicalRows` declares each BeautyQ
   source row's fields once; the generic canonical-row kernel derives token blocks, grouped nested
   sort fragments and ordering from that declaration. BeautyQ materializer result/error types are
   aliases of the generic kernel types. BeautyQ now owns transaction acquisition, snapshot shape,
   persisted-data validation, source field/value selection, joins/projection errors, attribute/text
   policy and encoding/version constants.
8. Commit 3F applied the reusable-boundary review to every implemented Gen2 brick: direct canonical
   row fields now use selectors, nested groups derive their own ordering without size vectors,
   canonical snapshot traversal follows the snapshot product, deterministic indexing and non-empty
   error accumulation are generic, the BeautyQ document uses exhaustive product coverage, and one
   structural tree drives both typed root ownership and rendering. Stable-code Doobie/Tapir adapters
   are derived from `CanonicalStringValue`; transaction-local SQL and business projection remain
   explicit domain adapters.
9. Commit 3G added the executable calibration checkpoint 3D-3F's single-domain extraction never had: a
   tracer deliberately shaped differently from BeautyQ and exercised through the complete declaration/
   materialization path owned by `search-gen2-contract`/`search-gen2-core`. It added support for
   `Long`/date-time values, dynamic `Text` families and `CanonicalSnapshot.Single` sources; discovered
   and avoided, but did not framework-prevent, a Scala/JVM nested-object initialization hazard; and
   added a shape-firewall
   (test-source import scan, exact-vocabulary drift pins, an executable multi-value-gap compile proof),
   and left two gaps deliberately open with a stated reason rather than freehanded: a multi-valued field
   (deferred to Brick 4's own constraint algebra) and the non-reusable `StableCodeCompanion` base
   (deferred as an out-of-scope `beautyq-model` change). Golden fingerprints were reclassified from
   assumed-immutable to reviewed change-detectors, since no persistent production database makes them
   load-bearing. Full detail: `SEARCH_GEN2_FRAMEWORK_SCOPE.md`.

10. Brick 4D+4E added the generic public-input registry and intent-matching primitives, then rewrote the
    BeautyQ boundary as explicit public names/typed field policies, stable-code actions and aliases over
    those primitives. It retained server-only provenance assignment, parsed hard constraints,
    `GeoProximitySignal`, residual text, semantic labels and readable traces. `variants.request` and
    `variants.intent` are derived from executable values; no plan compiler, precedence, geo-origin
    resolution, backend or route was added. Standard scalar/many/range/geo decoding remains in the
    BeautyQ request adapter; it is not yet a generic claim. Any future extraction must be justified by
    a concrete domain requirement and neutral proof under the authoring principles.

11. Brick 4F combined the validated public request and parsed intent into one validated
    `SearchPlan[VariantSearchDocumentGen2]`. `search-gen2-core`/`search-gen2-contract` gained the
    reusable mechanics: `CanonicalConstraintView` (typed constraint to `ConstraintSlot` projection,
    extracted from `CanonicalPlanView.constraint` to give slot/canonical derivation one owner),
    `ConstraintPrecedenceResolver` (a same-priority first-seen-anchor scan per tier, then a cross-tier
    suppression pass, with applied filters ordered higher-then-lower and suppressed filters merged into
    one true encounter-order sequence), `ConstraintPriorityTiers` (the resolver's higher/lower input,
    constructed only by the precedence value), `ConstraintPrecedence` (one typed source order deriving
    those tiers from one complete typed binding), `PublicPlanInputResolver` (generic public filter/sort
    geo-origin resolution over `PublicFilterPlanView`/`PublicSortPlanView` adapters, so the resolver
    never depends on a domain's concrete wrapper type), `FacetPlanRegistry`/`FacetRequest.fieldHandles`
    (validated facet declaration/lookup behind one `FacetPlanRegistryError`, with field handles derived
    from a request rather than repeated in a second vector, and `FacetSize.unsafeFrom`/
    `FacetPlanRegistry.unsafeFrom` as the framework-owned static-declaration constructors), and
    `SearchPlanCompilationKernel` (an opaque `prepare` result whose `assemble(finalNotices)` uses the
    exact prepared input/resolution, so a domain derives its own mode before final validation without
    being able to forge or pair a resolution). Each is proven by neutral fixtures unrelated to
    BeautyQ's document shape or vocabulary (`InventoryDocument`, `TrailDocument`) as well as by BeautyQ.

    BeautyQ added only `BeautyQSearchPlanPolicy` together with its typed `BeautyQConstraintSource` choice
    and generic `ConstraintPrecedence` value (constraint-source precedence: `PublicRequest` above
    `ParsedIntent`, exactly two ordered tiers) and `BeautyQGeoOriginPolicy` (the
    geo-origin source path and the actual `request -> Option[GeoPoint]` resolution on one
    concrete value, `RequestUserLocation`) - plus the four facet declarations carrying the Gen1-evidence
    bucket tables, the explicit empty group policy, the default-browse notice and plan-mode
    classification. `BeautyQSearchPlanCompiler` is a thin composition whose public API is exactly
    `compile(request, intent)` and which always obtains its tiers and geo origin from those policy values:
    generic geo-input resolution, then constraint-precedence resolution over the already-resolved facet
    requests carried by the validated request;
    mode classification and one final notices vector are derived from the opaque prepared resolution's
    own applied filters and passed into `prepared.assemble(finalNotices)`, so the returned plan is always exactly
    `SearchPlan.validate`'s own value, never copied afterward. `BeautyQSearchPlanCompilationTrace` is a
    diagnostic trace whose policy section renders `BeautyQSearchPlanPolicy`'s own values, never a second
    hand-maintained rendering. `BeautyQSearchPlanPolicy.facetRegistry` directly owns public facet IDs
    and lookup; no second, separately editable literal facet list exists. `variants.plan` exposes those same typed policy values - not only their rendered
    summaries - as a reviewer-readable branch of `BeautyQSearchDeclarations`. No semantic query text,
    `CandidatePlan`, cursor validation, backend or route was added.

    ```text
    Domain policy added: BeautyQConstraintSource, ConstraintPrecedence, BeautyQGeoOriginPolicy,
    BeautyQSearchPlanPolicy (the four facet declarations, empty group policy, the default-browse notice,
    plan-mode classification).
    Framework mechanics reused or extracted: CanonicalConstraintView, ConstraintPrecedenceResolver,
    ConstraintPriorityTiers, PublicPlanInputResolver, FacetPlanRegistry, FacetRequest.fieldHandles,
    FacetSize.unsafeFrom, SearchPlanCompilationKernel (search-gen2-core/contract).
    Neutral proof: InventoryDocument (CanonicalConstraintViewSpec, ConstraintPrecedenceSpec, ConstraintPrecedenceResolverSpec,
    FacetPlanRegistrySpec, SearchPlanCompilationKernelSpec) and TrailDocument (PublicSortClauseSpec,
    PublicPlanInputResolverSpec), both unrelated to BeautyQ's document shape or vocabulary.
    Canonical entry-point update: BeautyQSearchDeclarations.variants.plan.
    Derived-view proof: BeautyQSearchPlanPolicySpec pins variants.plan against BeautyQSearchPlanPolicy's
    own values and proves the typed precedence value drives both rendering and tiering; the compiler and
    trace specs pin the fixed policy and `compile(request, intent)` API across 12 golden compilation traces whose
    policy section is rendered from BeautyQSearchPlanPolicy directly.
    ```

The next code change is **Brick 4G**: compile semantic query text and `CandidatePlan` from the validated
`SearchPlan`, and validate inbound cursors against `PlanIdentity`. Preserve the exact `variants.Fields`
handles, stable codes and the Brick 4F plan-compilation gate order; keep candidate eligibility and cursor
semantics explicit, and do not wire ES/Qdrant or runtime ownership before the candidate contract is proven.
