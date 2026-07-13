# BeautyQ Search Framework Gen2 — technical specification

Status: proposed  
Scope: additive Gen2 architecture in the existing repository  
Compatibility rule: V1 `/beauty-search` behavior and JSON remain available through adapters until an explicitly versioned migration.

## 1. Goal

Build a second-generation search framework in which one lossless, typed domain declaration is compiled into:

- catalog snapshot loading;
- search-document projection and validation;
- public request/filter schema;
- intent parsing and plan compilation;
- Elasticsearch mapping, ingestion, request and response handling;
- Qdrant collection/payload, indexing, filter and search handling;
- domain response projection;
- fingerprints, compatibility validation and documentation views;
- quality/evaluation inputs and gates.

The framework must remove mechanical duplication while keeping true business decisions explicit.

## 2. Non-goals

Gen2 does not initially require:

- automatic derivation of document projection from catalog topology;
- CDC/outbox implementation in the first milestone;
- Qdrant-only production serving;
- cross-backend score fusion or reranking;
- route JSON breakage;
- raw ES/Qdrant JSON in domain declarations;
- automatic generation of intent vocabulary from catalog names;
- a single physical source file containing every declaration.

## 3. Architectural principles

### 3.1 One executable contract, multiple semantic branches

The domain root must expose two separate concepts:

- normalized catalog topology/snapshot source;
- denormalized search contract.

They are visible under one root but are not collapsed into one graph.

### 3.2 Common semantic plan, backend-specific policy

Common semantics define what the request means. Backend policy defines how a backend realizes it.

```text
public request + parsed intent
          ↓
       SearchPlan
          ↓
 ┌────────┴────────┐
 ES compiler     Qdrant compiler
```

Backend-specific features must remain typed extensions rather than being forced into a lowest-common-denominator model.

### 3.3 Lossless documentation views

A `SearchDomainSpec`-like documentation model may exist only as:

- a generated view of the executable contract; or
- a lossless wrapper around executable sections.

No manually maintained projection may silently drop semantics.

### 3.4 Explicit projection

The domain owns:

- joins;
- invariants;
- schema validation;
- text composition;
- stable IDs/codes;
- display fields;
- embedding source composition.

Framework helpers may supply indexing, joins and error accumulation but must not guess business meaning.

## 4. Proposed module ownership

### `search-contract-core`

Owns stable domain-independent contract types:

- field capabilities;
- public filter schema;
- constraint algebra;
- search plan;
- backend result algebra;
- facet/group result algebra;
- compile diagnostics;
- generated documentation model.

It must not depend on ES, Qdrant, BeautyQ, repositories or HTTP.

### `search-core`

Owns executable generic helpers:

- plan compilation primitives;
- field extraction/validation;
- result normalization;
- pagination abstractions;
- fingerprinting;
- generic supplement policy.

### `search-elasticsearch`

Owns:

- ES mapping compiler;
- ingestion compiler;
- `SearchPlan -> ES request` compiler;
- `ES response -> BackendSearchResult` decoder;
- ES-specific typed policy;
- compatibility validation.

### `search-qdrant`

Owns:

- collection/payload-index compiler;
- document point compiler;
- `SearchPlan -> Qdrant filter/vector request` compiler;
- hit decoder;
- Qdrant-specific typed policy;
- compatibility validation.

### BeautyQ modules

Own:

- catalog topology and repository evidence;
- snapshot source selection;
- variant projection;
- field semantics;
- constraint meaning;
- intent vocabulary;
- response composition;
- backend policy values;
- quality corpus and thresholds.

### `search-eval` or test/tooling module

Move offline M8–M21 scaffolding and report-generation code out of `beautyq-search-wiring` main runtime dependency unless it is required by serving.

## 5. Core Gen2 types

The names below are normative concepts; exact Scala names may change during implementation.

### 5.1 Field declaration

```scala
final case class SearchFieldV2[Document, Value](
  id: FieldId,
  path: FieldPath,
  extract: Document => Option[Value],
  codec: SearchValueCodec[Value],
  semantic: Option[FieldSemantic],
  capabilities: FieldCapabilities,
  text: Option[TextFieldPolicy],
)

final case class FieldCapabilities(
  searchable: Boolean,
  filterOperators: Set[FilterOperator],
  facetable: Set[FacetMode],
  sortable: Boolean,
  groupable: Boolean,
  payloadEligible: Boolean,
)
```

Validation must reject policies that reference unsupported capabilities.

### 5.2 Domain constraints

Business constraints remain explicit and domain-owned. They compile into a reusable backend-neutral algebra.

```scala
sealed trait PlannedConstraint[Document]
object PlannedConstraint {
  final case class Terms[Document, A](field: SearchFieldV2[Document, A], values: Set[A])
  final case class NumberRange[Document, A](field: SearchFieldV2[Document, A], bounds: RangeBounds[A])
  final case class IntervalOverlap[Document, A](from: SearchFieldV2[Document, A], to: SearchFieldV2[Document, A], bounds: RangeBounds[A])
  final case class BooleanTerm[Document](field: SearchFieldV2[Document, Boolean], value: Boolean)
  final case class GeoDistance[Document](field: SearchFieldV2[Document, GeoPoint], origin: GeoPoint, radius: Option[Distance])
}
```

`RangeBounds` must encode inclusivity explicitly.

### 5.3 Search plan

```scala
final case class SearchPlan[Document](
  residualText: Option[String],
  hardConstraints: Vector[PlannedConstraint[Document]],
  softSignals: Vector[PlannedSignal[Document]],
  sort: Vector[PlannedSort[Document]],
  page: PageRequest,
  requestedFacets: Vector[FacetRequest[Document]],
  requestedGroups: Vector[GroupRequest[Document]],
  diagnostics: PlanDiagnostics,
)
```

The plan is the single input to backend compilers.

### 5.4 Backend result

```scala
final case class BackendSearchResult[Document, Id](
  hits: Vector[BackendHit[Document, Id]],
  total: TotalHits,
  facets: Map[FacetId, FacetResult],
  groups: Map[GroupId, GroupResult[Id]],
  page: PageResult,
  diagnostics: BackendDiagnostics,
)
```

The ES decoder must populate all requested sections. Qdrant may return unsupported sections only when the plan/compiler explicitly marks them unsupported; silent omission is forbidden.

### 5.5 Public filter schema

```scala
final case class PublicFilterField[Constraint](
  name: PublicFieldName,
  operators: Set[PublicOperator],
  decode: PublicFilterValue => Either[FilterError, Constraint],
)
```

The public name is used by a real request codec, not only fingerprint/tests.

## 6. BeautyQ request V2

Add an internal/request model with at least:

```scala
final case class BeautySearchRequestV2(
  query: String,
  filters: List[PublicFilterInput],
  selectedFacets: List[SelectedFacetInput],
  sort: List[SortInput],
  page: PageInput,
  userLocation: Option[GeoPoint],
  limit: Int,
)
```

Rules:

1. V1 `UserSearchInput` adapts to V2 with empty structured fields.
2. Explicit UI filters have higher authority than parser-inferred constraints.
3. Conflicts must produce deterministic validation errors or a documented precedence result.
4. The response must return `appliedFilters` internally with origin:
   - `explicit_ui`;
   - `parsed_text`;
   - `inferred_response`.
5. V1 adapter maps the internal structure back to the existing `inferredFilters` JSON field.

## 7. Elasticsearch Gen2 requirements

### 7.1 Mapping

Typed ES policy must support:

- analyzer/search-analyzer;
- keyword/text multi-fields where declared;
- numeric/boolean/geo mappings;
- dynamic attribute object policy;
- mapping/index settings supplied by policy, not hard-coded globally;
- versioned physical index name and stable alias.

### 7.2 Request compilation

Compile the plan into:

- lexical query;
- hard filters;
- soft scoring signals;
- geo scoring/filtering;
- typed facets;
- typed groups;
- sort;
- pagination/search-after;
- total-hits policy.

The compiler must reject unsupported combinations with a typed error.

### 7.3 Response decoding

Decode:

- hits and score;
- exact/relation total hits;
- every requested terms/range facet;
- every requested group aggregation;
- search-after cursor where applicable;
- optional diagnostics.

Unknown/missing requested aggregation names must fail with a useful error rather than silently returning empty counts.

### 7.4 Response ownership

BeautyQ response projection consumes `BackendSearchResult`. It may apply domain presentation/ranking policy, but it must not recompute ES-owned facet counts from the hit window.

## 8. Qdrant Gen2 requirements

### 8.1 Payload projection

Declare a dedicated Qdrant payload projection containing all fields required for pushed-down constraints. At minimum for current BeautyQ constraints:

- variant ID;
- service/category stable ID or code;
- price-from and price-to;
- duration;
- dynamic enum/boolean/int/decimal attributes;
- location if geo filtering is enabled.

Display names may remain payload fields when useful but must not be primary filter identity.

### 8.2 Payload indexes

The Qdrant compiler must emit/validate required payload indexes for fields used by filter compilation.

### 8.3 Filter compilation

Compile supported `PlannedConstraint` values into Qdrant filters before vector retrieval. Unsupported constraints must be reported explicitly. Post-hydration checks remain a defense-in-depth verification, not the primary filter.

### 8.4 Candidate retrieval

Policy must declare:

- vector name;
- embedding source;
- top-K;
- oversampling;
- score threshold;
- hydration requirements;
- supplement budget;
- deduplication key.

The existing no-harm top-1 supplement remains the initial serving policy.

## 9. Catalog and index-source requirements

Introduce:

```scala
trait SearchSnapshotSource[F[_, _], Snapshot] {
  def loadVersioned(): F[QueryFailure, VersionedSnapshot[Snapshot]]
}
```

A version contains at least:

- source identifier;
- snapshot version/fingerprint;
- captured timestamp;
- record counts.

Required first implementation:

1. seed is inserted into repositories;
2. search snapshot is loaded back through repositories/catalog loader;
3. projection runs on the loaded snapshot;
4. parity test compares repository-loaded documents with direct-seed documents during migration;
5. index metadata stores the snapshot fingerprint;
6. physical index/collection is built under a versioned name;
7. alias/active pointer switches only after readiness checks.

CDC/outbox remains a later implementation behind the same source/update interface.

## 10. Semantic decisions that must be recorded before implementation

The Gen2 ADR must choose exact semantics for:

1. `PriceRange`: `priceFrom` containment, full interval containment, or interval overlap.
2. Numeric range boundaries: inclusive/exclusive per side.
3. Facet counts under selected filters: all filters, self-excluding/disjunctive, or another explicit policy.
4. Geo intent: coordinates always influence ranking, only `NearUser`, or explicit UI sort/filter.
5. Stable service/category identity: ID or domain code.
6. Parser/UI-filter conflict precedence.
7. Empty residual query behavior.
8. Total-hits exactness threshold.
9. Pagination model for ES and supplement composition.
10. Whether Qdrant may ever own facets/groups; initial answer should be no.

## 11. Compatibility and migration rules

- V1 route remains ES-owned and behavior-locked until the ES Gen2 parity gate passes.
- V2 core can run behind V1 adapters.
- No alias/facade deletion in the same diff that changes serving behavior.
- No Qdrant payload expansion and serving activation in the same diff.
- Every behavior-changing iteration requires a rollback path to the previous compiler/adapter.
- Generated docs/fingerprint changes must be reviewed independently from runtime activation.

## 12. Required validation

### Pure tests

- field capability validation;
- constraint semantics, especially interval/range boundaries;
- request-to-plan precedence;
- ES/Qdrant compiler golden JSON;
- ES response decoding including totals/facets/groups;
- V1/V2 adapter parity;
- generated documentation view losslessness;
- fingerprint sensitivity.

### Integration tests

- repository snapshot -> projection parity;
- ES mapping/index/search/facet/group/total behavior;
- Qdrant payload indexes/filtering/search;
- versioned rebuild + alias switch;
- startup failure leaves previous active index intact.

### Quality gates

- no lost/reordered ES baseline IDs for supplement policy;
- exact facet count parity against an authoritative test dataset;
- structured-filter round trip;
- zero constraint violations;
- duplicate rate;
- Recall@K/NDCG/MRR where expected labels exist;
- p50/p95 latency;
- index freshness/fingerprint consistency.

## 13. Definition of done

Gen2 is complete when:

1. serving compiles one executable BeautyQ contract into backend behavior;
2. no manually maintained lossy `SearchDomainSpec` projection is authoritative;
3. ES response totals/facets/groups come from decoded ES results;
4. public structured filters complete the UI round trip;
5. index documents are derived from a versioned repository snapshot;
6. Qdrant hard constraints are pushed into payload filters where supported;
7. V1 compatibility is supplied by an adapter, not duplicate implementation;
8. evaluation scaffolding is separated from the serving dependency graph;
9. compatibility aliases have an explicit deprecation/removal state;
10. architecture docs are generated from or directly reference the executable declaration.
