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
- [domain authoring principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md) for the repository-wide
  business-policy, reuse, and executable-source-of-truth acceptance contract.

## Current implementation state

This section is the single authoritative live status owner for Gen2 implementation. Update it in
the same commit that starts, completes, blocks, or materially re-scopes a brick. Root and docs-index
summaries must remain short and point here.

- **Overall:** implementation in progress.
- **Live status:** Bricks 0–3, 4A–4G-B, 5A–5D, 6A–6B-A and 6C are implemented; Brick 6B-B remains active pending its separate Qdrant 1.18.3 resource proof, and the pre-7 compliance audit makes Brick 6D the next implementation brick.
- **Completed:** 5D owns exact Elasticsearch groups and BeautyQ carousel projection. 6A owns the pure
  Qdrant policy, generation artifacts, candidate request compiler and candidate-only response decoder.
  6B-A owns the neutral JSON/HTTP transport, thin Elasticsearch adapter and exact Qdrant wire client.
- **Next sequence:** implement Brick 6D's authoring/ownership/proof closure; independently complete 6B-B's real-resource proof; then implement Brick 7 orchestration, Brick 8 independent application/evaluation, and Brick 9 atomic cutover/deletion.
- **Materialization boundary decision:** the canonical production source and materializer compute their
  fingerprints with the values they return. No untrusted production caller supplies those aggregates,
  so no additional defensive construction framework is justified.
- **Canonical reading path:** start at wiring-owned `BeautyQSearchGen2`. Its `contract` branch points to
  backend-neutral `BeautyQSearchDeclarations`; all other branches are direct references to downstream
  owners. Brick 6D must expose the currently missing intent-parser and candidate-compiler links before
  this path may again be called complete.

### Audit-derived acceptance gate for the remaining bricks

This is the local application of the normative domain-authoring principles, not another audit ledger:

1. **Ownership:** every persisted identity, authorized target and composed result has one executable
   owner; facade, trace, fingerprint and docs remain derived views.
2. **Totality at real boundaries:** validate untrusted HTTP, JSON and persisted resource state with
   typed errors; do not wrap ordinary internal immutable values against impossible Scala callers.
3. **Semantic integrity:** Elasticsearch remains the full-result owner and Qdrant remains candidate-only;
   no transport or lifecycle path may drop a declared constraint or invent totals/facets/groups.
4. **Authoring value and reuse:** BeautyQ declares only resource names, embedding/retrieval choices and
   hydration policy. The framework owns repeated transport, lifecycle and execution mechanics, proved
   with neutral fixtures rather than a renamed BeautyQ copy.
5. **Proof and documentation honesty:** scripted fixtures use the real wire shape, communication tests
   prove destructive/resource behavior, and this plan reports live status while the technical spec owns
   exact API and supported shapes.

### Accepted initial limits, not live abstraction debt

- Materialization aggregates remain ordinary internal immutable values until a real untrusted caller is
  introduced.
- Multi-valued search fields, a second production domain, CDC, authenticated cursors and arbitrary custom
  analyzers are outside the initial Gen2 scope.
- `Fields` must keep its dependencies local or outside its enclosing root; sibling capture is an
  unsupported authoring shape, not a planned defensive wrapper project.

Every brick that adds domain policy or reusable mechanics must satisfy the
[Domain Authoring Principles](../search/DOMAIN_AUTHORING_PRINCIPLES.md). A brick is not
framework-complete merely because BeautyQ works: domain differences must remain explicit, reusable
mechanics must be reused or extracted, neutral proof must exist, and generated views must derive
from one executable declaration. If the current implementation still has an extraction gap, name its
owner and next boundary in this plan instead of claiming the gap is already generic.

## Coordinator starting model

The next coordinator must preserve this distinction:

1. `catalog` describes normalized persisted business facts and source topology;
2. `variants.document` is an explicit denormalized read model built from a consistent snapshot;
3. `variants.intent` and `SearchPlan` describe requested operations over document fields and are not
   another stored copy of the catalog.

`BeautyQSearchDeclarations` is the backend-neutral contract root with two top-level branches:

```text
BeautyQSearchDeclarations
├── catalog       normalized topology
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

The 4D+4E inbound boundary is visible under the same branch: `variants.request` exposes
the authoritative public filter/sort/facet registries and `variants.intent` exposes the validated
stable-code vocabulary. Generic lookup/operator gating and ordered sort/facet inventories live in
`search-gen2-contract`, but standard scalar/collection/range decoding still has a tracked BeautyQ-owned
extraction gap assigned to Brick 6D. The longest-match/contextual/overlay engine is already generic.
BeautyQ declares public names, typed field policy, stable actions, aliases and domain translation/label policy. The validated
request assigns `ExplicitUi`/`FacetSelection` provenance on the server; the parser emits `ParsedHard`
constraints, canonical semantic labels and the one `GeoProximitySignal`. Noise without contextual
requirements remains in the independent matching phase; the Gen2-only NearUser rule is an explicit
semantic overlay with a hair-removal exclusion. Neither branch constructs a `SearchPlan`, resolves
geo origins, applies precedence or talks to a backend.

The contract root intentionally stops at the contract-module boundary. Wiring-owned
`BeautyQSearchGen2` is the navigation facade; Brick 6D completes its direct links across request/intent,
plan/candidate compilation, materialization, Elasticsearch and Qdrant without copying policy or reversing
the DAG.

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
the document identity and the remaining 43 handles exposed as ordinary fields in static declaration
order followed by dynamic-family order. The root contains no request, intent, plan, facet, group, response, backend or quality
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
had to be corrected retroactively (see the open reusable-framework gaps above). Brick 4 introduces
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

### Fixed ownership and execution path

Brick 5 is four reviewable slices because mapping/source compilation, query/result semantics, live
resource activation and groups fail in different ways. They compose in this order:

```text
MaterializedSearchDocuments + variants.document + BeautyQ Elasticsearch index policy
  -> 5A compiled mapping/source/generation artifact
  -> 5C physical-index build, validation and atomic alias activation

CompiledBeautyQSearchPlan.boundPlan + BeautyQ Elasticsearch query policy
  -> 5B compiled request with typed search_after state
  -> 5C transport execution
  -> 5B decoded hits/exact total/facets/page
  -> BeautyQ baseline result projection

BeautyQ group policy
  -> 5D explicit group request/result extension
```

`search-gen2-elasticsearch` owns domain-neutral mapping, source, request, cursor-state, response and
lifecycle mechanics. It must contain no BeautyQ symbol. `beautyq-search-gen2-wiring` owns the minimal
Elasticsearch policy and composition for `VariantSearchDocumentGen2`; it reuses the exact handles from
`BeautyQSearchDeclarations.variants.Fields` and never recreates fields or maintains a parallel document
inventory.

The contract module cannot expose backend-specific values without reversing the accepted module DAG.
The canonical domain reading path is therefore:

```text
BeautyQSearchDeclarations.variants.Fields/document/plan
  -> BeautyQElasticsearchPolicy in beautyq-search-gen2-wiring
  -> generic search-gen2-elasticsearch compilers
```

This physical split is not permission for a second business root. The wiring policy contains only
choices that can legitimately differ by domain (analyzers, text weights/ranking parameters, exactness
requirements and compatibility version); declaration traversal, JSON/source encoding, typed lookup,
stable tie-breakers, fingerprint composition and lifecycle sequencing remain framework-owned.

### Brick 5A — Pure index contract and generation artifact

#### Purpose

Establish the executable mapping/indexed-document boundary before any HTTP client or live index exists.
This is the authoring surface on which the rest of Brick 5 depends.

#### Diff

In `search-gen2-elasticsearch` add:

- the smallest typed Elasticsearch index-policy algebra actually consumed by this slice;
- a validated policy/declaration composition that requires an analyzer assignment for every declared
  searchable text field and rejects duplicate, undeclared or incomplete assignments;
- declaration-driven mapping compilation for every currently supported `SearchFieldKind`, required and
  optional fields, dotted dynamic paths and deterministic field order;
- declaration-driven `_source` and document-ID compilation using each field's extractor and canonical
  codec, with typed path/encoding errors rather than a domain-owned JSON encoder/fold;
- explicit backend compiler/index-format identity and the one typed Elasticsearch plan-contract
  contribution consumed by `PlanContractFingerprint`;
- one read-only compiled generation artifact binding mapping, ordered indexed documents and complete ES
  generation identity to the exact materialized input and executable policy that produced them.

In BeautyQ Gen2 wiring add:

- `BeautyQElasticsearchPolicy`, declaring only the actual BeautyQ analyzer/index choices over
  `BeautyQSearchDeclarations.variants.Fields` and the explicit compatibility choice;
- one shared BeautyQ contract-identity composition used by both `BeautyQSearchPlanCompiler` and the ES
  generation compiler, replacing the prior `Map.empty` contribution call;
- pure compilation from `MaterializedBeautyQVariantDocuments` to the generic generation artifact.

Do not add query weights, geo scoring parameters or result types in 5A unless they are consumed by the
implemented mapping/source path. Those belong to 5B. Do not add a client, NDJSON transport, alias
lifecycle, DI binding or live Elasticsearch test in 5A; those belong to 5C.

#### Proof

- neutral mapping/source goldens cover keyword, text, integer, long, decimal, boolean, date-time,
  geo-point, optional extraction and a dotted dynamic path;
- policy validation proves complete searchable-text coverage and typed rejection of duplicate/foreign
  handles without adding speculative validation for impossible Scala states;
- source compilation proves canonical scalar/geo values, absent optional fields, nested object shape,
  identity reuse and the document declaration's deterministic field order;
- policy/mapping/document changes alter the appropriate contract or generation identity, while repeated
  compilation of identical input is byte-stable;
- a neutral fixture proves the generic compiler; BeautyQ tests prove the concrete five text fields,
  dynamic attributes and exact declaration identities;
- `BeautyQSearchPlanCompiler` and the generation artifact consume the same Elasticsearch contract
  contribution value; no handwritten final fingerprint exists.

#### Forbidden

- importing or copying Gen1 Elasticsearch interpreters;
- a BeautyQ name, field list or default in `search-gen2-elasticsearch`;
- a domain-owned manual fold over `VariantSearchDocumentGen2`;
- deriving analyzer policy from `FieldSemantic`, public names or enum inventory;
- raw final fingerprint construction or a second mapping/source inventory;
- placeholder full-result, client or lifecycle APIs not executed by 5A.

### Brick 5B — Pure baseline-page request/result and cursor protocol

#### Purpose

Compile a cursor-bound validated plan into one deterministic ES request and decode its response into the
baseline page (hits, total, facets and next cursor). Keep the slice pure so semantic errors are separated
from transport/resource failures; Brick 5D now adds the real generic group component and full-result
aggregate without changing the baseline page contract.

#### Diff

In `search-gen2-elasticsearch` add:

- the role-specific hit, exact/qualified total, facet, page and backend-diagnostic result types actually
  returned by this decoder; groups remain a separate Brick 5D execution/result component, not an empty
  placeholder or a Qdrant slot;
- the query-policy portion consumed by this slice: weighted searchable fields, text operator, explicit
  geo scoring parameters, exact-total requirement and default relevance/tie-break policy;
- `BoundSearchPlan -> Elasticsearch request` compilation for residual text, terms, numeric/date ranges,
  interval overlap, geo signal/filter/sort, explicit field sorts, exact totals and requested facets;
- stable aggregation names derived from typed facet IDs, never normalized field paths;
- a typed Elasticsearch `search_after` codec carried inside Brick 4G-B's opaque backend cursor state;
- response decoding for source hits, scores, exact total/relation, terms/range/interval facet buckets,
  requested-section completeness and the next page cursor;
- typed errors for unsupported plan elements, malformed cursor state, malformed hits, missing requested
  sections, unknown aggregation IDs and non-exact totals when policy requires exactness.

In BeautyQ wiring:

- extend `BeautyQElasticsearchPolicy` only with the ranking/query choices actually consumed here;
- compile only `CompiledBeautyQSearchPlan.boundPlan`, never a raw or independently reconstructed plan;
- decode the initial BeautyQ baseline result without groups or public HTTP projection.

Pagination uses deterministic sort tuples and the declared identity as the final tie-breaker. The request
fetches enough information to determine whether a next cursor exists; the next cursor is issued from the
same `BoundSearchPlan`, never from independent plan/hash facts.

#### Proof

- exact JSON goldens for text, terms, every bound shape, interval overlap and the three independent geo
  operations;
- lifecycle authorization keeps cursor-carried generation references untrusted, rejects missing geo policy,
  non-finite scalar policy values and unsupported identity tie-breaker kinds, and has compile-negative
  construction proofs;
- facet aggregation/decoder fixtures for terms, number ranges and interval-overlap buckets, including
  half-open boundaries, both-unbounded no-op ranges, typed keyword/integer/long/boolean keys, precision
  errors and a missing/unknown requested section;
- default and explicit sorts end in the identity tie-breaker and round-trip a real
  `issue -> opaque transport -> fromTransport -> bind -> search_after` sequence;
- changed query/filter/sort/facet/page size/contract contribution rejects an old cursor through the
  existing typed plan-identity boundary;
- result decoding preserves exact totals beyond the hit window and facet counts independently of the
  returned page size in fixture responses;
- neutral compiler/decoder fixtures plus BeautyQ policy goldens; no mutable call-count spies.

#### Forbidden

- compiling an unbound `SearchPlan` or interpreting cursor envelope details in the domain module;
- offset/from pagination;
- coordinate presence implicitly enabling geo scoring/filter/sort;
- counting facets from returned hits;
- silently dropping unsupported constraints/facets or missing response sections;
- live client/lifecycle code or group/carousel behavior.

### Brick 5C — Physical index lifecycle and independent baseline service

#### Purpose

Execute the accepted 5A/5B artifacts against an independent Gen2 Elasticsearch resource without moving
semantic compilation into transport code. This is where complete live Elasticsearch acceptance - backend
limits such as the `_id` size cap and acceptance of the emitted mapping with BeautyQ's built-in
`standard` analyzer - is owned; Brick 5A validates only document shape
(`EmptyDocumentId`/`ValueEncoding`). Brick 5C-D now closes the initial analyzer vocabulary to
framework-known built-ins before an analyzer can reach index creation.

#### Diff

- add or extract a minimal neutral JSON/NDJSON Elasticsearch client; carry only transport concerns and
  its focused client tests, with no Gen1 interpreter or BeautyQ policy dependency;
- compile bulk ingestion from the 5A generation artifact and record its complete generation identity and
  build metadata in the physical index;
- implement `build -> ingest -> refresh -> validate mapping/metadata/count/fingerprint -> atomic alias
  switch -> discover only lifecycle-marked superseded generations -> exact cleanup` (Brick 5C-D completed);
- implement baseline request execution by sending the 5B request and passing the raw response back to the
  5B decoder;
- compose the independent Gen2 resource names, lifecycle and baseline service in
  `beautyq-search-gen2-wiring` without adding a V1 route or production fan-out. Repository loading and
  materializer execution remain Brick 8 application-composition responsibilities; 5C starts from the
  already materialized/compiled generation artifact.

Physical generation reuse requires the complete ES generation identity. A matching source fingerprint
alone is insufficient. Alias mutation is one atomic Elasticsearch aliases operation performed only after
every validation succeeds.

#### Proof

- scripted client contract tests prove endpoint/method/body semantics and typed failures;
- lifecycle tests prove failed create/ingest/validation leaves the old alias untouched and successful
  validation performs one atomic activation that marks old targets superseded and installs the new active alias;
- generation mismatch creates a new physical index; complete identity match permits reuse;
- a resource-backed default-local Elasticsearch fixture proves mapping, bulk ingestion, exact totals
  beyond 256 documents, complete-set facet counts, cursor pagination and alias activation; it cancels
  with a useful reason when the external resource is unavailable;
- focused BeautyQ composition proves compiled generation -> activation -> baseline search with separate
  Gen2 names. Brick 8 proves the preceding repository materialization composition. Coordinator/user full
  verification remains required for production graph/readiness.

#### Forbidden

- alias activation before count/fingerprint/mapping validation;
- in-place mutation of the active physical index;
- a boolean environment gate for the normal local resource-backed test;
- whole-plugin test modules, Gen1 resource names or V1 request fan-out;
- recomputing mapping, source, request or response semantics inside the lifecycle service.

### Brick 5D — Exact provider/service groups and carousels (completed)

Elasticsearch now executes each declared group as a dedicated composite query over the already compiled
main execution query and returns typed keys, representatives, counts, metrics, precision and deterministic
order. BeautyQ declares the provider/location and service group policy through canonical `Fields.*` handles
and owns only the final carousel projection. Counts are not reconstructed from the hit window, exactness is
enforced, and geo ordering exists only when explicitly declared. Neutral, BeautyQ and real-resource proofs
close the Brick 5 gate.

### Brick 5 closeout gate

Brick 5 is complete only when:

1. the BeautyQ authoring surface consists of one compact ES policy over canonical `Fields.*` plus
   operational resource configuration and the fixed post-activation superseded-generation cleanup;
2. generic modules contain no BeautyQ names and neutral fixtures cover mapping, source, request, result,
   cursor and lifecycle mechanics;
3. one executable policy supplies every mapping/query choice and its contract contribution; generated
   mapping, trace/goldens and generation identity are derived views;
4. the independent BeautyQ baseline returns hits, exact total, facets, groups and next cursor from ES;
5. failed builds cannot change the active alias and physical reuse requires complete generation identity;
6. Gen1 runtime/routes/resources remain untouched.

### Expected diff shape

```text
search-gen2-elasticsearch/src/main/...              policy + pure compilers + client/lifecycle
search-gen2-elasticsearch/src/test/...              neutral goldens + lifecycle/resource proofs
beautyq-search-gen2-wiring/...                      BeautyQ ES policy/composition/projection
beautyq-search-gen2-wiring/src/test/...             concrete policy + baseline/group scenarios
```

### Rollback

Remove the independent Gen2 ES vertical/resources. V1 ES remains active and untouched.

## Brick 6A — Pure Qdrant candidate contract (completed)

### Purpose

Define the backend-neutral Qdrant policy and deterministic artifacts without opening a network or
resource lifecycle.

### Diff

Implemented in `search-gen2-qdrant`: closed distance/payload vocabulary, validated policy with
declaration-derived payload indexes and separate collection/candidate fingerprints, typed embedding
input/result boundaries, deterministic generation metadata and collection JSON, typed payload
projection, filtered `CandidatePlan` request JSON, and candidate-only response decoding with
order-preserving deduplication and diagnostics. BeautyQ exposes only its canonical variant fields,
embedding model and retrieval choices through `BeautyQQdrantPolicy`.

### Boundary kept explicit

- no Qdrant transport, collection lifecycle, HTTP client, hydration or route activation;
- no Qdrant facet/group/page result ownership;
- no dependence on Gen1 Qdrant interpreter or supplement policy.

### Focused proof

- declaration-derived payload schema/index validation;
- typed scalar payloads and filter JSON for terms, numeric, interval and geo constraints;
- input/model/dimension and finite-vector binding;
- deterministic generation identity and metadata;
- candidate response status, hit-window, typed IDs, order and duplicate diagnostics.

### Delivered shape

```text
search-gen2-qdrant/src/main/...                     pure policy, compiler and decoder
search-gen2-qdrant/src/test/...                     neutral contract proofs
beautyq-search-gen2-wiring/...                      BeautyQ policy composition only
```

### Rollback

Remove the pure Gen2 Qdrant policy/compiler branch. Gen1 Qdrant remains untouched.

## Brick 6B — Qdrant transport, generation lifecycle and candidate execution

Brick 6B is delivered as two cohesive patches so transport extraction and resource mutation remain
independently reviewable.

### Brick 6B-A — Neutral JSON transport and exact Qdrant wire client (completed)

The second Gen2 backend makes the endpoint/path/timeout/header/JSON/error mechanics currently embedded in
the Elasticsearch client a source-confirmed second consumer. The completed slice adds
`search-gen2-transport`, moves those domain- and backend-neutral mechanics there, and keeps a thin
Elasticsearch adapter with unchanged observable behavior. Both backend projects now depend on the
neutral transport project; `search-gen2-core` remains free of HTTP concerns.

The Qdrant 1.18.3 wire client now runs over that transport with exact methods for collection details, aliases,
collection creation, payload-index creation, waited point upsert, exact count, atomic alias update and
`POST /collections/{target}/points/query`. Query parameters and optional `api-key` are typed transport
inputs; method/path/body choices are owned by the Qdrant adapter, not its callers. Scripted tests use the
documented Qdrant response envelopes. The candidate contract fingerprint includes the Qdrant compiler
and REST protocol versions that define this executable wire shape. No lifecycle, embeddings, hydration
or orchestration belongs here.

### Brick 6B-B — Convergent physical lifecycle and candidate service (active)

Consume only the compiler-owned 6A generation and candidate artifacts. For one deterministic physical
collection, create or validate its named-vector configuration and persisted `search_gen2` metadata,
create only missing matching payload indexes, upsert the complete ordered point set with `wait=true`,
require an exact point count, then atomically switch the configured alias. A partial compatible build is
completed by retry; incompatible metadata, vector configuration, payload schema or count fails typed and
never changes the alias.

Successful physical generations are not automatically deleted in the initial Qdrant lifecycle. Qdrant's
alias switch is atomic, but its collection deletion has no ES-style conditional alias guard; guessing that
a non-active collection is safe would repeat the destructive lifecycle race found by the pre-5D audit.
There is no retention enum or cleanup policy. The lifecycle also authorizes a candidate request only when
its candidate fingerprint matches the policy and the alias resolves to one metadata-compatible physical
collection. Candidate execution uses `/points/query` and delegates decoding to the 6A decoder.

The generic metadata codec, convergent lifecycle and target-authorizing candidate service are implemented.
Focused neutral and BeautyQ proofs are green. The communication proof still requires a separate local
Qdrant 1.18.3 resource; until it executes, Brick 6B-B remains active. Gen1 remains on 1.15.4 and
`/points/search`.

## Brick 6C — BeautyQ query embedding and candidate hydration (implemented)

The generic core now owns typed identity extraction, ordered candidate lookup, post-hydration constraint
assertion and generation-evidence checks. Qdrant owns the synchronous typed embedding port, the
prepare -> embed -> complete -> authorized-service pipeline and a service-owned result carrying the
authorized physical target and persisted metadata. BeautyQ composes those values with its semantic
candidate evaluation and declares only the initial `Fail`/`RequireAll` hydration policy plus
`semantic-supplement` provenance.

The final hydrated result remains candidate-only and read-only. Missing IDs, duplicate document identities,
generation fingerprint mismatch and hard-constraint violations are typed integrity failures, not graceful
Qdrant outages. No totals, facets, groups, public pagination, baseline fallback or append policy enters
this brick. The Qdrant 1.18.3 communication proof remains the separate 6B-B gate.

The pre-7 compliance audit accepted the production ownership and candidate-only result shape, but found
two proof gaps that Brick 6D must close before orchestration consumes this seam: one real BeautyQ eligible
path through intent -> plan -> candidate evaluation -> authorized Qdrant execution -> hydration, and direct
neutral hydrator coverage for projected-document, projection-format, point-count and hard-constraint
integrity failures. These are proof closeout, not a redesign of 6C.

## Brick 6D — Pre-orchestration authoring, navigation and proof closure (next)

### Why this is one brick

The pre-7 audit found one repeated-mechanics extraction gap and several incomplete views/proofs at the
same domain-authoring seam. Splitting them into independent micro-fixes would let the public-input API,
BeautyQ migration, canonical facade, onboarding and acceptance evidence drift again. Brick 6D is one
cohesive closeout: a new domain gets one reusable inbound authoring path, and the canonical BeautyQ path
proves that exact path through the already accepted 6C boundary. It does not implement supplement
orchestration.

### Current compliance state

| Vertical | Status before 6D | Required owner/action |
|---|---|---|
| catalog -> document -> `Fields` | green | preserve the canonical typed handles |
| snapshot -> projection -> materialization | green | no redesign |
| public request | **changes required** | move standard value/range decoding mechanics from BeautyQ into `search-gen2-contract` |
| intent -> `SearchPlan` | green | expose the parser/compiler path from the facade without wrapping it |
| Elasticsearch full result | green | preserve complete baseline ownership |
| Qdrant candidate/hydration | **almost green** | add the missing BeautyQ eligible proof and neutral integrity-error matrix; keep 6B-B's resource gate separate |
| baseline-plus-supplement | planned | Brick 7, after the ownership contract below is fixed |
| application/evaluation/cutover | planned | Bricks 8 and 9 |

### Generic inbound authoring closure

Add a high-level backend-neutral public-filter declaration/decoder kernel in `search-gen2-contract` while
retaining `PublicInputSpec`/`PublicInputRegistry` as the low-level escape hatch. The high-level path owns:

- scalar and non-empty collection shape decoding;
- canonical codec decoding, indexed error accumulation and canonical duplicate detection;
- one-sided inclusive/exclusive bound construction;
- ordered `between` validation;
- ordinary value/range, interval-overlap and geo-distance clause construction;
- capability-derived default public operators plus explicit validated narrowing.

The domain still declares every choice that may differ: public name, exact `Fields.*` handle(s), ordinary
value versus interval versus distance meaning, accepted-operator narrowing, dynamic-family public-name
composition, presentation/provenance policy and domain-facing error wording when it is genuinely policy.
Public names remain explicit and never derive from `SearchField.path`.

Migrate `BeautyQPublicFilterRegistry` to one ordered vector of these generic declarations. Remove its local
`ValueSpec`/`PriceSpec`/`DistanceSpec`, scalar/many/decode-many/range/bounds mechanics and the false comment
that calls private BeautyQ helpers reusable. Preserve exact public names, order, field handles, accepted
operators, decoding errors, accumulation order, interval price semantics, geo distance semantics and
provenance.

Prove the reusable boundary with a neutral document containing keyword, ordered numeric, interval,
geo and dynamic-family declarations. Tests must cover valid forms, wrong shapes, all single-bound
operators, inclusive/exclusive `between`, complete indexed error accumulation, canonical duplicates,
capability-derived operators, explicit narrowing and wrong-document compile rejection.

### Complete the readable executable path

Extend `BeautyQSearchGen2` only with direct references so a reviewer can navigate in data-flow order:

```text
contract/request
-> intent parser
-> SearchPlan compiler
-> candidate compiler
-> Elasticsearch baseline
-> Qdrant candidate/hydration pipeline
```

The facade owns no adapter, policy, list, renderer or wrapper. Its focused identity spec must cover every
implemented member, including Qdrant policy/resources/runtime/hydration/pipeline and the newly visible
intent/candidate compilers.

### Close 6C proof gaps

Add one BeautyQ-shaped eligible end-to-end candidate proof using the canonical request, intent, plan,
candidate policy, materialized document and hydration policy. It must preserve bound hard constraints,
candidate order, scores, `semantic-supplement` provenance, authorized target and generation metadata,
while exposing no total/facet/group/page surface.

Extend the neutral hydration suite with direct proofs for `ProjectedDocumentsMismatch`,
`ProjectionFormatMismatch`, `PointCountMismatch` and hydrator-level `ConstraintViolations`. These errors
remain integrity failures; Brick 7 must not degrade them into baseline-only success.

### Documentation closeout owned by 6D

- update the technical-spec `BeautyQSearchGen2` tree to include the real Qdrant branch and complete
  request/intent/candidate navigation;
- remove onboarding's stale claim that group/carousel result types are future work;
- stop presenting `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md` as the canonical Gen2 architecture owner;
  classify its Gen1-specific hard-constraint, lifecycle and route material as historical/evaluation
  evidence, while the Gen2 technical spec/ADR own current architecture;
- make onboarding show only the new high-level public-filter declarations, not a copied BeautyQ decoder;
- describe `ConstraintPrecedence.sourceOrder` as typed reviewer-readable order unless a real stable external
  ID contract is introduced; do not add defensive labels to unrelated enums;
- keep exact APIs in the technical spec, live gaps/order in this plan and the minimal new-domain path in
  onboarding; do not reproduce source or test-by-test narration.

### Proof and acceptance

- BeautyQ public names/order/handles/operators and every accepted/rejected request scenario remain exact;
- the neutral high-level decoder proves reuse independently of BeautyQ;
- no standard decode traversal or bounds construction remains in BeautyQ;
- the facade is a complete direct-reference navigation view with no copied policy;
- the BeautyQ eligible candidate path and neutral hydration error matrix are green;
- generic Gen2 modules remain free of BeautyQ symbols and Gen1 dependencies;
- docs no longer overstate the old registry, facade or Gen1 supplement document;
- Brick 6B-B remains independently active until its Qdrant 1.18.3 resource proof executes.

### Pain-to-promise closeout owned by the remaining sequence

| Gen1 pain / Gen2 promise | Current result | Remaining owner |
|---|---|---|
| executable root instead of descriptive readiness and compatibility facades | incomplete navigation view | Brick 6D |
| reusable domain authoring rather than copied registries/decoders | lookup is generic; standard decode mechanics are not | Brick 6D |
| Qdrant candidate filtering/hydration with no invented full-result semantics | production shape accepted; proof incomplete | Brick 6D plus 6B-B resource gate |
| closed public filter/facet/result loop and honest `appliedFilters` response | backend contracts exist; public composition is absent | Brick 8 |
| lifecycle reflects persisted state | ES proven; Qdrant scripted and focused, communication proof pending | Brick 6B-B |
| one backend role must not erase another | ES full result and Qdrant candidate result are separate; merge not yet executable | Brick 7 |
| no-harm supplement semantics | accepted ADR only; executable membership/degradation/append policy absent | Brick 7 |
| serving code isolated from evaluation and Gen1 | module firewall exists; eval/app composition is not built | Brick 8 |
| one final migration rather than compatibility layers | side-by-side build preserved | Brick 9 |

### Remaining order after the audit

```text
Brick 6D  reusable inbound authoring + readable facade + 6C proof/docs closeout
6B-B      independent Qdrant 1.18.3 communication gate
Brick 7   baseline membership + supplement orchestration
Brick 8   independent application/API/eval and cutover evidence
Brick 9   atomic route cutover and Gen1 deletion
```

Brick 7 must not start until Brick 6D is accepted. Brick 6B-B's resource proof may run independently,
but Brick 8 full-search readiness cannot pass without it.

## Brick 7 — Implement baseline-plus-supplement orchestration

### Purpose

Combine role-specific backend outputs under the narrow accepted no-harm policy.

Entry requires 6B's authorized candidate execution and 6C's generation-consistent hydrated candidate
result. Brick 7 does not reopen their transport, lifecycle, embedding or hydration decisions.

### Diff

In `search-gen2-core` add generic orchestration primitives where domain-neutral.

In `beautyq-search-gen2-wiring` add only the remaining BeautyQ composition policy:

- ES baseline `FullSearchResult`;
- the bound `BeautyQQdrantCandidatePipelineResult` produced by the accepted candidate/hydration owner;
- max-one append-only selection;
- baseline current-page deduplication;
- baseline full-match membership guard;
- separate baseline total and supplement count;
- fixed startup/readiness policy: ES baseline required, Qdrant outage exposes explicit `baseline_only` mode and `supplementReady = false`;
- fixed request-time policy: embedding transport/unavailability and Qdrant timeout/transport/backend
  failure return unchanged ES output with `supplement_failed` and a stable typed reason code;
- fixed integrity policy: embedding input/model/dimension/completion mismatch, plan/capability errors,
  generation evidence mismatch and hydration/constraint violations never degrade to baseline-only success;
- explicit degradation diagnostics, with no hidden retry or fallback candidate backend.

Eligibility, hard-constraint assertion and candidate provenance are inputs already bound by
`BeautyQCandidatePlanCompiler` and `BeautyQQdrantCandidatePipeline`; the orchestrator must not evaluate,
replace or reconstruct them. It may inspect the bound result only to choose append/no-append and render
the final supplement status.

Add a domain-neutral Elasticsearch membership compiler/service that evaluates exact candidate IDs against
the same lifecycle-authorized baseline plan/generation. BeautyQ orchestration must not build raw membership
JSON or accept an independently supplied query, filters or physical target.

### Forbidden

- score fusion;
- reranking;
- fallback ownership;
- supplement under price/duration/distance sort;
- changing ES facets/groups/totals to include the supplement implicitly.
- recomputing page/sort eligibility, hard constraints or candidate provenance;
- raw Elasticsearch membership JSON in BeautyQ code;
- treating embedding/configuration/integrity failures as ordinary Qdrant degradation.

### Proof

- ES prefix and order are unchanged;
- no ES result is removed;
- no candidate appears on a later baseline page;
- supplement runs only on eligible page/sort;
- ES baseline or document-lookup failure prevents serving readiness;
- Qdrant startup failure keeps baseline serving explicit but fails full-search/cutover readiness;
- request-time Qdrant failure returns the unchanged baseline with `supplement_failed`;
- request-time embedding transport/unavailability returns the unchanged baseline with its own stable reason;
- embedding completion/model/dimension errors remain typed integrity failures;
- plan/capability errors and hydration invariant violations are not degraded into baseline-only success;
- no-hard-constraint-violation gate is green;
- provenance and supplement count are correct.

### Expected diff shape

```text
search-gen2-core/...                                generic orchestration types
search-gen2-elasticsearch/...                       bound baseline-membership compiler/service
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
- wire the complete repository snapshot -> materializer -> generation compilation -> activation path;
- suspend the synchronous Elasticsearch client behind the application effect/runtime blocking boundary;
- cache the lifecycle-authorized active generation for first-page searches and invalidate that cache
  only after activation; a normal first page must not perform mapping, count and alias discovery again;
- wire Qdrant and baseline-plus-supplement orchestration;
- project one explicit public Gen2 response from the validated request, compiled plan, authoritative ES
  result and bound supplement result: `appliedFilters` with provenance, exact baseline total/facets/groups,
  cursor, carousel projections, supplement count/status/reason and per-hit origin have one response owner;
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
- runtime tests prove snapshot materialization precedes generation compilation/activation and failures
  cannot expose a partially activated generation;
- repeated first-page requests reuse the cached active generation with no three-round-trip lifecycle
  rediscovery; activation deterministically invalidates and replaces the cached value;
- blocking Elasticsearch calls execute only on the declared blocking/suspended boundary;
- semantic delta ledger fixtures pass;
- exact facet and group authoritative fixtures pass;
- pagination/supplement no-duplicate tests pass;
- route-level tests close the public filter/facet/applied-filter/result loop without a Gen1 codec adapter;
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

The public boundary also owns pagination recovery and abuse limits: a typed stale-generation cursor
failure becomes an explicit restart-pagination response rather than an internal error; request/query/
cursor and response sizes plus rate limits are enforced before expensive backend work; route-level tests
close the validated request -> plan -> baseline/supplement -> response loop before ownership switches.

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
- a stale cursor produces the documented restart-pagination contract, never an HTTP 500;
- request/response size and rate-limit boundaries fail deterministically before backend execution;
- route-level fixtures prove applied filters, facets, totals, groups, pagination and supplement
  diagnostics survive the complete public response loop;
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
search-gen2-es: compile deterministic index generations
search-gen2-es: compile baseline requests and full results
search-gen2-es: activate validated index generations
search-gen2-es: implement exact group projections
search-gen2-qdrant: implement filtered candidate vertical
beautyq-search-gen2: add no-harm supplement orchestration
beautyq-search-gen2: add independent application composition
search: cut over to Gen2 and remove Gen1
```
