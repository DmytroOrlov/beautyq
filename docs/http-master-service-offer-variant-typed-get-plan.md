# MasterServiceOfferVariant typed GET migration plan

This document is the migration plan for the last remaining Beauty legacy single-entity GET endpoint.

## Current state

- `MasterServiceOfferVariantApi` is the only remaining Beauty legacy single-entity GET endpoint.
- It still uses raw `Json` with the legacy `200 + null` missing-entity behavior.
- It is high-risk because the response is coupled to variant attribute JSON, enum `stringCode` JSON, and the existing numeric storage path.

## Required contract tests before migration

Add or keep focused route-level contract tests that pin down the current behavior before any endpoint migration work starts:

- existing variant returns the exact current JSON object
- missing variant returns the current legacy `200 + null` behavior before migration
- `enumAttributes` are encoded as `stringCode`
- `int` / `bigDecimal` / `boolean` attributes preserve the current JSON shape
- disallowed / required attribute validation semantics remain unchanged
- list endpoints remain unchanged

## Migration target

The target contract after migration is:

- existing variant: `200` + typed `MasterServiceOfferVariant` JSON
- missing variant: `404` + typed `HttpApiFailure.NotFound` JSON
- no `jsonBody[Option[_]]`
- no codec or storage changes

## Safe implementation sequence

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
