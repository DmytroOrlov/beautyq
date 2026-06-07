# Domain Model Guide

## Domain Overview

BeautyQ models a catalog of beauty services offered by masters at physical locations. The current code names are the architecture source of truth:

- `Category`: service taxonomy node.
- `Service`: concrete service in a category.
- `Master`: provider/master entity.
- `MasterLocation`: physical provider location with address and geo coordinates.
- `MasterServiceOffer`: relationship saying a master offers a service.
- `MasterServiceOfferVariant`: purchasable variant of an offer at a location with price, duration, and attributes.
- `ServiceVariantSchema`: allowed/required attribute schema for variants of one service.
- `MasterServiceOfferVariantAttributes`: typed attribute values attached to a variant.

Evidence: core case classes are in `bifunctor-tagless/src/main/scala/leaderboard/model/package.scala`, `MasterServiceOfferVariant.scala`, `MasterServiceOfferVariantAttributes.scala`, `ServiceVariantSchema.scala`, and `AttributeDefinition.scala`.

## Entity Relationship Map

Implemented/current:

```text
Category
  -> Service
    -> MasterServiceOffer <- Master
      -> MasterServiceOfferVariant -> MasterLocation -> Master

Service
  -> ServiceVariantSchema
    -> allowed/required AttributeDefinition entries

MasterServiceOfferVariant
  -> MasterServiceOffer
  -> MasterLocation
  -> MasterServiceOfferVariantAttributes
```

Direct relationship evidence:

- `Service(id, categoryId, name)` references `Category` in `model/package.scala`; `Services.Postgres` has `foreign key (category_id) references categories(id)`.
- `MasterLocation(id, masterId, ...)` references `Master`; `MasterLocations.Postgres` has `foreign key (master_id) references masters(id)`.
- `MasterServiceOffer(id, masterId, serviceId)` references `Master` and `Service`; `MasterServiceOffers.Postgres` has both FKs.
- `MasterServiceOfferVariant(id, masterServiceOfferId, masterLocationId, ...)` references offer and location; `MasterServiceOfferVariants.Postgres` has both FKs.
- `MasterServiceOfferVariants` enforces offer and location are for the same master with `offerAndLocationMustBelongToSameMaster`.

## Category

Implemented/current:

- Code: `Category` in `model/package.scala`.
- Fields: `id`, `parentId`, `depth`, `name`.
- Root: `Category.rootCategoryId` is a fixed UUID.
- Repository: `Categories[F]` in `repo/Categories.scala`.
- API: `CategoryApi.scala` and `CategoryTapirEndpoints.scala`.
- Tests: `CatalogRepositoriesSpec.scala`, `CategoryApiHttpContractSuite.scala`.

Product meaning:

- Category is the taxonomy parent for services. Search documents carry both `categoryId` and `categoryName` in `VariantSearchDocument`.

## Service

Implemented/current:

- Code: `Service` in `model/package.scala`.
- Fields: `id`, `categoryId`, `name`.
- Repository: `Services[F]` in `repo/Services.scala`.
- API: `ServiceApi.scala` and `ServiceTapirEndpoints.scala`.
- Tests: `CatalogRepositoriesSpec.scala`, `ServiceApiHttpContractSuite.scala`.

Product meaning:

- Service is the catalog intent users search for or navigate to, e.g. service-intent carousel results. `ServiceIntentSearchResult` in `BeautySearchModels.scala` carries `serviceId`, `serviceName`, `categoryId`, `categoryName`, matching count, and best score.

## Master

Implemented/current:

- Code: `Master` in `model/package.scala`.
- Fields: `id`, `name`.
- Repository: `Masters[F]` in `repo/Masters.scala`.
- API: `MasterApi.scala` and `MasterTapirEndpoints.scala`.
- Tests: `CatalogRepositoriesSpec.scala`, `MasterApiHttpContractSuite.scala`.

Product meaning:

- Master is the provider/master identity. Search result projections include `masterId` and `masterName`.

## MasterLocation

Implemented/current:

- Code: `MasterLocation` in `model/package.scala`.
- Fields: `id`, `masterId`, `name`, `address`, `lat`, `lon`.
- Repository: `MasterLocations[F]` in `repo/MasterLocations.scala`.
- API: `MasterLocationApi.scala` and `MasterLocationTapirEndpoints.scala`.
- Tests: `CatalogRepositoriesSpec.scala`, `MasterLocationApiHttpContractSuite.scala`.

Product meaning:

- MasterLocation is the physical provider location. It is used in provider/location search results and geo scoring. `ProviderSearchResult` carries `masterLocationId`, `locationName`, `address`, and `distanceKm`.

## MasterServiceOffer

Implemented/current:

- Code: `MasterServiceOffer` in `model/package.scala`.
- Fields: `id`, `masterId`, `serviceId`.
- Repository: `MasterServiceOffers[F]` in `repo/MasterServiceOffers.scala`.
- API: `MasterServiceOfferApi.scala` and `MasterServiceOfferTapirEndpoints.scala`.
- Tests: `CatalogRepositoriesSpec.scala`, `MasterServiceOfferApiHttpContractSuite.scala`.

Product meaning:

- Offer connects a master/provider to a service. Variants hang off offers and locations.

## MasterServiceOfferVariant

Implemented/current:

- Code: `MasterServiceOfferVariant` in `model/MasterServiceOfferVariant.scala`.
- Fields: `id`, `masterServiceOfferId`, `masterLocationId`, `priceFrom`, `priceTo`, `durationMin`, `attributes`.
- Factory: `MasterServiceOfferVariant.make` validates price and duration invariants.
- Repository: `MasterServiceOfferVariants[F]` in `repo/MasterServiceOfferVariants.scala`.
- API: `MasterServiceOfferVariantApi.scala` and `MasterServiceOfferVariantTapirEndpoints.scala`.
- Search projection: `VariantSearchDocument` in `search/document/VariantSearchDocument.scala` and `VariantSearchResult` in `search/BeautySearchModels.scala`.
- Tests: `MasterServiceOfferVariantsSpec.scala`, `VariantAttributeJsonSpec.scala`, `VariantAttributeStorageSpec.scala`, `MasterServiceOfferVariantApiHttpContractSuite.scala`, search eval tests.

Product meaning:

- This is the primary purchasable/search-result unit. Evidence: `beautyq_search_eval_plan_v1.md` says the primary buyable unit is `MasterServiceOfferVariant`; `VariantSearchDocumentBuilder` builds one search document per seeded variant.

Price/duration invariants:

- `priceFrom` must be non-negative.
- `priceTo` must be greater than or equal to `priceFrom`.
- `durationMin` must be positive.
- Evidence: `MasterServiceOfferVariant.make` in `MasterServiceOfferVariant.scala` returns `NegativePriceFrom`, `PriceToLessThanPriceFrom`, or `NonPositiveDurationMin`.

## ServiceVariantSchema

Implemented/current:

- Code: `ServiceVariantSchema` and `ServiceVariantSchemaItem` in `model/ServiceVariantSchema.scala`.
- Repository: `ServiceVariantSchemas[F]` in `repo/ServiceVariantSchemas.scala`.
- Storage: `service_variant_schema_items` table in `ServiceVariantSchemas.Postgres`.
- Seed: decoded from `serviceVariantSchemas` in `BeautyQSeedData.scala`.
- Tests: `VariantAttributeSchemaSpec.scala`, `CatalogRepositoriesSpec.scala`, `BeautyQSeedSpec.scala`.

Product meaning:

- It defines which typed attributes are allowed or required for variants of a service. `MasterServiceOfferVariants` validates variant attributes against service schema.

## Attributes And Coded Enums

Implemented/current:

- Attribute registry: `AttributeDefinition` in `model/AttributeDefinition.scala`.
- Attribute value groups: int, BigDecimal, boolean, enum.
- Variant values: `MasterServiceOfferVariantAttributes` in `model/MasterServiceOfferVariantAttributes.scala`.
- Enum abstraction: `CodedEnumValue` in `model/CodedEnumValue.scala`.
- JSON: `MasterServiceOfferVariant.scala` encodes enum values as stable `stringCode` under `enumAttributes` and keeps separate maps for `intAttributes`, `bigDecimalAttributes`, `booleanAttributes`, and `enumAttributes`.
- Search: `BeautySearchSpecV1` generates dynamic fields from `AttributeDefinition.all`.

Important consequence:

- Attribute shape is not a generic JSON blob in current code. It is typed in the domain model, schema-validated in repositories, encoded with stable attribute codes, and projected into search documents.

## Salon

Implemented/current:

- No first-class `Salon` model, repository, API, or table was found by targeted searches for `Salon|salon` in main/test/docs.
- Current provider/location concepts are `Master` and `MasterLocation`.

Interpretation:

- If product language says “salon,” the closest current code concept is probably a provider location (`MasterLocation`) with a master/provider (`Master`). This is an interpretation, not a code-level alias.

What not to infer:

- Do not add or document a first-class `Salon` abstraction as current architecture. It is absent from inspected code.

## Availability And Scheduling

Implemented/current:

- No first-class availability, appointment, booking calendar, schedule, or slot model was found by targeted searches across main source, tests, and docs.

What not to infer:

- `MasterServiceOfferVariant` can be described as the current purchasable/search-result unit because docs call it buyable and it carries price/duration, but current code does not implement appointment availability or scheduling.
