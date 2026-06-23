# AGENTS.md

## Operating rules

* Keep patches atomic: one purpose, no unrelated refactors or mixed risk layers. If broader changes are required, stop and report the smallest safe next step.
* Inspect nearby repo code before using framework APIs from memory.
* Existing focused tests and route-level HTTP contract tests are the primary source of truth for intended behavior. If tests, docs, and implementation conflict, flag the mismatch instead of guessing.
* Keep project/domain policy out of this file. Put product-specific routes, roadmap status, domain invariants, backend ownership rules, and milestone notes in project docs or task-local handoff files.

## Agent discipline

* Do not perform broad architecture/audit/design unless explicitly asked.
* Make bounded edits and focused checks only.
* If the requested change needs wider scope, stop and report the smallest safe next step.
* Reports should be short: focused result, deviations/compile fixes, blocked verification.
* Update project docs immediately for production exposure, runtime behavior, architecture policy, or roadmap status changes.
* Docs should record current state briefly and without fluff.
* Use task-named docs and directly touched source/spec/resource files. Do not read broad plugin/route/DI/http files unless a failure, explicit conflict, or the task scope requires it.
* Do not create, read, or write explicit absolute scratch/device paths such as `/tmp`, `/var/tmp`, `/private/tmp`, `/dev`, or `/dev/null` for repo work, validation, or artifacts. Keep temporary files, generated artifacts, and one-off helper outputs inside the repo working tree unless the user explicitly provides another path.

## Verification

Labels:

* `FOCUSED GREEN`: requested focused suite passed; full repo unknown.
* `FULL GREEN`: full requested repo/project verification passed.
* `VERIFICATION BLOCKED`: local permissions, resources, sbt, docker, or environment blocked verification.

Default rule: source changes need focused checks. Run only requested focused checks unless the task explicitly asks for full-suite validation.

## sbt rules

* Do not run sbt commands in parallel; run one chained sbt command.
* If a repo-local validation wrapper is provided, run it exactly. Otherwise run exact requested sbt commands from the repo working directory and keep sbt tasks quoted, e.g. `sbt 'Test/compile' 'testOnly package.SomeSpec'`.
* Do not run malformed or diagnostic variants such as `sbt Test/compile ...`, `sbt about`, `sbt ... | tail`, `sbt ... | head`, `sbt ... | tee`, or any command that rewrites, wraps, filters, or decomposes the requested validation command.
* Do not run setup probes (`type/which sbt`, `java -version`, `echo $JAVA_HOME`, `echo $SBT_OPTS`, `ls/cat .sbtopts .jvmopts`), inspect sbt wrapper/launcher lines, or read resolved tool paths outside the repo unless the exact command fails with a missing-command/setup error.
* If sbt hits `~/.sbt/boot/sbt.boot.lock`, retry the same command once with local permission/escalation.
* If escalation is unavailable, report `VERIFICATION BLOCKED` and the exact command.
* Do not edit source to work around sbt locks.
* If sbt fails with stale recursive target / `File name too long`, treat it as build-artifact cleanup: clean target directories, then rerun the same command. Report it as cleanup, not source change.

Preferred focused shape:

```bash
sbt 'Test/compile' 'testOnly package.SomeSpec'
```

## DI, lifecycle, and graph rules

* DI startup follows dependency edges, not binding order.
* Module order and memoization-root order are not sequencing guarantees.
* Graph garbage collection may remove bindings without concrete roots. Inspect roots, axes/activation, and suite inheritance before changing production modules.
* Disabled experiment activation must not construct heavy dependencies. Use explicit axis/config, by-name/factory/resource boundaries, or separate modules.
* Do not include a whole production plugin/application graph inside focused/unit spec modules unless the task explicitly asks for production graph coverage.
* Use targeted modules, existing app/role/testkit fixtures, or full-suite validation for production graph coverage.

Intentional dependency edges:

* `@unused` is for intentional dependency/lifecycle/readiness edges: roles, readiness, schema/table creation order, and constructor dependencies that force graph construction.
* Do not use `@unused`, `val _ = x`, or `@nowarn` to keep future placeholder params in pure functions.
* If a param/import/local is unused and not an intentional edge, remove it or make it part of real behavior.
* Prompt snippets and suggested signatures are subordinate to behavior. If a suggested param is unused, either use it in the real contract or remove it and report the deviation.
* Do not investigate scalac warning flags for unused symbols unless the failure remains unexplained after removing unused code.

## Constructive test style

Use constructive taxonomy instead of old `unit / functional / integration` labels.

Axes:

* Intention: `Contractual`, `Regression`, `Progression`, `Benchmark`.
* Encapsulation: `Blackbox`, `Effectual`, `Whitebox`.
* Isolation: `Atomic`, `Group`, `Communication`.

Defaults:

* `Contractual + Blackbox + Atomic` for pure functions, parsers, codecs, policies, and single algebras.
* `Contractual + Blackbox + Group` for in-process service/module seams.
* `Communication` only for real external processes such as databases, search engines, queues, model servers, dockerized services, or HTTP services.
* `Whitebox` only when the internal detail is the explicit contract.

Resource-backed test DoD: use default local resources, run when resources are available, and cancel with a useful reason when unavailable. Boolean env gates are allowed only for true manual-artifact, destructive, or explicitly external workflows; such gates must be documented.

Prefer abstract contract suites over duplicated implementation-specific tests:

```scala
abstract class LadderTest extends BaseTest
final class LadderTestDummy extends LadderTest with DummyTest
final class LadderTestPostgres extends LadderTest with ProdTest
```

For simple pure model tests, prefer `AnyWordSpec`, deterministic fixtures, direct `assert`, and no effects/DI/runtime.

### Fixture style

Prefer:

* argument injection;
* environment/service accessors for service scenarios;
* targeted modules;
* immutable fixture case classes;
* deterministic fixture services;
* local typed effect runners.

Avoid:

* suite-level mutable state;
* global singletons;
* direct random/time/UUID generation unless uniqueness is the contract;
* raw runtime boilerplate spread through tests;
* focused specs that include the whole production graph.

### Test doubles and assertions

Do not introduce `var` call logs, mutable counters, `Recording*`, `Counting*`, `CallCounter`, `RecordingSpy`, or `called/calls` probes by default.

Prefer:

* `Expecting*`: validates expected inputs and fails on unexpected ones.
* `Scripted*`: returns configured results.
* `FailIfCalled*`: proves collaborator is unused.
* `Stub*`: returns fixed results without recording.

Do not mechanically replace `var` with `Ref`, `AtomicInteger`, `AtomicReference`, or mutable collections. `Ref` is acceptable for in-memory fixture/repository state, not as a recording spy.

Avoid exact call-count assertions unless call count is the explicit contract. Prefer assertions over returned responses, failures, and diagnostics.

Local `var` is acceptable only for exact by-name/exactly-once thunk contracts when a pure rewrite would weaken the test.

Do not use:

* `assert(true)`;
* empty success branches in pattern matches;
* partial matches in expecting fakes;
* unsafe extraction such as `Map.apply`, `.head`, `.tail`, `.last`, `Option.get`, `.toOption.get`, `RightProjection.get`, or `LeftProjection.get` unless totality is source-proven and documented;
* `isInstanceOf` / `asInstanceOf` when pattern matching is practical;
* `assert(x == null)` / `assert(x != null)`.

In production code, preserve typed errors with `Either`, effect constructors from typed errors, or explicit domain failures. In tests, use pattern matching or `getOrElse(fail(...))` so failures keep useful context. Decoder cursor `.get` is allowed.

Before final report after editing Scala specs, scan touched specs for unsafe extraction and fix it.

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
* Prompt-provided imports and helper snippets are candidate source-truth, not paste-all requirements. Use only the imports/helpers needed by the final code or tests.
* Before running validation or compilation, prune unused imports, params, locals, helper methods, and dead code, and qualify/import nested object members consistently.
* Report pruning prompt-provided unused symbols as a normal compile-safety step, not as a behavior deviation.
* In Scala 3 tests, avoid discarded-value fixes by making Unit-returning lambdas, callbacks, and match branches explicitly return `Unit`. Do not leave `assert(...)` as the final discarded value in a context typed as `Unit`; add a final `(): Unit` or otherwise make the branch return `Unit` before running validation.

## HTTP / Tapir rules

* Put pure Tapir endpoint contracts in the repo's Tapir endpoint package.
* Keep API classes as thin HTTP adapters.
* Reuse existing support helpers.
* Do not migrate many endpoints in one patch.
* Do not change malformed path/body/exception contracts unless explicitly asked.
* Route-level contract tests override planning docs or framework defaults.
* For HTTP client algebras, model method/body semantics explicitly. Do not fake bodyless endpoints by sending empty JSON objects. If an endpoint is specified or observed as bodyless, add/use a bodyless client method and cover it in both the real client spec and scripted-client tests.

## Data/model invariants

* Do not touch stable domain codecs, storage schemas, validation, or JSON shape unless explicitly asked.
* Preserve unified storage paths and stable enum/string codes unless the task explicitly changes them.
* Treat existing route contracts and persisted JSON contracts as compatibility boundaries.

## Failure protocol

If one query/test fails, stop expanding the slice.

Report:

* suite/test name;
* exact error;
* whether it reproduces alone;
* whether it appears related to the patch;
* smallest safe next diagnostic command.

Then fix only that failure with the smallest safe change.
