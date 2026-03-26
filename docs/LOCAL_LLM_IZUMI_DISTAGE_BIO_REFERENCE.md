AI-ENTRYPOINT
LOCAL LLM REFERENCE
OFFLINE DISTAGE / IZUMI / BIO DOCS
Read this file before editing code with Applicative2 / Monad2 / Error2 / IO2 / Primitives2 / Async2 / Fork2 / Temporal2 / Panic2 / Lifecycle

# LOCAL LLM REFERENCE: izumi / distage / BIO

This file is for offline/local LLMs, Codex, and other AI assistants working in this repository when external docs may be unavailable.

Goal: give a compact, practical reference for reading and generating code in izumi/distage/BIO style **without accidentally changing runtime semantics**, especially during migrations from ZIO-specific code to BIO2-style abstractions.

This file is intentionally scoped to:
- BIO bifunctor effect typeclasses
- distage lifecycle / resource / DI basics
- common reasoning rules for effect-polymorphic code
- how BIO code interoperates with cats-effect style APIs
- **migration guardrails for preserving tracing context, logging context, header propagation, async semantics, fallback semantics, and observability contracts**

This file intentionally does **not** try to fully document:
- doobie / PostgreSQL
- http4s / circe as full libraries
- distage-testkit strategy
- broad project architecture
- every project-specific runtime contract outside the migration/observability concerns documented here

Those should live in separate docs.

---

## Read This First

If you see any of these in a signature, read this file before editing:

- `F[+_, +_]`
- `Applicative2`, `Monad2`, `Error2`, `Panic2`, `IO2`, `Async2`, `Fork2`, `Temporal2`, `Primitives2`
- `Lifecycle`, `Lifecycle.LiftF`, `Lifecycle.Of`, `Lifecycle.OfCats`
- `ModuleDef`, `PluginDef`, `make[..]`, `fromResource[..]`
- cats-effect style requirements coming from external libraries:
  - `Concurrent[F]`
  - `Spawn[F]`
  - `Temporal[F]`
  - `Async[F]`
- ZIO-specific code that currently carries request-local logging context, tracing context, or fiber-local state

**Critical migration rule:**

If a refactor moves code from ZIO-specific APIs to BIO2-style abstractions, you must assume the refactor is **behaviorally dangerous** until tests prove otherwise.

Do **not** assume any of the following are preserved automatically:
- trace/span parent-child structure
- Datadog header propagation/rewrite
- logger custom context / `trace_id`
- fiber-local context inheritance
- async child task lifetime semantics
- retry / fallback / replay ordering
- success-path observability
- error-path observability

---

## The Most Important Rule In This Repository

When migrating from ZIO-specific code to BIO2-style code:

### Never treat “it compiles” or “tests are green” as enough unless the right tests already existed.

Before refactoring suspicious code:
1. identify the observable runtime contract
2. add tests that lock it down
3. refactor only after those tests are green
4. keep those tests green after the refactor

If tests do not exist yet, **write them first**.

This is especially important for:
- tracing context
- logging context
- outgoing tracing headers
- span tree shape
- async publish/replay behavior
- circuit breaker / fallback behavior
- replay ordering
- Kafka producer contract

If you skip this step, you can easily ship a “nice generic refactor” that silently breaks tracing or logging context propagation.

---

## What Counts As A Fatal Refactor Here

A refactor is considered **fatal** if it silently changes the behavior of any of these contracts without explicit intent and dedicated tests:

- request-local `trace_id` disappears from logs or leaks between requests
- child spans disappear or move under a different parent
- outgoing `x-datadog-*` headers stop matching the current contract
- background publish/replay lifetime changes
- `publish` is no longer awaited in the same way
- fallback/retry ordering changes
- untraced requests inherit stale context from a previous traced request
- malformed trace headers now crash or silently mutate behavior differently
- success-path observability changes even though error-path tests still pass

Examples of historically dangerous changes:
- replacing `flatten`/equivalent ack-waiting semantics with `.void`
- replacing `forkDaemon`-like behavior with plain `fork`
- removing an explicit tracing wrapper or span around a sub-operation
- switching from a ZIO logger context update to a more generic abstraction without proving context propagation still holds
- genericizing a service without any meaningful non-IO implementation, thereby increasing surface area and risk without practical value

---

## Short Glossary

- `F[E, A]`: bifunctor effect. `E` is typed error, `A` is success value.
- `F[Nothing, A]`: cannot fail in the typed error channel.
- `F[Throwable, A]`: may fail with `Throwable`.
- `Applicative2`: pure construction and independent effect combination.
- `Monad2`: dependent sequencing (`flatMap`).
- `Error2`: typed failure and recovery.
- `Panic2`: stronger infrastructure/defect/bracket-level capability.
- `IO2`: synchronous effect suspension and conversions from sync data.
- `Async2`: callback/future async interop.
- `Fork2`: concurrent execution of existing effects.
- `Temporal2`: time-based control like sleep/timeout.
- `Primitives2`: refs, promises, semaphores, latches.
- `Concurrent2`: BIO-side capability corresponding to concurrent cats-effect style behavior.
- `Lifecycle[F, A]`: resource acquisition/release description.
- `Lifecycle.LiftF`: effectful constructor with no custom finalizer.
- **logging context**: request-local or fiber-local metadata attached to logs, for example `trace_id`.
- **tracing context**: current span / trace metadata used to create child spans and outgoing headers.
- **observability contract**: the concrete externally visible behavior of spans, tags, logs, headers, correlation IDs, and ordering.

---

## BIO Hierarchy You Actually Need

Useful mental model:

- `Applicative2 <- Monad2 <- Error2 <- Panic2 <- IO2 <- Async2`
- `Error2 <- Temporal2`
- `Fork2`, `Primitives2`, and `Concurrent2` are separate capabilities that often appear at integration boundaries

Implications:

- If you have `Async2`, you also have `IO2`, `Panic2`, `Error2`, `Monad2`, `Applicative2`.
- If you only have `Applicative2`, do not write `flatMap`.
- If you only have `Error2`, you already have monadic sequencing plus typed failure handling.
- When external APIs ask for cats-effect concurrency typeclasses, extra BIO capabilities like `Fork2`, `Primitives2`, and `Concurrent2` may appear even if your business logic itself looks simple.

---

## Cheat Sheet

### By Typeclass

| Typeclass | Use when | Typical ops | Do not use it for |
|---|---|---|---|
| `Applicative2` | Pure construction and independent effects | `pure`, `map`, `map2`, `unit` | Dependent sequencing |
| `Monad2` | Sequential dependent logic | `flatMap`, `flatten`, `tailRecM` | Error recovery by itself |
| `Error2` | Typed domain failure/recovery | `fail`, `catchAll`, `redeem`, `attempt` | Suspending impure code |
| `Panic2` | Infrastructure-safe integration / bracket / defects | `sandbox`, `terminate`, bracket-derived ops | Ordinary domain validation |
| `IO2` | Sync side effects or sync conversions | `sync`, `syncThrowable`, `fromEither` | Callback/Future bridging |
| `Async2` | Callback/Future interop | `async`, `asyncF`, `fromFuture` | Spawning fibers by itself |
| `Fork2` | Run an existing effect concurrently | `fork`, `forkOn` | Wrapping callback APIs |
| `Temporal2` | Sleep/timeout/retry timing | `sleep`, `timeout` | General async interop |
| `Primitives2` | Local coordination/state | `mkRef`, `mkPromise`, `mkSemaphore`, `mkLatch` | External async integration |
| `Concurrent2` | BIO-side concurrent semantics used for cats-effect bridges | bridge support, concurrency integration | Pure domain logic by default |

### By Operation

| Operation | Usually means you need |
|---|---|
| `F.pure(value)` | `Applicative2` |
| `fa.map(f)` | `Applicative2` |
| `fa.flatMap(f)` / for-comprehension | `Monad2` |
| `F.fail(err)` / `catchAll` | `Error2` |
| `F.fromEither`, `F.fromOption`, `F.sync` | usually `IO2` |
| `F.mkRef`, `ref.get`, `ref.modify` | `Primitives2` |
| `F.async`, `F.fromFuture` | `Async2` |
| `F.fork(effect)` | `Fork2` |
| `F.sleep`, `F.timeout` | `Temporal2` |
| `Lifecycle.LiftF(...)` | effectful constructor with no custom finalizer |
| `Lifecycle.OfCats(resource)` / `fromCats` | adapt cats-effect `Resource` |
| external API requiring cats `Concurrent[F]` | usually BIO bridge needs `IO2` + `Concurrent2` + `Fork2` + `Primitives2` |
| external API requiring cats `Spawn[F]` | usually BIO bridge needs `IO2` + `Concurrent2` + `Fork2` |
| external API requiring cats `Temporal[F]` or `Async[F]` | expect even stronger integration bounds than plain domain logic |

---

## How To Read Signatures

### `F[+_, +_]`

Read `F[E, A]` as:

- may fail with `E`
- may succeed with `A`

Examples:

- `F[QueryFailure, Unit]`: domain-level repo operation
- `F[Throwable, HttpServer]`: infrastructure/resource code
- `F[Nothing, A]`: local deterministic path without typed failures

### Context Bounds

Use the weakest typeclass that supports the implementation:

- `Applicative2` if only `pure`, `map`, `map2`
- `Monad2` if you need `flatMap` / for-comprehension
- `Error2` if you need `fail`, `catchAll`, `redeem`
- `IO2` if you need sync suspension or sync conversions
- `Async2` if you need callback/future interop
- `Fork2` if you need to start fibers
- `Temporal2` if time is involved
- `Primitives2` if you need refs/promises/semaphores
- `Concurrent2` if you are explicitly building or satisfying concurrent integration layers

Rule: do not strengthen a signature unless the code actually needs it.

Extra rule for library integration:
- if the code calls into http4s / circe / doobie / cats-effect style APIs, inspect what **cats-effect typeclass** that API needs before deciding your BIO bounds

**Migration rule:** do not genericize a service merely because some of its methods could compile under `Monad2` or `IO2`.

A generic service is justified only if at least one of these is true:
- there is a real, meaningful non-IO implementation or consumer
- it removes duplication without obscuring runtime behavior
- it improves testability without weakening observability guarantees
- it preserves current contracts while clearly reducing coupling

If none of these are true, leaving the service on `IO` is usually safer.

This is especially important for services that carry or depend on:
- logging context
- tracing context
- fiber-local state
- background task lifetime semantics
- retry/fallback behavior

---

## Key Differences

### `Applicative2` vs `Monad2`

- `Applicative2`: effects are independent.
- `Monad2`: later effect depends on earlier result.

### `Error2` vs `IO2`

- `Error2`: typed domain failures.
- `IO2`: sync effect suspension / sync conversions / exception capture.

### `Primitives2` vs `Async2`

- `Primitives2`: local coordination primitives.
- `Async2`: external async integration.

### `Async2` vs `Fork2`

- `Async2`: wrap callbacks/futures.
- `Fork2`: run existing `F[E, A]` concurrently.

### `Temporal2` vs `Async2`

- `Temporal2`: time-based control.
- `Async2`: async interop / runtime async layer.

### `Panic2` vs `Error2`

- `Error2`: recoverable typed failures.
- `Panic2`: stronger infrastructure semantics used around unsafe or integrated boundaries.

### `Concurrent2` vs `Fork2` / `Primitives2`

- `Concurrent2` is not “the same thing as forking”.
- `Fork2` is about starting concurrent execution of an effect.
- `Primitives2` is about local concurrent coordination.
- `Concurrent2` matters mainly when satisfying higher-level concurrency abstractions required by external libraries.

---

## Core Operations In Practice

### `pure`, `map`, `flatMap`, `fail`, `fromEither`

```scala
F.pure(value)
fa.map(f)
fa.flatMap(a => fb(a))
F.fail(err)
F.fromEither(either)
```

Heuristic:

* use `map` for pure transformation
* use `flatMap` when the next step is effectful
* use `fail` for intentional domain failure
* use `fromEither` when validation is already computed

### `mkRef` and `ref.modify`

```scala
for {
  ref <- F.mkRef(Map.empty[K, V])
  _   <- ref.update_(_ + (k -> v))
  out <- ref.get
} yield out
```

`ref.modify` is for atomic **pure** updates:

```scala
ref.modify { current =>
  val next = current + (k -> v)
  result -> next
}
```

Do not put effect execution inside `modify`.

Bad inside `modify`:

* logging
* SQL/repo calls
* decoding
* creating nested effects

### `async` and `fork`

```scala
F.async { cb =>
  registerCallback(result => cb(result))
}

F.fork(effect)
```

* `async`: wrap external async API
* `fork`: concurrently run existing effect

**Migration warning:**

Do not replace a ZIO-specific background execution pattern with generic `fork` unless you have explicitly checked the contract for:

* child lifetime
* cancellation scope
* inheritance of fiber-local context
* inheritance of tracing context
* inheritance of logger context

A plain-looking `fork` replacement can silently change runtime behavior.

---

## Pure / Atomic Paths vs Effectful Paths

Inside pure or atomic paths, keep logic effect-free:

* collection transforms
* sorting
* case class construction
* `Ref.modify` state transitions

Use effectful code when you need:

* IO
* SQL
* HTTP
* logging
* decoding
* callback/future integration
* resource allocation

Good pattern:

```scala
state
  .modify[Either[QueryFailure, Unit]] { current =>
    if (ok(current)) Right(()) -> nextState(current)
    else Left(err)  -> current
  }
  .flatMap(F.fromEither)
```

Atomic decision stays pure. Effect conversion happens after.

---

## `Ref1` / `Ref2` vs `RefM2`

Use plain ref + `modify` when:

* update depends only on current state
* decision is pure
* you only need atomic local update

Consider effectful update patterns only when:

* the update itself must run effects
* you cannot separate pure decision from effect execution
* pure `modify` would force illegal effectful logic inside the closure

---

## Distage Basics Needed For This Codebase

### `Lifecycle`

`Lifecycle[F, A]` describes resource acquisition and release.

Practical rule:

* if a constructor returns `Lifecycle`, it is a resource constructor

### `Lifecycle.LiftF`

Use when:

* constructor is effectful
* no custom finalizer is needed

Typical pattern:

```scala
final class Dummy[F[+_, +_]: Error2: Primitives2]
  extends Lifecycle.LiftF[F[QueryFailure, _], Service[F]](
    for {
      state <- F.mkRef(initial)
    } yield new Service[F] { ... }
  )
```

### `Lifecycle.Of`, `Lifecycle.OfCats`, `Lifecycle.fromCats`

Use these when adapting existing lifecycle/resource constructors, especially cats-effect `Resource`.

### Resource Allocation Pattern

Typical pattern:

1. log startup
2. allocate refs/resources
3. run setup if needed
4. return service

Heavy setup belongs in resource construction, not hot runtime paths.

### DI Wiring Basics

In distage plugin code:

* `make[X]` binds a component
* `make[X].from[Y]` binds via implementation
* `make[X].fromResource[Y]` binds via resource constructor
* `many[...]` aggregates implementations
* activation axes select implementations without changing consumers

**Migration rule for DI changes:**

If a runtime service is still only used as `IO`, avoid widening DI type surface to generic `F` unless that buys something concrete.

A larger generic DI surface:

* increases the space for inference problems
* increases maintenance cost
* can hide runtime-relevant behavior behind more abstract signatures
* can make observability regressions easier to miss

---

## Why External Cats-Effect Requirements May Pull Stronger BIO Bounds

This is the most common confusion point for local LLMs.

At the call site, code often looks simple:

```scala
rq.decodeJson[A]
req.as[A]
client.expect[A](...)
someExternalApiNeedingConcurrent(...)
```

It is tempting to ask:

> “Why does this operation need Async2 / Fork2 / Primitives2?”

Usually that is the wrong question.

The right question is:

> “What cats-effect typeclass does this external API require, and what BIO bridge is needed to provide it?”

### General mental model

Many external Scala libraries are written against cats-effect typeclasses:

* `Concurrent[F]`
* `Spawn[F]`
* `Temporal[F]`
* `Async[F]`

This codebase often uses izumi BIO `F[+_, +_]` instead of plain cats-effect `IO`.

So the compiler inserts a bridge:

```text
your BIO F[+_, +_]
-> izumi CatsConversions
-> cats-effect instance for F[Throwable, *]
-> external library API compiles
```

### The key bridge rules

#### If external API wants cats `Concurrent[F]`

Think:

```text
external API wants Concurrent[F]
-> izumi BIOToConcurrent bridge
-> needs BIO-side concurrency support
```

Typical BIO capabilities behind that bridge:

* `IO2`
* `Concurrent2`
* `Fork2`
* `Primitives2`

This is why a seemingly simple external call can pull in more than just “basic effects”.

#### If external API wants cats `Spawn[F]`

Think:

```text
external API wants Spawn[F]
-> bridge needs ability to run effects concurrently
```

Typical BIO capabilities behind that bridge:

* `IO2`
* `Concurrent2`
* `Fork2`

#### If external API wants cats `Temporal[F]`

Think:

```text
external API wants Temporal[F]
-> bridge needs timing + concurrency + runtime support
```

Expect stronger integration bounds than plain domain logic.

#### If external API wants cats `Async[F]`

Think:

```text
external API wants Async[F]
-> bridge needs async/runtime integration semantics
```

This is stronger still and often shows up in HTTP/server/client/resource integration layers.

### Why `decodeJson` is a common example

`decodeJson` looks like “just parse JSON”.

But in an http4s/circe stack, JSON decoding is not only about `Decoder[A]`.
It is typically part of an `EntityDecoder`, and that decoder stack is built on cats-effect style capabilities.

So the real chain is closer to:

```text
decodeJson
-> needs EntityDecoder / request body decoding
-> decoding stack requires cats-effect capability
-> project uses BIO, not plain IO
-> compiler inserts BIO -> cats bridge
-> bridge pulls in stronger BIO bounds
```

### Why IDE may show `Async2`, `Fork2`, and `Primitives2` together

Two important points:

1. The external API may only require something like cats `Concurrent[F]`.
2. But the local effect implementation and bridge resolution may surface stronger BIO evidence in IDE tooltips.

So if IDE shows:

* `Async2`
* `Fork2`
* `Primitives2`

do **not** conclude that the business operation itself inherently needs all three.

Often it means:

* the external library needs a cats-effect concurrency capability
* the project satisfies that through a BIO bridge
* the currently available effect instance in scope is stronger than the bare minimum
* `Fork2` and `Primitives2` are still required to complete the bridge

### Practical rule for local LLMs

When a library call unexpectedly demands stronger BIO bounds:

1. identify the cats-effect typeclass that external API wants
2. identify the BIO bridge needed for it
3. only then decide whether the current signature should stay strong or can be weakened

Do not reason from the surface syntax of the call.

### Typical examples of APIs that may do this

Not only `decodeJson`, but any call whose implementation depends on cats-effect concurrency abstractions, for example:

* request/response body decoding
* some entity decoders
* some client helpers
* some server/resource builder code
* some stream/concurrency integrations

### Safe heuristic

If an external API is from http4s / circe / cats-effect based integration code and the bounds look “too strong”, assume:

* the extra strength is probably coming from the cats bridge
* inspect the required cats-effect typeclass first
* then inspect the BIO bridge
* do not weaken the signature until you understand that chain

---

## How To Debug Surprising Typeclass Requirements

If a method seems to require “too many” typeclasses:

1. check whether the method itself really needs them
2. check whether they come from an adapter / interop layer
3. look for:

    * http4s syntax
    * circe syntax
    * cats conversions
    * distage resource adapters
4. ask:

    * is this a direct business-logic requirement?
    * or is it coming from `BIO -> cats-effect` conversion?

Typical anti-mistake:

* wrong: “JSON parsing needs Fork2”
* better: “this external decoding path needs a cats concurrency bridge, and that bridge needs Fork2”

---

## How To Read Code Before Editing

When reading a file:

1. identify the effect shape: `F[E, A]`, `F[Throwable, A]`, or `F[Nothing, A]`
2. read the weakest required typeclass from context bounds
3. separate pure logic from effectful logic
4. check whether the class is a plain service or a resource constructor
5. if external library calls are present, inspect whether they pull cats-effect typeclasses
6. if distage wiring is involved, inspect the plugin before changing signatures
7. if the file participates in tracing, logging, header propagation, or async publishing, identify the existing observability contract before changing anything
8. read the tests that already lock down behavior

Fast reading rule:

* repo/service files tell you domain and effect boundaries
* plugin/wiring files tell you construction and selection
* integration files often explain why bounds are stronger than the domain itself
* tests tell you what is actually enforced

---

## How To Choose The Needed Typeclass

Start from implementation, not habit:

1. Only `map` / pure values? `Applicative2`
2. Need `flatMap`? `Monad2`
3. Need typed domain failures? `Error2`
4. Need sync conversions / sync suspension? `IO2`
5. Need callback/future interop? `Async2`
6. Need background fiber? `Fork2`
7. Need refs/promises? `Primitives2`
8. Need sleep/timeout? `Temporal2`
9. Need defect/bracket-heavy infra semantics? `Panic2`

If external API wants cats-effect typeclasses:
10. determine the cats-effect requirement first
11. then determine the BIO bridge needed to satisfy it
12. keep the weakest honest bound that still supports that bridge

Prefer the smallest honest bound.

**But do not forget:** “smallest honest bound” is not the only objective.

You must also preserve:

* current runtime semantics
* current observability semantics
* current background task semantics
* current context propagation behavior

A weaker signature that changes behavior is not an improvement.

---

## How To Implement New Code In This Style

1. find the closest analogue first
2. match its typeclass bounds unless your implementation truly needs more
3. keep pure logic pure
4. keep effect boundaries explicit
5. use resource constructors for stateful/infrastructure allocators
6. do not hide effectful work inside atomic closures
7. preserve existing error-channel style
8. when calling external cats-effect based APIs, reason through the bridge instead of guessing from the surface syntax
9. if the code participates in observability, preserve the current contract first and refactor second

---

## Migration Guardrails: ZIO-Specific Code To BIO2-Style Code

This is the most important section for AI assistants touching tracing/logging-sensitive code.

### Default stance

Treat these migrations as **semantics migrations**, not syntax migrations.

A migration from ZIO-specific code to BIO2-style code is safe only after all relevant observability and async contracts are proven by tests.

### Suspicious places that require extra caution

If code touches any of these, assume it is dangerous:

* `updateService` / service-local logger enrichment
* `FiberRef` / fiber-local state / request-local context
* tracing wrappers or explicit child spans
* Datadog header generation or rewrite
* outgoing HTTP client wrappers
* retry / sleep / timeout / backoff logic
* `fork`, `forkDaemon`, background workers
* queue replay / local fallback / circuit breaker logic
* publish/send/ack semantics for Kafka or external clients
* constructors that capture logger/tracer instances at allocation time

### Red flags during migration

If you see changes like these, stop and verify behavior explicitly:

* `.flatten` -> `.void`
* explicit span wrapper removed
* `forkDaemon`-like pattern replaced by generic `fork`
* ZIO logger enrichment removed or moved away from request boundary
* logger/tracer implicitly captured in constructor instead of fetched in request path
* generic service abstraction added without meaningful non-IO implementation
* success-path tracing no longer explicitly tested
* only error-path tests remain green after a refactor

### Do not assume genericization is automatically good

Turning this:

```scala
trait Service {
  def op(...): IO[Throwable, A]
}
```

into this:

```scala
trait Service[F[+_, +_]] {
  def op(...): F[Throwable, A]
}
```

is **not** automatically an improvement.

It is justified only when it brings real value and preserves runtime behavior.

Do not genericize a service if:

* there is no meaningful non-IO implementation
* it obscures tracing/logging semantics
* it widens DI surface for no practical benefit
* it makes it easier to accidentally change background or observability behavior

---

## Required Test-First Workflow Before Suspicious Refactors

Before changing suspicious code, follow this workflow.

### Phase 1: inventory the current contract

List what the code currently guarantees, including:

* which logs contain `trace_id`
* which spans exist on success path
* which spans exist on error path
* which tags exist and on which spans
* what outgoing `x-datadog-*` headers look like
* what happens when trace headers are missing or malformed
* whether child/background work keeps the same context
* whether untraced requests remain clean after traced requests
* what happens on decode failure, retry, fallback, replay, and post-response work

### Phase 2: add tests before the refactor

Write tests that lock down the current behavior.

At minimum, cover all suspicious areas that the refactor touches.

### Phase 3: refactor in small slices

Prefer:

* one service at a time
* one integration seam at a time
* one DI/wiring change at a time

### Phase 4: rerun the contract tests after each meaningful step

Do not wait until the end.

### Phase 5: if a test fails, treat it as a semantic change

Do not “fix the test” unless the behavior change is intentional and reviewed.

---

## What Must Be Covered By Tests Before BIO2 Refactors

The exact suite names may differ by module, but the following contracts should be locked down before migrating suspicious code.

### 1. Route-level tracing on error path

Test that:

* request creates the expected server/root span
* required tags exist on that span
* throwable path is tagged correctly
* `trace_id` appears in error logs when header is present

### 2. Status-error observability

Test that 502/500/504-like paths preserve the same correlation contract:

* correct status returned
* correct `trace_id` in error log
* correct error tag/message on root/server span

### 3. Negative context cases

Test that:

* traced failing request does not contaminate the next untraced request
* malformed trace header has stable behavior
* malformed request does not contaminate the next request
* untraced request has no stale `trace_id`

### 4. Parallel isolation

Test that:

* concurrent requests with different `trace_id`s do not mix log context
* concurrent requests do not mix spans
* no cross-request leakage happens under overlap, not just sequential execution

### 5. Success-path tracing structure

Test that:

* success path creates the expected child spans
* parent-child relation is correct
* success path does not inherit error tags accidentally
* success-path structure stays stable across refactors

### 6. Outgoing Datadog/header contract

Test that:

* outgoing `x-datadog-*` headers are present or rewritten according to the current contract
* bogus incoming parent id does not silently flow through if current contract rewrites it
* header whitelist/forwarding behavior remains stable
* non-whitelisted headers do not accidentally start propagating

### 7. Publish-on-success-only contract

Test that:

* publish is triggered only on the statuses that currently allow it
* decode failure or status failure does not publish
* `200 OK + bad payload` does not silently publish

### 8. Background/publish lifetime semantics

Test that:

* post-response/background work still runs when it should
* cancellation/lifetime semantics do not silently change
* context inside background work remains correct if that is part of the current contract

### 9. Retry/fallback/circuit breaker semantics

Test that:

* retry counts and ordering remain stable enough to preserve the contract
* once breaker is open, fallback path is used as currently expected
* local fallback succeeds under the same current conditions
* tests avoid overfitting to irrelevant magic numbers unless the threshold is explicitly part of the setup

### 10. Replay ordering

Test that:

* republish happens before delete
* delete does not happen on failed replay
* replay path does not leak stale request-local trace context

### 11. Kafka/external producer contract

Test that:

* topic/key/value/header mapping stays stable
* version header or similar metadata keeps the current contract
* ack/send semantics are preserved where current behavior depends on them

### 12. Implementation parity when replacing runtime code

If a refactor replaces a concrete runtime implementation, add parity tests or a shared contract harness so that:

* old implementation and new implementation can be checked against the same assertions
* behavior differences are explicit, not accidental

---

## Test Design Guidance For These Refactors

When writing tests for suspicious migrations:

Prefer:

* `before/after` snapshots for logs and spans
* deterministic `Promise` / `Ref` / `TestClock` style coordination
* exact assertions for the new logs/spans produced by the action under test
* per-case assertions instead of broad set equality
* timeout guards for concurrent tests to avoid hangs

Avoid:

* relying on incidental log ordering when you can snapshot
* `Thread.sleep`
* broad set-based assertions that can pass despite missing correlation
* overfitting to unimportant response-body prefixes or formatting details
* weakening tests just to let a refactor pass

---

## Current-Contract Oracle Rule

When master already has contract tests for tracing/logging/publish behavior, treat those tests as the oracle.

This means:

* if a refactor breaks them, assume the refactor changed behavior
* do not weaken the tests unless the behavior change is intentional and explicitly approved
* do not port a change from another branch if it violates current master contracts without a deliberate decision

This is especially important when partially porting changes from a BIO branch back into master.

---

## How To Evaluate A Candidate Refactor Or Partial Port

For each changed piece, classify it as one of these:

### Safe to port

* clearly improves readability/structure/DI
* does not change observability or async semantics
* all current contract tests stay green

### Safe only with adaptation

* useful idea, but raw branch version changes behavior
* can be adapted to preserve current contract
* adaptation is smaller and safer than porting the branch version directly

### Do not port

* changes span tree or header contract
* changes logger/tracing context behavior
* changes background lifetime semantics
* changes retry/fallback/replay ordering
* genericizes services without practical value
* expands abstraction surface without meaningful benefit

---

## Specific Guidance On Logging Context And Tracing Context

### Logging context

Assume logging context is fragile across:

* async boundaries
* forks
* retries and sleeps
* constructor-time capture
* resource allocation boundaries
* request-to-background transitions

If code currently enriches a logger at request boundary, preserve that behavior unless tests prove a new approach is equivalent.

### Tracing context

Assume tracing context is fragile across:

* child span creation
* outgoing client wrappers
* background work
* stream boundaries
* partial abstraction over tracing APIs
* removal of explicit span wrappers

If code currently creates a named child span, removing that span is a semantic change until proven otherwise.

### Correlation contract

The important thing is not only that logs and spans still exist.
It is that they still correlate correctly.

Tests should prove, where relevant, that:

* the right `trace_id` lands in the right logs
* the right span tags land on the expected span
* outgoing headers belong to the same trace
* parallel requests do not cross-contaminate each other

---

## Common Pitfalls For AI Assistants

* adding stronger typeclasses than needed
* forgetting `Error2` already implies monadic sequencing
* using `flatMap` under only `Applicative2`
* putting effectful logic inside `Ref.modify`
* confusing `async` with `fork`
* replacing typed domain errors with thrown exceptions
* allocating state eagerly instead of through lifecycle/resource patterns
* editing signatures without checking how the same pattern is already written nearby
* assuming external library bounds come from business logic rather than from cats-effect bridges
* weakening a signature before understanding whether a cats `Concurrent[F]` / `Async[F]` bridge is being used
* genericizing services without meaningful non-IO value
* refactoring observability-sensitive code before adding tests
* trusting only error-path tests while success-path structure silently changes
* preserving logs and spans separately but not testing their correlation

---

## Practical Review Checklist Before Sending A PR

Before proposing a refactor from ZIO-specific code to BIO2-style code, verify all of these:

* I know what the current observability contract is.
* The suspicious places are covered by tests before my refactor.
* I did not change child span structure unintentionally.
* I did not change outgoing Datadog header behavior unintentionally.
* I did not change logger context propagation unintentionally.
* I did not change background task lifetime unintentionally.
* I did not change retry/fallback/replay ordering unintentionally.
* I did not genericize a service without real value.
* The new or existing contract tests are green.
* If behavior changed intentionally, that change is documented and explicitly reviewed.

If any answer is “no”, the refactor is not ready.

---

## What This File Intentionally Leaves To Other Docs

This file is **not** the main reference for:

* doobie / PostgreSQL / Hikari / SQL wrapper
* http4s routing / circe codecs / status mapping
* distage-testkit strategy
* Constructive Test Taxonomy / Dual Tests Tactic
* roles / launchers / docker infra
* distage app model / resource graph ordering / FK-backed repo startup order
* project-wide architecture and cross-module parallels outside the migration/observability focus

Those should be documented separately.

In particular:

* see `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md` for distage startup order, role wiring, and FK-driven Postgres repo dependencies

---

## Source Notes

This reference was assembled from:

* the actual code in this repository
* official izumi/distage/BIO documentation
* project code patterns visible in the workspace
* migration failures and regressions observed while moving ZIO-specific code toward BIO2-style abstractions

If izumi/distage versions change significantly, re-check APIs before relying on this file.
