# Legacy raw JSON single-entity HTTP contracts

This inventory documents the current compatibility layer for Beauty single-entity GET endpoints that still return raw `io.circe.Json` rather than a typed response.

## Current compatibility rule

No Beauty single-entity GET endpoints remain on the legacy `200 OK` + JSON `null` compatibility path.

That behavior was preserved during the migration phase while endpoints were still being converted. The remaining legacy case in this repo is `ProfileApi`, which is intentionally excluded from this Beauty inventory because it is a rank/read-model endpoint, not a Beauty domain typed single-entity migration candidate.

The current adapter-level encoding is centralized in `leaderboard.http.tapir.LegacyJsonResponse.optionalAsJson`, which turns `Option[A]` into `Json.Null` or the encoded entity body.

## Endpoints using raw `jsonBody[Json]` for single-entity GET responses

The following Tapir endpoint definitions currently expose single-entity GET responses as raw JSON:

- none for Beauty domain endpoints

Profile is intentionally excluded from this Beauty migration inventory. `ProfileApi` is a legacy rank/read-model endpoint, not a Beauty domain typed single-entity migration candidate, and remains the only legacy `200 + null` single-entity GET path in this repo.

## Per-endpoint inventory

### MasterApi

- Migrated on June 4, 2026.
- Current response contract: `GET /master/{id}` returns typed `Master` JSON.
- Missing-entity behavior now: `404 Not Found` with typed error JSON.
- This endpoint is no longer part of the legacy raw-JSON `200 + null` compatibility set.

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

- Migrated on June 4, 2026.
- Current response contract: `GET /master-location/{id}` returns typed `MasterLocation` JSON.
- Missing-entity behavior now: `404 Not Found` with typed error JSON.
- This endpoint is no longer part of the legacy raw-JSON `200 + null` compatibility set.

### MasterServiceOfferApi

- Migrated on June 4, 2026.
- Current response contract: `GET /master-service-offer/{id}` returns typed `MasterServiceOffer` JSON.
- Missing-entity behavior now: `404 Not Found` with typed error JSON.
- This endpoint is no longer part of the legacy raw-JSON `200 + null` compatibility set.

### MasterServiceOfferVariantApi

- Migrated on June 4, 2026.
- Current response contract: `GET /master-service-offer-variant/{id}` returns typed `MasterServiceOfferVariant` JSON.
- Missing-entity behavior now: `404 Not Found` with typed error JSON.
- This endpoint is no longer part of the legacy raw-JSON `200 + null` compatibility set.

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

`ServiceApi`, `CategoryApi`, `MasterApi`, `MasterLocationApi`, `MasterServiceOfferApi`, and `MasterServiceOfferVariantApi` have been migrated. No Beauty single-entity GET endpoints remain on the legacy raw-JSON `200 + null` path.

The migration steps should be:

1. add or extend route contract tests so the current compatibility behavior is pinned down,
2. introduce a typed endpoint with `singleEntityGetErrorOutput` and `jsonBody[Entity]`,
3. update the adapter to use `toRight(NotFound.xxx)` instead of `optionalAsJson`,
4. keep `ProfileApi` out of this Beauty migration inventory.
