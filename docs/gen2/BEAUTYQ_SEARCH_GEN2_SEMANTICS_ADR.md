# ADR: BeautyQ Search Gen2 semantic baseline

Status: proposed; must be accepted before Iteration 2  
Purpose: prevent backend compilers from encoding inconsistent semantics.

## Decisions to make

The following defaults are recommended. Change them only with explicit product evidence.

### Price range

Recommended: **interval overlap**.

A variant with `[priceFrom, priceTo]` matches requested `[min, max]` when:

```text
priceFrom <= max AND priceTo >= min
```

Reason: this matches the existing Qdrant supplement eligibility and represents “has an available price intersecting my budget”. If product semantics mean “starting price is within the range”, choose that instead and change every backend consistently.

### Numeric range bounds

Recommended: lower bound inclusive, upper bound exclusive for adjacent facet buckets; explicit request filters may use inclusive/exclusive operators.

Do not represent all bounds as bare `Option[min]/Option[max]`.

### Facet counts

Recommended initial behavior: counts under all currently applied filters, including the selected facet. Add disjunctive/self-excluding facets only as an explicit later policy.

### Geo

Recommended: coordinates alone do not imply “near me”. Geo score/filter activates only through explicit sort/filter or parsed `NearUser`. Coordinates are required data for executing that intent.

### Stable identity

Recommended: intent aliases resolve to stable service/category IDs or domain codes. Names remain display values.

### Constraint precedence

Recommended:

```text
explicit UI filter > parsed hard constraint > parsed soft signal > residual text
```

Conflicting explicit UI filters are validation errors. A parsed constraint conflicting with an explicit UI filter is suppressed and recorded in diagnostics.

### Empty query

Recommended: empty residual text with hard filters is valid; empty query with no filters uses a documented browse/default ranking plan rather than an accidental `bool {}` score.

### Total hits

Recommended: exact totals for current BeautyQ scale; make exactness a typed policy before scaling.

### Qdrant ownership

Recommended initial scope: candidate hits only. No facets, groups or response-filter inference ownership.

## Consequences

- `PriceRange` can no longer compile to a single generic field range if interval overlap is accepted.
- ES and Qdrant compilers need an interval-overlap constraint form.
- Existing range facet fixtures require boundary updates.
- `NearUser` must be represented in the plan rather than compiled to an empty filter.
- parser vocabulary and eval fixtures should migrate from names to stable IDs/codes.
