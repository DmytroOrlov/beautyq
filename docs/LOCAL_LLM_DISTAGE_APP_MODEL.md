AI-ENTRYPOINT
LOCAL LLM REFERENCE
OFFLINE DISTAGE APP MODEL DOCS
Read this file before editing distage PluginDef / ModuleDef / RoleAppMain / Lifecycle / fromResource / Postgres repo DDL / foreign keys / repo constructor dependencies

# LOCAL LLM REFERENCE: distage app model, resource graph, and schema-order rules

This file is for offline/local LLMs, Codex, and other AI assistants working in this repository when external distage docs are unavailable.

Use this file when the task touches:

- `PluginDef`, `ModuleDef`, `make[...]`, `fromResource[...]`
- `many[...]`, `.weak[...]`
- `RoleService`, `RoleAppMain`, launcher wiring
- `Lifecycle` resource constructors
- Postgres repos that create tables in their resource constructor
- `foreign key` constraints
- `@unused` repo constructor parameters that look unnecessary

This doc focuses on a project-specific rule that is easy for local LLMs to miss:

> distage resource startup order follows the dependency graph, not the textual order of bindings in `ModuleDef`.

If a Postgres repo creates a table with an FK to another table, the resource graph must contain a dependency edge to the repo that creates the parent table.

## The Critical Rule

For Postgres repos in this project:

1. DDL runs inside the repo resource constructor.
2. distage may allocate independent resources in parallel.
3. `make[A].fromResource[...]` order in `ModuleDef` is not a sequencing guarantee.
4. `memoizationRoots` order in tests is also not a sequencing guarantee.
5. Therefore, FK-backed tables must express their parent-table dependency through constructor dependencies.

Practical translation:

- If table `child` has `foreign key (...) references parent(...)`
- then `ChildRepo.Postgres` should depend on `ParentRepo[F]`
- even if the value is unused at runtime
- and that parameter should usually be marked `@unused`

This is not dead code.
It is a resource-order edge.

## Why This Matters

A fresh Postgres run can fail before test logic starts:

- child repo resource starts
- child DDL runs
- FK references a parent table that does not exist yet
- Postgres fails table creation

This is a schema-order race, not a domain-validation failure.

`memoizationRoots` and distage resource sharing make this more visible, because many repos may be initialized together and distage is free to parallelize independent roots.

## Current Table Dependency Graph In `bifunctor-tagless`

Independent tables:

- `ladder`
- `profiles`
- `categories`
- `masters`

FK-backed tables:

- `services -> categories`
- `master_locations -> masters`
- `master_service_offers -> masters`
- `master_service_offers -> services`
- `master_service_offer_variants -> master_service_offers`
- `master_service_offer_variants -> master_locations`

The transitive graph is:

```text
categories -> services -> master_service_offers -> master_service_offer_variants
masters    -> master_locations --------------------^
masters    -> master_service_offers ---------------^
```

As of the current code state, this graph is represented correctly in Postgres repo constructors.

That does **not** make the rule optional.
It means the rule has already paid for itself and must be preserved in future edits.

## Current Repo Resource Graph Pattern

The codebase already uses the correct distage pattern in several places:

### Correct: `MasterLocations.Postgres`

```scala
final class Postgres[F[+_, +_]: Error2](
  @unused masters: Masters[F],
  sql: SQL[F],
  log: LogIO2[F],
)
```

Why:

- `master_locations` has FK to `masters`
- `masters` parameter creates a resource-order edge

### Correct: `MasterServiceOffers.Postgres`

```scala
final class Postgres[F[+_, +_]: Error2](
  @unused masters: Masters[F],
  @unused services: Services[F],
  sql: SQL[F],
  log: LogIO2[F],
)
```

Why:

- `master_service_offers` has FKs to both `masters` and `services`
- both parents are represented as constructor dependencies

### Correct: `MasterServiceOfferVariants.Postgres`

```scala
final class Postgres[F[+_, +_]: Error2](
  @unused masterServiceOffers: MasterServiceOffers[F],
  @unused masterLocations: MasterLocations[F],
  sql: SQL[F],
  log: LogIO2[F],
)
```

Why:

- `master_service_offer_variants` has FKs to both `master_service_offers` and `master_locations`
- both immediate parents are represented as constructor dependencies

## Current Code State: The `Services.Postgres` Race Has Already Been Fixed

Current constructor shape:

```scala
final class Postgres[F[+_, +_]: Error2](
  @unused categories: Categories[F],
  sql: SQL[F],
  log: LogIO2[F],
)
```

Why this matters:

- `services` has FK to `categories`
- `@unused categories: Categories[F]` is the distage ordering edge
- local LLM must **not** remove it as cleanup

This doc exists partly because this exact class of bug was already observed in practice.
Treat this as a preserved fix pattern, not as optional style.

## Rule For New Postgres Repos

When adding or editing a repo with table DDL:

1. Inspect `create table` for all `foreign key ... references ...` clauses.
2. Map each referenced table to the repo that owns that table’s DDL.
3. Add those repos as constructor dependencies of the Postgres resource constructor.
4. Mark them `@unused` if they are only needed for ordering.
5. Keep DDL in the resource constructor, not in query methods.
6. Do not rely on textual binding order in `ModuleDef`.

## What Counts As The Parent Dependency

Use the repo that owns the parent table, not:

- a random service layer
- a helper object
- an unrelated model type
- `SQL[F]` alone

Example:

- child table references `categories(id)`
- parent resource edge should be `Categories[F]`

not:

- `Category`
- `CategoryId`
- another repo that happens to query categories

## Immediate Parents Are Usually Enough

You usually need edges only to immediate FK parents.

Example:

- `master_service_offer_variants` references `master_service_offers` and `master_locations`
- it does not need a direct constructor dependency on `masters` or `services`
- those are already covered transitively through parent repos

Use the table’s actual FK graph, not the domain graph in your head.

## Dummy vs Postgres: Different Reasons, Same Parents

In dummy repos, parent repos are often needed for domain validation:

- check that the referenced entity exists
- enforce same-master rules
- preserve behavior parity with Postgres

In Postgres repos, parent repos may be needed for two reasons:

- runtime/domain validation
- resource ordering for FK-backed DDL

Sometimes both reasons apply.
Sometimes only the second one applies.

Do not delete a repo constructor parameter just because the implementation body does not call methods on it.

## Three Different Meanings Of Constructor Dependencies In This Repo

Not every constructor dependency means the same thing.

### 1. Runtime-use dependency

Example:

- repo or API actually calls methods on the dependency

Typical sign:

- parameter is used in the method bodies

### 2. Resource-order dependency

Example:

- Postgres repo constructor depends on parent repo only so parent DDL runs first

Typical sign:

- parameter is marked `@unused`
- dependency corresponds to FK parent table

### 3. Retention / composition dependency

Example:

- role depends on API or child role so those components stay in the object graph
- server depends on `Set[HttpApi[F]]`

Typical signs:

- `@unused` in role constructors
- `many[HttpApi[F]].weak[...]` in plugin wiring

Do not collapse these meanings.

If a parameter looks unused, ask:

```text
Is this used for runtime behavior?
Or resource ordering?
Or role/API graph retention?
```

## Distage-Specific Mental Model

Read this:

```text
repo constructor parameters
-> distage dependency edges
-> resource graph
-> startup order constraints
-> DDL succeeds or races
```

Not this:

```text
ModuleDef binding order
-> startup order
```

That second model is wrong for this project.

## Role / API Graph Pattern In This Repo

There is another distage pattern local LLMs often miss:

- APIs are registered into `many[HttpApi[F]]`
- those references are added with `.weak[...]`
- `HttpServer.Impl` depends on `Set[HttpApi[F]]`
- individual roles depend on their API and the running server
- the composite role depends on child roles

Practical consequence:

- role constructor dependencies can exist only to retain subgraphs
- removing an `@unused` role or API parameter can break route availability even if compilation still succeeds

Read this as:

```text
role dependency
-> keep API/server subgraph alive
-> endpoint is mounted
```

This is a different pattern from FK-ordering, but it uses the same DI graph mechanism.

## Launcher / Plugin Discovery Pattern

In `bifunctor-tagless`:

- `MainBase` extends `RoleAppMain.LauncherBIO[IO]`
- default activation is `Scene.Provided`
- role-specific launchers add `Repo.Dummy`, `Repo.Prod`, `Scene.Managed`, or `Scene.Provided`
- under Graal Native Image the launcher uses `PluginConfig.const(...)`
- on normal JVM it uses `PluginConfig.cached(...)`

Implications for offline LLM:

- do not assume plugin discovery always happens dynamically
- do not assume scene/activation defaults from one launcher automatically apply everywhere
- if you add new plugin modules or roles, check both launcher wiring and plugin config behavior

## How This Connects To Tests

This repo prefers dummy and prod implementations over automatic mocks.

That fits Constructive Test Taxonomy well:

- dummy-backed tests are cheap and contractual
- prod-backed tests validate real schema/integration behavior
- dual tests expose mismatches between domain-only and DB-backed behavior

If resource ordering is wrong, prod tests fail before contract logic even runs.

So resource graph correctness is part of making:

- Contractual-Blackbox-Atomic tests cheap
- Contractual-Blackbox-Group tests reliable
- Dual Tests tactic useful instead of noisy

Also remember:

- `memoizationRoots` cause grouped resource startup and sharing
- this makes order bugs surface earlier and more consistently
- that is useful, not a nuisance

## Checklist Before Editing distage / Postgres Repo Code

Ask these questions in order:

1. Does this repo create a table?
2. Does that table reference any other table via FK?
3. Which repo owns each parent table’s DDL?
4. Does the Postgres constructor already depend on those parent repos?
5. If not, is there a schema-order race?
6. Are dummy and prod implementations still behaviorally aligned?
7. Will `memoizationRoots` or grouped startup expose the race in tests?

## Anti-Patterns

Avoid these mistakes:

- assuming `make[A]` before `make[B]` means `A` starts before `B`
- assuming `memoizationRoots = Set(A, B, C)` means `A` starts before `B`
- removing `@unused` parent repos as “cleanup”
- removing `@unused` API/role dependencies as “cleanup”
- relying only on query-time existence checks while FK DDL still races
- adding broad unrelated dependencies instead of immediate FK parents
- encoding schema-order assumptions only in comments, not in constructor deps
- changing role/plugin wiring without checking launcher behavior and `many[HttpApi].weak[...]`

## What To Update When Adding A New FK-Backed Repo

Use this checklist:

1. Add the model/repo code.
2. Add FK-aware constructor dependencies in the Postgres repo.
3. Keep or add matching parent dependencies in the dummy repo if domain validation needs them.
4. Register both dummy and prod resources in the plugin.
5. If the repo is part of grouped prod tests, ensure it is included in relevant `memoizationRoots`.
6. Add dual tests for dummy and prod behavior.
7. Re-check transitive FK graph after the change.

If you skip step 2, the code may compile and still fail nondeterministically on fresh Postgres startup.

## Fast Reference

If you see:

```sql
foreign key (...) references categories(id)
```

mentally add:

```scala
@unused categories: Categories[F]
```

If you see:

```sql
foreign key (...) references parent_table(id)
```

mentally ask:

```text
Which repo owns parent_table DDL?
Is that repo in the Postgres constructor?
```

If you see:

```scala
@unused something: Something[F]
```

mentally ask:

```text
Is this runtime-use?
Or FK/resource ordering?
Or role/API graph retention?
```

## Source Notes

This reference was assembled from:

- the actual repo/resource/DDL code in this workspace
- the current test setup and memoization roots
- the current launcher/plugin wiring in this workspace
- the project’s existing BIO/distage local reference
- Constructive Test Taxonomy guidance applied to this codebase
