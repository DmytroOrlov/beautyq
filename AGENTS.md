# AGENTS.md

## Operating rules

* Inspect nearby repo code before using framework APIs from memory.
* One patch = one purpose. Do not mix code, tests, docs, build files, and AGENTS edits unless explicitly asked.
* Do not make broad refactors while fixing one failing test.
* Existing focused tests and route-level HTTP contract tests are source of truth.
* Do not commit debug output, logs, build artifacts, or temporary `println`.
* If the requested change needs wider scope, stop and report the smallest safe next step.

## Architecture review context

Use `docs/codebase-review/README.md` as the navigation entrypoint when you need current BeautyQ architecture context. `docs/codebase-review/INVENTORY.md` is the factual index; the other files are human-oriented architecture guides.

Do not treat roadmap docs or experiments as production behavior. The reviewed status is:

* No production HTTP Beauty search route or production `BeautySearchService` / `BeautySearchBackend` binding was found in inspected app wiring.
* `BeautySearchService.Impl` is the verified service implementation name; do not use stale `BeautySearchService.Live` wording.
* Qdrant and hybrid search are non-production/manual-local/experimental unless a task explicitly changes that.
* Elasticsearch has interpreters/client/integration-test coverage, but no verified production Beauty search runtime wiring.
* `Salon` is not a first-class inspected model; current domain uses `Master` and `MasterLocation`.
* `MasterServiceOfferVariant` is the central purchasable/search-result unit. Do not call it bookable unless implementing real booking/scheduling support.
* Benchmark output is decision support, not production automation.

## Verification labels

Use explicit labels:

* `FOCUSED GREEN`: requested focused suite passed; full repo status unknown.
* `FULL GREEN`: full `sbt test` passed.
* `FULL RED`: full `sbt test` failed.
* `VERIFICATION BLOCKED`: sbt/docker/local permissions blocked verification.
* `USER-VERIFIED FULL GREEN`: user ran the exact command and reported green.

Do not call work commit-ready unless `FULL GREEN`, `USER-VERIFIED FULL GREEN`, or the user explicitly accepts focused-only verification.

For `src/main` changes, run focused checks and then full test unless the user accepts focused-only. If full test fails, stop, report the failing suite/test and exact error, then fix only that failure.

## sbt rules

* Do not run sbt commands in parallel.
* Prefer one chained, project-scoped sbt command.
* Do not use `-no-server` unless the user explicitly asks.
* If sbt hits `~/.sbt/boot/sbt.boot.lock`, retry the same command once with local permission/escalation.
* If escalation is unavailable or rejected, report `VERIFICATION BLOCKED` and the exact command for the user.
* Do not edit source to work around sbt locks.

Preferred focused shape:

```bash
sbt 'project bifunctor-tagless' Test/compile 'testOnly leaderboard.search.BeautySearchPureSpec'
```

Cold runtime reset:

```bash
docker rm -f $(docker ps -a -q -f "label=distage.type") || true
find . -type d -name target -print0 | xargs -0 rm -rf
sbt test
```

Use `sbt --shutdown` only for stale compile/classpath state, not for runtime test failures.

## Context bundle rule

When collecting context for ChatGPT, write a unique file and copy that file:

```bash
OUT="/tmp/beautyq-<topic>-$(date +%Y%m%d-%H%M%S)-$RANDOM.txt"
{ echo "## git status <random> <topic>"; git status --short; } > "$OUT"
cpf "$OUT"
echo "$OUT"
```

Do not rely on generic `Pasted text.txt` or stale numbered files.

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
* Beauty single-entity GETs use typed `200 domain JSON / 404 HttpApiFailure JSON`; `ProfileApi` is out of that scope.
* Route-level contract tests override assumptions from old planning docs or Tapir defaults.

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

## BeautyQ search principles

Search semantics live in DSL/spec data, not backend interpreters.

Allowed place for BeautyQ semantics:

* `BeautySearchSpecV1.scala`

Do not hardcode service names, query phrases, eval query ids, ranking rules, or attribute semantics inside ES/Qdrant clients, generic parser/interpreter code, or in-memory backends.

Elasticsearch owns lexical search, filters, facets, exact attributes, price/duration, lexical ranking, and normal lexical response assembly.

Qdrant owns semantic candidate recall only.

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

* `BeautySearchService` production wiring
* production Distage wiring
* ES/Qdrant production hybrid calls
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

Before non-production wiring, decide activation axis/config, disabled-construction behavior, readiness config ownership, collection creation, snapshot indexing, kill switch, explicit invocation path, and diagnostics surface.

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
