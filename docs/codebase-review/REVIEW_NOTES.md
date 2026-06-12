# Codebase review notes

## Review metadata

- Review date/time: 2026-06-07, generated during Codex pass 1.
- Repository root: `/Users/do/git/sandbox/distage-example`.
- Scope requested: factual inventory only; no production-code changes; no final architecture guide.
- Files created/updated in this pass:
  - `docs/codebase-review/REVIEW_NOTES.md`
  - `docs/codebase-review/INVENTORY.md`

## Commands run

- `pwd`
- `git status --short`
- `find . -maxdepth 2 -type f \( -name 'build.sbt' -o -name '*.sbt' -o -name 'AGENTS.md' -o -name '*.md' \) | sort | sed 's#^./##' | head -200`
- `find . -maxdepth 2 -type d | sort | sed 's#^./##' | head -200`
- `find bifunctor-tagless/src -maxdepth 5 -type f | sort | sed 's#^#/#' | head -400`
- `find docs -maxdepth 3 -type f | sort`
- `find bifunctor-tagless/src -type f \( -name '*.sql' -o -name '*.json' -o -name '*.conf' -o -name '*.yaml' -o -name '*.yml' \) | sort`
- `rg -n "(class|trait|object|case class|enum) (.*(Master|Salon|Service|Offer|Variant|Search|Repository|Postgres|Qdrant|Hybrid|Retrieval|Seed|Benchmark|Carousel|Location|Geo|Api|Endpoints|Module|App|Launcher))" bifunctor-tagless/src docs build.sbt project -S`
- `find bifunctor-tagless/src/main/scala/leaderboard/search -type f | sort`
- `find bifunctor-tagless/src/main/scala/leaderboard -type f | sort | grep -v '/search/'`
- `find bifunctor-tagless/src/test/scala/leaderboard -type f | sort`
- `find bifunctor-tagless/src -type f \( -name '*.scala' -o -name '*.sql' -o -name '*.conf' -o -name '*.json' \) -print0 | xargs -0 rg -n "(ignored|ignore|pending|manual|local|NonProduction|Experiment|memoizationRoots|ModuleDef|make\[|many\[|Activation|Repo|Scene|transaction|transact|CREATE TABLE|REFERENCES|FOREIGN KEY|matched_queries|carousel|geo|lat|lon|latitude|longitude)" -S`
- `sed -n '1,260p' bifunctor-tagless/src/main/scala/leaderboard/model/package.scala`
- `sed -n '1,260p' bifunctor-tagless/src/main/scala/leaderboard/model/MasterServiceOfferVariant.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/model/AttributeDefinition.scala`
- `sed -n '1,720p' bifunctor-tagless/src/main/scala/leaderboard/LeaderboardRole.scala`
- `for f in bifunctor-tagless/src/main/scala/leaderboard/repo/*.scala; do echo '###' $f; rg -n "^(trait|object|  class|  final class|  private def|  def|    def|  case class|  final case class)|create table|references|foreign key|insert into|select|delete from|update" $f -i; done`
- `for f in bifunctor-tagless/src/main/scala/leaderboard/search/**/*.scala bifunctor-tagless/src/main/scala/leaderboard/search/*.scala; do echo '###' $f; rg -n "^(trait|object|final class|final case class|sealed trait|case object|  def|  private def|  final case class|  case object|  class)|def (search|build|assemble|project|run|index|upsert|parse|interpret|route|load|benchmark|compare)" $f; done`
- `sed -n '1,260p' bifunctor-tagless/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala`
- `sed -n '1,180p' bifunctor-tagless/src/test/scala/leaderboard/testkit/LeaderboardSpecSupport.scala`
- `sed -n '1,140p' bifunctor-tagless/src/main/resources/common-reference.conf`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/seed/BeautyQSeedData.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/seed/BeautyQSeedInserter.scala`
- `sed -n '1,120p' bifunctor-tagless/src/main/scala/leaderboard/seed/BeautyQSeedLoader.scala`
- `for f in docs/*.md README.md beautyq_search_eval_plan_v1.md; do echo '###' $f; sed -n '1,80p' $f; done`
- `rg -n '"(categories|services|masters|masterLocations|masterServiceOffers|masterServiceOfferVariants|serviceVariantSchemas|queries|expected|benchmark|eval)' bifunctor-tagless/src/main/resources/seed/wandsbek_hamburg_beauty_services_seed_ready.json bifunctor-tagless/src/test -S | head -300`
- Final verification: `git diff --check`

## Files/directories inspected

- Top-level build/docs: `build.sbt`, `project/plugins.sbt`, `README.md`, `beautyq_search_eval_plan_v1.md`, `docs/*.md`.
- Main source root: `bifunctor-tagless/src/main/scala/leaderboard`.
- Domain model: `bifunctor-tagless/src/main/scala/leaderboard/model`.
- API and Tapir endpoints: `bifunctor-tagless/src/main/scala/leaderboard/api`, `bifunctor-tagless/src/main/scala/leaderboard/http`, `bifunctor-tagless/src/main/scala/leaderboard/http/tapir`.
- Repository/persistence source: `bifunctor-tagless/src/main/scala/leaderboard/repo`, `bifunctor-tagless/src/main/scala/leaderboard/sql`, `bifunctor-tagless/src/main/scala/leaderboard/config`.
- Seed source/data: `bifunctor-tagless/src/main/scala/leaderboard/seed`, `bifunctor-tagless/src/main/resources/seed/wandsbek_hamburg_beauty_services_seed_ready.json`.
- Search source: `bifunctor-tagless/src/main/scala/leaderboard/search` and subpackages `document`, `dsl`, `elasticsearch`, `embedding`, `eval`, `hybrid`, `inmemory`, `interpreter`, `lexical`, `parser`, `qdrant`, `routing`, `semantic`.
- Runtime/wiring: `bifunctor-tagless/src/main/scala/leaderboard/LeaderboardRole.scala`, `bifunctor-tagless/src/main/scala/leaderboard/plugins`.
- Test source: `bifunctor-tagless/src/test/scala/leaderboard` including API contract, repository, attributes, variants, wiring, seed, ranking, and search suites.

## Tests/checks run

- `git diff --check`: passed.

## Tests/checks not run and why

- No SBT compile/test command was run. This pass only creates Markdown inventory files and does not change production or test code.
- Full `sbt test` was not run because it is expensive and likely requires Docker/Postgres/Qdrant/local resources for several suites.
- Focused tests were not run because the requested output is documentation-only inventory, not a behavior change.

## Assumptions made

- The active repository under review is the current working directory `/Users/do/git/sandbox/distage-example`.
- The primary application module is `bifunctor-tagless`, supported by `build.sbt` and the visible source/test tree.
- Because no standalone migration directory or `.sql` files were found, schema inventory is based on `create table if not exists` statements inside `leaderboard.repo.*.Postgres` classes.
- Search/Qdrant/hybrid status labels in `INVENTORY.md` are based on names and inspected code/docs/tests. Anything named `NonProduction`, `Experimental`, `Smoke`, `Manual`, env-gated, or documented as non-production is not classified as production/current behavior.

## Unresolved questions and uncertainty

- Production search wiring is unclear from inspected files: `BeautySearchService.Impl`, search backends, Elasticsearch client usage, and API exposure exist in source, but this pass did not find a production HTTP route that exposes Beauty search.
- Elasticsearch runtime client/resource wiring was not fully enumerated in the first pass; only interpreters/tests/docs were inventoried.
- The docs state some current search coverage numbers and user-verified green statuses; this pass did not rerun those tests, so those claims are recorded as existing documentation, not independently verified.
- The term `Salon` was requested, but inspected model files show `MasterLocation` and provider/location-oriented search projections rather than a first-class `Salon` model. Pass 2 should confirm whether salon is represented implicitly by `MasterLocation`, `Master`, or another concept.
- Availability/scheduling was requested, but no obvious availability/schedule domain model was found in the inspected paths. Pass 2 should run deeper searches for appointment/calendar/time-slot terms if this matters.
- Some docs such as `http-master-service-offer-variant-typed-get-plan.md` describe a historical/current state that may have been superseded by code and `http-legacy-json-contracts.md`; pass 2 should reconcile doc drift.

## Possible inconsistencies discovered

- `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md` says the currently migrated slices are `LadderApi`, `MasterApi`, and `ProfileApi`, but inspected source contains Tapir endpoint files for `Category`, `Service`, `MasterLocation`, `MasterServiceOffer`, and `MasterServiceOfferVariant` as well under `bifunctor-tagless/src/main/scala/leaderboard/http/tapir`.
- `docs/http-master-service-offer-variant-typed-get-plan.md` says `MasterServiceOfferVariantApi` is the last remaining Beauty legacy single-entity GET endpoint, while `docs/http-legacy-json-contracts.md` says no Beauty single-entity GET endpoints remain on the legacy path and names `MasterServiceOfferVariantApi` as migrated on June 4, 2026.
- `README.md` still describes this as the generic `distage-example`; BeautyQ-specific source/docs are present under `bifunctor-tagless` and `docs`.

## Next-pass recommendations

- Verify production entrypoints and HTTP routes for search specifically: find whether any search API is bound in `LeaderboardPlugin.modules.api` or only used in tests/non-production experiments.
- Reconcile current documentation drift around Tapir migration and `MasterServiceOfferVariantApi` legacy/typed behavior.
- Trace exact `BeautySearchService.Impl` construction and any Elasticsearch runtime client/resource wiring, not just pure interpreters.
- Build a dependency graph of repository constructors and DDL FK edges from code, including `@unused` dependencies that preserve startup order.
- Classify search tests by resource needs using source annotations and environment variables, then choose a safe focused verification suite for later passes.

# Pass 2 notes

## Pass 2 scope

- Transformed pass-1 factual inventory into human-oriented architecture documentation.
- Did not change production code, test code, build files, or source docs outside `docs/codebase-review/`.
- Did not rewrite `INVENTORY.md`; no factual correction was applied to it in this pass.

## Pass 2 commands run

- `sed -n '1,220p' docs/codebase-review/INVENTORY.md && sed -n '1,180p' docs/codebase-review/REVIEW_NOTES.md`
- `for f in beautyq_search_eval_plan_v1.md docs/beautyq-search-dsl-v1.md docs/search-dsl-domain-onboarding.md docs/search-dsl-qdrant-vector-backend.md docs/search-dsl-hybrid-v1-plan.md docs/http-legacy-json-contracts.md docs/http-master-service-offer-variant-typed-get-plan.md docs/LOCAL_LLM_DISTAGE_APP_MODEL.md docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md; do echo '###' $f; sed -n '1,140p' $f; done`
- `rg -n "BeautySearchService|BeautySearchBackend|UserSearchInput|BeautySearchResponse" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala`
- `rg -n "search\(|BeautySearch|SearchApi|SearchRoute|Tapir.*Search|/search" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala`
- `rg -n "make\[.*BeautySearch|BeautySearchService\.Impl|BeautySearchBackend|Elasticsearch|Qdrant|ExperimentalBeautySearchService|NonProductionHybrid|NonProductionExperiment" bifunctor-tagless/src/main/scala/leaderboard/plugins bifunctor-tagless/src/main/scala/leaderboard/LeaderboardRole.scala bifunctor-tagless/src/main/resources -S`
- `rg -n "Elasticsearch|Elastic|matched_queries|matchedQueries|matchedFields|_matched_queries" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala docs -S`
- `rg -n "ignore\(|taggedAs|Ignore|Manual|Docker|LLAMA|QDRANT|env|sys\.env|Assume|assume\(|cancel|pending|isDefined|nonEmpty" bifunctor-tagless/src/test/scala/leaderboard/search bifunctor-tagless/src/test/scala/leaderboard -S`
- `rg -n "Salon|salon|availability|schedule|scheduling|booking|bookable|calendar|slot|appointment" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala docs beautyq_search_eval_plan_v1.md -S`
- `sed -n '1,140p' bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala`
- `sed -n '1,120p' bifunctor-tagless/src/main/scala/leaderboard/search/hybrid/ExperimentalBeautySearchService.scala`
- `sed -n '1,90p' bifunctor-tagless/src/main/scala/leaderboard/search/hybrid/ExperimentalHybridSearchBackend.scala`
- `sed -n '1,90p' bifunctor-tagless/src/main/scala/leaderboard/search/qdrant/QdrantNonProductionHybridExperiment.scala`
- `sed -n '1,130p' bifunctor-tagless/src/main/scala/leaderboard/search/elasticsearch/ElasticsearchSearchResponseInterpreter.scala`
- `sed -n '1,80p' bifunctor-tagless/src/main/scala/leaderboard/search/lexical/LexicalDocumentBackend.scala`
- `sed -n '1,120p' bifunctor-tagless/src/test/scala/leaderboard/search/ElasticsearchSearchResponseInterpreterSpec.scala`
- `sed -n '1,120p' bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchElasticsearchIntegrationSpec.scala`
- `sed -n '420,515p' bifunctor-tagless/src/test/scala/leaderboard/search/BeautySearchElasticsearchIntegrationSpec.scala`
- `sed -n '1,110p' bifunctor-tagless/src/test/scala/leaderboard/search/QdrantDockerSmokeSpec.scala`
- `sed -n '1,430p' bifunctor-tagless/src/main/scala/leaderboard/search/document/VariantSearchDocument.scala`
- Final verification: `git diff --check`

## Files inspected in pass 2

- Pass-1 docs: `docs/codebase-review/INVENTORY.md`, `docs/codebase-review/REVIEW_NOTES.md`.
- Existing docs: `beautyq_search_eval_plan_v1.md`, `docs/beautyq-search-dsl-v1.md`, `docs/search-dsl-domain-onboarding.md`, `docs/search-dsl-qdrant-vector-backend.md`, `docs/search-dsl-hybrid-v1-plan.md`, `docs/http-legacy-json-contracts.md`, `docs/http-master-service-offer-variant-typed-get-plan.md`, `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md`, `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md`.
- Search source: `BeautySearchModels.scala`, `ExperimentalBeautySearchService.scala`, `ExperimentalHybridSearchBackend.scala`, `QdrantNonProductionHybridExperiment.scala`, `ElasticsearchSearchResponseInterpreter.scala`, `LexicalDocumentBackend.scala`, `VariantSearchDocument.scala`.
- Search tests: `BeautySearchPureSpec.scala` by targeted search, `ElasticsearchSearchResponseInterpreterSpec.scala`, `BeautySearchElasticsearchIntegrationSpec.scala`, `QdrantDockerSmokeSpec.scala`, and env-gated Qdrant/Llama/benchmark specs by targeted search.
- Wiring source: `LeaderboardPlugin.scala`, `LeaderboardRole.scala`, `ElasticsearchDockerPlugin.scala`, `QdrantDockerPlugin.scala` by targeted search.

## Docs created in pass 2

- `docs/codebase-review/README.md`
- `docs/codebase-review/01-system-map.md`
- `docs/codebase-review/02-domain-model-guide.md`
- `docs/codebase-review/03-repositories-and-persistence.md`
- `docs/codebase-review/04-api-and-http-contracts.md`
- `docs/codebase-review/05-search-and-retrieval-architecture.md`
- `docs/codebase-review/06-tests-and-contracts.md`
- `docs/codebase-review/07-current-gaps-and-roadmap.md`
- `docs/codebase-review/ARCHITECTURE_DECISIONS_OBSERVED.md`

## Tests/checks run in pass 2

- `git diff --check`: passed.

## Tests/checks not run in pass 2 and why

- No SBT compile/test command was run. This pass created Markdown architecture docs only.
- Full `sbt test` remains expensive and resource-heavy because the repo includes Docker-backed Postgres/Elasticsearch/Qdrant and env-gated Llama/Qdrant benchmark tests.
- Focused tests were not needed to verify Markdown-only changes.

## Five pass-1 open questions answered or narrowed

1. Beauty search HTTP route: narrowed to absent in inspected production wiring. No `SearchApi`, search Tapir endpoint, search role, or `/search` route was found.
2. `BeautySearchService.Impl` wiring: narrowed to implemented but not production-bound in `LeaderboardPlugin.scala`; tests construct it directly.
3. Elasticsearch runtime/indexing pieces: narrowed to pure interpreters plus Docker-backed integration tests. No production `BeautySearchBackend` or indexing lifecycle binding was found.
4. Salon representation: narrowed to absent as a first-class model. Current code uses `Master` and `MasterLocation`; treating either as “salon” is an interpretation requiring product confirmation.
5. Qdrant/Llama/benchmark gating: narrowed to Docker-backed `QdrantDockerSmokeSpec` plus env-gated Llama/Qdrant/benchmark specs using `LLAMA_CPP_EMBEDDING_URL`, `QDRANT_*` gates, and `cancel(...)`; no prominent `ignore(` or `taggedAs` markers found in targeted search.

## Open questions that remain after pass 2

- Is `LeaderboardRole` intentionally missing `ProfileRole[F]` as a constructor dependency while logging Profile APIs and while `ProfileRole` exists separately?
- Should `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` be changed to depend directly on `BeautyQSeedReady` per the seed-backed snapshot rule?
- Is the intended first production search backend Elasticsearch, in-memory, or something else? Current code has no production binding.
- Should old docs be updated or archived before the next implementation pass?
- What exact transaction boundary exists around `MasterServiceOfferVariants.Postgres` base-row upsert plus attribute replacement? Pass 2 did not line-audit transaction semantics deeply.

## Suspected stale docs

- `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md`: stale current migrated slice list; source has more Tapir endpoint slices.
- `docs/http-master-service-offer-variant-typed-get-plan.md`: appears historical/stale relative to `docs/http-legacy-json-contracts.md` and current source/tests.
- `README.md`: still describes the generic upstream distage-example rather than BeautyQ-specific architecture.

## Unsupported claims removed or qualified

- Did not claim Qdrant is production-ready.
- Did not claim hybrid search is production-ready.
- Did not claim `BeautySearchService` is replaced by `ExperimentalBeautySearchService`.
- Did not claim benchmarks automate production switching.
- Did not claim fake/generic retrieval seams are production adapters.
- Did not claim `Salon` exists as a first-class model.
- Did not claim scheduling/availability exists.

# Pass 3 review notes

## Scope

Pass 3 audited and hardened the generated documentation under `docs/codebase-review/`. No production code, tests, build files, runtime configuration, or source files were edited.

## Commands run

- `find docs/codebase-review -maxdepth 1 -type f -name '*.md' -print | sort`
- `rg -n "BeautySearchService|BeautySearchBackend|SearchApi|/search|Tapir.*Search|make\[.*BeautySearch|BeautySearchService\.Impl|Elasticsearch|Qdrant|Hybrid|NonProduction|Experimental" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala docs/codebase-review docs -S`
- `rg -n "ignore\(|taggedAs|pending|cancel|Assume|assume|sys\.env|env\.|QDRANT|LLAMA|Docker|Manual|manual|TestSetup" bifunctor-tagless/src/test/scala bifunctor-tagless/src/main/scala docs -S`
- `rg -n "Salon|salon|availability|schedule|scheduling|booking|bookable|calendar|slot|appointment|MasterServiceOfferVariant|BeautyQSeedReady|SeedScopedFromRepositories|ProfileRole|LeaderboardRole" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala docs/codebase-review docs beautyq_search_eval_plan_v1.md -S`
- Focused `sed` reads of repository, HTTP server, role, and search-related files.
- Markdown relative-link sanity check using `python3`.
- `git diff --check`

## Files inspected

- All Markdown files under `docs/codebase-review/`.
- Referenced search/API/local docs under `docs/` and `beautyq_search_eval_plan_v1.md`.
- Focused source/test paths under `bifunctor-tagless/src/main/scala/leaderboard/` and `bifunctor-tagless/src/test/scala/leaderboard/`, especially `search`, `repo`, `api`, `http`, `plugins`, and `roles`.

## Docs changed

- `docs/codebase-review/README.md`
- `docs/codebase-review/02-domain-model-guide.md`
- `docs/codebase-review/03-repositories-and-persistence.md`
- `docs/codebase-review/05-search-and-retrieval-architecture.md`
- `docs/codebase-review/INVENTORY.md`
- `docs/codebase-review/REVIEW_NOTES.md`
- `docs/codebase-review/PASS_3_AUDIT.md`

## Unsupported claims removed or qualified

- Replaced unsupported `BeautySearchService old Live object name` references with verified `BeautySearchService.Impl` references.
- Qualified `purchasable-or-bookable` wording for `MasterServiceOfferVariant`; current source supports purchasable/search-result usage but did not show implemented availability/scheduling/booking behavior.
- Qualified the `SeedScopedFromRepositories` missing `BeautyQSeedReady` edge as a rule mismatch rather than a proven runtime failure.
- Clarified `MasterServiceOfferVariants.Postgres` constructor dependencies so `serviceVariantSchemas` is not described as an unused FK readiness-only dependency.
- Converted README index items into real Markdown links and added the pass-3 audit report.

## Remaining open questions

- Superseded historical note: pass-3 wording said no production route/binding was found in inspected source. Current known route wiring is `POST /beauty-search` through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`; the exposed backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`; `seedCatalogInMemory` / `InMemorySearchBackend` are rollback/non-default; Qdrant/hybrid are not production-wired.
- Whether stale planning docs should be retired or rewritten remains a documentation-management question.
- Whether `SeedScopedFromRepositories` should be changed to depend directly on `BeautyQSeedReady` remains an implementation question.
- Whether availability/scheduling is intended but absent remains a product/domain question requiring human confirmation.
- Whether Qdrant/hybrid should ever become production-wired requires a separate implementation design and explicit activation policy.

## Checks run

- Markdown relative-link sanity check over `docs/codebase-review/*.md`.
- `git diff --check`.

No SBT tests were run because pass 3 was documentation-only and the requested validation was documentation/path/claim audit plus `git diff --check`, not runtime behavior verification.

# Final documentation cleanup notes

## Scope

Final cleanup was limited to obvious Markdown/documentation typo fixes under `docs/codebase-review/`.

## Corrections made

- Fixed malformed inline code in `docs/codebase-review/03-repositories-and-persistence.md`: an empty inline-code marker before FK readiness edges now reads `as `@unused` FK readiness edges`.
- Reworded `MasterServiceOfferVariant` in `docs/codebase-review/INVENTORY.md` from `purchasable/booking unit` to `purchasable/search-result unit` to avoid implying implemented booking/scheduling behavior.

## Checks run

- Targeted grep for stale search-service, bookable/booking/scheduling, and malformed inline-code wording.
- Markdown relative-link sanity check over `docs/codebase-review/*.md`.
- `git diff --check`.

No SBT tests were run because this was a documentation-only typo cleanup with no production or test code changes.
