# AGENTS.md

## Repo workflow rules

* Check nearby project code before using framework APIs from memory.

  * For Distage axes/modules/test wiring, follow existing repo patterns instead of assumed library APIs.

* Keep paired Distage test suites aligned when moving or splitting specs.

  * Preserve `*Dummy`/`*Postgres` wrapper suites and trait mixins so both Repo modes keep running.

* Keep mechanical refactors behavior-preserving.

  * Extract shared fixtures first, move tests unchanged, and limit follow-up fixes to imports, wiring, visibility, and syntax.

## Domain invariants

* Preserve the `MasterServiceOfferVariant` JSON attribute contract when changing codecs.

  * Keep attribute-group decoding centralized and type-aware; enum attributes remain string codes in JSON (`enumAttributes: Map[String, String]`).

* Keep `MasterServiceOfferVariant` additional attributes on the unified numeric storage path.

  * Use `master_service_offer_variant_numeric_attributes` for int, bigdecimal, enum-as-int-code, and boolean values; validate enum attribute/value compatibility before encoding, and add new storage paths only as an intentional schema redesign.

## HTTP migration rules

* Preserve existing route-level HTTP contracts during Tapir migrations.

  * Existing route-level contract tests are the source of truth, not Tapir defaults; keep current malformed path/body decode and uncaught-exception responses unless deliberately changing the contract.

* Keep the current Tapir adapter split.

  * Put pure endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`, keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters, and reuse `TapirHttpSupport` for route interpretation.

* Preserve current missing-entity response contracts.

  * Do not replace existing `200 + null` responses with `404` or `jsonBody[Option[_]]` during the current migration phase; map missing values explicitly to `Json.Null` where required.

## Distage resource graph rules

* Distage resource startup order follows dependency edges, not textual binding order.

  * `make[A].fromResource[...]` order in `ModuleDef` and `memoizationRoots` order in tests are not sequencing guarantees.

* Preserve FK ordering edges in Postgres repos.

  * If child-table DDL references a parent table, `ChildRepo.Postgres` must depend on the immediate parent repo, usually via `@unused`; do not remove these params as dead code.

## BIO / observability refactor rules

* Treat ZIO-specific to BIO2 refactors as behaviorally risky.

  * Preserve or add contract tests for tracing context, logging context, tracing headers, async child lifetime, publish/replay ordering, and fallback behavior before refactoring.

* Do not genericize services from `IO` to polymorphic `F` without a concrete reason.

  * A wider effect surface is justified only by a real non-IO consumer, clear duplication reduction, or improved testability without weakening observability guarantees.

## BeautyQ search DSL rules

### Core invariant

* Keep BeautyQ search semantics in `BeautySearchSpecV1` / DSL data, not in backend interpreters.

  * Mapping, ingestion, searchable fields, filters, facets, boosts, request behavior, grouping, and synonym behavior must be driven by the spec.
  * If Elasticsearch or in-memory search needs new semantics, add metadata to the DSL/spec first.
  * Do not hardcode BeautyQ service names, attribute codes, query phrases, or ranking rules inside Elasticsearch interpreters.

### Eval coverage workflow

* Add eval coverage in small slices.

  * First add pure/in-memory coverage.
  * Only after the pure slice is green, add Elasticsearch coverage in a separate patch.
  * Do not add pure and Elasticsearch coverage in the same patch unless explicitly requested.
  * Do not add unrelated query ids while working on a slice.

* For a pure eval slice, allowed files are usually:

  * `BeautySearchEvalInventory.scala`
  * `BeautySearchPureSpec.scala`
  * `BeautySearchSpecV1.scala` for narrow dictionary/spec data only

* For an Elasticsearch eval slice, allowed files are usually:

  * `BeautySearchElasticsearchIntegrationSpec.scala`
  * `BeautySearchSpecV1.scala` only if Elasticsearch exposes a narrow residual-text dictionary gap

### Dictionary rules

* Keep dictionary fixes narrow and contextual.

  * Prefer exact phrase synonyms and `requires` constraints.
  * Do not add broad tokens such as `brows`, `gel`, `removal`, `lifting`, `correction`, or `lip` as unconditional service triggers.
  * Do not implement generic negation or NLP logic for one failing query.
  * Do not fix a failing query by changing ranking unless the task explicitly asks for ranking work.

### Interpreter rules

* Keep search interpreter changes rare and spec-driven.

  * Do not add query-specific branches to parser/interpreter code.
  * Do not change Elasticsearch interpreters while adding eval coverage unless a test proves a real spec-driven interpreter bug.
  * Do not make Elasticsearch and in-memory behavior diverge intentionally.

### Failure protocol

* If one query fails, stop and report:

  * query id
  * query text
  * parsed intent, if available
  * top variant ids
  * top provider location ids
  * top service ids
  * scorer failed assertions
  * Elasticsearch request JSON, for Elasticsearch tests

* Fix only the failing query with the smallest dictionary/spec-data change.

* Do not continue expanding coverage while a current slice is red.

### Patch hygiene

* Keep each patch to one purpose.

  * Do not mix eval coverage, docs updates, AGENTS.md edits, and unrelated cleanup in one patch.
  * Do not commit local opencode/session logs.
  * Do not leave temporary println/debug output in green patches.

* When adding a new eval slice:

  * update `BeautySearchEvalInventory`
  * update the inventory overlap/duplicate check to include the new set
  * update the expected covered count in the inventory test
  * report coverage counts from the test output, not from memory

* Do not update docs coverage numbers in the same patch as code/test coverage unless explicitly requested.

  * Prefer a separate docs-only patch after pure and Elasticsearch coverage are both green for a slice.

### Dictionary safety examples

* Broad words must usually be contextual:

  * `brows`
  * `gel`
  * `removal`
  * `lifting`
  * `correction`
  * `lip`
  * `face`

* Prefer exact phrase entries for known eval phrases.

* For ambiguous bare terms, prefer `requires` / `excludes` constraints instead of unconditional service triggers.

### SBT rules

* Run one sbt command at a time.

  * Avoid parallel sbt invocations because the repo can hit sbt server locks.

* Standard verification commands:

  * Pure slice: `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'`
  * Elasticsearch slice: `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'`
  * Compile-only check: `sbt 'project bifunctor-tagless' test:compile`
