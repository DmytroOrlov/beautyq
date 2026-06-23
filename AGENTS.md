# AGENTS.md

## Operating rules

* Keep patches atomic: one purpose, no unrelated refactors or mixed risk layers. If broader changes are required, stop and report the smallest safe next step.
* Inspect nearby repo code before using framework APIs from memory.
* Existing focused tests and route-level HTTP contract tests are the primary source of truth for intended behavior; if they conflict with docs or implementation, flag the mismatch instead of guessing.

## Agent discipline

* Do not perform broad architecture/audit/design unless explicitly asked.
* Make bounded edits and focused checks only.
* If the requested change needs wider scope, stop and report the smallest safe next step.
* Reports should be short: focused result, deviations/compile fixes, blocked verification.
* Update docs immediately for production exposure, runtime behavior, architecture policy, or roadmap status changes.
* Docs should record current state briefly and without fluff.
* For BeautyQ search/eval/reporting tasks, use task-named docs and directly touched source/spec/resource files; do not read broad repo/plugin/route/DI/http files unless a failure or explicit conflict requires it.
* Do not create, read, or write explicit absolute scratch/device paths such as `/tmp`, `/var/tmp`, `/private/tmp`, `/dev`, or `/dev/null` for repo work, validation, or artifacts. Keep temporary files, generated artifacts, and one-off helper outputs inside the repo working tree unless the user explicitly provides another path.

## Verification

Labels:

* `FOCUSED GREEN`: requested focused suite passed; full repo unknown.
* `VERIFICATION BLOCKED`: sbt/docker/local permissions blocked verification.

Default repo rule: `bifunctor-tagless/src/main` changes need focused checks. Agent runs only requested focused checks, not full sbt test.

## sbt rules

* Do not run sbt commands in parallel, instead run one chained sbt command.
* If a repo-local validation wrapper is provided, run it exactly; otherwise run exact requested sbt commands from the repo working directory and keep sbt tasks quoted, e.g. `sbt 'Test/compile' 'testOnly leaderboard.search.SomeSpec'`.
* Do not run malformed or diagnostic variants such as `sbt Test/compile ...`, `sbt about`, `sbt ... | tail`, `sbt ... | head`, `sbt ... | tee`, or any command that rewrites, wraps, filters, or decomposes the requested validation command.
* Do not run setup probes (`type/which sbt`, `java -version`, `echo $JAVA_HOME`, `echo $SBT_OPTS`, `ls/cat .sbtopts .jvmopts`), inspect sbt wrapper/launcher lines, or read resolved tool paths outside the repo unless the exact command fails with a missing-command/setup error.
* If sbt hits `~/.sbt/boot/sbt.boot.lock`, retry the same command once with local permission/escalation.
* If escalation is unavailable, report `VERIFICATION BLOCKED` and the exact command.
* Do not edit source to work around sbt locks.
* If sbt fails with stale recursive target / `File name too long`, treat it as build-artifact cleanup: all `target` directories, then rerun the same command. Report it as cleanup, not source change.

Preferred focused shape:

```bash
sbt 'Test/compile' 'testOnly leaderboard.search.SomeSpec'
```

## Current BeautyQ production search

Current production route:

```text
POST /beauty-search
→ LeaderboardPlugin
→ BeautySearchRouteModules.seedCatalogElasticsearchPortConfigured
→ BeautySearchRouteModules.seedCatalogElasticsearch
→ BeautySearchCatalogBackendModules.seedResourceElasticsearch
→ BeautyQSeedLoader.ResourceLoader
→ BeautySearchReadyCatalogDocuments
→ ElasticsearchSeedIndexInitializer
→ ElasticsearchSearchBackend
→ BeautySearchService.Impl
→ BeautySearchApi
```

Facts to preserve:

* Production `/beauty-search` is ES-backed over seed-resource catalog snapshot, it is lexical/simple/catalog-first.
* `BeautySearchProductionInclusion*` exists as staging/helper boundary, not the active gate for the current route.
* Real route kill switch / enable-disable gate remains future work.
* `MasterServiceOfferVariant` is the central search-result unit.
* Benchmark output is decision support, not production automation.
* `seedCatalogInMemory` remains available as rollback/non-default.

Current route characterization:

* Positive limit returns `200 OK` capped by requested limit.
* Zero/negative limit returns `200 OK` with empty variant carousel.
* Huge limit is capped by `BeautySearchSpecV1.spec.carouselSpec.variantSize`.
* Coordinates are not range-validated.
* Query text is not length-validated.
* `BeautySearchReadyCatalogDocuments` rejects blank source / empty documents and preserves non-empty source/documents.
* Freshness, refresh/replacement, typed 4xx validation, observability, and kill-switch remain future hardening.

## Distage and seed rules

* Distage startup follows dependency edges, not binding order.
* `ModuleDef` order and `memoizationRoots` order are not sequencing guarantees.
* Distage has graph GC; inspect roots, axes, and suite inheritance.
* Weak set contributions may require concrete retention roots in tests. Do not fake weak-set proof with alias bindings that bypass the weak set.
* Disabled experiment activation must not construct heavy Qdrant/semantic dependencies. Use explicit axis/config, by-name/factory/resource boundaries, or separate modules.

Intentional dependency edges:

* `@unused` is for intentional dependency/lifecycle/readiness edges: Distage roles, seed readiness, FK table creation order, and constructor dependencies that force graph construction.
* Do not use `@unused`, `val _ = x`, or `@nowarn` to keep future placeholder params in pure functions.
* If a param/import/local is unused and not an intentional edge, remove it or make it part of real behavior.
* Prompt snippets and suggested signatures are subordinate to behavior. If a suggested param is unused, either use it in the real contract or remove it and report the deviation.
* Do not investigate scalac warning flags for unused symbols unless the failure remains unexplained after removing unused code.

If a test reads seed JSON and then loads a repo-backed seed-scoped snapshot, it must depend directly on `BeautyQSeedReady` before repository reads.

Required shape:

```scala
(seedReady: BeautyQSeedReady, repos...) =>
  loadDocuments(seedReady, repos...)

private def loadDocuments(
  @unused seedReady: BeautyQSeedReady,
  repos...
): IO[QueryFailure, List[VariantSearchDocument]] =
  ...
```

If full suite fails with “Seed-scoped search snapshot is missing Category/Service/Master”, check for missing direct `BeautyQSeedReady` edge before changing search, Qdrant, Elasticsearch, or seed data.

### Whole-plugin include hazard

Do not use `include(LeaderboardPlugin.modules.api[IO])` inside focused/unit spec `ModuleDef`s.

Use targeted modules, existing app/role/testkit fixtures, or full-suite validation for production graph coverage.

The reproduced hazard is ad-hoc test-local `include(LeaderboardPlugin.modules.api[IO])`, which can trigger `IncludesDSL$Include.interpret` NPE in Distage 1.2.20 and 1.2.25. `BeautySearchProductionInclusion*` bindings are not proven direct root cause.

Changes touching `LeaderboardPlugin.modules.api` or whole-plugin include tests require full project test; repeat full once for plugin/module shape changes because the failure was intermittent.

## Constructive test style

Use constructive taxonomy instead of old `unit / functional / integration` labels.

Axes:

* Intention: `Contractual`, `Regression`, `Progression`, `Benchmark`.
* Encapsulation: `Blackbox`, `Effectual`, `Whitebox`.
* Isolation: `Atomic`, `Group`, `Communication`.

Defaults:

* `Contractual + Blackbox + Atomic` for pure functions, parsers, codecs, policies, and single algebras.
* `Contractual + Blackbox + Group` for in-process service/module seams.
* `Communication` only for real external processes such as Postgres, Qdrant, Elasticsearch, Llama, Docker, or HTTP.
* `Whitebox` only when the internal detail is the explicit contract.

Resource-backed test DoD: use distage/default local resources, run when resources are available, and cancel with a useful reason when unavailable. Boolean env gates are allowed only for true manual-artifact, destructive, or explicitly external workflows; such gates must be documented.

Prefer abstract contract suites over duplicated implementation-specific tests:

```scala
abstract class LadderTest extends LeaderboardTest
final class LadderTestDummy extends LadderTest with DummyTest
final class LadderTestPostgres extends LadderTest with ProdTest
```

For simple pure search/eval model tests, prefer `AnyWordSpec`, deterministic UUID fixtures, direct `assert`, and no effects/Distage/runtime.

### Distage/ZIO fixture style

Prefer:

* argument injection;
* ZIO environment accessors for service scenarios;
* targeted `ModuleDef`s;
* immutable fixture case classes;
* deterministic fixture services such as `Rnd[F]`;
* local typed effect runners such as `runIO(effect: IO[E, A])`.

Avoid:

* suite-level mutable state;
* global singletons;
* direct random/time/UUID generation unless uniqueness is the contract;
* raw `Runtime.default.unsafe.run(...).getOrThrowFiberFailure()` boilerplate spread through tests;
* focused specs that include the whole production plugin graph.

### Test doubles and assertions

Do not introduce `var` call logs, mutable counters, `Recording*`, `Counting*`, `CallCounter`, `RecordingSpy`, or `called/calls` probes by default.

Prefer:

* `Expecting*`: validates expected inputs and fails on unexpected ones.
* `Scripted*`: returns configured results.
* `FailIfCalled*`: proves collaborator is unused.
* `Stub*`: returns fixed results without recording.

Do not mechanically replace `var` with `ZIO Ref`, `AtomicInteger`, `AtomicReference`, or mutable collections. `Ref` is acceptable for in-memory fixture/repository state, not as a recording spy.

Avoid exact call-count assertions unless call count is the explicit contract. Prefer assertions over returned responses/failures/diagnostics.

Local `var` is acceptable only for exact by-name/exactly-once thunk contracts when a pure rewrite would weaken the test.

Do not use:

* `assert(true)`;
* empty success branches in pattern matches;
* partial matches in expecting fakes;
* Do not use unsafe extraction such as `.toOption.get`, `Option.get`, `RightProjection.get`, or `LeftProjection.get`. In production code, preserve typed errors with `Either`, `ZIO.fromEither`, or explicit domain failures. In tests, use pattern matching with `fail(...)` so failures keep useful context. For decoded `Map`, `List`, `Option`, `Either`, or JSON-derived structures, prefer pattern matching with useful failure messages; do not use `Map.apply`, `.head`, `.tail`, `.last`, `.get`, `.toOption.get`, or right/left projection `.get` unless totality is source-proven and documented. Before final report after editing Scala specs, scan touched specs for these forms and fix them; `getOrElse(fail(...))` and decoder cursor `.get` are allowed.
* `isInstanceOf` / `asInstanceOf` when pattern matching is practical;
* `assert(x == null)` / `assert(x != null)`.

Prefer:

```scala
assert(result == ExpectedCase)
```

or:

```scala
result match {
  case Expected(value) => assert(value == expected)
  case other => fail(s"Expected ..., got $other")
}
```

Expecting fakes over ADTs must use total matches:

```scala
event match {
  case actual: ExpectedEvent if actual == expected =>
    ZIO.unit
  case other =>
    ZIO.dieMessage(s"Unexpected event: expected $expected, got $other")
}
```

## Scala rules

* Do not add `@nowarn` as a first fix.
* Never add `@nowarn("msg=Unreachable")`.
* Fix unreachable matches instead of suppressing them.
* `@nowarn` must be exact, narrow, intentional, and explained.
* Prompt-provided imports and helper snippets are candidate source-truth, not paste-all requirements. Use only the imports/helpers needed by the final code or tests. Before running validation or compilation, prune unused imports, params, locals, helper methods, and dead code, and qualify/import nested object members consistently. Report pruning prompt-provided unused symbols as a normal compile-safety step, not as a behavior deviation.
* In Scala 3 tests, avoid `E175` discarded-value fixes by making Unit-returning lambdas, callbacks, and match branches explicitly return `Unit`. Do not leave `assert(...)` as the final discarded value in a context typed as `Unit`; add a final `(): Unit` or otherwise make the branch return `Unit` before running validation.

## HTTP / Tapir rules

* Put pure Tapir endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters.
* Reuse existing support helpers.
* Do not migrate many endpoints in one patch.
* Do not change malformed path/body/exception contracts unless explicitly asked.
* Route-level contract tests override planning docs or Tapir defaults.
* For HTTP client algebras, model method/body semantics explicitly. Do not fake
  bodyless endpoints by sending empty JSON objects such as `Json.obj()`. If an
  endpoint is specified or observed as bodyless, add/use a bodyless client method
  and cover it in both the real client spec and scripted-client tests.

Beauty search route:

* `POST /beauty-search` is production-exposed.
* Bad JSON/body currently returns `500` with empty body; do not call it desired contract.
* Do not change route JSON, request validation, or `TapirHttpSupport` error mapping without explicit contract task.

Beauty single-entity GETs use typed `200 domain JSON / 404 HttpApiFailure JSON`; `ProfileApi` is out of that scope.

## MasterServiceOfferVariant invariants

Do not touch `MasterServiceOfferVariant` codecs, storage, validation, or JSON shape unless explicitly asked.

Preserve:

* `intAttributes`
* `bigDecimalAttributes`
* `enumAttributes`
* `booleanAttributes`
* enum values as stable `stringCode`
* unified numeric storage path

`MasterServiceOfferVariants.Postgres` uses `masterServiceOffers` and `masterLocations` as `@unused` FK readiness edges; `serviceVariantSchemas` is an active validation collaborator.

## BeautyQ search principles

Search semantics live in DSL/spec data, not backend interpreters.

Allowed place for BeautyQ semantics:

* `BeautySearchSpecV1.scala`

Do not hardcode service names, query phrases, eval query ids, ranking rules, or attribute semantics inside ES/Qdrant clients, generic parser/interpreter code, or in-memory backends.

Elasticsearch is the production Beauty search backend (lexical retrieval baseline for text search, structured filters, facets, exact/range/geo constraints).

Qdrant owns semantic candidate recall only. It is not currently the production Beauty search backend.

`InMemorySearchBackend` is a rollback/regression/pure backend. It is not an in-memory Elasticsearch and must not be treated as ES scoring/order/analyzer oracle.

Rules:

* ES lexical baseline intentionally leaves broad semantic gaps.
* Qdrant semantic candidates cover broad semantic eval cases.
* Do not close semantic gaps with broad lexical dictionary hacks.
* Residual text alone must never route to Qdrant.
* Hard-negative/noise queries must not route to Qdrant because of residual text.
* Eval query ids may appear in tests/docs, not production routing.

ES-native principle:

* Elasticsearch should be designed from Elasticsearch primitives: mappings, analyzers, bool/filter/range/geo queries, aggregations/facets, scoring/boosting, pagination/search_after, profile/debug where useful.
* Product response assembly is a projection over engine-native results.
* ES must not be forced to mimic current in-memory response order/scoring.

Qdrant-native principle:

* Qdrant should be designed from Qdrant primitives: embedding text, model identity, dimension, distance, topK, scoreThreshold, payload filters, missing lookup handling.
* Qdrant is semantic recall/complement candidate, not auto-helper.
* Hard filters/facets/price/duration/exact business constraints remain lexical/parser/ES-owned unless separately approved.

Hybrid policy:

* generic hybrid retrieval container is not ranking policy;
* BeautyQ projection/merge is domain-specific;
* lexical-first semantic-supplement order is explicit;
* ES and Qdrant scores stay separate;
* display scores are not fused ranking scores;
* facets and inferred filters stay lexical/parser-owned unless separately approved.

## Search task modes

Pick one mode before editing. Do not mix modes.

### Production Beauty route hardening

Allowed: route characterization tests, narrow contract tests, readiness/source diagnostics, explicit kill-switch work when requested, batched docs.

Forbidden unless explicitly requested: Qdrant/hybrid/Elasticsearch backend changes, route JSON changes, global Tapir error changes, ranking rewrites, production data-source replacement.

### Pure eval / parser / DSL

Allowed: `BeautySearchPureSpec.scala`, eval inventory files, narrow `BeautySearchSpecV1.scala` changes, pure engine-eval model/metric code.

Forbidden: ES runtime, Qdrant runtime, production wiring, docs unless asked.

### Elasticsearch eval

Use ES from ES primitives/capabilities, not by forcing it to mimic in-memory search order/scoring. Do not change eval inventory, Qdrant, route wiring, or docs unless asked.

### Qdrant-only

Forbidden unless explicitly requested: Elasticsearch changes, `BeautySearchSpecV1` dictionary changes, production search wiring, hybrid/fallback, score fusion/reranking, llama.cpp Dockerization, starting/stopping llama.cpp from code.

llama.cpp is manual-only. Qdrant semantic eval requires `LLAMA_CPP_EMBEDDING_URL`; quality assertions require `QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true`.

### Hybrid / experiment

Forbidden unless explicitly requested:

* production hybrid calls;
* production fallback;
* score fusion/reranking;
* Qdrant-as-default;
* residual-text routing;
* eval query ids in main code;
* HTTP/API routing metadata fields;
* production collection manager;
* startup auto-indexing;
* alias/blue-green implementation;
* benchmark decision as automatic model switch.

## B-lite / ES+Qdrant eval strategy

Reached:

```text
A. non-production real-resource Qdrant/hybrid manual runner
A→B. production-hybrid control-plane v0
B1/B2. production-hidden control-plane activation/handle and targeted module proof
```

Current strategy:

```text
B-lite. ES-native + Qdrant-native benchmark comparison
```

B-lite guardrails:

* Runtime hybrid expansion is paused after B2.
* ES and Qdrant may advance together only in eval/benchmark.
* Production serving remains sequential and unchanged.
* Simulated hybrid is offline benchmark/eval only.
* No Qdrant auto-supplement or `HybridServe` from benchmark alone.
* Resource-backed hidden Qdrant/hybrid module work is paused until ES/Qdrant eval comparison is improved.

M-ESQ-EVAL (= measured Elasticsearch-native + Qdrant-native evaluation comparison) is offline/eval-only and not complete. `EngineEval` pure/report/assembly and saved-report comparison support exist, but benchmark/eval output remains decision support, not production automation.

Not target yet:

```text
C. production /beauty-search hybrid backend
```

C requires separate design for route switch, lifecycle, readiness/observability, freshness/reindex, kill switch, fallback/no-fallback, score/ranking policy, and rollback.

## Qdrant lifecycle rules

* Versioned collection names are current policy.
* No production alias/blue-green yet.
* Qdrant readiness must use one source of truth: collection name, vector name, dimension, distance, and embedding model identity when available.
* Dimension/vector/distance mismatch must fail fast.
* Delete/recreate only in tests/non-production experiments.
* Never silently recreate an active production-like collection.
* Snapshot indexing with guard must index only the collection checked for compatibility.
* Qdrant point ids must be Qdrant-compatible ids; arbitrary domain ids belong in payload.

## Benchmark rules

Benchmark output is decision support, not production automation.

Do not use benchmark decisions as automatic model switch, routing policy, score calibration, or production rollout signal.

Benchmark runner must fail on duplicate candidate ids, unexpected candidate ids, result query ids without expectations, missing expected query results, and duplicate result query ids per candidate.

The benchmark subset is still small. Do not make broad model-quality claims until expanded.

For B-lite, compare by role, not “which engine wins overall”:

* ES recall by query class;
* Qdrant recall by semantic/broad class;
* Qdrant complement over ES misses;
* Qdrant noise rate where Qdrant should stay silent;
* ES ∩ Qdrant overlap;
* simulated hybrid gain over ES-alone;
* missing lookup rate;
* latency if available.

## Dictionary rules

Keep dictionary fixes narrow. Prefer exact phrases, contextual `requires`, conflict-preventing `excludes`, and safe no-op residual cleanup.

Do not implement generic NLP/negation for one failing query. Do not fix a query by changing ranking unless explicitly requested.

## Failure protocol

If one query/test fails, stop expanding the slice.

Report:

* suite/test name;
* exact error;
* whether it reproduces alone;
* whether it appears related to the patch;
* smallest safe next diagnostic command.

For search eval failures also report query id/text, parsed intent, remaining text, top ids, failed assertions, and raw ES/Qdrant request details if relevant.

Then fix only that failure with the smallest safe change.
