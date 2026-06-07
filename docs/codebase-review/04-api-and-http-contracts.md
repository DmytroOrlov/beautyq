# API And HTTP Contracts

## API Layer Shape

Implemented/current:

```text
Role class
  -> *Api adapter
    -> *TapirEndpoints pure contract
    -> repositories/services
    -> TapirHttpSupport -> HttpApi -> HttpServer
```

Evidence:

- Roles: `LeaderboardRole.scala`.
- API adapters: `bifunctor-tagless/src/main/scala/leaderboard/api/*.scala`.
- Tapir contracts: `bifunctor-tagless/src/main/scala/leaderboard/http/tapir/*TapirEndpoints.scala`.
- DI wiring: `LeaderboardPlugin.modules.api`.
- HTTP composition: `HttpServer.scala` and `many[HttpApi[F]]` in `LeaderboardPlugin.scala`.

## Role-Backed APIs

Implemented/current:

- `LadderApi` via `LadderRole`.
- `ProfileApi` via `ProfileRole`.
- `CategoryApi` via `CategoryRole`.
- `ServiceApi` via `ServiceRole`.
- `MasterApi` via `MasterRole`.
- `MasterLocationApi` via `MasterLocationRole`.
- `MasterServiceOfferApi` via `MasterServiceOfferRole`.
- `MasterServiceOfferVariantApi` via `MasterServiceOfferVariantRole`.

## Tapir Migration Status

Implemented/current based on source:

- Tapir endpoint files exist for all listed API slices: `Category`, `Service`, `Master`, `MasterLocation`, `MasterServiceOffer`, `MasterServiceOfferVariant`, `Ladder`, and `Profile`.
- `LeaderboardPlugin.modules.api` binds each `*TapirEndpoints` singleton and each `*Api` adapter.

Documentation drift:

- `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md` says the current migrated slices are only `LadderApi`, `MasterApi`, and `ProfileApi`.
- Current source shows more Tapir endpoint slices than that doc lists.

## Error And Legacy Helpers

Implemented/current:

- `HttpApiFailure.scala` defines typed HTTP failures used by Beauty single-entity GET adapters.
- `HttpApiFailureTapirSupport.scala` provides Tapir failure output support.
- `LegacyJsonResponse.scala` centralizes optional-as-JSON compatibility behavior.
- `TapirHttpSupport.scala` centralizes Tapir/http4s route interpretation and preserves project-specific malformed path/body/exception behavior according to `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md`.

## Single-Entity GET Contracts

Implemented/current based on docs and source searches:

- `docs/http-legacy-json-contracts.md` says no Beauty single-entity GET endpoints remain on the legacy `200 OK` plus JSON `null` path.
- It lists `MasterApi`, `CategoryApi`, `ServiceApi`, `MasterLocationApi`, `MasterServiceOfferApi`, and `MasterServiceOfferVariantApi` as migrated to typed entity JSON with `404` typed error JSON for missing entity.
- Source searches found `HttpApiFailure.NotFound.*` usage in Beauty API adapters.

Stale-doc warning:

- `docs/http-master-service-offer-variant-typed-get-plan.md` says `MasterServiceOfferVariantApi` is the last remaining Beauty legacy single-entity GET endpoint and still uses raw `Json` with `200 + null` missing-entity behavior.
- That appears stale relative to `docs/http-legacy-json-contracts.md` and current source/tests. Treat it as historical migration plan, not current architecture, unless a future line-level audit proves otherwise.

## Beauty Search HTTP Route

Contract skeleton/current:

- `BeautySearchTapirEndpoints.scala` defines a pure, unwired Beauty search Tapir contract.
- Contract path/method: `POST /beauty-search`.
- Request body: `UserSearchInput` JSON with `query`, `userLat`, `userLon`, and `limit`.
- Response body: `BeautySearchResponse` JSON preserving the existing variant carousel, provider carousel, service intent carousel, facets, and inferred filters model.
- Error output uses the existing `HttpApiFailureTapirSupport.endpointBase` non-single-entity behavior; route contract tests currently pin backend/query failure as `500` with an empty body through existing support.
- Empty search responses are explicit arrays for all carousel/facet/filter fields, not `null`.
- Diagnostics and routing metadata are not exposed by this contract skeleton; any diagnostics API remains future design.
- `BeautySearchApi.scala` is a thin, unwired API adapter that bridges `BeautySearchTapirEndpoints` to `BeautySearchService[F]`.
- `BeautySearchApiHttpContractSuite.scala` exercises the adapter route through a test-local fake `BeautySearchService` only.

Production-wired/current:

- No search role was found in `LeaderboardRole.scala`.
- `LeaderboardPlugin.modules.api` does not bind `BeautySearchService`, `BeautySearchBackend`, search endpoints, or a search `HttpApi`.
- `LeaderboardPlugin` was not changed for the unwired adapter, and targeted searches for production `/beauty-search` wiring found no production HTTP route.
- The implemented search model/service boundary exists in code as `UserSearchInput`, `ParsedSearchIntent`, `BeautySearchResponse`, `BeautySearchBackend[F]`, `BeautySearchService[F]`, `BeautySearchService.Impl`, and `BeautySearchSpecV1`, but that design boundary is not found in inspected wiring as a production route.

Conclusion:

- Beauty search now has a pure route contract skeleton and thin unwired API adapter, but it is not exposed as a production HTTP route in inspected runtime app wiring.
- Production Beauty search requires explicit route/binding. No confirmed production search role or production `/beauty-search` route was found in inspected wiring.
- No `LeaderboardPlugin` include, role registration, `BeautySearchService` production binding, or `BeautySearchBackend` production binding was added by the contract skeleton or unwired adapter.

Future implementation boundary:

- The next production Beauty search step should be `test(search): prove BeautySearchService.Impl binding with fake/in-memory backend` or `docs(search): design BeautySearchBackend readiness/freshness boundary`.
- Future production wiring still needs explicit decisions for role inclusion, backend selection, readiness behavior, timeout behavior, observability, and diagnostics visibility.
- Qdrant/hybrid remain non-production/manual-local/experimental and are not selected by the route contract skeleton or unwired adapter.

## Contract Tests

Architectural source of truth:

- `CategoryApiHttpContractSuite.scala`
- `ServiceApiHttpContractSuite.scala`
- `MasterApiHttpContractSuite.scala`
- `MasterLocationApiHttpContractSuite.scala`
- `MasterServiceOfferApiHttpContractSuite.scala`
- `MasterServiceOfferVariantApiHttpContractSuite.scala`
- `LadderApiHttpContractSuite.scala`
- `ProfileApiHttpContractSuite.scala`
- `LegacySingleEntityGetHttpContractSuite.scala`
- `TapirHttpSupportContractSuite.scala`
- `BeautySearchApiHttpContractSuite.scala`

What they protect:

- Exact path behavior.
- JSON body shape.
- Missing-entity status/body behavior.
- Malformed path/body behavior where pinned by Tapir support tests.
- Adapter delegation behavior through controlled contract state objects.

Rule for future docs/edits:

- For HTTP behavior, route-level contract tests are the source of truth. Do not update docs based only on adapter intuition.
- Beauty search HTTP behavior is currently defined only by the pure unwired endpoint plus route-level contract skeleton tests; do not infer production runtime exposure from those tests.
