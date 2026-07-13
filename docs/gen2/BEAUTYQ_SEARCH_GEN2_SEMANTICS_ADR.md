# ADR: BeautyQ Search Gen2 semantic baseline

Status: **accepted for the side-by-side Gen2 build**
Decision scope: Gen2 contracts, compilers, tests and final cutover
Supersedes: the proposed/undecided version of this ADR

## Context

Gen1 contains several useful but inconsistent interpretations of the same user intent. In particular, price, range boundaries and geo activation differ between Elasticsearch, in-memory response assembly and the Qdrant supplement path.

Gen2 is not required to preserve those inconsistencies. It is built in parallel and receives one accepted semantic contract before backend code is written.

V1 fixtures remain evidence of historical behavior, not a normative Gen2 oracle.

## Decisions

### 1. Stable business identity

Search intent declarations use explicit stable business codes rather than display names or UUID literals.

Add domain types equivalent to:

```scala
opaque type ServiceCode = String
opaque type CategoryCode = String
```

Requirements:

- codes are unique, immutable and human-readable;
- codes are persisted in SQL and present in seed data;
- intent aliases reference codes;
- catalog snapshot validation rejects duplicate or unknown codes;
- projection writes both stable code and internal ID into the search document;
- display names remain localizable presentation data and may change without changing intent declarations.

Initial scope requires codes for `Service` and `Category`. Other entities receive codes only when they become public or vocabulary identities.

Decision: codes are added to the shared `Service`/`Category` domain and persistence models rather than a Gen2-only sidecar identity model. This is an accepted additive shared domain/API change before cutover; derived Service/Category JSON may gain code fields. Gen1 search runtime behavior and ownership remain unchanged, but shared BeautyQ API schemas are not promised to remain byte-for-byte identical.

### 2. Price interval semantics

A BeautyQ offer exposes an available price interval:

```text
offer = [priceFrom, priceTo]
```

A price filter matches when the offer interval overlaps the requested interval according to the request bounds.

For a half-open request bucket `[min, max)` the canonical predicate is:

```text
priceFrom < max AND priceTo >= min
```

For an inclusive upper bound `[min, max]` the first predicate becomes:

```text
priceFrom <= max
```

Unbounded sides omit the corresponding predicate.

Therefore price cannot be modeled as a range over `priceFrom` alone.

Gen2 represents it explicitly:

```scala
IntervalOverlap(
  fromField = Fields.priceFrom,
  toField = Fields.priceTo,
  bounds = ...,
)
```

### 3. Numeric bounds

All numeric constraints use explicit bounds:

```scala
sealed trait Bound[+A]
object Bound {
  case object Unbounded
  final case class Inclusive[A](value: A)
  final case class Exclusive[A](value: A)
}

final case class RangeBounds[A](lower: Bound[A], upper: Bound[A])
```

Adjacent facet buckets are lower-inclusive and upper-exclusive:

```text
[min, max)
```

The last bucket may be upper-unbounded.

Request operators preserve their exact meaning: `gt`, `gte`, `lt`, `lte`, `between` with explicit bound flags.

### 4. Price facets

Price facets use a dedicated semantic mode:

```scala
IntervalOverlapFacet(
  id,
  fromField = Fields.priceFrom,
  toField = Fields.priceTo,
  buckets,
)
```

They are not compiled as a normal range aggregation over `priceFrom`.

Elasticsearch compiles each bucket to a filters aggregation using the canonical overlap predicate. Qdrant compiles the same predicate into two payload conditions when price filtering is requested.

### 5. Facet counting policy

Initial Gen2 behavior counts facets under all currently applied hard filters, including a filter originating from the same facet.

Self-excluding/disjunctive facets are a separate future policy and must be explicitly represented if added.

Every facet result declares its counting policy and precision.

### 6. Geo semantics

Geo behavior is represented by three different plan elements:

```text
GeoProximitySignal  // changes relevance score
GeoDistanceFilter   // rejects documents outside a radius
GeoDistanceSort     // orders by distance
```

They are never collapsed into one optional-radius constraint.

Rules:

- coordinates alone do not activate geo behavior;
- parsed `NearUser` produces `GeoProximitySignal` only;
- an explicit radius filter produces `GeoDistanceFilter`;
- an explicit distance sort produces `GeoDistanceSort`;
- all three require user coordinates and fail validation when coordinates are absent;
- a hard radius is never inferred from `NearUser` without an explicit product decision.

### 7. Constraint precedence and provenance

Precedence is:

```text
explicit UI filter
> parsed hard constraint
> parsed soft signal
> residual text
```

All applied or suppressed constraints retain provenance:

```text
ExplicitUi
FacetSelection
ParsedHard
ParsedSoft
SystemDefault
```

Rules:

- conflicting explicit UI filters are validation errors;
- a parsed hard constraint conflicting with an explicit UI filter is suppressed and recorded in diagnostics;
- equivalent constraints are normalized and deduplicated;
- soft signals never override hard filters.

### 8. Public filter model and provenance trust boundary

A selected facet is a filter with server-assigned `FacetSelection` provenance. It is not a separate competing request subsystem.

The external request contains one filter collection and cannot claim parser/system provenance:

```scala
final case class PublicFilterInput(
  field: PublicFieldName,
  operator: PublicOperator,
  value: PublicFilterValue,
  presentationId: Option[FacetSelectionId],
)
```

The public schema decodes the complete input, including operator:

```scala
decode: PublicFilterInput => Either[FilterError, Constraint]
```

The server maps a validated `presentationId` to `FacetSelection`; absence of it maps to `ExplicitUi`. Internally the plan uses:

```scala
final case class SourcedConstraint[Constraint](
  constraint: Constraint,
  provenance: ConstraintProvenance,
)
```

Only server code may assign `ParsedHard`, `ParsedSoft` or `SystemDefault`.

### 9. Empty query

An empty residual text is valid when hard filters, sort or browse policy is present.

An entirely empty request uses an explicit default browse plan. It must not rely on accidental backend behavior such as an unqualified `bool {}` query.

### 10. Totals

Elasticsearch baseline totals are exact in the initial BeautyQ profile.

The baseline result exposes:

```text
baselineTotal
relation = Exact
```

A semantic supplement does not rewrite baseline facet/group totals. The final response exposes supplement count separately rather than pretending that ES aggregations include supplemented documents.

### 11. Pagination

The public request has one pagination source of truth:

```scala
final case class PageRequest(
  cursor: Option[SearchCursor],
  size: PageSize,
)
```

There is no separate top-level `limit`.

Initial baseline policy:

- Elasticsearch uses stable cursor pagination;
- cursor state is tied to contract fingerprint, a value-only `PlanIdentity` and sort;
- `PlanIdentity` is built from a `CanonicalPlanView` with the current cursor removed;
- it contains normalized query text, stable field IDs, typed constraints/signals, sort, facet/group requests, page size and all geo values that affect execution;
- it excludes diagnostics, timings, extractors/codecs, backend handles and all function-valued/runtime members;
- `SearchField` canonical identity is `FieldId`, not function/case-class equality;
- changing filters, query, sort, page size, facet/group requests or contract invalidates the cursor;
- offset pagination is not the canonical API;
- deterministic tie-breakers are mandatory.

The cursor envelope stores the `PlanIdentity` hash and backend pagination values. The current cursor is never part of the identity it validates, avoiding recursive encoding.

### 12. Semantic query text policy

Qdrant embedding input is not the raw query and is not chosen ad hoc by the backend compiler.

The accepted BeautyQ policy builds deterministic semantic text from:

```text
normalized residual text
+ canonical labels for parser-recognized semantic clauses
```

Canonical labels are human-readable declaration values keyed by stable service/category/attribute identities. Explicit UI filters, facet selections, numeric/geo constraints, sort and system defaults do not contribute embedding text.

When both residual text and canonical semantic labels are empty, semantic supplementation is ineligible with `NoSemanticQueryText`. Filter-only and default-browse requests therefore continue through the Elasticsearch baseline without constructing an invalid empty `CandidatePlan`.

### 13. Qdrant supplement eligibility

Initial supplement policy is intentionally narrow:

```text
page: first page only
sort: default/relevance only
max appended candidates: 1
position: append-only after ES baseline hits
reorder: forbidden
baseline removal: forbidden
facets/groups ownership: Elasticsearch only
```

The supplement is disabled for price, duration, distance or any other explicit non-relevance sort.

A candidate must:

- satisfy all hard constraints through pushed-down payload filters;
- pass post-hydration validation as a defensive assertion;
- not duplicate a hit on the current page;
- not belong to the complete Elasticsearch baseline match set.

The last rule is checked by a baseline membership query or equivalent backend proof. It prevents a candidate appended on page one from appearing again on a later ES page.

Failure policy is fixed:

- Elasticsearch baseline and Gen2 document lookup are mandatory for serving readiness;
- Qdrant startup/readiness failure leaves the service explicitly `baseline_only` with `supplementReady = false`, while the full-search/cutover readiness gate fails;
- a request-time Qdrant timeout/transport/backend failure returns the unchanged ES baseline with supplement status `supplement_failed` and a stable reason code;
- this is explicit degradation, not silent success;
- plan/capability errors and hydration invariant violations are not degraded into baseline-only success.

### 14. Backend result roles

Elasticsearch and Qdrant do not share one lowest-common-denominator output type.

Elasticsearch returns:

```scala
FullSearchResult[Document, Id](
  hits,
  total,
  facets,
  groups,
  page,
  diagnostics,
)
```

Qdrant returns:

```scala
CandidateSearchResult[Id](
  candidates,
  diagnostics,
)
```

Qdrant receives a compiled `CandidatePlan`, not facet/group/page requests that it does not own.

### 15. Group and carousel semantics

Provider and service carousels are explicit group projections, not plain `terms` buckets.

Each group request declares:

- key field;
- representative-document projection;
- required metrics;
- bucket size;
- ordering tuple;
- precision policy.

Initial BeautyQ ordering is deterministic:

```text
bestScore descending
matchingVariantCount descending
stable group key ascending
```

When geo proximity is active, a separately declared proximity metric may participate in the ordering tuple. It is not implicit.

The Elasticsearch compiler must request representative data (`top_hits`, `top_metrics` or another explicit mechanism) and every metric used for ordering. A plain `terms` aggregation is insufficient.

### 16. Snapshot consistency and identity

A versioned snapshot is loaded under one consistent repository read boundary.

Initial SQL implementation uses a single read-only transaction with repeatable-read semantics or a stronger equivalent supported by the repository layer.

Snapshot metadata separates:

```text
contentFingerprint  // hash of canonical snapshot content
sourceRevision      // DB revision/LSN when available
capturedAt          // observation timestamp
```

`capturedAt` is excluded from `contentFingerprint`.

A repeated load of unchanged data must produce the same content fingerprint.

### 17. Catalog and seed insertion order

The catalog declaration remains a read topology, not a complete SQL dependency graph.

It may validate parent-before-child relationships that it actually declares, but it must not be used to infer missing relational dependencies.

If seed insertion is automated, ordering comes from an explicit persistence write dependency plan owned by the repository/seed layer. Gen2 may cross-check that plan against catalog edges and SQL/repository evidence.

### 18. Quality ownership

The executable serving root stores quality references and policies only:

```text
corpusId
metricSet
gateThresholds
gatePolicy
```

The corpus, reports and evaluation runners live in `beautyq-search-gen2-eval`. Serving modules must not depend on that module.

## Semantic delta ledger

Gen2 intentionally preserves:

- document IDs and join invariants;
- explicit document projection;
- established useful intent aliases;
- Elasticsearch ownership of the full baseline result;
- append-only, no-harm semantic supplementation.

Gen2 intentionally changes:

- shared Service/Category domain/API schemas add stable code fields before search cutover;
- display-name vocabulary identity to stable codes;
- price filtering from `priceFrom` range to interval overlap;
- facet buckets to explicit half-open boundaries;
- coordinate-driven implicit geo scoring to explicit geo plan elements;
- request shape to typed filters, sort and cursor pagination;
- total/facet/group ownership to decoded Elasticsearch results;
- Qdrant filtering from post-hydration primary filtering to payload-filter pushdown;
- backend result contracts from one generic result to role-specific results.

## Consequences

- V1 result parity is not a release criterion where the delta ledger declares an intentional change.
- Cross-backend semantic fixtures are the normative oracle.
- Backend compilers must fail on unsupported plan elements rather than silently degrade.
- The side-by-side Gen2 build can evolve without compatibility adapters inside V1.
