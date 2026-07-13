# BeautyQ Search Framework Gen2 — side-by-side technical specification

Status: **accepted architecture baseline; implementation pending**
Scope: an independent Gen2 module graph built beside Gen1
Delivery rule: no V1 runtime migration; one final cutover followed by Gen1 deletion

## 1. Goal

Build a second-generation search framework in new modules without depending on the existing Gen1 search framework.

Gen2 must expose one typed, executable domain facade from which domain-free compilers and runtime services obtain:

- catalog snapshot requirements;
- explicit search-document projection and validation;
- public request/filter/facet/sort/page schema;
- intent vocabulary and plan compilation;
- Elasticsearch mapping, ingestion, request and full response decoding;
- Qdrant collection, payload indexing, filtered candidate retrieval and decoding;
- baseline-plus-supplement orchestration;
- BeautyQ response projection and provenance;
- contract and content fingerprints;
- generated documentation views;
- references to quality corpora, metrics and gates.

The Gen1 search runtime remains buildable and runnable and is not modified to host Gen2 abstractions or adapters. Accepted additive changes to shared domain models and their derived HTTP JSON, such as stable service/category codes, are allowed before cutover.

## 2. Delivery model

```text
Gen1 search runtime remains untouched;
accepted shared domain/API additions are allowed
        │
        ├── Gen2 is constructed in a separate module DAG
        │   and can be run/evaluated independently
        │
        └── after the Gen2 cutover gate passes:
            one application binding/route cutover
            + deletion of Gen1 search modules and aliases
```

Small implementation bricks are required. Incremental ownership migration inside Gen1 is forbidden.

## 3. Non-goals

Gen2 does not initially require:

- automatic derivation of document projection from catalog topology;
- CDC/outbox/event streaming before the snapshot architecture is complete;
- Qdrant-owned facets, groups or public pagination;
- Qdrant-only serving;
- cross-backend score fusion or reranking;
- raw Elasticsearch or Qdrant JSON in BeautyQ declarations;
- automatic vocabulary generation from display names;
- compatibility adapters that route V1 runtime through Gen2;
- byte-for-byte V1 request/response parity where the semantic delta ADR declares a change;
- preserving historical M8–M21 evaluation scaffolding in the serving classpath.

## 4. Architectural principles

### 4.1 Separate normalized and denormalized branches

The domain root exposes both:

- normalized catalog topology/snapshot policy;
- denormalized search contract.

They are visible under one facade but remain semantically distinct.

The architecture has three semantic views, but they are not three copies of the same data:

1. the normalized domain/catalog is the persisted business fact model;
2. the denormalized search document is an explicit read model projected from a consistent snapshot;
3. parsed intent and `SearchPlan` describe operations requested over document fields, not another
   stored representation.

The single business-visible root therefore has two top-level branches, not three sibling stores:

```text
BeautyQSearchDeclarations
├── catalog       normalized source topology and snapshot requirements
└── variants      document-centred search view, request, intent, plan and output policy
```

Stable business values such as `ServiceCode`, `CategoryCode`, IDs, money, duration and geo values
cross these boundaries. Normalized entity case classes and storage paths do not become the public
query contract automatically.

### 4.2 Explicit business logic

BeautyQ explicitly owns:

- joins and invariant checks;
- stable business codes;
- document text composition;
- intent aliases and constraint meaning;
- facet and group presentation policy;
- ranking policy values;
- supplement eligibility;
- response projection.

Framework code may remove mechanical boilerplate but may not infer these decisions from case-class shape.
The authoring boundary is normative: the domain writes business choices, while reusable code derives
only tautological evidence already fixed by a selected type, direct selector, or declared inventory.
"Explicit" therefore means that policy is visible, not that a business author repeats a selector's
name as an ID, path, semantic, extractor, or type argument.

### 4.3 Shared semantics, backend-specific realization

```text
public request + parsed intent
              ↓
          SearchPlan
        ┌─────┴─────┐
        ↓           ↓
 ES FullPlan     CandidatePlan
        ↓           ↓
FullSearchResult CandidateSearchResult
        └─────┬─────┘
              ↓
      BeautyQ orchestration
```

Backend-specific capabilities remain typed. Gen2 is not a lowest-common-denominator abstraction.

### 4.4 Fail closed

A compiler must return a typed unsupported-capability or validation error when it cannot faithfully realize a plan element.

Silent omission and implicit degradation are forbidden.

### 4.5 Lossless documentation

Documentation views are generated from executable sections or wrap them losslessly. A manually maintained descriptive shadow contract is not authoritative.

### 4.6 `VariantDocument.Fields` is the search-view semantic hub

The catalog is upstream source topology; it is not the central search DSL. The exact typed handles
owned by `BeautyQSearchDeclarations.variants.Fields` are reused by every document-centred declaration:

```text
consistent snapshot --explicit projection--> VariantSearchDocumentGen2
                                             │
                                  VariantDocument.Fields
                                  ├── document declaration
                                  ├── public filters and parsed intent
                                  ├── constraints, signals and sorts
                                  ├── facets and groups
                                  ├── Elasticsearch mapping/request policy
                                  ├── Qdrant payload/filter policy
                                  └── response/applied-filter descriptions
```

This reuse is by typed field handle and stable value type. A catalog node is not reused as a
`SearchField`, because normalized relationships and denormalized searchable values have different
semantics. Explicit projection is the bridge between them; stable codes are the identity bridge
between catalog facts, document values and intent vocabulary.

The initial declaration review must make this traceability visible:

| Business concept | Normalized source | Variant document | Intent/plan use | Backend/UI use |
|---|---|---|---|---|
| service identity | `Service(code, name, categoryId)` | `serviceCode`, `serviceName` | exact code constraint plus text semantics | ES text/keyword, Qdrant payload, applied filter |
| category identity | `Category(code, parentId)` | `categoryCode`, `categoryName` | exact code constraint plus aliases | ES text/keyword, Qdrant payload, category facet |
| price | offer/schema/variant values | `priceFrom`, `priceTo` | explicit interval overlap | ES filters aggregation, Qdrant range conditions, price facet |
| duration | variant/offer values | `durationMin` | numeric range/sort | ES field, optional Qdrant payload, applied filter |
| location | master locations | typed document geo value(s) | proximity signal, radius filter or distance sort | ES geo operations, Qdrant geo payload/filter, distance presentation |

This table explains ownership; executable declarations and generated views remain authoritative for
the final field set and exact policy.

Starting implementation with catalog topology alone is forbidden as the search-framework vertical:

- catalog relationships cannot determine joins, normalization, search text or embedding text;
- public filter/facet names must not be derived from SQL or document paths;
- price overlap, geo signal/filter/sort and group representatives are search semantics, not catalog
  edges;
- some catalog data is not searchable and some searchable values are derived rather than catalog
  nodes.

The Gen2 root still owns a visible `catalog` branch early, using the neutral `repo-core` catalog
algebra. The implementation priority is the typed document/field kernel because projection, intent
and both backend compilers converge there.

### 4.7 Human-reviewable executable declarations

Tapir-style means immutable typed declaration values assembled fluently and interpreted by
domain-free code. It does not mean forcing the catalog, document and request pipeline into one
homogeneous builder chain. The canonical domain declaration must be readable without platform
internals: low-level constructors remain escape hatches, not the new-domain example.

The business authoring budget is:

| Domain author writes | Framework derives |
|---|---|
| document case class and catalog topology | nominal codecs and logical type IDs |
| identity selection | direct value type, path, default field ID/semantic and required extraction |
| String keyword/text choice | unambiguous non-String kind |
| capabilities, public names, dynamic inventories and explicit ignored members | registration order, exhaustive product coverage, identity exclusion and document assembly |
| projection joins and invariants | deterministic structural rendering |

The derived defaults remain overrideable when a legitimate contract differs: public request/filter/
facet IDs are separate business policy and are never derived from `FieldPath`.

Every declaration layer must provide a deterministic, human-readable structural view generated from
the executable value. At minimum the document view shows field ID, value type, path, semantic role
and capabilities. Later views show request-to-intent-to-plan decisions and backend compilation.

Checked-in golden views or inline expected trees are allowed only when generated from the executable
root and paired with structural assertions. They must not become manually maintained shadow
contracts. A declaration change should produce a reviewable diff such as:

```text
serviceCode: ServiceCode
├── filter: equal, in
├── facet: terms
└── qdrantPayload: true

price: [Money, Money]
├── filter: interval-overlap
├── facet: interval-overlap
└── sort: minimum-price
```

Business-scenario tests use readable stages rather than opaque implementation probes:

```text
public request -> parsed intent -> SearchPlan -> backend plan/result role -> public response
```

Generic declaration code is proved with a neutral sample document. BeautyQ tests prove the concrete
business declaration and scenarios. Generic modules must contain no BeautyQ name or policy.

### 4.8 End-to-end path and optimization boundary

Indexing and request execution are separate pipelines joined by the document contract:

```text
SQL/repositories
  -> repeatable-read normalized snapshot
  -> explicit BeautyQ projection and invariant validation
  -> VariantSearchDocumentGen2
  -> ES source/mapping compiler and Qdrant point/payload/embedding compiler

public request
  -> validation and intent parsing
  -> one normalized SearchPlan over variants.Fields
  -> ES FullPlan and optional Qdrant CandidatePlan
  -> FullSearchResult and CandidateSearchResult
  -> BeautyQ orchestration and public response
```

The performance/correctness boundary is deliberate:

- joins and expensive normalization happen once during materialization rather than per search;
- query meaning is normalized once into `SearchPlan`, then realized by domain-free backend compilers;
- Elasticsearch owns exact full-result behavior, totals, facets, groups and page state;
- Qdrant owns filtered semantic candidate retrieval only;
- typed capabilities reject unsupported uses before a backend silently drops semantics;
- source, projected-document, contract, projection/compiler/index-format and embedding identities
  prevent incorrect resource reuse while avoiding rebuilds for unchanged inputs.

## 5. New module graph

Normative project names may use the repository naming convention, but ownership and dependency direction are fixed.

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
        ↑
may depend on Gen2 serving modules;
no serving module may depend on eval
```

More explicitly:

```text
search-gen2-contract
  └─ no backend, BeautyQ, repository or HTTP dependency

search-gen2-core
  └─ depends on search-gen2-contract

search-gen2-elasticsearch
  └─ depends on search-gen2-contract/core and a neutral ES transport

search-gen2-qdrant
  └─ depends on search-gen2-contract/core and a neutral Qdrant transport

beautyq-search-gen2-contract
  └─ depends on search-gen2-contract, repo-core and beautyq-model
     (`repo-core` supplies the catalog declaration algebra owned by the Gen2 root)

beautyq-search-gen2-materialization
  └─ depends on BeautyQ Gen2 contract, search-gen2-core, repo-core,
     beautyq-search-repositories and approved model modules
     (`search-gen2-core` supplies the reusable materialization kernel; see §9.4)

beautyq-search-gen2-wiring
  └─ depends on all required Gen2 runtime modules

beautyq-search-gen2-eval
  └─ depends on Gen2 modules, fixtures and evaluation libraries
```

### 5.1 Allowed shared dependencies

Gen2 may depend on neutral/shared project foundations such as:

- `leaderboard-core`;
- `repo-core`;
- `beautyq-model`;
- `beautyq-search-repositories`;
- generic JSON/HTTP/effect/logging libraries already used by the repository;
- a newly extracted neutral transport/client module.

### 5.2 Forbidden Gen1 dependencies

No Gen2 module may depend on or import from:

```text
search-contract-core
search-core
search-elasticsearch
search-qdrant
beautyq-search-contract
beautyq-search-materialization
beautyq-search-wiring
```

The rule applies to main and test sources, except for an explicitly named comparison fixture module that is not on any Gen2 runtime classpath.

### 5.3 Neutral clients

If reusable ES/Qdrant transport code currently lives inside Gen1 backend modules, Gen2 must not depend on those modules.

Allowed solutions:

1. introduce a new neutral transport module used by Gen2;
2. implement the minimal client in the Gen2 backend module;
3. copy a small transport implementation temporarily and delete the duplicate with Gen1.

Moving V1 runtime through the new transport is optional and must not block Gen2.

### 5.4 Gen1 references are classified, not dependencies

The implementation plan's [Gen1 evidence and reuse map](BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md#gen1-evidence-and-reuse-map)
is the coordinator index for existing source/tests. Every referenced item is explicitly classified as:

- allowed direct reuse from a neutral/shared module;
- evidence-only reading with a fresh Gen2 implementation;
- extract-first transport code.

Being generic in Scala type parameters does not make a class reusable across the module firewall. A
symbol located in `search-core`, `search-elasticsearch`, `search-qdrant` or a BeautyQ Gen1 search
module remains forbidden until it is extracted into an approved neutral module. Tests and business
fixtures may be ported as semantic evidence, but imports from Gen2 main/test source to those projects
remain forbidden except for the explicitly isolated comparison fixture module described above.

## 6. Module/import firewall

The first Gen2 code change adds automated checks that:

- inspect declared sbt project dependencies;
- scan Gen2 imports for forbidden package prefixes;
- reject BeautyQ symbols in generic Gen2 modules;
- reject eval dependencies from serving modules;
- reject cyclic dependencies in the Gen2 DAG.

A firewall violation fails CI.

## 7. Core Gen2 algebra

Exact Scala syntax may evolve, but semantic distinctions are normative.

### 7.1 Typed fields

```scala
final case class SearchField[Document, Value](
  id: FieldId,
  path: FieldPath,
  extract: Document => Option[Value],
  codec: SearchValueCodec[Value],
  semantic: Option[FieldSemantic],
  capabilities: FieldCapabilities,
  textPolicy: Option[TextFieldPolicy],
)

final case class FieldCapabilities(
  searchable: Boolean,
  filterOperators: Set[FilterOperator],
  facetModes: Set[FacetMode],
  sortModes: Set[SortMode],
  groupModes: Set[GroupMode],
  payloadEligible: Boolean,
)
```

Validation rejects any policy referencing an unsupported capability.

Field declarations are first-class immutable values. The fluent API is additive and finishes as an
ordinary typed contract model; interpreters do not know whether that model was assembled with a
builder. The intended business-facing shape is:

```scala
private val declarations = searchFields[VariantSearchDocumentGen2]("variants")

val variantId = declarations.inferred(_.variantId).payloadEligible.declare
val serviceCode = declarations.inferred(_.serviceCode)
  .filterable(Equal, In).facetable(TermsFacet).payloadEligible.declare
val serviceName = declarations.keyword(_.serviceName).declare
val document = declarations.completeDocument(variantId)
```

Exact method names may change during implementation, but these constraints are normative:

- `FieldId` remains a stable observable contract value; a direct selector may supply its default ID
  when that equality is tautological, and an explicit override remains available;
- public request/filter/facet names are separate explicit IDs and are not derived from `FieldPath`;
- the document type and field value type are preserved by the handle;
- capabilities are visible at the field declaration and validated wherever referenced;
- field order and generated structural output are deterministic;
- every search-document product member is a declared field/dynamic family or an explicit `.ignore`;
- `Fields.*` values are reused directly rather than reconstructed in intent or backend sections;
- field extractors/codecs are excluded from canonical cursor identity while their versioned effects
  are covered by contract/projection/compiler fingerprints.

### 7.2 Bounds and constraints

```scala
sealed trait Bound[+A]
object Bound {
  case object Unbounded
  final case class Inclusive[A](value: A)
  final case class Exclusive[A](value: A)
}

final case class RangeBounds[A](lower: Bound[A], upper: Bound[A])

sealed trait PlannedConstraint[Document]
object PlannedConstraint {
  final case class Terms[Document, A](
    field: SearchField[Document, A],
    values: Set[A],
  ) extends PlannedConstraint[Document]

  final case class NumberRange[Document, A](
    field: SearchField[Document, A],
    bounds: RangeBounds[A],
  ) extends PlannedConstraint[Document]

  final case class IntervalOverlap[Document, A](
    from: SearchField[Document, A],
    to: SearchField[Document, A],
    bounds: RangeBounds[A],
  ) extends PlannedConstraint[Document]

  final case class GeoDistanceFilter[Document](
    field: SearchField[Document, GeoPoint],
    origin: GeoPoint,
    radius: Distance,
  ) extends PlannedConstraint[Document]
}
```

Geo scoring and sorting are separate types:

```scala
GeoProximitySignal
GeoDistanceSort
```

### 7.3 Search plan and stable plan identity

```scala
final case class SearchPlan[Document](
  residualText: Option[String],
  hardConstraints: Vector[PlannedConstraint[Document]],
  softSignals: Vector[PlannedSignal[Document]],
  sort: Vector[PlannedSort[Document]],
  page: PageRequest,
  facets: Vector[FacetRequest[Document]],
  groups: Vector[GroupRequest[Document]],
  appliedFilters: Vector[AppliedFilter],
  diagnostics: PlanDiagnostics,
)
```

`SearchPlan` is an executable runtime value and is **not** encoded directly as cursor identity. In particular, the current cursor, diagnostics, extractors, codecs and other function-valued/runtime members must not participate in equality or canonical encoding.

Gen2 defines a separate value-only projection:

```scala
final case class PlanIdentity(
  contractFingerprint: ContractFingerprint,
  normalizedQuery: Option[NormalizedQueryText],
  hardConstraints: Vector[CanonicalConstraint],
  softSignals: Vector[CanonicalSignal],
  sort: Vector[CanonicalSort],
  facets: Vector[CanonicalFacetRequest],
  groups: Vector[CanonicalGroupRequest],
  pageSize: PageSize,
)

trait CanonicalPlanView[Document] {
  def identityOf(plan: SearchPlan[Document]): PlanIdentity
}
```

`PlanIdentity` includes stable field IDs, typed normalized values, effective query text, geo values when they affect execution, sort, facets, groups, page size and contract fingerprint. It excludes:

- the current `PageRequest.cursor`;
- diagnostics and timings;
- function-valued field extractors/codecs;
- backend client objects and runtime handles;
- non-semantic request metadata.

A `SearchField` is identified canonically by `FieldId`, not by case-class/function equality. `FieldPath` and physical backend mapping are protected by the contract fingerprint.

The cursor envelope stores the `PlanIdentity` hash plus backend pagination state and deterministic sort tie-breakers. Cursor validation first reconstructs `PlanIdentity` from the new request with `cursor = None`, then compares that hash with the envelope. This avoids recursive cursor identity.

### 7.4 Facets

Gen2 supports distinct facet semantics:

```scala
sealed trait FacetRequest[Document]

TermsFacet(field, size, order, countingPolicy)
NumberRangeFacet(field, buckets, countingPolicy)
IntervalOverlapFacet(fromField, toField, buckets, countingPolicy)
```

Every bucket has a stable ID independent of display label.

Facet results carry:

- requested facet ID;
- bucket IDs and typed values;
- counts;
- counting policy;
- precision/exactness metadata;
- diagnostics.

### 7.5 Groups

A group request is richer than a `terms` aggregation:

```scala
final case class GroupRequest[Document, Key](
  id: GroupId,
  keyField: SearchField[Document, Key],
  size: Int,
  representative: RepresentativeRequest[Document],
  metrics: Vector[GroupMetricRequest[Document]],
  order: Vector[GroupOrder],
  precision: GroupPrecisionPolicy,
)
```

`GroupResult` contains:

- key;
- matching document count;
- representative document data or ID;
- requested metrics such as best score and minimum distance;
- precision metadata.

### 7.6 Pagination

```scala
final case class PageRequest(
  cursor: Option[SearchCursor],
  size: PageSize,
)

final case class PageResult(
  nextCursor: Option[SearchCursor],
  hasMore: Boolean,
)
```

There is no second `limit` field.

### 7.7 Public filters and trusted provenance

The external JSON model does not accept internal parser/system provenance:

```scala
final case class PublicFilterInput(
  field: PublicFieldName,
  operator: PublicOperator,
  value: PublicFilterValue,
  presentationId: Option[FacetSelectionId],
)

final case class PublicFilterField[Constraint](
  name: PublicFieldName,
  operators: Set[PublicOperator],
  decode: PublicFilterInput => Either[FilterError, Constraint],
)
```

The server assigns public provenance deterministically:

- `presentationId = None` becomes `ExplicitUi`;
- `presentationId = Some(...)` becomes `FacetSelection` after validating the facet/bucket identity.

Parser and system provenance is internal only:

```scala
final case class SourcedConstraint[Constraint](
  constraint: Constraint,
  provenance: ConstraintProvenance, // ExplicitUi | FacetSelection | ParsedHard | ParsedSoft | SystemDefault
)
```

`ParsedHard`, `ParsedSoft` and `SystemDefault` are never accepted from the public codec.

### 7.8 Role-specific backend plans and results

Elasticsearch compiles the full plan:

```scala
FullSearchResult[Document, Id](
  hits: Vector[BackendHit[Document, Id]],
  total: TotalHits,
  facets: Map[FacetId, FacetResult],
  groups: Map[GroupId, GroupResult],
  page: PageResult,
  diagnostics: BackendDiagnostics,
)
```

Qdrant receives a candidate plan only after semantic-text and eligibility compilation succeeds:

```scala
final case class CandidatePlan[Document](
  semanticText: NonEmptyString,
  hardConstraints: Vector[PlannedConstraint[Document]],
  topK: CandidateLimit,
  threshold: Option[SemanticScoreThreshold],
  oversampling: OversamplingPolicy,
)

sealed trait CandidatePlanDecision[+Plan]
object CandidatePlanDecision {
  final case class Eligible[Plan](plan: Plan) extends CandidatePlanDecision[Plan]
  final case class Ineligible(reason: SupplementIneligibility) extends CandidatePlanDecision[Nothing]
}

final case class CandidateSearchResult[Id](
  candidates: Vector[CandidateHit[Id]],
  diagnostics: BackendDiagnostics,
)
```

`CandidatePlan.semanticText` remains non-empty because an empty semantic query produces `Ineligible(NoSemanticQueryText)` before a plan is constructed. No unsupported facet/group/page placeholders are required in the Qdrant result.

## 8. BeautyQ Gen2 executable root

The root exists before backend implementation, not after runtime migration.

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
    └── quality
        ├── corpusRef
        ├── metricSet
        └── gates
```

The root references executable typed sections. Generated documentation is a view of this root.

### 8.1 Stable identities

Add and validate `ServiceCode` and `CategoryCode` as specified by the ADR.

The accepted implementation places these codes in the shared BeautyQ domain/persistence models, not in a Gen2-only sidecar. This may add code fields to derived Service/Category HTTP JSON before search cutover. The side-by-side guarantee is therefore: Gen1 search runtime semantics and ownership remain unchanged; shared domain/API schemas may receive this documented additive change.

The variant document contains both internal IDs and stable codes where search/filter/intent behavior requires them.

### 8.2 Explicit document projection

Projection remains domain code and must perform:

- snapshot indexing by IDs/codes;
- joins;
- cross-owner invariant checks;
- service variant schema validation;
- attribute normalization;
- search text and embedding source construction;
- deterministic error accumulation;
- deterministic document ordering.

## 9. Snapshot and materialization

### 9.1 Consistent repository snapshot

```scala
trait SearchSnapshotSource[Snapshot] {
  def load: IO[SnapshotLoadError, VersionedSnapshot[Snapshot]]
}

final case class VersionedSnapshot[A](
  value: A,
  contentFingerprint: ContentFingerprint,
  sourceRevision: Option[SourceRevision],
  capturedAt: Instant,
)
```

Requirements:

- all repository reads occur in one repeatable-read transaction or equivalent;
- content fingerprint uses canonical content only;
- timestamps do not alter content fingerprint;
- identical data yields identical fingerprint;
- projection fails before index activation on missing/inconsistent entities.

### 9.2 Catalog and write-order validation

Catalog topology may validate declared read dependencies. It does not infer the full SQL insertion order.

Seed insertion order, if automated, is based on an explicit repository/persistence write dependency graph. A validation step compares applicable catalog edges and foreign-key/repository evidence with that graph.

### 9.3 Projected-document and generation identity

After explicit projection and validation, the materializer computes:

```scala
final case class ProjectedDocumentsFingerprint(value: String)
```

The fingerprint is calculated over the canonical projected documents:

- documents are ordered by stable document ID;
- each document uses a versioned canonical value encoding;
- all projected field values that can affect ES indexed source, Qdrant payload or embedding source text are included;
- volatile metadata such as capture/build timestamps and diagnostics is excluded;
- identical projected document values produce the same fingerprint regardless of repository iteration order.

This fingerprint detects document changes caused by projection logic, field extraction, normalization or text composition even when the SQL/source snapshot fingerprint is unchanged.

The generation identity also contains explicit implementation/format versions:

```scala
final case class GenerationIdentity(
  sourceContentFingerprint: ContentFingerprint,
  projectedDocumentsFingerprint: ProjectedDocumentsFingerprint,
  contractFingerprint: ContractFingerprint,
  projectionFormatVersion: ProjectionFormatVersion,
  backendCompilerVersion: BackendCompilerVersion,
  backendIndexFormatVersion: BackendIndexFormatVersion,
  embeddingModel: Option[EmbeddingModelIdentity],
)
```

Rules:

- `projectionFormatVersion` is bumped when projection, extractor, canonical document encoding, normalization or text-composition semantics can change, even when the current fixture corpus happens to produce identical documents;
- `backendCompilerVersion` is bumped when mapping, ingestion, payload or point compilation behavior can change without a declarative contract change;
- `backendIndexFormatVersion` identifies the concrete ES mapping/index format or Qdrant collection/payload format;
- `EmbeddingModelIdentity` includes model/provider identity, model version, vector dimensions and relevant normalization/configuration version;
- ES and Qdrant may use backend-specific compiler and index-format version types;
- build timestamp, generation ID and `capturedAt` are metadata and never part of reusable content identity.

Every generated ES index and Qdrant collection records:

- the complete `GenerationIdentity` applicable to that backend;
- generation ID and build timestamp;
- document count and validation summary.

A physical index or collection may be reused only when its complete generation identity matches the requested build. Generation reuse therefore depends on:

```text
source content
+ projected canonical documents
+ Gen2 contract
+ projection format
+ backend compiler/index format
+ embedding model/version when applicable
```

A mismatch requires a new physical generation followed by normal validation and atomic activation. Matching source and contract fingerprints alone is never sufficient.

### 9.4 Generic materialization kernel

`search-gen2-core` owns only domain-neutral snapshot metadata, canonical
row/token framing and ordering, document-declaration traversal and
materialization orchestration. A domain selects the canonical source fields
and values once; the kernel derives the emitted token block and deterministic
row sort key (including self-sized nested groups, never parallel size vectors).
It has no SQL, repository, BeautyQ or backend dependency.

Each domain owns its transaction-local SQL/source adapter and pure business projection. Consistent
transaction acquisition is an adapter responsibility; the generic kernel
does not prescribe PostgreSQL or Doobie.

## 10. BeautyQ request and response

### 10.1 Request

```scala
final case class BeautySearchRequestGen2(
  query: Option[String],
  filters: Vector[PublicFilterInput],
  requestedFacets: Vector[FacetId],
  sort: Vector[SortInput],
  page: PageRequest,
  userLocation: Option[GeoPoint],
)
```

Rules:

- selected facets are represented in `filters`; provenance is assigned by the server from the validated presentation ID;
- the public codec cannot submit `ParsedHard`, `ParsedSoft` or `SystemDefault`;
- requested facet descriptors identify which facets should be returned;
- invalid field/operator/value combinations return typed validation errors;
- one request cannot contain conflicting pagination sizes;
- location without geo intent is accepted as data but does not change ranking;
- plan compilation follows the accepted precedence ADR.

### 10.2 Response

The Gen2 response exposes:

- baseline hits and provenance;
- optional appended semantic hit and provenance;
- exact baseline total and total relation;
- supplement count separately;
- typed facets and precision;
- typed provider/service groups and precision;
- applied and suppressed filters with origin;
- next cursor;
- execution diagnostics safe for the API profile.

External JSON names are selected for Gen2 directly. They do not need V1 codec aliases.

## 11. Elasticsearch Gen2 requirements

### 11.1 Mapping and ingestion

The compiler derives mappings from typed fields and ES-specific policy.

It must validate:

- analyzer availability;
- exact/filter subfields;
- sortable field representation;
- geo field type;
- interval fields used by overlap constraints/facets;
- document IDs;
- source encoding.

### 11.2 Request compilation

Compile:

- residual text and weighted text fields;
- exact terms;
- explicit-bound numeric ranges;
- interval-overlap predicates;
- geo signal/filter/sort independently;
- stable deterministic sorts and tie-breakers;
- cursor/search-after state;
- exact totals;
- requested facets;
- requested group representatives and metrics.

Aggregation names use stable typed IDs, not normalized field paths alone.

### 11.3 Response decoding

Decode into `FullSearchResult`:

- hits and scores;
- exact/qualified total hits;
- terms, numeric range and interval-overlap facet buckets;
- group buckets, representative data and metrics;
- pagination state;
- precision/error metadata;
- unknown/missing aggregation diagnostics.

A requested section missing from the response is a typed error unless the request explicitly allowed degradation.

### 11.4 Group implementation

A plain `terms` aggregation is not sufficient.

The compiler must request the representative data and every ordering metric declared by the BeautyQ group policy. Candidate implementations include `terms` plus `top_hits`/`top_metrics` and metric sub-aggregations, or a dedicated secondary group query when that produces clearer correctness.

The selected mechanism must pass exact fixture tests for:

- representative document;
- matching count;
- best score;
- optional proximity metric;
- deterministic bucket order.

### 11.5 Lifecycle

Gen2 uses versioned physical indexes and a stable alias:

```text
build -> validate -> count/fingerprint check -> atomic alias switch
```

A failed build never changes the active alias. Previous generation retention is policy-driven.

## 12. Qdrant Gen2 requirements

### 12.1 Semantic query text policy

Embedding input is produced by an explicit `SemanticQueryTextPolicy`; the Qdrant compiler does not choose between raw and residual query text.

Initial BeautyQ policy is:

```text
semantic text =
  normalized residual text
  + canonical human-readable labels emitted by parser-recognized semantic clauses
```

Rules:

- do not embed the raw original query verbatim after parsing;
- include normalized residual text first;
- append deduplicated canonical labels for parser-derived service, category, attribute, provider or location semantics;
- canonical labels come from Gen2 declarations keyed by stable codes, not mutable request strings;
- exclude explicit UI filters, facet selections, numeric price/duration bounds, geo coordinates, sort instructions and system defaults from semantic text;
- use deterministic ordering and normalization so the same semantic request produces the same embedding input;
- when both residual text and canonical semantic labels are empty, return `Ineligible(NoSemanticQueryText)`;
- a filter-only request or default-browse request therefore skips Qdrant supplementation without error.

The policy returns `Option[NonEmptyString]` (or an equivalent eligibility result). `CandidatePlan` is created only for `Some`.

### 12.2 Point and payload projection

The point contains:

- stable variant ID;
- named vector(s);
- all payload fields required by candidate hard constraints;
- payload schema/version;
- generation identity, including source, projected-document, contract, projection, compiler/format and embedding identities where practical.

### 12.3 Payload indexes

The collection compiler declares and validates payload indexes for all pushed-down filter fields.

A CandidatePlan cannot be activated if a required payload field or index is missing.

### 12.4 Candidate compilation

Compile:

- semantic query vector;
- terms and numeric constraints;
- interval-overlap price constraints;
- geo radius filters when explicitly requested;
- topK, threshold and oversampling;
- payload return policy.

Qdrant does not receive facets, groups or public page requests.

### 12.5 Candidate decoding and hydration

Decode to `CandidateSearchResult`, then hydrate via the Gen2 document store/snapshot view.

Post-hydration constraint checking remains an assertion and diagnostic. It is not the primary filter implementation.

## 13. Baseline-plus-supplement orchestration

The orchestrator receives:

```text
baseline: FullSearchResult
supplement: CandidateSearchResult
```

Policy is defined by the ADR:

- first page only;
- default relevance sort only;
- append-only maximum one;
- no baseline reorder or removal;
- all hard constraints satisfied;
- baseline-set membership guard prevents later-page duplicates;
- ES retains ownership of total, facets and groups;
- supplement count/provenance is separate.

Failure behavior is fixed rather than left as a future configuration branch:

- Elasticsearch baseline availability is mandatory. Startup/readiness fails when the ES baseline, Gen2 document lookup or required baseline index is unavailable.
- Qdrant unavailability does not prevent baseline serving. The application starts or remains serving in explicit `baseline_only` mode with `supplementReady = false`.
- the full-search/cutover readiness gate still fails while the configured supplement backend is unavailable;
- a request-time Qdrant timeout, transport error or backend error returns the successful Elasticsearch baseline unchanged and sets supplement status to `supplement_failed` with a stable reason code and diagnostics;
- the response does not silently claim a successful hybrid execution;
- plan compilation errors, unsupported capabilities and hydration invariant violations are not graceful Qdrant failures: they fail validation/request handling or mark the composition unhealthy according to their scope.

No automatic fallback candidate backend or hidden retry path is introduced.

## 14. Independent Gen2 application composition

Before cutover, Gen2 is runnable through an independent local/test composition:

- separate role, command or endpoint;
- separate ES index alias and Qdrant collection names;
- separate complete generation identities and physical resource namespaces;
- no shadow decoder or adapter inside the V1 backend;
- no production request fan-out from V1 to Gen2.

Comparison occurs in `beautyq-search-gen2-eval` or integration tests.

## 15. Quality and evaluation

`beautyq-search-gen2-eval` owns:

- labeled query corpus;
- boundary datasets;
- V1 observation fixtures where useful;
- cross-backend semantic fixtures;
- relevance metrics;
- no-harm supplement reports;
- latency and freshness reports;
- cutover report generation.

Serving modules contain only quality references and gate policies.

Required gates include:

- zero hard-constraint violations;
- exact facet counts on authoritative fixtures;
- deterministic group ordering on fixtures;
- stable cursor behavior;
- no cross-page supplement duplicate;
- no baseline hit removal/reorder;
- acceptable relevance metrics;
- index/collection generation-identity consistency, including projected-document and compiler/format versions;
- module firewall clean;
- no Gen1 runtime dependency.

## 16. Isolation and cutover rules

### 16.1 Before cutover

- V1 runtime source is not modified to consume Gen2;
- Gen2 uses separate resources/namespaces;
- no V1-to-V2 production adapter is created;
- V1 fixtures may be read only from eval/test code;
- every Gen2 brick is independently testable.

### 16.2 Cutover gate

Cutover is allowed only when:

1. accepted ADR has no unresolved P0 decision;
2. all Gen2 modules and firewall checks pass;
3. repository snapshot, projection and index lifecycle are proven;
4. ES full result tests pass for hits/totals/facets/groups/page;
5. Qdrant candidate filtering and hydration tests pass;
6. supplement page/sort/no-duplicate policy passes;
7. independent Gen2 endpoint/role passes end-to-end tests;
8. semantic delta ledger is reflected in API/eval fixtures;
9. deletion plan shows no required capability exists only in Gen1.

### 16.3 One cutover change set

The final cutover change set:

- switches application binding/route ownership to Gen2;
- switches operational resource names/aliases as planned;
- removes V1 route/wiring/contracts/interpreters and compatibility aliases;
- removes obsolete Gen1 sbt projects and dependencies;
- removes historical serving-time eval scaffolding not retained in Gen2 eval;
- updates documentation to make Gen2 the only architecture.

The change set is reverted as a unit if the cutover gate or post-merge smoke tests fail.

## 17. Required tests

### 17.1 Pure tests

- typed document builder preserves declared field order and exact `Fields.*` handles;
- field IDs and public request/filter/facet IDs remain explicit and are not derived from paths;
- a field from the wrong document type does not compile;
- field capability validation;
- deterministic human-readable declaration tree matches the executable root metadata;
- the generated tree and structural reachability assertions cover the same declaration sections;
- readable business scenarios expose public request, parsed intent, normalized plan and eligibility;
- stable code validation;
- explicit bound semantics;
- interval overlap boundaries;
- geo signal/filter/sort separation;
- filter operator decoding;
- constraint precedence/provenance;
- plan normalization/fingerprint;
- ES and Qdrant golden compilation;
- full and candidate result decoding;
- generated documentation losslessness;
- module/import firewall.

### 17.2 Integration tests

- repeatable-read repository snapshot consistency;
- unchanged-data source fingerprint stability;
- deterministic projected-document fingerprint stability and change detection;
- projection/compiler/index-format version invalidation;
- snapshot -> projection invariants;
- ES mapping/ingestion/search/totals/facets/groups/cursor;
- ES versioned index activation safety;
- Qdrant payload indexes and filtered candidate retrieval;
- hydration assertion;
- first-page supplement and baseline membership guard;
- independent Gen2 HTTP/role composition.

### 17.3 Evaluation tests

- semantic delta ledger fixtures;
- zero constraint violations;
- exact facet fixture parity;
- group ordering fixture parity;
- Recall@K, NDCG and MRR where labels exist;
- zero-result and duplicate rates;
- no-harm supplement gate;
- p50/p95 latency;
- source/index freshness.

## 18. Definition of done

Gen2 is complete when:

1. all runtime code lives in the new Gen2 DAG;
2. no Gen2 module depends on a Gen1 search module;
3. one executable BeautyQ root owns all runtime declarations;
4. `BeautyQSearchDeclarations.variants.Fields` is the single document-field handle owner used by
   document, intent, facets/groups, backend policies and response descriptions;
5. stable service/category codes are persisted and validated;
6. repository snapshots are transactionally consistent and content-versioned;
7. explicit projection produces deterministic validated documents and a canonical `ProjectedDocumentsFingerprint`;
8. ES/Qdrant generation reuse requires equality of source, projected-document, contract, projection, compiler/index-format and applicable embedding identities;
9. structured filters, facets, sort and cursor pagination form a closed API loop;
10. Elasticsearch returns decoded full results including totals, facets and groups;
11. Qdrant returns filtered candidate-only results only for requests with eligible non-empty semantic query text;
12. cursor validation uses value-only `PlanIdentity` and never includes the current cursor or runtime/function values;
13. supplement orchestration obeys first-page/default-sort/no-duplicate policy and the accepted explicit Qdrant failure policy;
14. serving modules do not depend on evaluation corpus/report code;
15. generated structural documentation and readable scenario views remain deterministic views of the executable root rather than shadow contracts;
16. the independent Gen2 composition passes the cutover gate, including full-search readiness with Qdrant available;
17. the final cutover removes Gen1 search modules, routes, aliases and obsolete scaffolding;
18. repository documentation describes Gen2 as the sole search framework.
