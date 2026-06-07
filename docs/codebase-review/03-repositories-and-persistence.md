# Repositories And Persistence

## Repository Pattern

Implemented/current:

- Repository traits live in `bifunctor-tagless/src/main/scala/leaderboard/repo`.
- Each major domain area has a trait plus `Dummy` and `Postgres` implementations.
- Dummy implementations use in-memory state and are bound under `Repo.Dummy` in `LeaderboardPlugin.modules.repoDummy`.
- Postgres implementations are resource constructors and are bound under `Repo.Prod` in `LeaderboardPlugin.modules.repoProd`.

Repository interfaces:

- `Ladder[F]`: score submission/listing.
- `Profiles[F]`: profile set/get.
- `Categories[F]`: category upsert/get/children.
- `Services[F]`: service upsert/get/by-category.
- `Masters[F]`: master upsert/get/list.
- `MasterLocations[F]`: location upsert/get/by-master.
- `MasterServiceOffers[F]`: offer upsert/get/by-master/by-service.
- `ServiceVariantSchemas[F]`: schema upsert/get.
- `MasterServiceOfferVariants[F]`: variant upsert/get/by-offer/by-location.

## Where Schema Is Defined

Implemented/current:

- No standalone `.sql` migration files were found under `bifunctor-tagless/src` during pass 1.
- Postgres schema is defined with `create table if not exists` statements inside repository `Postgres` resource constructors.
- SQL runs through `SQL.Impl[F]` and Doobie `Transactor` in `leaderboard/sql/SQL.scala` and `TransactorResource.scala`.

Important consequence:

- Repository resource construction is also schema initialization. Startup ordering matters because child table DDL can reference parent tables.

## FK Dependencies And Startup Order

Implemented/current:

- `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md` states Distage resource startup follows dependency edges, not textual binding order or `memoizationRoots` order.
- Parent repository constructor dependencies are used to make FK table creation order explicit.

Observed table graph:

```text
categories -> services -> master_service_offers -> master_service_offer_variants
masters    -> master_locations --------------------^
masters    -> master_service_offers ---------------^
services   -> service_variant_schema_items
master_service_offer_variants -> master_service_offer_variant_numeric_attributes
```

Why parent dependencies exist:

- `Services.Postgres` needs `Categories[F]` because `services.category_id` references `categories(id)`.
- `MasterLocations.Postgres` needs `Masters[F]` because `master_locations.master_id` references `masters(id)`.
- `MasterServiceOffers.Postgres` needs `Masters[F]` and `Services[F]` because its table references both.
- `ServiceVariantSchemas.Postgres` needs `Services[F]` because schema rows reference services.
- `MasterServiceOfferVariants.Postgres` uses `masterServiceOffers` and `masterLocations` as `@unused` FK readiness edges and uses `serviceVariantSchemas` for variant attribute validation.

What not to infer:

- `@unused` parent repo constructor parameters are not necessarily dead code. In this architecture they can be resource-order edges.

## Variant Attribute Storage

Implemented/current:

- Domain attributes are typed in `MasterServiceOfferVariantAttributes`.
- `MasterServiceOfferVariantAttributesRepository.scala` encodes/decodes additional attributes for storage.
- Postgres storage table: `master_service_offer_variant_numeric_attributes`.
- The table stores `master_service_offer_variant_id`, `attribute_code`, and numeric value; decode logic maps int, BigDecimal, boolean, and enum values back to typed attributes.
- `MasterServiceOfferVariant.scala` preserves JSON shape as separate `intAttributes`, `bigDecimalAttributes`, `enumAttributes`, and `booleanAttributes` maps.

Protected by tests:

- `VariantAttributeJsonSpec.scala`
- `VariantAttributeStorageSpec.scala`
- `VariantAttributeSchemaSpec.scala`
- `MasterServiceOfferVariantsSpec.scala`
- `MasterServiceOfferVariantApiHttpContractSuite.scala`

## Service Variant Schema Storage

Implemented/current:

- `ServiceVariantSchemas.Postgres` stores rows in `service_variant_schema_items`.
- Rows include service id, attribute code, and required flag.
- `ServiceVariantSchemas` validates attribute codes through `AttributeDefinition` when decoding rows.
- `MasterServiceOfferVariants` validates variant attributes against service schema before storing/loading variants.

## Seed Data Flow

Implemented/current:

```text
seed JSON resource
  -> BeautyQSeedLoader.ResourceLoader
  -> BeautyQSeedData decoder
  -> BeautyQSeedInserter.Impl
  -> repository upserts in dependency-safe order
  -> BeautyQSeedReady.LoadAndInsert in Mode.Test
```

Evidence:

- Seed resource: `bifunctor-tagless/src/main/resources/seed/wandsbek_hamburg_beauty_services_seed_ready.json`.
- Decoder: `BeautyQSeedData.scala`.
- Loader: `BeautyQSeedLoader.scala`.
- Inserter: `BeautyQSeedInserter.scala`.
- DI binding: `LeaderboardPlugin.modules.seed`, `seedProd`, and `seedTest`.

Seed insertion order:

1. Non-root categories sorted by depth/name/id.
2. Services.
3. Masters.
4. Master locations.
5. Master service offers.
6. Service variant schemas.
7. Master service offer variants.

This order is visible in `BeautyQSeedInserter.Impl.insert`.

## Search Snapshot Loading From Repositories

Implemented/current:

- `BeautySearchCatalogSnapshotLoader.FromRepositories` loads categories recursively, services by category, schemas by service, masters, locations, offers, and variants.
- `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` uses seed ids and then loads existing repository rows, failing when a seed-scoped entity is missing.
- `SeedScopedFromRepositories` now takes a direct `BeautyQSeedReady` constructor dependency before repository collaborators, matching the seed-json plus shared-Postgres snapshot readiness rule without changing loader behavior.

Resolved mismatch:

- Pass-3 review recorded this as a dependency-rule mismatch rather than a proven runtime failure.
- The mismatch is now resolved by moving the direct readiness edge into `SeedScopedFromRepositories` itself, so seed-scoped repository snapshot paths express `BeautyQSeedReady` explicitly at construction time.

## Transaction Boundaries

Implemented/current, limited observation:

- Postgres repository methods compose Doobie `ConnectionIO` and run through `SQL[F]`.
- `MasterServiceOfferVariants.Postgres` composes variant base-row operations with attribute replacement through `MasterServiceOfferVariantAttributesRepository.Postgres`.
- Pass 2 did not prove a cross-repository transaction boundary for seed insertion; `BeautyQSeedInserter.Impl` sequentially calls repository methods.

Unclear:

- Whether variant base-row upsert and attribute replacement are always atomic as one Doobie transaction should be confirmed by line-level inspection in a future implementation pass.

## Repository Tests As Contracts

Architectural source of truth:

- `CatalogRepositoriesSpec.scala`: category/service/master/location/offer repository behavior across dummy and Postgres variants.
- `MasterServiceOfferVariantsSpec.scala`: variant repository behavior and invariants.
- `VariantAttributeStorageSpec.scala`: storage encoding/decoding of attributes.
- `BeautyQSeedSpec.scala`: seed decode/insert/load behavior.
- `LeaderboardSpecSupport.scala`: Distage test activation and repo memoization roots.

These tests are more authoritative for current persistence behavior than broad prose docs.
