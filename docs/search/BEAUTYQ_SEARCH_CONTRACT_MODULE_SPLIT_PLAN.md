# BeautyQ Search Contract Module Split Plan

Status: Phase 7b (BeautyQ variant document projection engine slice), Phase 7c (BeautyQ catalog snapshot loaders slice), and Phase 7d (variant projection consumes materialization-owned catalog snapshots) recorded, alongside Phase 8d (static/offline evaluation contract slice), Phase 8e (full `SearchDomainSpec` readiness blockers made explicit via `BeautyQSearchDomainSpecReadiness`), Phase 8f (BeautyQ variant result unit declared via `BeautyQSearchResultUnitContract`, resolving the `document-result-unit` blocker), Phase 8g (BeautyQ supported search languages `de`/`en`/`ru` declared via `BeautyQSearchLanguageContract`, resolving the `intent-languages` blocker), Phase 8h (BeautyQ generic document fields declared via `BeautyQSearchDocumentFieldContract`, resolving the `document-field-kind-mapping` blocker), Phase 8i (BeautyQ runtime/response sections declared via `BeautyQSearchRuntimeContract`/`BeautyQSearchResponsePolicyContract`, resolving the `runtime-capabilities`/`response-policy` blockers), Phase 8j (BeautyQ generic intent section declared via `BeautyQSearchIntentSectionContract`, resolving the final `intent-section-mapping` blocker; full generic `SearchDomainSpec` now assembled), Phase 10b (Qdrant supplement activation/preflight policy slice), Phase 10a (first `beautyq-search-wiring` slice), and Phase 8c (`BeautyQSearchDomainContract` thin aggregate). `BeautyQVariantSearchDocumentMaterialization` (the actual projection engine) now lives in `beautyq-search-materialization`, alongside `BeautyQCatalogGraph`, and exposes both a seven-list `project(...)` and a snapshot-level `project(BeautyQSearchCatalogSnapshot)` overload; `BeautyQVariantSearchDocumentSchema` in `bifunctor-tagless` is now a thin compatibility facade delegating to the snapshot-level overload via `BeautySearchCatalogSnapshot.toMaterializationSnapshot`. The actual full-catalog and seed-scoped repo-backed snapshot loading algorithms (`BeautyQSearchCatalogSnapshotLoader.FromRepositories`/`SeedScopedFromRepositories`, over the seed-free `BeautyQSearchCatalogSnapshot`/`BeautyQSearchCatalogSeedScope`) now live in `beautyq-search-materialization` too; legacy `BeautySearchCatalogSnapshotLoader.FromRepositories`/`SeedScopedFromRepositories` in `bifunctor-tagless` are now compatibility facades delegating to them and converting back via the companion-owned `BeautySearchCatalogSnapshot.fromMaterializationSnapshot`, while legacy `BeautySearchCatalogSnapshot`/`fromSeedData` and `BeautyQSeedData`/`BeautyQSeedLoader`/`BeautyQSeedInserter`/`BeautyQSeedReady` still remain in `bifunctor-tagless`. `BeautySearchServingGate` and the Qdrant supplement activation state/config/preflight/command/diagnostics live in `beautyq-search-wiring`; `BeautyQSearchDomainContract` aggregates catalog/document/intent/runtime/response/evaluation contract slices and now assembles the full generic `SearchDomainSpec` from those contract-owned sections (`evaluationDeclared = true`, `fullSearchDomainSpecDeclared = true`); `BeautySearchApi`, route/plugin modules, launcher modules, clients, bootstrap/seed code, JSON/resource parsing, the eval runtime harness/backend runners, and repositories remain in `bifunctor-tagless` pending deeper movement.

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

## Phase 9c record: test/docs consumer cleanup for contract-shaped schema references

A test/docs cleanup slice, not a main-source consumer migration: in
`bifunctor-tagless` test files that only read the contract-shaped members of
`BeautyQVariantSearchDocumentSchema` (`Fields`/`documentSpec`/
`qdrantPayloadSpec`/`querySchema`, never `project`/`projection`/
`buildDocument`), those specific references now read
`BeautyQVariantSearchDocumentContract` directly:

- `BeautyQVariantSearchDocumentSchemaSpec.scala` - its `documentSpec`/
  `Fields`/`qdrantPayloadSpec`/`querySchema` assertions (and describe-block
  titles) now read `BeautyQVariantSearchDocumentContract`; its `project`
  assertions and helper methods still read `BeautyQVariantSearchDocumentSchema`,
  since those exercise projection/materialization behavior over
  `BeautySearchCatalogSnapshot`.
- `QdrantVariantDocumentPointBuilderSpec.scala` - only ever read
  `qdrantPayloadSpec`; migrated entirely to
  `BeautyQVariantSearchDocumentContract`.
- `QdrantEmbeddingBenchmarkExecutorSpec.scala` - its one `Fields.variantId`
  reference (a field handle passed to `QdrantSemanticCandidateSearch`, not a
  projection call) migrated to `BeautyQVariantSearchDocumentContract`.

No main-source production migration was made in this slice: the remaining
main-source `BeautyQVariantSearchDocumentSchema` usages are the
materialization/projection compatibility surface itself
(`projection`/`project`/`buildDocument`/validation/text-building,
`BeautySearchCatalogSnapshotLoader`/`VariantSearchDocumentBuilder`), which
Phase 9a/9b already established are not safe to bulk-migrate. Compatibility
facade deletion remains forbidden until a zero-usage audit confirms nothing -
main-source or test - still reads it. Many more `bifunctor-tagless` test
files still hold contract-shaped-only `BeautyQVariantSearchDocumentSchema`
references outside this slice's edited-file scope; they remain for a later,
separately-scoped cleanup pass.

No behavior change: each replacement reads the identical underlying value
(the schema's delegating `val`/`lazy val` already forwarded 1:1 from the
contract object). `build.sbt` untouched.

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

## Phase 10b record: Qdrant supplement activation/preflight policy split into beautyq-search-wiring

This slice splits the Qdrant supplement activation/preflight policy from
route/module selection, so the pure policy can live in `beautyq-search-wiring`
while the route wiring stays in `bifunctor-tagless`:

- Qdrant supplement activation state (`BeautySearchQdrantSupplementActivation`),
  operator-value config parsing (`BeautySearchQdrantSupplementActivationConfig`),
  pure preflight policy (`BeautySearchQdrantSupplementActivationPreflight`),
  the read-only real-resource preflight command
  (`BeautySearchQdrantSupplementActivationPreflightCommand`), and operator-facing
  diagnostics formatting (`BeautySearchQdrantSupplementActivationDiagnostics`)
  all moved into `beautyq-search-wiring`.
- Package preserved as `leaderboard.plugins` throughout.
- Route `distage.ModuleDef` selection remains in `bifunctor-tagless`, in a new
  `BeautySearchQdrantSupplementActivationModuleSelector` (`moduleFor`,
  `moduleForOperatorValue`), which composes the moved pure state/config with
  `BeautySearchRouteModules` and `BeautySearchServingGate` - both of which
  stay in `bifunctor-tagless`.
- `beautyq-search-wiring` still does not depend on `bifunctor-tagless`.
- No route modules were moved (`BeautySearchRouteModules` stays in
  `bifunctor-tagless`).
- No plugin modules were moved (`BeautySearchPluginModules`,
  `BeautySearchLocalQdrantSupplementLauncherModule`,
  `BeautySearchQdrantSupplementRuntimeBindingModules` stay in
  `bifunctor-tagless`).
- No HTTP/tapir handlers were moved.
- No ES/Qdrant concrete clients were moved.
- No bootstrap/startup/seed/repository/materialization/projection code was
  moved.
- No production route activation, fallback, fusion, rerank, or behavior
  change.
- Deeper route/plugin movement remains pending: `BeautySearchApi`,
  `BeautySearchRouteModules`, `BeautySearchPluginModules`, launcher/runtime
  binding modules, and HTTP/tapir wiring all still depend on app/http/parser/
  backend/client/seed surfaces that stay in `bifunctor-tagless` for now.

## Phase 10c record: pure Qdrant supplement route-selection policy extracted

Continues Phase 10b: the pure activation-to-route-selection policy moved into
`beautyq-search-wiring` as `BeautySearchQdrantSupplementRouteSelection` (an
`EsOnlyRollbackRoute`/`QdrantSupplementNotReadyRoute`/`QdrantSupplementReadyRoute`
ADT carrying `qdrantSupplementRouteSelected`/`servingGate`) and
`BeautySearchQdrantSupplementRouteSelectionPolicy` (`selectionFor`,
`selectionForOperatorValue`), package `leaderboard.plugins`.

`BeautySearchQdrantSupplementActivationModuleSelector` remains in
`bifunctor-tagless` as the `RouteSelection -> ModuleDef` interpreter only: its
new `moduleForRouteSelection` interprets a pure route selection into the
existing `BeautySearchRouteModules` module, and its existing public
`moduleFor`/`moduleForOperatorValue` methods now compose the moved policy with
that interpreter, unchanged in signature and behavior.

`BeautySearchApi`, `BeautySearchRouteModules`, `BeautySearchPluginModules`,
launcher/runtime-binding/catalog-backend modules, HTTP/Tapir handlers,
clients, and seed/bootstrap/startup code all remain in `bifunctor-tagless`,
untouched. No production route activation, fallback, fusion, rerank, default
route, or Qdrant opt-in behavior changed; `build.sbt` untouched.

## Phase 10d record: Qdrant supplement runtime-binding and launcher plans extracted

Continues Phase 10c: pure descriptions of the Qdrant supplement runtime
bindings and the managed-local launcher defaults moved into
`beautyq-search-wiring`, package `leaderboard.plugins`:

- `BeautySearchQdrantSupplementRuntimeBindingPlan` (`vectorSearchSpec`,
  `lexicalBackendBindingName = "qdrantSupplementLexicalElasticsearch"`,
  `variantIdField = BeautyQVariantSearchDocumentContract.Fields.variantId`).
- `BeautySearchLocalQdrantSupplementLauncherPlan` (`vectorSearchSpec`,
  `servingGate`, `runtimeBindingPlan`), with `VectorSpec` and `default`
  carrying the exact unchanged managed-local vector spec/serving-gate values.

`BeautySearchQdrantSupplementRuntimeBindingModules` remains in
`bifunctor-tagless` as the `RuntimeBindingPlan -> ModuleDef` interpreter: its
new `supplementRuntimeBindings(plan)` overload binds the same four runtime
bindings as before, and the existing `supplementRuntimeBindings(vectorSearchSpec)`
delegates to it via `fromVectorSearchSpec`.

`BeautySearchLocalQdrantSupplementLauncherModule` remains in
`bifunctor-tagless` as the `LauncherPlan -> ModuleDef`/client/bootstrap
interpreter: its new `managedLocal(plan)` assembles the same includes/bindings
as before, and existing `VectorSpec`/`managedLocalDefault` delegate to it
(`DefaultPlan`/`managedLocal(DefaultPlan)`), unchanged in signature and
behavior.

`BeautySearchApi`, `BeautySearchRouteModules`, `BeautySearchPluginModules`,
route handlers, HTTP/Tapir, clients, and seed/bootstrap/startup code all
remain in `bifunctor-tagless`, untouched. No production route activation,
default route, fallback, fusion, rerank, Qdrant opt-in, client, seed,
bootstrap, or runtime behavior changed; `build.sbt` untouched.

## Phase 11a record: schema contract-shaped forwarders retired from test usage

The remaining test-only contract-shaped `BeautyQVariantSearchDocumentSchema`
references (`Fields`, `documentSpec`, `qdrantPayloadSpec`, `querySchema`)
across 30 `bifunctor-tagless` test files were migrated to
`BeautyQVariantSearchDocumentContract`, the source-confirmed owner of those
declarations. `BeautyQVariantSearchDocumentSchema` no longer exposes those
forwarders at all - it now only declares `projection`/`project(...)`, its
`projection` reading `BeautyQVariantSearchDocumentContract.documentSpec`
directly instead of through a removed local `documentSpec` forwarder.

`BeautyQVariantSearchDocumentSchema.project(...)`/`projection` remain
unchanged and are still used by production code
(`BeautySearchCatalogBackendModules.scala`, `VariantSearchDocument.scala`)
and by projection/materialization tests (`BeautyQVariantSearchDocumentSchemaSpec.scala`,
`BeautyQRepoGraphLoaderSpec.scala`), none of which were edited beyond the
prior Phase 9c state.

Deletion of the schema facade remains forbidden until `project(...)`/
`projection` production and test usages are gone - they are not gone here.
`BeautySearchCatalogSnapshot`, `BeautySearchCatalogSnapshotLoader`, and
`BeautyQCatalogGraph` were not removed because real usages remain. No
production behavior changed; `build.sbt` untouched.

## Phase 11b record: schema projection compatibility facade deleted

The last `BeautyQVariantSearchDocumentSchema.project(...)` callers were
migrated to call `BeautyQVariantSearchDocumentMaterialization.project(...)`
(over `BeautySearchCatalogSnapshot.toMaterializationSnapshot(...)`) directly:
`VariantSearchDocumentBuilder.build` in `VariantSearchDocument.scala`,
`BeautySearchCatalogBackendFactory.fromSeedLoader` in
`BeautySearchCatalogBackendModules.scala`, and the projection assertions in
`BeautyQVariantSearchDocumentSchemaSpec.scala`/`BeautyQRepoGraphLoaderSpec.scala`.
`BeautyQVariantSearchDocumentSchema.scala` was then deleted - it had no
remaining callers.

`VariantSearchDocumentBuilder` remains in `bifunctor-tagless` as a
compatibility API with its public signature unchanged, but now delegates
directly to materialization-owned projection instead of through the deleted
schema facade. `BeautySearchCatalogSnapshot`/`BeautySearchCatalogSnapshotLoader`
remain legacy snapshot-loader compatibility APIs in `bifunctor-tagless`.
`BeautyQVariantSearchDocumentMaterialization`, `SearchDocumentProjection`,
`BeautyQSearchCatalogSnapshot`, `BeautyQSearchCatalogSnapshotLoader`, and
`BeautyQCatalogGraph` remain in `beautyq-search-materialization`, unedited.

No projection output, validation, document fields, tokens, snapshot loading,
or runtime behavior changed; `build.sbt` untouched.

## Phase 12c record: builder/snapshot test consumers migrated to materialization APIs

Migrated the remaining test-only consumers of legacy builder/snapshot
compatibility APIs in `bifunctor-tagless` to materialization-owned APIs
directly, across 22 test files:

- Seed-data document-building consumers (16 files) now build a
  `BeautyQSearchCatalogSnapshot` from the seed's seven lists directly and
  call `BeautyQVariantSearchDocumentMaterialization.project(...)`, instead of
  `BeautySearchCatalogSnapshot.fromSeedData(...)` +
  `VariantSearchDocumentBuilder.build(...)`.
- Seed-scoped repo-loader consumers (5 files:
  `BeautySearchElasticsearchIntegrationSpec.scala`,
  `QdrantEmbeddingBenchmarkLiveCandidateSupport.scala`,
  `QdrantSemanticCandidateEvalSpec.scala`,
  `QdrantExperimentalHybridServiceIntegrationSpec.scala`,
  `RuntimeEsQdrantScorecardProofSpec.scala`) now construct a
  `BeautyQSearchCatalogSeedScope` (omitting `serviceVariantSchemas`, which
  the seed-scoped loading algorithm reads through repositories, not a
  seed-supplied list) and call
  `BeautyQSearchCatalogSnapshotLoader.SeedScopedFromRepositories`, instead of
  the legacy `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories`.
- The full-catalog repo-loader consumer (`BeautyQRepoGraphLoaderSpec.scala`)
  now uses `BeautyQSearchCatalogSnapshotLoader.FromRepositories` directly,
  with `BeautyQVariantSearchDocumentMaterialization.project` called on the
  materialization-owned snapshot without a legacy conversion step.
- `BeautySearchPureSpec.scala`'s `VariantSearchDocumentBuilder`-titled block
  (testing broken-join projection failure, not builder compatibility
  behavior) was rewritten to test `BeautyQVariantSearchDocumentMaterialization.project`
  directly.

After this migration, `VariantSearchDocumentBuilder` had zero real Scala
usages outside its own definition, so the `VariantSearchDocumentBuilder`
object was deleted from `VariantSearchDocument.scala`. `BeautySearchCatalogSnapshot`
and `BeautySearchCatalogSnapshotLoader` were not deleted or narrowed - both
remain in `bifunctor-tagless` as legacy compatibility APIs.
`BeautyQVariantSearchDocumentSchemaSpec.scala` still proves the legacy
`BeautySearchCatalogSnapshot`/`toMaterializationSnapshot` compatibility path,
while production `VariantSearchDocument.scala` and
`BeautySearchCatalogBackendModules.scala` keep the legacy snapshot/loader
compatibility APIs alive. `BeautyQVariantSearchDocumentMaterialization`, `SearchDocumentProjection`,
`BeautyQSearchCatalogSnapshot`, `BeautyQSearchCatalogSnapshotLoader`,
`BeautyQSearchCatalogSeedScope`, and `BeautyQCatalogGraph` remain in
`beautyq-search-materialization`, unedited. Stale test comments referencing
the deleted `BeautyQVariantSearchDocumentSchema`/`VariantSearchDocumentBuilder`
in `RuntimeEsQdrantScorecardProofSpec.scala` were also corrected to name
`BeautyQVariantSearchDocumentMaterialization`.

No projection output, repo traversal, seed/bootstrap behavior, route
behavior, runtime behavior, or test intent changed; `build.sbt` untouched.

## Phase 13 record: legacy snapshot/loader compatibility APIs deleted

Deleted the remaining legacy snapshot/loader compatibility APIs from
`bifunctor-tagless`: `BeautySearchCatalogSnapshot`,
`BeautySearchCatalogSnapshotLoader` (`FromRepositories` and
`SeedScopedFromRepositories`), and `VariantSearchDocumentBuilder` were all
already gone or removed, and the now-empty
`VariantSearchDocument.scala` file itself was deleted.

Production `BeautySearchCatalogBackendFactory.fromSeedLoader` in
`BeautySearchCatalogBackendModules.scala` now builds a
`BeautyQSearchCatalogSnapshot` directly from `BeautyQSeedData` (via a
private `materializationSnapshotFromSeedData` helper) and calls
`BeautyQVariantSearchDocumentMaterialization.project` on it directly,
without the legacy snapshot adapter.

The former `BeautyQVariantSearchDocumentSchemaSpec.scala` (a legacy
`BeautySearchCatalogSnapshot`/`toMaterializationSnapshot` conversion proof)
was renamed and migrated to
`BeautyQVariantSearchDocumentContractProjectionSpec.scala`, using a local
`materializationSnapshot` helper that builds a `BeautyQSearchCatalogSnapshot`
directly and projects it via `BeautyQVariantSearchDocumentMaterialization.project`;
all expected documents, assertion messages, ids, paths, and failure messages
are unchanged.

`BeautyQSearchCatalogSnapshot`, `BeautyQSearchCatalogSeedScope`,
`BeautyQSearchCatalogSnapshotLoader`, `BeautyQCatalogGraph`, and
`BeautyQVariantSearchDocumentMaterialization` remain in
`beautyq-search-materialization`, unedited except for stale-comment cleanup
in `BeautyQCatalogGraph.scala` and `BeautyQVariantSearchDocumentMaterialization.scala`
that no longer name the deleted `BeautyQVariantSearchDocumentSchema`.
`BeautyQVariantSearchDocumentContract.scala` in `beautyq-search-contract`
received the same kind of comment-only correction; its behavior did not
change.

No projection output, document fields, repo traversal, seed/bootstrap
behavior, route behavior, or runtime behavior changed; `build.sbt` untouched.

## Phase 14c record: BeautyQ runtime model/parser/in-memory/response/readiness layer moved to beautyq-search-wiring

Moved six BeautyQ search runtime/model/helper files from `bifunctor-tagless`
into `beautyq-search-wiring`, with package names unchanged:

- `leaderboard.search.BeautySearchModels` (`UserSearchInput`, `ParsedSearchIntent`,
  `BeautySearchResponse`, `BeautySearchBackend`, `BeautySearchService`, and related
  runtime model/codec types)
- `leaderboard.search.parser.BeautySearchIntentParser`
- `leaderboard.search.inmemory.InMemorySearchBackend`
- `leaderboard.search.document.BeautySearchReadyCatalogDocuments`
- `leaderboard.search.interpreter.SearchResponseAssembler`
- `leaderboard.search.interpreter.SearchSpecSupport`

These files were source-confirmed as pure BeautyQ runtime/model/helper layer,
not HTTP/Tapir route handlers, app bootstrap, concrete ES/Qdrant clients, or
Distage `ModuleDef` interpreters, and already only used BeautyQ contract/
materialization/runtime concepts visible to `beautyq-search-wiring` through
its existing dependencies on `beautyq-search-contract`, `beautyq-search-materialization`,
`search-elasticsearch`, and `search-qdrant`. Both `beautyqSearchWiring/compile`
and `bifunctor-tagless/compile`/`Test/compile` succeeded with zero import
fixes required; `build.sbt` was not touched.

`bifunctor-tagless` remains the interpreter shell for HTTP/Tapir route
handlers, app bootstrap/startup, concrete Elasticsearch/Qdrant client wiring,
Distage `ModuleDef` interpreters, and the production route/plugin graph
(`BeautySearchApi`, `BeautySearchRouteModules`, `BeautySearchPluginModules`,
`BeautySearchCatalogBackendModules`, `BeautySearchQdrantSupplementRuntimeBindingModules`,
`BeautySearchLocalQdrantSupplementLauncherModule`, `SearchBackendRouter`, and
the Qdrant candidate/projector/hybrid/eval files); none of those moved in
this patch.

No parser behavior, response shape, in-memory scoring/filtering/ranking,
runtime binding names, route behavior, readiness behavior, or projection
behavior changed.

## Phase 15c record: concrete backend/hybrid/routing/semantic/lexical runtime layer moved to beautyq-search-wiring

Moved 43 of 54 source-confirmed BeautyQ concrete backend/hybrid/routing/
semantic/lexical runtime files from `bifunctor-tagless` into
`beautyq-search-wiring`, package names unchanged: `VariantSearchDocumentSnapshotProvider`;
all eight `leaderboard.search.elasticsearch` BeautyQ ES backend/readiness/
adapter files (`BeautyQElasticsearchInterpreterAdapter`, `ElasticsearchSearchBackend`,
`ElasticsearchSeedSearchComposition`, and related readiness/lifecycle DTOs);
10 of 21 targeted `leaderboard.search.qdrant` files (`QdrantCandidateAssembler`,
`QdrantCandidateResponseProjector`, `QdrantSemanticCandidateBackend`,
`QdrantSemanticCandidateSearch`, the moved `QdrantVariantDocument*`
indexer/point-builder files, and the non-production experiment files); 21 `leaderboard.search.hybrid`
files including `ExperimentalHybridSearchBackend`, `ExperimentalBeautySearchService`,
`ExperimentalHybridRouteDecider`/`Diagnostics`, the `BeautyQHybrid*` projection/
response files, the `BeautyQNonProductionHybridRunner*` composition/manual-input
files, `hybrid/control/BeautySearchHybridControlPlane`, and
`hybrid/production/BeautySearchHybridProductionActivation`; and
`routing.SearchBackendRouter`, `semantic.SemanticCandidateBackend`,
`lexical.LexicalDocumentBackend`.

11 targeted `leaderboard.search.qdrant` files could not move and remain in
`bifunctor-tagless`: `QdrantProductionCandidateReadiness`,
`QdrantProductionCandidateActivationConfigApproval`,
`QdrantExplicitOptInRoutePrerequisites`, `QdrantExplicitOptInBeautySearchBackend`,
`QdrantProductionCandidateActivationPlanning`, `QdrantProductionCandidateActivationPolicy`,
`QdrantProductionCandidateIndexingReadiness`, `QdrantProductionCandidateObservabilityReadiness`,
`QdrantProductionCandidateRollbackReadiness`,
`QdrantProductionCandidateSearchReadiness`, and
`QdrantProductionCandidateServingApprovalRequest`. Reason: `QdrantProductionCandidateReadiness`
and `QdrantProductionCandidateActivationConfigApproval` both directly reference
`QdrantProductionCandidateQualityReport`/`QdrantProductionCandidateQualityDecisionStatus`/
`QdrantProductionCandidateQualityGate`, which stay in `bifunctor-tagless` per this
patch's own constraint (`QdrantProductionCandidateQualityGate` imports
`leaderboard.search.eval.EngineEvalAggregateReport`). Moving either file would
have made `beautyq-search-wiring` depend on `bifunctor-tagless`, which already
depends on `beautyq-search-wiring` - a real module cycle. The other eight files
transitively require types owned by those two (`QdrantProductionCandidateReadinessStatus`/
`ReadinessReport`, `QdrantProductionCandidateActivationApprovalStatus`,
`QdrantProductionCandidateActivationConfigApprovalReport`), so the entire
cluster had to stay together in `bifunctor-tagless`. This mirrors the same
constraint already applied to `QdrantProductionCandidateQualityGate` itself and
to `QdrantEmbeddingBenchmark*`.

`beautyqSearchWiring/compile`, `bifunctor-tagless/compile`, and
`bifunctor-tagless/Test/compile` all succeeded afterward with zero import
fixes; `build.sbt` was not touched, and no compatibility shim was added.
`bifunctor-tagless` remains the shell for HTTP/Tapir API, app bootstrap/
startup, Distage `ModuleDef` interpreters, config binding modules, Docker/
testkit/app launcher shell, and the eval/benchmark runner shell, plus the
11-file Qdrant production-candidate readiness/activation-config cluster
described above and the previously-pinned `BeautyQNonProductionHybridRunnerRealClientInputs`,
`BeautySearchHybridProductionModules`, `QdrantProductionCandidateQualityGate`,
and `QdrantEmbeddingBenchmark*` files.

No ES/Qdrant request behavior, candidate assembly/projection behavior, hybrid
route decisions, hybrid response projection, lexical/semantic backend
contracts, snapshot provider semantics, readiness behavior, or production
activation data models changed.

## Phase 16 record: Qdrant quality policy split and production readiness cluster moved to beautyq-search-wiring

Split `QdrantProductionCandidateQualityGate.scala`'s two mixed concerns and
moved the remaining Qdrant production-candidate readiness/activation/
explicit-opt-in cluster (previously blocked by that file) to
`beautyq-search-wiring`.

Extracted the pure Qdrant quality DTOs and policy functions
(`QdrantProductionCandidateQualityRule`, `QdrantProductionCandidateParityReport`,
`QdrantProductionCandidateQualityDecisionStatus`, `QdrantProductionCandidateQualityDecision`,
`QdrantProductionCandidateParityOutcome`, `QdrantProductionCandidateQualityReport`,
and `evaluate(...)`/`readinessStatus(...)`) into a new
`beautyq-search-wiring/.../qdrant/QdrantProductionCandidateQualityPolicy.scala`.
`QdrantProductionCandidateQualityGate.scala` remains in `bifunctor-tagless` as
a thin eval-shell adapter only: it keeps the `EngineEvalAggregateReport`
import and `fromEngineEval(...)` (preserving the exact existing field
mapping), and its `evaluate(...)`/`readinessStatus(...)` now delegate to
`QdrantProductionCandidateQualityPolicy`. No duplicate DTO definitions remain
in the adapter file.

With the quality-gate coupling resolved, the 11-file cluster blocked in
Phase 15c moved to `beautyq-search-wiring`: `QdrantProductionCandidateReadiness`,
`QdrantProductionCandidateActivationConfigApproval`,
`QdrantExplicitOptInRoutePrerequisites`, `QdrantExplicitOptInBeautySearchBackend`,
`QdrantProductionCandidateActivationPlanning`, `QdrantProductionCandidateActivationPolicy`,
`QdrantProductionCandidateIndexingReadiness`, `QdrantProductionCandidateObservabilityReadiness`,
`QdrantProductionCandidateRollbackReadiness`, `QdrantProductionCandidateSearchReadiness`,
and `QdrantProductionCandidateServingApprovalRequest`. The single call site in
`QdrantProductionCandidateReadiness.withQualityReport` that referenced
`QdrantProductionCandidateQualityGate.readinessStatus(...)` was updated to call
`QdrantProductionCandidateQualityPolicy.readinessStatus(...)` instead - the
only import-driven edit required by the physical move.

`beautyqSearchWiring/compile`, `bifunctor-tagless/compile`, and
`bifunctor-tagless/Test/compile` all succeeded afterward with no other edits;
`build.sbt` was not touched and no compatibility shim was added.
`bifunctor-tagless` keeps `QdrantProductionCandidateQualityGate` (eval-shell
adapter only), all `leaderboard.search.eval.*` files, all
`QdrantEmbeddingBenchmark*` files, config files, startup/bootstrap files,
API/HTTP files, and Distage `ModuleDef` plugin/module files.

No Qdrant quality-gate thresholds, decision statuses, readiness behavior,
activation policy behavior, explicit opt-in route prerequisite validation, or
report shapes changed.

## Phase 17 record: pure eval and Qdrant embedding benchmark model/report layer moved to beautyq-search-wiring

Moved the largest remaining pure eval/benchmark layer from `bifunctor-tagless`
into `beautyq-search-wiring`, package names unchanged.

Qdrant embedding benchmark: `QdrantEmbeddingBenchmark` (DTOs/aggregation),
`QdrantEmbeddingBenchmarkReportJson`, `QdrantEmbeddingBenchmarkReportFormatter`,
`QdrantEmbeddingBenchmarkDecisionPolicy`, `QdrantEmbeddingBenchmarkSavedReportComparison`,
and `QdrantEmbeddingBenchmarkRunner` (which also declares the pure
`QdrantEmbeddingBenchmarkCandidateExecutor` trait it depends on). The
real-client implementation of that trait stays behind: `QdrantEmbeddingBenchmarkCandidateExecutor.scala`
in `bifunctor-tagless` defines the concrete `QdrantEmbeddingBenchmarkQdrantCandidateExecutor`
(imports `QdrantClient`, `LlamaCppEmbeddingClient`) and was not moved.

EngineEval: `EngineEval` (DTOs/aggregate metrics), `EngineEvalReportAssembly`,
`EngineEvalReportFormatter`, `EngineEvalReportJson`,
`EngineEvalSavedReportComparison`, and `BeautySearchEvalReportJson` (pure JSON
codec over `BeautySearchEvalReport`).

Split `BeautySearchEval.scala`'s two mixed concerns: the pure eval model/scoring
code (`BeautySearchEvalLocation`, `EvalNumericRange`, `EvalConstraintBlock`,
`EvalTopK`, `EvalCarouselWeights`, `EvalVariantExpectation`,
`EvalProviderExpectation`, `EvalServiceExpectation`, `EvalScoring`,
`BeautySearchEvalQuery`, `BeautySearchEvalSuite`, `BeautySearchEvalReport`, and
`object BeautySearchEvalScorer`) moved to `beautyq-search-wiring/.../eval/BeautySearchEval.scala`.
The file-IO loader/decoder shell (`object BeautySearchEvalLoader`, its
`Files.readString`/`Path`/`StandardCharsets` usage, and all its implicit
Circe decoders with their existing defaulting behavior) moved unchanged into
a new `bifunctor-tagless/.../eval/BeautySearchEvalLoader.scala`, in the same
`leaderboard.search.eval` package so it resolves the pure model types without
a cross-module import.

`beautyqSearchWiring/compile`, `bifunctor-tagless/compile`, and
`bifunctor-tagless/Test/compile` all succeeded afterward with zero import
fixes; `build.sbt` was not touched and no compatibility shim was added.
`bifunctor-tagless` keeps `QdrantEmbeddingBenchmarkCandidateExecutor.scala`,
`QdrantProductionCandidateQualityGate.scala`, `BeautySearchEvalLoader.scala`,
`BeautySearchLocalDevOnlyFallbackPolicy.scala`, all `M*.scala` eval
design/scaffold files, config files, API/HTTP files, startup/bootstrap files,
and Distage `ModuleDef` plugin/module files.

No benchmark semantics, eval aggregation, report JSON shape, decoder
defaulting behavior, candidate selection, search behavior, or app graph
semantics changed.

## Phase 18 record: remaining pure eval design/scaffold and quality-gate layer moved to beautyq-search-wiring

Moved the remaining pure eval design/scaffold layer and the Qdrant
quality-gate adapter from `bifunctor-tagless` into `beautyq-search-wiring`,
package names unchanged.

All 45 `M*.scala` eval design/scaffold files (`M8TelemetrySchemaRenderer`
through `M21ProductionActivationDecisionPackage`, including the full `M9`/`M11`/
`M12`/`M19`/`M20` families) and `BeautySearchLocalDevOnlyFallbackPolicy.scala`
moved to `beautyq-search-wiring/.../eval/`. These are pure data/policy/design
declarations with no HTTP/API/startup/config/Distage/real-client/file-loader
dependency; `BeautySearchLocalDevOnlyFallbackPolicy.scala`'s only cross-file
dependency, `M10BeautyQSearchOfflineRoutingBoundary` (declared in
`M10BeautyQSearchRetrievalPolicyReadiness.scala`), moved in the same patch.

`QdrantProductionCandidateQualityGate.scala` also moved to
`beautyq-search-wiring/.../qdrant/`: after Phase 17 it only depends on
`EngineEvalAggregateReport` and the wiring-owned `QdrantProductionCandidateQualityPolicy`,
so its remaining `bifunctor-tagless` residency was no longer required.

`beautyqSearchWiring/compile`, `bifunctor-tagless/compile`, and
`bifunctor-tagless/Test/compile` all succeeded afterward with zero import
fixes; `build.sbt` was not touched and no compatibility shim was added.
`bifunctor-tagless` keeps `BeautySearchEvalLoader.scala`,
`QdrantEmbeddingBenchmarkCandidateExecutor.scala`,
`BeautySearchCatalogBackendModules.scala`, `BeautySearchRouteModules.scala`,
`BeautySearchPluginModules.scala`, `BeautySearchHybridProductionModules.scala`,
`BeautyQNonProductionHybridRunnerRealClientInputs.scala`, all API/HTTP files,
startup/bootstrap files, config files, and Docker/testkit/plugin shell files.

No API behavior, route behavior, app graph semantics, runtime binding names,
benchmark semantics, eval semantics, config names, HTTP contracts, Qdrant/ES
requests, response shape, scoring, ranking, filtering, readiness, startup
behavior, renderers, reason codes, or report shapes changed.

## Phase 19c record: pure request/config/embedding-decoder/query-subset boundaries split out of bifunctor-tagless

Split the remaining source-confirmed pure boundaries out of the `bifunctor-tagless`
API/config/embedding shell, across three destination modules.

`BeautySearchRequestContract.scala` (package `leaderboard.search`, unchanged
constants/codes/messages/`SemanticError`) moved to `beautyq-search-wiring`. A
new `BeautySearchRequestValidation` object was added there, extracted
byte-for-byte from `BeautySearchApi.validate(...)`'s branching (blank query,
limit bounds, latitude bounds, longitude bounds), returning
`Either[BeautySearchRequestContract.SemanticError, UserSearchInput]`.
`BeautySearchApi.scala` in `bifunctor-tagless` now delegates:
`BeautySearchRequestValidation.validate(input).left.map(badRequest)`, keeping
`badRequest`/`HttpApiFailure.BadRequest` mapping and all HTTP/Tapir route
logic as HTTP shell, unchanged validation ordering and HTTP response behavior.

`QdrantEmbeddingBenchmarkQuerySubset.scala` moved to `beautyq-search-wiring`
unchanged (subset ids, descriptions, query ids, filtering, deduplication,
limit behavior, operation name, and error messages preserved).

`LlamaCppEmbeddingClient.scala` split: the pure `LlamaCppEmbeddingClientConfig`
case class and the pure `decodeEmbeddingJson` JSON-decoding logic (field names
`data`/`embedding`, operation name `llama-cpp-embedding`, exact
missing-data/error messages) moved to new
`search-qdrant/.../embedding/LlamaCppEmbeddingClientConfig.scala` and
`LlamaCppEmbeddingResponseDecoder.scala`. The real `LlamaCppEmbeddingClient`
class and its Java `HttpClient` request/response shell stayed in
`bifunctor-tagless`; its companion's public `decodeEmbeddingJson(json: Json)`
is now a one-line delegate to `LlamaCppEmbeddingResponseDecoder.decodeEmbeddingJson(json)`,
preserving the method as a compatibility surface. `decodeEmbedding`, HTTP
request construction, timeout, model string, endpoint URL construction, and
status handling are unchanged.

`QdrantPortCfg.scala` and `ElasticsearchPortCfg.scala` (both pure `leaderboard.config`
case class DTOs) moved to `leaderboard-core`, which `search-qdrant`,
`search-elasticsearch`, and `bifunctor-tagless` already depend on. Config
binding, Docker-derived config creation, and `makeConfig[...]` shell were not
touched and remain in `bifunctor-tagless`.

`leaderboard-core/compile`, `search-qdrant/compile`, `beautyqSearchWiring/compile`,
`bifunctor-tagless/compile`, and `bifunctor-tagless/Test/compile` all
succeeded afterward; the only edits beyond the moves were the anticipated
`BeautySearchApi.scala` import/delegation change and removing an
now-unused `io.circe.Decoder` import from `LlamaCppEmbeddingClient.scala`.
`build.sbt` was not touched and no compatibility shim beyond the explicitly
allowed `decodeEmbeddingJson` delegate was added.
`bifunctor-tagless` keeps `BeautySearchApi.scala`, `BeautySearchTapirEndpoints.scala`,
`HttpApiFailure.scala`, `BeautySearchProductionInclude.scala`,
`BeautySearchProductionInclusion.scala`, `LlamaCppEmbeddingClient.scala`,
`BeautySearchCatalogBackendModules.scala`, `BeautySearchRouteModules.scala`,
`BeautySearchPluginModules.scala`, `BeautySearchHybridProductionModules.scala`,
`BeautySearchEvalLoader.scala`, `QdrantEmbeddingBenchmarkCandidateExecutor.scala`,
and all API/HTTP/startup/config-binding/ModuleDef/real-client/file-IO shell.

No API behavior, route behavior, HTTP contracts, error codes/messages,
request validation ordering, production route exposure, config names, client
request semantics, JSON response decoding semantics, app graph semantics,
runtime binding names, Qdrant/ES requests, response shape, scoring, ranking,
filtering, readiness, or startup behavior changed.

## Phase 20 record: managed-local bootstrap pure plan/fingerprint layer moved to beautyq-search-wiring

Extracted the remaining source-confirmed pure managed-local bootstrap layer
out of `BeautyQManagedLocalSearchBootstrap.scala`, leaving only the
lifecycle/startup execution shell in `bifunctor-tagless`.

`BeautyQManagedLocalSearchBootstrapFingerprint.scala` (fingerprint
construction, `Version`/`MetadataKey`/`MetadataVersionKey`, `build(...)`,
`decodeCollectionMetadataValue(...)`, `asQdrantCollectionMetadata`) moved to
`beautyq-search-wiring/.../startup/`, unchanged.

A new `beautyq-search-wiring/.../startup/BeautyQManagedLocalSearchBootstrapPlan.scala`
now holds the pure bootstrap DTOs/action labels
(`BeautyQManagedLocalSearchBootstrapResult`, `BeautyQManagedLocalSearchBootstrapAction`)
and the pure plan helpers moved out of the old `BeautyQManagedLocalSearchBootstrap`
object: `EmbeddingPreflightOperationName`, `EmbeddingModelName`,
`SourceTextFields` (unchanged order: `serviceText`, `attributeText`,
`allText`, `categoryName`), `SourceTextFieldPaths`, `ExpectedVectorDimension`
(`1024`), `embeddingSpec(...)` (unchanged `VectorDistance.Cosine`),
`readinessConfig(...)`, and `qdrantCollectionInfoReusable(...)` with its
private `observedQdrantPointCount(...)` (unchanged Qdrant count-field
fallback order: `result.points_count`, `result.indexed_vectors_count`,
top-level `points_count`, top-level `indexed_vectors_count`).

`BeautyQManagedLocalSearchBootstrap.scala` in `bifunctor-tagless` keeps
`embeddingPreflight(...)`, `run(...)`, `preparedResourcesReusable(...)`,
`elasticsearchReusable(...)`, `qdrantReusable(...)`, `prepareElasticsearch(...)`,
`prepareQdrant(...)`, `isQdrantCollectionAlreadyExists(...)`,
`isTransientQdrantCollectionMiss(...)`, and `BeautyQManagedLocalSearchDataReady`
(with its `Noop`/`Bootstrap` `Lifecycle` classes) as the startup/effect shell.
Its public compatibility surface (`EmbeddingPreflightOperationName`,
`EmbeddingModelName`, `SourceTextFields`, `SourceTextFieldPaths`,
`ExpectedVectorDimension`, `embeddingSpec(...)`, `readinessConfig(...)`,
`qdrantCollectionInfoReusable(...)`) now delegates to
`BeautyQManagedLocalSearchBootstrapPlan`; only imports that became unused
after the move (`BeautyQVariantSearchDocumentContract`, `VectorDistance`,
`QdrantCollectionIdentity`) were removed.

`beautyqSearchWiring/compile`, `bifunctor-tagless/compile`, and
`bifunctor-tagless/Test/compile` all succeeded afterward with no other
edits; `build.sbt` was not touched and no compatibility shim beyond the
explicitly-required delegating members was added.

No bootstrap behavior, preflight behavior, fingerprint value, fingerprint
inputs, metadata keys, reuse decision, ES/Qdrant creation behavior, retry
behavior, action labels, result fields, log message, runtime binding names,
source text field order, expected vector dimension, or Qdrant count-field
fallback order changed.

## Phase 21 record: bifunctor-tagless direct dependencies collapsed to the wiring boundary

Build-graph cleanup only, no Scala source files touched. Phases 14c through
20 moved essentially all source-confirmed pure/reusable BeautyQ search code
out of `bifunctor-tagless` into `beautyq-search-wiring` (and, for a few
narrowly-scoped pieces, into `search-qdrant`/`leaderboard-core`). What
remains in `bifunctor-tagless` is genuine shell: Distage `ModuleDef`/plugin
wiring, HTTP/Tapir/API routes, startup/lifecycle, config binding, Java/real
clients, and file/resource IO - source-confirmed to have no further safe
move without relocating that shell itself.

With that boundary reached, `bifunctor-tagless`'s direct project dependency
list in `build.sbt` no longer needed to spell out every lower module by
name. Replaced:

```
.dependsOn(`leaderboard-core`, `search-core`, `search-elasticsearch`, `search-qdrant`, repoCore, beautyqModel, beautyqSearchContract, beautyqSearchRepositories, beautyqSearchMaterialization, beautyqSearchWiring)
```

with:

```
.dependsOn(beautyqSearchWiring)
```

`beautyqSearchWiring` already transitively depends on
`beautyqSearchContract`, `beautyqSearchMaterialization`, `search-elasticsearch`,
and `search-qdrant`, which in turn transitively bring in
`beautyqSearchRepositories`, `repoCore`, `beautyqModel`, `search-core`,
`searchContractCore`, and `leaderboard-core` - the exact same set
`bifunctor-tagless` previously depended on directly, now available only
transitively through the single `beautyqSearchWiring` boundary. No other
project's `.dependsOn(...)` and no root `.aggregate(...)` entry changed.
`bifunctor-tagless/compile`, `bifunctor-tagless/Test/compile`, and every
downstream module's compile succeeded unchanged, with zero Scala source
edits required.

`bifunctor-tagless` is now intentionally the app/shell module living over
the `beautyq-search-wiring` boundary: it depends on the search/runtime stack
only through that one module, and keeps only HTTP/API/Tapir routes, Distage
`ModuleDef` interpreters, startup/lifecycle shell, config binding, real
ES/Qdrant/embedding clients, and file/resource IO loaders as source.

No API behavior, route behavior, HTTP contracts, error codes/messages,
request validation, production route exposure, config names, client request
semantics, JSON decoding semantics, app graph semantics, runtime binding
names, Qdrant/ES requests, response shape, scoring, ranking, filtering,
readiness, startup behavior, renderers, reason codes, fingerprints, metadata
keys, or report shapes changed.

## Phase 22 record: HTTP/API/Tapir shell split into a new app-http module

App-shell module split. Added `lazy val appHttp` (`.in(file("app-http"))`,
`name := "app-http"`, `lightweightSettings` with `circeGeneric`/`zio`/`zioCats`/
`http4sDsl`/`http4sCirce`/`tapirHttp4sServer`/`tapirJsonCirce`/`scalatest % Test`),
`.dependsOn(beautyqSearchWiring)`, added to the root `.aggregate(...)`.
`bifunctor-tagless`'s `.dependsOn(beautyqSearchWiring)` became
`.dependsOn(beautyqSearchWiring, appHttp)`; `appSettings(...)` and library
dependencies unchanged.

Moved with `git mv`, package declarations unchanged: 12 of the 13 originally
targeted `leaderboard.api` files (`BeautySearchApi`, `BeautySearchProductionInclude`,
`BeautySearchProductionInclusion`, `CategoryApi`, `EsLifecycleStatusApi`,
`HttpApi`, `LadderApi`, `MasterApi`, `MasterLocationApi`,
`MasterServiceOfferApi`, `MasterServiceOfferVariantApi`, `ServiceApi`),
`leaderboard.http.HttpApiFailure`, and all 12 `leaderboard.http.tapir.*`
files, to `app-http`.

`ProfileApi.scala` could not move: it imports `leaderboard.services.Ranks`, a
generic business-logic service (over `Ladder`/`Profiles` repos) that lives
only in `bifunctor-tagless` and is out of this patch's scope. Moving it
would have forced `appHttp` to depend on `bifunctor-tagless`, which is
forbidden and circular (`bifunctor-tagless` already depends on `appHttp`).
`ProfileApi.scala` stays in `bifunctor-tagless`, unedited, in its original
`leaderboard.api` package; it resolves `HttpApi`, `HttpApiFailure`,
`ProfileTapirEndpoints`, and `LegacyJsonResponse` from `app-http` transitively
through `bifunctor-tagless`'s new dependency, exactly like any other shell
consumer. `LeaderboardPlugin.scala` still wires `ProfileApi` together with
`Ranks` unchanged.

`HttpServer.scala` (`Lifecycle`, `BeautyQSeedReady`, `BeautyQManagedLocalSearchDataReady`,
`Set[HttpApi[F]]` combination) stayed in `bifunctor-tagless`, along with all
`leaderboard.plugins` `ModuleDef` shell, config binding, startup/lifecycle
shell, `LlamaCppEmbeddingClient`, `BeautySearchEvalLoader`,
`QdrantEmbeddingBenchmarkCandidateExecutor`, and all other real-client/file-IO
shell.

`appHttp/compile`, `appHttp/Test/compile`, `bifunctor-tagless/compile`,
`bifunctor-tagless/Test/compile`, and `beautyqSearchWiring/compile` all
succeeded with zero Scala source body edits (only file relocation);
`build.sbt` changes were limited to the new project block and the two
`dependsOn`/`aggregate` lines.

No endpoint paths, HTTP methods, request/response JSON shapes, error codes,
validation ordering, production inclusion behavior, opt-in route exposure,
app graph semantics, config names, startup behavior, or runtime binding
names changed.

## Phase 23 record: app-services boundary extracted; Phase 22's ProfileApi deviation closed

Closes the Phase 22 `ProfileApi.scala` deviation (it stayed in `bifunctor-tagless`
because it depended on `leaderboard.services.Ranks`, which lived only there).
Added a new `lazy val appServices` (`.in(file("app-services"))`,
`name := "app-services"`, `lightweightSettings` with `distageCore`/`scalatest % Test`),
`.dependsOn(beautyqSearchRepositories)`, added to the root `.aggregate(...)`.
`appHttp`'s `.dependsOn(beautyqSearchWiring)` became
`.dependsOn(beautyqSearchWiring, appServices)`; `bifunctor-tagless`'s
`.dependsOn(beautyqSearchWiring, appHttp)` became
`.dependsOn(beautyqSearchWiring, appHttp, appServices)`.

Moved with `git mv`, package declarations unchanged: `Ranks.scala`
(`trait Ranks`, `object Ranks` with `Ranks.Impl`) from
`bifunctor-tagless/.../leaderboard/services/` to
`app-services/.../leaderboard/services/`; `ProfileApi.scala` from
`bifunctor-tagless/.../leaderboard/api/` to `app-http/.../leaderboard/api/`,
completing the 13-of-13 `leaderboard.api` file move started in Phase 22.

`Ladder`/`Profiles` repo interfaces and their `Dummy`/`Postgres`
implementations (`Lifecycle`/Doobie shell) stayed in
`beautyq-search-repositories`, unedited. `LeaderboardPlugin.scala`,
`LeaderboardRole.scala`, and `HttpServer.scala` stayed in `bifunctor-tagless`,
unedited; they still wire/consume `ProfileApi` and `Ranks` by import,
resolving both transitively through `bifunctor-tagless`'s dependency on
`appHttp`/`appServices`, exactly like every other API/service class.

`appServices/compile`, `appServices/Test/compile`, `appHttp/compile`,
`appHttp/Test/compile`, `bifunctor-tagless/compile`,
`bifunctor-tagless/Test/compile`, `beautyqSearchRepositories/compile`, and
`beautyqSearchWiring/compile` all succeeded with zero Scala source body
edits (only file relocation); `build.sbt` changes were limited to the new
project block and the two `dependsOn`/`aggregate` lines.

No `ProfileApi` routes, HTTP methods, request/response JSON, error
codes/messages, validation ordering, `Ranks.getRank` behavior, `Ladder`/`Profiles`
behavior, production inclusion behavior, opt-in route exposure, or app graph
semantics changed.

## Phase 24 record: canonical Qdrant source-text fields centralized

Adds `BeautyQSearchSourceTextFieldsContract` (package
`leaderboard.search.beautyq.contract`, in `beautyq-search-contract`) as the
single contract-owned declaration of the canonical BeautyQ Qdrant semantic
source-text field order:

```scala
val qdrantSourceTextFields: List[SearchField[VariantSearchDocument]] =
  List(
    BeautyQVariantSearchDocumentContract.Fields.serviceText,
    BeautyQVariantSearchDocumentContract.Fields.attributeText,
    BeautyQVariantSearchDocumentContract.Fields.allText,
    BeautyQVariantSearchDocumentContract.Fields.categoryName,
  )
val qdrantSourceTextFieldPaths: List[String] = qdrantSourceTextFields.map(_.path)
```

`BeautyQSearchDomainContract` exposes both values as a thin passthrough
(`qdrantSourceTextFields`, `qdrantSourceTextFieldPaths`), alongside its
existing catalog/document/intent/runtime/response/evaluation slices.

Prior to this phase, the exact same four-field list was declared locally,
byte-for-byte identical, in `BeautyQManagedLocalSearchBootstrapPlan.SourceTextFields`
(`beautyq-search-wiring`) and `QdrantEmbeddingBenchmarkExecutorConfig.sourceTextFields`
(`bifunctor-tagless`, real Qdrant/Llama client shell), and duplicated again in
nine test files. Both production/runtime consumers now reference
`BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields`/`qdrantSourceTextFieldPaths`
directly, while keeping their own public compatibility names
(`SourceTextFields`/`SourceTextFieldPaths`, `sourceTextFields`) unchanged, so no
call site elsewhere had to change. The nine test files with an *exact* full
four-field list now reference the same contract declaration:
`RuntimeEsQdrantScorecardProofSpec` (11 occurrences, including the Y0E
`baseline_current` source-field candidate), `DisabledRuntimeHybridExecutionEsQdrantProofSpec`,
`QP2NoWorseningRouteProofSpec`, `M18DualEngineOfflineEvalQdrantRealLegSpec`,
`QdrantSemanticCandidateEvalSpec`, `QP19QdrantSupplementMeasuredAcceptanceGateSpec`,
`QP18QdrantSupplementImprovementNoWorseningSpec`, `BeautySearchQdrantSupplementProvenanceSpec`,
and `QdrantExperimentalHybridServiceIntegrationSpec`. Intentionally partial or
synthetic source-text-field lists elsewhere (`serviceText`+`allText`,
`allText`-only, `serviceName`-only, test-local synthetic `SearchField` lists in
files such as `QP5QdrantSupplementReadinessContractSpec`,
`QP6NoWorseningRouteMatrixSpec`, `QP8QdrantSupplementActivationPreflightSpec`,
`QP14QdrantSupplementActivationDiagnosticsSpec`, `BeautySearchPureSpec`,
`SearchDslTypedFieldSpec`, `BeautyQNonProductionHybridRunner*InputsSpec`/`*ModuleSpec`,
and the other `Y0ESourceFieldCandidate`/Y0-series measurement axes in
`RuntimeEsQdrantScorecardProofSpec`) were left untouched — they are deliberate
measurement/test axes, not accidental duplication of the canonical list.

`beautyqSearchContract/compile`, `Test/compile`, `beautyqSearchWiring/compile`,
`bifunctor-tagless/compile`, and `Test/compile` all succeeded with no other
source changes. No field names, field order, Qdrant payload shape, ES mapping,
materialized document text, runtime bootstrap fingerprints, benchmark report
shapes, eval/report semantics, or route/API behavior changed.

## Coordinator checkpoint (after Phase 24/25)

- The north star is the real BeautyQ `SearchDomainSpec` / DSL: contract-owned
  declarations every interpreter consumes, not a clean module graph for its
  own sake.
- Module split and shell split are means, not the final goal. A boundary
  move only counts as progress if it reduces duplicated/invented BeautyQ
  semantics somewhere else.
- Catalog topology is only one `SearchDomainSpec` section (see "Full
  `SearchDomainSpec` sections" above) - never describe it as the whole
  contract.
- Duplicated BeautyQ semantics (field lists, model names, thresholds,
  dimensions, distances, capability declarations) should move into
  contract-owned slices only when source proves an *exact* duplication -
  not a superficially similar but intentionally different test/measurement
  axis, mismatch fixture, or synthetic label.
- Coordinator prompting/review/verification-label workflow rules live in
  `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`, not in this file.
- Current module ownership and architecture live in
  `docs/beautyq-search-dsl-v1.md`, not in a separate onboarding doc.

## Phase 25 record: managed-local Qdrant runtime defaults centralized

Adds to `BeautyQSearchRuntimeContract` (`beautyq-search-contract`):
explicit backend-id constants (`ElasticsearchBackendId = SearchBackendId("elasticsearch")`,
`QdrantBackendId = SearchBackendId("qdrant")`, now used by the `elasticsearch`/`qdrant`
declarations instead of inline string literals) and the canonical managed-local
BeautyQ Qdrant runtime defaults: `ManagedLocalQdrantEmbeddingModelName =
"local-llama-cpp-embedding"`, `ManagedLocalQdrantExpectedVectorDimension = 1024`,
`ManagedLocalQdrantVectorDistance = VectorDistance.Cosine`. `BeautyQSearchDomainContract`
exposes all five as thin passthroughs (`elasticsearchBackendId`, `qdrantBackendId`,
`managedLocalQdrantEmbeddingModelName`, `managedLocalQdrantExpectedVectorDimension`,
`managedLocalQdrantVectorDistance`).

Prior to this phase, the same three managed-local values were declared locally in
three places: `BeautyQManagedLocalSearchBootstrapPlan.EmbeddingModelName`/`ExpectedVectorDimension`
(`beautyq-search-wiring`), `QdrantEmbeddingBenchmarkExecutorConfig.distance`
(`bifunctor-tagless`, real Qdrant/Llama client shell), and the request JSON `"model"`
field hardcoded inside `LlamaCppEmbeddingClient` (`bifunctor-tagless`, real HTTP client
shell). All three now delegate to `BeautyQSearchRuntimeContract`, keeping their own
public compatibility names (`EmbeddingModelName`, `ExpectedVectorDimension`, `distance`)
unchanged, so no call site elsewhere had to change. Importing `BeautyQSearchRuntimeContract`
into `LlamaCppEmbeddingClient` introduced no dependency cycle: `bifunctor-tagless` already
depends on `beautyq-search-contract` transitively via `beautyq-search-wiring`.

Positive managed-local test constants updated to reference the contract: the
`embeddingSpecTemplate` in `QdrantSemanticCandidateEvalSpec` and
`QdrantExperimentalHybridServiceIntegrationSpec` (`modelName`/`distance`, `dimension = 1`
left as an intentional reduced test dimension, distinct from the canonical `1024`);
the `QdrantRetrievalSmokeSpec.embeddingSpec` helper in `QdrantLlamaCppRetrievalSmokeSpec`
(`modelName`/`distance`, `dimension` stays a runtime-derived parameter); the
`expectation` fixture in `BeautySearchQdrantSupplementActivationPolicySpec`
(`expectedDimension`/`expectedDistance`/`embeddingModelName`, with a deliberate
`expectedDimension + 1` mismatch case in a second test left untouched); and one
literal `1024` regression-pin assertion in `ManagedLocalEmbeddingPreflightSpec`.
`ManagedLocalSearchBootstrapFingerprintSpec` needed no change - it already
fully delegates through `BeautyQManagedLocalSearchBootstrap`'s own public
surface with no locally hardcoded managed-local literal. Intentionally
different constants left unchanged: `dimension = 1` test-local axes above,
the `wrongDimension`/`actualDimension=512` mismatch branch and the
`"expectedDimension=1024"` message-content substring check (both in
`ManagedLocalEmbeddingPreflightSpec`), and the `expectedDimension + 1`
mismatch fixture in `BeautySearchQdrantSupplementActivationPolicySpec`.

Also removes the last references to the now-deleted standalone coordinator
onboarding doc: its useful north-star/scope-drift content is folded into the
"Coordinator checkpoint" section above, and `docs/beautyq-search-dsl-v1.md`'s
top-of-file pointer now points at this plan and at
`docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md` instead.

`beautyqSearchContract/compile`, `Test/compile`, `beautyqSearchWiring/compile`,
`Test/compile`, `bifunctor-tagless/compile`, and `Test/compile` all succeeded
with no other source changes. No backend ids, backend kinds, runtime
declaration order, capabilities, managed-local model name/dimension/distance,
source-text fields/order, Qdrant payload shape, ES mapping, materialized
document text, bootstrap fingerprints, benchmark report shapes, eval/report
semantics, or route/API behavior changed.

## Phase 26 record: shared eval production-posture metric names centralized

Adds `BeautyQSearchEvaluationMetricNames` (package `leaderboard.search.eval`, in
`beautyq-search-contract`), an `object SharedProductionPosture` declaring the 22
BeautyQ eval production-posture metric name constants (`DefaultBeautySearchEsBacked`,
`QdrantOptInDisabledByDefault`, `QdrantProductionActivationApproved`,
`ProductionRouteActivated`, `DefaultRouteSwitched`, `ProductionBeautySearchCalled`,
`EsClientCreated`, `QdrantClientCreated`, `EsExecuted`, `QdrantExecuted`,
`RoutePluginDiHttpInvolved`, `RealBackendCallRequired`, `RealBackendCallImplemented`,
`HybridServingImplied`, `FallbackImplied`, `ScoreFusionImplied`, `RerankingImplied`,
`ProductionTelemetryImplied`, `QualityGreenClaimed`, `ProductionReadinessClaimed`,
`RouteActivationClaimed`, `ServingApprovalClaimed`) plus an `All` list in that exact
order, used by M9/M10's static scorecards (`beautyq-search-contract`) and the
M10-M14 offline planning/design scorecards (`beautyq-search-wiring`) to prove
"not production serving / not route activation / no real backend execution / no
hidden Qdrant activation" boundaries.

This is name-only centralization, like Phases 24/25: no metric value, metric
order, renderer, scorecard outcome, or eval semantic changed, and the generic
`SearchDomainSpec` shape is untouched - the same mechanical DSL-adoption pattern
as centralizing the Qdrant source-text fields (Phase 24) and the managed-local
Qdrant runtime defaults (Phase 25), not a `SearchDomainSpec` shape change.

Prior to this phase, the same 22 metric-key strings were declared as inline
literals in every `metrics()` builder across ten files: `M9BeautyQSearchEvalStaticScorecard`
(2 of the 22 present: `route_plugin_di_http_involved`, `real_backend_call_required`;
`production_activation_approval` is a distinct M9-only metric and was left
untouched) and `M10BeautyQSearchFullQueryClassificationCoverageScorecard` (all
22) in `beautyq-search-contract`; `M10BeautyQSearchRetrievalPolicyReadiness`,
`M11BeautyQSearchCandidateGenerationInputSkeleton`,
`M11BeautyQSearchCandidateGenerationResultSchema`,
`M12BeautyQSearchFusionRerankingInputScaffold`,
`M12BeautyQSearchFusionRerankingPolicyCatalog`,
`M12BeautyQSearchFusionRerankingSavedOutputSchema`,
`M13BeautyQSearchControlledOptInRoutePlanning`, and
`M14BeautyQSearchRouteGateReadinessDesign` (all 22 each) in `beautyq-search-wiring`.
All ten now reference `BeautyQSearchEvaluationMetricNames.SharedProductionPosture.*`
directly - no import needed, since these `metrics()` builders already live in the
exact same `leaderboard.search.eval` package the new object is declared in.
Module-specific names (e.g. M13/M14's own `route_plugin_di_http_change`,
`qdrant_production_activation` denied-drift-case names, which are textually
similar to but distinct from the shared `route_plugin_di_http_involved` /
`qdrant_production_activation_approved` metrics) were left untouched, along
with every `m11_*`/`m12_*`/`m13_*`/`m14_*` module-specific metric, consumed
verdict, row count, drift case, and artifact id.

Ten corresponding `bifunctor-tagless` test files were updated the same way,
replacing exact-match `metricValue(..., "name")` metric-key lookups with the
constants (import added to each file's existing `leaderboard.search.eval`
import block); `forbiddenRenderedTokens`/`fabricationTokens` prose lists and
`rendered.contains(...)` markdown-content assertions were left untouched since
none of them are exact metric-key literals matching the shared 22 names.

`BeautyQSearchEvaluationContractSpec` now asserts the evaluation scorecard's
metrics include every `SharedProductionPosture.All` name (both M9's 2 and
M10's 22 feed the same distinct `EvalScorecardConfig.metrics` list);
`BeautyQSearchDomainContractSpec` gained one small assertion that
`evaluation.scorecard.metrics` contains a few representative shared posture
names, without weakening any existing `SearchDomainSpec`/evaluation assertion.

`beautyqSearchContract/compile`, `Test/compile`, `beautyqSearchWiring/compile`,
`Test/compile`, `bifunctor-tagless/compile`, and `Test/compile` all succeeded
with no other source changes. No metric string values, metric order, report
markdown output (beyond the source of the string constants), scorecard
outcomes, eval semantics, or generic `SearchDomainSpec` shape changed. This
remains an exact-duplicate Sonnet patch, same class of change as Phases 24/25;
the Fable 5 countdown stays reserved for a future shape/architecture decision,
not this kind of mechanical name centralization.

## Phase 27 record: residual M9/M11/M12/M20/M21 eval boundary surfaces adopt shared production-posture names

Closes out the `BeautyQSearchEvaluationMetricNames.SharedProductionPosture` (Phase
26) consumer-adoption sweep over the eval boundary surfaces Phase 26 did not
reach: `M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan`,
`M9BeautyQSearchEvalRealResourcePrerequisitesAudit`,
`M9BeautyQSearchEvalCombinedSmokeComparisonPlan`,
`M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix`,
`M12BeautyQSearchFusionRerankingExperimentPlan`,
`M12BeautyQSearchFusionRerankingBoundaryFailureMatrix`,
`M20CControlledHybridServingCloseout`, and
`M21ProductionActivationDecisionPackage` (all in `beautyq-search-wiring`).

Like Phases 24/25/26, this is exact name-only consumer adoption: no new
`BeautyQSearchEvaluationMetricNames` constant was added, no metric value,
metric order, rendered markdown, checked-in artifact byte content, scorecard
outcome, or eval semantic changed, and the generic `SearchDomainSpec` shape is
untouched.

The two M9 plan files (`QdrantOnlySmokeExecutionPlan`,
`CombinedSmokeComparisonPlan`) each had exactly one exact-match occurrence -
their `PlanReason.render` enum's `DefaultBeautySearchEsBacked` case - now
returning `SharedProductionPosture.DefaultBeautySearchEsBacked` instead of the
inline literal. `M9BeautyQSearchEvalRealResourcePrerequisitesAudit` had one
occurrence inside `MinimumValidationEvidenceFields`
(`"real_backend_call_implemented"`), now the constant. M9-only reason names
that are textually similar but distinct in value - `production_activation_not_approved`,
`qdrant_production_activation_not_approved`,
`qdrant_production_activation_not_approved_confirmed`, `no_production_route_change`,
`real_backend_call_not_implemented`, `production_activation_boundary_not_approved_standing_boundary`
- were left untouched, as were all smoke/comparison/prerequisite-specific labels.

`M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix`,
`M12BeautyQSearchFusionRerankingExperimentPlan`, and
`M12BeautyQSearchFusionRerankingBoundaryFailureMatrix` each had the full,
identical 22-entry `SharedProductionPosture` block inline in their `metrics()`
builders (the same block Phase 26 already centralized in their sibling M11/M12
files); all three now reference the 22 constants directly, in the same order.
Matrix-specific names (`matrix_*`, `accepted_row_count`, `denied_row_count`,
`reason_code_count_sum`, `m11_*`/`m12_*` consumed-verdict/count metrics, case
ids, and reason codes) were left untouched.

`M20CControlledHybridServingCloseout` and `M21ProductionActivationDecisionPackage`
each had one exact-match occurrence of `qdrant_production_activation_approved`
inside their human-readable `render: String` line
(`s"qdrant_production_activation_approved=$qdrantProductionActivationApproved"`),
now `s"${SharedProductionPosture.QdrantProductionActivationApproved}=$qdrantProductionActivationApproved"`
- the rendered text is byte-identical since the constant's value equals the
literal it replaced. `M20CControlledHybridServingCloseout.CloseoutEvidence.missingEvidence`
also contains one `"qdrant_production_activation_approved"` entry, but that
list is a closeout-specific blocker-code report (parallel to the M9/M11/M12
reason-code enums), not a `metrics()` builder, so it was deliberately left as
a local literal rather than folded into this adoption pass; it is reported as
a known remaining exact-string match, not missed by accident.
`qdrant_production_activation_approval_still_absent`, `production_activation_approved=false`,
`serving_approved`, `fallback_enabled`, `score_fusion_enabled`,
`approve_production_activation`, `reject_production_activation`,
`defer_production_activation`, and every other `m20_*`/`m21_*` name were left
untouched.

Corresponding `bifunctor-tagless` specs were updated the same way:
`M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSpec` and
`M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSpec` had their exact-match
`metricValue(..., "name")` shared-key lookups replaced with the constants
(import added to each file's existing `leaderboard.search.eval` import block).
The three M9 spec files assert the plan/audit boolean accessor methods
directly (never a string-keyed metric lookup), so none needed a change.
`M20CControlledHybridServingCloseoutSpec` and `M21ProductionActivationDecisionPackageSpec`
only assert the rendered text output (e.g.
`rendered.contains("qdrant_production_activation_approved=false")`), which
stays byte-identical, so neither needed a change either. No file named
`M12BeautyQSearchFusionRerankingExperimentPlanSpec.scala` exists in the repo,
but the class `M12BeautyQSearchFusionRerankingExperimentPlanSpec` does exist -
as a second top-level `AnyWordSpec` in
`M12BeautyQSearchFusionRerankingPolicyCatalogSpec.scala`, alongside
`M12BeautyQSearchFusionRerankingPolicyCatalogSpec` itself - and
`sbt bifunctor-tagless/testOnly *M12BeautyQSearchFusionRerankingExperimentPlanSpec`
runs its 19 tests directly. Both classes in that file already asserted their
shared-key `metricValue` lookups through
`BeautyQSearchEvaluationMetricNames.SharedProductionPosture.*` before this
phase (bundled into the same file edit when Phase 26 updated
`M12BeautyQSearchFusionRerankingPolicyCatalog`'s corresponding spec), so no
test-side edit was needed here even though the production-side
`M12BeautyQSearchFusionRerankingExperimentPlan.scala` still had inline
literals until this phase fixed them: the string-equality lookup passes
either way, which is why every one of these tests was green both before and
after this phase's production-side edit.

Checked-in `bifunctor-tagless/src/test/resources/leaderboard/search/eval/`
markdown artifacts (`m11-beautyq-candidate-generation-boundary-failure-matrix.md`,
`m12-beautyq-fusion-reranking-boundary-failure-matrix.md`) remain
byte-identical, confirmed both by `git diff` showing no changes and by the
existing byte-for-byte checked-in-artifact spec assertions passing unchanged.

`beautyqSearchWiring/compile`, `Test/compile`, `bifunctor-tagless/compile`, and
`Test/compile` all succeeded with no other source changes. This remains an
exact-duplicate Sonnet patch, same class of change as Phases 24/25/26; the
Fable 5 countdown stays reserved for a future shape/architecture decision, not
this kind of mechanical name adoption.

## Phase 8d record: static/offline evaluation contract section extracted

This slice moves the pure static/offline BeautyQ evaluation declarations
(M8/M9/M10 offline planning/eval contracts) into `beautyq-search-contract`
and adds a `BeautyQSearchEvaluationContract` aggregate wired into
`BeautyQSearchDomainContract.evaluation` as a generic `EvalSection` - the
first BeautyQ contract slice expressed directly through `search-contract-core`
ADTs rather than `search-core` ones.

Moved, package preserved (`leaderboard.search.eval`), objects/classes/enums
not renamed:

- `M8M9EvalContracts.scala` - shared M8 telemetry-schema-plan and M9
  offline-eval enums/value types (`ServingMode`, `CandidateSource`,
  `FusionPolicy`, `RerankerPolicy`, `QueryClass`, `OfflineEvalMetricName`,
  etc.).
- `M9BeautyQSearchEvalQueryDataset.scala` - the 89-query BeautyQ seed eval
  dataset metadata (`datasetId = "wandsbek_hamburg_beauty_services_seed_ready"`,
  `queryCount = 89`).
- `M9BeautyQSearchEvalQueryDatasetStaticRows.scala` - the checked-in 89
  static query ids and their static-row mapping (`StaticQueryIds`,
  `DefaultResult`).
- `M9BeautyQSearchEvalStaticScorecard.scala` - static/offline dataset
  readiness scorecard and markdown renderer.
- `M9OfflineEvalSavedReport.scala` - the saved offline-eval report shape and
  markdown renderer.
- `M9OfflineEvalStaticRunner.scala` - the pure static-run validator/summarizer.
- `M9OfflineEvalStaticFixtures.scala` - canonical M9 static fixture rows and
  example artifacts.
- `M10BeautyQSearchQueryClassification.scala` - offline query
  category/signal classification.
- `M10BeautyQSearchFullQueryClassification.scala` - full 89-query offline
  classification mapping.
- `M10BeautyQSearchOfflineRoutingPolicy.scala` - offline strategy-intent
  mapping and the `M10BeautyQSearchOfflineRoutingBoundary.Standing` value
  (every production-activation/execution/behavior-change field `false`).
- `M10BeautyQSearchFullQueryClassificationCoverageScorecard.scala` - offline
  classification/routing-intent coverage scorecard over all 89 rows.

Each file was independently verified to import nothing beyond sibling
`leaderboard.search.eval` symbols before moving - no HTTP/tapir/routes,
clients, repositories, materialization, seed, bootstrap, ES/Qdrant concrete
clients, ZIO, doobie, Postgres, Docker, or runtime backends anywhere in this
slice.

Added: `beautyq-search-contract/src/main/scala/leaderboard/search/beautyq/contract/BeautyQSearchEvaluationContract.scala`
- references the moved static declarations (`datasetMetadata`, `staticRows`,
  `staticScorecard`, `fullClassificationCoverage`, `offlineRoutingBoundary`)
  and builds `section: EvalSection` (from `search-contract-core`):
  `acceptedQueryRoles` (Golden/Negative/Exploratory), `negativeControls` (from
  `M10BeautyQSearchFullQueryClassification.AcceptedNegativeControlQueryIds`),
  `backendExpectations` (ES + Qdrant, both `expectedMinRecall = None`),
  `scorecard` (static-scorecard + full-classification-coverage metric names,
  deduplicated), and `productionRoutingEffect = EvalProductionRoutingEffect.None`
  - the single-inhabitant marker type, not a boolean, so this can never be
  constructed claiming otherwise.

`BeautyQSearchDomainContract.scala` updated:

- `val evaluation = BeautyQSearchEvaluationContract.section` added.
- `evaluationDeclared` changed from `false` to `true`.
- `fullSearchDomainSpecDeclared` stays `false` - no generic
  `SearchDomainSpec[...]` value exists yet, and this slice does not add one
  even though the evaluation section is now expressed through the generic
  `EvalSection` ADT: catalog/document/intent/runtime/response are still
  `search-core`-shaped, so a full `SearchDomainSpec[Catalog, Document,
  ResultUnit]` assembly remains future work.
- The label continues to state the aggregate is not a complete
  `SearchDomainSpec`.

Tests: added `BeautyQSearchEvaluationContractSpec.scala` (9 cases) covering
the 89-query dataset metadata, static-row/full-classification-coverage row
counts matching that 89, the static scorecard never claiming production
activation approval, every offline-routing-boundary claim-bearing field
being `false`, `section.productionRoutingEffect == EvalProductionRoutingEffect.None`,
ES+Qdrant backend expectations with no recall claim, representative scorecard
metric names, and that no full `SearchDomainSpec` is declared. Updated
`BeautyQSearchDomainContractSpec.scala`: the old `evaluationDeclared == false`
assertion became `true` plus a new `evaluation eq BeautyQSearchEvaluationContract.section`
identity assertion; `fullSearchDomainSpecDeclared == false` kept unchanged.

Not moved in this slice: JSON/resource parsing (the checked-in
`beautyq_search_eval_queries_v1.json` resource itself, and any parser for
it), the eval runtime harness / real-backend runners, ES/Qdrant concrete
clients, routes/plugins/API/http/tapir, and bootstrap/startup/seed/
repositories/materialization/projection - all remain in `bifunctor-tagless`.
No production route activation, fallback, fusion, rerank, telemetry, or
Qdrant production approval was introduced. Existing eval tests
(`M9OfflineEvalStaticRunnerSpec`, `M9OfflineEvalStaticFixturesSpec`,
`M9BeautyQSearchEvalQueryDatasetSpec`, `M9BeautyQSearchEvalQueryDatasetStaticRowsSpec`,
`M9BeautyQSearchEvalStaticScorecardSpec`, `M10BeautyQSearchQueryClassificationSpec`,
`M10BeautyQSearchOfflineRoutingPolicySpec`,
`M10BeautyQSearchFullQueryClassificationCoverageSpec`) needed zero import
changes - package preservation made that unnecessary - and all still pass
unchanged.

Build changes: none - `build.sbt` was not touched. All 11 moved files
compiled cleanly in `beautyq-search-contract` on the first attempt with the
module's existing dependencies.

Deeper evaluation harness / full `SearchDomainSpec` assembly remains pending.

## Phase 8e record: full SearchDomainSpec readiness blockers made explicit

No full `SearchDomainSpec[...]` value was constructed in this slice. Added
`BeautyQSearchDomainSpecReadiness` (and `BeautyQSearchDomainSpecPendingDecision`)
in `beautyq-search-contract`, a pure data record of what still blocks a full
`SearchDomainSpec` assembly, instead of a comment-only claim.

Ready sections: `catalog`, `evaluation`. Pending decisions (id / section):
`document-result-unit` / `document`, `intent-languages` / `intent`,
`document-field-kind-mapping` / `document`, `runtime-capabilities` /
`runtime`, `response-policy` / `response`.

`BeautyQSearchDomainContract` gained `searchDomainSpecReadiness =
BeautyQSearchDomainSpecReadiness.current`; `fullSearchDomainSpecDeclared` now
delegates to `searchDomainSpecReadiness.fullSearchDomainSpecDeclared` and
remains `false`, backed by these non-empty pending decisions rather than a
bare literal. `evaluationDeclared` stays `true`.

This is a prerequisite for a later full `SearchDomainSpec` assembly, not that
assembly itself. No runtime/client/repo/materialization/seed/app behavior
moved; `build.sbt` untouched.

## Phase 8f record: BeautyQ variant result unit declared

Added `BeautyQSearchResultUnitContract` (and `BeautyQSearchResultUnit`) in
`beautyq-search-contract`: a pure descriptor (`id = "variant"`, `label =
"BeautyQ variant result"`, `documentIndexName = "beautyq_variant_v1"`,
`carouselLimitName = "variantSize"`) for the value `DocumentSection`'s
`resultUnit` field requires. `BeautyQSearchDomainContract` gained `resultUnit
= BeautyQSearchResultUnitContract.variant`.

This resolves the `document-result-unit` readiness blocker: readiness now has
ready sections `catalog`, `evaluation`, and `document-result-unit`. Remaining
blockers are `intent-languages`, `document-field-kind-mapping`,
`runtime-capabilities`, and `response-policy` - unchanged and in the same
relative order. `fullSearchDomainSpecDeclared` remains `false`.

No full `SearchDomainSpec` value was constructed, and no
runtime/repo/materialization/seed/app/client behavior moved; `build.sbt`
untouched.

## Phase 8g record: BeautyQ supported search languages declared

Added `BeautyQSearchLanguageContract` in `beautyq-search-contract`:
`supported` declares `de`, `en`, `ru` (in that order, matching the eval
validation report's language key order) as the `List[SearchLanguage]`
`IntentSection` requires. `mixed_language` in that report remains eval
metadata/tagging, not a declared `SearchLanguage`. `BeautyQSearchDomainContract`
gained `languages = BeautyQSearchLanguageContract.supported`.

This resolves the `intent-languages` readiness blocker: readiness now has
ready sections `catalog`, `evaluation`, `document-result-unit`, and
`intent-languages`. Remaining blockers are `document-field-kind-mapping`,
`runtime-capabilities`, and `response-policy`. `fullSearchDomainSpecDeclared`
remains `false`.

No full `SearchDomainSpec` value was constructed, and no
runtime/repo/materialization/seed/app/client behavior moved; `build.sbt`
untouched.

## Phase 8h record: BeautyQ generic document fields declared

Added `BeautyQSearchDocumentFieldContract` in `beautyq-search-contract`:
`fields` maps every `BeautyQVariantSearchDocumentContract.documentSpec.fields`
entry into a generic `search-contract-core` `SearchField`. Text fields map to
generic `Text`, not `SemanticText`, since `BeautySearchSpecV1.spec` declares
no embedding spec. Facetable keyword/boolean fields map to generic `Facet`;
facetable integer/decimal fields map to generic `Range`; non-facetable
integer/decimal fields map to generic `Numeric`; old `GeoPoint` maps to
generic `Geo`. `BeautyQSearchDomainContract` gained `fields =
BeautyQSearchDocumentFieldContract.fields`.

This resolves the `document-field-kind-mapping` readiness blocker: readiness
now has ready sections `catalog`, `evaluation`, `document-result-unit`,
`intent-languages`, and `document-field-kind-mapping`. Remaining blockers are
`runtime-capabilities` and `response-policy`. `fullSearchDomainSpecDeclared`
remains `false`.

No full `SearchDomainSpec` value was constructed, and no
runtime/repo/materialization/seed/app/client behavior moved; `build.sbt`
untouched.

## Phase 8i record: BeautyQ runtime and response sections declared

Added `BeautyQSearchRuntimeContract` in `beautyq-search-contract`:
`section: RuntimeSection` declares Elasticsearch as full-text/facet/geo
capable and not semantic-vector capable, and Qdrant as semantic-vector
capable and not full-text/facet/geo capable - matching
`ElasticsearchSearchRequestInterpreter`'s source-confirmed ES support and
Qdrant's current vector/semantic candidate/supplement role.

Added `BeautyQSearchResponsePolicyContract` in `beautyq-search-contract`:
`section: ResponseSection` declares generic response policy. Generic
grouping is intentionally `None`, since BeautyQ has two component-specific
grouping fields (provider carousel groups by `masterLocationId`,
service-intent carousel groups by `serviceId`) and generic `GroupingPolicy`
has only one grouping slot; the component-specific fields are preserved in
`BeautyQSearchResponsePolicyDetails` instead. Generic carousel `maxItems`
uses the variant result-unit limit (`variantSize = 10`); provider/
service-intent carousel limits remain BeautyQ-specific details. Generic
facets derive from `BeautySearchSpecV1.spec.facetSpec.fields`; inferred
filters are described from `FacetSpec` enabled/threshold/min-count values.
Presentation labels are empty and debug flags are `false`, since no generic
source-owned labels/debug flags exist. `BeautyQSearchDomainContract` gained
`runtimeSection = BeautyQSearchRuntimeContract.section` and `response =
BeautyQSearchResponsePolicyContract.section`.

This resolves the `runtime-capabilities` and `response-policy` readiness
blockers: readiness now has ready sections `catalog`, `evaluation`,
`document-result-unit`, `intent-languages`, `document-field-kind-mapping`,
`runtime-capabilities`, and `response-policy`. A new pending decision,
`intent-section-mapping` (section `intent`), replaces them: generic
`IntentSection` still needs vocabularies/vocabularyGroups/noiseControls
mapping beyond the supported-language list already declared in Phase 8g;
BeautyQ still exposes only a search-core `SearchIntentVocabulary`.
`fullSearchDomainSpecDeclared` remains `false`.

No full `SearchDomainSpec` value was constructed, and no
runtime/repo/materialization/seed/app/client behavior moved; `build.sbt`
untouched.

## Phase 8j record: BeautyQ generic intent section and full SearchDomainSpec declared

Added `BeautyQSearchIntentSectionContract` in `beautyq-search-contract`:
`section: IntentSection` uses `BeautyQSearchLanguageContract.supported` for
`languages`, maps `BeautyQSearchIntentVocabulary.vocabulary.rules`' structured
aliases to generic `SearchVocabulary` entries (sorted `terms`, grouped under
a `"structured-aliases"` `SearchVocabularyGroup`), and maps query-noise
phrases to generic `NoiseControl` entries (sorted `excludedTerms`).
`SearchVocabulary.synonyms` stays `Map.empty` throughout: old
`SearchIntentRule` structured aliases are explicitly not lexical analyzer
synonyms, and generic `SearchVocabulary` has no fields for
`constraints`/`softBoosts`/`requires`/`excludes`, so those are intentionally
dropped rather than encoded.

`BeautyQSearchDomainContract` gained `intentSection =
BeautyQSearchIntentSectionContract.section`, `documentSection =
DocumentSection(document, resultUnit, fields)` (assembled from the
already-owned document/result-unit/field slices), and `searchDomainSpec =
SearchDomainSpec(id, catalog, documentSection, intentSection, runtimeSection,
response, evaluation)` - the full generic `SearchDomainSpec`, assembled
entirely from contract-owned sections. `label` no longer says "not complete
SearchDomainSpec".

This resolves the final `intent-section-mapping` readiness blocker: readiness
now has ready sections `catalog`, `evaluation`, `document-result-unit`,
`intent-languages`, `document-field-kind-mapping`, `runtime-capabilities`,
`response-policy`, and `intent-section-mapping`, with `pendingDecisions =
Nil`. `fullSearchDomainSpecDeclared = true`.

No production routing, route activation, Qdrant production approval, or
runtime behavior changed; no runtime/repo/materialization/seed/app/client
code moved; `build.sbt` untouched.

## Phase 7b record: BeautyQ variant document projection engine extracted into beautyq-search-materialization

Continues Phase 7's catalog-materialization slice with the document side:
the actual row-projection engine (previously inline in
`bifunctor-tagless`'s `BeautyQVariantSearchDocumentSchema`) moved next to
`BeautyQCatalogGraph` in `beautyq-search-materialization`, leaving
`BeautyQVariantSearchDocumentSchema` as a thin compatibility facade.

Moved, package preserved (`leaderboard.search.document`):

- `beautyq-search-materialization/src/main/scala/leaderboard/search/document/SearchDocumentProjection.scala`
  (from `bifunctor-tagless`) - the generic join/index/invariant-check
  projection helper, unchanged.

Added: `beautyq-search-materialization/src/main/scala/leaderboard/search/document/BeautyQVariantSearchDocumentMaterialization.scala`
- owns the actual BeautyQ projection engine: `BeautyQCatalogGraph.Nodes`
  handles (category/service/master/masterLocation/masterServiceOffer),
  `project(categories, services, serviceVariantSchemas, masters,
  masterLocations, masterServiceOffers, masterServiceOfferVariants)`,
  `buildDocument`, `validateAgainstSchema`, `makeAttributeTokens`,
  `humanize`, `normalizeText` - copied verbatim from
  `BeautyQVariantSearchDocumentSchema.project`/`buildDocument`/etc, with
  `project` taking plain lists instead of the seed-coupled
  `BeautySearchCatalogSnapshot` (so this engine has no seed dependency).
  Source ordering, first-occurrence dedup, missing-entity messages, the
  cross-master invariant failure message, the schema-validation error
  message, enum/boolean/int/decimal attribute projection, `humanize`,
  `normalizeText`, text field construction order, and every
  `VariantSearchDocument` field value are unchanged.

`bifunctor-tagless`'s `BeautyQVariantSearchDocumentSchema.scala` edited to a
thin facade: still exposes `Fields`, `documentSpec`, `qdrantPayloadSpec`,
`querySchema`, `projection`, and `project(snapshot: BeautySearchCatalogSnapshot)`
unchanged in signature, but `project` now only unpacks the snapshot's seven
lists and delegates to `BeautyQVariantSearchDocumentMaterialization.project(...)`.
The direct `BeautyQCatalogGraph`/`ServiceVariantSchemas` imports and all the
node-handle/`buildDocument`/`validateAgainstSchema`/`makeAttributeTokens`/
`humanize`/`normalizeText` code were removed from this file - moved, not
duplicated.

Not moved (seed-coupled, stay in `bifunctor-tagless`'s `VariantSearchDocument.scala`,
which was not edited): `BeautySearchCatalogSnapshot` (including `fromSeedData`),
`VariantSearchDocumentBuilder`, `BeautySearchCatalogSnapshotLoader`
(`FromRepositories` and `SeedScopedFromRepositories`) - these depend on
`leaderboard.seed.{BeautyQSeedData, BeautyQSeedReady}`, and seed code stays
in `bifunctor-tagless` for this patch. `VariantSearchDocumentSnapshotProvider.scala`
was likewise not touched.

Build changes: none - `build.sbt` was not touched.
`beautyq-search-materialization` already depended on `beautyqSearchContract`
(for `VariantSearchDocument`, transitively `search-core`'s `SearchGeoPoint`/
`SearchDocumentSpec`), `beautyqSearchRepositories`, `repoCore`, and
`beautyqModel`, which covered every symbol both moved/new files use.

No seed/bootstrap/startup code moved. No routes/plugins/API/http/tapir
moved. No ES/Qdrant clients or interpreters moved. No repository code moved.
No production route activation, fallback, fusion, rerank, telemetry, or
behavior change - the projection engine is a verbatim copy with an adapted
(list-based instead of snapshot-based) parameter shape, and the facade's
public signatures are unchanged.

Tests: no test file needed editing - package preservation meant existing
`BeautyQVariantSearchDocumentSchemaSpec` (19 cases), `BeautyQRepoGraphLoaderSpec`
(10 cases), `VariantSearchDocumentSnapshotProviderSpec` (4 cases),
`BeautySearchReadyCatalogDocumentsSpec` (4 cases), and
`ManagedLocalSearchBootstrapSpec` (2 cases) all still resolve the facade
unchanged and pass unchanged.

Deeper seed/snapshot/loader extraction remains pending.

## Phase 7c record: BeautyQ catalog snapshot loaders moved into beautyq-search-materialization

Follow-up to Phase 7b, covering the snapshot-loading side left pending
there: `BeautyQSearchCatalogSnapshot` was added in
`beautyq-search-materialization` as the seed-free materialization-owned
snapshot type, and `BeautyQSearchCatalogSnapshotLoader.FromRepositories` was
added there as the real full-catalog repo-backed snapshot loader; legacy
`BeautySearchCatalogSnapshotLoader.FromRepositories` in `bifunctor-tagless`
is now a compatibility facade delegating to the materialization loader and
converting back to legacy `BeautySearchCatalogSnapshot`.

`BeautyQSearchCatalogSeedScope` was added in `beautyq-search-materialization`
as a seed-free scope type for seed-scoped loading, and
`BeautyQSearchCatalogSnapshotLoader.SeedScopedFromRepositories` was added
there as the real seed-scoped repo-backed snapshot loader. Legacy
`BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` in
`bifunctor-tagless` keeps its old constructor signature, including
`BeautyQSeedReady` and `BeautyQSeedData`, but now adapts `BeautyQSeedData` to
`BeautyQSearchCatalogSeedScope`, delegates to the materialization loader, and
converts back to legacy `BeautySearchCatalogSnapshot`.

`BeautySearchCatalogSnapshot` and `BeautySearchCatalogSnapshot.fromSeedData`
still remain in `bifunctor-tagless`, as do `BeautyQSeedData`,
`BeautyQSeedLoader`, `BeautyQSeedInserter`, and `BeautyQSeedReady`.
`beautyq-search-materialization` still has no `leaderboard.seed` dependency.

No build changes, and no routes/plugin modules/launchers/HTTP/Tapir code/
bootstrap/startup code/ES/Qdrant clients/production policy/document
projection behavior/seed data loading behavior/production routing changes
were made.

Focused verification for this slice: `sbt beautyqSearchMaterialization/compile`,
`sbt bifunctor-tagless/compile`, `sbt bifunctor-tagless/testOnly *BeautyQRepoGraphLoaderSpec`,
`sbt bifunctor-tagless/testOnly *BeautyQVariantSearchDocumentSchemaSpec`, and
`sbt bifunctor-tagless/testOnly *VariantSearchDocumentSnapshotProviderSpec` -
not a full `sbt test` run.

## Phase 7d record: BeautyQ variant projection consumes materialization-owned catalog snapshots

Follow-up to Phase 7c, closing the loop between the materialization-owned
snapshot type and the projection engine. `BeautyQVariantSearchDocumentMaterialization`
in `beautyq-search-materialization` gained a snapshot-level
`project(snapshot: BeautyQSearchCatalogSnapshot)` overload, delegating to the
existing seven-list `project(...)` method, which is unchanged and still kept.

`BeautySearchCatalogSnapshot`'s companion object in `bifunctor-tagless` is now
the single place holding both snapshot conversions:
`fromMaterializationSnapshot(BeautyQSearchCatalogSnapshot): BeautySearchCatalogSnapshot`
and `toMaterializationSnapshot(BeautySearchCatalogSnapshot): BeautyQSearchCatalogSnapshot`.
`BeautySearchCatalogSnapshotLoader.FromRepositories`/`SeedScopedFromRepositories`
now call the companion-owned `fromMaterializationSnapshot` instead of a
duplicate private helper, which was removed. `BeautyQVariantSearchDocumentSchema.project`
now calls the new snapshot-level `BeautyQVariantSearchDocumentMaterialization.project`
overload via `BeautySearchCatalogSnapshot.toMaterializationSnapshot`, and
remains a `bifunctor-tagless` facade adapting the legacy snapshot shape.

`BeautySearchCatalogSnapshot.fromSeedData` remains the seed-coupled adapter in
`bifunctor-tagless`: it still builds a `BeautyQSearchCatalogSnapshot` from the
seven seed lists directly (including `seed.serviceVariantSchemas`, not
`BeautyQSearchCatalogSeedScope`, which intentionally omits it) and converts it
via `fromMaterializationSnapshot`. No seed types moved.
`beautyq-search-materialization` still has no `leaderboard.seed` dependency.

This is not full `SearchDomainSpec` work and does not change production
behavior; `fullSearchDomainSpecDeclared` stays `false`.

Focused verification for this slice: `sbt beautyqSearchMaterialization/compile`,
`sbt bifunctor-tagless/compile`, `sbt bifunctor-tagless/testOnly *BeautyQVariantSearchDocumentSchemaSpec`,
and `sbt bifunctor-tagless/testOnly *VariantSearchDocumentSnapshotProviderSpec` -
not a full `sbt test` run.

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
