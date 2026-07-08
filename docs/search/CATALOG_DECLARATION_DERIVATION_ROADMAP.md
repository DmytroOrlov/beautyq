# Catalog Declaration Derivation Roadmap

> Status: A–F are historical/completed phase notes through full-loader traversal and snapshot
> assembly derivation. For current coordinator handoff and next active work, start from
> `docs/search/CATALOG_DECLARATION_DERIVATION_HANDOFF.md`.

## Status

* Module split closeout is done separately in `BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md`.
* This document tracks post-closeout catalog declaration simplification.
* Current accepted direction: business-facing code should stay close to the catalog tree, while
  Scala 3 derivation/macros/interpreters remove repeated evidence/boilerplate.
* Phase A is implemented as Phase 46: the self-tree root key now lives in the catalog declaration.

## Architecture layers to keep separate

```text
1. Pure catalog declaration / contract
   Owns declarative topology: entities, roots, edges, key selectors, explicit business facts such as self-tree root key.
   Must not know F, repositories, DI, SQL, clients, loaders, HTTP, runtime backend routing, or search projection.

2. Catalog materialization interpreter
   Owns turning the pure declaration into repo-backed relations, traversal, loaded graph, and catalog snapshot.
   May know repositories, loader evidence, GraphLoading, snapshot assembly, and materialization-specific compile-time derivation.

3. Search document projection / runtime / app shell
   Owns search document construction, projection invariants, runtime wiring, backend routing, HTTP, config, DI/resources, clients, startup, and app shell execution.
   Must not be hidden inside the catalog topology DSL.
```

## Values / constraints

* Business code should be as close as possible to:

```scala
catalog("beautyq")
  .branch[Category]
  .rootTree(_.parentId, root = Category.rootCategoryId)
  .child[Service](_.categoryId)
  .branch[Service]
  .value[ServiceVariantSchema](_.serviceId)
  .branch[Master]
  .rootAll
  .child[MasterLocation](_.masterId)
  .child[MasterServiceOffer](_.masterId)
  .branch[MasterServiceOffer]
  .child[MasterServiceOfferVariant](_.masterServiceOfferId)
```

* Explicit business facts should remain explicit.
* Tautological evidence should be derived.
* Derivation must fail at compile time with useful errors when ambiguous.
* Every phase must be small and behavior-preserving.
* Do not add DI/Distage/ZIO dependencies to pure `repo-core` or pure contract modules.
* Do not mix repo derivation, snapshot derivation, document projection, and runtime changes in one
  patch.

## Current tautology inventory

```text
1. `Repositories[F]` repeats all required repositories for graph materialization.
2. Repo companion `entity = RepoEntity.derived[...]` repeats entity type.
3. Repo companion `byId` wrappers repeat obvious get-by-id methods.
4. Repo companion `byCategory` / `byMaster` / `byOffer` wrappers repeat edge loader methods.
5. `CatalogEntity.Aux[...]` givens repeat conventional `id`.
6. `CatalogValue.Aux[...]` and value-source wiring repeat value aggregate metadata.
7. `CatalogRootTree.Aux` repeats `.rootTree(...)`.
8. `CatalogMany.Aux` repeats `.child[Child](...)`.
9. `CatalogValueEdge.Aux` repeats `.value[Value](...)`.
10. `Graph` fields repeat declared edges as named relation fields.
11. `Graph.fromDeclaration` repeats those fields via `relationAs[...]`.
12. `Relations` facade repeats `Graph` fields against `Repositories`.
13. Full loader traversal repeats catalog tree traversal order.
14. Snapshot constructor repeats loaded graph outputs.
15. Compatibility `Nodes` repeat entity/value handles.
```

## Approved phases A–F

### Phase A: root key in declaration

Status: implemented in Phase 46.

Goal:

* Move self-tree root key from loader call into catalog declaration:

```scala
.rootTree(_.parentId, root = Category.rootCategoryId)
```

Accepted scope:

* `RootTreeSpec` carries `rootKey`;
* `Relation.SelfTree` carries `rootKey`;
* `GraphLoading.selfTreeFrom` reads `relation.rootKey`;
* full snapshot loader stops passing root key manually.

Non-goals:

* repo derivation;
* Graph/Relations removal;
* snapshot derivation;
* document projection;
* DI wiring.

### Phase B: derive standard repo operation adapters

Status: Phase B.1 derives standard repo operation adapter bodies by method signature while keeping
existing BeautyQ companion wrapper names stable. Relation evidence derivation is still Phase D.

Status: Phase B.2 policy accepted. Ambiguous repo loader derivation must stay explicit when
distinct domain IDs are transparent aliases of the same underlying type. The derivation must not
fall back to method names. Current BeautyQ ambiguous wrappers remain manual:
`MasterServiceOffers.byMaster`, `MasterServiceOffers.byService`,
`MasterServiceOfferVariants.byOffer`, and `MasterServiceOfferVariants.byLocation` - because
`MasterId`, `ServiceId`, `MasterLocationId`, and `MasterServiceOfferId` are transparent aliases of
the same underlying `UUID`, so after dealiasing the paired many-loader methods in each repo have
identical type shapes.

Future domains should prefer nominal/opaque ID types when they want more complete type-based
derivation. Migrating BeautyQ IDs to nominal/opaque types is a separate model refactor and is not
part of Phase B.

Goal:

* Remove repetitive wrappers like:

```scala
def byId[F[_, _]](repo: Services[F]): OptionalByKey[F, ServiceId, Service] =
  OptionalByKey(repo.getService)
```

Direction:

* Prefer Scala 3 quoted macro or typeclass derivation that finds repo methods by signature:
  * `K => F[QueryFailure, Option[A]]` for by-id;
  * `K => F[QueryFailure, List[A]]` for many-by-key;
  * no-arg `F[QueryFailure, List[A]]` for all.
* If exactly one matching method exists, derive.
* If none or more than one exists, fail with a compile-time error and require explicit override.

Non-goals:

* do not rename all repo methods in this phase unless the derivation needs a small convention;
* do not introduce DI into repo-core.

### Phase C: derive catalog entity/value evidence

Status: Phase C.1 adds automatic `CatalogEntity.Aux` derivation from a conventional `id` field
(`CatalogEntity.derivedFromId`, repo-core), but only `Category`'s manual BeautyQ
`CatalogEntity.Aux[...]` given was removed from `BeautyQCatalogGraph.Evidence` - it is the only one
of the six that never appears as an edge's child side or as a `rootAll` root, so its key type is
always already pinned by the declared spec type. The other five (`Service`, `Master`,
`MasterLocation`, `MasterServiceOffer`, `MasterServiceOfferVariant`) stay explicit: verified
empirically, Scala's implicit search cannot use a fully generic `derivedFromId[A, K]` given to
solve a free key type in `MaterializeOne.manyEdge`'s `childEntity` or `MaterializeOne.rootAll`'s
`entity` slot, because unifying `CatalogEntity.Aux[C, CK]` against a generic candidate leaves `CK`
unconstrained before macro expansion runs. Closing that gap would mean rewriting
`CatalogRootAll`/`CatalogMany`/`MaterializeOne` to carry the child/root key type in the spec tuple
itself, which is out of scope for this phase. `CatalogValue` / aggregate value-source evidence
remains fully explicit, unchanged.

Status: Phase C.2 design spike (`repo-core/src/test/scala/leaderboard/repo/`
`CatalogEntityKeyPropagationDesignSpec.scala`) positively proved key-carrying specs (Candidate A): a
rootAll-like or manyEdge-like spec that carries its output key type(s) lets
`CatalogEntity.derivedFromId` resolve automatically and build real `Relation.All` /
`Relation.HasMany` values without explicit entity givens.

Candidate B, directly parameterized entity evidence such as `CatalogEntity[A, K]`, remains open. A
fair validating macro-backed test could not be completed inside the test-only spike without adding
a separate macro implementation file. It is not rejected.

Production C.3 still needs a coordinator architecture decision: first pin output key types in
specs, first parameterize catalog evidence, or combine both. The combined path may be the
highest-boilerplate-reduction target.

Status: Phase C.2b (`repo-core/src/test/scala/leaderboard/repo/CatalogEntity2Derivation.scala` +
`CatalogEntityParameterizedDesignSpec.scala`) fairly retested Candidate B with a real validating
macro in its own file (separate from its call sites, resolving the Phase C.2 "Cyclic macro
dependencies" blocker) and proved parameterized `CatalogEntity[A, K]` alone does not solve the
free-key problem; specs still need to carry output key types before remaining entity givens can be
removed. The same free-key `RootAllSpecCurrent[A]`/child-key `ManyEdgeSpecCurrent[P, C, K]` shapes
that fail with `CatalogEntity.Aux[A, K]` also fail with a validating, directly-parameterized
`ParamCatalogEntity[A, K]`, with the identical "macro expansion was stopped" symptom; the
key-carrying `RootAllSpec2`/`ManyEdgeSpec2` shapes succeed with either encoding. Production C.3
should prioritize key-carrying specs (Candidate A); parameterizing catalog evidence on top remains
an open, independent boilerplate-reduction question, not a substitute for it.

Status: Phase C3 pins rootAll and child-edge output key types in production catalog specs,
allowing all normal BeautyQ `CatalogEntity.Aux[...]` givens to be removed. `CatalogValue` and
relation loader evidence remain explicit.

`RootAllSpec[A]`/`ManyEdgeSpec[P, C, K]` became `RootAllSpec[A, K]`/`ManyEdgeSpec[P, C, K, CK]`,
carrying their output key type(s) the same way `RootTreeSpec[A, K]` already did. `CatalogBranch
.rootAll`/`CatalogChildStart.apply` keep their exact public call shape (`.rootAll`,
`.child[Service](_.categoryId)`, no explicit type arguments) unchanged - confirmed by recompiling
the untouched `BeautyQCatalogDeclaration.declaration` as-is. All five remaining BeautyQ normal
entity givens (`Service`/`Master`/`MasterLocation`/`MasterServiceOffer`/
`MasterServiceOfferVariant`) were removed from `BeautyQCatalogGraph.Evidence`; only `Category`'s
had been removable before this phase.

Deviation from the task's suggested implementation shape, verified empirically: a `rootAll[K](using
CatalogEntity.Aux[A, K])`/`apply[K, CK](...)(using CatalogEntity.Aux[C, CK])` shape - `K`/`CK` as a
free type parameter of `rootAll`/`apply` itself, resolved via a `using` clause - does not compile.
Scala defaults the unconstrained `K`/`CK` to `Any` before attempting the `using` search (regardless
of whether the sought type is `CatalogEntity.Aux[A, K]` or the unrefined `CatalogEntity[A]`), and
`CatalogEntity.derivedFromId`'s validation then correctly rejects `Any`. This reproduces, at
declaration time, the exact same free-variable limitation Phase C.1/C.2/C.2b diagnosed at
materialization time - it is not specific to `MaterializeOne`'s nested implicit search. Instead,
`RepoGraph.scala` adds `ConventionalIdKey[Labels, Elems]`, a pure type-level match type that reads
a product type's conventional `id` field type directly off its own `Mirror.ProductOf` element
labels/types - no macro, no free type parameter, no value indirection. `rootAll`/`apply` use it
(`using mirror: Mirror.ProductOf[A]`, keyed off the *named* `mirror` parameter's own path-dependent
members) to pin the output key type without ever asking Scala's implicit search to solve a free
variable. `CatalogEntity.derivedFromId` is unchanged and still does all the work at materialization
time, where the key type is now always already concrete.

Status: Phase D1 derives unambiguous rootTree/rootAll/many relation-loader evidence from the
repository bundle by operation signature. BeautyQ no longer hand-wires the unambiguous
`Category` tree, `Category -> Service`, `Master` rootAll, or `Master -> MasterLocation` loaders.
`CatalogValueEdge` and transparent-UUID ambiguous many edges remain explicit.

Status: Phase D2A derives unambiguous value-edge (`CatalogValueEdge`) relation-loader evidence the
same way D1 derived rootTree/rootAll/many: exactly one repositories-bundle field with a method
shaped `K => F[QueryFailure, V]`, via a new `CatalogRelationEvidenceDerivation.valueEdgeImpl`
reusing the existing `uniqueRepositoryField`/`singleArgCandidates` helpers - no method-name or
repo-field-name fallback, compile-time failure on zero or multiple matches. This supersedes the D1
status line above: `CatalogValueEdge` no longer remains explicit. BeautyQ's only declared value edge
(`Service -> ServiceVariantSchema`, keyed by `serviceId`) now resolves automatically; its explicit
`CatalogValueEdge.Aux[...]` given was removed from `BeautyQCatalogGraph.Evidence`.
`CatalogValue.Aux[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem]` stays explicit,
unchanged - value-source identity evidence is a separate, independent decision from the edge-loader
derived here. Transparent-UUID ambiguous many edges remain explicit, unchanged.

`CatalogValue`/aggregate value-source derivation is not started or claimed.

Goal:

* Remove repetitive `CatalogEntity.Aux[...]` where entities have conventional `id`.
* Consider replacing path-dependent `Aux` style with parameterized typeclasses:

```scala
CatalogEntity[A, K]
CatalogValue[V, K, Item]
```

Direction:

* Use Scala 3 `Mirror` / quoted derivation for conventional `id`.
* Keep value aggregate special cases explicit until they can be derived safely.

Non-goals:

* no behavior changes to entity IDs;
* no projection logic.

### Phase D: derive relation evidence from declaration + repo loaders

Goal:

* Remove repetitive:
  * `CatalogRootTree.Aux`;
  * `CatalogMany.Aux`;
  * `CatalogValueEdge.Aux`.

Direction:

* Use the tree declaration plus derived repo loader adapters.
* Explicit override remains available for ambiguous loaders.

Non-goals:

* no Graph/Relations API removal yet;
* no snapshot derivation yet.

### Phase E: replace `Graph` / `Relations` named-field tautology with typed accessors

Status: Phase E1 removes the BeautyQ-specific `Graph` / `Graph.fromDeclaration` factory wrapper.
`BeautyQCatalogGraph.graph` now exposes the generic materialized declaration directly.

Status: Phase E2 removes the BeautyQ `Relations` named facade. The full snapshot loader now
selects relation factories directly from the generic materialized declaration via typed
`relationAs[...]` and keeps traversal/snapshot assembly unchanged. Phase F snapshot traversal and
constructor derivation remains future work.

Goal:

* Stop repeating every edge as:
  * `Graph` field;
  * `Graph.fromDeclaration` `relationAs[...]`;
  * `Relations` field.

Direction:

* Provide typed relation accessors such as:

```scala
relations.rootTree[Category]
relations.many[Category, Service]
relations.value[Service, ServiceVariantSchema]
relations.all[Master]
```

* Require explicit labels only when `(Parent, Child)` or relation type is ambiguous.

Non-goals:

* no full loader rewrite in this phase unless required for tests;
* keep compatibility API until zero-usage audit.

### Phase F: derive full catalog snapshot loader

Status: Phase F1 derives the full-loader traversal into a generic loaded-catalog helper. The
BeautyQ full snapshot loader no longer manually sequences `GraphLoading.selfTreeFrom` / `manyFor` /
`valueFor` / `allOf`; it still manually assembles `BeautyQSearchCatalogSnapshot` and keeps
deduplication keys explicit. Seed-scoped loading remains explicit.

`LoadedCatalog[Items <: Tuple]` (repo-core, new file `CatalogLoadedGraph.scala`) is a raw,
non-deduplicated bundle of loaded lists, one per declared relation, selected by exact `List[A]` type
via the existing `TupleSelect`. `LoadCatalogRelations[F, R, Rels]`/`LoadOneRelation[F, R, H, Prior]`
(both `private[repo]`, mirroring `MaterializeAll`/`MaterializeOne`'s own internal-machinery-vs-public-
evidence split) recursively load a materialized relation tuple, one `LoadOneRelation` given per
relation-factory shape (`SelfTree`/`All`/`HasMany`/`HasValue`). `declaration.loadAll(repositories)` is
a standalone extension on `MaterializedDeclaration` (not a method in its own body: `Materialized
Declaration`'s own `F[_, _]` is invariant, and `Error2[F]` - needed for the `.map` that wraps the
result in `LoadedCatalog` - requires `F[+_, +_]`; the extension introduces its own fresh covariant
`F[+_, +_]: Error2`, the same shape `GraphLoading`'s own standalone functions already use, rather than
widening `MaterializedDeclaration`'s own declared variance).

`Rels` is stored in reverse declaration order (each `.rootTree`/`.rootAll`/`.child`/`.value` call
prepends its spec, so the tuple's head is always the latest-declared relation). `LoadCatalogRelations
.cons` recurses into the tuple's tail *before* loading its head, accumulating one loaded list per
relation into `Prior`; a `HasMany`/`HasValue` head then selects its already-loaded parent list out of
`Prior` via `TupleSelect[Prior, List[P]]` (safe here, unlike the Phase C3 free-`K`-defaults-to-`Any`
pitfall: `P` is never a free variable needing solving on its own - it, `Prior`, and every other type
parameter are all determined by unifying the given's own declared result type against the already-fully
-concrete `H`/`TLoaded` implicit search is looking for, the same mechanism `TupleSelect.found` itself
already relies on). Unwinding this tail-first recursion runs the actual loads in forward declaration
order - parents before children - even though the tuple itself is stored backwards; verified against
BeautyQ's real seven-relation chain (categories, services, schemas, masters, locations, offers,
variants) via the unchanged `BeautyQRepoGraphLoaderSpec` ordering assertions, all passing.

Status: Phase F2 derives full-loader snapshot constructor assembly from `LoadedCatalog` into a
generic repo-core snapshot assembly helper. The BeautyQ full loader now delegates both traversal and
snapshot construction to generic materialization helpers. Raw loaded lists remain non-deduplicated;
snapshot assembly applies first-occurrence deduplication by catalog entity key or aggregate value key.
Seed-scoped loading remains explicit. Search document projection and value-edge derivation remain
separate.

`loaded.toSnapshot[Snapshot]` (repo-core, new file `CatalogSnapshotAssembly.scala`) is a standalone
extension on `LoadedCatalog` (same reasoning as `loadAll` on `MaterializedDeclaration`: it introduces
its own type parameters rather than adding a method to `LoadedCatalog`'s own body). It walks
`Mirror.ProductOf[Snapshot].MirroredElemTypes` - the snapshot's own field-type tuple, e.g.
`(List[Category], List[Service], ...)` - via `private[repo]` typeclasses `AssembleSnapshotFields`
(recurses the field tuple) and `AssembleSnapshotField` (assembles one `List[A]` field), then builds
`Snapshot` via `mirror.fromProduct`. Snapshot field order therefore always follows `Snapshot`'s own
constructor, never `LoadedCatalog.items`'s storage order - the two tuples are walked independently,
joined only by `TupleSelect[Items, List[A]]` per field.

Each field is deduplicated (first-occurrence) using whichever key evidence the domain already
declared for `A`, with `AssembleSnapshotField.fromValue` (aggregate/value types, keyed by
`CatalogValue.Aux[A, K, Row]`'s own `valueSource.keyField` - e.g. `serviceId` for
`ServiceVariantSchema`) taking priority over the low-priority `fromConventionalId` fallback (normal
entity types), mirroring `TupleSelect`/`TupleSelectLowPriority`'s own disambiguation pattern.
`fromConventionalId` cannot request `CatalogEntity.Aux[A, K]` via a `using` clause - `K` would be a
free type parameter with no other unification source, reproducing the declaration-time
free-`K`-defaults-to-`Any` limitation `ConventionalIdKey`'s own doc describes. It instead calls
`CatalogEntity.derived[A](RepoEntity.derived[A])` directly, requiring `fromConventionalId` itself to
be `inline` so that call sees a concrete `A` at each summon site. Two further, purely mechanical
constraints followed from that: an `inline given` may not itself return a bare function value or an
anonymous class instance (both rejected by the compiler as "duplicated at each inline site"), so both
`AssembleSnapshotField` givens construct a named `AssembleSnapshotFieldImpl` instead, keeping the
`inline given`'s own right-hand side a plain constructor call. No broad automatic
`given CatalogEntity[A]` was added anywhere - this derivation is entirely local to
`AssembleSnapshotField`'s own low-priority given.

`BeautyQSearchCatalogSnapshotLoader.FromRepositories.load()` now reads only
`for { loaded <- BeautyQCatalogGraph.graph[F].loadAll(repositories) } yield loaded.toSnapshot
[BeautyQSearchCatalogSnapshot]`, with a local `import BeautyQCatalogGraph.Evidence.given` so
`CatalogValue.Aux[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem]` is in scope for
`fromValue` to pick up - confirmed by the unchanged `BeautyQRepoGraphLoaderSpec` ordering and
projection assertions, all still passing. `SeedScopedFromRepositories` is untouched and still builds
`BeautyQSearchCatalogSnapshot` manually from seed-scoped loads.

`CatalogValue`/`CatalogValueEdge` derivation is not started or claimed.

Goal:

* Remove manual full traversal and snapshot constructor when the snapshot fields match loaded graph
  outputs.

Direction:

* Generic full traversal follows declaration order.
* Snapshot derivation uses `Mirror.ProductOf[Snapshot]`.
* Field matching should be by unique element type / collection type, with explicit alias required
  on ambiguity.
* Dedup uses `CatalogEntity` / value key evidence.

Non-goals:

* seed-scoped loading policy remains explicit;
* search document projection remains separate;
* business invariants/schema validation remain separate.

## What must not be inferred from catalog tree

* search document projection;
* projection invariants;
* schema/attribute validation semantics;
* seed-scoped loading policy;
* response policy;
* runtime backend routing;
* Qdrant activation/fallback/fusion/rerank;
* HTTP/app-shell/DI/resource wiring;
* full `SearchDomainSpec` sections outside catalog.

## Closeout / continuation

This roadmap is now historical phase detail for A–F (and D2A, recorded above). Current active work
continues in `CATALOG_DECLARATION_DERIVATION_HANDOFF.md`.

D2A (derive `CatalogValueEdge` from repositories while keeping `CatalogValue` / value-source policy
explicit) is done. The next recommended patch is Seed F1: extract seed-scoped loading helpers
without changing seed semantics.
