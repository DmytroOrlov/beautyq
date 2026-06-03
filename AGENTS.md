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

This section is intentionally strict. Follow it literally.

### Main rule

* Search behavior must live in `BeautySearchSpecV1` / DSL data.
* Backend interpreters must stay mechanical.
* Do not hardcode BeautyQ service names, attribute codes, query phrases, or ranking rules inside Elasticsearch interpreters.

Allowed search-semantic place:

* `BeautySearchSpecV1.scala`

Usually forbidden places for domain semantics:

* `ElasticsearchSearchRequestInterpreter.scala`
* `ElasticsearchSearchResponseInterpreter.scala`
* `ElasticsearchMappingInterpreter.scala`
* `ElasticsearchIngestionInterpreter.scala`
* `InMemorySearchBackend.scala`
* parser/interpreter code, unless the task explicitly asks for parser/interpreter work

If Elasticsearch or in-memory search needs new semantics, first add metadata or dictionary/spec data to the DSL/spec.

### Pick exactly one task mode

Before editing, classify the task as exactly one mode.

#### Mode A: pure eval slice

Use this when adding new query coverage for `InMemorySearchBackend`.

Allowed files:

* `BeautySearchEvalInventory.scala`
* `BeautySearchPureSpec.scala`
* `BeautySearchSpecV1.scala` only for narrow dictionary/spec data

Forbidden:

* Do not edit Elasticsearch tests.
* Do not edit Elasticsearch interpreters.
* Do not update docs.
* Do not edit AGENTS.md.
* Do not refactor helpers.

Required steps:

1. Add one query-id set to `BeautySearchEvalInventory`.

2. Add that set to the covered inventory.

3. Update the duplicate/overlap check to include the new set.

4. Update the expected covered count.

5. Add one pure test in `BeautySearchPureSpec`.

6. Run:

   `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'`

7. Report per-query pass/fail and coverage counts from test output.

#### Mode B: Elasticsearch eval slice

Use this only after the same pure slice is green.

Allowed files:

* `BeautySearchElasticsearchIntegrationSpec.scala`
* `BeautySearchSpecV1.scala` only if Elasticsearch exposes a narrow residual-text dictionary gap

Forbidden:

* Do not edit `BeautySearchEvalInventory.scala`.
* Do not edit `BeautySearchPureSpec.scala`.
* Do not edit Elasticsearch interpreters.
* Do not change ranking.
* Do not update docs.
* Do not edit AGENTS.md.
* Do not refactor helpers.

Required steps:

1. Add one Elasticsearch test in `BeautySearchElasticsearchIntegrationSpec`.

2. Reuse the existing query-id set from `BeautySearchEvalInventory`.

3. Reuse `BeautySearchEvalTestSupport.requireEvalOutcome`.

4. Keep existing ES diagnostics unchanged.

5. Run:

   `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'`

6. Report per-query pass/fail.

#### Mode C: docs-only update

Allowed files:

* docs files only

Forbidden:

* Do not edit Scala files.
* Do not edit tests.
* Do not run sbt unless docs generation exists.

#### Mode D: AGENTS.md update

Allowed files:

* `AGENTS.md` only

Forbidden:

* Do not edit Scala files.
* Do not edit tests.
* Do not update docs.

#### Mode E: infrastructure cleanup

Use this when sbt fails because of local build output issues.

Allowed actions:

* remove stale `target` directories
* rerun the same sbt command

Forbidden:

* Do not edit source code.
* Do not edit tests.
* Do not change dictionary/spec data.

### Dictionary rules

Keep dictionary fixes narrow.

Preferred fixes:

* exact phrase synonyms
* contextual `requires`
* conflict-preventing `excludes`

Avoid broad unconditional tokens.

Do not add these as unconditional service triggers:

* `brows`
* `gel`
* `removal`
* `lifting`
* `correction`
* `lip`
* `face`
* `дизайн`
* `коррекция`
* `снятие`
* `гель`

Bad:

```scala
phrase(Set("gel"), ...)
```

Better:

```scala
phrase(Set("снять гель с ногтей"), ...)
```

Bad:

```scala
phrase(Set("brows"), List(ServiceAny(PMU), ...))
```

Better:

```scala
phrase(Set("powder brows"), List(ServiceAny(PMU), ...))
phrase(Set("brows"), List(...), requires = List(ServiceAny(Set(PMU))))
```

Do not implement generic negation or NLP logic for one failing query.

Do not fix a failing query by changing ranking unless the task explicitly asks for ranking work.

### Failure protocol

If one query fails, stop expanding the slice.

Report:

* query id
* query text
* parsed intent, if available
* remaining text, if available
* top variant ids
* top provider location ids
* top service ids
* scorer failed assertions
* raw hit count, for Elasticsearch tests
* Elasticsearch request JSON, for Elasticsearch tests

Then fix only that query with the smallest dictionary/spec-data change.

Do not keep adding more query ids while the current slice is red.

### Patch hygiene

Keep each patch to one purpose.

Do not mix:

* eval coverage
* docs updates
* AGENTS.md edits
* infrastructure cleanup
* unrelated refactors

Do not commit:

* opencode session logs
* local debug files
* build artifacts
* temporary println/debug output

When adding a new eval slice, always report coverage counts from test output, not from memory.

Do not update docs coverage numbers in the same patch as code/test coverage unless explicitly requested.

### Vector / Qdrant workflow

* Qdrant work must stay separate from Elasticsearch until Qdrant-only eval is measured.

  * First build Qdrant-only retrieval.
  * Then measure Qdrant-only eval.
  * Only after that consider ES/Qdrant fallback, hybrid ranking, or reranking.
  * Do not add hybrid/fallback/reranking in the same patch as Qdrant ingestion or Qdrant eval.

* Elasticsearch remains the lexical/filter/facet baseline.

  * Do not change Elasticsearch behavior while adding Qdrant.
  * Do not change Elasticsearch interpreters to make Qdrant tests pass.
  * Do not use Qdrant to hide Elasticsearch regressions.

* llama.cpp embedding server is manual-only.

  * Agents must not start, stop, install, or Dockerize llama.cpp.
  * Tests that need llama.cpp must be gated by `LLAMA_CPP_EMBEDDING_URL`.
  * Normal test suites must pass without llama.cpp running.
  * The user starts llama.cpp manually when needed:
    `~/git/llama.cpp/build/bin/llama-server -m ~/git/Qwen3-Embedding-0.6B-Q8_0.gguf --embedding --pooling last -ub 8192 --port 8081`

* Qdrant-only eval rules:

  * Do not change `BeautySearchSpecV1` dictionary to make Qdrant eval pass.
  * Do not add lexical synonyms for `q_broad_004` or `q_broad_006`.
  * Do not add production search wiring.
  * Do not add fallback from ES to Qdrant.
  * Do not add Qdrant results to user-facing search responses until separate Qdrant eval is measured.

* Qdrant implementation order:

  1. DSL/vector spec data
  2. embedding text extraction
  3. pure Qdrant JSON
  4. Qdrant Docker smoke
  5. llama.cpp embedding client
  6. synthetic Qdrant + llama.cpp retrieval smoke
  7. BeautyQ Qdrant-only semantic candidate eval
  8. only later: fallback/hybrid/rerank

### SBT rules

Run one sbt command at a time.

Avoid parallel sbt invocations because the repo can hit sbt server locks.

Standard commands:

Pure slice:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'
```

Elasticsearch slice:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'
```

Compile-only check:

```bash
sbt 'project bifunctor-tagless' test:compile
```

If sbt fails with `graal-resources/target` path recursion or `File name too long`:

1. Stop search work.
2. Clean stale target/build output directories.
3. Rerun the same sbt command.
4. Do not change source code while fixing this infrastructure issue.

* If sbt fails on `~/.sbt/boot/sbt.boot.lock` or another lock outside the sandbox writable roots:
  * stop source-code work
  * rerun the same sbt command with the required local permission/escalation
  * do not change source code to fix this infrastructure issue
  * report that the first failure was an environment/sandbox lock issue
