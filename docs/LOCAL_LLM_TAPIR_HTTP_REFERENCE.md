AI-ENTRYPOINT
LOCAL LLM REFERENCE
OFFLINE TAPIR HTTP MIGRATION DOCS
Read this file before migrating additional `bifunctor-tagless` HTTP slices to Tapir

# LOCAL LLM REFERENCE: Tapir in `bifunctor-tagless`

This file documents the current Tapir migration pattern introduced for `MasterApi` and then reused for `ProfileApi` and `LadderApi`.

Goal:

- use Tapir inside the HTTP adapter layer
- keep `HttpApi[F]`, `HttpServer`, distage plugin style, and BIO/domain layers unchanged
- preserve existing runtime HTTP contracts during incremental migration
- create a small reusable pattern for the next slices

## Current Structure

Current migrated slices:

- `LadderApi`
- `MasterApi`
- `ProfileApi`

Current file split:

- `src/main/scala/leaderboard/http/tapir/MasterTapirEndpoints.scala`
  pure Tapir endpoint definitions
- `src/main/scala/leaderboard/http/tapir/ProfileTapirEndpoints.scala`
  pure Tapir endpoint definitions
- `src/main/scala/leaderboard/http/tapir/LadderTapirEndpoints.scala`
  pure Tapir endpoint definitions
- `src/main/scala/leaderboard/http/tapir/TapirHttpSupport.scala`
  small shared http4s/Tapir interpreter support with current-contract-preserving handlers
- `src/main/scala/leaderboard/api/LadderApi.scala`
  inbound HTTP adapter; assembles Tapir server logic and delegates route construction to Tapir
- `src/main/scala/leaderboard/api/MasterApi.scala`
  inbound HTTP adapter; assembles Tapir server logic and delegates route construction to Tapir
- `src/main/scala/leaderboard/api/ProfileApi.scala`
  inbound HTTP adapter; assembles Tapir server logic and delegates route construction to Tapir

This is intentional:

- Tapir contracts stay pure
- server-endpoint assembly stays inside the HTTP adapter when a separate class would only be a thin wrapper
- BIO/repo calls stay in the adapter layer
- `HttpServer` still just combines `Set[HttpApi[F]]`
- distage wiring still binds one `HttpApi[F]` implementation per slice

## Preferred Pattern

Use the following split for migrated slices:

- pure endpoint contracts in `*TapirEndpoints.scala`
- thin `HttpApi[F]` adapter in `leaderboard.api.*Api`
- central shared route interpreter policy in `TapirHttpSupport`

Preferred shape:

```scala
final class SliceApi[F[+_, +_]](
  dep1: Dep1[F],
  dep2: Dep2[F],
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit async: Async[F[Throwable, _]]) {
  private def all: List[ServerEndpoint[...]] = ...
  override def http = tapirHttpSupport.toRoutes(all)
}
```

Rationale:

- important HTTP assembly is visible in DI
- IDE navigation stays short: contract object plus one adapter class
- `*Api` stays obviously thin and transport-only
- endpoint contracts stay pure and reusable

## Contract Preservation Rules

For the current migration phase, Tapir defaults are not the source of truth.
Existing route-level contract tests are.

`TapirHttpSupport` therefore overrides default Tapir behavior:

- malformed path capture decode falls through as route mismatch
  result: server-level `404 Not found`
- malformed body decode returns `500` with empty body
- uncaught exceptions from server logic return `500` with empty body

This matches the current pre-Tapir baseline used in contract suites.

## Important `MasterApi` Detail

Do not model missing entity output as `jsonBody[Option[Master]]` during this migration phase.

Tapir does not preserve the current `200 + null` contract for that case reliably enough for this project baseline.

Instead:

- keep the endpoint output as JSON
- map `Option[Master]` explicitly in server logic
- use `Json.Null` for missing entity

That preserves the exact existing contract:

- existing entity -> `200` + JSON object
- missing entity -> `200` + `null`

## How To Migrate The Next Slice

1. Add route-level contract tests first if they do not already exist.
2. Create pure Tapir endpoints in `leaderboard/http/tapir/...Endpoints.scala`.
3. Keep the public slice entrypoint as `leaderboard.api.<Slice>Api` implementing `HttpApi[F]`.
4. Assemble Tapir server logic inside that adapter when a separate `*TapirServerEndpoints` class would only be wrapper boilerplate.
5. Reuse `TapirHttpSupport` for route interpretation.
6. Wire `TapirHttpSupport` plus the `HttpApi[F]` implementation in `LeaderboardPlugin`.
7. Run existing contract suites before and after the migration.

## What Not To Do

- do not migrate multiple slices at once
- do not replace `HttpServer`
- do not move business logic into Tapir endpoint definitions
- do not introduce a cross-project error ADT just for Tapir
- do not silently accept Tapir defaults for path/body decode behavior
- do not switch `200 + null` missing-entity responses to `404` without an explicit product decision

## Current Baseline

The current migrated slices already cover:

- JSON body + list/read/write pattern: `MasterApi`
- UUID path + `200 + null` + logging/service composition: `ProfileApi`
- numeric path capture + list/read/write without body: `LadderApi`
