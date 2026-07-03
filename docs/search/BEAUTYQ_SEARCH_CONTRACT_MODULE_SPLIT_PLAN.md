# BeautyQ Search Contract Module Split Plan

Status: Phase 10a (first `beautyq-search-wiring` slice) and Phase 8c (`BeautyQSearchDomainContract` thin aggregate) recorded. `BeautySearchServingGate` lives in `beautyq-search-wiring`; `BeautyQSearchDomainContract` aggregates existing catalog/document/intent/runtime/response contract slices but is explicitly not a full `SearchDomainSpec` (no evaluation section, no generic `SearchDomainSpec[...]` value yet); `BeautySearchApi`, plugin/route modules, launcher modules, clients, bootstrap/seed code, and materialization remain in `bifunctor-tagless` pending deeper wiring/plugin movement.

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
  -> beautyq-search-contract   # added Phase 5: the pure catalog declaration
                                # is built with repo-core's generic catalog
                                # DSL (`catalog`, `.branch`, `.rootTree`,
                                # `.child`/`.value`), not re-derived locally.

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

Note (added Phase 8a/8b): `search-core` is an **existing** generic search
DSL/runtime-spec module (not one of the 10 new BeautyQ split modules listed
above) that already housed `SearchIntentVocabulary`, `SearchIntentRule`,
`SearchFieldSemantic`, `SearchField`, `SearchDocumentSpec`, `SearchQuerySchema`,
`SearchRuntimeSpec`, and related generic types before this split began.
`beautyq-search-contract` may depend on it - and does, since Phase 8a - for
those generic types; `search-core` itself depends only on `leaderboard-core`,
so this does not introduce any dependency on `bifunctor-tagless`,
repositories, materialization, ES/Qdrant, HTTP/app, wiring, clients, or
routes.

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

## Phase 4 record: generic search-contract-core ADTs added

Created in `search-contract-core/src/main/scala/leaderboard/search/contract/`
(package `leaderboard.search.contract`, dropping the Phase 2 placeholder's
`.core` suffix per this phase's package guidance):

- `SearchDomainSpec.scala` — `SearchDomainId`, `SearchLanguage`,
  `SearchVocabularyId`, `SearchBackendId`; `CatalogSection[Catalog]`;
  `SearchVocabulary`/`SearchVocabularyGroup`/`NoiseControl`/`IntentSection`;
  `DocumentSection[Document, ResultUnit]`;
  `GroupingPolicy`/`CarouselPolicy`/`FacetResponsePolicy`/`InferredFilterPolicy`/
  `PresentationMetadata`/`DebugPolicy`/`ResponseSection`;
  `EvalQueryRole`/`EvalBackendExpectation`/`EvalScorecardConfig`/
  `EvalProductionRoutingEffect`/`EvalSection`; the top-level
  `SearchDomainSpec[Catalog, Document, ResultUnit]`.
- `SearchField.scala` — `SearchFieldName`, `SearchFieldKind` (Text, Keyword,
  Facet, Numeric, Range, Geo, SemanticText), `SearchField`.
- `SearchRuntimeDeclaration.scala` — `SearchBackendKind` (Elasticsearch,
  Qdrant, InMemory), `SearchBackendCapabilities`, `SearchRuntimeDeclaration`,
  `RuntimeSection`.

All types are pure data (case classes/enums); no clients, effects, HTTP,
ES/Qdrant concrete classes, repository loaders, materialization code, or
BeautyQ model imports. `EvalSection.productionRoutingEffect` is typed as the
single-inhabitant `EvalProductionRoutingEffect` (not a boolean flag), so an
evaluation declaration can never claim to activate production routing.

Test: `SearchDomainSpecSpec.scala` builds a minimal generic
`SearchDomainSpec` from fixture types only (no BeautyQ values), and checks
section data preservation, declarative ES+Qdrant runtime declarations, and
the eval non-production-routing marker.

Build changes: `searchContractCore` gained `Deps.scalatest % Test` only (no
main-scope dependency added, no new dependency edges). It remains standalone:
no `repoCore`, no BeautyQ/ES/Qdrant/HTTP/app module dependency, matching the
documented DAG (`search-contract-core` has no edge to `repo-core`).

The Phase 2 placeholder `leaderboard.search.contract.core.ModulePlaceholder`
was removed since the module now has real source.

## Phase 5 prerequisite record: BeautyQ model moved into beautyq-model

Moving the BeautyQ catalog section into `beautyq-search-contract` would have
forced it to depend on `bifunctor-tagless` (the only place the BeautyQ model
types lived), reintroducing the monolith boundary the split is meant to
remove. This prerequisite step moves the model out first.

Moved from `bifunctor-tagless/src/main/scala/leaderboard/model/` to
`beautyq-model/src/main/scala/leaderboard/model/`, package name preserved
(`leaderboard.model`). The entire package moved as one unit rather than a
per-file subset: `leaderboard.model`'s `package.scala` is a Scala package
object, and a package can have only one package object on the classpath, so
splitting it across two modules that share a classpath (`bifunctor-tagless`
depends on `beautyq-model`) is not possible without a duplicate-class
conflict. Every file in the package was inspected and confirmed pure
model/data with no repository, materialization, search, or runtime import:

- `package.scala` — `UserId`/`ServiceId`/`MasterId`/`MasterLocationId`/
  `MasterServiceOfferId`/`MasterServiceOfferVariantId`/`Score`/`AttributeMap`
  type aliases; `Category`, `Service`, `Master`, `MasterServiceOffer`,
  `MasterLocation` case classes + circe codecs; `Category.rootCategoryId`.
- `AttributeDefinition.scala` — attribute definition ADT and the full BeautyQ
  attribute registry (int/decimal/boolean/enum definitions).
- `AttributeMap.scala` — generic attribute value map + `AttributeValueName`.
- `CodedEnumValue.scala` — `CodedEnumValue` + all coded enum types (nail/hair/
  lash/brow/pmu/facial/body attribute value enums).
- `MasterServiceOfferVariant.scala` — the variant model, its validation, and
  circe codec.
- `MasterServiceOfferVariantAttributes.scala` — variant attribute bag +
  `MasterServiceOfferVariantValidationError`.
- `ServiceVariantSchema.scala` — schema/item model + validation error.
- `UserProfile.scala` — `UserProfile`/`RankedProfile` + circe codecs.

Not moved: `Categories.scala`, `Services.scala`, `Masters.scala`,
`MasterLocations.scala`, `MasterServiceOffers.scala`,
`MasterServiceOfferVariants.scala`, `MasterServiceOfferVariantAttributesRepository.scala`,
`ServiceVariantSchemas.scala`, `BeautyQCatalogGraph.scala`, `Ladder.scala`,
`Profiles.scala`, and `leaderboard.repo`'s `package.scala` — all repository/
data-access/catalog-declaration code, staying in `bifunctor-tagless` per this
phase's scope. Tests `AttributeDefinitionSpec` and `CodedEnumValueSpec`
(under `leaderboard.attributes`) stayed in `bifunctor-tagless` too: they
extend `LeaderboardTest`/`VariantTestFixtures`, `bifunctor-tagless`-local
distage-testkit fixtures, not purely generic tests.

Build changes: `beautyqModel` gained `Deps.circeGeneric` (main scope, for the
model types' circe codecs - the same dependency `search-core` already uses
generically) and no other dependency. `bifunctor-tagless` gained
`dependsOn(beautyqModel)`. No compatibility export was needed - the package
name was preserved, so no imports elsewhere needed changes.

## Phase 5 record: BeautyQ catalog topology section moved into beautyq-search-contract

**Explicitly not the full BeautyQ search contract** - this is the catalog
topology section only: which BeautyQ types exist and how they join (roots,
self-trees, has-many/has-value edges). Document, intent, runtime, response,
and evaluation sections do not exist yet. `BeautyQSearchCatalogSection.label`
states this in code (`"catalog topology section, not complete search
contract"`) so the scope claim is assertable, not just a comment.

Moved: the pure `val declaration = catalog("beautyq")...` chain, previously
inlined in `bifunctor-tagless`'s `leaderboard.repo.BeautyQCatalogGraph`
(package `leaderboard.repo`), into a new file and a new package:

- `beautyq-search-contract/src/main/scala/leaderboard/search/beautyq/contract/BeautyQCatalogDeclaration.scala`,
  package `leaderboard.search.beautyq.contract` (not `leaderboard.repo` -
  this is a genuinely new contract-owned home, not a relocation-in-place, so
  there was no broad-churn reason to preserve the old package).

Added (not moved - new code): `BeautyQSearchCatalogSection.scala` in the same
package, wrapping the declaration in `search-contract-core`'s generic
`CatalogSection[Catalog]` ADT (`BeautyQCatalogSection.catalogTopology` /
`.section`), plus the `label` constant above.

`BeautyQCatalogGraph.scala` (still in `bifunctor-tagless`, still owning all
repository/materialization/evidence wiring - `Repositories`, `Evidence`,
`Graph`, `Relations`, `Nodes`) now imports
`leaderboard.search.beautyq.contract.BeautyQCatalogDeclaration` and
materializes `BeautyQCatalogDeclaration.declaration` instead of a
locally-defined value. Its doc comments already referenced
`BeautyQCatalogDeclaration.declaration` (written ahead of this move), so no
comment rewrite was needed beyond noting the new module boundary.

Build changes:

- `beautyqSearchContract` gained `dependsOn(repoCore)` - the catalog
  declaration is built with `repo-core`'s generic catalog DSL (`catalog`,
  `.branch`, `.rootTree`/`.rootAll`, `.child`/`.value`), so this contract
  module needs the DSL, not just the ADTs in `search-contract-core`. This is
  a direct edge the original DAG sketch did not spell out; the DAG above now
  shows it explicitly. `beautyqSearchContract` still depends on nothing else
  (no `bifunctor-tagless`, no repositories/materialization/wiring, no ES/Qdrant,
  no HTTP/app) - `repoCore` itself has no such dependencies either, so this
  does not smuggle in anything forbidden.
- `beautyqSearchContract` gained `Deps.scalatest % Test` for the new spec.
- `bifunctor-tagless` gained `dependsOn(beautyqSearchContract)` -
  temporary, per this phase's instructions, so `BeautyQCatalogGraph` can
  import the moved declaration until repositories/materialization/wiring
  move in later phases and take over that consumption.

No cycle: `repoCore` and `beautyqSearchContract` do not depend back on
`bifunctor-tagless`, `beautyq-search-repositories`, or
`beautyq-search-materialization`.

Tests: added `BeautyQCatalogDeclarationSpec.scala` in `beautyq-search-contract`
(11 cases) asserting the declaration's `.name`/`.steps` match the same
catalog topology previously asserted only via post-materialization
`BeautyQCatalogGraph.graph[IO].steps` in `bifunctor-tagless` - proving the
topology is fully expressed pre-materialization, with no repository loader
or `F`/repositories type required. Existing `RepoFieldRelationSpec` (19
cases) and `BeautyQRepoGraphLoaderSpec` (10 cases) needed no changes -
neither referenced the relocated `declaration` symbol directly - and both
still pass unchanged.

## Phase 6 record: BeautyQ repositories moved into beautyq-search-repositories

A first Phase 6 attempt stopped at preflight: every non-trivial repository
file imports `leaderboard.sql.SQL` and `leaderboard.runtime.QueryFailureToThrowable`,
both of which lived in `bifunctor-tagless` outside `leaderboard.repo`, so
moving repositories without them would force a forbidden/cyclic
`beautyq-search-repositories -> bifunctor-tagless` edge. A second attempt
found `QueryFailureToThrowable` is *not* repository-scoped: it's also used by
`leaderboard.search.startup.BeautyQManagedLocalSearchBootstrap` (ES/Qdrant
search bootstrap) and `leaderboard.seed.BeautyQSeedInserter` (seed
orchestration) - both explicitly out of this split's repository/materialization
boundary - so it stopped again rather than misfile a cross-cutting helper
under a repositories-only module.

**Ownership decision** (this patch): `leaderboard.runtime.QueryFailureToThrowable`
moved to `leaderboard-core` - it only ever depended on `izumi.functional.bio.Error2`
(external) and `leaderboard.model.QueryFailure`, which `leaderboard-core`
already owns, so this is a natural home for a cross-cutting `QueryFailure`
bridge, not a new dependency edge. `leaderboard.sql.SQL` moved to
`beautyq-search-repositories` - its only repo-local dependency is the same
`QueryFailure`, and every one of its consumers is a repository file also
moving in this patch.

Moved, package preserved (`leaderboard.repo` / `leaderboard.sql` /
`leaderboard.runtime`):

- `leaderboard-core/src/main/scala/leaderboard/runtime/QueryFailureToThrowable.scala`
  (from `bifunctor-tagless`) - cross-cutting `QueryFailure`-to-`Throwable`
  bridge, API/behavior unchanged.
- `beautyq-search-repositories/src/main/scala/leaderboard/sql/SQL.scala`
  (from `bifunctor-tagless`) - the doobie/BIO SQL executor abstraction,
  API/behavior unchanged.
- `beautyq-search-repositories/src/main/scala/leaderboard/repo/`: `Categories.scala`,
  `Services.scala`, `Masters.scala`, `MasterLocations.scala`,
  `MasterServiceOffers.scala`, `MasterServiceOfferVariants.scala`,
  `MasterServiceOfferVariantAttributesRepository.scala`,
  `ServiceVariantSchemas.scala`, `Ladder.scala`, `Profiles.scala` - all
  BeautyQ repository/data-access companions, each independently verified to
  import no materialization/search-document/ES/Qdrant/HTTP code.
- `package.scala` (the `leaderboard.repo` package object, `rootCategoryIdSqlLiteral`)
  moved with `Categories.scala`, its only consumer, per the package-object
  rule: a package can have only one package object on a shared classpath, and
  this was the only `leaderboard.repo` package object anywhere in the repo
  (verified by search - `repo-core`'s generic files are plain objects/classes,
  never a package object), so moving it does not split or duplicate anything.

Not moved (materialization/contract-consumer, stays in `bifunctor-tagless`):
`BeautyQCatalogGraph.scala` in full - `Repositories`, `Evidence`, `Graph`,
`Relations`, `Nodes` all still live there, since they wire the moved
repositories to the moved catalog declaration and are explicitly out of this
phase's scope. `VariantSearchDocument.scala`, ES/Qdrant code, routes, and seed
data were untouched.

Tests: none moved. Every consumer of the moved repositories in
`bifunctor-tagless`'s test tree (`RepoFieldRelationSpec`,
`BeautyQRepoGraphLoaderSpec`, HTTP contract suites, seed/search integration
specs, etc.) references them by the same preserved package name, so they
resolve the moved classes via the new `bifunctor-tagless -> beautyqSearchRepositories`
project dependency with no import changes and needed no edits.

Build changes:

- `leaderboard-core` gained `Deps.distageCore` (for `izumi.functional.bio.Error2`,
  used by the relocated `QueryFailureToThrowable`) - the same minimal generic
  dependency `repo-core` already uses for the same reason.
- `beautyqSearchRepositories` gained `Deps.distageCore` (`distage.Lifecycle`,
  `izumi.functional.bio`), `Deps.doobie` + `Deps.doobiePostgres` (repository
  SQL/doobie code), `Deps.catsCore` (`cats.data.NonEmptyList`), and
  `Deps.logstageSlf4j` (`logstage.LogIO2`) - all pre-existing dependency
  aliases already used elsewhere in the repo for the same libraries, and
  `dependsOn(leaderboard-core, repoCore, beautyqModel)`.
- `bifunctor-tagless` gained `dependsOn(beautyqSearchRepositories)`.

No cycle: `leaderboard-core` and `beautyqSearchRepositories` do not depend
back on `bifunctor-tagless`, `beautyqSearchMaterialization`,
`beautyqSearchWiring`, `search-elasticsearch`, or `search-qdrant`. No
compatibility export was needed - package names were preserved throughout, so
no consumer needed an import change.

## Phase 7 record: catalog-materialization slice moved into beautyq-search-materialization

This slice moves only the BeautyQ **catalog** materialization boundary -
`BeautyQCatalogGraph.scala` (`Repositories`, `Evidence`, `Graph`, `Relations`,
`Nodes`, `graph[F]`) - not document materialization. Source inspection
confirmed the document side is not a pure materialization move and would mix
this phase with later contract work: `BeautyQVariantSearchDocumentSchema.scala`
mixes `documentSpec`/`qdrantPayloadSpec`/`querySchema`/`projection`/`project`/
`buildDocument` and depends on `leaderboard.search.dsl` BeautyQ search
semantics, and `VariantSearchDocument.scala` has seed-scoped loader pieces
depending on `leaderboard.seed.BeautyQSeedData`/`BeautyQSeedReady`. Document
materialization remains pending for a dedicated later slice.

Moved, package preserved (`leaderboard.repo`):

- `beautyq-search-materialization/src/main/scala/leaderboard/repo/BeautyQCatalogGraph.scala`
  (from `bifunctor-tagless`) - `Repositories[F]` (the repo dependency bundle),
  `Evidence` (`CatalogEntity`/`CatalogValue`/`CatalogRootTree`/`CatalogRootAll`/
  `CatalogMany`/`CatalogValueEdge` given instances wiring BeautyQ repositories
  to the generic catalog DSL), `Graph[F]`/`Graph.fromDeclaration` (the
  materialized, BeautyQ-named relation bundle), `Nodes` (typed graph nodes for
  external callers such as `BeautyQVariantSearchDocumentSchema`), `Relations[F]`
  (eager relation wrapper against a concrete `Repositories[F]`), and `graph[F]`
  (materializes `BeautyQCatalogDeclaration.declaration`, already living in
  `beautyq-search-contract`, against `Evidence`).

Not moved: `BeautyQVariantSearchDocumentSchema.scala`, `VariantSearchDocument.scala`,
`SearchDocumentProjection.scala`, `VariantSearchDocumentSnapshotProvider.scala`,
`BeautySearchReadyCatalogDocuments.scala`, seed files, ES/Qdrant code,
plugin/wiring/route code - all still in `bifunctor-tagless`, consuming the
moved `BeautyQCatalogGraph` via the new project dependency below.

Build changes: no new library dependency was needed -
`beautyqSearchMaterialization`'s existing `dependsOn(beautyqSearchContract,
beautyqSearchRepositories, repoCore, beautyqModel)` already covered every
symbol `BeautyQCatalogGraph.scala` uses (BeautyQ model types, BeautyQ repo
companions, the generic catalog DSL/evidence typeclasses, and
`BeautyQCatalogDeclaration`). `bifunctor-tagless` gained
`dependsOn(beautyqSearchMaterialization)` so its existing consumers
(`RepoFieldRelationSpec`, `BeautyQRepoGraphLoaderSpec`,
`BeautyQVariantSearchDocumentSchema.scala`, etc.) resolve
`leaderboard.repo.BeautyQCatalogGraph` unchanged - no import edits needed
anywhere. No compatibility export was needed.

No cycle: `beautyqSearchMaterialization` does not depend back on
`bifunctor-tagless`, ES/Qdrant, HTTP/app, wiring, clients, or routes.

## Phase 8a record: intent/field-semantics contract slice moved into beautyq-search-contract

This slice moves only three pure BeautyQ intent/constraint/field-semantics
files - not full `SearchDomainSpec`, and not document/presentation/runtime/
evaluation content. Source inspection confirmed those remain entangled with
non-contract code: `BeautySearchSpec.scala`/`BeautySearchSpecV1.scala` import
`VariantSearchDocument`, `BeautyQSearchPresentation.scala` imports
`VariantSearchDocument` and holds response helper methods,
`BeautyQVariantSearchDocumentSchema.scala` mixes contract-shaped
`documentSpec`/`qdrantPayloadSpec`/`querySchema` with
`projection`/`project`/`buildDocument` and imports `BeautyQCatalogGraph` plus
`ServiceVariantSchemas`, and `VariantSearchDocument.scala` mixes the document
model with `BeautySearchCatalogSnapshot`, repo-backed loaders, and
seed-scoped loaders. None of those moved.

Moved, package preserved (`leaderboard.search.dsl`):

- `beautyq-search-contract/src/main/scala/leaderboard/search/dsl/SearchConstraint.scala`
  (from `bifunctor-tagless`) - the `SearchConstraint` ADT (`ServiceAny`,
  `CategoryAny`, `EnumAttr`, `BoolAttr`, `IntRange`, `DecimalRange`,
  `PriceRange`, `DurationRange`, `NearUser`) and its circe codec. Only
  imports circe.
- `beautyq-search-contract/src/main/scala/leaderboard/search/dsl/BeautyQSearchIntentVocabulary.scala`
  (from `bifunctor-tagless`) - the pure BeautyQ intent vocabulary (structured
  aliases and noise phrases over `SearchIntentVocabulary[SearchConstraint]`).
  No imports at all - it resolves `SearchIntentVocabulary`/`SearchIntentRule`
  (generic search DSL types, `search-core`) and `SearchConstraint` (moved
  above) via same-package resolution, since all three share package
  `leaderboard.search.dsl` across modules.
- `beautyq-search-contract/src/main/scala/leaderboard/search/dsl/BeautyQSearchFieldSemantics.scala`
  (from `bifunctor-tagless`) - pure BeautyQ field-semantic name constants
  over generic `SearchFieldSemantic` (`search-core`), same same-package
  resolution.

Tests: added `BeautyQIntentContractSpec.scala` (8 cases) in
`beautyq-search-contract` proving the vocabulary is non-empty and contains
known BeautyQ tokens (`"маникюр"`, `"lashes"`), `SearchConstraint` round-trips
`ServiceAny`/`EnumAttr`/`PriceRange`/`NearUser` through its circe codec, and
`BeautyQSearchFieldSemantics` derives the expected `enumAttributes.x`/
`booleanAttributes.x` paths - all without any repository or materialization
dependency in scope.

Build changes: `beautyqSearchContract` gained `dependsOn(search-core)` -
`BeautyQSearchIntentVocabulary`/`BeautyQSearchFieldSemantics` use
`SearchIntentVocabulary`/`SearchIntentRule`/`SearchFieldSemantic`, generic
search DSL types owned by `search-core` - and `Deps.circeGeneric` (main
scope) for `SearchConstraint`'s manual circe codec, the same alias
`beautyqModel` already uses for the same reason. No compatibility export was
needed - package names were preserved, so the many existing
`bifunctor-tagless` consumers of these three files (parser, hybrid policy,
response assembler, document schema, eval schema, and their tests) needed no
import changes; they resolve them via the existing
`bifunctor-tagless -> beautyqSearchContract` dependency.

No cycle: `search-core` depends only on `leaderboard-core`, so it does not
depend back on `beautyqSearchContract`, `bifunctor-tagless`, repositories, or
materialization. `beautyqSearchContract` still has no dependency on
`bifunctor-tagless`, repositories, materialization, ES/Qdrant, HTTP/app,
wiring, clients, or routes.

## Phase 8b record: document/runtime/response contract slice moved into beautyq-search-contract

This slice moves the pure BeautyQ document model and document/query contract
declarations, plus the BeautyQ runtime-spec/presentation aggregates built on
top of them - still not full `SearchDomainSpec`, and still not evaluation
content or ES/Qdrant interpreters (Phase 9). Source inspection confirmed
`VariantSearchDocument.scala` and `BeautyQVariantSearchDocumentSchema.scala`
each mixed pure contract shape with repo-backed/seed-scoped materialization,
so each was split rather than moved whole.

**New file, not a duplicate object** - naming rule followed: the existing
`leaderboard.repo`/materialization-facing object name
`BeautyQVariantSearchDocumentSchema` stays exactly where it was
(`bifunctor-tagless`), so every existing consumer (~40 production/test files
across parser, hybrid policy, response assembler, eval schema, benchmark
executors, and their tests) keeps compiling with zero import changes. The
contract-owned declarations moved into a **new**, differently-named object,
`BeautyQVariantSearchDocumentContract`, and the old object now delegates to
it - there is exactly one `BeautyQVariantSearchDocumentSchema` object on the
classpath, in `bifunctor-tagless`.

Moved/created, package preserved (`leaderboard.search.document` /
`leaderboard.search.dsl`):

- `beautyq-search-contract/src/main/scala/leaderboard/search/document/VariantSearchDocument.scala`
  (new file, split out of `bifunctor-tagless`'s `VariantSearchDocument.scala`)
  - the pure `VariantSearchDocument` case class and its circe codecs only.
  `BeautySearchCatalogSnapshot`, `VariantSearchDocumentBuilder`, and
  `BeautySearchCatalogSnapshotLoader` (`FromRepositories`,
  `SeedScopedFromRepositories`) stayed in `bifunctor-tagless`'s
  `VariantSearchDocument.scala`, which was edited to drop only the moved
  case class/companion - they depend on `BeautyQCatalogGraph`,
  `GraphLoading`, repo companions, and `leaderboard.seed.BeautyQSeedData`/
  `BeautyQSeedReady`, none of which belong in the contract.
- `beautyq-search-contract/src/main/scala/leaderboard/search/document/BeautyQVariantSearchDocumentContract.scala`
  (new file) - `Fields` (all `SearchField[VariantSearchDocument]`
  declarations), `documentSpec`, `qdrantPayloadSpec`, `querySchema`, and the
  constraint/facet resolution helpers `querySchema` needs
  (`beautyQResolveConstraint`, `beautyQFacetConstraint`, `rangeConstraint`,
  `fieldByCode`) - split out of `bifunctor-tagless`'s
  `BeautyQVariantSearchDocumentSchema.scala`. Imports only `leaderboard.model`
  (beautyq-model), `leaderboard.search.dsl` (search-core generics +
  `SearchConstraint`/`BeautyQSearchFieldSemantics`/`BeautyQSearchPresentation`,
  all now in this same module). No `BeautyQCatalogGraph`, `ServiceVariantSchemas`,
  repositories, materialization, seed, ES/Qdrant, HTTP/app, clients, or
  routes.
- `beautyq-search-contract/src/main/scala/leaderboard/search/dsl/BeautyQSearchPresentation.scala`,
  `BeautySearchSpec.scala`, `BeautySearchSpecV1.scala` (moved from
  `bifunctor-tagless`) - response/carousel/ranking presentation helpers and
  the BeautyQ runtime-spec aggregates. `BeautySearchSpecV1.scala` was edited
  to reference `BeautyQVariantSearchDocumentContract` (`Fields`,
  `documentSpec`, `qdrantPayloadSpec`, `querySchema`) instead of
  `BeautyQVariantSearchDocumentSchema`, since it now lives in the contract
  module and must not depend on `bifunctor-tagless`.

`bifunctor-tagless`'s `BeautyQVariantSearchDocumentSchema.scala` now only
keeps `Repositories`-adjacent materialization: node handles from
`BeautyQCatalogGraph.Nodes`, `projection`/`project`/`buildDocument`/
`validateAgainstSchema`/`makeAttributeTokens`/`humanize`/`normalizeText`, plus
delegating vals (`val Fields = BeautyQVariantSearchDocumentContract.Fields`,
`lazy val documentSpec = BeautyQVariantSearchDocumentContract.documentSpec`,
etc.) for source compatibility - exactly the shape this phase's task
description specified.

Build changes: none. `beautyqSearchContract` already depended on
`search-core`, `searchContractCore`, `beautyqModel`, and `repoCore` (from
Phases 4/5/8a), which covers every symbol the moved/new files use; no new
dependency edge was needed, and `bifunctor-tagless` already depended on
`beautyqSearchContract` (from Phase 5), so every existing consumer resolves
the moved symbols with zero import changes.

Tests: added `BeautyQDocumentContractSpec.scala` (9 cases) in
`beautyq-search-contract` proving `VariantSearchDocument` constructs with
representative values; `documentSpec`/`qdrantPayloadSpec` declare the
expected index/paths; `querySchema.resolve` resolves `ServiceAny` to a terms
constraint on `serviceName`, `PriceRange` to a range constraint on
`priceFrom`, and `NearUser` to a geo-distance constraint on `location`;
`BeautySearchSpecV1.runtimeSpec.payloadSpecs` carries the Qdrant payload
spec; and `BeautyQSearchPresentation.carouselSpec` exposes provider/service-
intent groups built from contract fields - all without any repository or
materialization dependency in scope. Existing
`BeautyQVariantSearchDocumentSchemaSpec` (19 cases), `SearchRuntimeSpecSpec`
(2 cases), `ElasticsearchBudgetRangeRequestSpec` (2 cases), and
`BeautySearchBudgetIntentParserSpec` (9 cases) needed no changes and still
pass unchanged.

No cycle: `beautyqSearchContract` still has no dependency on
`bifunctor-tagless`, repositories, materialization, ES/Qdrant, HTTP/app,
wiring, clients, or routes.

## Phase 9a record: Qdrant/semantic consumers migrated to BeautyQVariantSearchDocumentContract

A consumer-migration-only slice of Phase 9 ("migrate interpreters to
contract slices"): no files moved, no `search-qdrant` (generic interpreter)
edits, no ES interpreter edits, no response assembler edits, no
materialization/projection edits. Three `bifunctor-tagless` Qdrant/hybrid
consumers that only ever read the contract-shaped members of
`BeautyQVariantSearchDocumentSchema` (never `projection`/`project`/
`buildDocument`) were migrated to read `BeautyQVariantSearchDocumentContract`
(in `beautyq-search-contract`) directly instead:

- `leaderboard.search.qdrant.QdrantVariantDocumentPointBuilder` - now uses
  `BeautyQVariantSearchDocumentContract.qdrantPayloadSpec` and
  `.documentSpec.id(document)`.
- `leaderboard.search.qdrant.QdrantEmbeddingBenchmarkCandidateExecutor` - now
  uses `BeautyQVariantSearchDocumentContract.Fields.{variantId, serviceText,
  attributeText, allText, categoryName}`.
- `leaderboard.search.hybrid.BeautyQNonProductionHybridRunnerManualAdapterInputs` -
  now uses `BeautyQVariantSearchDocumentContract.Fields.variantId`.

`BeautyQVariantSearchDocumentSchema` itself was not edited - it remains the
materialization/projection compatibility surface in `bifunctor-tagless`
(`projection`/`project`/`buildDocument`/validation/text-building), still
delegating its contract-shaped members from `BeautyQVariantSearchDocumentContract`
for the consumers that still go through it. No behavior changed: each
replacement reads the exact same underlying values (the schema's delegating
`val`/`lazy val` already forwarded to the contract object 1:1), so payload
JSON, point IDs, embedding source fields, and hybrid wiring are byte-for-byte
identical to before.

Not migrated in this slice (Phase 9 remainder): ES interpreter, response
assembler, generic `search-qdrant` builders (which already consume generic
`SearchDocumentPayloadSpec`/`SearchDocumentSpec`, not BeautyQ-specific
types), and any other `bifunctor-tagless` code still reading
`BeautyQVariantSearchDocumentSchema`. No production route activation and no
Qdrant/ES/hybrid runtime behavior change.

## Phase 9b record: remaining Qdrant managed-local/runtime/startup consumers migrated

Continues the Phase 9 consumer-migration-only slice, covering the three
remaining main-source Qdrant managed-local/runtime/startup consumers that
only ever read contract-shaped members of `BeautyQVariantSearchDocumentSchema`:

- `leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingModules` -
  now uses `BeautyQVariantSearchDocumentContract.Fields.variantId` when
  constructing `QdrantSemanticCandidateSearch`.
- `leaderboard.search.startup.BeautyQManagedLocalSearchBootstrapFingerprint` -
  now uses `BeautyQVariantSearchDocumentContract.qdrantPayloadSpec` in the
  runtime-spec payload map it fingerprints.
- `leaderboard.search.startup.BeautyQManagedLocalSearchBootstrap` - its
  `SourceTextFields`/`SourceTextFieldPaths` now derive from
  `BeautyQVariantSearchDocumentContract.Fields.{serviceText, attributeText,
  allText, categoryName}`, values exactly equivalent to before.

Source-truth check confirmed no ES interpreter patch and no response
assembler patch were needed in this slice: both were already contract-backed
as of Phase 8b (they consume `BeautySearchSpecV1`/`BeautyQSearchPresentation`,
already living in `beautyq-search-contract`), not
`BeautyQVariantSearchDocumentSchema` directly.

Intentionally not migrated (materialization/projection surface, not contract
consumers): `BeautySearchCatalogBackendModules.scala` and
`bifunctor-tagless`'s `VariantSearchDocument.scala` (its
`BeautySearchCatalogSnapshotLoader`/`VariantSearchDocumentBuilder`), plus
`BeautyQVariantSearchDocumentSchema`'s own `projection`/`project`/
`buildDocument`/seed refs - all still read the compatibility schema object,
as intended.

No files moved, no test edits needed (compile proved no test imported the
old schema symbols touched here), no behavior change: each replacement reads
the identical underlying value the schema's delegating `val`/`lazy val`
already forwarded from the contract object, so Qdrant collection bindings,
fingerprint inputs, and managed-bootstrap source-text field paths are
byte-for-byte identical to before. No production route activation. Phase 10
(wiring module assembly) remains separate and untouched.

## Phase 10a record: BeautySearchServingGate moved into beautyq-search-wiring

First `beautyq-search-wiring` slice: `BeautySearchServingGate` - a narrow,
disabled-by-default runtime serving gate (`enabled`/`servingReady` ->
`rejectsServing`) with zero imports, no HTTP/tapir/client/repository/
materialization dependency, and no production activation logic - moved from
`bifunctor-tagless` into `beautyq-search-wiring`, package preserved
(`leaderboard.api`):

- `beautyq-search-wiring/src/main/scala/leaderboard/api/BeautySearchServingGate.scala`
  (from `bifunctor-tagless`).

Not moved (still `bifunctor-tagless`, per this phase's scope):
`BeautySearchApi`, `BeautySearchPluginModules`, `BeautySearchRouteModules`,
`BeautySearchLocalQdrantSupplementLauncherModule`,
`BeautySearchQdrantSupplementActivation`, `BeautySearchCatalogBackendModules`,
`BeautySearchQdrantSupplementRuntimeBindingModules`, ES/Qdrant clients,
bootstrap/startup code, seed code, parser/backend/service code, and
repositories/materialization/projection - all still depend on app/http/
tapir/client/seed surfaces that stay in `bifunctor-tagless` for now. Deeper
plugin/route/module movement remains pending until those surfaces have their
own dedicated slices.

Build changes: `beautyqSearchWiring` gained `Deps.scalatest % Test` (for the
new spec) - no HTTP/tapir/client/repository/materialization dependency was
added, since the moved file needs none. `bifunctor-tagless` gained
`dependsOn(beautyqSearchWiring)`.

No cycle: `beautyqSearchWiring` depends only on `beautyqSearchContract`,
`beautyqSearchMaterialization`, `search-elasticsearch`, `search-qdrant` -
none of which depend on `bifunctor-tagless` - so it does not depend back on
`bifunctor-tagless`. The placeholder
`leaderboard.beautyq.search.wiring.ModulePlaceholder` was removed since the
module now has real source.

Tests: added `BeautySearchServingGateSpec.scala` (7 cases) in
`beautyq-search-wiring` covering `disabled`/`enabledNotReady`/`enabledReady`
and all four direct `enabled`/`servingReady` combinations. Package preserved
meant zero import changes anywhere in `bifunctor-tagless` (`BeautySearchApi`,
route/plugin/launcher modules, and their tests all still resolve
`leaderboard.api.BeautySearchServingGate` unchanged via the new project
dependency). No production route/fallback/fusion/rerank behavior changed.

## Phase 8c record: BeautyQSearchDomainContract thin aggregate added

By Phase 8b, `beautyq-search-contract` held several independently-owned
slices (catalog topology, document/query contract, intent vocabulary, and
the BeautyQ runtime/search spec + presentation) with no single place that
read them together - the catalog declaration in particular was visually
isolated from its siblings. This slice adds one coordinator-readable
aggregate object, `BeautyQSearchDomainContract`, that gathers references to
the sections that already exist. It adds no new contract content of its own
- every value is a direct `eq`-identical reference to an already-owned
section:

- `beautyq-search-contract/src/main/scala/leaderboard/search/beautyq/contract/BeautyQSearchDomainContract.scala`
  (new file) - `domainId` ("beautyq"), `label` (states explicitly it is not a
  complete `SearchDomainSpec`), `catalog` (-> `BeautyQCatalogSection.section`,
  itself wrapping `BeautyQCatalogDeclaration.declaration` unchanged),
  `document`/`qdrantPayload`/`query` (-> `BeautyQVariantSearchDocumentContract`),
  `intent` (-> `BeautyQSearchIntentVocabulary.vocabulary`), `searchSpec`/
  `runtime` (-> `BeautySearchSpecV1.spec`/`.runtimeSpec`), `carousel`/`facets`
  (-> `BeautySearchSpecV1.spec.carouselSpec`/`.facetSpec`),
  `evaluationDeclared = false`, `fullSearchDomainSpecDeclared = false`.

**Explicitly not a full `SearchDomainSpec`**: named `BeautyQSearchDomainContract`,
not `BeautyQSearchDomainSpec` - no generic `search-contract-core`
`SearchDomainSpec[...]` value is constructed here, and none was required to
express this aggregate. The current BeautyQ slices are still expressed
through `search-core`'s `SearchDocumentSpec`/`SearchQuerySchema`/
`SearchRuntimeSpec`/`CarouselSpec`/`FacetSpec` ADTs, not the generic
`search-contract-core` ones, and no evaluation section has moved into
`beautyq-search-contract` yet - both `evaluationDeclared` and
`fullSearchDomainSpecDeclared` are `false` by construction, asserted
directly rather than left to a comment. `BeautyQCatalogDeclaration` itself
was not touched; the aggregate only references it (via
`BeautyQCatalogSection`), it does not redeclare or wrap it a second time.

Imports are limited to `leaderboard.search.document` and
`leaderboard.search.dsl` (plus same-package references to
`BeautyQCatalogDeclaration`/`BeautyQCatalogSection`) - no
`leaderboard.repo.BeautyQCatalogGraph`, `BeautyQVariantSearchDocumentSchema`,
`BeautySearchCatalogSnapshot`, repositories, materialization, seed, ES/Qdrant
clients, HTTP/tapir routes, or bootstrap/wiring runtime services.

Tests: added `BeautyQSearchDomainContractSpec.scala` (15 cases) in
`beautyq-search-contract` asserting `domainId`, the `label` wording, `eq`-identity
of every aggregated value against its source section (including the same
seven catalog topology summaries `BeautyQCatalogDeclarationSpec` already
asserts), both `false` flags, and that the aggregate is fully usable without
constructing any repository/materializer/client. Existing
`BeautyQCatalogDeclarationSpec` (11 cases), `BeautyQDocumentContractSpec` (9
cases), and `BeautyQIntentContractSpec` (8 cases) needed no changes and
still pass unchanged.

Build changes: none - `build.sbt` was not touched. No behavior change: this
is a pure aggregation of existing values, with no repository/materialization/
ES/Qdrant/client/route/runtime code moved or imported.

## Migration phases

- **Phase 0** — docs/architecture freeze. Add this plan. No Scala/build behavior changes.
- **Phase 1** — dead compatibility cleanup. Remove zero-usage legacy symbols only. No module movement. No `SearchDomainSpec` implementation.
- **Phase 2** — build/module skeleton. Create module shells only. No behavior movement unless explicitly scoped.
- **Phase 3** — move generic repo/catalog core. Move generic primitives to `repo-core`. Add compatibility exports only if necessary and temporary.
- **Phase 4** — create `search-contract-core`. Introduce generic contract ADTs/types only. No BeautyQ runtime semantics.
- **Phase 5** — move BeautyQ catalog section. Move pure catalog section into `beautyq-search-contract`. Label it as catalog section, not full contract.
- **Phase 6** — move BeautyQ repositories. Move repo companions and dependency bundles into `beautyq-search-repositories`.
- **Phase 7** — move materialization. Move repo-backed catalog/document materializers into `beautyq-search-materialization`.
- **Phase 8** — fill BeautyQ contract slices and eventually the full `SearchDomainSpec`. Add document/intent/runtime/response/evaluation sections. Still no client/runtime behavior changes unless explicitly scoped.
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
