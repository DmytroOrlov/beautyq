# BeautyQ Search Contract Module Split Plan

Status: Phase 3 recorded. Generic repo/catalog core moved into `repo-core`; BeautyQ-specific repo companions and materialization code have not been moved yet.

## Non-negotiable premise

- The current catalog tree is only a **catalog topology section**.
- It is **not** the full BeautyQ search contract.
- It must **not** be described as "business-owned full search contract".
- It may be coordinator-readable and is suitable as the future `catalog` section of a larger contract.
- A full search contract must describe every declarative aspect needed by search interpreters (document shape, intent/vocabulary, backend runtime declarations, response policy, evaluation policy) — not just catalog topology.
- Repositories, clients, HTTP handlers, runtime services, analytics, and app wiring are **not part of the pure contract**.

Example of what exists today:

```scala
catalog("beautyq")
  .branch[Category]
    .rootTree(_.parentId)
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

Label: `catalog topology section, not complete search contract`.

## Tapir analogy

- In tapir, endpoint contracts hold declarative API structure (paths, inputs, outputs, schemas).
- Server logic, repositories, clients, runtime effects, and handlers are separate from the endpoint definition.
- For BeautyQ search, `SearchDomainSpec` should play the role of the pure endpoint contract.
- ES, Qdrant, in-memory, evaluation, and materialization/response-assembly code are **interpreters or consumers** of slices of that contract.
- Interpreters must not invent BeautyQ semantics locally — semantics live only in the contract.

## Full `SearchDomainSpec` sections

The eventual full contract contains at least these sections. Different interpreters consume different slices — no interpreter needs all sections.

| Section | Contents | Consumed by |
|---|---|---|
| `catalog` | roots, edges, entity/value relationships, key selectors — topology only | materialization, wiring |
| `document` | search document model, result unit, text/filter/facet/range/geo/semantic-text fields, vector-backend payload fields | ES, Qdrant, response assembler |
| `intent` | supported languages, vocabulary, synonyms, attribute vocabulary, service/category vocabulary, price budget semantics, geo/location semantics, negative controls/noise handling | query interpreters, evaluation |
| `runtime` | ES declarative settings/capabilities, Qdrant declarative vector/payload settings, in-memory backend expectations — declarations only, never clients | ES, Qdrant interpreters |
| `response` | result carousel policy, provider/service grouping, facets, inferred filters, presentation metadata, explanation/debug metadata | response assembler |
| `evaluation` | accepted query roles, negative controls, backend expectation metadata, scorecard/evidence configuration — must **not** imply production routing or automatic Qdrant activation | eval harness only |

## Outside the contract

The following must stay outside `SearchDomainSpec`. They belong to repositories, materialization, interpreter, wiring, or app modules:

- `Repositories[F]`
- `Categories[F]`, `Services[F]`, `Masters[F]`, etc.
- repo loader functions such as `Services.byCategory`
- `ServiceVariantSchemas.valueSource` physical source wiring
- Elasticsearch clients
- Qdrant clients
- embedding clients
- Tapir/http4s routes and handlers
- app configuration and runtime wiring
- analytics/logging/runtime services
- production fallback/fusion/rerank activation logic

## Target 10-module split

1. **`repo-core`** — generic repo/catalog primitives: `RepoEntity`, `RepoField`, `EntityNode`, `RepoValueSource`, `Relation`, `GraphLoading`, `RepoOp`, catalog DSL mechanics, `CatalogEntity`, `CatalogValue`, `TupleSelect`, narrow `CatalogEntity.derived` macro/helper if kept.
2. **`search-contract-core`** — generic search contract DSL/spec: `SearchDomainSpec`, `CatalogSection`, `DocumentSection`, `IntentSection`, `RuntimeSection`, `ResponseSection`, `EvalSection`, generic field/backend capability ADTs.
3. **`beautyq-model`** — BeautyQ domain model: `Category`, `Service`, `Master`, `MasterLocation`, `MasterServiceOffer`, `MasterServiceOfferVariant`, `ServiceVariantSchema`, document model types if model-owned.
4. **`beautyq-search-contract`** — pure BeautyQ search contract: catalog section, document section, intent section, runtime declarations, response declarations, evaluation declarations/references. Must not depend on repositories, ES/Qdrant clients, HTTP, or app wiring.
5. **`beautyq-search-repositories`** — BeautyQ data access: `Categories`, `Services`, `Masters`, `MasterLocations`, `MasterServiceOffers`, `MasterServiceOfferVariants`, `ServiceVariantSchemas`, repository bundle/environment types.
6. **`beautyq-search-materialization`** — connects contract to repositories: catalog materializer, search document materializer, snapshot builder, repo-backed loaders/evidence. May depend on contract, repositories, model, and repo-core.
7. **`search-elasticsearch`** — generic ES interpreter: contract to mapping, contract to query, document ingestion, ES response decoding. Must not depend on BeautyQ repositories or HTTP.
8. **`search-qdrant`** — generic Qdrant interpreter: semantic/vector declarations to collection/payload/vector specs, query to Qdrant request, response to candidates. Must not depend on BeautyQ repositories or HTTP.
9. **`beautyq-search-wiring`** — composition/server-logic layer: BeautyQ contract + repositories + materialization + ES/Qdrant interpreters + concrete search service wiring. May depend on runtime clients via abstractions/config.
10. **`app-http`** (or existing app shell) — Tapir/http4s routes, controllers/handlers, server boot, auth/config/health/logging shell. Should depend on wiring, not directly on repo graph internals.

## Dependency DAG

Arrows mean "may depend on".

```text
repo-core
  -> beautyq-search-repositories
  -> beautyq-search-materialization

search-contract-core
  -> beautyq-search-contract
  -> search-elasticsearch
  -> search-qdrant

beautyq-model
  -> beautyq-search-contract
  -> beautyq-search-repositories
  -> beautyq-search-materialization

beautyq-search-contract + beautyq-search-repositories + repo-core
  -> beautyq-search-materialization

beautyq-search-contract + beautyq-search-materialization + search-elasticsearch + search-qdrant
  -> beautyq-search-wiring
  -> app-http
```

## Forbidden dependencies

- `beautyq-search-contract` must **not** depend on: `beautyq-search-repositories`, `beautyq-search-materialization`, `search-elasticsearch`, `search-qdrant`, `beautyq-search-wiring`, `app-http`, concrete clients or runtime services.
- `search-elasticsearch` must **not** depend on: BeautyQ repositories, BeautyQ materialization, HTTP handlers, app wiring.
- `search-qdrant` must **not** depend on: BeautyQ repositories, BeautyQ materialization, HTTP handlers, app wiring.
- `beautyq-search-repositories` must **not** depend on: ES/Qdrant interpreters, HTTP/app modules, search runtime services.
- `app-http` must **not** directly depend on: repo graph internals, catalog materialization internals, backend-specific search semantics.

## Legacy compatibility retirement matrix

| Legacy surface | Current role | Target owner/replacement | Remove when | Remove now? |
|---|---|---|---|---|
| `BeautyQRepoGraph` | old compatibility facade over repo graph/entity/relation access | search contract + materialization/wiring | all consumers use new materializer/service | No, unless zero usages prove safe |
| `BeautyQRepoGraph#category`, `#service`, `#master`, etc. | old exported node handles | no public node handles; use derived entity evidence or contract/materializer | zero usages | Yes if zero usage |
| `BeautyQCatalogGraph.Nodes` | node/value-source compatibility | normal id nodes removed; value source moved to explicit materialization evidence | schema/tests/legacy wrappers no longer use it | Only zero-usage normal id nodes |
| `BeautyQCatalogGraph.Graph` | named materialized relation API | materializer/interpreter output or wiring API | consumers do not require named relation fields | No |
| `BeautyQCatalogGraph.Relations` | eager relation wrapper against repositories | materializer-owned relation access | loaders/snapshot builders migrate | No |
| `Repositories[F]` | repo dependency bundle | `beautyq-search-repositories` or materialization environment | replaced by equivalent environment | No |
| `ServiceVariantSchemas.valueSource` | explicit physical value-source wiring | materialization/evidence | value-source registry replaces it | No |

Rules:

- Do not delete compatibility because it "looks ugly".
- Delete only after usage audit and focused compile/tests.
- Compatibility wrappers are allowed temporarily but must not be described as contract.

## Phase 2 record: exact sbt project ids

Module shells were added to `build.sbt` with camelCase sbt project ids (per
`lazy val` identifier) and hyphenated `name :=` / base-directory conventions,
deviating from this repo's existing style of backtick-quoted hyphenated
`lazy val` identifiers (e.g. `` `search-elasticsearch` ``). No behavior or
existing dependency wiring changed beyond adding the new module dependency
edges below; `search-elasticsearch` and `search-qdrant` gained a
`searchContractCore` dependency per the DAG.

| sbt project id | base directory | published name |
|---|---|---|
| `repoCore` | `repo-core/` | `repo-core` |
| `searchContractCore` | `search-contract-core/` | `search-contract-core` |
| `beautyqModel` | `beautyq-model/` | `beautyq-model` |
| `beautyqSearchContract` | `beautyq-search-contract/` | `beautyq-search-contract` |
| `beautyqSearchRepositories` | `beautyq-search-repositories/` | `beautyq-search-repositories` |
| `beautyqSearchMaterialization` | `beautyq-search-materialization/` | `beautyq-search-materialization` |
| `beautyqSearchWiring` | `beautyq-search-wiring/` | `beautyq-search-wiring` |

Each module contains a single boring placeholder object
(`leaderboard.<package>.ModulePlaceholder`) only so the module has a source
root; no domain code was moved or added.

## Phase 3 record: generic repo/catalog core moved

Moved from `bifunctor-tagless/src/main/scala/leaderboard/repo/` to
`repo-core/src/main/scala/leaderboard/repo/`, package name preserved
(`leaderboard.repo`):

- `RepoEntity.scala` (`RepoEntity`, `RepoField`, `EntityNode` construction)
- `RepoGraph.scala` (`EntityNode`, `Relation`, `GraphLoading`, the catalog
  declaration DSL, `CatalogEntity`/`CatalogValue` evidence, materialization,
  `TupleSelect`)
- `RepoNaming.scala` (`RepoNamingStrategy`)
- `RepoOp.scala` (`RepoOp` load-shape wrappers)
- `CatalogEntityDerivation.scala` (`CatalogEntity.derived` macro helper)

`GraphLoadingSpec.scala` (purely generic, no BeautyQ dependency) moved with
them to `repo-core/src/test/scala/leaderboard/repo/`. `RepoFieldRelationSpec`
and `BeautyQRepoGraphLoaderSpec` stayed in `bifunctor-tagless` since they
exercise BeautyQ-specific repos (`Categories`, `Services`,
`BeautyQCatalogGraph`, etc.).

The placeholder `leaderboard.repo.core.ModulePlaceholder` (Phase 2 skeleton)
was removed since `repo-core` now has real source.

Not moved (BeautyQ-specific repo companions, stay in `bifunctor-tagless`):
`Categories.scala`, `Services.scala`, `Masters.scala`, `MasterLocations.scala`,
`MasterServiceOffers.scala`, `MasterServiceOfferVariants.scala`,
`MasterServiceOfferVariantAttributesRepository.scala`, `Ladder.scala`,
`Profiles.scala`, `ServiceVariantSchemas.scala`, `BeautyQCatalogGraph.scala`,
and `package.scala` (defines `rootCategoryIdSqlLiteral`, which references the
BeautyQ `Category` model).

Build changes: `repoCore` gained `Deps.distageCore` (for
`izumi.functional.bio`, already used by the moved `GraphLoading`/`RepoOp`
code) and a `dependsOn(leaderboard-core)` (for `QueryFailure`, already
generic); test scope gained `Deps.zio % Test` and `Deps.scalatest % Test` for
the moved spec. `bifunctor-tagless` gained `dependsOn(repoCore)`. No
compatibility export was needed - the package name was preserved, so no
imports elsewhere needed changes.

## Migration phases

- **Phase 0** — docs/architecture freeze. Add this plan. No Scala/build behavior changes.
- **Phase 1** — dead compatibility cleanup. Remove zero-usage legacy symbols only. No module movement. No `SearchDomainSpec` implementation.
- **Phase 2** — build/module skeleton. Create module shells only. No behavior movement unless explicitly scoped.
- **Phase 3** — move generic repo/catalog core. Move generic primitives to `repo-core`. Add compatibility exports only if necessary and temporary.
- **Phase 4** — create `search-contract-core`. Introduce generic contract ADTs/types only. No BeautyQ runtime semantics.
- **Phase 5** — move BeautyQ catalog section. Move pure catalog section into `beautyq-search-contract`. Label it as catalog section, not full contract.
- **Phase 6** — move BeautyQ repositories. Move repo companions and dependency bundles into `beautyq-search-repositories`.
- **Phase 7** — move materialization. Move repo-backed catalog/document materializers into `beautyq-search-materialization`.
- **Phase 8** — fill full BeautyQ `SearchDomainSpec`. Add document/intent/runtime/response/evaluation sections. Still no client/runtime behavior changes unless explicitly scoped.
- **Phase 9** — migrate interpreters to contract slices. ES reads document/runtime/intent sections. Qdrant reads semantic/runtime/payload sections. Response assembler reads response/document sections. One interpreter function per patch.
- **Phase 10** — wiring module. Compose contract + repos + materializers + backend interpreters.
- **Phase 11** — delete legacy wrappers. Delete old facades only after usages reach zero.

## Patch types allowed

Every future patch must be exactly one of:

- inventory/docs-only
- module skeleton only
- move generic code only
- move BeautyQ contract only
- move repositories only
- move materialization only
- migrate one consumer/interpreter only
- remove one legacy surface only

Explicitly forbidden patch type:

```text
module split + DSL redesign + legacy deletion + behavior change + test rewrite
```

## Anti-scope-drift rules

- Every patch must state its phase.
- Every patch must state allowed files.
- Every patch must state forbidden files.
- Every patch must state whether behavior may change.
- Every patch must state whether compatibility may be removed.
- Every patch must not create review bundles/zips unless explicitly requested.
- Delegated agents must not run full `sbt test` unless explicitly requested.
- Focused tests only.
- No "FULL GREEN" claim unless user manually reports full suite green.
- Do not call catalog tree "full business search contract".
- Do not move runtime dependencies into contract to make derivation easier.
- Do not let ES/Qdrant interpreters invent BeautyQ semantics locally.
- Do not hide boilerplate below a pretty DSL and claim it is gone.
- Do not keep legacy wrappers without a removal condition.
- Do not delete legacy wrappers without usage audit.

## Stop conditions

- `BLOCKED_SCOPE_DRIFT`
- `BLOCKED_MODULE_CYCLE`
- `BLOCKED_FORBIDDEN_DEPENDENCY`
- `BLOCKED_COMPATIBILITY_BREAK`
- `BLOCKED_BEHAVIOR_CHANGE_REQUIRED`
- `BLOCKED_LEGACY_USAGE_UNCLEAR`
- `BLOCKED_CONTRACT_IMPORTS_RUNTIME_DEPS`
- `BLOCKED_INTERPRETER_IMPORTS_BEAUTYQ_REPOS`
- `BLOCKED_CATALOG_TREE_MISREPRESENTED_AS_FULL_CONTRACT`

## Minimum readiness gate before implementing full `SearchDomainSpec`

Full `SearchDomainSpec` work must not begin until:

1. This plan exists.
2. Forbidden dependencies are documented.
3. Legacy compatibility matrix exists.
4. Catalog tree is explicitly labeled as catalog section.
5. There is a chosen module/package owner for: contract, repositories, materialization, interpreters, wiring.
6. At least one dependency boundary is enforced by module dependencies or documented as pending.
