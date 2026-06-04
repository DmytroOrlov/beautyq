# AGENTS.md

## Global repo rules

* Inspect nearby code before using framework APIs from memory.
* Keep mechanical refactors behavior-preserving.
* Do not mix unrelated changes in one patch.
* Existing route-level contract tests are the source of truth.

## Distage / resource graph rules

* Distage startup order follows dependency edges, not textual binding order.
* Do not remove `@unused` parent repo dependencies from Postgres repos if they preserve FK table creation order.
* Keep paired dummy/postgres suites aligned when moving tests.

## Domain invariants

* Preserve `MasterServiceOfferVariant` JSON attribute contract.
* Enum attributes stay as stable string codes in JSON.
* Keep variant additional attributes on the unified numeric storage path unless the task explicitly asks for schema redesign.
* Do not touch `MasterServiceOfferVariant` codecs/storage in unrelated HTTP/search patches.

## Tapir / HTTP rules

* Keep pure endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters.
* Reuse `TapirHttpSupport` and existing helper style.
* Do not migrate many endpoints in one patch.

### HTTP missing-entity migration

There are two valid modes.

Legacy mode:

* keep existing `200 + null`;
* keep `jsonBody[Json]`;
* use `LegacyJsonResponse.optionalAsJson`;
* do not introduce 404.

Typed migration mode:

* only when the task explicitly asks for one endpoint migration;
* migrate one single-entity GET endpoint only;
* success is `200 + typed domain JSON`;
* missing entity is `404 + typed HttpApiFailure JSON`;
* use `HttpApiFailureTapirSupport.singleEntityGetErrorOutput`;
* update focused HTTP contract tests and docs.

Current migrated endpoints:

* `ServiceApi`
* `CategoryApi`
* `MasterApi`

Current legacy endpoints:

* `ProfileApi`
* `MasterLocationApi`
* `MasterServiceOfferApi`
* `MasterServiceOfferVariantApi`

Migration order:

1. Service
2. Category
3. Master
4. MasterLocation
5. MasterServiceOffer
6. Profile if still needed
7. MasterServiceOfferVariant last

Never migrate `MasterServiceOfferVariant` as part of another endpoint patch.

## BeautyQ search invariant

Search semantics must live in DSL/spec data, not backend interpreters.

Allowed place for BeautyQ search semantics:

* `BeautySearchSpecV1.scala`

Do not hardcode BeautyQ service names, attribute codes, query phrases, eval query ids, or ranking rules inside:

* Elasticsearch interpreters
* Qdrant client/interpreters
* InMemory backend
* generic parser/interpreter code

If a backend needs new semantics, add metadata to DSL/spec first.

## Search modes

Pick one mode before editing. Do not mix modes.

### Mode A: pure eval slice

Allowed files:

* `BeautySearchEvalInventory.scala`
* `BeautySearchPureSpec.scala`
* `BeautySearchSpecV1.scala` only for narrow dictionary/spec data

Forbidden:

* no Elasticsearch tests
* no Qdrant tests
* no backend interpreters
* no docs
* no helper refactors

Run:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'
```

### Mode B: Elasticsearch eval slice

Use only after the same pure slice is green.

Allowed files:

* `BeautySearchElasticsearchIntegrationSpec.scala`
* `BeautySearchSpecV1.scala` only for narrow ES residual-text fixes

Forbidden:

* no eval inventory changes
* no pure spec changes
* no Elasticsearch interpreter changes
* no ranking changes
* no docs

Run:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'
```

### Mode C: Qdrant-only work

Always forbidden unless explicitly requested:

* no Elasticsearch changes
* no `BeautySearchSpecV1` dictionary changes
* no production search wiring
* no hybrid/fallback
* no ranking changes
* no user-facing response changes
* no llama.cpp Dockerization
* no starting/stopping llama.cpp from code

llama.cpp is manual-only. The user starts it:

```bash
~/git/llama.cpp/build/bin/llama-server -m ~/git/Qwen3-Embedding-0.6B-Q8_0.gguf --embedding --pooling last -ub 8192 --port 8081
```

Tests that need llama.cpp must be gated by:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081
```

Qdrant order:

1. vector DSL data
2. embedding text extraction
3. pure Qdrant JSON
4. Qdrant Docker smoke
5. llama.cpp embedding client
6. synthetic Qdrant + llama.cpp smoke
7. BeautyQ Qdrant semantic eval
8. only later: fallback/hybrid/rerank

### Mode D: hybrid/fallback work

Hybrid starts as docs/design or pure routing only.

Forbidden unless explicitly requested:

* no production routing changes
* no `BeautySearchService` wiring
* no ES/Qdrant hybrid calls
* no score fusion
* no reranking
* no fallback behavior
* no Elasticsearch/Qdrant client changes

Current measured split:

* ES lexical baseline: 61/63 eval queries
* Qdrant semantic candidates: `q_broad_004`, `q_broad_006`
* no production hybrid/fallback exists yet

Rules:

* ES owns filters, facets, exact attributes, price/duration, lexical ranking, and normal response assembly.
* Qdrant owns semantic candidate recall only.
* Qdrant must not own canonical facets or exact filters.
* Residual text alone must never route to Qdrant.
* Hard-negative queries must not route to Qdrant just because they have residual text.
* Eval query ids may appear in tests/docs, not production routing code.

First implementation patch must be pure routing model + pure tests only.

### Mode E: docs-only

Allowed:

* docs files only

Forbidden:

* no Scala
* no tests
* no AGENTS.md
* no sbt unless docs tooling requires it

### Mode F: AGENTS.md-only

Allowed:

* `AGENTS.md` only

Forbidden:

* no Scala
* no tests
* no docs

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

If sbt fails with `graal-resources/target` recursion or `File name too long`:

1. stop source work;
2. clean stale target/build directories;
3. rerun the same sbt command;
4. do not change source code.

If sbt fails on `~/.sbt/boot/sbt.boot.lock`:

1. stop source work;
2. rerun the same sbt command with local permission/escalation;
3. do not change source code;
4. report it as environment/sandbox lock issue.

## Dictionary rules

Keep dictionary fixes narrow.

Prefer:

* exact phrases
* contextual `requires`
* conflict-preventing `excludes`
* safe no-op residual cleanup

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

Do not implement generic NLP/negation for one failing query.

Do not fix a query by changing ranking unless explicitly requested.

## Failure protocol

If one query fails, stop expanding the slice.

Report:

* query id
* query text
* parsed intent if available
* remaining text if available
* top variant ids
* top provider location ids
* top service ids
* scorer failed assertions
* raw hit count for ES/Qdrant
* request JSON for ES/Qdrant

Then fix only that query with the smallest safe change.

## Patch hygiene

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

## sbt rules

Do not run sbt commands in parallel.

Prefer one chained command instead of several concurrent shells, for example:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.MasterApiHttpContractSuite' 'testOnly leaderboard.LegacySingleEntityGetHttpContractSuite' test:compile
```

Do not use `-no-server` unless the user explicitly asks. It previously caused false classpath errors in this repo.

Standard commands:

```bash
sbt 'project bifunctor-tagless' test:compile
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantDockerSmokeSpec'
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Manual Qdrant semantic eval:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Manual Qdrant quality gate:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```
