# Legacy raw JSON single-entity HTTP contracts

This inventory documents the current compatibility layer for single-entity GET endpoints that still return raw `io.circe.Json` rather than a typed response.

## Current compatibility rule

Single-entity GET endpoints may intentionally return `200 OK` with JSON `null` when the entity is missing.

That behavior is preserved today as part of the legacy contract. It stays in place until a later typed-response / `404` migration phase explicitly replaces it.

The current adapter-level encoding is centralized in `leaderboard.http.tapir.LegacyJsonResponse.optionalAsJson`, which turns `Option[A]` into `Json.Null` or the encoded entity body.

## Endpoints using raw `jsonBody[Json]` for single-entity GET responses

The following Tapir endpoint definitions currently expose single-entity GET responses as raw JSON:

- `leaderboard.http.tapir.ProfileTapirEndpoints`
- `leaderboard.http.tapir.MasterTapirEndpoints`
- `leaderboard.http.tapir.MasterLocationTapirEndpoints`
- `leaderboard.http.tapir.MasterServiceOfferTapirEndpoints`
- `leaderboard.http.tapir.MasterServiceOfferVariantTapirEndpoints`

These are the actual files used by the HTTP adapters in:

- `leaderboard.api.ProfileApi`
- `leaderboard.api.MasterApi`
- `leaderboard.api.MasterLocationApi`
- `leaderboard.api.MasterServiceOfferApi`
- `leaderboard.api.MasterServiceOfferVariantApi`

## Per-endpoint inventory

### ProfileApi

- Current response contract: `GET /profile/{id}` returns `jsonBody[Json]`.
- Likely future typed response: `UserProfile` or a domain-specific profile read model, depending on whether this endpoint is meant to expose the stored profile or the computed rank payload.
- Missing-entity behavior today: `200 OK` with `null`.
- Migration risk: low to medium. The payload is already a simple entity-shaped response, but the current API path is entangled with legacy compatibility behavior.
- Required tests before changing: route contract tests for existing `200 + null`, existing object-body serialization, and the malformed-path / failure-path behavior that the Tapir adapter currently preserves.

### MasterApi

- Current response contract: `GET /master/{id}` returns `jsonBody[Json]`.
- Likely future typed response: `Master`.
- Missing-entity behavior today: `200 OK` with `null`.
- Migration risk: low. This is a straightforward single-entity lookup with a direct domain type available.
- Required tests before changing: route contract tests for missing entity, present entity, and any existing contract expectations around status code and JSON shape.

### CategoryApi

- Migrated on June 4, 2026.
- Current response contract: `GET /category/{id}` returns typed `Category` JSON.
- Missing-entity behavior now: `404 Not Found` with typed error JSON.
- This endpoint is no longer part of the legacy raw-JSON `200 + null` compatibility set.

### ServiceApi

- Migrated on June 4, 2026.
- Current response contract: `GET /service/{id}` returns `200 OK` with typed `Service` JSON when present.
- Missing-entity behavior now: `404 Not Found` with typed error JSON.
- This endpoint is no longer part of the legacy raw-JSON `200 + null` compatibility set.

### MasterLocationApi

- Current response contract: `GET /master-location/{id}` returns `jsonBody[Json]`.
- Likely future typed response: `MasterLocation`.
- Missing-entity behavior today: `200 OK` with `null`.
- Migration risk: medium. Location lookups are often consumed alongside master-service-offer data, so compatibility needs to be checked against clients that rely on null-returning reads.
- Required tests before changing: route contract tests for missing entity, present entity, and the existing response body serialization.

### MasterServiceOfferApi

- Current response contract: `GET /master-service-offer/{id}` returns `jsonBody[Json]`.
- Likely future typed response: `MasterServiceOffer`.
- Missing-entity behavior today: `200 OK` with `null`.
- Migration risk: medium. This entity sits closer to the broader offer graph and may be part of workflows that expect compatibility with the current raw-JSON transport.
- Required tests before changing: route contract tests for missing entity, present entity, and any service/master lookup behavior that depends on the current read contract.

### MasterServiceOfferVariantApi

- Current response contract: `GET /master-service-offer-variant/{id}` returns `jsonBody[Json]`.
- Likely future typed response: `MasterServiceOfferVariant`.
- Missing-entity behavior today: `200 OK` with `null`.
- Migration risk: high. This endpoint is the most coupled to the variant attribute codec and the legacy JSON contract is part of a larger storage/encoding story.
- Required tests before changing: route contract tests for missing entity, present entity, JSON encoding stability, and any variant attribute round-trip behavior already covered elsewhere. This should not be the first endpoint migrated.

## Future migration options

### Option A: keep `200 + null`, but centralize typed encoding

Keep the compatibility status code and missing-entity shape, but move the actual response body construction to a typed endpoint or a typed adapter boundary.

This minimizes client churn and lets the codebase move toward typed responses without breaking legacy consumers.

### Option B: switch to `404` with a typed error body

Change the contract so missing entities become `404 Not Found` and return a typed error payload.

This is cleaner semantically, but it is a breaking contract change and must be done only after the relevant clients and tests are updated.

### Option C: use `oneOf` / ADTs in Tapir while preserving compatibility temporarily

Model the response as an ADT in Tapir so the contract can explicitly represent both the existing compatibility case and the future typed case.

This provides an intermediate migration path, but it still needs careful adapter logic so the current `200 + null` behavior stays unchanged until the cutover.

## Recommended first migration step

Start with one low-risk endpoint, most likely `MasterApi` or `ServiceApi`.

The first step should be:

1. add or extend route contract tests so the current compatibility behavior is pinned down,
2. introduce a typed endpoint behind the existing compatibility adapter,
3. keep the `200 + null` behavior for missing entities during the transition,
4. do not start with `MasterServiceOfferVariantApi`.

`MasterServiceOfferVariantApi` should come later because it carries the most coupling to variant encoding and would make the first migration unnecessarily risky.
