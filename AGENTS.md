# AGENTS.md

## Repo workflow rules

- Check nearby project code before using framework APIs from memory.
  - For Distage axes/modules/test wiring, follow existing repo patterns instead of assumed library APIs.

- Keep paired Distage test suites aligned when moving or splitting specs.
  - Preserve `*Dummy`/`*Postgres` wrapper suites and trait mixins so both Repo modes keep running.

- Keep mechanical refactors behavior-preserving.
  - Extract shared fixtures first, move tests unchanged, and limit follow-up fixes to imports, wiring, visibility, and syntax.

## Domain invariants

- Preserve the `MasterServiceOfferVariant` JSON attribute contract when changing codecs.
  - Keep attribute-group decoding centralized and type-aware; enum attributes remain string codes in JSON (`enumAttributes: Map[String, String]`).

- Keep `MasterServiceOfferVariant` additional attributes on the unified numeric storage path.
  - Use `master_service_offer_variant_numeric_attributes` for int, bigdecimal, enum-as-int-code, and boolean values; validate enum attribute/value compatibility before encoding, and add new storage paths only as an intentional schema redesign.

## HTTP migration rules

- Preserve existing route-level HTTP contracts during Tapir migrations.
  - Existing route-level contract tests are the source of truth, not Tapir defaults; keep current malformed path/body decode and uncaught-exception responses unless deliberately changing the contract.

- Keep the current Tapir adapter split.
  - Put pure endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`, keep `leaderboard.api.*Api` as thin `HttpApi[F]` adapters, and reuse `TapirHttpSupport` for route interpretation.

- Preserve current missing-entity response contracts.
  - Do not replace existing `200 + null` responses with `404` or `jsonBody[Option[_]]` during the current migration phase; map missing values explicitly to `Json.Null` where required.

## Distage resource graph rules

- Distage resource startup order follows dependency edges, not textual binding order.
  - `make[A].fromResource[...]` order in `ModuleDef` and `memoizationRoots` order in tests are not sequencing guarantees.

- Preserve FK ordering edges in Postgres repos.
  - If child-table DDL references a parent table, `ChildRepo.Postgres` must depend on the immediate parent repo, usually via `@unused`; do not remove these params as dead code.

## BIO / observability refactor rules

- Treat ZIO-specific to BIO2 refactors as behaviorally risky.
  - Preserve or add contract tests for tracing context, logging context, tracing headers, async child lifetime, publish/replay ordering, and fallback behavior before refactoring.

- Do not genericize services from `IO` to polymorphic `F` without a concrete reason.
  - A wider effect surface is justified only by a real non-IO consumer, clear duplication reduction, or improved testability without weakening observability guarantees.
