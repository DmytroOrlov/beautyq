# Pass 3 Audit

## Scope

This pass audited the generated architecture documentation under `docs/codebase-review/` for factual support, path/symbol accuracy, search-production-status claims, domain model wording, repository/seed claims, HTTP/API claims, test-gate descriptions, and internal Markdown link consistency.

The audit did not edit production code, tests, build files, or runtime configuration. Corrections were limited to documentation under `docs/codebase-review/`.

## Commands run

- `find docs/codebase-review -maxdepth 1 -type f -name '*.md' -print | sort`
- `rg -n "BeautySearchService|BeautySearchBackend|SearchApi|/search|Tapir.*Search|make\[.*BeautySearch|BeautySearchService\.Impl|Elasticsearch|Qdrant|Hybrid|NonProduction|Experimental" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala docs/codebase-review docs -S`
- `rg -n "ignore\(|taggedAs|pending|cancel|Assume|assume|sys\.env|env\.|QDRANT|LLAMA|Docker|Manual|manual|TestSetup" bifunctor-tagless/src/test/scala bifunctor-tagless/src/main/scala docs -S`
- `rg -n "Salon|salon|availability|schedule|scheduling|booking|bookable|calendar|slot|appointment|MasterServiceOfferVariant|BeautyQSeedReady|SeedScopedFromRepositories|ProfileRole|LeaderboardRole" bifunctor-tagless/src/main/scala bifunctor-tagless/src/test/scala docs/codebase-review docs beautyq_search_eval_plan_v1.md -S`
- `sed -n '1,240p' bifunctor-tagless/src/main/scala/leaderboard/repo/MasterServiceOfferVariants.scala`
- `sed -n '360,460p' bifunctor-tagless/src/main/scala/leaderboard/repo/MasterServiceOfferVariants.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/repo/Services.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/repo/ServiceVariantSchemas.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/repo/MasterLocations.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/repo/MasterServiceOffers.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/http/HttpServer.scala`
- `sed -n '1,220p' bifunctor-tagless/src/main/scala/leaderboard/roles/LeaderboardRole.scala`
- `rg -n "BeautySearchService\.Live|bookable|purchasable-or-bookable|schema dependencies" docs/codebase-review -S`
- Markdown relative-link sanity check using a small `python3` script over `docs/codebase-review/*.md`.
- `git diff --check`

## Files inspected

Documentation inspected:

- `docs/codebase-review/README.md`
- `docs/codebase-review/01-system-map.md`
- `docs/codebase-review/02-domain-model-guide.md`
- `docs/codebase-review/03-repositories-and-persistence.md`
- `docs/codebase-review/04-api-and-http-contracts.md`
- `docs/codebase-review/05-search-and-retrieval-architecture.md`
- `docs/codebase-review/06-tests-and-contracts.md`
- `docs/codebase-review/07-current-gaps-and-roadmap.md`
- `docs/codebase-review/ARCHITECTURE_DECISIONS_OBSERVED.md`
- `docs/codebase-review/INVENTORY.md`
- `docs/codebase-review/REVIEW_NOTES.md`
- Existing referenced docs under `docs/`, plus `beautyq_search_eval_plan_v1.md`.

Source/test areas inspected:

- `bifunctor-tagless/src/main/scala/leaderboard/search/`
- `bifunctor-tagless/src/main/scala/leaderboard/plugins/`
- `bifunctor-tagless/src/main/scala/leaderboard/roles/`
- `bifunctor-tagless/src/main/scala/leaderboard/http/`
- `bifunctor-tagless/src/main/scala/leaderboard/api/`
- `bifunctor-tagless/src/main/scala/leaderboard/repo/`
- `bifunctor-tagless/src/test/scala/leaderboard/search/`
- `bifunctor-tagless/src/test/scala/leaderboard/api/`
- `bifunctor-tagless/src/test/scala/leaderboard/repo/`

## Corrections made

- `docs/codebase-review/REVIEW_NOTES.md` and `docs/codebase-review/INVENTORY.md`: replaced stale references to `BeautySearchService old Live object name` with `BeautySearchService.Impl`. Evidence: `BeautySearchService.Impl` is the object defined in `bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala`; targeted searches found no `BeautySearchService old Live object name` symbol.
- `docs/codebase-review/02-domain-model-guide.md`: changed `purchasable-or-bookable` wording to `purchasable` where the prior wording risked implying implemented scheduling/booking behavior. Evidence: targeted searches for availability, scheduling, booking, calendar, slot, and appointment did not find a current first-class availability/scheduling model in source.
- `docs/codebase-review/05-search-and-retrieval-architecture.md`: qualified the `BeautyQSeedReady` gap for `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` as a repository-instruction/rule mismatch rather than a test-proven runtime failure. Evidence: pass-3 inspection verified the missing direct edge claim, but did not find a current failing test proving a runtime defect.
- `docs/codebase-review/INVENTORY.md` and `docs/codebase-review/03-repositories-and-persistence.md`: clarified `MasterServiceOfferVariants.Postgres` constructor dependencies. Evidence: `bifunctor-tagless/src/main/scala/leaderboard/repo/MasterServiceOfferVariants.scala` uses `@unused masterServiceOffers` and `@unused masterLocations` as FK readiness edges, while `serviceVariantSchemas` is an active collaborator for validation rather than an unused readiness-only edge.
- `docs/codebase-review/README.md`: rewrote the index entries as real Markdown links, added the pass-3 audit link, and aligned the search summary with pass-3 evidence that production Beauty search bindings/routes were not found.

## Unsupported claims removed or qualified

- Removed/qualified the stale `BeautySearchService old Live object name` symbol reference. The supported symbol name is `BeautySearchService.Impl`.
- Weakened wording that could imply implemented booking/scheduling behavior for `MasterServiceOfferVariant`. Current docs now state that the variant is the primary purchasable/search-result unit where supported by source/docs, while scheduling/availability remains absent or unresolved in inspected source.
- Qualified the `SeedScopedFromRepositories` seed-readiness issue as a current dependency-edge rule mismatch, not as a proven runtime failure.
- Clarified repository constructor dependency wording to avoid treating all constructor dependencies as the same kind of FK readiness edge.

## Claims verified as supported

- Beauty search model code exists, including `UserSearchInput`, `ParsedSearchIntent`, `BeautySearchResponse`, `BeautySearchBackend`, and `BeautySearchService.Impl`, under `bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala`.
- Pass-3 targeted searches did not find a production HTTP search route, `SearchApi`, Tapir search endpoint, or production Distage binding for `BeautySearchService.Impl`/`BeautySearchBackend` in the inspected main-source wiring.
- Elasticsearch search/indexing code is supported by interpreters, clients, and integration tests, but pass-3 inspection did not find production runtime wiring for an Elasticsearch-backed Beauty search route.
- Qdrant and hybrid components are represented by implemented code, docs, env-gated/manual tests, and non-production experiment naming; pass-3 inspection did not find production-wired Qdrant or hybrid search.
- The current domain model uses `Master` and `MasterLocation`; pass-3 source searches did not find a first-class `Salon` domain/repository/API model.
- `HttpServer.Impl` depends on `BeautyQSeedReady` before exposing aggregated `HttpApi` instances, as shown in `bifunctor-tagless/src/main/scala/leaderboard/http/HttpServer.scala`.
- `LeaderboardRole` composes category, service, master, master location, master service offer, and master service offer variant APIs, as shown in `bifunctor-tagless/src/main/scala/leaderboard/roles/LeaderboardRole.scala`.
- Qdrant/embedding benchmark and semantic eval specs are gated by environment variables such as `LLAMA_CPP_EMBEDDING_URL`, `QDRANT_SEMANTIC_QUALITY_ASSERTIONS`, `QDRANT_EMBEDDING_BENCHMARK_*`, and compatibility-specific `QDRANT_*` flags in inspected test files.
- Benchmark verdicts remain documented as decision support, not production automation.

## Remaining uncertainties

- Production search exposure remains best stated as “not found in inspected source” rather than “impossible,” because this audit did not prove absence outside the inspected source/docs/test paths.
- Documentation around old Tapir migration plans remains stale relative to current source and `docs/http-legacy-json-contracts.md`; the docs now flag this drift, but a dedicated cleanup pass could retire or rewrite stale plans.
- The exact complete set of expensive Docker-backed tests was not exhaustively executed; test-gate classification is based on source inspection.
- The absence of first-class availability/scheduling is based on targeted searches and source inspection, not an exhaustive semantic model proof.
- `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` seed readiness mismatch was documented in pass 3 as a rule mismatch, not a proven runtime failure. The code now resolves that mismatch by adding a direct `BeautyQSeedReady` constructor edge for the seed-scoped repository snapshot loader.

## Checks run

- Markdown relative-link sanity check over `docs/codebase-review/*.md`: passed after creating this audit file.
- `git diff --check`: passed.

No SBT suites were run. This was a documentation audit, and full SBT suites would be expensive and outside the requested validation scope for pass 3.

## Recommended pass 4, if needed

A pass 4 is only needed if the team wants to convert these reviewed docs into stable project documentation outside `docs/codebase-review/`, retire stale planning docs, or make implementation changes for the unresolved seed/search/API gaps. No additional documentation-only pass is required solely to make the pass-3 audit trustworthy.
