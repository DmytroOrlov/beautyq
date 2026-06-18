# MasterServiceOfferVariant typed GET migration plan

Historical migration note only. Current source, route contract tests, and
canonical docs show `MasterServiceOfferVariantApi` is already on the typed
single-entity GET path. Keep this file only until pruning removes or merges the
remaining historical references.

## Current state

- This file described the former migration target for `MasterServiceOfferVariantApi`.
- Current source uses typed single-entity GET handling with typed `404` error output.
- Current contract tests cover typed `MasterServiceOfferVariant` JSON plus typed
  not-found behavior.
- The variant JSON surface remains high-sensitivity because it is coupled to
  attribute JSON, enum `stringCode`, and the existing numeric storage path.

## Required contract tests before migration

Historical checklist retained for audit context:

- existing variant returns the exact current JSON object
- missing variant returns the current legacy `200 + null` behavior before migration
- `enumAttributes` are encoded as `stringCode`
- `int` / `bigDecimal` / `boolean` attributes preserve the current JSON shape
- disallowed / required attribute validation semantics remain unchanged
- list endpoints remain unchanged

## Migration target

Migration target that has now been reached:

- existing variant: `200` + typed `MasterServiceOfferVariant` JSON
- missing variant: `404` + typed `HttpApiFailure.NotFound` JSON
- no `jsonBody[Option[_]]`
- no codec or storage changes

## Safe implementation sequence

Historical implementation sequence:

1. First add focused contract tests pinning the current variant JSON.
2. Then add the `NotFound` constructor/helper if it does not already exist in the typed path needed by this endpoint.
3. Then update the Tapir endpoint success and error output.
4. Then update the API adapter.
5. Then update the docs inventory.

## Non-goals

- no storage schema redesign
- no enum encoding changes
- no attribute validation changes
- no list endpoint migration
- no search-index changes

## Report

- file created: `docs/http-master-service-offer-variant-typed-get-plan.md`
- production code changed: no
- tests run: no
