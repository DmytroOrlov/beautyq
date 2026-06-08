# AGENTS.md

## Purpose

This file contains stable repo-specific guardrails for BeautyQ work. Prompts should not repeat these rules unless a task needs a local exception. Inline only task-specific facts, exact signatures, changed files, and verification commands.

## Operating rules

* Inspect nearby repo code before using framework APIs from memory.
* One patch = one purpose. Do not mix unrelated risk layers.
* Do not make broad refactors while fixing one failing test.
* Existing focused tests and route-level HTTP contract tests are source of truth.
* Do not commit debug output, logs, build artifacts, copied dependency sources, or temporary `println`.
* Do not create `izumi/` or copy Distage/source dependency files into the repo.
* If a requested change needs wider scope, stop and report the smallest safe next step.
* Commit messages should be extended by default: subject plus body covering behavior, tests, unchanged boundaries, and verification caveats.

## Prompt / agent discipline

Stable rules live here. Do not paste the same long architecture warnings into every prompt.

When preparing prompts for weaker agents:

* Use small, mechanical tasks.
* Prefer one new test file or one existing spec update.
* Avoid design, Distage internals, runtime wiring, and multi-layer changes.
* Inline exact current signatures and files from the latest bundle.
* Give exact validation commands.
* Do not ask weak agents to infer architecture from docs.

Recommended `qwen3.6-35b-a3b` thinking budgets:

* `thinking-budget=128`: one-line docs tweak, delete/rename, mechanical fix.
* `thinking-budget=256`: small docs-only patch or simple test copied from an existing pattern.
* `thinking-budget=512`: test-only patch with existing Distage/ModuleDef/route/fake-client setup.
* `thinking-budget=1024`: only if comparing several existing specs or likely compile fixes around Distage/ZIO/typeclasses.
* `4096+`: do not use Qwen; split the task or wait for GPT-5.5.

Docs cadence:

* Do not update docs after every tiny characterization test.
* Batch related characterization results into one docs patch.
* Update docs immediately when production exposure, runtime behavior, architecture policy, or roadmap status changes.
* Documentation should record the result/current state, not serve as scratchpad for every micro-step.

## Architecture review context

Use `docs/codebase-review/README.md` as the navigation entrypoint for BeautyQ architecture. `docs/codebase-review/INVENTORY.md` is the factual index; the other files are human-oriented architecture guides.

Do not treat roadmap docs or experiments as production behavior. Check current code/tests when production wiring matters.

Current verified BeautyQ search status:

* `POST /beauty-search` is production-exposed through `LeaderboardPlugin`.
* The production include is `BeautySearchRouteModules.seedCatalogInMemory[F]`.
* The production backend is seed-resource catalog snapshot + `InMemorySearchBackend`.
* This production route is lexical/simple/catalog-first.
* It is not Elasticsearch, Qdrant, or hybrid search.
* `BeautySearchService.Impl` is the verified service implementation name; do not use stale `BeautySearchService.Live` wording.
* The earlier `BeautySearchProductionInclusionActivation` / handle / included-apis boundary still exists as a staging/helper boundary, but it is not the active gate for the currently exposed route.
* A real kill switch / enable-disable production route gate remains future work.
* `Salon` is not a first-class inspected model; current domain uses `Master` and `MasterLocation`.
* `MasterServiceOfferVariant` is the central purchasable/search-result unit. Do not call it bookable unless implementing real booking/scheduling support.
* Benchmark output is decision support, not production automation.

Current production route characterization:

* Limit behavior:
  * positive limit returns `200 OK` with variants capped by requested limit;
  * zero and negative limits return `200 OK` with empty variant carousel;
  * huge limits are capped by `BeautySearchSpecV1.spec.carouselSpec.variantSize`.
* Invalid request behavior currently returns `500 InternalServerError` with empty body for malformed JSON, empty body, wrong limit type, and missing required fields. This is current behavior, not desired final contract.
* Coordinates are not range-validated: out-of-range and huge finite `userLat` / `userLon` currently return `200 OK`.
* Query text is not length-validated: empty, whitespace-only, normal, and very long queries currently return `200 OK`.
* `BeautySearchReadyCatalogDocuments` rejects empty/blank source and empty documents, and preserves non-empty source/documents.
* Freshness, refresh/replacement, staleness bounds, structured errors, typed 4xx validation, observability, and kill-switch remain future hardening.

## Verification labels

Use explicit labels:

* `FOCUSED GREEN`: requested focused suite passed; full repo status unknown.
* `FULL GREEN`: full `sbt test` or requested full project test passed.
* `FULL RED`: full test failed.
* `VERIFICATION BLOCKED`: sbt/docker/local permissions blocked verification.
* `USER-VERIFIED FULL GREEN`: user ran the exact command and reported green.

Do not call work commit-ready unless `FULL GREEN`, `USER-VERIFIED FULL GREEN`, or the user explicitly accepts focused-only verification.

For `src/main` changes, run focused checks and then full test unless the user accepts focused-only. If full test fails, stop, report the failing suite/test and exact error, then fix only that failure.

## sbt rules

* Do not run sbt commands in parallel.
* Prefer one chained, project-scoped sbt command.
* Do not use `-no-server` unless explicitly asked.
* `sbt --shutdown` is not valid for this repo launcher. Do not use it.
* If sbt hits `~/.sbt/boot/sbt.boot.lock`, retry the same command once with local permission/escalation.
* If escalation is unavailable or rejected, report `VERIFICATION BLOCKED` and the exact command for the user.
* Do not edit source to work around sbt locks.
* If sbt fails with stale recursive target / `File name too long`, treat it as build-artifact cleanup:
  * do not run `sbt --shutdown`;
  * run `sbt clean` or remove generated `target` directories;
  * rerun the same focused command;
  * report this as build-artifact cleanup, not source change.

Preferred focused shape:

```bash
sbt 'project bifunctor-tagless' Test/compile 'testOnly leaderboard.search.BeautySearchPureSpec'
```

Cold runtime reset when explicitly needed:

```bash
docker rm -f $(docker ps -a -q -f "label=distage.type") || true
find . -type d -name target -print0 | xargs -0 rm -rf
sbt test
```

## Context bundle rule

When collecting context for ChatGPT, write a unique file and copy that file:

```bash
OUT="/tmp/beautyq-<topic>-$(date +%Y%m%d-%H%M%S)-$RANDOM.txt"
{ echo "## git status <topic>"; git status --short; } > "$OUT"
cpf "$OUT"
echo "$OUT"
```

Do not rely on generic `Pasted text.txt`, screenshots, or stale numbered files for repo state.

A bundle should include only what the next prompt needs. For weak agents, prefer compact bundles around changed files, nearby specs, and anchors.

## Scala warning rules

* Do not add `@nowarn` as a first fix.
* Never add `@nowarn("msg=Unreachable")`.
* For unreachable match cases, fix the match instead of suppressing it.
* `@nowarn` is allowed only when exact, narrow, intentional, and explained.
* Prefer removing unused imports, params, or dead code.

## Distage and seed rules

* Distage startup follows dependency edges, not binding order.
* `ModuleDef` order and `memoizationRoots` order are not sequencing guarantees.
* Distage has graph GC; inspect roots, axes, and suite inheritance.
* Weak set contributions may require concrete retention roots in tests. Do not fake weak-set proof with alias bindings that bypass the weak set.
* Do not remove `@unused` parent repo dependencies if they preserve FK table creation order.
* Disabled experiment activation must not accidentally construct heavy Qdrant/semantic dependencies. Use explicit axis/config, by-name/factory/resource boundaries, or separate modules.

If a test reads seed JSON and then loads a repo-backed seed-scoped snapshot, it must depend directly on `BeautyQSeedReady` before repository reads. This applies to `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` and any seed-json + shared-Postgres snapshot path.

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

Do not rely on `Mode.Test`, `memoizationRoots`, isolated green runs, or timing. If a spec is green alone but full suite fails with `Seed-scoped search snapshot is missing Category/Service/Master`, check for a missing direct `BeautyQSeedReady` edge before changing search, Qdrant, Elasticsearch, or seed data.

## HTTP / Tapir rules

* Put pure Tapir endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters.
* Reuse existing support helpers.
* Do not migrate many endpoints in one patch.
* Do not change malformed path/body/exception contracts unless explicitly asked.
* Route-level contract tests override assumptions from old planning docs or Tapir defaults.

Beauty search route:

* `POST /beauty-search` is currently production-exposed.
* Current bad JSON / bad body behavior is characterized as `500` with empty body. Do not call it desired contract.
* Do not change route JSON, request validation, or `TapirHttpSupport` error mapping without an explicit contract task.

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

## Distage plugin include guardrail

- Do not use `include(LeaderboardPlugin.modules.api[IO])` inside focused/unit spec `ModuleDef`s.
- Prefer targeted modules in specs that bind only the types the test needs.
- If the whole production plugin graph must be tested, use an established app/role/testkit fixture or full-suite validation, not ad-hoc `include()` calls.
- `BeautySearchProductionInclusion*` plugin bindings in `LeaderboardPlugin.modules.api` are unnecessary unless there is an explicit production design, but they are not proven direct root cause of the NPE.
- The hazard is ad-hoc test-local `include(LeaderboardPlugin.modules.api[IO])` which can trigger `IncludesDSL$Include.interpret` NPE in Distage 1.2.20 and 1.2.25.
- Any changes touching `LeaderboardPlugin.modules.api` or whole-plugin include tests require full `sbt 'project bifunctor-tagless' test`; repeat full once for plugin/module shape changes.

## FP test-style guardrails

Derived from recent refactor lessons. Do not repeat in every prompt.

### Avoid Java-style mutable spies in Scala tests

* Do not introduce `var` call logs, mutable counters, `Recording*` spies, or `called/calls` probes by default.
* Prefer immutable fixtures, scripted fakes, expecting fakes, fail-if-called collaborators, and assertions over returned responses/failures/diagnostics.
* Do not mechanically replace `var` with `ZIO Ref`, `AtomicInteger`, `AtomicReference`, or mutable collections.

### Remaining `var` exceptions must be explicit

* Local `var` is acceptable only when the exact contract is by-name/exactly-once evaluation and a pure rewrite would weaken the test or make it much noisier.
* Current accepted examples are exact-once thunk checks such as `apiEvaluations` / `apiConstructed`.

### Avoid Java-style null assertions

* Do not use `assert(x != null)` / `assert(x == null)`.
* Prefer type assertions, `Option`, pattern matching, or behavior that proves the value was materialized.

### Keep effect runners local and typed

* If a ScalaTest spec needs to run ZIO effects, use a small local helper such as `runIO(effect: IO[E, A])`.
* Do not spread raw `Runtime.default.unsafe.run(...).getOrThrowFiberFailure()` boilerplate through tests unless the surrounding suite already has that style and refactoring is out of scope.

### Distage focused specs must avoid whole-plugin includes

* Do not use `include(LeaderboardPlugin.modules.api[IO])` inside ad-hoc focused/unit spec `ModuleDef`s.
* Use targeted modules or existing app/role fixtures instead.
* Full `sbt 'project bifunctor-tagless' test` remains the production graph validation.

### Distinguish Ref fixture vs Ref spy

* `Ref` as a test fixture for immutable state tracking (e.g. HTTP contract state recording inputs/results) is acceptable.
* `Ref` as a recording spy — recording call counts, call sequences, captured inputs, or "was called" checks — is a whitebox smell.
* Prefer expecting fakes, scripted fakes, and fail-if-called collaborators over recording through `Ref`.

### Avoid Recording/Counting fake naming

* Avoid new fake names like `Recording*`, `Counting*`, `CallCounter`, `RecordingSpy`.
* Prefer names that describe behavior:
  * `Expecting*` — validates expected inputs, fails on unexpected ones.
  * `Scripted*` — returns configured results based on input.
  * `FailIfCalled*` — proves a collaborator is not used on a code path.
  * `Stub*` — returns fixed results without recording.

### Avoid exact call-count assertions

* Prefer expecting fake that validates expected input and fails on unexpected input.
* Prefer fail-if-called fake to prove a collaborator is not used.
* Prefer scripted fake that returns configured result and test asserts response/failure/diagnostics.
* Exact-once by-name thunk checks remain allowed as explicit exceptions (see "Remaining `var` exceptions").

### Reference pattern

* `BeautySearchAppGraphBoundarySpec.ExpectingBeautySearchBackend` — fails on unexpected input/intent, returns scripted result. Use this pattern instead of Recording/Counting fakes.

## BeautyQ search principles

Search semantics live in DSL/spec data, not backend interpreters.

Allowed place for BeautyQ semantics:

* `BeautySearchSpecV1.scala`

Do not hardcode service names, query phrases, eval query ids, ranking rules, or attribute semantics inside ES/Qdrant clients, generic parser/interpreter code, or in-memory backends.

Current production search route:

```text
POST /beauty-search
→ LeaderboardPlugin
→ BeautySearchRouteModules.seedCatalogInMemory
→ BeautyQSeedLoader.ResourceLoader
→ BeautySearchCatalogSnapshot
→ BeautySearchReadyCatalogDocuments
→ InMemorySearchBackend
→ BeautySearchService.Impl
→ BeautySearchApi
```

This route is seed-resource catalog snapshot + in-memory backend. It is not ES/Qdrant/hybrid.

Elasticsearch owns future lexical production candidates: filters, facets, exact attributes, price/duration, lexical ranking, and normal lexical response assembly. It is not currently the production Beauty search backend.

Qdrant owns semantic candidate recall only. It is not currently the production Beauty search backend.

Rules:

* ES lexical baseline intentionally leaves broad semantic gaps.
* Qdrant semantic candidates cover broad semantic eval cases.
* Do not close semantic gaps with broad lexical dictionary hacks.
* Residual text alone must never route to Qdrant.
* Hard-negative/noise queries must not route to Qdrant because of residual text.
* Eval query ids may appear in tests/docs, not production routing.

Current direction:

```text
domain DSL/spec
→ generic lexical/semantic retrieval boundaries
→ generic hybrid retrieval container
→ BeautyQ-specific projection/merge policy
→ explicit non-production experiment
→ later production design
```

Already-present seams are not production hybrid search. Runtime hybrid requires explicit user approval.

## Search task modes

Pick one mode before editing. Do not mix modes.

### Production Beauty route hardening

Allowed:

* route-level characterization tests;
* narrow contract tests;
* readiness/source diagnostics;
* explicit kill-switch design or implementation when requested;
* docs batch after a group of related characterization tests.

Forbidden unless explicitly requested:

* Qdrant/hybrid/Elasticsearch backend changes;
* route JSON changes;
* global Tapir error behavior changes;
* ranking rewrites;
* production data-source replacement.

### Pure eval / parser / DSL

Allowed:

* `BeautySearchPureSpec.scala`
* eval inventory files
* narrow `BeautySearchSpecV1.scala` changes

Forbidden:

* ES runtime changes
* Qdrant runtime changes
* production wiring
* docs unless asked

### Elasticsearch eval

Use only after the same pure slice is green.

Forbidden:

* eval inventory changes
* Qdrant changes
* ranking rewrites
* docs unless asked

### Qdrant-only

Forbidden unless explicitly requested:

* Elasticsearch changes
* `BeautySearchSpecV1` dictionary changes
* production search wiring
* hybrid/fallback
* score fusion/reranking
* llama.cpp Dockerization
* starting/stopping llama.cpp from code

llama.cpp is manual-only. Qdrant semantic eval requires `LLAMA_CPP_EMBEDDING_URL`; quality assertions require `QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true`.

### Hybrid / experiment

Still forbidden unless explicitly requested:

* production hybrid calls
* production fallback behavior
* score fusion/reranking
* Qdrant-as-default
* residual-text routing
* eval query ids in main code
* HTTP/API routing metadata fields
* production collection manager
* startup auto-indexing
* alias/blue-green implementation
* benchmark decision as automatic model switch

Hybrid policy:

* generic hybrid retrieval container is not a ranking policy;
* BeautyQ projection/merge is domain-specific;
* lexical-first semantic-supplement ordering is explicit;
* ES and Qdrant scores stay separate;
* display scores are not fused ranking scores;
* facets and inferred filters stay lexical/parser-owned unless a separate policy is approved.

Before non-production real-resource wiring, decide activation/config, disabled-construction behavior, readiness config ownership, collection creation, snapshot indexing, kill switch, explicit invocation path, and diagnostics surface.

## Qdrant / hybrid roadmap status

Current Qdrant/hybrid status:

* Generic lexical/semantic/hybrid seams exist.
* Qdrant point id, point builder, indexing, readiness, compatibility, and snapshot indexing guards exist.
* BeautyQ hybrid projection/pipeline exists with explicit carousel limits.
* Non-production composition and activation tests exist.
* Composition build is characterized as side-effect-free; `indexSnapshot()` and `semanticBackend.candidates(...)` are explicit calls.
* Readiness/compatibility guard behavior is characterized with fakes.
* Qdrant/hybrid remains non-production/manual-local/experimental.
* No production Qdrant/hybrid route is wired.

Near-term target:

```text
non-production real-resource Qdrant/hybrid manual runner
```

Not target yet:

```text
production hybrid backend
```

Approximate remaining work to the near-term target is tracked conversationally in qwen-runs; keep it out of code/docs unless asked.

## Qdrant lifecycle rules

* Versioned collection names are the current policy. No production alias/blue-green yet.
* Qdrant readiness must use one source of truth: collection name, vector name, vector dimension, distance, and embedding model identity when available.
* Dimension/vector/distance mismatch must fail fast.
* Delete/recreate is allowed only in tests/non-production experiments.
* Never silently recreate an active production-like collection.
* Snapshot indexing with a guard must index only the collection checked for compatibility.
* Qdrant point ids must be Qdrant-compatible ids. Arbitrary domain ids belong in payload.

## Benchmark rules

Benchmark output is decision support, not production automation.

Do not use benchmark decisions as automatic model switch, routing policy, score calibration, or production rollout signal.

Benchmark runner must fail on duplicate candidate ids, unexpected candidate ids, result query ids without expectations, missing expected query results, and duplicate result query ids per candidate.

The benchmark subset is still small. Do not make broad model-quality claims until the subset is expanded.

## Dictionary rules

Keep dictionary fixes narrow. Prefer exact phrases, contextual `requires`, conflict-preventing `excludes`, and safe no-op residual cleanup.

Do not add broad unconditional triggers such as:

* `gel`
* `face`
* `beauty`
* `рядом`
* `недорого`
* `коррекция`
* `снятие`

Do not implement generic NLP/negation for one failing query. Do not fix a query by changing ranking unless explicitly requested.

## Failure protocol

If one query/test fails, stop expanding the slice.

Report:

* suite/test name
* exact error
* changed files
* whether it reproduces alone
* whether it appears related to the patch
* smallest safe next diagnostic command

For search eval failures also report query id/text, parsed intent, remaining text, top ids, failed assertions, and raw ES/Qdrant request details if relevant.

Then fix only that failure with the smallest safe change.
