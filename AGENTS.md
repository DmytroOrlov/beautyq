# AGENTS.md

## Core rules

* Inspect nearby repo code before using framework APIs from memory.
* One patch = one purpose.
* Do not mix code, tests, docs, build changes, and AGENTS.md edits unless explicitly asked.
* Existing route-level contract tests are the source of truth.
* Do not make broad refactors while fixing one failing test.
* Do not commit local debug output, session logs, build artifacts, or temporary `println`.

## Build and warning rules

* Do not add `@nowarn` as a first fix.
* Never add `@nowarn("msg=Unreachable")` to make compilation pass.
* If the compiler reports an unreachable match case, fix the match:

    * remove unreachable fallback branches;
    * simplify the ADT match;
    * do not hide it with `@nowarn`.
* `@nowarn` is allowed only when:

    * the exact warning is known;
    * the warning is intentionally false-positive or DI-related;
    * the annotation is narrow;
    * a short comment explains why the code should stay as-is.
* Prefer removing unused imports, unused params, or dead code over suppressing warnings.
* Do not change build versions in the same patch as business logic.

## sbt rules

* Do not run sbt commands in parallel.
* Prefer one chained sbt command instead of several concurrent shells.
* Do not use `-no-server` unless the user explicitly asks.
* If sbt fails on `~/.sbt/boot/sbt.boot.lock`, rerun the same command with local permission/escalation. Do not edit source.
* If sbt fails with stale target/class loading or resource recursion, stop source work, clean targets, rerun the same command, and report it.
* For multiple checks, chain commands in one sbt session.

Useful commands:

```bash
sbt 'project bifunctor-tagless' Compile/compile
sbt 'project bifunctor-tagless' Test/compile
sbt 'project bifunctor-tagless' 'testOnly leaderboard.SomeSuite' 'testOnly leaderboard.OtherSuite' Test/compile
```

Search commands:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantDockerSmokeSpec'
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Manual Qdrant semantic eval, only after the user starts llama.cpp:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Manual Qdrant semantic quality gate:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

## Distage rules

* Distage startup order follows dependency edges, not textual binding order.
* `ModuleDef` order and `memoizationRoots` order are not sequencing guarantees.
* Distage has graph GC. Do not claim it starts all bindings; inspect actual roots, axes, and suite inheritance.
* Do not remove `@unused` parent repo dependencies from Postgres repos if they preserve FK table creation order.
* Keep paired dummy/postgres suites aligned when moving tests.
* If test runtime is slow, inspect Distage roots/axes/resources before blaming the whole graph.

## MasterServiceOfferVariant invariants

* Do not touch `MasterServiceOfferVariant` codecs, storage, or validation unless the task explicitly asks.
* Preserve variant JSON attribute contract:

    * `enumAttributes` are stable string codes in JSON;
    * int, bigdecimal, boolean, and enum-as-int storage stay on the unified numeric storage path;
    * do not redesign storage in HTTP/search patches.
* `MasterServiceOfferVariant` endpoint migration requires focused contract tests first.
* Never migrate `MasterServiceOfferVariant` as part of another endpoint patch.

## HTTP / Tapir rules

* Put pure Tapir endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters.
* Reuse existing helpers:

    * `TapirHttpSupport`
    * `LegacyJsonResponse.optionalAsJson`
    * `HttpApiFailureTapirSupport.singleEntityGetErrorOutput`
* Do not migrate many endpoints in one patch.
* Do not change malformed path/body/exception contracts unless explicitly asked.

### HTTP missing-entity modes

Legacy mode:

* keep `200 + null`;
* keep `jsonBody[Json]`;
* use `LegacyJsonResponse.optionalAsJson`;
* do not introduce 404.

Typed migration mode:

* only when explicitly requested;
* migrate one single-entity GET endpoint only;
* found entity: `200 + typed domain JSON`;
* missing entity: `404 + typed HttpApiFailure JSON`;
* use `singleEntityGetErrorOutput`;
* update focused HTTP contract tests and docs.

Current Beauty typed GET migrated endpoints:

* `ServiceApi`
* `CategoryApi`
* `MasterApi`
* `MasterLocationApi`
* `MasterServiceOfferApi`

Current remaining Beauty legacy/high-risk endpoint:

* `MasterServiceOfferVariantApi`

Out of Beauty typed GET migration scope:

* `ProfileApi`

`ProfileApi` is a legacy ranked/read-model endpoint, not a Beauty domain single-entity GET.

## BeautyQ search rules

Search semantics must live in DSL/spec data, not backend interpreters.

Allowed place for BeautyQ search semantics:

* `BeautySearchSpecV1.scala`

Do not hardcode BeautyQ service names, attribute codes, query phrases, eval query ids, or ranking rules inside:

* Elasticsearch interpreters;
* Qdrant client/interpreters;
* InMemory backend;
* generic parser/interpreter code.

If a backend needs new semantics, add metadata to DSL/spec first.

## Search task modes

Pick one mode before editing. Do not mix modes.

### Pure eval slice

Allowed files:

* `BeautySearchEvalInventory.scala`
* `BeautySearchPureSpec.scala`
* `BeautySearchSpecV1.scala` only for narrow dictionary/spec data

Forbidden:

* no Elasticsearch tests;
* no Qdrant tests;
* no backend interpreters;
* no docs;
* no helper refactors.

Run:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'
```

### Elasticsearch eval slice

Use only after the same pure slice is green.

Allowed files:

* `BeautySearchElasticsearchIntegrationSpec.scala`
* `BeautySearchSpecV1.scala` only for narrow ES residual-text fixes

Forbidden:

* no eval inventory changes;
* no pure spec changes;
* no Elasticsearch interpreter changes;
* no ranking changes;
* no docs.

Run:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchElasticsearchIntegrationSpec'
```

### Qdrant-only work

Forbidden unless explicitly requested:

* no Elasticsearch changes;
* no `BeautySearchSpecV1` dictionary changes;
* no production search wiring;
* no hybrid/fallback;
* no ranking changes;
* no user-facing response changes;
* no llama.cpp Dockerization;
* no starting/stopping llama.cpp from code.

llama.cpp is manual-only. The user starts it:

```bash
~/git/llama.cpp/build/bin/llama-server -m ~/git/Qwen3-Embedding-0.6B-Q8_0.gguf --embedding --pooling last -ub 8192 --port 8081
```

Tests that need llama.cpp must be gated by:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081
```

Qdrant order:

1. vector DSL data;
2. embedding text extraction;
3. pure Qdrant JSON;
4. Qdrant Docker smoke;
5. llama.cpp embedding client;
6. synthetic Qdrant + llama.cpp smoke;
7. BeautyQ Qdrant semantic eval;
8. only later: fallback/hybrid/rerank.

### Hybrid/fallback work

Hybrid starts as docs/design or pure routing only.

Forbidden unless explicitly requested:

* no production routing changes;
* no `BeautySearchService` wiring;
* no ES/Qdrant hybrid calls;
* no score fusion;
* no reranking;
* no fallback behavior;
* no Elasticsearch/Qdrant client changes.

Current measured split:

* ES lexical baseline: 61/63 eval queries;
* Qdrant semantic candidates: `q_broad_004`, `q_broad_006`;
* no production hybrid/fallback exists yet.

Rules:

* ES owns filters, facets, exact attributes, price/duration, lexical ranking, and normal response assembly.
* Qdrant owns semantic candidate recall only.
* Residual text alone must never route to Qdrant.
* Hard-negative queries must not route to Qdrant just because they have residual text.
* Eval query ids may appear in tests/docs, not production routing code.
* First implementation patch must be pure routing model + pure tests only.

## Dictionary rules

Keep dictionary fixes narrow.

Prefer:

* exact phrases;
* contextual `requires`;
* conflict-preventing `excludes`;
* safe no-op residual cleanup.

Do not add broad unconditional triggers such as:

* `gel`
* `face`
* `beauty`
* `рядом`
* `недорого`
* `коррекция`
* `снятие`

Do not implement generic NLP/negation for one failing query.
Do not fix a query by changing ranking unless explicitly requested.

## Failure protocol

If one query/test fails, stop expanding the slice.

Report:

* test/suite name;
* query id and query text, if relevant;
* parsed intent and remaining text, if available;
* top ids and failed assertions, if relevant;
* raw hit count and request JSON for ES/Qdrant;
* exact HTTP status/body for HTTP failures.

Then fix only that failure with the smallest safe change.
