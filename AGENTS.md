# AGENTS.md

## Operating rules

* Keep patches atomic: one purpose, no unrelated refactors or mixed risk layers. If broader changes are required, stop and report the smallest safe next step.
* Inspect nearby repo code before using framework APIs from memory.
* Existing focused tests and route-level HTTP contract tests are the primary source of truth for intended behavior. If tests, docs, and implementation conflict, flag the mismatch instead of guessing.

## Agent discipline

* Do not perform broad architecture/audit/design unless explicitly asked.
* Make bounded edits and focused checks only.
* If the requested change needs wider scope, stop and report the smallest safe next step.
* Reports should be short: focused result, deviations/compile fixes, blocked verification.
* Update project docs immediately for production exposure, runtime behavior, architecture policy, or roadmap status changes.
* Docs should record current state briefly and without fluff.
* Use task-named docs and directly touched source/spec/resource files. Do not read broad plugin/route/DI/http files unless a failure, explicit conflict, or the task scope requires it.
* Do not create, read, or write explicit absolute scratch/device paths such as `/tmp`, `/var/tmp`, `/private/tmp`, `/dev`, or `/dev/null` for repo work, validation, or artifacts. Keep temporary files, generated artifacts, and one-off helper outputs inside the repo working tree unless the user explicitly provides another path.

## Ownership and abstraction

* Put behavior where its source of truth lives; do not hard-code one app/domain into reusable layers.
* For framework, DSL, runtime, interpreter, or adapter work, name the generic boundary and the app-specific boundary before editing.
* Keep app-specific names, defaults, policies, and compatibility shims at the edge unless the task explicitly changes the generic contract.
* Prove reusable code with neutral fixtures or contract tests; app examples alone do not prove a generic boundary.
* If preserving behavior requires domain names in a reusable layer, stop and report the boundary conflict.
* Prefer small explicit adapters over broad "generic" code that secretly knows one domain.

## Verification

Labels:

* `FOCUSED GREEN`: requested focused suite passed; full repo unknown.
* `USER-VERIFIED FULL GREEN`: the user ran the exact full verification command and reported green.
* `VERIFICATION BLOCKED`: local permissions, resources, sbt, docker, environment, or agent policy blocked verification.

Default rule: source changes need focused checks. Run only requested focused checks.

Delegated agents must not run full-suite verification. Do not run broad project/repo test commands such as unscoped `test`, `Test/test`, or project-wide test tasks. If a patch requires full-suite confidence, report the focused result and say coordinator/user full verification is required.

Focused-only checks are never `FULL GREEN`.

Do not call a patch commit-ready from focused checks alone when the task touches plugin/module shape, production graph wiring, lifecycle/readiness, or other full-graph-sensitive code. Report: focused result only; coordinator/user full verification required.

## sbt rules

* Do not run sbt commands in parallel; run one chained sbt command.
* If a repo-local validation wrapper is provided, run it exactly unless it is full-suite verification. Otherwise run requested focused sbt tasks from the repo working directory, keep sbt tasks quoted, and use the sandbox-safe launcher options documented below.
* Scope compilation and tests to the owning sbt subproject. A root aggregate `Test/compile` is not a focused prerequisite for one spec and may initialize unrelated application graphs or socket-using compile-time checks.
* If the requested command is full-suite verification, do not run it as a delegated agent. Report `VERIFICATION BLOCKED` by agent policy and ask coordinator/user to run it.
* Do not run malformed or diagnostic variants such as `sbt Test/compile ...`, `sbt about`, `sbt ... | tail`, `sbt ... | head`, `sbt ... | tee`, or any command that rewrites, wraps, filters, or decomposes the requested validation command.
* Do not run setup probes (`type/which sbt`, `java -version`, `echo $JAVA_HOME`, `echo $SBT_OPTS`, `ls/cat .sbtopts .jvmopts`), inspect sbt wrapper/launcher lines, or read resolved tool paths outside the repo unless the exact command fails with a missing-command/setup error.
* Sandboxed agents must not request write access to home-directory sbt or Ivy caches. Before their first sbt command, create the ignored repo-local directory `target/codex-sbt/ivy2`, then add `-Dsbt.server.forcestart=true -Dsbt.ivy.home=target/codex-sbt/ivy2` to the normal command. `-Dsbt.server=false` alone does not bypass the sbt launcher boot socket.
* If dependency resolution also fails because the shared Coursier cache is read-only, use the official sandboxed Coursier setup `COURSIER_CACHE=target/codex-sbt/coursier-cache` for the same command. Network approval may still be required to download an artifact that is not already cached; do not request home-directory write access instead.
* If sbt reaches the requested test or compile task and that project code itself fails to bind a Unix or TCP socket with `Operation not permitted`, the launcher/cache workaround has succeeded. Request sandbox escalation only when that exact focused task genuinely requires socket access; do not change source to evade the sandbox.
* If required socket or network escalation is unavailable, report `VERIFICATION BLOCKED` and the exact command.
* Do not edit source to work around sbt locks.
* If sbt fails with stale recursive target / `File name too long`, treat it as build-artifact cleanup: clean target directories, then rerun the same command. Report it as cleanup, not source change.

Preferred focused shape:

```bash
mkdir -p target/codex-sbt/ivy2
sbt --batch --no-global -Dsbt.server=false -Dsbt.server.forcestart=true -Dsbt.ivy.home=target/codex-sbt/ivy2 'projectName/testOnly package.SomeSpec'
```

## Cleanup / reset policy

* Do not run Docker cleanup, database reset, broad target deletion, or cold reset as a first response to unexplained failures.
* Use the smallest relevant reset only when the failure indicates stale generated state, stale build artifacts, stale containers, or stale database schema/data state, especially after schema or migration-related changes.
* Report the reset as environment/state cleanup, not as a source fix.
* Rerun the same validation command after cleanup.
* Do not use cleanup to hide a reproducible source/test failure.

## DI, lifecycle, and graph rules

* DI startup follows dependency edges, not binding order.
* Module order and memoization-root order are not sequencing guarantees.
* Graph garbage collection may remove bindings without concrete roots. Inspect roots, axes/activation, and suite inheritance before changing production modules.
* Disabled experiment activation must not construct heavy dependencies. Use explicit axis/config, by-name/factory/resource boundaries, or separate modules.
* Do not include a whole production plugin/application graph inside focused/unit spec modules unless the task explicitly asks for production graph coverage.
* Use targeted modules, existing app/role/testkit fixtures, or coordinator/user full-suite validation for production graph coverage.

### Whole-plugin include hazard

* Do not use ad-hoc test-local `include(LeaderboardPlugin.modules.api[IO])` inside focused/unit spec `ModuleDef`s.
* `LeaderboardPlugin.modules.api[IO]` is a concrete example of the broader hazard: whole-plugin includes inside focused specs.
* The reproduced hazard is ad-hoc whole-plugin include in focused specs, which can trigger `IncludesDSL$Include.interpret` NPE in Distage.
* When a full-suite/coordinator run aborts with `IncludesDSL$Include.interpret` or `Include.bindings() is null`, do not debug the first aborted suite as root cause. First search for ad-hoc whole-plugin includes in tests, including `LeaderboardPlugin.modules.api`, `apiBase`, and similar broad plugin module includes.
* Replace test-local whole-plugin includes with targeted modules, existing role/testkit fixtures, or explicit minimal bindings. If no broad include is found, stop and request a bundle with the matched stack trace, module snippets, and grep output.
* This abort is a graph-construction hazard, not product behavior evidence.
* Changes touching `LeaderboardPlugin.modules.api` or whole-plugin include tests require coordinator/user full project verification. Delegated agents must not run full-suite verification and must not call such patches commit-ready from focused checks alone.

### Intentional dependency edges

* `@unused` is for intentional dependency/lifecycle/readiness edges: roles, readiness, schema/table creation order, and constructor dependencies that force graph construction.
* Do not use `@unused`, `val _ = x`, or `@nowarn` to keep future placeholder params in pure functions.
* If a param/import/local is unused and not an intentional edge, remove it or make it part of real behavior.
* Prompt snippets and suggested signatures are subordinate to behavior. If a suggested param is unused, either use it in the real contract or remove it and report the deviation.
* Do not investigate scalac warning flags for unused symbols unless the failure remains unexplained after removing unused code.

### Readiness and weak-set proof

* If a test reads data that depends on readiness/seed/bootstrap completion, it must depend directly on the readiness edge before repository reads.
* Do not fix missing readiness-edge failures by changing unrelated search, storage, or external-client code first.
* Weak-set contributions may require concrete retention roots in tests.
* Do not fake weak-set proof with alias bindings that bypass the weak set.

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

For simple pure model tests, prefer `AnyWordSpec`, deterministic UUID fixtures when IDs are needed, direct `assert`, and no effects/DI/runtime.

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

* Put pure Tapir endpoint contracts in the repo's Tapir endpoint package, usually `leaderboard/http/tapir/*TapirEndpoints.scala`.
* Keep `leaderboard.api.*Api` classes as thin `HttpApi[F]` adapters.
* Reuse existing support helpers.
* Do not migrate many endpoints in one patch.
* Do not change malformed path/body/exception contracts unless explicitly asked.
* Route-level contract tests override planning docs or framework defaults.
* For HTTP client algebras, model method/body semantics explicitly. Do not fake bodyless endpoints by sending empty JSON objects. If an endpoint is specified or observed as bodyless, add/use a bodyless client method and cover it in both the real client spec and scripted-client tests.

## Data/model invariants

* Do not touch stable domain codecs, storage schemas, validation, or JSON shape unless explicitly asked.
* Preserve unified storage paths and stable enum/string codes unless the task explicitly changes them.
* Treat existing route contracts and persisted JSON contracts as compatibility boundaries.

## Eval-style failure reports

For eval-style failures, report:

* case id/name;
* input;
* parsed/decoded state if available;
* observed output;
* expected output;
* failed assertion.

## Failure protocol

If one focused query/test fails or aborts, stop expanding the slice.

Report:

* suite/test name;
* exact error;
* whether it reproduces alone, if focused reproduction was requested or safe;
* whether it appears related to the patch;
* smallest safe next focused diagnostic command.

If external resources or environment are missing, report blocked/canceled verification, not product behavior failure.

Then fix only that failure with the smallest safe change.
