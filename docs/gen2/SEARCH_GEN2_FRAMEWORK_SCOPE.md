# Search Gen2 reusable-framework scope

Status: **living document; update in the same commit that changes kernel vocabulary or shape support**
Scope: `search-gen2-contract` and `search-gen2-core` only - the two generic (domain-free) Gen2 modules
Owner: reusable-framework generality; new-domain business authoring stays owned by
`docs/search/NEW_DOMAIN_ONBOARDING.md`

## Why this document exists

Commits `b0466597`..`cbe041b9` built the reusable declaration and materialization kernel by extracting
it from exactly one domain (BeautyQ). An extraction from a single consumer necessarily takes the shape
of that consumer. This document is the deliberate executable-calibration checkpoint: it records what the
kernel's vocabulary and shape assumptions actually are, what an executable
[tracer](../../search-gen2-core/src/test/scala/leaderboard/search/gen2/core/tracer/LibraryTracerDomain.scala)
calibrates beyond the BeautyQ extraction, what remains a tracked gap, and what is a deliberate non-goal.

Narrow-by-design is an accepted, correct engineering choice for this kernel (the implementation plan
explicitly rejects "a speculative, oversized generic-algebra diff"). What is not accepted is narrow
disguised as general. Every entry below is either exercised by a structurally different executable
fixture or explicitly marked as not yet proven beyond BeautyQ. The tracer proves technical
expressibility and regression behavior; it is not a second production or product consumer.

## Supported shapes

### Document field values (`search-gen2-contract`)

| Value type | `SearchFieldKind` | Evidence beyond BeautyQ |
|---|---|---|
| `String` (keyword) | `Keyword` | tracer `isbn`-style keyword fields |
| `String` (text) | `Text` | tracer `title`, dynamic `translations` family |
| `Int` | `Integer` | tracer not required (BeautyQ evidence only) |
| `Long` | `Long` | tracer `checkoutCount` - **BeautyQ has no `Long` field; added for this checkpoint** |
| `BigDecimal` | `Decimal` | tracer not required (BeautyQ evidence only) |
| `Boolean` | `Boolean` | tracer not required (BeautyQ evidence only) |
| `java.time.Instant` | `DateTime` | tracer `publishedAt` - **BeautyQ has no date/time field; added for this checkpoint** |
| `GeoPoint` | `GeoPoint` | tracer not required (BeautyQ evidence only) |
| any `UuidBackedId[A]` | `Keyword` (derived) | tracer `BookId`/`BranchId` |
| any `CanonicalStringValue[A]` | `Keyword` (derived) | tracer `Isbn` (hand-rolled instance; see resolved gap G-6) |

Pinned exactly (drift-detector tests, not prose) in
[`SearchGen2VocabularyLedgerSpec`](../../search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2VocabularyLedgerSpec.scala).

The current backend-neutral DateTime capability set is deliberately conservative before the backend
compilers exist: range filtering, range faceting and value sorting are accepted; exact/in filters,
terms facets and grouping are not promised. Brick 5/6 may broaden that matrix only with an explicit
semantic decision and proof from both backend compilers. The tracer demonstrates the currently
accepted shape; it does not establish every operation a future date field could legitimately need.

### Dynamic map families (`search-gen2-contract`)

Supported: `Map[String, Value]` with an ordered `Definition` inventory and a `Definition => String` code
accessor, for any `Value` that has a direct-field kind method. BeautyQ's four families (enum/boolean/
int/decimal attributes) all happen to be keyword or numeric; the tracer's `translations` family proves
the same mechanism works for a **non-attribute semantic** (per-locale searchable titles) and, as of this
checkpoint, for a `Text` kind (`DynamicFieldKindDraft.text`, which did not exist before this checkpoint -
only BeautyQ's four families existed as evidence, and none of them needed `Text`).

Not supported: a dynamic family keyed by anything other than a `String` map key, or valued by anything
other than a direct-field-kind-eligible `Value` (see gap G-3).

### Document product coverage (`search-gen2-contract`)

Supported: any flat product (case class) type, with `completeDocument` requiring every member to be a
declared field, a dynamic-map family, or an explicit `.ignore`. Proven with two structurally different
documents (`VariantSearchDocumentGen2`, 28 members; tracer `BookDocument`, 8 members including one
explicitly ignored member - see gap G-3).

Not supported: nested document types (a field whose own type is itself a searchable sub-document).
`SearchFieldDerivation`'s selector macro already rejects nested selectors at compile time; this is an
intentional non-goal, not a tracked gap (see "Non-goals" below).

### Snapshot source shapes (`search-gen2-core`)

Supported, via `CanonicalSnapshotTuple` given resolution over the snapshot product's `Mirror`:

- `Vector[Entity]` members, any number, any order - the only shape BeautyQ needed;
- `CanonicalSnapshot.Single[Entity]` members (added at this checkpoint) - exactly one source row, e.g. a singleton
  configuration/catalog-version row alongside sibling `Vector` sources. Proven by the tracer's
  `CatalogConfig` source and by `CanonicalSnapshotSpec`'s mixed-shape test.

`CanonicalSnapshot.Single[A]` is a distinct wrapper type, not a type alias for bare `A`, specifically
so given resolution between `Vector[Entity] *: Tail` and
`CanonicalSnapshot.Single[Entity] *: Tail` stays unambiguous even when `Entity` itself happens to be a
`Vector[_]`.

Not supported: any other collection shape (`Option[Entity]`, `Map[K, Entity]`, `Set[Entity]`) as a
snapshot member - no given exists; resolution fails at compile time with a missing-implicit error, not a
silent wrong encoding.

### Canonical row authoring (`search-gen2-core`)

Supported: `canonicalRow(prefix, source).field(_.x)...group(name, items)(...).build`, for any source
type and any field/group shape a domain chooses, using any registered `SearchValueCodec`. Proven with
BeautyQ's seven source types and the tracer's three (including `Long`/`Instant`-valued fields and a
group over a `Map` entry - the same construction BeautyQ's attribute families already used, now also
exercised with different key/value semantics).

### Catalog boundary (outside this tracer)

The tracer covers the complete local path owned by `search-gen2-contract` and `search-gen2-core`:
document declaration, canonical snapshot encoding, projection, materialization and both fingerprints.
It does **not** declare a catalog topology. Catalog relations are a separate neutral `repo-core`
boundary, already proved by its generic contract suites (`CatalogRelationEvidenceDerivationSpec`,
`CatalogLoadedGraphSpec`, `CatalogSnapshotAssemblySpec`, `RepoSnapshotProjectionSpec`,
`GraphLoadingSpec`). Adding `repo-core` to `search-gen2-core` merely to make one test appear
end-to-end would reverse the intended module DAG. Cross-boundary equality should continue to use the
existing "derive or prove equal" pattern in a domain module rather than merge these generic kernels.

## Tracked gaps

Each gap below has a numbered ID so a future tracer/neutral-shape exercise, or a Brick 4 design
review, can reference a gap by ID instead of re-describing it.

`SearchGen2VocabularyLedgerSpec` mechanically pins vocabulary that already has a code surface and the
current multi-value compile gap. It is a drift detector, not a mechanical parser of the semantics ADR:
interval-overlap and the geo signal/filter/sort separation remain normative prose until Brick 4 gives
them typed algebra cases and executable contract tests.

### G-3 - multi-valued (`Vector[A]`) searchable/filterable field

**Status: open, deliberately not resolved at this checkpoint.**

No `SearchFieldKind`, `SearchValueCodec`, or `FieldExtraction` shape exists for "zero or more independent
values of a searchable field" (e.g. tags, multi-category membership). The tracer's `Book.tags`/
`BookDocument.tags` field exists specifically to keep this gap visible: it is explicitly `.ignore`d
(`completeDocument`'s exhaustive-coverage check forces that explicit decision), and
[`SearchGen2VocabularyLedgerSpec`](../../search-gen2-contract/src/test/scala/leaderboard/search/gen2/contract/SearchGen2VocabularyLedgerSpec.scala)
mechanically pins that `.inferred`/`.keyword` on a `Vector[String]` field still fail to compile today.

This is deliberately deferred rather than freehanded: a multi-value field also needs new
`FilterOperator` semantics (e.g. `ContainsAny`/`ContainsAll`) and new `FacetMode` semantics, which are
constraint-algebra decisions that belong with Brick 4's request/intent/plan work, not a standalone
kernel-vocabulary patch. Brick 4 must introduce it from a real BeautyQ requirement and exercise its API
with a different neutral/tracer shape in the same slice; the fixture alone must not invent the feature.

### G-8 - `Fields`/outer-alias class-initialization hazard

**Status: open; avoided by the supported authoring pattern, not prevented by the framework.**

The production root and the tracer both use this shape:

```scala
object SomeDomain {
  object Fields {
    // field declarations
    val document = declarations.completeDocument(id)
  }
  val document = Fields.document   // outer alias
}
```

This is safe only if `Fields` does not close over another value owned by `SomeDomain`. If `Fields`
reads a sibling `private val`, JVM reentrant singleton initialization can let the outer alias observe a
default value when first read from a third object, even though direct `Fields.document` access works.
The tracer keeps its definition inventory inside `Fields`, and `LibraryTracerDomainSpec` proves the
supported outer alias is identical when read externally.

This checkpoint did not add a DSL-level prohibition or a checked-in reproduction of the unsupported
cyclic shape, so it must not claim the hazard is fixed. Until the API prevents the cycle constructively,
every input `Fields` needs must be declared inside `Fields` or imported from a scope entirely outside
the enclosing root object, never from a sibling value of the object that aliases `Fields.document`.

## Resolved at this checkpoint

Recorded here so a future reviewer can see what the tracer exercise actually changed, not only what it
found still missing.

| Gap | Resolution | Evidence |
|---|---|---|
| G-1 - no `Long` value type | Added `SearchFieldKind.Long`, `SearchValueCodec[Long]`, `DefaultSearchFieldKind[Long]`, capability table entries, `.long` on direct and dynamic declaration APIs | tracer `checkoutCount`; `SearchValueCodecSpec`; `SearchFieldDeclarationsSpec` |
| G-2 - no date/time value type | Added `SearchFieldKind.DateTime`, `SearchValueCodec[Instant]` (canonical ISO-8601 UTC), `DefaultSearchFieldKind[Instant]`, capability table entries, `.dateTime` on direct and dynamic declaration APIs | tracer `publishedAt`; `SearchValueCodecSpec`; `SearchFieldDeclarationsSpec` |
| G-4 - snapshot derivation required a product of `Vector`s | Added `CanonicalSnapshot.Single[A]` and its `CanonicalSnapshotTuple` given, unambiguous alongside the existing `Vector[Entity] *: Tail` case | tracer `CatalogConfig`; `CanonicalSnapshotSpec` |
| G-5 - dynamic families assumed to be "attributes" | No code change needed - the existing `dynamicMap` mechanism was already mechanically neutral; the tracer calibrates it with non-attribute `translations` semantics but does not add a new map shape | tracer `translations` family |
| G-7 - dynamic families had no `Text` kind | Added `DynamicFieldKindDraft.text`, mirroring the direct-field `.text` choice | tracer `translations` family (`.text.searchable`); `SearchFieldDeclarationsSpec` |
| G-6 - the one reusable canonical-String-wrapper base (`StableCodeCompanion`) was `private[model]`, unreachable outside `beautyq-model` | Moved the Circe-free base (grammar validation, canonical evidence, unsafe construction, `.value` extension) to `leaderboard-core` and made it public; `beautyq-model` now layers a small `StableCodeCirceSupport` mixin on top for `ServiceCode`/`CategoryCode`'s Circe `Codec`, with zero new `leaderboard-core` dependency and no behavior change. The accepted grammar (lowercase snake_case) stays fixed rather than parameterized - a domain needing a different grammar (the tracer's `Isbn`, digits-and-hyphens) still writes its own `CanonicalStringValue` instance, now visibly following the same established pattern rather than being blocked by visibility | `leaderboard-core`'s `StableCode.scala`; `beautyq-model`'s `StableCode.scala`; existing `StableCodeSpec`/`ServiceCode`/`CategoryCode` suites unchanged |

## Brick 4B - SearchPlan, facet, group and provenance contract

`search-gen2-contract` owns typed facet/group/page/provenance/diagnostic values, `SearchPlan` validation
and a diagnostic generated view (`PlanValue.scala`, `FacetRequest.scala`, `GroupRequest.scala`,
`SearchPlan.scala`, `SearchPlanTrace.scala`), built on Brick 4A's `PlannedConstraint`/`PlannedSignal`/
`PlannedSort` algebra.

`SearchPlan` stores applied sourced filters once (`appliedFilters: Vector[AppliedFilter[Document]]`) and
derives `hardConstraints` from them, so execution constraints and provenance reporting cannot drift into
two independent vectors.

`SearchPlanTrace`'s generated strings are diagnostic only and must never be treated as `PlanIdentity`
input.

Domain request decoding, precedence, default browse behavior and plan compilation remain outside the
generic contract (Brick 4D+).

Proven with two structurally different neutral fixtures (`SearchPlanSpec`'s CatalogDocument and
VenueDocument), each validated end to end and rendered to an exact golden `SearchPlanTrace`. G-3 stays
open: `ContainsAny`/`ContainsAll` are not introduced by this checkpoint either.

## Brick 4C — Plan identity and cursor envelope

`search-gen2-core` owns the value-only projection from a validated `SearchPlan`, the versioned
canonical identity encoding, the SHA-256 `PlanIdentityHash`, and the versioned cursor envelope.

Plan identity includes contract fingerprint, effective query text, hard constraints, soft signals, sort,
facets, groups and page size. It excludes the current cursor, diagnostics, suppressed filters,
provenance and all function/runtime values.

A field is identified by `FieldId`; path/type/mapping changes are carried by the separately supplied
contract fingerprint. The cursor stores only envelope version, plan hash and opaque backend state.
Validation reconstructs identity from `plan.withoutCursor`, preventing recursive cursor identity;
backend-state semantics remain backend-owned.

## Non-goals

Deliberate, not gaps:

- **Nested/sub-document search fields.** A document field is a leaf value, never itself a searchable
  document. Rejected at compile time by the same selector macro that rejects any nested selector.
- **Automatic derivation of capabilities, public names, or projection logic from case-class shape.**
  These stay explicit business authoring per `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`'s
  business-authoring gate; the kernel only derives tautological evidence.
- **A generic constraint/plan algebra before Brick 4 existed.** Brick 4A (`PlannedConstraint`/
  `PlannedSignal`/`PlannedSort`), Brick 4B (`SearchPlan`, facets, groups, provenance) and Brick 4C
  (`PlanIdentity`, canonical encoding and cursor envelope) have since landed in their owning modules.
  Request decoding, precedence, default browse behavior and backend compilation remain Brick 4D+ scope
  and do not belong in this document until they exist.
- **Backend-specific (Elasticsearch/Qdrant) representation concerns in the generic kernel.** Field kinds,
  capabilities and codecs stay backend-neutral by construction; mapping/analyzer/payload policy is each
  backend module's own concern.

## How to extend this document

Update this file in the same commit that:

- adds or removes a `SearchFieldKind`, `FilterOperator`, `FacetMode`, `SortMode`, or `GroupMode` case
  (update the pinned sets in `SearchGen2VocabularyLedgerSpec` in the same commit - the test will fail
  otherwise);
- adds or removes a `SearchValueCodec`/`DefaultSearchFieldKind` instance;
- adds or removes a `CanonicalSnapshotTuple` shape;
- closes a tracked gap (move its row from "Tracked gaps" to "Resolved at this checkpoint" with evidence);
- adds a new tracked gap discovered by a future tracer/neutral-shape exercise.

A production vocabulary feature needs a real domain requirement; a tracer/neutral fixture must then
exercise a structurally different use of its reusable representation. A fixture alone does not justify
the feature, and a BeautyQ-only shape with no calibration or explicit single-consumer status is a review
red flag under `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`'s business-authoring gate.
