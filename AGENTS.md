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

Follow this section literally. Pick one mode before editing. Do not mix modes.

### Main invariant

Search semantics must live in DSL/spec data, not in backend interpreters.

Allowed place for BeautyQ search semantics:

* `BeautySearchSpecV1.scala`

Usually forbidden for BeautyQ domain semantics:

* `ElasticsearchSearchRequestInterpreter.scala`
* `ElasticsearchSearchResponseInterpreter.scala`
* `ElasticsearchMappingInterpreter.scala`
* `ElasticsearchIngestionInterpreter.scala`
* `InMemorySearchBackend.scala`
* `QdrantClient.scala`
* `QdrantJsonInterpreter.scala`
* parser/interpreter code, unless the task explicitly asks for it

Do not hardcode BeautyQ service names, attribute codes, query phrases, eval query ids, or ranking rules inside backend interpreters.

If a backend needs new semantics, add missing metadata to the DSL/spec first.

---

### Mode A: pure eval slice

Use for new `InMemorySearchBackend` eval coverage.

Allowed files:

* `BeautySearchEvalInventory.scala`
* `BeautySearchPureSpec.scala`
* `BeautySearchSpecV1.scala` only for narrow dictionary/spec data

Forbidden:

* no Elasticsearch tests
* no Qdrant tests
* no backend interpreters
* no docs
* no AGENTS.md
* no helper refactors

Required steps:

1. Add one query-id set to `BeautySearchEvalInventory`.

2. Add it to covered inventory.

3. Add it to duplicate/overlap checks.

4. Update expected covered count.

5. Add one pure test in `BeautySearchPureSpec`.

6. Run:

   `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'`

7. Report per-query pass/fail and coverage counts from test output.

---

### Mode B: Elasticsearch eval slice

Use only after the same pure slice is green.

Allowed files:

* `BeautySearchElasticsearchIntegrationSpec.scala`
* `BeautySearchSpecV1.scala` only for narrow residual-text dictionary/spec fixes

Forbidden:

* no `BeautySearchEvalInventory.scala`
* no `BeautySearchPureSpec.scala`
* no Elasticsearch interpreters
* no ranking changes
* no docs
* no AGENTS.md
* no helper refactors

Required steps:

1. Add one ES test in `BeautySearchElasticsearchIntegrationSpec`.

2. Reuse the existing query-id set from `BeautySearchEvalInventory`.

3. Reuse `BeautySearchEvalTestSupport.requireEvalOutcome`.

4. Keep existing diagnostics.

5. Run:

   `sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'`

6. Report per-query pass/fail.

---

### Mode C: Qdrant-only work

Use for Qdrant/vector steps.

Allowed depends on the task, but keep it isolated.

Always forbidden unless explicitly requested:

* no Elasticsearch changes
* no `BeautySearchSpecV1` dictionary changes
* no production search wiring
* no hybrid/fallback
* no ranking changes
* no user-facing response changes
* no llama.cpp Dockerization
* no starting/stopping llama.cpp from code

llama.cpp is manual-only. The user starts it when needed:

`~/git/llama.cpp/build/bin/llama-server -m ~/git/Qwen3-Embedding-0.6B-Q8_0.gguf --embedding --pooling last -ub 8192 --port 8081`

Tests that need llama.cpp must be env-gated with:

`LLAMA_CPP_EMBEDDING_URL=http://localhost:8081`

Normal tests must pass without llama.cpp running.

Qdrant order:

1. vector DSL data
2. embedding text extraction
3. pure Qdrant JSON
4. Qdrant Docker smoke
5. llama.cpp embedding client
6. synthetic Qdrant + llama.cpp retrieval smoke
7. BeautyQ Qdrant-only semantic candidate eval
8. only later: fallback/hybrid/rerank

Qdrant semantic eval rules:

* do not change `BeautySearchSpecV1` to make Qdrant pass
* do not add lexical synonyms for `q_broad_004` or `q_broad_006`
* do not add fallback from ES to Qdrant
* do not add Qdrant to production responses
* Qdrant-only quality gates are env-gated/manual until explicitly promoted

---

### Mode D: hybrid/fallback work

Hybrid/fallback starts as docs/design or pure routing only.

Forbidden unless explicitly requested:

* no production routing changes
* no `BeautySearchService` wiring
* no ES/Qdrant hybrid calls
* no score fusion
* no reranking
* no fallback behavior
* no Elasticsearch interpreter changes
* no Qdrant interpreter/client changes

Current measured split:

* ES lexical baseline: 61/63 eval queries
* Qdrant-only semantic candidates: `q_broad_004`, `q_broad_006`
* no production hybrid/fallback exists yet

Rules:

* ES owns filters, facets, exact attributes, price/duration, lexical ranking, and normal response assembly.
* Qdrant owns semantic candidate recall only.
* Qdrant must not own canonical facets or exact filters.
* Hard-negative queries must not route to Qdrant just because they have residual text.
* Residual text alone must never route to Qdrant.
* Eval query ids may appear in tests/docs, not in production routing code.

First implementation patch must be pure routing model + pure tests only:

* no backend calls
* no `BeautySearchService` changes
* no `QdrantClient` calls
* no production routing changes

---

### Mode E: docs-only

Allowed files:

* docs files only

Forbidden:

* no Scala
* no tests
* no AGENTS.md
* no sbt unless docs tooling requires it

---

### Mode F: AGENTS.md-only

Allowed files:

* `AGENTS.md` only

Forbidden:

* no Scala
* no tests
* no docs

---

### Mode G: infrastructure cleanup

Use only for local build-output problems.

Allowed:

* remove stale `target` directories
* rerun the same sbt command
* rerun with local permission/escalation for sbt boot locks

Forbidden:

* no source changes
* no test changes
* no dictionary/spec changes

Known issues:

If sbt fails with `graal-resources/target` recursion or `File name too long`:

1. stop search work
2. clean stale target/build directories
3. rerun the same sbt command
4. do not change source code

If sbt fails on `~/.sbt/boot/sbt.boot.lock`:

1. stop source-code work
2. rerun the same sbt command with required local permission/escalation
3. do not change source code
4. report it as environment/sandbox lock issue

---

### Dictionary rules

Keep dictionary fixes narrow.

Prefer:

* exact phrases
* contextual `requires`
* conflict-preventing `excludes`
* no-op residual cleanup only when safe

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
* `beauty`
* `рядом`
* `недорого`

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
phrase(Set("beauty"), ...)
```

Better:

```scala
phrase(Set("beauty at home"), ...)
```

Do not implement generic NLP/negation for one failing query.

Do not fix a query by changing ranking unless explicitly requested.

---

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
* raw hit count, for ES/Qdrant tests
* request JSON, for ES/Qdrant tests

Then fix only that query with the smallest safe change.

Do not keep adding more query ids while current slice is red.

---

### Patch hygiene

One patch = one purpose.

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

Use counts from test output, not memory.

Do not update docs coverage numbers in the same patch as code/test coverage unless explicitly requested.

---

### Standard sbt commands

Pure search tests:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'
```

Elasticsearch tests:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'
```

Qdrant Docker smoke:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantDockerSmokeSpec'
```

Qdrant semantic eval without llama.cpp should cancel cleanly:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Manual Qdrant semantic eval, only after user starts llama.cpp:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Manual Qdrant semantic quality gate:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Compile-only:

```bash
sbt 'project bifunctor-tagless' test:compile
```
