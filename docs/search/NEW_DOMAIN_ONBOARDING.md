# New domain onboarding

Owner: new-domain onboarding, including the future-domain supplement-gate checklist. Reusable
generic supplement architecture/policy is owned by `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`.

A concise guide for adding a new domain (a new BeautyQ-style catalog: nominal ids, a repo layer,
a catalog declaration, a full/seed loader, and a projection) after the derivation work recorded in
`docs/search/CATALOG_DECLARATION_DERIVATION_HANDOFF.md`. It distinguishes what is now generic and
reusable as-is from what every domain still writes itself as deliberate business policy.

This is not a claim that a new domain is zero-code. It still needs nominal ids, model case classes,
repository implementations, a pure catalog declaration, a loader, a seed policy, and a projection -
this guide tells you which of those are now thin (a few lines, generically derived) and which are
full business logic you write yourself.

## What is generic now

These surfaces need no new derivation code, no macro, and no per-domain adapter body - you either
extend/call something already in `leaderboard-core`/`repo-core`, or a `given` already resolves
automatically once your types have the right shape:

- `UuidBackedId[A]` (`leaderboard-core`) - the trait every UUID-backed opaque id extends.
- Generic Doobie `Meta[A]` for any `UuidBackedId[A]` (`beautyq-search-repositories`'s
  `leaderboard.repo` package object today; a new domain in its own repo module writes the same
  one-line generic `given` once, keyed off `UuidBackedId[A]`, not per id).
- Generic Tapir `Codec[String, A, CodecFormat.TextPlain]`/`Schema[A]` support through
  `leaderboard.http.tapir.UuidBackedIdTapirSupport` (`app-http`) - import
  `UuidBackedIdTapirSupport.given` in any Tapir endpoint file that captures a `UuidBackedId[A]` id.
- Generic Scalacheck `Arbitrary[A]` for any `UuidBackedId[A]` (one `given` in the test tree, e.g.
  `leaderboard-app-shell/src/test/scala/leaderboard/Rnd.scala`).
- `RepoEntity.derived[A]` - model-derived entity metadata (model name, physical source name, column
  list, conventional id column) for any product type, no raw table/column string.
- `RepoField.derived(_.someField)` - a model-derived, typed field (label + column) from a direct
  selector.
- `RepoValueSource.derived[A, K, Row](_.key)` - model-derived value-source metadata (aggregate model
  name, physical row source, key field) for an aggregate keyed by a field other than a conventional
  id (see the example below).
- Repo operation adapter derivation (`OptionalByKey.derived`, `ValueByKey.derived`, and friends) -
  matches a repositories-bundle method by exact signature, never by name.
- Catalog relation/evidence derivation - `CatalogEntity.derivedFromId`, `CatalogRootTree
  .derivedFromRepositories`, `CatalogRootAll.derivedFromRepositories`, `CatalogMany
  .derivedFromRepositories`, `CatalogValueEdge.derivedFromRepositories` all resolve automatically
  once your repositories bundle has exactly one method of the right shape - no method-name fallback,
  compile-time failure on zero or multiple matches.
- Full loader: `declaration.materialize[F, R](identity).loadAll(repositories).map(_.toSnapshot
  [YourSnapshot])` - generic traversal in declaration order, generic dedup (by `CatalogValue`/
  conventional id) into your snapshot case class via `Mirror.ProductOf`.
- `RepoSnapshotProjection` for indexing snapshot rows and required joins in document projection.
- Seed loader: explicit `GraphLoading.seedRequiredById`/`seedValuesByKey` calls (still one call per
  seed-scoped list - see "What a new domain still declares" below) assembled into your snapshot via
  `LoadedCatalog(...).toRawSnapshot[YourSnapshot]` - same generic `Mirror.ProductOf` assembly as
  `toSnapshot`, but with no dedup, matching seed input's already-distinct assumption.

## What a new domain still declares

These are business-policy surfaces. Nothing above infers, derives, or guesses them for you:

- Nominal ids themselves (which fields are ids, what they're named) - `UuidBackedId[A]` only
  supplies the mechanical `apply`/`fromString`/`.value`/typeclass-registration boilerplate once you
  declare `opaque type FooId = UUID` and `object FooId extends UuidBackedId[FooId]`.
- Domain model case classes and their JSON codecs.
- Repository traits and their SQL/`Dummy`/`Postgres` implementations - what a repository method is
  named, what it queries, what constraints it enforces.
- Each repository companion's `entity`/`itemEntity`-style `val`s (`RepoEntity.derived[A]`) and any
  aggregate `valueSource` `val`s (`RepoValueSource.derived[...]`) - derivation gives you the
  mechanical metadata construction; deciding which type needs one, and what its key/row type is, is
  still yours.
- The pure catalog declaration itself (which roots/edges exist, in what order, keyed by what) - see
  the minimal example below.
- The repositories bundle case class for your domain.
- Your snapshot case class (the `toSnapshot`/`toRawSnapshot` target).
- The full loader call site (one line, but you write it).
- Seed scope (which lists a seed-scoped load needs) and seed loader policy (no-dedup, canonical
  missing-entity message, ordering) - a deliberate, different policy than the full loader's, not
  something to unify without a coordinator decision.
- Projection logic: joins, missing-entity messages, schema/business-invariant validation, text/token
  normalization. Projection owns its own row metadata (built from your repositories' `entity`/
  `valueSource` vals) as local implementation detail - it is not, and should not become, a shared
  catalog-graph facade (see "Nodes/projection boundary cleanup scope" in the handoff doc for why one
  used to exist and was removed).
- API/runtime/search backend policy: route shapes, response shapes, backend routing/activation,
  ranking/fusion.

## Minimal catalog declaration example

A pseudo-example (`CategoryLike`/`ItemLike`/`ItemSchemaLike` stand in for real domain types),
mirroring the real BeautyQ declaration's shape (`category` self-tree -> `service` many-edge ->
`serviceVariantSchema` value-edge on `service`):

```scala
val declaration =
  catalog("myDomain")
    .branch[CategoryLike]
    .rootTree(_.parentId, root = CategoryLike.rootId)   // self-tree root, keyed by parentId
    .child[ItemLike](_.categoryId)                      // CategoryLike -> ItemLike, many-edge
    .branch[ItemLike]                                   // switch focus: edges below are from ItemLike
    .value[ItemSchemaLike](_.itemId)                     // ItemLike -> ItemSchemaLike, value-edge
```

`.child[C](by)` and `.value[V](by)` always attach to the *current* branch - switching which entity
subsequent edges hang off requires an explicit `.branch[NextEntity]` first, exactly like the real
declaration switches from `category` to `service` before declaring the `serviceVariantSchema` value
edge.

Materializing this declaration (`declaration.materialize[F, Repositories[F]](identity)`) needs
`CatalogEntity`/`CatalogRootTree`/`CatalogMany` evidence, all derived automatically from your
repositories bundle by the surfaces listed above - only a genuinely ambiguous edge (two repositories
methods matching the same requested key type) would ever need an explicit `given`, and if your ids
are nominal (`UuidBackedId`-backed, not raw `UUID` aliases), that ambiguity should not arise.

## Value-source example

```scala
val valueSource: RepoValueSource[Aggregate, ParentId, AggregateRow] =
  RepoValueSource.derived[Aggregate, ParentId, AggregateRow](_.parentId)
```

`Row` (`AggregateRow` above) stays an explicit type parameter - it is never inferred or derived from
`Aggregate` - because the aggregate's physical truth and its in-memory shape are allowed to differ.
`ServiceVariantSchema` is the real example: it is keyed by `serviceId` and physically stored as one
row per schema item (`ServiceVariantSchemaItem`), not one row per aggregate. Claiming the aggregate
itself is a `RepoEntity` of its own physical columns would be wrong for exactly this shape; `Row`
lets the value source say "keyed like *this*, but physically sourced from *that*" without guessing.

`CatalogValue` identity evidence (`CatalogValue.from(yourValueSource)`) stays an explicit `given` in
your domain's `Evidence` object, the same way BeautyQ's does - automatic `CatalogValue` discovery
(e.g. searching a repository companion's implicit scope) is deliberately not attempted anywhere in
this codebase: a repository companion is not the aggregate's own companion, so hiding this wiring
behind implicit scope would be clever but harder to debug than one explicit line.

## What not to derive from catalog tree

- Projection joins, text/token normalization, schema/business-invariant validation.
- Runtime routing/backend policy (which search backend serves a query, activation/fallback).
- Response policy (API response shapes, pagination, error envelopes).
- Seed dedup/order policy (the seed loader's no-dedup, order-preserving behavior is a deliberate,
  separate policy from the full loader's dedup - keep them separate, don't unify without a
  coordinator decision).
- Persistence/SQL constraints (foreign keys, uniqueness, check constraints) - these belong in the
  repository's own `Postgres` implementation, not inferred from the catalog declaration.

## Measured local gate before building a supplement

Before wiring any new-domain search supplement (a candidate source added alongside a baseline
backend) toward production, prove it locally first. Generic supplement policy, gate metrics, and
default pass/fail thresholds are owned by `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md` - reuse that
method, not any BeautyQ threshold or query text.

A new domain must define:

- baseline owner;
- supplement candidate owner;
- accepted query/eval set (source-confirmed, including at least one expected-improvement query and
  one baseline-preservation query);
- measured gate command/spec owner (the same way BeautyQ's is
  `QP19QdrantSupplementMeasuredAcceptanceGateSpec`);
- metric thresholds (reuse the generic thresholds in `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`
  unless a domain-specific accepted harm budget says otherwise);
- failure meanings (a short code per stop condition);
- docs owner for the domain's own concrete local/test gate, if it keeps one (the same way
  `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md` owns BeautyQ's);
- a separate approval boundary for any production/default route change - a passing measured gate is
  local/test evidence only, never rollout approval by itself.

Lock or stop: if the gate is green, lock the exact query set, counts, and failure markers; if there
is no improvement, stop; if there is any regression, stop or get an explicit coordinator/user-approved
budget before implementing (never a vague "almost no worsening"); if the source query inventory is
incomplete, report it as source-incomplete rather than inventing queries.
