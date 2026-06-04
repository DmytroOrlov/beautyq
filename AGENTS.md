# AGENTS.md

## Core rules

* Inspect nearby repo code before using framework APIs from memory.
* One patch = one purpose.
* Do not mix code, tests, docs, build changes, and AGENTS.md edits unless explicitly asked.
* Do not make broad refactors while fixing one failing test.
* Existing focused tests and route-level HTTP contract tests are the source of truth.
* Do not commit debug output, session logs, build artifacts, or temporary `println`.
* If a requested change needs a wider scope than the prompt allows, stop and report.

## Verification rules

Use explicit status labels:

* `FOCUSED GREEN`: requested focused suite passed; full repo status is unknown.
* `FULL GREEN`: full `sbt test` passed.
* `FULL RED`: full `sbt test` failed.

Do not call work commit-ready unless `FULL GREEN` is reached, or unless the user explicitly asked for focused-only verification.

For patches touching `src/main`, run focused checks and then full test before reporting commit-ready.

Preferred command shape:

```bash
sbt 'project bifunctor-tagless' Test/compile 'testOnly leaderboard.search.BeautySearchPureSpec' test
```

If full test fails:

* stop;
* do not continue to the next task;
* do not claim the repo is stable;
* report failing suite/test, exact error, and whether it appears related to changed files;
* do not add suppressions or speculative fixes.

## sbt rules

* Do not run sbt commands in parallel.
* Prefer one chained, project-scoped sbt command over several shells.
* Do not use `-no-server` unless the user explicitly asks.
* If sbt hits `~/.sbt/boot/sbt.boot.lock`, rerun the same command with local permission/escalation. Do not edit source.

Use `sbt --shutdown` only for stale/inconsistent compile state, for example:

* `error while loading SomeClass.class`;
* bad/stale classfile or classpath errors;
* the same clean compile command fails differently on repeat;
* sbt/IDE/agent compilation was interrupted or may have overlapped.

Compile-state reset:

```bash
sbt --shutdown
find . -type d -name target -print0 | xargs -0 rm -rf
sbt 'project bifunctor-tagless' Test/compile
```

Do not use `sbt --shutdown` to explain runtime test failures. It does not reset Docker, Postgres, Elasticsearch, Distage resources, or seed state.

Cold runtime test reset:

```bash
docker rm -f $(docker ps -a -q -f "label=distage.type") || true
find . -type d -name target -print0 | xargs -0 rm -rf
sbt test
```

If compile is green but full tests are red, diagnose runtime resources/tests. Do not keep cleaning targets or changing source blindly.

## Warning rules

* Do not add `@nowarn` as a first fix.
* Never add `@nowarn("msg=Unreachable")`.
* For unreachable match cases, fix the match:

  * remove unreachable branches;
  * simplify the ADT match;
  * do not hide it with `@nowarn`.
* `@nowarn` is allowed only when the warning is exact, narrow, intentional, and explained by a short comment.
* Prefer removing unused imports, unused params, and dead code over suppressing warnings.

## Distage rules

* Distage startup order follows dependency edges, not textual binding order.
* `ModuleDef` order and `memoizationRoots` order are not sequencing guarantees.
* Distage has graph GC. Do not claim it starts all bindings; inspect roots, axes, and suite inheritance.
* Do not remove `@unused` parent repo dependencies from Postgres repos when they preserve FK table creation order.
* If tests are slow, inspect Distage roots/axes/resources before blaming the whole graph.

## HTTP / Tapir rules

* Put pure Tapir endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters.
* Reuse existing helpers:

  * `TapirHttpSupport`
  * `LegacyJsonResponse.optionalAsJson`
  * `HttpApiFailureTapirSupport.singleEntityGetErrorOutput`
* Do not migrate many endpoints in one patch.
* Do not change malformed path/body/exception contracts unless explicitly asked.

Beauty single-entity GETs already migrated to typed `200 domain JSON / 404 HttpApiFailure JSON`:

* `ServiceApi`
* `CategoryApi`
* `MasterApi`
* `MasterLocationApi`
* `MasterServiceOfferApi`
* `MasterServiceOfferVariantApi`

`ProfileApi` is out of Beauty typed GET scope. It is a legacy ranked/read-model endpoint, not a Beauty domain single-entity GET.

## MasterServiceOfferVariant invariants

Do not touch `MasterServiceOfferVariant` codecs, storage, validation, or JSON shape unless explicitly asked.

Preserve:

* `intAttributes`
* `bigDecimalAttributes`
* `enumAttributes`
* `booleanAttributes`
* enum attributes encoded as stable `stringCode` values
* unified numeric storage path for int/bigdecimal/boolean/enum-as-int storage

Never change these in search, build cleanup, or unrelated HTTP patches.

## BeautyQ search architecture

Search semantics must live in DSL/spec data, not backend interpreters.

Allowed place for BeautyQ search semantics:

* `BeautySearchSpecV1.scala`

Do not hardcode BeautyQ service names, attribute codes, query phrases, eval query ids, or ranking rules inside:

* Elasticsearch interpreters;
* Qdrant client/interpreters;
* InMemory backend;
* generic parser/interpreter code.

If a backend needs new semantics, add metadata to DSL/spec first.

## Search ownership

Elasticsearch owns:

* lexical search;
* filters;
* facets;
* exact attributes;
* price/duration;
* lexical ranking;
* normal response assembly.

Qdrant owns:

* semantic candidate recall only.

Rules:

* ES lexical baseline is intentionally `61/63`.
* Qdrant semantic candidates are `q_broad_004` and `q_broad_006`.
* Do not close those semantic gaps with broad lexical dictionary hacks.
* Residual text alone must never route to Qdrant.
* Hard-negative/noise queries must not route to Qdrant just because they have residual text.
* Eval query ids may appear in tests/docs, not production routing code.

## Search task modes

Pick one mode before editing. Do not mix modes.

### Pure eval / parser / DSL work

Allowed:

* `BeautySearchPureSpec.scala`
* eval inventory files
* `BeautySearchSpecV1.scala` only for narrow dictionary/spec data

Forbidden:

* no Elasticsearch runtime changes;
* no Qdrant runtime changes;
* no production wiring;
* no docs unless asked.

Run focused test:

```bash
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.BeautySearchPureSpec'
```

### Elasticsearch eval work

Use only after the same pure slice is green.

Allowed:

* Elasticsearch integration spec
* `BeautySearchSpecV1.scala` only for narrow residual-text/spec fixes

Forbidden:

* no eval inventory changes;
* no Qdrant changes;
* no ranking rewrites;
* no docs unless asked.

### Qdrant-only work

Forbidden unless explicitly requested:

* no Elasticsearch changes;
* no `BeautySearchSpecV1` dictionary changes;
* no production search wiring;
* no hybrid/fallback;
* no score fusion;
* no reranking;
* no llama.cpp Dockerization;
* no starting/stopping llama.cpp from code.

llama.cpp is manual-only. The user starts it:

```bash
~/git/llama.cpp/build/bin/llama-server \
  -m ~/git/Qwen3-Embedding-0.6B-Q8_0.gguf \
  --embedding \
  --pooling last \
  -ub 8192 \
  --port 8081
```

Env-gated eval:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

Quality gate:

```bash
LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 \
QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true \
sbt 'project bifunctor-tagless' 'testOnly leaderboard.search.QdrantSemanticCandidateEvalSpec'
```

## Hybrid/fallback work

Hybrid starts with pure model/tests. Runtime hybrid requires explicit user approval.

Already completed pure steps:

* Qdrant candidate assembly;
* router + Qdrant candidate assembly composition;
* Qdrant candidate response projection.

Still forbidden unless explicitly requested:

* no `BeautySearchService` wiring;
* no ES/Qdrant hybrid calls;
* no production fallback behavior;
* no score fusion;
* no reranking;
* no Qdrant-as-default;
* no Elasticsearch/Qdrant client changes.

Next runtime work must start with a read-only design/validation report unless the user explicitly asks to implement.

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
* exact error;
* changed files;
* whether the failure reproduces alone;
* whether it appears related to the patch;
* smallest safe next diagnostic command.

For search eval failures, also report:

* query id and query text;
* parsed intent and remaining text, if available;
* top ids and failed assertions;
* raw hit count and request JSON for ES/Qdrant, if relevant.

Then fix only that failure with the smallest safe change.
