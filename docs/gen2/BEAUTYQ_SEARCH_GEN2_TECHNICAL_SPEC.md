# BeautyQ Search Framework Gen2 — current implemented architecture

Status: **native cutover complete; core architecture frozen; bounded post-cutover work approved**
Scope: the sole search architecture in the repository
Delivery rule: one final cutover completed; Gen1 search modules and routes are absent

Sections explicitly labelled implemented describe current source. All backend/runtime sections
are implemented; delivery closure is recorded below. Approved but unimplemented evaluation and
operational corrections belong to
[BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md](BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md).

## 1. Goal

Build a second-generation search framework in new modules without depending on the existing Gen1 search framework. (Historical delivery strategy: Gen2 was constructed independently beside Gen1; the final cutover removed all Gen1 search modules and routes.)

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

Gen1 search modules and routes are absent. The native route is `POST /beauty-search`.

## 2. Delivery model

(Historical delivery strategy — Gen2 was built independently beside Gen1; the final cutover is complete.)

```text
Gen1 search runtime was untouched during implementation;
accepted shared domain/API additions were allowed
        │
        ├── Gen2 was constructed in a separate module DAG
        │   and was run/evaluated independently
        │
        └── after the Gen2 cutover gate passed:
            one application binding/route cutover
            + deletion of Gen1 search modules and aliases
```

Small implementation bricks were required. Incremental ownership migration inside Gen1 was forbidden.

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
- byte-for-byte V1 request/response parity where this specification defines an intentional change;
- preserving historical M8–M21 evaluation scaffolding in the serving classpath.

## Semantic invariants

This technical specification records current BeautyQ Gen2 semantic invariants.

Elasticsearch owns the complete canonical baseline result, including canonical hit order, totals, facets, groups, cursor state, and baseline diagnostics where those shapes are supported. Qdrant does not own or redefine the final result.

Qdrant returns candidate identities, scores, and candidate diagnostics only. Supplement composition is append-only: it must not remove, replace, reorder, or rewrite baseline hits. Supplement candidates already present in the current page or complete baseline are excluded before append-budget selection. Supplement failure follows the current typed readiness/degradation policy and must not fabricate a successful enriched result.

Stable protocol identity is explicit. Field IDs, public filter and sort names, facet IDs, group IDs, reason/status codes, explicit ordered policy vectors, and plan/generation contract fingerprints are stable identities. Display labels, unordered-map iteration, incidental source order, or rendered prose must not silently become protocol identity. Meaningful order must be explicit and tested.

Cursor state is bound to canonical plan identity. Backend execution is authorized against the persisted generation contract. A stale, missing, ambiguous, or incompatible generation is rejected through typed failure handling. A cursor must not silently continue against another incompatible plan or generation.

Clients may submit supported public request forms. Clients cannot assign trusted parsed/system provenance to their own filters. Request, field, cursor, and backend-response decoders reject malformed or incompatible shapes instead of guessing missing semantics. Backend response ownership remains closed at the appropriate projector or decoder boundary.

## 4. Architectural principles

### 4.1 Separate normalized and denormalized branches

The backend-neutral contract root exposes both:

- normalized catalog topology;
- denormalized search contract.

The downstream facade also links the snapshot/materialization owners. These concerns are visible from
one starting point but remain semantically and physically distinct.

The architecture has three semantic views, but they are not three copies of the same data:

1. the normalized domain/catalog is the persisted business fact model;
2. the denormalized search document is an explicit read model projected from a consistent snapshot;
3. parsed intent and `SearchPlan` describe operations requested over document fields, not another
   stored representation.

The single business-visible root therefore has two top-level branches, not three sibling stores:

```text
BeautyQSearchDeclarations
├── catalog       normalized source topology
└── variants      document-centred search view, request, intent and plan policy
```

Stable business values such as `ServiceCode`, `CategoryCode`, IDs, money, duration and geo values
cross these boundaries. Normalized entity case classes and storage paths do not become the public
query contract automatically.

### 4.2 Explicit business logic

The normative contract is
[`docs/search/DOMAIN_AUTHORING_PRINCIPLES.md`](../search/DOMAIN_AUTHORING_PRINCIPLES.md). This
specification records only how BeautyQ applies it:

- joins and invariant checks;
- stable business codes;
- document text composition;
- intent aliases and constraint meaning;
- facet and group presentation policy;
- ranking policy values;
- supplement eligibility;
- response projection.

The complete implemented composition entry is wiring-owned `BeautyQSearchGen2`. Its `contract` branch
references `BeautyQSearchDeclarations`, read as
`catalog → variants.Fields → variants.document → variants.request → variants.intent → variants.plan`;
the direct `input` branch exposes `BeautySearchRequestGen2` and `BeautyQIntentParserGen2`, while the
`plan` and `qdrant` branches expose the candidate compiler, Qdrant policy, runtime, hydration policy
and candidate pipeline;
its other branches reference the owning materialization, plan and Elasticsearch objects directly.
Executable declarations own policy; structure trees, inventories, traces, ledgers, and fingerprints
are derived views. The framework derives only tautological evidence fixed by a selected type, direct
selector, or declared inventory.

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

search-gen2-transport

search-gen2-contract + search-gen2-core + search-gen2-transport
   ├── search-gen2-elasticsearch
   └── search-gen2-qdrant

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

search-gen2-transport
  └─ backend- and domain-neutral synchronous JSON/HTTP transport; no search-contract dependency

search-gen2-elasticsearch
  └─ depends on search-gen2-contract/core/transport

search-gen2-qdrant
  └─ depends on search-gen2-contract/core/transport

search-gen2-eval
  └─ independent neutral evaluation kernel

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
  └─ depends on search-gen2-eval and beautyq-search-gen2-wiring
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
search-core
search-elasticsearch
search-qdrant
beautyq-search-contract
beautyq-search-materialization
beautyq-search-wiring
```

The rule applies to main and test sources, except for an explicitly named comparison fixture module that is not on any Gen2 runtime classpath.

### 5.3 Neutral clients

Brick 6B-A introduces `search-gen2-transport` because endpoint validation, safe path/query assembly,
timeouts, headers, synchronous JDK execution, JSON decoding and typed HTTP failures now have two real
Gen2 backend consumers. Elasticsearch keeps a thin backend-named adapter; Qdrant owns only its exact
REST method/path/body protocol. Transport contains no search semantics, backend resource lifecycle or
BeautyQ policy. Gen1 search modules are absent.

The implemented transport surface is one synchronous `Gen2JsonHttpClient` with `putJson`, bodyless
`post`, `postJson`, `postNdjson`, `getJson` and bodyless `delete`. Each operation accepts typed query
parameters and headers; path confinement, UTF-8 JSON decoding, redirect refusal, timeout validation and
typed HTTP/connection/JSON errors remain transport-owned. `ElasticsearchGen2JsonClient` preserves its
existing six-method caller surface as a thin adapter. `QdrantGen2Client` binds only the exact collection,
alias, waited-index/upsert, exact-count and `/points/query` paths; it accepts only a validated resource-name
segment and performs no lifecycle authorization. Brick 6B-B binds an authorized physical target internally
before invoking it; there is no second HTTP implementation.

### 5.4 Gen1 references are classified, not dependencies

`BEAUTYQ_SEARCH_GEN2_REVIEW.md` owns historical Gen1 findings;
`BeautyQGen1SearchDeletionInventory` owns typed completed-cutover evidence;
delivery closure is recorded in the delivery status section below.

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

Current backend-neutral field/value support is:

| Scala value | `SearchFieldKind` |
|---|---|
| `String` | explicit `Keyword` or `Text` |
| `Int`, `Long`, `BigDecimal`, `Boolean`, `java.time.Instant`, `GeoPoint` | `Integer`, `Long`, `Decimal`, `Boolean`, `DateTime`, `GeoPoint` |
| `UuidBackedId[A]`, `CanonicalStringValue[A]` | derived `Keyword` |

`DateTime` currently supports range filtering, range faceting and value sorting; exact/in filters,
terms facets and grouping are not part of the accepted generic capability matrix. Dynamic families
support `Map[String, Value]` plus an ordered definition inventory and definition-code accessor for any
direct-field-kind-eligible `Value`, including `.text`; non-`String` map keys and collection-valued
members are unsupported.

Document derivation supports flat product types. Every product member must be a declared direct field,
a declared dynamic family, or an explicit `.ignore`. Nested searchable sub-documents are a deliberate
non-goal and direct nested selectors are rejected. Multi-valued fields such as `Vector[String]` have no
generic kind, codec, extraction or filter/facet semantics in the initial supported shape.

A domain root may expose `val document = Fields.document`, but `Fields` must not capture sibling values
from that same enclosing object. Such a capture can create a JVM reentrant singleton-initialization cycle
and let the outer alias observe a default value. Inputs belong inside `Fields` or in a scope outside the
root. The cyclic shape is unsupported; Gen2 does not add defensive wrappers for it.

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
  appliedFilters: Vector[AppliedFilter[Document]],
  softSignals: Vector[PlannedSignal[Document]],
  sort: Vector[PlannedSort[Document]],
  page: PageRequest,
  facets: Vector[FacetRequest[Document]],
  groups: Vector[GroupRequest[Document, ?]],
  diagnostics: PlanDiagnostics[Document],
)
```

`hardConstraints` is derived from `appliedFilters`; execution constraints and their trusted
provenance are not stored in parallel vectors.

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
  pageSize: Int,
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

A `SearchField` is identified canonically by `FieldId`, not by case-class/function equality. `FieldPath`
is protected by the declaration-derived contract fingerprint. Physical backend mapping compatibility
has an additive typed contribution seam; complete physical generation reuse remains owned by
`GenerationIdentity`.

The generic fingerprint owner is:

```scala
type PlanContractContributions =
  Map[PlanContractContributionId, PlanContractContributionVersion]

PlanContractFingerprint.compute(
  version,
  documentDeclaration,
  contributions = Map.empty,
): ContractFingerprint
```

It derives field/path/type/presence/capability identity from the executable declaration, mixes in one
explicit `PlanContractVersion`, and canonicalizes the unique typed contribution map by ID. The returned
`ContractFingerprint` is framework-produced and has no public string constructor; its package-scoped
internal constructor is available only inside `leaderboard.search.gen2.core.plan`, not to backend
packages. BeautyQ now contributes the one `elasticsearch`-keyed entry `BeautyQElasticsearchPolicy` derives
from its analyzer assignments and framework-owned compiler/index-format versions (Brick 5A);
`BeautyQSearchPlanCompiler`'s cursor-bound plan identity and the Elasticsearch generation artifact consume
that exact same contribution and fingerprint value, never a second hash protocol or an independently
maintained one. The fingerprint does not hash a rendered tree.

The cursor boundary is one bound value, not independently supplied facts:

```scala
final class BoundSearchPlan[Document] private (
  val plan: SearchPlan[Document],
  val identity: PlanIdentity,
  val identityHash: PlanIdentityHash,
  val backendState: Option[BackendCursorState],
) {
  def isFirstPage: Boolean
}

object SearchCursorEnvelope {
  def bind[Document](
    plan: SearchPlan[Document],
    view: CanonicalPlanView[Document],
  ): Either[SearchCursorError, BoundSearchPlan[Document]]

  def issue[Document](bound: BoundSearchPlan[Document], backendState: String): SearchCursor
}
```

`SearchCursor.fromTransport` only wraps an untrusted transport token. `bind` strips the current cursor
from identity, decodes the envelope, compares its hash and returns the bound result. The envelope stores
one opaque backend-pagination state; Elasticsearch later owns its deterministic `search_after` contents
and tie-breakers. The generic envelope neither interprets nor authenticates that state.

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
  size: GroupSize,
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
```

There is no second `limit` field.

Backend page-result and next-cursor types are introduced with the Elasticsearch vertical; they are not
implemented by the current contract/core modules.

### 7.7 Public filters and trusted provenance

The external JSON model does not accept internal parser/system provenance:

```scala
final case class PublicFilterInput(
  field: PublicFieldName,
  operator: PublicOperator,
  value: PublicFilterValue,
  presentationId: Option[FacetSelectionId],
)

final case class PublicFilterField[Document](
  name: PublicFieldName,
  fieldHandles: Vector[SearchField[Document, ?]],
  acceptedOperators: Vector[PublicOperator],
)

sealed trait PublicFilterDeclaration[Document] {
  def name: PublicFieldName
  def fieldHandles: Vector[SearchField[Document, ?]]
  def acceptedOperators: Vector[PublicOperator]
  def withOperators(operators: Vector[PublicOperator]): PublicFilterDeclaration[Document]
}

object PublicFilterDeclaration {
  def value[Document, A](name: PublicFieldName, field: SearchField[Document, A]): PublicFilterDeclaration[Document]
  def ordered[Document, A: Ordering](name: PublicFieldName, field: SearchField[Document, A]): PublicFilterDeclaration[Document]
  def intervalOverlap[Document, A: Ordering](name: PublicFieldName, from: SearchField[Document, A], to: SearchField[Document, A]): PublicFilterDeclaration[Document]
  def geoDistance[Document](name: PublicFieldName, field: SearchField[Document, GeoPoint]): PublicFilterDeclaration[Document]
}

object PublicFilterRegistry {
  def apply[Document](
    declarations: Vector[PublicFilterDeclaration[Document]],
  ): Either[NonEmptyErrors[PublicFilterDeclarationError], PublicFilterRegistry[Document]]

  def unsafeFrom[Document](declarations: Vector[PublicFilterDeclaration[Document]]): PublicFilterRegistry[Document]
}

final class PublicFilterRegistry[Document] {
  val fields: Vector[PublicFilterField[Document]]
  def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]]
  def decodeNamed(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], (PublicFieldName, PublicFilterClause[Document])]
}
```

`PublicFilterDeclaration` is the reusable high-level authoring path. `value`, `ordered`,
`intervalOverlap` and `geoDistance` retain typed field handles and derive shape decoding, canonical
value handling, bound construction and geo-radius construction. A domain supplies public names,
ordered declarations and any intentional operator narrowing; it does not copy these mechanics. The
low-level `PublicInputSpec`/`PublicInputRegistry` remains an escape hatch for a non-standard input
shape, not the canonical new-domain example.

```scala
sealed trait PublicFilterDeclarationError
object PublicFilterDeclarationError {
  final case class DuplicateName(name: PublicFieldName, firstIndex: Int, duplicateIndex: Int)
    extends PublicFilterDeclarationError
  final case class NoSupportedOperators(name: PublicFieldName, declarationIndex: Int)
    extends PublicFilterDeclarationError
  final case class UnsupportedOperator(name: PublicFieldName, declarationIndex: Int, operator: PublicOperator, supported: Vector[PublicOperator])
    extends PublicFilterDeclarationError
}
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

Elasticsearch compiles the full plan into its role-specific aggregate:

```scala
ElasticsearchFullSearchResult[Document, Id](
  hits: Vector[BackendHit[Document, Id]],
  total: TotalHits,
  facets: Map[FacetId, FacetResult],
  groups: Map[GroupId, GroupResult],
  page: PageResult,
  diagnostics: BackendDiagnostics,
)
```

Qdrant receives a candidate plan only after semantic-text and eligibility compilation succeeds. Brick 4G-A
owns the backend-neutral candidate contract itself, in `search-gen2-contract`:

```scala
opaque type SemanticQueryText = String // SemanticQueryText.from rejects empty/whitespace-only input

final case class CandidatePlan[Document](
  semanticText: SemanticQueryText,
  hardConstraints: Vector[PlannedConstraint[Document]],
)

sealed trait CandidatePlanDecision[+Plan, +Reason]
object CandidatePlanDecision {
  final case class Eligible[Plan](plan: Plan) extends CandidatePlanDecision[Plan, Nothing]
  final case class Ineligible[Reason](reason: Reason) extends CandidatePlanDecision[Nothing, Reason]
}

final case class CandidateSearchResult[Id](
  candidates: Vector[CandidateHit[Id]],
  diagnostics: BackendDiagnostics,
)
```

`CandidatePlan` is a plain generic value; the canonical BeautyQ compiler supplies the compiled plan's
hard constraints, but the generic constructor does not enforce their provenance. It carries no retrieval
knob: `topK`, threshold and oversampling policy are Brick 6A's
`CandidatePlan -> Qdrant request` compiler concern, not this contract type. `CandidatePlanDecision` is
generic in `Reason`: the generic contract declares no ineligibility vocabulary of its own (no
`NoSemanticQueryText`/`NotFirstPage`/`NonDefaultSort` case lives here). BeautyQ's own three-case
`BeautyQCandidateIneligibility` enum (`beautyq-search-gen2-contract`) is the `Reason` BeautyQ's compiler
produces. `CandidatePlan.semanticText` remains non-empty because an empty semantic query produces
`Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText)` before a plan is constructed. No
unsupported facet/group/page placeholders are required in the Qdrant result.

Candidate eligibility is evaluated through one typed error boundary:

```scala
// search-gen2-contract
enum CandidateGateOutcome[+Reason] { case Passed; case Rejected(reason: Reason) }
final case class CandidateGateResult[Gate, Reason](gate: Gate, outcome: CandidateGateOutcome[Reason])
def evaluate[Gate, Plan, Reason](
  gates: Vector[Gate],
  semanticText: Either[SemanticQueryTextError, SemanticQueryText],
  evaluateGate: (Gate, Either[SemanticQueryTextError, SemanticQueryText]) => CandidateGateOutcome[Reason],
  eligiblePlan: SemanticQueryText => Plan,
): Either[CandidateEvaluationError, CandidateEvaluation[Gate, Plan, Reason]]

// beautyq-search-gen2-wiring
def compile(compiled: CompiledBeautyQSearchPlan): Either[CandidateEvaluationError, CompiledCandidateEvaluation]
```

Ordinary ineligibility (`NoSemanticQueryText`, `NotFirstPage`, `NonDefaultSort`) comes only from an active,
declared domain gate failing during the ordinary gate walk, never from a detached fallback.
`CandidateEvaluationError.MissingSemanticTextAfterAllGatesPassed` is a distinct, generic malformed-policy
evaluation error, not a BeautyQ business reason: it reports that every declared gate passed while semantic
text was still missing - meaning no active gate actually tracked it - and preserves the original
`SemanticQueryTextError` unmodified. BeautyQ's own `eligibilityGates` never triggers it, since its
`SemanticQueryText` gate's predicate checks exactly `semanticText.isRight`, so missing text always fails
that gate first. Only `Right` produces the compiler-bound `CompiledCandidateEvaluation` that
`BeautyQCandidatePlanTrace.render` accepts; a `Left` propagates unchanged and is never rendered as
ineligibility. Cursor binding is already owned by Brick 4G-B; the pure Qdrant retrieval compiler is
owned by Brick 6A, while transport and resource authorization remain the separate 6B boundary.

`CandidateEvaluation[Gate, Plan, Reason]` itself is a read-only, framework-produced result: it is a type
alias for `SemanticCandidateEvaluation.Result`, whose constructor is private to
`SemanticCandidateEvaluation`. The aggregate stores only its typed gate outcomes and final decision, and
the evaluator derives the first rejection and eligible plan from those same values. A caller cannot supply
separate pass/reason callbacks, independently replace the plan, or use `copy`/a public factory to create an
inconsistent result. `CandidateEvaluationError` remains the sole distinct `Left` boundary for a malformed
evaluation policy.

For BeautyQ 4G-A, the domain contract additionally fixes the reviewer-visible identities and order:

- semantic parts: `residual-text`, then `canonical-semantic-labels`;
- eligibility gates: `semantic-query-text`, `first-page`, then `default-sort`;
- ineligibility reason codes: `no-semantic-query-text`, `not-first-page`, and
  `non-default-sort`.

`BeautyQSemanticCandidatePolicy` owns these typed IDs/codes and the two explicit active vectors. The
compiler, canonical declaration branch, trace and vocabulary ledger consume those values; none may read
enum case names or discover active order from enum inventory. This is a BeautyQ contract requirement,
not a generic stable-label requirement for unrelated domains.

### 7.9 Plan-input resolution and compilation

Combining a domain's already-validated public request and parsed intent into one validated `SearchPlan`
is generic. Only constraint-source precedence, the geo-origin source, the facet inventory, group policy
and plan-mode classification are domain policy (§10.2 documents BeautyQ's).

Canonical constraint identity is keyed by slot, not by the full canonical value, so precedence
resolution can detect "same thing, different value" independent of "same thing, same value". Slot
identity uses `FieldId` only — never `SearchField` equality, path, capabilities, semantics or extractor
identity — and a constraint's kind is part of its slot, so a `Terms` and a `NumberRange` constraint over
the same field are different slots:

```scala
sealed trait ConstraintSlot
object ConstraintSlot {
  final case class Terms(fieldId: FieldId) extends ConstraintSlot
  final case class NumberRange(fieldId: FieldId) extends ConstraintSlot
  final case class IntervalOverlap(fromFieldId: FieldId, toFieldId: FieldId) extends ConstraintSlot
  final case class GeoDistanceFilter(fieldId: FieldId) extends ConstraintSlot
}

object CanonicalConstraintView {
  def apply[Document](constraint: PlannedConstraint[Document]): CanonicalConstraint
  def slot(constraint: CanonicalConstraint): ConstraintSlot
}
```

`PlanIdentity`'s own canonical-constraint projection (§7.3) delegates to `CanonicalConstraintView` rather
than keeping a second conversion, so `PlanIdentity` and precedence resolution can never compute a
different canonical value for the same constraint.

Precedence resolution takes two already-ordered tiers — the caller's own domain policy decides which is
higher, never inferred from `ConstraintProvenance` — and returns applied/suppressed filters or typed
conflicts:

```scala
object ConstraintPrecedenceResolver {
  def resolve[Document](
    higherPriority: Vector[SourcedConstraint[Document]],
    lowerPriority: Vector[SourcedConstraint[Document]],
  ): Either[NonEmptyErrors[ConstraintResolutionError], ConstraintResolution[Document]]
}
```

Rules:

- within one tier, the first value seen in a slot is that slot's anchor; a later value in the same slot
  is compared only against the anchor (never against other same-slot values) — an equivalent later value
  is suppressed as `EquivalentDuplicate`, a non-equivalent later value is a typed
  `ConflictingHigherPriority`/`ConflictingLowerPriority` error;
- any same-tier conflict accumulates every such conflict from both tiers and returns `Left` with no
  partial applied/suppressed result;
- once both tiers resolve without conflict, each surviving lower-tier value is compared against the
  higher tier's applied value in the same slot only: an equivalent match suppresses as
  `EquivalentDuplicate`, a non-equivalent match suppresses as `OverriddenByHigherPrecedence`; different
  slots all apply;
- applied filters are ordered as unique higher-tier values in request order, then unique lower-tier
  survivors in parser order; suppressed filters are ordered as higher-tier encounter order, then one
  merged lower-tier encounter order across both same-tier-duplicate and cross-tier-suppression reasons,
  by each value's own original position in `lowerPriority` — never grouped by reason.

Public filter/sort geo-clause resolution is likewise generic, reached through narrow adapter views so the
resolver never depends on a domain's concrete decoded-filter/decoded-sort wrapper type:

```scala
trait PublicFilterPlanView[Filter, Document, Name] {
  def name(value: Filter): Name
  def clause(value: Filter): PublicFilterClause[Document]
  def provenance(value: Filter): ConstraintProvenance
}

trait PublicSortPlanView[Sort, Document, Name] {
  def name(value: Sort): Name
  def clause(value: Sort): PublicSortClause[Document]
}

object PublicPlanInputResolver {
  def resolve[Filter, Sort, Document, FilterName, SortName](
    filters: Vector[Filter],
    sorts: Vector[Sort],
    origin: Option[GeoPoint],
    filterView: PublicFilterPlanView[Filter, Document, FilterName],
    sortView: PublicSortPlanView[Sort, Document, SortName],
  ): Either[NonEmptyErrors[PublicPlanInputResolutionError[FilterName, SortName]], ResolvedPublicPlanInput[Document]]
}
```

Public filter and sort declarations use the same validated ordered index. Construction rejects every
later duplicate before creating a lookup map, so duplicate names cannot become a last-wins runtime
choice; `unsafeFrom` is reserved for static domain declarations whose invalid source is a startup
failure:

```scala
sealed trait PublicDeclarationError[+Name]
object PublicDeclarationError {
  final case class DuplicateName[Name](name: Name, firstIndex: Int, duplicateIndex: Int)
    extends PublicDeclarationError[Name]
}

object PublicInputRegistry {
  def apply[Input, Name, Operator, Clause, Error](
    specs: Vector[PublicInputSpec[Input, Name, Operator, Clause, Error]],
    nameOf: Input => Name,
    operatorOf: Input => Operator,
    unknownField: Name => Error,
    unsupportedOperator: (Name, Operator, Vector[Operator]) => Error,
  ): Either[NonEmptyErrors[PublicDeclarationError[Name]], PublicInputRegistry[Input, Name, Operator, Clause, Error]]

  def unsafeFrom[Input, Name, Operator, Clause, Error](
    specs: Vector[PublicInputSpec[Input, Name, Operator, Clause, Error]],
    nameOf: Input => Name,
    operatorOf: Input => Operator,
    unknownField: Name => Error,
    unsupportedOperator: (Name, Operator, Vector[Operator]) => Error,
  ): PublicInputRegistry[Input, Name, Operator, Clause, Error]
}

object PublicSortRegistry {
  def apply[Input, Name, Decoded, Error](
    specs: Vector[PublicSortSpec[Input, Name, Decoded, Error]],
    nameOf: Input => Name,
    unknownSort: Name => Error,
  ): Either[NonEmptyErrors[PublicDeclarationError[Name]], PublicSortRegistry[Input, Name, Decoded, Error]]

  def unsafeFrom[Input, Name, Decoded, Error](
    specs: Vector[PublicSortSpec[Input, Name, Decoded, Error]],
    nameOf: Input => Name,
    unknownSort: Name => Error,
  ): PublicSortRegistry[Input, Name, Decoded, Error]
}
```

BeautyQ keeps public names and accepted operators explicit. Its dynamic public inventory is composed
from the corresponding `DynamicFieldFamily.entries` in declaration order, so a declared dynamic field
cannot silently disappear between the document family and the request registry.

A `PublicFilterClause.GeoRadius`/`PublicSortClause.GeoDistance` clause without a supplied origin is a
typed `MissingLocationForFilter`/`MissingLocationForSort` error carrying the exact index and public name;
missing-location errors accumulate across every filter in request order, then every sort in request
order. Coordinates present with no geo clause requested activate no filter, sort or signal.

Facet declarations are validated once into one lookup used by the public-request boundary, so a domain
never maintains its own facet-ID list or lookup map. The trusted request carries the resolved typed
facet requests into compilation. A declaration holds only a `FacetRequest`; it never
repeats that request's own field(s) in a second vector, because `FacetRequest.fieldHandles` derives them:

```scala
object FacetRequest {
  def fieldHandles[Document](request: FacetRequest[Document]): Vector[SearchField[Document, ?]]
  // Terms/NumberRange -> one field; IntervalOverlap -> the from/to fields, in that order.
}

final case class FacetPlanDeclaration[Document](request: FacetRequest[Document]) {
  def fieldHandles: Vector[SearchField[Document, ?]] // = FacetRequest.fieldHandles(request)
}

object FacetPlanRegistry {
  def apply[Document](declarations: Vector[FacetPlanDeclaration[Document]]): Either[NonEmptyErrors[FacetPlanRegistryError], FacetPlanRegistry[Document]]

  /** For static domain-policy declarations only: throws with every offending declaration's facet ID,
    * index and validation error, rather than each domain hand-rolling its own match/throw. */
  def unsafeFrom[Document](declarations: Vector[FacetPlanDeclaration[Document]]): FacetPlanRegistry[Document]
}

final class FacetPlanRegistry[Document] {
  def ids: Vector[FacetId]
  def contains(id: FacetId): Boolean
  def resolve(requested: Vector[FacetId]): Either[NonEmptyErrors[FacetPlanRegistryError.UnknownRequestedFacet], Vector[FacetRequest[Document]]]
}
```

Construction validates every declaration's `FacetRequest` and rejects duplicate facet IDs before a
caller ever receives a usable registry; `resolve` preserves requested order independent of declaration
order and reports an unknown requested ID as `UnknownRequestedFacet` rather than silently dropping it.
`DuplicateFacetId(id, firstIndex, duplicateIndex)`, `InvalidFacetDeclaration` and
`UnknownRequestedFacet` remain one `FacetPlanRegistryError` hierarchy; request resolution narrows its
left type to `UnknownRequestedFacet` because declaration errors were rejected before the registry was
constructed. Every later duplicate is emitted once in declaration encounter order and retains both indexes.
`FacetSize.unsafeFrom(value: Int): FacetSize` is the equivalent framework-owned constructor for a
declaration's literal facet size.

Finally, one kernel resolves constraints, assembles the `SearchPlan`, attaches the resolver's
suppressions as diagnostics, and validates the result, so a domain compiler can never observe an
unvalidated plan - and never mutates an already-validated one. It exposes an opaque prepared value
precisely so a domain can derive its own plan mode from the *resolved* applied filters and provide one
final notices vector before validation, without forging a resolution or pairing it with another input:

```scala
final case class SearchPlanCompilationInput[Document](
  constraintPriorityTiers: ConstraintPriorityTiers[Document],
  residualText: Option[String],
  softSignals: Vector[PlannedSignal[Document]],
  sort: Vector[PlannedSort[Document]],
  page: PageRequest,
  facets: Vector[FacetRequest[Document]],
  groups: Vector[GroupRequest[Document, ?]],
)

final class PreparedSearchPlanCompilation[Document] private[plan] (
  private val input: SearchPlanCompilationInput[Document],
  val resolution: ConstraintResolution[Document],
) {
  def assemble(finalNotices: Vector[PlanDiagnostic]): Either[NonEmptyErrors[SearchPlanCompilationError], SearchPlan[Document]]
}

object SearchPlanCompilationKernel {
  def prepare[Document](input: SearchPlanCompilationInput[Document]): Either[NonEmptyErrors[SearchPlanCompilationError], PreparedSearchPlanCompilation[Document]]
  def compile[Document](input: SearchPlanCompilationInput[Document], finalNotices: Vector[PlanDiagnostic] = Vector.empty): Either[NonEmptyErrors[SearchPlanCompilationError], SearchPlan[Document]]
}
```

`ConstraintPrecedence.above(higher, lower)` owns one typed source order and returns a typed validation
error when both tiers name the same source; static domain declarations use
`ConstraintPrecedence.unsafeAbove(...)` as the fail-fast constructor. Its `tiers` method requires one
complete typed `Source => Vector[SourcedConstraint[Document]]` function and constructs the
framework-private `ConstraintPriorityTiers`; domain modules cannot construct that tier value directly.
Diagnostic views render the ordinary source enum values directly. This kernel supports exactly two
ordered source tiers.

A resolver conflict is wrapped as `ConstraintResolutionFailed`; a `SearchPlan.validate` failure once
resolution succeeds is wrapped as `InvalidSearchPlan`. The kernel knows nothing about any domain's plan
modes, semantic labels, matched rule IDs, user-location source, public names or browse-default
classification — those stay domain policy, applied by the domain compiler between `prepare` and
`prepared.assemble(finalNotices)` (§10.2), never after `assemble`/`compile` already returned.

### 7.10 Append-only supplement selection and lifecycle-bound membership (implemented, Brick 7A)

`BaselineMembershipResult[Id]` is a final class with a private constructor and a
`private[gen2] fromBackend` factory. Domain wiring (`leaderboard.search.beautyq.gen2`) cannot call
`fromBackend` or the constructor. It carries `requestedIds` and `matchingIds` (stored in
`requestedIds` order, containing only IDs from `requestedIds`).

`AppendOnlySupplementPolicyError.InvalidMaxAppended` rejects zero and negative `maxAppended` values.
`AppendOnlySupplementPolicy` owns `select[Candidate, Id]` which classifies candidates in a single
immutable fold: current-page duplicates go to `currentPageDuplicateIds`, complete-baseline members
(not on current page) go to `baselineMemberIds`, up to `maxAppended` candidates go to `appended`,
and remaining eligible candidates go to `budgetExcludedIds`. Classification precedence: current-page
duplicate > baseline member > append > excluded. Candidate order is preserved; score is never used
for fusion or reranking. Returns `MembershipInputMismatch` when candidate IDs differ from requested
IDs including order.

`AppendOnlySupplementSelection[Candidate, Id]` is a final read-only result with
`appended`, `currentPageDuplicateIds`, `baselineMemberIds` and `budgetExcludedIds`. No public
constructor/apply/copy.

`BoundElasticsearchBaselineResult[Document, Id]` is a final class carrying
`authorizedRequest: AuthorizedElasticsearchSearchRequest[Document, Id]` (private[elasticsearch])
and `result: ElasticsearchFullSearchResult[Document, Id]`. Domain code can read `target` and
`generationReference` but cannot replace authorization, target, generation or query.

`ElasticsearchBaselineService.searchBound` moves the authorize → search → decode → groups execution
into one method; `search` becomes a derived view via `_.result`. There is one execution
implementation.

`ElasticsearchBaselineMembershipCompiler.compile` takes a `BoundElasticsearchBaselineResult`
and `candidateIds`, encodes each ID through the bound baseline's authorized identity-field codec via
`ElasticsearchScalarCompiler.toBackendJson`, and produces `CompiledElasticsearchBaselineMembershipRequest`
with body:

```json
{
  "size": <candidate count>,
  "_source": false,
  "track_total_hits": false,
  "query": {
    "bool": {
      "must": [<baseline.authorizedRequest.prepared.executionQuery>],
      "filter": [{
        "terms": {
          "<identity field path>": [<typed encoded candidate ids in candidate order>]
        }
      }]
    }
  }
}
```

No sort, search_after, cursor, facet/group aggregation, `_source` loading, score fusion, wildcard
target or active alias.

`CompiledElasticsearchBaselineMembershipRequest[Document, Id]` is a final class carrying the bound
baseline, candidate IDs and compiled body. Target is derived from the bound baseline. No constructor
accepting raw query JSON or physical target.

`ElasticsearchBaselineMembershipDecoder.decode` validates the response shape (top-level object,
hits object, hits.hits array), rejects excessive hits, malformed hits, decode failures, duplicate
identities (with first/duplicate indexes) and unexpected identities. Creates `matchingIds` by
filtering `candidateIds` against the decoded match set in request order.

`ElasticsearchBaselineService.membership` returns empty result without transport call when
`candidateIds` is empty; otherwise compiles, POSTs to bound target, and decodes.

## 8. BeautyQ Gen2 executable composition

The contract root and downstream composition facade have different module-safe ownership:

```text
BeautyQSearchGen2
├── contract -> BeautyQSearchDeclarations
│   ├── catalog.topology
│   └── variants (Fields, document, request, intent, plan/candidate policy)
├── input (request validation and intent parser)
├── materialization (snapshot, canonical-row, projection and materializer owners)
├── plan (policy, groups, full-plan compiler and candidate compiler owners)
├── qdrant (policy, resources, runtime, hydration policy and candidate pipeline owners)
└── elasticsearch (policy, resources, generation, baseline and service owners)
```

Every facade member is a direct reference to an implemented owner; it contains no copied policy and no
placeholder Qdrant, public-response, quality or evaluation branch. Future implemented branches are added
to this facade without making the backend-neutral contract module depend on downstream modules.

### 8.1 Stable identities

`ServiceCode` and `CategoryCode` are the stable business identities used and validated by the current BeautyQ Gen2 contract.

`ServiceCode` and `CategoryCode` live in the shared BeautyQ domain and persistence models rather than in a Gen2-only sidecar. They are the current stable identities used by the native Gen2 contract and may also be present in shared domain/API representations. No side-by-side Gen1 search guarantee remains after the completed cutover.

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

`VersionedSnapshot` and `MaterializedSearchDocuments` remain simple internal immutable Scala values.
The canonical production snapshot source computes the source fingerprint with the snapshot it returns,
and `SearchMaterializer` computes the projected fingerprint from the exact projected vector it returns.
No untrusted production caller supplies these aggregates, so no additional defensive construction layer
is part of the accepted architecture.

`CanonicalSnapshotTuple` supports any product composed of `Vector[Entity]` members and
`CanonicalSnapshot.Single[Entity]` members in any order. `Single` is a distinct wrapper so its typeclass
resolution stays unambiguous even when an entity type is itself collection-shaped. Bare singleton
values and `Option`/`Map`/`Set` snapshot members are unsupported and fail given resolution rather than
receiving an implicit encoding.

Canonical source rows use
`canonicalRow(prefix, source).field(...).group(...).build`; a domain selects fields, nested groups and
values, while the kernel derives framing and deterministic row ordering for registered codecs.

Catalog relations remain a separate generic `repo-core` boundary. `search-gen2-core` owns snapshot,
projection and materialization mechanics and must not acquire a reverse dependency on `repo-core` merely
to combine their tests. A domain module composes the two boundaries and proves their declarations agree.

## 10. BeautyQ request and response

### 10.1 Request

```scala
final case class BeautySearchRequestGen2(
  query: Option[String],
  filters: Vector[PublicFilterInput],
  requestedFacets: Vector[FacetId],
  sort: Vector[BeautySortInput],
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
- plan compilation follows the constraint-precedence policy defined in this specification.

### 10.2 Plan compilation

`BeautyQSearchPlanCompiler.compile(request, intent)` combines the validated request and parsed intent
into a `CompiledBeautyQSearchPlan`, composing §7.9's reusable mechanics. It always uses
`BeautyQSearchPlanPolicy`'s typed precedence and geo-origin values; callers cannot substitute either
policy per call or reverse BeautyQ's precedence:

```scala
type CompiledBeautyQSearchPlan = BeautyQSearchPlanCompiler.CompiledBeautyQSearchPlan

object BeautyQSearchPlanCompiler {
  final class CompiledBeautyQSearchPlan private[BeautyQSearchPlanCompiler] (
    val boundPlan: BoundSearchPlan[VariantSearchDocumentGen2],
    val mode: BeautyQSearchPlanMode,
    val canonicalSemanticLabels: Vector[CanonicalSemanticLabel],
    val matchedRuleIds: Vector[IntentRuleId],
  ) {
    def plan: SearchPlan[VariantSearchDocumentGen2] = boundPlan.plan
  }

  def compile(
    request: ValidatedBeautySearchRequestGen2,
    intent: ParsedBeautyIntentGen2,
  ): Either[NonEmptyErrors[BeautyQSearchPlanCompileError], CompiledBeautyQSearchPlan]
}

enum BeautyQSearchPlanMode {
  case SemanticSearch, StructuredBrowse, DefaultBrowse
}
```

The compiler's gate order is:

1. **Gate 1 — public geo-input resolution**: `PublicPlanInputResolver.resolve` over the request's
   decoded filters/sort and `BeautyQSearchPlanPolicy.geoOriginPolicy.resolve(request)`.
2. **Validated-request boundary — facet policy lookup**: `BeautySearchRequestGen2.validate` resolves
   `request.requestedFacets` once through `BeautyQSearchPlanPolicy.facetRegistry` and stores the typed
   `FacetRequest` values in `ValidatedBeautySearchRequestGen2.facets`; compilation consumes that value
   and never looks up facet IDs a second time.
3. **Gate 2 — prepared constraint precedence**: the typed `PublicRequest`/`ParsedIntent` binding feeds
   `ConstraintPrecedence.tiers`, then `SearchPlanCompilationKernel.prepare` owns the exact input and
   resolution. A caller cannot pair another resolution with it.
4. **Mode and notice derivation**: the prepared resolution's applied filters plus parsed intent facts
   classify the mode and derive one final notices vector. Raw query, requested facets, cursor presence,
   suppressed-filter count and matched-rule count are not classification inputs.
5. **Gate 3 — assembly and validation**: `prepared.assemble(finalNotices)` returns the validated plan.
6. **Gate 4 — cursor binding**: `SearchCursorEnvelope.bind` validates the assembled plan's cursor
   against the declaration-derived `PlanIdentity`. Only then is the compiler-owned result returned.

`BeautyQSearchPlanPolicy.classify` owns the mode rule: semantic facts first, then applied filters or
explicit sort for structured browse, otherwise default browse. Requested facets alone do not make a
plan structured.

`SemanticSearch = residualText.isDefined || softSignals.nonEmpty || canonicalSemanticLabels.nonEmpty`;
else `StructuredBrowse = appliedFilters.nonEmpty || sort.nonEmpty`; else `DefaultBrowse`. Requested
facets alone never make a plan `StructuredBrowse`.

BeautyQ's own policy, declared once in `BeautyQSearchPlanPolicy` and exposed for review at
`BeautyQSearchDeclarations.variants.plan`:

- **constraint-source precedence** — one generic `ConstraintPrecedence[BeautyQConstraintSource]`
  orders the typed enum cases `PublicRequest` above `ParsedIntent`; its exhaustive `tiers` binding
  derives from that same value. `ExplicitUi`/`FacetSelection` (public request, equal priority) therefore
  sit strictly above `ParsedHard` (parsed intent);
- **geo-origin policy** — `BeautyQGeoOriginPolicy.RequestUserLocation`: its `resolve` reads
  `request.userLocation` for both the public geo filter and the public geo sort, with no server-side
  default origin; its `sourcePath` exposes that choice in the declaration tree and diagnostic trace;
- **facet inventory** — exactly `service` (`Terms` over `serviceCode`), `category` (`Terms` over
  `categoryCode`), `price` (`IntervalOverlap` over `priceFrom`/`priceTo`) and `durationMinutes`
  (`NumberRange` over `durationMin`), each with the Gen1-evidence bucket table, half-open finite buckets
  and an upper-unbounded final bucket, declared via `FacetSize.unsafeFrom`/`FacetPlanRegistry.unsafeFrom`
  (§7.9) rather than a hand-rolled `getOrElse(throw ...)` or manual match/throw;
- **group policy** — two explicit `RequireExact` projections: provider/location grouped by
  `masterLocationId` and service grouped by `serviceId`, each with representative fields, best-score
  ordering, matching-count ordering and a stable-key tie-breaker. The provider projection adds its
  declared minimum-distance metric only when the compiled plan contains the `location` proximity signal;
  coordinates alone never change the group policy;
- **cursor contract version** — `BeautyQSearchDeclarations.variants.plan.contractVersion` is the
  explicit BeautyQ compatibility choice mixed into the generic declaration-derived fingerprint;
- **mode classification** — the policy's executable classifier described above.

`BeautyQSearchPlanPolicy.facetRegistry` is the direct owner of public facet IDs and lookup; no compatibility
registry or separately editable facet list is kept. `BeautyQSearchDeclarations.variants.plan` exposes the
`constraintPrecedence`/`geoOriginPolicy`/`facetRegistry`/`groupPolicy`/`defaultBrowsePolicy` values
directly - not only their rendered summaries - so a reviewer can navigate straight to every actual
declaration; its summary fields derive from those same typed values rather than restating them.

`BeautyQSearchPlanCompilationTrace.render` produces a deterministic, human-readable
`request`/`intent`/`policy`/`compilation`/`plan` sectioned view of one compilation for review and golden
tests; its policy section renders `BeautyQSearchPlanPolicy`'s own values directly rather than a second,
hand-maintained rendering.

### 10.3 Response

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

`search-gen2-elasticsearch` owns domain-neutral compilers and lifecycle mechanics.
`beautyq-search-gen2-wiring` owns one compact `BeautyQElasticsearchPolicy` over the exact handles from
`BeautyQSearchDeclarations.variants.Fields`, plus composition with materialization and resource names.
The backend module must not know BeautyQ; the domain module must not recreate mapping/source/request/
response traversal.

The Elasticsearch policy is one executable source for the legitimate domain choices used across the
index and query paths: text analyzers, text weights/ranking parameters, geo scoring parameters,
exactness requirements and the explicit compatibility contribution. Mapping, indexed source,
aggregation names, identity tie-breakers, contract fingerprint input and structural test views derive
from that policy plus the canonical document/plan declarations. Operational host credentials are
configuration; fixed superseded-generation cleanup is lifecycle mechanics, not business policy.

### 11.1 Mapping and ingestion (implemented, Brick 5A)

`ElasticsearchIndexPolicy[Document, Id]` binds one `SearchDocumentDeclaration[Document, Id]`, its
`PlanContractVersion`, an explicit `ElasticsearchPolicyVersion`, and the domain's ordered
`ElasticsearchTextFieldMapping[Document]` analyzer assignments for its declared searchable text fields.
`ElasticsearchCompilerVersion`/`ElasticsearchIndexFormatVersion` are framework-owned: every constructed
policy carries exactly `ElasticsearchCompilerVersion.Current`/`ElasticsearchIndexFormatVersion.Current`,
and neither `ElasticsearchIndexPolicy.apply` nor `.unsafeFrom` accepts either as a parameter - a domain
cannot substitute one. Its only construction paths are `ElasticsearchIndexPolicy.apply` (returning
`Either[ElasticsearchIndexPolicyErrors, ElasticsearchIndexPolicy[Document, Id]]`) and
`ElasticsearchIndexPolicy.unsafeFrom` for static domain declarations. It validates:

- every declared searchable `Text` field has exactly one analyzer assignment (a missing one is reported
  in the document declaration's deterministic field order);
- no duplicate assignment (reported with its first/duplicate index);
- no assignment to an undeclared, foreign-document, or non-searchable/non-`Text` handle - a `SearchField`
  belonging to another document type fails to compile rather than reaching this runtime check.

Brick 5C-D closes the initial public analyzer algebra to framework-known built-ins (`standard` and
`whitespace`) and rejects arbitrary names before index creation; BeautyQ uses `standard`, and arbitrary
custom analyzer support is not an initial framework feature.

`ElasticsearchMappingCompiler.compile` and `ElasticsearchDocumentCompiler.compile` both traverse
`SearchDocumentDeclaration.allFields` in its contract order through the shared, domain-neutral
`ElasticsearchDottedPathTree`; BeautyQ supplies no parallel encoder or field inventory. The
low-boilerplate field DSL builds that order as static declaration order followed by dynamic-family
declaration order. Dotted dynamic-field paths compile into deterministic nested objects; optional
extraction omits the leaf, and an
entirely absent optional parent is never emitted. Every logical value uses its declared canonical
`SearchValueCodec` with the ES representation selected by `SearchFieldKind` (`keyword`; `text` plus the
policy analyzer; `integer`; `long`; `double`; `boolean`; `date` with `strict_date_optional_time`; or
`geo_point` as an exact `{lat, lon}` decimal object - `BigDecimal` values are never converted through
`Double`). Every declared field - identity included - is extracted and canonically encoded exactly once
per document, through one shared field-compilation path; the identity's one compiled value is reused for
both the document's `_id` and its `_source` entry, never re-extracted. A path conflict - one declaration
path that cannot coexist with another already-built object/leaf path - is a typed
`ElasticsearchPathConflict`; an empty canonical identity value is a typed `EmptyDocumentId`. The
document compiler does not report a "missing required value" error - `FieldExtraction.Required` always
extracts a value by construction, so that state is unreachable through the exposed API - but it does
report a typed `ElasticsearchDocumentCompileError.ValueEncoding` (carrying the document index, field ID
and declared kind) when a field's own codec produces canonical text that the standard codec for its
declared `SearchFieldKind` cannot decode, for example a custom `Int`-kind codec whose `encodeCanonical`
returns `"one"`; this is a reachable state (a caller-suppliable codec is only guaranteed to round-trip
through itself, never to match its field's declared backend kind), so it is a typed `Left`, never thrown.
For the identity field specifically, emptiness is checked on its extracted canonical value before
backend-kind decoding ever runs, so an empty identity is always `EmptyDocumentId`, never `ValueEncoding`,
regardless of the identity field's declared kind. `EmptyDocumentId` and `ValueEncoding` are the complete
Brick 5A document-shape validation surface; complete live Elasticsearch acceptance - including backend
limits such as the 512-byte `_id` size cap and configured analyzer/mapping availability - is a Brick 5C
concern, not implemented here.

`ElasticsearchGenerationCompiler.compile` calls both compilers exactly once each over the same policy and
`MaterializedSearchDocuments`, and derives one `ElasticsearchGenerationIdentity` (source content,
projected-documents and contract fingerprints, plus projection-format/compiler/index-format versions)
directly from those inputs. It returns the one compiler-bound `CompiledElasticsearchGeneration[Document,
Id]` - a `final` class whose constructor is private to the compiler, with no public
`apply`/`copy`/subclassing path - binding the compiled mapping, ordered indexed documents and identity
together, so a caller cannot independently pair a different declaration, contract fingerprint, projected
fingerprint, or independently compiled mapping/document vector. HTTP bulk encoding and alias activation
(Brick 5C) consume that artifact later; they do not rebuild it.

BeautyQ's own `BeautyQElasticsearchPolicy` declares only its five canonical searchable text fields
(`Fields.allText`/`serviceText`/`attributeText`/`providerText`/`locationText`), all using the standard
analyzer, under the explicit `beautyq-elasticsearch-v1` policy version; `BeautyQElasticsearchGeneration`
is a thin composition from `MaterializedBeautyQVariantDocuments` to the generic generation compiler. The
Elasticsearch contribution to `PlanContractContributions` - one `elasticsearch`-keyed entry, derived
canonically from the policy/compiler/index-format versions and every searchable text field's declaration
identity and analyzer - is the exact same value `BeautyQSearchPlanCompiler`'s cursor-bound plan identity
and the ES generation artifact both consume; no handwritten final fingerprint or second hash protocol
exists.

### 11.2 Request compilation (implemented, Brick 5B)

`ElasticsearchPolicy[Document, Id]` is the one complete, fingerprint-owning executable policy: it binds
one `ElasticsearchIndexPolicy` (Brick 5A's mapping/source concern, now index-only - it no longer carries
`contributions`/`contractFingerprint` itself) to the domain's query choices - weighted searchable text
fields (`ElasticsearchWeightedTextField`, each a declared searchable-text handle plus a positive
`ElasticsearchQueryWeight`), an `ElasticsearchTextOperator`, an optional `ElasticsearchGeoScoringPolicy`
(scale/offset as `Distance`, a decay strictly in `(0, 1)`, a positive weight), an
`ElasticsearchTotalHitsPolicy`, and an `ElasticsearchDefaultSortPolicy` (relevance and identity
tie-breaker directions). `ElasticsearchCompilerVersion`/`ElasticsearchIndexFormatVersion` remain owned by
the nested index policy; `ElasticsearchSearchCompilerVersion` is a new, separate framework-owned version
token contributed by the query side alone and reused unchanged as
`ElasticsearchCursorState.protocolVersion`, so cursor-state and query-fingerprint versioning share one
protocol rather than two. `ElasticsearchPolicy.contributions`/`.contractFingerprint` is the one contract
fingerprint now consumed by `ElasticsearchGenerationCompiler`, `BeautyQSearchPlanCompiler`'s bound-plan
identity and `ElasticsearchSearchRequestCompiler` alike; changing any executable query choice - a weight,
the operator, any geo parameter, the total-hits policy, either sort direction - changes it. Construction
mirrors `ElasticsearchIndexPolicy`'s own `apply`/`unsafeFrom`/validate/normalize shape for this parallel
concern, rather than a shared generic validator. `ElasticsearchTextOperator` is the closed `And`/`Or`
algebra; query weights must be finite and positive, geo weight finite and positive, and geo decay finite
in the open interval `(0, 1)`. Non-finite `Double` values are typed policy errors and can never reach
request JSON. The identity declaration is also validated against the emitted value-sort tie-breaker:
Text and GeoPoint identities are rejected before request compilation.

`ElasticsearchSearchRequestCompiler.compile(policy, boundPlan)` is the pure preparation entry point,
returning `Either[ElasticsearchSearchRequestCompileError, PreparedElasticsearchSearchRequest[Document, Id]]`.
It accepts only an already cursor-bound `BoundSearchPlan` - a raw `SearchPlan` does not
type-check - and rejects a bound plan whose `contractFingerprint` differs from the policy's own
(`ContractFingerprintMismatch`) before compiling anything. The prepared request carries the complete
validated `plan.groups` vector. Elasticsearch executes each group through an exact composite traversal
with explicit representative/metric sub-aggregations; group results are decoded separately from the
hit window and then exposed by the full baseline result.

The compiled request body is exact and deterministic: `_source: true`, `track_total_hits: true`,
`track_scores: true`, `size` equal to `pageSize + 1` (one lookahead hit beyond the requested page, or a
typed `UnrepresentablePageSize` if that would overflow), `query`, `sort`, `search_after` when a cursor is
present, and `aggs` when facets are requested - `from` is never emitted, on the first page or any later
one. The base query follows Elasticsearch's own standalone-query-type shape rather than a uniform `bool`
wrapper: residual text alone compiles to a bare `multi_match` over the policy's weighted fields
(`field^weight`) and operator; hard filters alone compile under `bool.filter` with no `must` key (filter
context has no standalone top-level query type); both together compile to
`bool{must: [multi_match], filter: [...]}`; neither present compiles to an explicit `match_all`, never an
empty `bool`. Hard constraints compile per kind: `Terms` as `term` for a single canonical value or sorted
`terms` for several (never dependent on `Set` iteration order), and `match_none` - never a dropped clause
- for an empty set; `NumberRange` preserves every inclusive/exclusive/unbounded bound combination and
uses `match_all` for both-unbounded bounds, with the canonical ISO instant string for `DateTime` fields;
`IntervalOverlap` compiles the
the exact interval-overlap direction mapping (the request's lower bound constrains the indexed `to` field with
`gte`/`gt`; the request's upper bound constrains the indexed `from` field with `lte`/`lt`), collapsing to
`match_all` when fully unbounded; `GeoDistanceFilter` compiles with meters and explicit `distance_type:
arc`. Geo scoring is independent of both hard filters and sorts: a `GeoProximitySignal` contributes one
`gauss` function wrapped in `function_score` around the unmodified base query (`score_mode`/`boost_mode:
sum`); a signal with no configured `geoScoringPolicy` is a typed `MissingGeoScoringPolicy` error and
cannot produce a request, so no scoring signal disappears; a `GeoDistanceFilter`/`GeoDistanceSort`
compiles with no scoring function at all. Sort compiles explicit plan clauses in order; when none exist,
relevance (`_score`, the policy's relevance direction) is used only if scoring is active (residual text
or a successfully compiled geo signal); the declared document identity is always appended as the final
deterministic value-sort tie-breaker. Policy validation rejects Text and GeoPoint identities, which cannot
support that emitted value sort, before request compilation. Facet aggregation names derive directly from the typed `FacetId` as
`facet:<value>`, never from a normalized field path; `Terms`, `NumberRange` and `IntervalOverlap` facets
all compile under the plan's complete `AllAppliedHardFilters` context with no self-exclusion, and the
same shared overlap-predicate builder used for hard-constraint compilation is reused for
`IntervalOverlap` facet buckets, so the direction mapping is expressed in exactly one place.

The prepared request contains no executable physical target. For a first page it carries
`ElasticsearchGenerationRequirement.Active`; for a later page it carries the decoded, untrusted
`ElasticsearchGenerationReference` from cursor state. The lifecycle owner must resolve/authorize that
reference and produce an `AuthorizedElasticsearchSearchRequest` before transport or response decoding.
`ElasticsearchSearchTarget` exists only on that lifecycle-selected generation; an untrusted cursor string
never becomes an executable index name. `ElasticsearchCursorState(protocolVersion, generationReference,
searchAfterValues)` is the versioned, typed backend state the generic `SearchCursorEnvelope` carries
opaquely; its supported `search_after` scalar domain is exactly string/number/boolean/null
(`ElasticsearchSearchAfterValue`; arrays/objects are rejected), `ElasticsearchCursorStateCodec` produces
deterministic JSON with an explicit protocol version, never an offset/`from`, and decoding validates arity
against the compiled sort vector. This module only encodes/decodes its own opaque payload string; parsing
and binding the envelope itself remains solely `SearchCursorEnvelope`'s concern.

Query-value encoding reuses `ElasticsearchScalarCompiler.toBackendJson` - the same shared kind-to-JSON
mechanic Brick 5A's document compiler uses for indexed source values - so a query value and an indexed
value for the same field always follow the same backend representation rules; each caller wraps the
shared `SearchValueDecodeError` with its own context (`ElasticsearchQueryValueContext.Constraint` or
`.Facet`, versus the document compiler's `documentIndex`).

`PreparedElasticsearchSearchRequest[Document, Id]` is the compiler-owned, private-constructor, `final`
aggregate binding `generationRequirement`, `body`, `executionQuery`, `boundPlan`, `identityField`,
`sortShape`, `requestedFacets`, `requestedGroups`, `totalHitsPolicy` and `pageSize`. Lifecycle authorization wraps it in the equally
closed `AuthorizedElasticsearchSearchRequest`, which adds the resolved generation target. Neither result
has a public `apply`/`copy`/subclass path; the response decoder consumes only the authorized aggregate and
never a separately supplied plan, facet list, sort shape, policy or target.

### 11.3 Response decoding (baseline implemented, group extension in Brick 5D)

`ElasticsearchSearchResponseDecoder.decode(authorizedRequest, responseJson)` returns
`Either[ElasticsearchSearchResponseErrors, BaselineSearchPage[Document, Id]]`. The Brick 5D group executor
decodes each requested composite traversal into an exact generic group result, and the full baseline
aggregate joins the page and group values. Only these role-specific Elasticsearch types decode a response;
BeautyQ adds its typed carousel projection in wiring, never a generic domain type. Brick 5B types decode
the baseline response - never Qdrant candidate
slots or an HTTP client's own response type. `BaselineSearchPage[Document, Id]` is a decoder-owned,
private-constructor, `final` read-only aggregate (hits, exact/qualified total, typed facets, validated
backend diagnostics and next cursor); it
has no public `apply`/`copy`/subclass path either. A `timed_out` or shard-failure response is a typed
partial-response error, never a trusted page.

Each hit decodes its typed identity from `_id` through the declaration's own identity codec - never a
speculative `_id == _source.identity` check - while an object `_source` is preserved as the response's raw JSON,
never reconstructed into a `Document`. A total-hits relation other than `eq` under an exact-total policy
is a typed error. A hit's sort tuple is validated for both value shape and arity against the request's own
compiled sort vector before use.

Facet decoding compares the response's returned aggregation names against the compiled request's own
recorded `requestedFacets` and reports every missing or unknown one deterministically; keyed range and
interval-overlap buckets are restored into declared bucket order regardless of response order; dynamic
`Terms` bucket keys remain typed through the requested field's codec (preferring `key_as_string`, with
scalar string/number/boolean fallback), and per-bucket error bounds remain present. Precision is
classified honestly as exact (`0`), bounded (positive), or unknown (`-1`/missing); values below `-1`,
negative counts and negative `sum_other_doc_count` are malformed. Facet counts always come from
Elasticsearch aggregations, never recounted from the returned hit page.

Pagination rejects a raw hit array longer than `pageSize + 1` before decoding any hit, then returns only
`pageSize`; `hasNext` is determined from whether that lookahead hit was present. Every hit must contain a
JSON-object `_source`, which is preserved raw. When a lookahead exists, the next cursor is issued from the
last *included* hit's sort tuple - never the lookahead hit itself - encoding the authorized generation
reference via `SearchCursorEnvelope.issue` against the same bound plan; when it does not, no next cursor
is issued at all.

Group buckets, representative data and metrics are decoded by the Brick 5D group executor after the
baseline page; they are not reconstructed from the returned hit window.

### 11.4 Group implementation (implemented, Brick 5D)

A plain `terms` aggregation is not sufficient. The prepared request retains the typed group vector and
the generic Elasticsearch owner executes one deterministic composite traversal per group with the same
compiled execution query. The composite page size is framework-owned; `after_key` traversal continues
until exhaustion, then all buckets are ordered by the declared tuple and truncated to the declared group
size.

The query requests `top_hits` representative data and every declared ordering metric. Keys are decoded
through the exact declared field codec; representative fields are decoded through the exact requested
handles; counts and diagnostics are validated; and the decoded precision is `Exact` for this composite
implementation. A repeated `after_key`, duplicate key or malformed representative is a typed response
error rather than a partial result.

The implementation passes exact fixture tests for:

- representative document;
- matching count;
- best score;
- optional proximity metric;
- deterministic bucket order.

BeautyQ declares provider/location and service groups in its plan policy. The provider group adds the
minimum-distance metric only when a compiled `location` proximity signal exists; a location coordinate
alone never adds geo ordering. BeautyQ wiring projects only typed provider/service carousel values and
does not recreate field handles or group IDs.

### 11.5 Lifecycle

Brick 5C uses deterministic versioned physical indexes and one stable alias:

```text
compiled generation
  -> canonical persisted identity and physical name
  -> create/reuse
  -> bounded sequential bulk + refresh
  -> strict metadata/mapping/live-count validation
  -> one atomic alias update
```

The physical name is a non-empty configured prefix plus a full lowercase SHA-256 derived from the six
persisted generation-identity strings through the repository's shared `CanonicalFingerprint` framing.
Mapping `_meta` stores the complete metadata: schema version, generation ID, canonical build instant,
document count and persisted identity. The lifecycle-resolved aggregate retains that exact decoded
metadata. Reuse requires exact name, metadata identity, mapping `properties`, metadata count and a live
`_count` whose shard diagnostics are complete and failure-free; `builtAt` is observation metadata and is
excluded from reuse identity.

Cursor state remains untrusted. It carries only a generation reference; lifecycle validates its prefix/hash
shape, reads and verifies persisted metadata, and only then constructs an executable physical target.
Authorization compares the plan's trusted contract fingerprint with the persisted raw value. A failed
build never changes the alias. A physical index created by the current call is cleaned up only before
mapping/count validation succeeds; a concurrently existing or already validated generation is never
blindly deleted.

Brick 5C-D implements deletion only for generations atomically marked by the lifecycle-owned
`<active-alias>--superseded` alias. A metadata-valid, not-yet-active candidate has neither lifecycle
alias and is therefore not a cleanup candidate. During activation, old active targets are moved to the
superseded alias in the same exact `_aliases` operation that installs the new active target. Cleanup
reads only that alias, validates each exact mapping `_meta` against the physical name and persisted
identity, then sends one sorted exact `remove_index` action list. A later activation cannot delete the
current active target: cleanup performs a read-side sole-active check and repeats the active relationship
as a `must_exist` remove/add guard inside the same atomic `_aliases` request as every `remove_index` action.
An active/superseded overlap or a failed guard therefore fails closed. Reactivation removes a stale
superseded marker from the target becoming active in the same atomic alias mutation. A successful aliases response may omit
`action_results`; when results are present they are decoded strictly in request order. A superseded
target whose mapping has disappeared is treated as idempotently already removed, while other lookup
transport failures remain typed. Cleanup failures are typed and retryable; a cursor pinned to a deleted
generation is a typed stale-generation failure. There is no configurable retention policy in the initial
greenfield architecture.

The neutral synchronous JDK client accepts an omitted endpoint port or an explicit port in `1..65535`,
owns confined HTTP paths, timeouts, methods/content types, and maps request construction, connection,
HTTP and JSON failures to typed errors carrying method and path. Bulk framing derives from compiled documents,
respects document and UTF-8 byte limits, preserves order and is followed by exactly one refresh when
non-empty; item failures retain global/local index, ID, target, status and raw error JSON. Create-race
recovery recognizes only a parsed `error.type = resource_already_exists_exception` envelope. Alias
activation atomically moves sorted old targets to the lifecycle-owned superseded alias, removes any
stale superseded marker from the new target, and never marks the new target superseded. Cleanup and
activation responses accept Elasticsearch's normal acknowledged success without `action_results`; when
present, results are accepted only in Elasticsearch's structured
`action_results` shape (`action.type`, exact `action.indices`, optional `action.aliases`, integer
`status`, and raw `error`), with declaration order preserved. The baseline
service authorizes a prepared request through lifecycle, sends its unchanged JSON to the authorized
physical target, and delegates the raw response to the existing typed decoder. BeautyQ declares only its
resource names and composes these generic owners; repository materialization belongs to Brick 8 runtime
composition.

## 12. Qdrant Gen2 requirements

`search-gen2-qdrant` owns the neutral Qdrant 1.18.3 transport, collection lifecycle, authorization, response decoding, and candidate execution. BeautyQ application composition owns query embedding and candidate hydration. `QdrantGen2DockerPlugin` is the sole Distage-managed Qdrant container owner, and `QdrantGen2PortCfg` is the sole Qdrant endpoint view. No Gen1 Qdrant search runtime remains.

### 12.1 Semantic query text policy

Embedding input is produced by `BeautyQSemanticCandidatePolicy`; the Qdrant compiler does not choose
between raw and residual query text. BeautyQ owns semantic composition and eligibility, while the
backend compiler consumes only an eligible `CandidatePlan`.

Initial BeautyQ policy is:

```text
semantic text =
  normalized residual text
  + canonical human-readable labels emitted by parser-recognized semantic clauses
```

Rules:

- do not embed the raw original query verbatim after parsing;
- include normalized residual text first;
- append the canonical labels emitted by parser-derived service, category, attribute, provider or location
  semantics. The parser deduplicates labels by `stableKey`, preserving the first occurrence and its order;
  semantic composition consumes that resulting vector in order and does not deduplicate again by display
  text, so distinct stable keys with identical display text both remain in the embedding input;
- canonical labels come from Gen2 declarations keyed by stable codes, not mutable request strings;
- exclude explicit UI filters, facet selections, numeric price/duration bounds, geo coordinates, sort instructions and system defaults from semantic text;
- use deterministic ordering and normalization so the same semantic request produces the same embedding input;
- when both residual text and canonical semantic labels are empty, semantic-text construction returns
  `Left(SemanticQueryTextError.EmptyOrBlank)` and the active `SemanticQueryText` gate produces
  `Ineligible(BeautyQCandidateIneligibility.NoSemanticQueryText)`;
- a filter-only request or default-browse request therefore skips Qdrant supplementation without error.

The semantic-text boundary is
`Either[SemanticQueryTextError, SemanticQueryText]`. `SemanticCandidateEvaluation` then evaluates the
declared typed gates once and returns
`Either[CandidateEvaluationError, CandidateEvaluation[Gate, Plan, Reason]]`, deriving the typed
`CandidatePlanDecision` from those stored outcomes. A `CandidatePlan` exists only inside an eligible
decision; ineligible decisions carry the domain's reason instead. `CandidateEvaluationError` remains a
distinct malformed-policy error and is never translated into BeautyQ business ineligibility.

### 12.2 Policy and deterministic collection artifacts (Brick 6A)

`QdrantPolicy[Document, Id]` is one validated, declaration-owned policy. Its identity and embedding
handles must belong to the same `SearchDocumentDeclaration`; the identity is keyword/integer/long,
the embedding field is searchable text, model values are non-blank with a positive dimension, and
all pushed-down filter capabilities must be payload-eligible and supported by the closed Qdrant
schema vocabulary. Payload fields and payload indexes are derived from the declaration in order;
BeautyQ supplies no parallel payload inventory. Collection and candidate fingerprints are separate
typed contributions: collection compatibility excludes retrieval-only knobs, while candidate identity
includes vector/model/distance/retrieval choices plus the compiler and REST protocol versions that define
its executable wire shape.

`QdrantGenerationCompiler.prepare` creates compiler-owned points sorted by canonical point identity and
binds each embedding input to its subject/model fingerprint. `complete` accepts only matching finite
vectors of the declared dimension, derives a deterministic generation identity and physical collection
name, and emits collection metadata containing the complete identity plus exact payload-index requests.
No constructor/copy path lets a caller pair a vector with another prepared input.

### 12.3 Point and payload projection

The point contains:

- stable variant ID;
- named vector(s);
- all payload fields required by candidate hard constraints;
- payload schema/version;
- generation identity, including source, projected-document, contract, projection, compiler/format and embedding identities where practical.

### 12.4 Payload indexes

The collection compiler declares and validates payload indexes for all pushed-down filter fields.

A CandidatePlan cannot be activated if a required payload field or index is missing.

### 12.5 Candidate compilation

Compile:

- semantic query vector;
- terms and numeric constraints;
- interval-overlap price constraints;
- geo radius filters when explicitly requested;
- topK, threshold and oversampling;
- payload return policy.

Qdrant does not receive facets, groups or public page requests.

### 12.6 Candidate decoding (Brick 6A) and hydration (Brick 6C)

Brick 6A decodes the Qdrant response to the candidate-only
`QdrantCandidateSearchResult[Id]`, retaining ordered typed hits and duplicate/elapsed diagnostics. The
6B service wraps that decoder result in a private-constructor
`QdrantAuthorizedCandidateResult[Id]` carrying the exact authorized physical collection and persisted
`QdrantGenerationMetadata`; callers cannot supply either value.

Brick 6C exposes a synchronous typed `QdrantQueryEmbeddingPort` and one executable
`QdrantCandidatePipeline`: `prepare -> embed -> complete -> authorized service`. Its
`ExecutedQdrantCandidatePlan` binds the original `CandidatePlan`, declaration, request, authorized target,
metadata and diagnostics before hydration. The generic core then checks source/projected/projection
fingerprints and point count, performs one ordered identity lookup, preserves candidate order/scores and
attaches domain provenance. Missing IDs and duplicate document identities are typed failures; no candidate
is silently dropped. The lookup derives identities through the declaration's `identityOf(document)` view;
BeautyQ does not supply a parallel identity selector or map.

The initial generic hydration vocabulary is deliberately closed to `Fail` for missing documents and
`RequireAll` for hard constraints. `PlannedConstraintAssertion` replays the exact bound hard constraints
for terms, numeric/temporal ranges, interval overlap and geo distance in declared constraint order. This
is an integrity assertion after backend filtering, not a second capability validator or a replacement for
Qdrant filter compilation.

BeautyQ supplies only `BeautyQQdrantHydrationPolicy` (`semantic-supplement` provenance) and composes the
policy/evaluation with the generic pipeline. The final result remains candidate-only: it has ordered
hydrated documents, scores, provenance, authorized target, persisted metadata and Qdrant diagnostics, but
no totals, facets, groups or public pagination. The implemented composition in section 13 appends these
candidates without transferring full-result ownership away from Elasticsearch.

### 12.7 Transport and physical collection lifecycle (Brick 6B)

Brick 6B-A implements the neutral Gen2 JSON transport and the Qdrant wire adapter. The adapter owns its
exact 1.18.3 paths and bodies:
collection details and aliases, collection creation, waited payload-index creation, waited point upsert,
exact count, atomic alias updates and `/points/query`. The optional `api-key` is transport configuration,
never domain policy or a rendered diagnostic value. The Elasticsearch client is a thin compatibility
adapter over the same transport; `search-gen2-core` remains HTTP-free.

Brick 6B-B implements the following lifecycle and execution contract:

The lifecycle consumes only a compiler-owned `QdrantCompiledGeneration`. A deterministic physical
collection is convergently created or completed, then accepted only when named-vector configuration,
persisted `search_gen2` metadata, payload schema and exact point count match the compiled artifact. The
configured alias changes atomically only after those checks pass. Existing incompatible state is a typed
failure and is never mutated into apparent compatibility.

Candidate execution accepts only a compiler-owned request whose candidate fingerprint matches the
configured policy. It resolves the configured alias to one physical collection, validates that
collection's persisted generation metadata, named-vector configuration and payload schema against the
policy, binds the trusted target internally and passes the raw `/points/query` response to the 6A decoder.
Callers cannot supply a physical target.

The initial lifecycle performs no automatic deletion of successfully built physical collections and
exposes no retention policy. Qdrant aliases switch atomically, but collection deletion has no conditional
alias guard equivalent to the accepted Elasticsearch cleanup protocol; deletion based on a prior alias
read would reintroduce a destructive TOCTOU race. Partial compatible builds are completed by retry, and
failed/incompatible state remains observable for an operator rather than being guessed safe to delete.

## 13. Baseline-plus-supplement orchestration

The implemented BeautyQ boundary is compiler-owned and receives no raw query,
target, membership JSON or independently supplied candidate result:

```text
BeautyQSearchOrchestrator.execute(
  baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
  evaluation: CompiledCandidateEvaluation,
  materialized: MaterializedBeautyQVariantDocuments,
  embeddingPort: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
  qdrantService: QdrantCandidateService,
  baselineService: ElasticsearchBaselineService,
): Either[BeautyQSearchOrchestrationError, BeautyQSearchOrchestrator.Result]
```

`baseline` and `evaluation` must carry the same compiler-owned bound-plan
reference. The orchestrator invokes candidate execution, verifies generation
consistency, asks the bound baseline service for complete-set membership, and
then applies the generic append-only selection. `Result` and its
`Ineligible`/`Evaluated`/`Failed` outcomes are final owner-private classes;
baseline and evaluation are stored once, and status/reason/cause are derived
views rather than independently supplied fields. The eval module derives its
`SupplementEvidence` only from this result.

Current supplement policy:

- first page only;
- default relevance sort only;
- append-only maximum one;
- no baseline reorder or removal;
- all hard constraints satisfied;
- baseline-set membership guard prevents later-page duplicates;
- ES retains ownership of total, facets and groups;
- supplement count/provenance is separate.

Current failure behavior is:

- Elasticsearch baseline availability is mandatory. Startup/readiness fails when the ES baseline, Gen2 document lookup or required baseline index is unavailable.
- Qdrant unavailability does not prevent baseline serving. Current startup may automatically publish
  typed `baseline_only` readiness with `supplementReady = false`; no operator opt-in owns that choice yet.
- the full-search/cutover readiness gate still fails while the configured supplement backend is unavailable;
- a request-time Qdrant timeout, transport error or backend error returns the successful Elasticsearch baseline unchanged and sets supplement status to `supplement_failed` with a stable reason code and diagnostics;
- the response does not silently claim a successful hybrid execution;
- plan compilation errors, unsupported capabilities and hydration invariant violations are not graceful Qdrant failures: they fail validation/request handling or mark the composition unhealthy according to their scope.

No automatic fallback candidate backend or hidden retry path is introduced.

This unconditional startup degradation is a source-confirmed post-cutover gap, not the final
operational contract. The approved correction introduces closed
`SupplementStartupPolicy.Required|Preferred|Disabled` configuration and separate
`ServingMode.FullSearch|BaselineOnly`. `Required` is the default, `Preferred` permits degraded
baseline-only startup, and `Disabled` is the intentional operator kill switch. A permitted
baseline-only process remains Kubernetes-ready because it serves the complete canonical baseline.
The disabled app-shell branch must reach baseline readiness without constructing, probing or
activating Qdrant/embedding resources; ES, snapshot/materialization and baseline integrity remain
required.
SupplementStartupPolicy remains BeautyQ wiring-owned. BeautyQSupplementStartup is an app-shell
Distage activation adapter. The activation choice is applied before planning; Disabled is selected
before provisioning and its retained managed graph does not contain Qdrant or embedding resources.
Immutable startup status owns the operator-facing status/event and persistent public warning;
request-time supplement outcome independently owns transient failure warnings and counters. Neither
owner mutates the other, and no mode promotes itself without restart. Exact judgment semantics,
partial-activation runbook and delivery order belong to the post-cutover plan.

## 14. Native Gen2 application composition

Gen2 is the default production composition:

- `POST /beauty-search` is the sole search route;
- separate ES index alias and Qdrant collection names;
- separate complete generation identities and physical resource namespaces;
- no shadow decoder or adapter inside any Gen1 backend;
- no production request fan-out from Gen1 to Gen2 (Gen1 is absent).

Comparison occurs in `beautyq-search-gen2-eval` or integration tests.

The application follows the same canonical path as a domain reviewer. It compiles validated input,
intent, plan and candidate evaluation once; obtains the lifecycle-bound baseline and complete-set
membership through the BeautyQ Elasticsearch service; then invokes the existing orchestrator. The
application must not reconstruct generic lifecycle/service internals or choose a physical target.
`BeautyQSearchGen2` exposes that application owner by direct reference and contains no parallel facade or
placeholder branch.

The current implementation exposes that owner as `BeautyQSearchGen2.application`. The app shell's
`BeautySearchGen2PluginModules` is the default native Gen2 composition, included by `LeaderboardPlugin`
for `Repo.Prod`: `appShellConfigModule` reads
`beautyq-gen2-app-shell` for connect/request timeouts and Elasticsearch bulk limits; `appShellGraph`
binds `java.time.Clock.systemUTC()`, the `BeautyQSearchSnapshotSource.Postgres[IO]`, the `BeautyQVariantMaterializer.FromSnapshotSource[IO]`,
the per-edge `ElasticsearchGen2JsonClient`, `QdrantGen2Client`, `BeautyQGen2EmbeddingClient` (all built
directly from their typed endpoint and timeout values to avoid raw unnamed HTTP-client double bindings),
the `BeautyQSearchGen2Bootstrap`, one `BeautyQSearchGen2Startup` Distage resource that owns the
`materialize -> activate ES -> activate Qdrant -> publish trusted activation` sequence, and
`BeautyQSearchApplication`/`BeautyQSearchGen2Runtime` derived from that same activation; `routeComposition`
binds the existing `BeautySearchGen2TapirEndpoints`, `BeautySearchGen2Api`, and the
`BeautyQSearchGen2HttpService` adapter. `POST /beauty-search` is the native Gen2 route; `/beauty-search-gen2`
is absent. The endpoint decodes `BeautySearchRequestGen2` directly and
projects one `BeautyQSearchResponseGen2`; no V1 request/response adapter or backend fan-out is
involved.

The public response projector accepts one `BeautyQSearchOrchestrator.Result`. Applied-filter field,
canonical constraint and provenance; suppressed-filter field, constraint, provenance and reason;
baseline totals/relation/facets/groups/cursor, carousel projection, supplement status/reason and per-hit origin
derive through that aggregate. No constructor accepts independently replaceable plan, baseline or
supplement values. Existing BeautyQ pipeline/projection aggregates must have owner-private construction
before the application treats them as trusted results.

`BeautyQSearchResponseGen2Projector` is the sole construction owner of the public response and nested
DTOs. Their public types are read-only aliases to final projector-owned classes: HTTP encoders can read
fields, but external wiring cannot call `apply`, `copy`, a public constructor or subclass them.
The projector already owns total relation and complete applied/suppressed filter evidence. The
`BeautySearchGen2Json` encoder exposes every projector-owned field, including `totalRelation`,
applied-filter `fieldId`/`constraint`/`provenance`, suppressed-filter `fieldId`/`constraint`/`provenance`/`reason`,
baseline `hits` in exact projector order, `facets`, `groups`, the `nextCursor` opaque value, supplement
`status`/`statusCode`/`count`, `ineligibilityReason`/`degradationReason` views, and the `diagnostics`
shard/timed-out evidence; the encoder reads the projector DTO and never reconstructs constraints,
provenance, suppression reasons or backend mechanics in the HTTP layer.

Evaluation remains a one-way derived consumer of the orchestrator result. Tautological report views such
as supplement count and status code derive from appended IDs and typed status rather than being stored as
independently replaceable constructor values.

Readiness is the existing `BeautyQSupplementReadinessPolicy.Result`, not another Boolean/configuration
model. The initial runtime resolves persisted lifecycle state for each request. It has no process-local
active-generation cache invalidated only by local activation, because another process can switch the
alias; any later optimization requires an explicit, tested cross-process coherence contract.

The same application instance therefore re-authorizes the current physical generation on every
request and observes an alias switch made by another activation; request-time execution does not
reuse a previously bound target.

Generation activation is owned by `BeautyQSearchGenerationApplication`: materialization is completed
before Elasticsearch activation, Qdrant failures that are transport/unavailable remain explicit
baseline-only readiness, and invalid embedding results or incompatible generation state remain hard
activation failures. No partially activated Qdrant generation is exposed.

At request time, `BaselineOnly` readiness executes the validated baseline path and skips supplement
mechanics; `FullSearch` executes the complete orchestrator. Both modes return the same projector-owned
response contract, and degradable supplement failures remain successful baseline-preserving responses.

The pre-cutover evaluation gate consumes the explicit typed
`BeautyQCutoverQueryFixture.required` vector, in this order:
`q_broad_006_ready_append_probe` (“beauty near Wandsbek Markt”),
`manicure_real_route_probe` (“маникюр”), `q_broad_001_widened_probe`
(“салон красоты wandsbek ногти”), and `q_broad_003_widened_probe`
(“что-то для лица рядом”). The fixture binds each stable ID to its query;
the runner executes that fixture query and the observation derives its ID and
append budget from the same fixture. The fixture also derives the canonical
native Gen2 request — its own query, no filters, no facets, no sort, its
fixture-owned optional user location, first page with no cursor and the
canonical page size — so the
runner never repeats query strings, page sizes or filter sets. The page size
is validated once in the fixture companion. The budget itself derives from
`BeautyQSupplementPolicy.appendOnly`, not a second evaluation literal. The gate requires a
`BeautyQSupplementReadinessPolicy.Result` together with the observations;
the readiness-aware check (`full-search-readiness`) is part of the same
machine-readable check vector and only passes for
`Serving(FullSearch)`. `BaselineOnly` fails with the observed mode;
`NotServing` fails with the stable unavailable dependency IDs. The gate
derives `testedQueries`, `improvedQueries`, `unchangedQueries`,
`worsenedQueries`, `totalSupplementOnlyAppends`, `duplicateBaselineIds`,
`lostBaselineIds`, `prefixOrderRegressions`,
`baselineOwnedComponentChanges` and `appendBudgetViolations`. The gate
cannot pass without at least one improvement, zero worsening, zero baseline
loss/order/component changes, zero duplicates, zero append-budget violations
and `FullSearch` readiness. Each observation is produced from one
compiler-owned application result and its projector-owned response: the
evidence reads returned hit order and supplement provenance, then compares
the actual response's baseline-owned facets, groups, filters, totals, cursor
and diagnostics with that result's baseline-only projection. It does not
reconstruct result IDs or accept an independently supplied fingerprint. Raw
baseline/result/evidence fields are not a public construction path; the
historical `derive(result)` shortcut is removed. The only accepted evidence
construction path is `BeautyQNoHarmSupplementEvidence.fromExecution`.
The Distage-managed ES/Qdrant communication proof covers those two backend wire contracts and passes;
it is not cutover approval. Its deterministic embedding is a contract fixture, not an
embedding-service communication claim. Brick 8A binds the Gen2 embedding adapter from the existing
`LlamaCppEmbeddingClientConfig` (base URL + `/v1/embeddings` endpoint path) and proves the real
endpoint: a managed scenario calls the production `BeautyQGen2EmbeddingClient`, validates the
configured model identity, asserts the returned vector's exact dimension against
`BeautyQQdrantPolicy.policy.embeddingModel`, runs the resulting vector through the real Qdrant
candidate path, executes the `BeautyQSearchGen2Runtime`, projects the native Gen2 response, and
exercises `POST /beauty-search` through the real route adapter. The same managed communication
proof also exercises the Gen2 `/points/query` wire path against the single
Distage-managed Qdrant 1.18.3 process. An unreachable llama.cpp endpoint is a precise
`VERIFICATION BLOCKED`; a reachable endpoint that returns malformed JSON, the wrong model, an empty
vector or the wrong dimension is red. The Gen2 embedding response model is bound to the
requested model identity: a missing, non-string or mismatched `model` field is a typed
`InvalidResult(QdrantEmbeddingError.ModelMismatch)` and never degrades to `Unavailable`/`Timeout`.
The four typed cutover fixtures executed through the Brick 8A native
application graph. FullSearch readiness was established, the no-harm gate
passed, and the deterministic cutover and Gen1 deletion-inventory reports were
written under `target/search-gen2/`. The runner does not invoke the HTTP route, does not
synthesise IDs, does not rebuild result IDs, does not execute a second baseline search for comparison,
and reuses the existing test-owned Distage managed-resource support. Distage injects the managed
Elasticsearch endpoint and the single Distage-managed Qdrant 1.18.3 endpoint (canonical view
`QdrantGen2PortCfg`). The managed communication proof exercises the Gen2 `/points/query` wire path
against that single endpoint. The resource gate reads the exact reserved
Qdrant alias before mutation and fails closed if it is occupied, regardless of its target. Alias and
collection inventory read/decode failures are red failures. Pure preflight reports every
reachable-but-broken resource before any genuinely unavailable external dependency can block
communication verification.

## 15. Quality and evaluation

Q1 evaluation kernel and canonical BeautyQ corpus are implemented.

`beautyq-search-gen2-eval` owns the four-query readiness-aware cutover gate, append-only no-harm
evidence, deterministic cutover report encoding, the completed Gen1 deletion inventory, a canonical
89-case BeautyQ evaluation corpus, domain evaluation policy and adapters. All 89 migrated cases are
visible regression/partial judgments; no protected holdout exists yet. No complete real-resource corpus
report exists yet.

The Distage communication owner executes those four probes through the native application and real
Elasticsearch, Qdrant and embedding paths.

The domain-neutral `search-gen2-eval` project supplies stable evaluation identities, ranked metric
mathematics (with declared-cutoff denominators), ordered scope/cutoff aggregation, comparison and
deterministic report/manifest codecs. Domain corpus schemas,
request construction, labels, slices, thresholds, and gates remain in each domain's eval project.
Serving modules continue to have no eval dependency.

Q2 remains responsible for execution, protected cases, accepted manifest and measured report. The
Q1 codec accepts only the canonical typed corpus shape (`notes` and judgment vectors are arrays),
fingerprints the decoded typed corpus through its canonical encoder, and preserves declared aggregate
observation order rather than sorting map keys. Protected encoding retains aggregate/slice evidence
without per-case or result identities.
Full reports remain generated artifacts.

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

### 16.1 Cutover complete

- Gen1 runtime source was not modified to consume Gen2;
- Gen2 uses separate resources/namespaces;
- no V1-to-V2 production adapter was created;
- V1 fixtures were read only from eval/test code;
- every Gen2 brick was independently testable.

Cutover passed: 380/380 across 55 suites.

### 16.2 One cutover change set

The final cutover change set:

- `/beauty-search` owns native Gen2 request/response; `/beauty-search-gen2` is absent;
- Gen1 search modules and compatibility DTOs are absent;
- internal Gen2 type/module/package names remain intentionally stable;
- deleted-generation cursors return HTTP 409 `stale_search_cursor`;
- the client must restart pagination without the cursor;
- one managed Qdrant 1.18.3 remains;
- production serving has no eval dependency;
- rollback is a revert of the complete Brick 9 change set.

No application-level rate limiter is introduced as a Gen2 completion artifact: the repository has no
accepted caller-identity or quota policy. Deployment ingress owns that operational concern.

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
- Success@K/MRR over declared acceptable evidence; Recall@K only for exhaustive acceptable sets;
- Precision@K only for exhaustive judgments, or explicitly named judged precision plus unjudged rate
  for a partial pool;
- NDCG@K only for graded exhaustive judgments with a defined ideal ranking; a closed partial pool may
  report only `PooledNDCG@K` with pool fingerprint and coverage;
- zero-result and duplicate rates; duplicate public IDs fail structurally, while first-occurrence
  relevance values remain diagnostic only;
- no-harm supplement gate;

Initial report-only operational evidence:

- application-execution p50/p95 latency under the fixed warmup/sample/concurrency profile;
- backend timing only where existing typed observations provide it;
- snapshot capture, activation duration/time and active generation age under restart-only freshness.

Latency is not a release gate until the measurement environment/protocol and domain-owned thresholds
are separately approved. Cross-environment latency comparisons are initially forbidden.

## 18. Definition of done

Gen2 is complete when:

1. all runtime code lives in the new Gen2 DAG;
2. no Gen2 module depends on a Gen1 search module;
3. `BeautyQSearchGen2` is the single navigation facade over the executable owners, while each declaration
   remains owned by its module-safe canonical object;
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

## Delivery status and operational closure

BeautyQ Search Gen2 is the complete native BeautyQ serving stack. Its accepted declaration,
baseline/supplement, lifecycle-authorization and route architecture is frozen.

`POST /beauty-search` is the sole BeautyQ search route. The old `/beauty-search-gen2` route is absent.
Gen1 serving/runtime owners are removed.

Elasticsearch owns the complete baseline result. Qdrant remains candidate-only and can only append
through the no-harm policy. Evaluation code is outside production serving.

Application composition requires `BeautyQSeedReady` before `BeautyQSearchGen2Startup` begins snapshot
materialization and backend activation.

In managed/test composition, `BeautyQSeedReady.LoadAndInsert` waits for the repository lifecycle and
completes seed insertion first.

In provided composition, `BeautyQSeedReady.Noop` preserves externally owned database readiness while
keeping the startup dependency explicit.

The core architecture is frozen. The source-confirmed evaluation ownership mismatch, explicit
supplement-startup policy, public request budgets, bounded Qdrant batching, restart-only operational
contract and eval-first second-domain path are approved bounded work in the post-cutover plan.
Anything beyond that plan still requires a product requirement or a source-confirmed defect.

### Verification ownership

This technical specification records which proof layer owns each architectural claim; it does not own step-by-step test recipes. Pure policy, Distage graph, HTTP contract, and real-resource communication are separate proof layers. `BeautyQGen2CutoverGateSpec` is the pure gate owner; `BeautyQSearchGen2CutoverCommunicationSpec` is the real-resource communication owner. Operational commands and practical proof-selection guidance belong to [`NEW_DOMAIN_ONBOARDING.md`](../search/NEW_DOMAIN_ONBOARDING.md).

Before the later startup-readiness ordering dofix, the recorded clean root `sbt test` completed
successfully. Across the 13 aggregated project test tasks:
- 1727 tests passed;
- 176 suites completed;
- 0 failed, canceled, ignored, or pending.

The final `380 tests / 55 suites` block belongs only to `leaderboard-app-shell`; it is not the
repository-wide total. The repository-wide total was obtained by adding the separate subproject
summaries from the same clean root run.

A new repository-wide count must be recorded only after the full root suite is rerun on the post-dofix
tree; this document does not infer that count.

### Accepted limits

These are accepted limits, not open defects or roadmap commitments:

- no second production search domain yet;
- no CDC materialization;
- no authenticated cursor state;
- no arbitrary custom-analyzer framework;
- no universal supplement acceptance threshold;
- generic multi-valued field support remains limited to documented shapes;
- no application rate limiter without a product quota policy;
- no process-local active-generation cache without a coherence contract.
