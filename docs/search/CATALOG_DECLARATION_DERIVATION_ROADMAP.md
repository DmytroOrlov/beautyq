# Catalog Declaration Derivation Roadmap

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

## Next coordinator checklist

* Review the Phase A / Phase 46 implementation and validation notes first.
* Then continue with Phase B evidence bundle before editing.
* Do not jump directly to Phase E/F.
* Do not introduce a library dependency before comparing zero-dependency Scala 3 derivation with any
  external library.
* Keep each phase focused and behavior-preserving.
