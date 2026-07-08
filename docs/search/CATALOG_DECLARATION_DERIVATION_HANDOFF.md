# Catalog Declaration Derivation Handoff

## Purpose

This is the **current coordinator starting point** for catalog declaration / Scala 3 derivation
work, picking up immediately after Phases A–F of
`docs/search/CATALOG_DECLARATION_DERIVATION_ROADMAP.md` completed. That roadmap document is now
**historical phase detail** — it stays as the dated record of how A–F were designed, attempted,
and (in a few cases) redesigned after an approach failed empirically. Read it when you need the
"why" behind a specific mechanism (e.g. why `ConventionalIdKey` exists, why `LoadedCatalog` stores
its tuple in reverse order). Read *this* document first for "what is true now" and "what to do
next".

## Current accepted state

All of Phases A–F are done, and D2A/Seed F1/wrapper cleanup/seed root-key cleanup besides:

```text
A:         root key moved into catalog declaration
B:         standard repo op adapters derived by method signature
C:         normal CatalogEntity evidence derived from conventional id; specs carry output key types
D1:        unambiguous rootTree/rootAll/many relation-loader evidence derived
D2A:       unambiguous value-edge (CatalogValueEdge) relation-loader evidence derived
E1/E2:     BeautyQ Graph/fromDeclaration/Relations named-field layers removed
F1/F2:     full-loader traversal and full-loader snapshot assembly derived generically
Seed F1:   seed-scoped required/value loading helpers derived by conventional id / signature
Wrapper cleanup: zero-usage BeautyQ repo companion wrappers deleted
Seed root-key cleanup: seed-scoped root filtering derived from the materialized catalog relation
```

The BeautyQ full loader (`BeautyQSearchCatalogSnapshotLoader.FromRepositories.load()`) is now:

```scala
for {
  loaded <- BeautyQCatalogGraph.graph[F].loadAll(repositories)
} yield loaded.toSnapshot[BeautyQSearchCatalogSnapshot]
```

`loadAll` (Phase F1) generically traverses the materialized relation tuple in declaration order,
producing raw, non-deduplicated lists in a `LoadedCatalog`. `toSnapshot` (Phase F2) assembles the
snapshot case class from that `LoadedCatalog` via `Mirror.ProductOf`, deduplicating each field by
whichever evidence the domain declared (`CatalogValue` for aggregate/value fields,
conventional-`id` `CatalogEntity` derivation otherwise).

**Seed-scoped loading keeps its exact semantics, but no longer hand-wires per-entity wrapper calls.**
`BeautyQSearchCatalogSnapshotLoader.SeedScopedFromRepositories.load()` now calls
`GraphLoading.seedRequiredById[F, Repo[F], A](items, repo)` /
`GraphLoading.seedValuesByKey[F, Repo[F], K, V](keys, repo)` (repo-core, Seed F1) instead of manually
writing `GraphLoading.seedRequired(items, X.entity.modelName, _.id, X.byId(repo))` per entity. Same
canonical missing-entity message, same seed item order, same manual
`BeautyQSearchCatalogSnapshot(...)` constructor, same no-dedup behavior - Seed F1 changed *how* the
per-entity loader/model-name/id-selector triple is obtained, not any of the seed-scoped semantics
themselves. No phase has touched, or should touch without a deliberate decision, its missing-entity
behavior, its ordering, or its dedup policy (it doesn't deduplicate - seed input is assumed
already-distinct).

## Current architecture boundaries

The three-layer model from the historical roadmap still holds and is not being revisited:

```text
1. pure catalog declaration / contract
   Owns declarative topology only. Must not know F, repositories, DI, SQL, clients, loaders,
   HTTP, runtime backend routing, or search projection.

2. catalog materialization interpreter
   Owns turning the pure declaration into repo-backed relations, traversal, loaded graph, and
   catalog snapshot. May know repositories, loader evidence, GraphLoading, snapshot assembly,
   and materialization-specific compile-time derivation.

3. search document projection / runtime / app shell
   Owns search document construction, projection invariants, runtime wiring, backend routing,
   HTTP, config, DI/resources, clients, startup, and app shell execution.
```

Nothing in the sections below authorizes reaching into layer 3. Projection, runtime routing,
Qdrant/ES activation policy, and HTTP/DI wiring are **not** to be inferred, derived, or refactored
as a side effect of catalog derivation work. See "Known non-goals" below.

## Current remaining tautology inventory

```text
1. CatalogValue / CatalogValueEdge evidence
2. SeedScopedFromRepositories
3. repo companion wrappers
4. root-id/root-key leakage in seed path
5. transparent UUID ambiguity
6. Nodes compatibility for projection
```

Status per cluster:

```text
CatalogValueEdge:
  DONE_D2A_DERIVED_FROM_REPOSITORIES

CatalogValue:
  KEEP_EXPLICIT_VALUE_SOURCE_POLICY_FOR_NOW

SeedScopedFromRepositories:
  DONE_SEED_F1_EXTRACTED_SEED_LOADING_HELPERS
  BLOCKED_SEED_TO_SNAPSHOT_BY_DEDUP_POLICY

Repo wrappers:
  DONE_WRAPPER_ZERO_USAGE_CLEANUP_FOR_ID_KEYED_WRAPPERS
  BLOCKED_BY_TRANSPARENT_UUID_AMBIGUITY

Root id/root key:
  DONE_SEED_ROOT_FILTER_FROM_DECLARATION
  KEEP_EXPLICIT_REPOSITORY_ROOT_INVARIANTS
  KEEP_EXPLICIT_API_ROOT_CHILDREN_ENDPOINT

Transparent UUID ambiguity:
  BLOCKED_TRANSPARENT_UUID_AMBIGUITY

Nodes compatibility for projection:
  KEEP_EXPLICIT_NODES_FOR_PROJECTION
```

Notes on each status:

* **`DONE_*`** — implemented and validated; kept in the inventory so the historical shape of the
  cluster (what it used to be, why it mattered) stays visible without having to open the roadmap.
* **`PATCH_READY_*`** — a specific, scoped patch is ready to propose; see the scope sections below.
* **`KEEP_EXPLICIT_*`** — a deliberate decision to leave this explicit for now; not a blocker, not
  forgotten work. Re-derive only behind a fresh coordinator decision, not as a drive-by.
* **`BLOCKED_*`** — genuinely blocked on something outside this initiative's current scope
  (a policy decision, a model refactor, or another patch landing first).

`CatalogValueEdge` was the one remaining piece of the original Phase D goal
(`docs/search/CATALOG_DECLARATION_DERIVATION_ROADMAP.md`'s "Phase D: derive relation evidence from
declaration + repo loaders" listed `CatalogRootTree.Aux`/`CatalogMany.Aux`/`CatalogValueEdge.Aux`;
D1 derived the first two, `CatalogValueEdge` was left explicit until D2A). `CatalogRelationEvidence
Derivation.valueEdgeImpl` (repo-core) now derives it the same way D1 derived
rootTree/rootAll/many: exactly one repositories-bundle field with a method shaped `K => F[QueryFailure,
V]` for the requested `K`/`V`, reusing the existing `uniqueRepositoryField`/`singleArgCandidates`
helpers - no method-name or repo-field-name fallback, fails to compile on zero or multiple matches.
BeautyQ's only declared value edge (`Service -> ServiceVariantSchema`, keyed by `serviceId`) resolves
this way now; its explicit `CatalogValueEdge.Aux[...]` given was removed from
`BeautyQCatalogGraph.Evidence`. `CatalogValue` itself (the value-source *identity* evidence, as
opposed to the edge-loader) is a separate, independent decision, deliberately untouched by D2A - do
not conflate deriving the edge loader with deriving the value source.

`SeedScopedFromRepositories` no longer hand-writes `GraphLoading.seedRequired(items,
X.entity.modelName, _.id, X.byId(repo))`/`GraphLoading.seedValues(keys, X.byService(repo))` per
entity - `GraphLoading.seedRequiredById`/`seedValuesByKey` (repo-core, Seed F1) derive the
model name, id selector (via a direct `CatalogEntity.derivedFromId[A, ConventionalIdKey[...]]`
invocation - pinning `K` to `ConventionalIdKey` *before* summoning entity evidence for it, not
summoning evidence first and reusing its own separately-derived `Key` member, which empirically
does not reach a nested quoted macro's own type comparison reduced) and repo operation (via
`OptionalByKey.derived`/`ValueByKey.derived`, by signature, no method-name fallback) automatically.
A zero-usage check performed after Seed F1 found `Categories.byId`, `Services.byId`,
`Masters.byId`, `MasterLocations.byId`, `MasterServiceOffers.byId`,
`MasterServiceOfferVariants.byId`, and `ServiceVariantSchemas.byService` had no remaining callers
anywhere - Seed F1 was their last one, D1 having already removed the full loader's dependence on
them. The wrapper zero-usage cleanup patch then deleted all 13 zero-usage wrappers (see "Wrapper zero-usage cleanup
scope" below) and fixed the D2A-era comment in `BeautyQCatalogGraph.scala`'s `Evidence` object that
had claimed the seed-scoped loader was still a caller.

## Recommended next sequence

```text
1. D2A: derive CatalogValueEdge from repositories.                              [done]
2. Seed F1: extract seed-scoped loading helpers without changing seed semantics. [done]
3. Wrapper zero-usage cleanup: remove the zero-usage repo companion wrappers.   [done]
4. Seed root-key cleanup: derive root filtering from declaration root where safe. [done]
5. Transparent UUID ambiguity decision: separate policy decision.
6. D2B/value-source DSL: separate policy decision.
```

Recommended next patch: **Transparent UUID ambiguity decision**.

Rationale: D2A, Seed F1, wrapper cleanup, and seed root-key cleanup are all landed. The two
remaining explicit wrappers/evidence (`MasterServiceOffers.byMaster`/
`MasterServiceOfferVariants.byOffer`, and the `BeautyQCatalogGraph.Evidence` givens that call them)
are blocked purely on transparent UUID aliasing (`MasterId`/`ServiceId`/`MasterLocationId`/
`MasterServiceOfferId` all dealias to the same underlying `UUID`, so type-only derivation cannot
disambiguate them) - not on anything a mechanical patch can resolve. That decision (accept the
aliasing as permanently explicit, or migrate BeautyQ IDs to nominal/opaque types as a separate model
refactor - flagged as a future domain preference back in the historical roadmap's Phase B.2 record)
is the only remaining item in this tautology inventory that isn't already `KEEP_EXPLICIT_*` or
`DONE_*`. D2B/value-source DSL remains a separate, independent policy decision, not advanced by any
of D2A/Seed F1/wrapper cleanup/seed root-key cleanup.

## D2A scope

Status: implemented. `CatalogValueEdge.derivedFromRepositories` (repo-core, `RepoGraph.scala` +
`CatalogRelationEvidenceDerivation.valueEdgeImpl`) derives unambiguous value-edge loader evidence
from a repositories bundle by exact operation shape (`K => F[QueryFailure, V]`), the same
type/signature-based matching D1 already used for rootTree/rootAll/many - no method-name or
repo-field-name fallback, compile-time failure on zero or multiple matches.
`BeautyQCatalogGraph.Evidence`'s explicit `CatalogValueEdge.Aux[F, Repositories[F], Service,
ServiceVariantSchema, ServiceId]` given was removed; `CatalogValue.Aux[ServiceVariantSchema,
ServiceId, ServiceVariantSchemaItem]` stays explicit, unchanged, per this scope's own "Keep" rule
below. `ServiceVariantSchemas.byService` was kept during D2A. Seed F1 later removed the seed-loader
call site too, so it is now part of the wrapper zero-usage cleanup set.

Original scope, kept for reference:

```text
Goal:
- remove explicit CatalogValueEdge.Aux for Service -> ServiceVariantSchema.

Keep:
- explicit CatalogValue.Aux[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem].

Forbidden:
- do not derive CatalogValue;
- do not change ValueEdgeSpec;
- do not change snapshot assembly;
- do not change seed loader;
- do not use method-name fallback.
```

The "do not use method-name fallback" rule repeats the Phase B.2 policy already on record in the
historical roadmap: ambiguous repo loader derivation (e.g. two ID types that are transparent
aliases of the same underlying type) must stay explicit rather than guess from a method name.

## Seed F1 scope

Status: implemented. `GraphLoading.seedRequiredById`/`GraphLoading.seedValuesByKey` (repo-core)
replaced `SeedScopedFromRepositories`'s seven manual
`GraphLoading.seedRequired(items, X.entity.modelName, _.id, X.byId(repo))`/
`GraphLoading.seedValues(keys, X.byService(repo))` call sites. Missing-entity message, seed item
order, key order, the manual `BeautyQSearchCatalogSnapshot(...)` constructor, and the no-dedup
behavior are all byte-for-byte unchanged - confirmed by both repo-core unit coverage
(`GraphLoadingSpec`) and BeautyQ-level coverage (`BeautyQRepoGraphLoaderSpec`'s new
"BeautyQ seed-scoped graph loader" block), which exercises the real
`SeedScopedFromRepositories` end-to-end, including the canonical missing-entity failure path and a
seed snapshot that still projects successfully. `seedScope.nonRootCategories`/root filtering itself
was intentionally left untouched in this patch, not derived from the catalog declaration yet, per
the original scope below - that followed later, in Seed root-key cleanup (see its own scope
section below).

Original scope, kept for reference:

```text
Goal:
- reduce SeedScopedFromRepositories helper boilerplate.

Forbidden:
- do not change missing-entity behavior;
- do not change seed order;
- do not silently deduplicate seed snapshot;
- do not replace seed constructor with toSnapshot until dedup policy is decided.
```

The seed loader intentionally does not deduplicate (seed input is assumed already-distinct) — this
is a different policy than the full loader's first-occurrence dedup, and the two must not be
silently unified. Whether/how to eventually let the seed loader also use `toSnapshot` is an open
policy question, not a mechanical refactor; it is explicitly out of scope for "Seed F1" as scoped
above.

## Wrapper zero-usage cleanup scope

Status: implemented. Mechanical deletion only, per the zero-usage check recorded above - no
repository trait changed, no repo method renamed, no evidence logic changed.

Removed (zero remaining callers anywhere in the repo):

```text
Categories.byId
Categories.childrenByParent
Services.byId
Services.byCategory
ServiceVariantSchemas.byService
Masters.byId
Masters.all
MasterLocations.byId
MasterLocations.byMaster
MasterServiceOffers.byId
MasterServiceOffers.byService
MasterServiceOfferVariants.byId
MasterServiceOfferVariants.byLocation
```

Kept (still explicit ambiguous relation evidence - `BeautyQCatalogGraph.Evidence` calls both
directly, because `MasterId`/`ServiceId`/`MasterLocationId`/`MasterServiceOfferId` are transparent
aliases of the same underlying `UUID`, so type-only derivation is ambiguous):

```text
MasterServiceOffers.byMaster
MasterServiceOfferVariants.byOffer
```

Now-unused `leaderboard.repo.RepoOp.{OptionalByKey, ManyByKey, ValueByKey, AllValues}` imports were
removed from each companion object where every symbol they imported became unused; `ManyByKey`
stayed imported in `MasterServiceOffers`/`MasterServiceOfferVariants` since the kept `byMaster`/
`byOffer` still use it. `BeautyQCatalogGraph.scala`'s `Evidence` object comment was updated (the
D2A-era "the seed-scoped loader still calls it directly" claim about `ServiceVariantSchemas
.byService` was already stale before this patch, fixed in a prior closeout follow-up) - the
`Evidence` givens themselves were not touched.

## Seed root-key cleanup scope

Status: implemented. `BeautyQSearchCatalogSeedScope.nonRootCategories` (`categories.filterNot(_.id
== Category.rootCategoryId)`) was removed - it had exactly one caller anywhere in the repo
(`SeedScopedFromRepositories`, changed in this same patch), so no coordinator decision was needed
to keep it. `BeautyQSearchCatalogSeedScope` now only carries seed lists; it does not know the
catalog declaration's root key.

`SeedScopedFromRepositories` now builds a `BeautyQCatalogGraph.Repositories[F]` bundle (the same
shape `FromRepositories` already builds) and reads the category self-tree relation's own `rootKey`
off the materialized catalog declaration:

```scala
private val categoryRootKey: CategoryId =
  BeautyQCatalogGraph.graph[F]
    .relationAs[BeautyQCatalogGraph.Repositories[F] => Relation.SelfTree[F, Category, CategoryId]]
    .apply(repositories)
    .rootKey

private val seedCategories: List[Category] =
  seedScope.categories.filterNot(_.id == categoryRootKey)
```

`categoryRootKey` is the exact value `BeautyQCatalogDeclaration.declaration`'s own
`.rootTree(_.parentId, root = Category.rootCategoryId)` call declared as the category tree's root -
read off the materialized relation, not `Category.rootCategoryId` referenced directly in the
search-materialization seed path. `BeautyQCatalogGraph.graph[F]` needed no caller-side
`import BeautyQCatalogGraph.Evidence.given`: its own body already imports `Evidence.given`
internally (it is `transparent inline`), and evidence is only needed to *build* the materialized
relation tuple, not to read an already-materialized relation's `rootKey` afterward - confirmed
empirically (the compiler flags a caller-side import of `Evidence.given` here as unused).

Seed order, the canonical missing-entity message, the manual `BeautyQSearchCatalogSnapshot(...)`
constructor, and the no-dedup behavior are all unchanged - confirmed by
`BeautyQRepoGraphLoaderSpec`'s existing seed-scoped test block (updated only to stop describing the
exclusion as "via `seedScope.nonRootCategories`", since that method no longer exists) and by
`RepoFieldRelationSpec`, both still passing.

Everything storage/bootstrap/API-facing keeps using `Category.rootCategoryId` directly, unchanged
by this patch: `Category.rootCategoryId` itself, `BeautyQSeedData.nonRootCategories` and
`BeautyQSeedInserter` (bootstrap/seed-insertion ordering policy, `bifunctor-tagless`/app shell,
never depended on the search-materialization module), `Categories`/`Services` repository
persistence checks and `rootCategoryIdSqlLiteral` (SQL constraint literal), and
`CategoryApi.getRootChildren` (the API root-children endpoint). This patch only removed the
repetition inside `beautyq-search-materialization`'s own seed-scoped search loader - a materialized
catalog snapshot query is not the same design pressure as a persistence constraint, a bootstrap
insertion order, or a public API route, and none of those needed to (or should) share a single root
source of truth with the search-materialization loader.

## Bundle / coordinator workflow rules

Operational rules established and confirmed while closing out A–F:

```text
When there is any source-truth gap that affects edit recipe, stop and request a focused bundle.
Do not delegate read-only audits.
Do not issue commit messages until patch is reviewed and accepted.
Agents do not commit and do not propose commit messages.
```

## Known non-goals

Do not infer:

```text
- search document projection;
- projection invariants;
- schema/attribute validation;
- runtime backend routing;
- Qdrant activation/fallback/fusion/rerank;
- HTTP/app shell/DI/resource wiring;
- full SearchDomainSpec outside catalog.
```

These stay owned by their existing layers (see "Current architecture boundaries" above) regardless
of how much further catalog-derivation work proceeds.
