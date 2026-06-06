# AGENTS.md

## Core rules

* Inspect nearby repo code before using framework APIs from memory.
* One patch = one purpose.
* Do not mix code, tests, docs, build files, and AGENTS edits unless explicitly asked.
* Do not make broad refactors while fixing one failing test.
* Existing focused tests and route-level HTTP contract tests are source of truth.
* Do not commit debug output, logs, build artifacts, or temporary `println`.
* If the requested change needs wider scope, stop and report.

## Verification rules

Use explicit labels:

* `FOCUSED GREEN`: requested focused suite passed; full repo status unknown.
* `FULL GREEN`: full `sbt test` passed.
* `FULL RED`: full `sbt test` failed.
* `VERIFICATION BLOCKED`: sbt/docker/local permissions blocked verification.
* `USER-VERIFIED FULL GREEN`: user ran the exact command and reported green.

Do not call work commit-ready unless `FULL GREEN`, `USER-VERIFIED FULL GREEN`, or the user explicitly accepts focused-only verification.

For `src/main` changes, run focused checks and then full test unless the user accepts focused-only.

If full test fails, stop, report the failing suite/test and exact error, then fix only that failure.

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

## Warning rules

* Do not add `@nowarn` as a first fix.
* Never add `@nowarn("msg=Unreachable")`.
* For unreachable match cases, fix the match instead of suppressing it.
* `@nowarn` is allowed only when exact, narrow, intentional, and explained.
* Prefer removing unused imports, params, or dead code.

## Distage rules

* Distage startup follows dependency edges, not binding order.
* `ModuleDef` order and `memoizationRoots` order are not sequencing guarantees.
* Distage has graph GC; inspect roots, axes, and suite inheritance.
* Do not remove `@unused` parent repo dependencies if they preserve FK table creation order.
* Disabled experiment activation must not accidentally construct heavy Qdrant/semantic dependencies. Use explicit axis/config, by-name/factory/resource boundaries, or separate modules.

## Seed-backed snapshot rule

If a test reads seed JSON and then loads a repo-backed seed-scoped snapshot, it must depend directly on `BeautyQSeedReady` before repository reads.

Applies to:

* `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories`
* any seed-json + shared-Postgres snapshot path

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

Do not rely on `Mode.Test`, `memoizationRoots`, isolated green runs, or timing.

If a spec is green alone but full suite fails with `Seed-scoped search snapshot is missing Category/Service/Master`, check for a missing direct `BeautyQSeedReady` edge before changing search, Qdrant, Elasticsearch, or seed data.

## HTTP / Tapir rules

* Put pure Tapir endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters.
* Reuse existing support helpers.
* Do not migrate many endpoints in one patch.
* Do not change malformed path/body/exception contracts unless explicitly asked.

Beauty single-entity GETs use typed `200 domain JSON / 404 HttpApiFailure JSON`. `ProfileApi` is out of that scope.

## MasterServiceOfferVariant invariants

Do not touch `MasterServiceOfferVariant` codecs, storage, validation, or JSON shape unless explicitly asked.

Preserve:

* `intAttributes`
* `bigDecimalAttributes`
* `enumAttributes`
* `booleanAttributes`
* enum values as stable `stringCode`
* unified numeric storage path

## BeautyQ search principles

Search semantics live in DSL/spec data, not backend interpreters.

Allowed place for BeautyQ semantics:

* `BeautySearchSpecV1.scala`

Do not hardcode service names, query phrases, eval query ids, ranking rules, or attribute semantics inside ES/Qdrant clients, generic parser/interpreter code, or in-memory backends.

Elasticsearch owns:

* lexical search
* filters
* facets
* exact attributes
* price/duration
* lexical ranking
* normal lexical response assembly

Qdrant owns:

* semantic candidate recall only

Rules:

* ES lexical baseline intentionally leaves broad semantic gaps.
* Qdrant semantic candidates cover broad semantic eval cases.
* Do not close semantic gaps with broad lexical dictionary hacks.
* Residual text alone must never route to Qdrant.
* Hard-negative/noise queries must not route to Qdrant because of residual text.
* Eval query ids may appear in tests/docs, not production routing.

## Current search architecture

Current direction:

```text
domain DSL/spec
→ generic lexical/semantic retrieval boundaries
→ generic hybrid retrieval container
→ BeautyQ-specific projection/merge policy
→ explicit non-production experiment
→ later production design
```

Already present:

* generic Qdrant document indexing seam
* Qdrant point-id validation boundary
* generic semantic document backend/hit
* generic lexical document backend/hit
* generic semantic assembly/projection seams
* generic hybrid retrieval container
* BeautyQ hybrid projection/merge policy
* BeautyQ hybrid response pipeline
* non-production BeautyQ hybrid experiment runner
* disabled-by-default activation skeleton
* Qdrant readiness config / collection identity / compatibility guard
* benchmark reporting and validation infrastructure

These are not production hybrid search.

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

llama.cpp is manual-only:

```bash
~/git/llama.cpp/build/bin/llama-server \
  -m ~/git/Qwen3-Embedding-0.6B-Q8_0.gguf \
  --embedding \
  --pooling last \
  -ub 8192 \
  --port 8081
```

Env-gated Qdrant run:

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

## Hybrid / experiment rules

Hybrid starts with pure model/tests and explicit non-production experiments. Runtime hybrid requires explicit user approval.

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

Current hybrid policy:

* generic hybrid retrieval container is not a ranking policy
* BeautyQ projection/merge is domain-specific
* lexical-first semantic-supplement ordering is explicit
* ES and Qdrant scores stay separate
* display scores are not fused ranking scores
* facets and inferred filters stay lexical/parser-owned unless a separate policy is approved

Before non-production wiring, decide/design:

* activation axis/config
* disabled means Qdrant dependencies are not constructed
* who creates readiness config
* who creates collections
* who runs snapshot indexing
* kill switch / explicit invocation path
* diagnostics surface

## Qdrant lifecycle rules

Versioned collection names are the current policy. No production alias/blue-green yet.

Qdrant readiness must use one source of truth:

* collection name
* vector name
* vector dimension
* distance
* embedding model identity when available

Dimension/vector/distance mismatch must fail fast.

Delete/recreate is allowed only in tests/non-production experiments. Never silently recreate an active production-like collection.

Snapshot indexing with a guard must index only the collection that was checked for compatibility.

Qdrant point ids must be Qdrant-compatible ids. Arbitrary domain ids belong in payload.

## Benchmark rules

Benchmark output is decision support, not production automation.

Do not use benchmark decisions as:

* automatic model switch
* routing policy
* score calibration
* production rollout signal

Benchmark runner must fail on:

* duplicate candidate ids
* unexpected candidate ids
* result query ids without expectations
* missing expected query results
* duplicate result query ids per candidate

The benchmark subset is still small. Do not make broad model-quality claims until the subset is expanded.

## Dictionary rules

Keep dictionary fixes narrow.

Prefer exact phrases, contextual `requires`, conflict-preventing `excludes`, and safe no-op residual cleanup.

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

* suite/test name
* exact error
* changed files
* whether it reproduces alone
* whether it appears related to the patch
* smallest safe next diagnostic command

For search eval failures also report query id/text, parsed intent, remaining text, top ids, failed assertions, and raw ES/Qdrant request details if relevant.

Then fix only that failure with the smallest safe change.
