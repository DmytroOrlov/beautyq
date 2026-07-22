# AGENTS.md

## Core rules

* Keep patches atomic: one purpose, no unrelated refactors or mixed risk layers. If wider scope is required, stop with the smallest safe next step.
* Inspect nearby repository code before using framework APIs from memory.
* Focused tests and route-level HTTP contract tests are the primary source of truth. If tests, docs, and implementation conflict, report it; do not guess.
* Do not perform broad architecture, audit, or design work unless explicitly requested. Make bounded edits and focused checks only.
* Reports are brief: focused result, deviations or compile fixes, and blocked verification.
* Canonical docs: read once and only at named ranges. If the prompt supplies current-state evidence or replacement text, do not read first; update once after code and proofs stabilize.
* Every delegated prompt, for every model tier, must inline exact paths, signatures, imports, fixtures, contracts, and replacements. Treat them as authoritative; missing or contradictory evidence means `NEED_BUNDLE`, not rediscovery.
* After a first read, use exact ranges or `git diff`. Compiler errors permit only the reported file/range and named definition. Search only exact symbols in listed paths; no repository/home/cache/filesystem-wide grep/find unless discovery is explicit.
* Batch one planned edit pass per file and at most one diagnostic pass. Do not alternate micro-edits, rereads, and compiles.
* Use one initial checklist and at most one completion update before the final report.
* Default budget: 12 file-read calls, 4 exact searches, 3 sbt invocations, and 2 todo updates. Exceed it only for a named blocker and report why.
* Keep temporary files and generated output inside the repository. Do not use explicit absolute scratch/device paths such as `/tmp`, `/var/tmp`, `/private/tmp`, `/dev`, or `/dev/null` unless the user provides one.

## Ownership and abstraction

* Put behavior at its source of truth; reusable layers must not hard-code one app/domain.
* Before framework, DSL, runtime, interpreter, or adapter edits, name the generic and app-specific boundaries.
* Keep app-specific names, defaults, policies, and compatibility shims at the edge unless the task changes the generic contract.
* Prove reusable code with neutral fixtures or contract tests; app examples alone are insufficient.
* If behavior preservation requires domain names in a reusable layer, stop and report the boundary conflict.
* Prefer small explicit adapters over broad “generic” code that secretly knows one domain.

### Business declaration DSLs

Search-domain work follows [docs/search/DOMAIN_AUTHORING_PRINCIPLES.md](docs/search/DOMAIN_AUTHORING_PRINCIPLES.md). Before changing a domain or reusable search component, verify:

* new business policy is reachable from the canonical domain entry point;
* domain code declares policy; repeated mechanics remain framework-owned;
* a neutral fixture/tracer or second unrelated domain challenges the reusable boundary;
* registries, trees, traces, ledgers, fingerprints, and docs derive from one executable declaration.

Domain declarations explicitly state legitimately variable choices: topology, identity selection, String keyword/text meaning, capabilities, public names, dynamic inventories, projection joins, invariants, and backend policy. Reusable layers derive tautological evidence: nominal codecs/type IDs, direct value type/path/default ID/semantic, extraction presence, unambiguous non-String kind, registration order, identity exclusion, document assembly, and structural rendering.

“Explicit” applies to business policy, not facts already fixed by selectors, types, or declaration order. Repeated name/type/path/semantic literals, parallel ordered field lists, manual document folds, and domain-owned generic renderers are review red flags. Low-level constructors may remain escape hatches, but are not the canonical new-domain example.

## Verification

Labels:

* `FOCUSED GREEN`: requested focused suite passed; full repository unknown.
* `USER-VERIFIED FULL GREEN`: user ran the exact full command and reported green.
* `VERIFICATION BLOCKED`: permissions, resources, sbt, Docker, environment, or agent policy prevented verification.

Rules:

* Delegated agents never run broad/full tasks such as unscoped `test`, `Test/test`, or project-wide tests. If full confidence is required, report focused results and require coordinator/user verification.
* Focused checks are never `FULL GREEN`.
* Focused checks alone cannot make plugin/module-shape, production-graph, lifecycle/readiness, or other full-graph-sensitive changes commit-ready; require coordinator/user full verification.

## sbt

* Use one chained sbt command; never run sbt in parallel.
* Run a repository validation wrapper exactly unless it is full-suite. Otherwise run quoted focused tasks from the repository root with the sandbox options below.
* Scope compile/tests to the owning subproject. Root aggregate `Test/compile` is not a prerequisite for one spec and may initialize unrelated graphs or socket checks.
* If asked for a full suite, report `VERIFICATION BLOCKED` by policy and ask coordinator/user to run it.
* Do not run malformed forms such as `sbt Test/compile ...` or `sbt about`. Never pipe sbt through `tail`, `head`, `tee`, or grep: pipelines can truncate root diagnostics and return the filter status instead of sbt status. A repository wrapper may save the full log while printing every diagnostic block and the final summary.
* Do not run `Test/compile` before `testOnly` for the same project: `testOnly` already compiles. Use a separate compile task only when no final test task covers the changed source or when localizing a compile failure.
* On compile failure, inspect all diagnostics from the complete output, group them by root cause, apply all source-confirmed fixes in one batch, then rerun. Never rerun after fixing only the last visible error.
* Do not probe setup (`type/which sbt`, `java -version`, `$JAVA_HOME`, `$SBT_OPTS`, `.sbtopts`, `.jvmopts`), inspect launcher lines, or resolve tool paths outside the repository unless the exact command fails with a missing-command/setup error.
* Before the first sandboxed sbt command, create `target/codex-sbt/ivy2` and use `-Dsbt.server.forcestart=true -Dsbt.ivy.home=target/codex-sbt/ivy2`; `-Dsbt.server=false` alone does not bypass the boot socket.
* If the shared Coursier cache is read-only, rerun the same command with `COURSIER_CACHE=target/codex-sbt/coursier-cache`. Uncached artifacts may require network approval; never request home-cache writes.
* If sbt reaches the task and project code fails to bind a Unix/TCP socket with `Operation not permitted`, launcher/cache setup succeeded. Request escalation only for that exact focused task.
* If socket/network escalation is unavailable, report `VERIFICATION BLOCKED` and the exact command.
* Never edit source to work around sbt locks.
* For stale recursive targets or `File name too long`, clean target directories and rerun the same command; report environment cleanup, not a source change.

Preferred form:

```bash
mkdir -p target/codex-sbt/ivy2
sbt --batch --no-global \
  -Dsbt.server=false \
  -Dsbt.server.forcestart=true \
  -Dsbt.ivy.home=target/codex-sbt/ivy2 \
  'projectName/testOnly package.SomeSpec'
```

## Database schema and reset

* BeautyQ has no persistent production database requiring forward schema migration.
* Do not add `ALTER TABLE`, backfills, legacy fallback values, migration stages, or a migration framework.
* Change fresh `CREATE TABLE`, seed data, repositories, and tests directly.
* After an intentional schema change, reused Distage containers may hold stale data. Reset only confirmed stale Distage state:

```bash
docker rm -f $(docker ps -a -q -f "label=distage.type") || true
```

* Do not reset for unexplained source/test failures. Rerun the same validation and report the reset as environment cleanup.

## DI, lifecycle, and graph

* Startup follows dependency edges, not binding/module/memoization-root order.
* Graph GC may remove unrooted bindings; inspect roots, axes/activation, and suite inheritance before production-module changes.
* Disabled experiments must not construct heavy dependencies; use explicit axis/config, by-name/factory/resource boundaries, or separate modules.
* Focused/unit specs must not include whole production plugins/apps unless production graph coverage is explicit; prefer targeted modules, existing app/role/testkit fixtures, or coordinator/user full validation.

### Whole-plugin include hazard

* Never add focused-spec `include(LeaderboardPlugin.modules.api[IO])`; this also covers `apiBase` and similar whole-plugin includes.
* These can cause `IncludesDSL$Include.interpret` NPE or `Include.bindings() is null`.
* On either full-run signature, do not debug the first aborted suite as root cause. First find ad-hoc whole-plugin includes in tests and replace them with targeted modules, existing role/testkit fixtures, or explicit minimal bindings.
* If none exist, stop and request the stack trace, module snippets, and grep output.
* This is graph-construction evidence, not product-behavior evidence.
* Changes to `LeaderboardPlugin.modules.api` or whole-plugin include tests require coordinator/user full-project verification; delegated agents neither run it nor declare commit readiness from focused checks.

### Intentional dependency edges

* `@unused` is only for intentional dependency/lifecycle/readiness edges: roles, readiness, schema/table ordering, or constructor dependencies forcing graph construction.
* Never use `@unused`, `val _ = x`, or `@nowarn` for future placeholder parameters in pure functions.
* Remove unused imports/parameters/locals unless they are intentional edges or real behavior.
* Prompt signatures are subordinate to behavior: use a suggested parameter or remove it and report the deviation.
* Do not investigate scalac unused-symbol flags until unused code is removed and the failure persists.

### Readiness and weak sets

* Tests reading readiness/seed/bootstrap-dependent data must depend directly on the readiness edge before repository reads.
* Do not first alter unrelated search, storage, or external-client code for a missing readiness edge.
* Weak-set contributions may require concrete retention roots.
* Do not fake weak-set proof through alias bindings that bypass the weak set.

## Constructive tests

Axes:

* intention: `Contractual`, `Regression`, `Progression`, `Benchmark`;
* encapsulation: `Blackbox`, `Effectual`, `Whitebox`;
* isolation: `Atomic`, `Group`, `Communication`.

Defaults:

* `Contractual + Blackbox + Atomic`: pure functions, parsers, codecs, policies, single algebras;
* `Contractual + Blackbox + Group`: in-process service/module seams;
* `Communication`: only real external processes—databases, search engines, queues, model servers, Dockerized services, HTTP services;
* `Whitebox`: only when the internal detail is the contract.

Resource-backed DoD:

* use default local resources and run when available;
* cancel usefully only for genuinely external unavailable resources;
* for repository-owned `Scene.Managed` Docker, inject the endpoint through Distage—never hard-code localhost or cancel because it was not manually started;
* boolean environment gates are only for documented manual-artifact, destructive, or explicitly external workflows.

Prefer shared contract suites:

```scala
abstract class LadderTest extends BaseTest
final class LadderTestDummy extends LadderTest with DummyTest
final class LadderTestPostgres extends LadderTest with ProdTest
```

For simple pure models, prefer `AnyWordSpec`, deterministic UUID fixtures when needed, direct `assert`, and no effects/DI/runtime.

### Fixtures

Prefer argument injection, environment/service accessors, targeted modules, immutable fixture case classes, deterministic fixture services, and local typed effect runners.

Avoid suite-level mutable state, global singletons, random/time/UUID generation unless uniqueness is the contract, repeated raw runtime boilerplate, and focused specs including whole production graphs.

### Doubles and assertions

Do not default to `var` call logs, mutable counters, `Recording*`, `Counting*`, `CallCounter`, `RecordingSpy`, or `called/calls`. Prefer:

* `Expecting*`: validate inputs and fail on unexpected ones;
* `Scripted*`: return configured results;
* `FailIfCalled*`: prove non-use;
* `Stub*`: return fixed results without recording.

Do not mechanically replace `var` with `Ref`, atomics, or mutable collections. `Ref` is for fixture/repository state, not recording. Avoid exact call counts unless count is the contract; assert responses, failures, and diagnostics. Local `var` is allowed only for exact by-name/exactly-once thunk contracts when a pure form weakens the test.

Never use:

* `assert(true)`;
* empty success branches;
* partial matches in expecting fakes;
* unsafe `Map.apply`, `.head`, `.tail`, `.last`, `Option.get`, `.toOption.get`, or right/left projection `.get` unless totality is source-proven and documented;
* `isInstanceOf`/`asInstanceOf` when matching is practical;
* null assertions.

Production preserves typed errors with `Either`, typed effect constructors, or explicit domain failures. Tests use matching or `getOrElse(fail(...))`; decoder-cursor `.get` is allowed. Before final reporting after Scala-spec edits, scan all touched specs and fix unsafe extraction.

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

Expecting ADT fakes must match totally:

```scala
event match {
  case actual: ExpectedEvent if actual == expected =>
    ZIO.unit
  case other =>
    ZIO.dieMessage(s"Unexpected event: expected $expected, got $other")
}
```

## Scala

* Do not add `@nowarn` first; never add `@nowarn("msg=Unreachable")`.
* Fix unreachable matches. Any remaining `@nowarn` must be exact, narrow, intentional, and explained.
* Prompt imports/helpers are candidates, not paste-all requirements. Keep only used symbols.
* Before validation, remove unused imports, parameters, locals, helpers, and dead code; qualify/import nested members consistently. Unused diagnostics remain fatal—do not weaken compiler settings to reduce agent iterations.
* Report prompt-symbol pruning as compile safety, not behavior deviation.
* In Scala 3 tests, Unit lambdas/callbacks/branches must explicitly return `Unit`; do not leave `assert(...)` as the discarded final value—append `(): Unit` or otherwise return Unit.
* Enum `toString` is fine for incidental diagnostics. When source truth defines stable IDs/codes or explicit active order used by roots, traces, ledgers, wire, or public contracts, retain typed values and derive views; do not replace them with `toString`/`values` or generalize this exception.

## HTTP and Tapir

* Put pure Tapir contracts in the repository Tapir package, normally `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters; reuse support helpers.
* Do not migrate many endpoints in one patch.
* Do not alter malformed path/body/exception contracts unless requested.
* Route-level contract tests override planning docs and framework defaults.
* HTTP client algebras model method/body semantics explicitly. A bodyless endpoint must use a bodyless client method, not empty JSON, and be covered by real-client and scripted-client tests.

## Data/model invariants

* Do not change stable domain codecs, storage schemas, validation, or JSON shape unless requested.
* Preserve unified storage paths and stable enum/string codes unless changed by scope.
* Existing route and persisted JSON contracts are compatibility boundaries.

## Eval failures

Report case ID/name, input, parsed/decoded state when available, observed output, expected output, and failed assertion.

## Failure protocol

After one focused query or test fails or aborts, stop expanding. One compile task with multiple diagnostics is one failure: inspect and fix all diagnostics in the reported files that share the same root causes before rerunning. Report:

* suite/test name;
* exact error;
* whether it reproduces alone, when requested or safe;
* whether it appears patch-related;
* smallest safe next focused diagnostic command.

Missing external resources/environment means blocked or canceled verification, not product failure. Then fix only that failure with the smallest safe change.
