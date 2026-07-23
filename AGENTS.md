# AGENTS.md

Runtime tags: `[ALL]` every model, `[W]` weak/Qwen3.6, `[M]` medium/MiniMax-M3, `[S]` strong. Task tags are used only where behavior differs, such as `[CONTINUATION]` and `[DOCS]`. Untagged repository safety rules apply to all.

## Core rules

* Keep patches atomic: one purpose, no unrelated refactors or mixed risk layers. If wider scope is required, stop with the smallest safe next step.
* Inspect nearby repository code before using framework APIs from memory.
* Focused tests and route-level HTTP contract tests are the primary source of truth. If tests, docs, and implementation conflict, report it; do not guess.
* Do not perform broad architecture, audit, or design work unless explicitly requested. Make bounded edits and focused checks only.
* Reports are brief: focused result, deviations or compile fixes, and blocked verification.
* `[W][CONTINUATION]` When exact current-state anchors or replacement hunks are supplied, read canonical docs only at the named ranges and update them after code and proofs stabilize.
* `[M/S][DOCS]` A full read of the canonical owner is allowed for documentation deduplication, owner-map changes, or broad compatibility/cutover reconciliation.
* `[W]` After a first read, use exact ranges, exact symbols, and `git diff`; do not broaden discovery unless the task explicitly requires it.
* `[M/S]` A named dependency-frontier search is allowed when a public API, module edge, or complete diagnostic proves it necessary.
* Batch coherent edits per file and avoid micro-edit loops. Make another pass only when a new complete diagnostic or source contradiction requires it.
* `[W]` Use at most one compact checklist when the prompt requires it; do not rewrite the full checklist between edits. `[M/S]` Todo tooling is optional.
* Keep temporary files and generated output inside the repository. Do not use explicit absolute scratch/device paths such as `/tmp`, `/var/tmp`, `/private/tmp`, `/dev`, or `/dev/null` unless the user provides one.
## Ownership and abstraction

* Put behavior at its source of truth; reusable layers must not hard-code one app/domain.
* Before framework, DSL, runtime, interpreter, or adapter edits, name the generic and app-specific boundaries.
* Keep app-specific names, defaults, policies, and compatibility shims at the edge unless the task changes the generic contract.
* Prove reusable code with neutral fixtures or contract tests; app examples alone are insufficient.
* If behavior preservation requires domain names in a reusable layer, stop and report the boundary conflict.
* Prefer small explicit adapters over broad “generic” code that secretly knows one domain.

### Declarative business DSLs

Before changing a business declaration or reusable component, verify:

* new business policy is reachable from the canonical entry point;
* business code declares legitimate choices while repeated mechanics remain framework-owned;
* a neutral fixture, tracer, or structurally different consumer challenges the reusable boundary;
* registries, trees, traces, ledgers, fingerprints, and docs derive from one executable declaration.

Declarations state legitimately variable choices such as topology, identity selection, String keyword/text meaning, capabilities, public names, dynamic inventories, projection joins, invariants, and backend policy. Reusable layers derive facts already fixed by selectors, types, and declaration order.

Repeated name/type/path/semantic literals, parallel ordered field lists, manual document folds, and business-owned generic renderers are review red flags. Low-level constructors may remain escape hatches, but are not the canonical authoring example.
## Verification

* Delegated agents run focused commands only; they never run an unscoped full repository suite.
* If broader confidence is required, report the focused result and state the exact broader command for the user or an explicitly authorized primary/coordinator to run before committing.
* Report verification in plain language: command, result, useful counts, blocked resources, and remaining uncertainty.
* Do not use synthetic confidence labels in reports or commit messages.
* Focused checks alone cannot prove unrelated modules, the entire production graph, lifecycle/readiness across every axis, or the full repository.
## sbt

* Use one chained sbt command; never run sbt in parallel.
* Run a repository validation wrapper exactly unless it is full-suite. Otherwise run quoted focused tasks from the repository root with the sandbox options below.
* Scope compile/tests to the owning subproject. Root aggregate `Test/compile` is not a prerequisite for one spec and may initialize unrelated graphs or socket checks.
* A delegated agent asked for an unscoped full suite must decline and provide the exact command for the user or explicitly authorized primary/coordinator.
* Do not run malformed forms such as `sbt Test/compile ...` or `sbt about`, and never pipe sbt through `tail`, `head`, `tee`, or grep. Use a repository-owned validation wrapper when one exists. Until then, run the ordinary unpiped focused command; do not hand-roll shell redirection or summary parsing that could lose sbt's exit status or diagnostics.
* Do not run `Test/compile` before `testOnly` for the same project: `testOnly` already compiles. Use a separate compile task only when no final test task covers the changed source or when localizing a compile failure.
* On compile failure, inspect all diagnostics from the complete output, group them by root cause, apply all source-confirmed fixes in one batch, then rerun. Never rerun after fixing only the last visible error.
* Compiler diagnostics may authorize repair of direct retained callers inside the already named module frontier, but are not a deletion inventory. A retained contract, wiring, firewall, lifecycle, or resource proof is a repair target unless the task's exact manifest deletes its owner. Deleting an owner, adding a project edge, changing a public contract, or crossing into a new module frontier requires coordinator approval and an exact scope-expansion report.
* Do not probe setup (`type/which sbt`, `java -version`, `$JAVA_HOME`, `$SBT_OPTS`, `.sbtopts`, `.jvmopts`), inspect launcher lines, or resolve tool paths outside the repository unless the exact command fails with a missing-command/setup error.
* Before the first sandboxed sbt command, create `target/codex-sbt/ivy2` and use `-Dsbt.server.forcestart=true -Dsbt.ivy.home=target/codex-sbt/ivy2`; `-Dsbt.server=false` alone does not bypass the boot socket.
* If the shared Coursier cache is read-only, rerun the same command with `COURSIER_CACHE=target/codex-sbt/coursier-cache`. Uncached artifacts may require network approval; never request home-cache writes.
* If sbt reaches the task and project code fails to bind a Unix/TCP socket with `Operation not permitted`, launcher/cache setup succeeded. Request escalation only for that exact focused task.
* If socket/network escalation is unavailable, report the blocked focused command and exact environment error.
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

* Add forward migrations, backfills, legacy fallbacks, or a migration framework only when current repository source and deployment policy prove that persistent schema evolution is required.
* For disposable fresh-schema workflows, change `CREATE TABLE`, seed data, repositories, and tests directly.
* After an intentional schema change, reused Distage containers may hold stale data. Reset only confirmed stale Distage state:

```bash
docker rm -f $(docker ps -a -q -f "label=distage.type") || true
```

* Do not reset for unexplained source/test failures. Rerun the same validation and report the reset as environment cleanup.
## DI, lifecycle, and graph

* Startup follows dependency edges, not binding/module/memoization-root order.
* Graph GC may remove unrooted bindings; inspect roots, axes/activation, and suite inheritance before production-module changes.
* Disabled experiments must not construct heavy dependencies; use explicit axis/config, by-name/factory/resource boundaries, or separate modules.
* Focused/unit specs must not include whole production plugins/apps unless production graph coverage is explicit; prefer targeted modules, existing app/role/testkit fixtures, or broader verification outside the delegated task.

### Whole-plugin include hazard

* Never add focused-spec `include(LeaderboardPlugin.modules.api[IO])`; this also covers `apiBase` and similar whole-plugin includes.
* These can cause `IncludesDSL$Include.interpret` NPE or `Include.bindings() is null`.
* On either full-run signature, do not debug the first aborted suite as root cause. First find ad-hoc whole-plugin includes in tests and replace them with targeted modules, existing role/testkit fixtures, or explicit minimal bindings.
* If none exist, stop and request the stack trace, module snippets, and grep output.
* This is graph-construction evidence, not product-behavior evidence.
* Changes to `LeaderboardPlugin.modules.api` or whole-plugin include tests require broader full-project verification by the user or an explicitly authorized primary/coordinator; delegated agents do not run it and must not imply that focused checks cover the whole graph.

### Intentional dependency edges

* `@unused` is only for intentional dependency/lifecycle/readiness edges: roles, readiness, schema/table ordering, or constructor dependencies forcing graph construction.
* Never use `@unused`, `val _ = x`, or `@nowarn` for future placeholder parameters in pure functions.
* Remove unused imports/parameters/locals unless they are intentional edges or real behavior.
* Prompt signatures are subordinate to behavior: use a suggested parameter or remove it and report the deviation.
* Do not investigate scalac unused-symbol flags until unused code is removed and the failure persists.

### Readiness and weak sets

* Any test or startup resource that performs a readiness/seed/bootstrap-dependent repository or external read must depend on that readiness edge before the read; a shared downstream root does not order sibling prerequisites.
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

After one focused command fails or aborts, triage every failure and diagnostic from that complete run. Fix one coherent shared root cause across the reported files before rerunning; do not expand beyond that command's dependency frontier. Report:

* suite/test name;
* exact error;
* whether it reproduces alone, when requested or safe;
* whether it appears patch-related;
* smallest safe next focused diagnostic command.

Missing external resources/environment means blocked or canceled verification, not product failure. Then fix only that failure with the smallest safe change.
