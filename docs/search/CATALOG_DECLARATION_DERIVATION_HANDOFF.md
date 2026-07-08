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

All of Phases A–F are done, and D2A besides:

```text
A:     root key moved into catalog declaration
B:     standard repo op adapters derived by method signature
C:     normal CatalogEntity evidence derived from conventional id; specs carry output key types
D1:    unambiguous rootTree/rootAll/many relation-loader evidence derived
D2A:   unambiguous value-edge (CatalogValueEdge) relation-loader evidence derived
E1/E2: BeautyQ Graph/fromDeclaration/Relations named-field layers removed
F1/F2: full-loader traversal and full-loader snapshot assembly derived generically
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

**Seed-scoped loading remains fully explicit and untouched by any of this.**
`BeautyQSearchCatalogSnapshotLoader.SeedScopedFromRepositories.load()` still manually calls
`GraphLoading.seedRequired`/`GraphLoading.seedValues` per field and manually constructs
`BeautyQSearchCatalogSnapshot(...)`. No phase has touched, or should touch without a deliberate
decision, its missing-entity behavior, its ordering, or its dedup policy (it doesn't deduplicate —
seed input is assumed already-distinct).

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
  PATCH_READY_SEED_F1_EXTRACT_SEED_LOADING_HELPERS
  BLOCKED_SEED_TO_SNAPSHOT_BY_DEDUP_POLICY

Repo wrappers:
  BLOCKED_BY_SEED_LOADER_USAGE
  BLOCKED_BY_TRANSPARENT_UUID_AMBIGUITY

Root id/root key:
  PATCH_READY_SEED_ROOT_FILTER_FROM_DECLARATION
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

## Recommended next sequence

```text
1. D2A: derive CatalogValueEdge from repositories.               [done]
2. Seed F1: extract seed-scoped loading helpers without changing seed semantics.
3. Seed root-key cleanup: derive root filtering from declaration root where safe.
4. Wrapper cleanup: only after zero-usage audit.
5. D2B/value-source DSL: separate policy decision.
```

Recommended next patch: **Seed F1**.

Rationale: with D2A landed, the next-smallest, least-design-risk patch in the list is Seed F1
(reduce `SeedScopedFromRepositories` boilerplate without touching its semantics). Everything after
it either depends on a policy decision (seed dedup, value-source DSL) or on Seed F1/seed root-key
work landing first (wrapper cleanup).

## D2A scope

Status: implemented. `CatalogValueEdge.derivedFromRepositories` (repo-core, `RepoGraph.scala` +
`CatalogRelationEvidenceDerivation.valueEdgeImpl`) derives unambiguous value-edge loader evidence
from a repositories bundle by exact operation shape (`K => F[QueryFailure, V]`), the same
type/signature-based matching D1 already used for rootTree/rootAll/many - no method-name or
repo-field-name fallback, compile-time failure on zero or multiple matches.
`BeautyQCatalogGraph.Evidence`'s explicit `CatalogValueEdge.Aux[F, Repositories[F], Service,
ServiceVariantSchema, ServiceId]` given was removed; `CatalogValue.Aux[ServiceVariantSchema,
ServiceId, ServiceVariantSchemaItem]` stays explicit, unchanged, per this scope's own "Keep" rule
below. `ServiceVariantSchemas.byService` was kept (not removed) - the seed-scoped loader still
calls it directly.

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
