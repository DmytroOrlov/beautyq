# Repository inventory

## 1. Top-level structure

- `build.sbt`: SBT build definition for the repository. This pass did not inspect all settings deeply, but the visible module source is `bifunctor-tagless`.
- `project/plugins.sbt`: SBT plugin configuration.
- `README.md`: upstream `distage-example` README. It describes `bifunctor-tagless` as the main example, `sbt test`, launcher usage, Docker/Postgres requirements, and GraalVM native-image commands.
- `beautyq_search_eval_plan_v1.md`: BeautyQ search eval plan. It states the primary buyable unit is `MasterServiceOfferVariant` and describes three expected carousels: `variantCarousel`, `providerCarousel`, and `serviceIntentCarousel`.
- `docs/`: architecture/reference/task docs. Files include search DSL, Qdrant vector backend, hybrid plan, Tapir migration reference, HTTP legacy contract notes, and local coordinator/local-LLM references.
- `docs/codebase-review/`: created by this pass for review notes and inventory.
- `bifunctor-tagless/src/main/scala/leaderboard`: main Scala source for roles, APIs, model, repositories, seed loading, SQL, HTTP, plugins, and search.
- `bifunctor-tagless/src/main/resources/common-reference.conf`: Postgres and logger configuration. It defines `postgres.jdbcDriver`, `url`, `user`, `password`, `host`, and `port`.
- `bifunctor-tagless/src/main/resources/seed/wandsbek_hamburg_beauty_services_seed_ready.json`: BeautyQ seed resource. Its metadata records counts for `categories`, `services`, `serviceVariantSchemas`, `masters`, `masterLocations`, `masterServiceOffers`, and `masterServiceOfferVariants`.
- `bifunctor-tagless/src/test/scala/leaderboard`: test suites for attributes, repositories, HTTP contracts, ranking, search, seed loading, variants, wiring, and testkit support.
- `graal-resources/`: Graal resources directory present at top level.
- `target/`, `bifunctor-tagless/target/`, `project/target/`: build output directories present; not inventoried as source.

Build/module observations:

- Main code inspected is under `bifunctor-tagless`; README mentions other upstream variants (`monofunctor-tagless`, `monomorphic-cats`), but those directories were not present in the shallow top-level directory listing used in this pass.
- Runtime roles are defined in `bifunctor-tagless/src/main/scala/leaderboard/LeaderboardRole.scala` using Distage `RoleAppMain.LauncherBIO[IO]`.
- DI bindings are primarily in `bifunctor-tagless/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala`.

## 2. Existing documentation

- `README.md`: generic distage-example README. Evidence: describes `bifunctor-tagless/src` as main example, `sbt test`, `./launcher -u scene:managed :leaderboard`, and `./launcher -u repo:dummy :leaderboard`.
- `beautyq_search_eval_plan_v1.md`: offline benchmark/eval plan for Wandsbek/Hamburg seed dataset. Evidence: names `MasterServiceOfferVariant` as primary buyable unit and lists `variantCarousel`, `providerCarousel`, `serviceIntentCarousel`.
- `docs/beautyq-search-dsl-v1.md`: describes search V1 as immutable Scala values interpreted into Postgres snapshot flattening, Elasticsearch mapping/ingestion/request/response, and pure in-memory regression backend. Evidence: names packages `leaderboard.search.dsl`, `document`, `parser`, `interpreter`, `elasticsearch`, `inmemory`, `eval`.
- `docs/search-dsl-domain-onboarding.md`: describes reusable search DSL/interpreter concepts and domain-specific pieces. Evidence: names `SearchField`, `SearchDocumentSpec`, `SearchConstraint`, `SearchSynonym`, `BeautySearchSpec`, Elasticsearch interpreters, `InMemorySearchBackend`, and `BeautySearchEvalTestSupport`.
- `docs/search-dsl-qdrant-vector-backend.md`: Qdrant/vector backend plan/status. Evidence: states Qdrant is separate semantic/vector recall backend, complements Elasticsearch, and production hybrid is not implemented. It lists implemented non-production pieces such as `QdrantClient`, `QdrantSemanticCandidateBackend`, `QdrantVariantDocumentSnapshotIndexer`, and `QdrantNonProductionExperimentComposition`.
- `docs/search-dsl-hybrid-v1-plan.md`: hybrid search plan/status. Evidence: states Hybrid V1 is non-production foundation, Elasticsearch remains baseline, and there is no production hybrid behavior, fallback, score fusion, reranking, or production routing change.
- `docs/http-legacy-json-contracts.md`: HTTP contract inventory for legacy raw JSON single-entity GETs. Evidence: states no Beauty single-entity GET endpoints remain on `200 OK` plus JSON `null`, and Profile is excluded.
- `docs/http-master-service-offer-variant-typed-get-plan.md`: migration plan for `MasterServiceOfferVariant` typed GET. Evidence: states it is/was a plan, with non-goals including no storage schema redesign, enum encoding changes, attribute validation changes, list endpoint migration, or search-index changes.
- `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md`: local reference for Distage app model and schema-order rules. Evidence: documents that repo DDL runs in resource constructors and FK-backed tables should express parent-table dependencies through constructor dependencies.
- `docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md`: local reference for Izumi/BIO/Distage style. Evidence: focuses on `F[+_, +_]`, `Applicative2`, `Error2`, `Lifecycle`, `ModuleDef`, etc.
- `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md`: local reference for Tapir HTTP migration pattern. Evidence: says pure endpoint contracts are in `*TapirEndpoints.scala`, adapters in `leaderboard.api.*Api`, and `TapirHttpSupport` centralizes route interpreter policy.
- `docs/local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`: canonical coordinator-only workflow guide. Evidence: owns coordinator workflow, source-truth gate, prompt packaging, closeout, bundle scripts, docs ownership, and model recommendation guidance; the old standalone model-selection policy was merged here and removed.

Documentation drift noted:

- `docs/LOCAL_LLM_TAPIR_HTTP_REFERENCE.md` says current migrated slices are `LadderApi`, `MasterApi`, and `ProfileApi`, but source also has `CategoryTapirEndpoints`, `ServiceTapirEndpoints`, `MasterLocationTapirEndpoints`, `MasterServiceOfferTapirEndpoints`, and `MasterServiceOfferVariantTapirEndpoints`.
- `docs/http-master-service-offer-variant-typed-get-plan.md` appears older than `docs/http-legacy-json-contracts.md`; the former describes a remaining legacy endpoint, while the latter says no Beauty single-entity GET endpoints remain legacy.

## 3. Runtime entrypoints and application wiring

Runtime roles in `bifunctor-tagless/src/main/scala/leaderboard/LeaderboardRole.scala`:

- `LadderRole`: starts ladder API; constructor depends on `LadderApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `ProfileRole`: starts profile API; constructor depends on `ProfileApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `CategoryRole`: starts category API; constructor depends on `CategoryApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `ServiceRole`: starts service API; constructor depends on `ServiceApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `MasterRole`: starts master API; constructor depends on `MasterApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `MasterLocationRole`: starts master-location API; constructor depends on `MasterLocationApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `MasterServiceOfferRole`: starts master-service-offer API; constructor depends on `MasterServiceOfferApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `MasterServiceOfferVariantRole`: starts master-service-offer-variant API; constructor depends on `MasterServiceOfferVariantApi[F]`, `HttpServer`, and `LogIO2[F]`.
- `LeaderboardRole`: composite role depending on category, service, master, master location, master service offer, and variant roles. It logs that those APIs plus Profile APIs started; constructor list does not visibly include `ProfileRole[F]` in inspected source snippet.

Launcher objects in `LeaderboardRole.scala`:

- `MainDummy`: `Activation(Repo -> Repo.Dummy)` and required `LeaderboardRole`.
- `MainProdDocker`: `Activation(Repo -> Repo.Prod, Scene -> Scene.Managed)` and required `LeaderboardRole`.
- `MainProd`: `Activation(Repo -> Repo.Prod, Scene -> Scene.Provided)` and required `LeaderboardRole`.
- Per-slice launchers exist for ladder, category, service, master, master-location, master-service-offer, master-service-offer-variant, and profile with dummy/prod-docker/prod variants.
- `MainHelp`: launches Distage help role.
- `MainWriteReferenceConfigs`: launches config writer role with HOCON output.
- `GenericLauncher`: `Activation(Repo -> Repo.Prod, Scene -> Scene.Provided)`, no required roles by default.
- `MainBase`: extends `RoleAppMain.LauncherBIO[IO]`, uses `PluginConfig.cached(pluginsPackage = "leaderboard.plugins")` unless running in Graal native image, and overrides default activation with `Scene.Provided` and `Mode.Prod`.

DI/plugin wiring in `bifunctor-tagless/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala`:

- Includes modules: `roles`, `api`, `repoDummy`, `repoProd`, `seed`, `seedProd`, `seedTest`, `configs`, `prodConfigs`.
- `modules.roles`: binds role descriptors with `makeRole[...]` for ladder, category, service, master, master-location, master-service-offer, master-service-offer-variant, profile, and composite leaderboard; includes bundled `help` and `configwriter` roles.
- `modules.api`: binds Tapir endpoint singleton values, API adapters, `TapirHttpSupport[F]`, `HttpServer.Impl[F]`, and `Ranks.Impl[F]`. It also creates `many[HttpApi[F]]` weak set with the API adapters.
- `modules.repoDummy`: tagged `Repo.Dummy`; binds dummy resources for `Ladder`, `Categories`, `Masters`, `MasterLocations`, `MasterServiceOffers`, `ServiceVariantSchemas`, `MasterServiceOfferVariants`, `Services`, and `Profiles`.
- `modules.repoProd`: tagged `Repo.Prod`; binds Postgres resources for the same repositories; binds `SQL.Impl[F]`, `TransactorResource`, and `PortCheck`.
- `modules.seed`: binds `BeautyQSeedLoader.ResourceLoader` and `BeautyQSeedInserter.Impl[F]`.
- `modules.seedProd`: tagged `Mode.Prod`; binds `BeautyQSeedReady.Noop[F]`.
- `modules.seedTest`: tagged `Mode.Test`; binds `BeautyQSeedReady.LoadAndInsert[F]`.
- `modules.configs`: reads `PostgresCfg` from `postgres` config.
- `modules.prodConfigs`: tagged `Scene.Provided`; reads `PostgresPortCfg` from `postgres` config.

Plugin files:

- `bifunctor-tagless/src/main/scala/leaderboard/plugins/PostgresDockerPlugin.scala`: present; not deeply inspected in this pass beyond existence.
- `bifunctor-tagless/src/main/scala/leaderboard/plugins/ElasticsearchDockerPlugin.scala`: present; not deeply inspected in this pass beyond existence.
- `bifunctor-tagless/src/main/scala/leaderboard/plugins/QdrantDockerPlugin.scala`: present; `rg` found `QdrantDocker` and `QdrantDockerPlugin` symbols.

Test wiring in `bifunctor-tagless/src/test/scala/leaderboard/testkit/LeaderboardSpecSupport.scala`:

- `LeaderboardTest` extends `SpecZIO` and `AssertZIO`.
- Test config uses `PluginConfig.cached(packagesEnabled = Seq("leaderboard.plugins"))`.
- Activation defaults to `Scene.Managed` and `Mode.Prod`.
- `memoizationRoots` includes repo keys for `Ladder`, `Profiles`, `Categories`, `Masters`, `MasterLocations`, `MasterServiceOffers`, `ServiceVariantSchemas`, `MasterServiceOfferVariants`, and `Services`.
- `DummyTest` adds `Activation(Repo -> Repo.Dummy)`.
- `ProdTest` adds `Activation(Repo -> Repo.Prod)`.

## 4. Domain model inventory

Core model in `bifunctor-tagless/src/main/scala/leaderboard/model/package.scala`:

- `UserId`: type alias for `UUID`; used by ladder/profile.
- `ServiceId`: type alias for `UUID`.
- `MasterId`: type alias for `UUID`.
- `MasterLocationId`: type alias for `UUID`.
- `MasterServiceOfferId`: type alias for `UUID`.
- `MasterServiceOfferVariantId`: type alias for `UUID`.
- `Score`: type alias for `Long`.
- `AttributeMap[A]`: alias for `AttributeMap.Impl[A, AttributeDefinition[A]]`.
- `Service(id, categoryId, name)`: service belongs to `Category` by `categoryId`; Circe codec derived.
- `Category(id, parentId, depth, name)`: category tree model; `Category.rootCategoryId` is a fixed UUID; Circe codec derived.
- `Master(id, name)`: provider/person/business domain entity; Circe codec derived.
- `MasterServiceOffer(id, masterId, serviceId)`: link between a master and a service; Circe codec derived.
- `MasterLocation(id, masterId, name, address, lat, lon)`: location for a master with geo coordinates; Circe codec derived.

Variant model in `bifunctor-tagless/src/main/scala/leaderboard/model/MasterServiceOfferVariant.scala`:

- `MasterServiceOfferVariant`: private case class constructor with public `make` factory. Fields: `id`, `masterServiceOfferId`, `masterLocationId`, `priceFrom`, `priceTo`, `durationMin`, `attributes`.
- `MasterServiceOfferVariant.make`: validates `priceFrom >= 0`, `priceTo >= priceFrom`, and `durationMin > 0`; returns `Either[MasterServiceOfferVariantValidationError, MasterServiceOfferVariant]`.
- Attribute accessors: overloaded `getAttribute` for int, BigDecimal, enum, and boolean definitions.
- Attribute JSON shape: exposes `intAttributes`, `bigDecimalAttributes`, `enumAttributes`, `booleanAttributes`; decoder validates attribute codes and enum string codes.

Variant attributes in `bifunctor-tagless/src/main/scala/leaderboard/model/MasterServiceOfferVariantAttributes.scala`:

- `MasterServiceOfferVariantAttributes`: stores typed `AttributeMap`s for int, BigDecimal, enum, and boolean values.
- `MasterServiceOfferVariantValidationError`: includes `NegativePriceFrom`, `PriceToLessThanPriceFrom`, and `NonPositiveDurationMin` based on inspected imports/usages.

Attribute definitions in `bifunctor-tagless/src/main/scala/leaderboard/model/AttributeDefinition.scala`:

- Trait hierarchy: `AttributeDefinition[A]`, `IntAttributeDefinition`, `BigDecimalAttributeDefinition`, `BooleanAttributeDefinition`, `EnumAttributeDefinition[E <: CodedEnumValue]`.
- Int attributes: `SessionCount`, `IncludedCorrectionsCount`, `MaxClients`.
- BigDecimal attributes: `DepositAmount`, `HomeVisitSurcharge`, `MaterialsSurcharge`, `FixedDiscountAmount`.
- Boolean attributes: `WithRemoval`, `WithDesign`, `WithTinting`, `WithCorrection`.
- Enum attributes include `HairRemovalMethodAttribute`, `NailCoatingTypeAttribute`, `NailServiceTypeAttribute`, `LashServiceTypeAttribute`, `LashVolumeAttribute`, `BrowServiceTypeAttribute`, `PmuAreaAttribute`, `FacialTreatmentTypeAttribute`, and `BodyAreaAttribute`.
- `AttributeDefinition.all` is referenced by `BeautySearchSpecV1` to generate dynamic attribute fields.

Enums in `bifunctor-tagless/src/main/scala/leaderboard/model/CodedEnumValue.scala`:

- `CodedEnumValue`: enum-value abstraction with stable `intCode`/`stringCode` behavior inferred from code references.
- `NailServiceType`, `LashServiceType`, `BrowServiceType`: concrete enums found by symbol search.
- Other enum value families referenced by `AttributeDefinition`: `HairRemovalMethod`, `NailCoatingType`, `LashVolume`, `PmuArea`, `FacialTreatmentType`, `BodyArea`.

Service schema model in `bifunctor-tagless/src/main/scala/leaderboard/model/ServiceVariantSchema.scala`:

- `ServiceVariantSchemaItem(attribute, required)`: schema item for an attribute and required flag.
- `ServiceVariantSchema(serviceId, items)`: schema for allowed/required variant attributes per service.
- `ServiceVariantSchemaValidationError`: includes disallowed and missing-required errors based on symbol search.

Other model files:

- `bifunctor-tagless/src/main/scala/leaderboard/model/AttributeMap.scala`: typed attribute map implementation used by variant attributes and test fixtures.
- `bifunctor-tagless/src/main/scala/leaderboard/model/QueryFailure.scala`: application/domain operation failure model used across repos/search/seed.
- `bifunctor-tagless/src/main/scala/leaderboard/model/UserProfile.scala`: profile domain/read model used by `Profiles` and `ProfileApi`.

Relationships directly visible in code:

- `Service.categoryId` references `Category.id`; repository DDL has `services.category_id references categories(id)`.
- `MasterLocation.masterId` references `Master.id`; repository DDL has `master_locations.master_id references masters(id)`.
- `MasterServiceOffer.masterId` references `Master.id`; repository DDL has `master_service_offers.master_id references masters(id)`.
- `MasterServiceOffer.serviceId` references `Service.id`; repository DDL has `master_service_offers.service_id references services(id)`.
- `MasterServiceOfferVariant.masterServiceOfferId` references `MasterServiceOffer.id`; repository DDL has `master_service_offer_variants.master_service_offer_id references master_service_offers(id)`.
- `MasterServiceOfferVariant.masterLocationId` references `MasterLocation.id`; repository DDL has `master_service_offer_variants.master_location_id references master_locations(id)`.
- `MasterServiceOfferVariants` repository enforces that offer and location belong to the same master via `offerAndLocationMustBelongToSameMaster` in both dummy and Postgres flows.
- `ServiceVariantSchema.serviceId` references `Service.id`; repository DDL has `service_variant_schema_items.service_id references services(id)`.
- No first-class `Salon` class was found in inspected model files; provider/location concepts appear as `Master` and `MasterLocation`.
- No availability/scheduling model was found in inspected model file names or searched symbols.

## 5. API / DTO / transport model inventory

API adapter files under `bifunctor-tagless/src/main/scala/leaderboard/api`:

- `HttpApi.scala`: common API adapter trait; not deeply inspected beyond existence.
- `LadderApi.scala`: HTTP adapter for ladder endpoints.
- `ProfileApi.scala`: HTTP adapter for profile/rank endpoints.
- `CategoryApi.scala`: HTTP adapter for category endpoints; `rg` found typed `NotFound.category` handling.
- `ServiceApi.scala`: HTTP adapter for service endpoints.
- `MasterApi.scala`: HTTP adapter for master endpoints; `rg` found typed `NotFound.master` handling.
- `MasterLocationApi.scala`: HTTP adapter for master-location endpoints; `rg` found typed `NotFound.masterLocation` handling.
- `MasterServiceOfferApi.scala`: HTTP adapter for offer endpoints.
- `MasterServiceOfferVariantApi.scala`: HTTP adapter for variant endpoints; `rg` found typed `NotFound.masterServiceOfferVariant` handling.

HTTP infrastructure:

- `bifunctor-tagless/src/main/scala/leaderboard/http/HttpServer.scala`: server composition; `LeaderboardPlugin.modules.api` binds `HttpServer.Impl[F]` from resource.
- `bifunctor-tagless/src/main/scala/leaderboard/http/HttpApiFailure.scala`: typed HTTP failure model; used in API adapters and Tapir support.

Tapir endpoint definitions under `bifunctor-tagless/src/main/scala/leaderboard/http/tapir`:

- `CategoryTapirEndpoints.scala`: pure Category Tapir contracts.
- `LadderTapirEndpoints.scala`: pure Ladder Tapir contracts.
- `MasterTapirEndpoints.scala`: pure Master Tapir contracts.
- `MasterLocationTapirEndpoints.scala`: pure MasterLocation Tapir contracts.
- `MasterServiceOfferTapirEndpoints.scala`: pure MasterServiceOffer Tapir contracts.
- `MasterServiceOfferVariantTapirEndpoints.scala`: pure MasterServiceOfferVariant Tapir contracts.
- `ProfileTapirEndpoints.scala`: pure Profile Tapir contracts.
- `ServiceTapirEndpoints.scala`: pure Service Tapir contracts.
- `TapirHttpSupport.scala`: shared Tapir/http4s interpreter support.
- `HttpApiFailureTapirSupport.scala`: support for typed failure outputs.
- `LegacyJsonResponse.scala`: central legacy optional JSON response helper; `docs/http-legacy-json-contracts.md` says it encodes `Option[A]` as `Json.Null` or entity JSON.

HTTP contract tests under `bifunctor-tagless/src/test/scala/leaderboard`:

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
- `HttpContractTestSupport.scala`: shared contract-test support.

Observed route/data examples from tests/docs:

- `MasterLocationApiHttpContractSuite` asserts JSON fields `id`, `masterId`, `name`, `address`, `lat`, and `lon` for location responses.
- `LeaderboardRole.scala` scaladoc examples show routes `/category`, `/service`, `/master`, `/master-location`, `/master-service-offer`, `/master-service-offer-variant`, `/profile`, and `/ladder`.

## 6. Repository and persistence inventory

Repository interfaces and implementations under `bifunctor-tagless/src/main/scala/leaderboard/repo`:

- `Ladder[F]` in `Ladder.scala`: methods `submitScore(userId, score)` and `getScores`. Implementations: `Ladder.Dummy`, `Ladder.Postgres`. Postgres DDL creates `ladder(user_id, score)` and orders scores descending.
- `Profiles[F]` in `Profiles.scala`: methods `setProfile(userId, profile)` and `getProfile(userId)`. Implementations: `Profiles.Dummy`, `Profiles.Postgres`. Postgres DDL creates `profiles(user_id, name, description)`.
- `Categories[F]` in `Categories.scala`: methods `upsertCategory`, `getCategory`, `getChildren`. Implementations: `Categories.Dummy`, `Categories.Postgres`. Postgres DDL creates `categories(id, parent_id, depth, name)` with root-category behavior; code has `rootCategoryCannotBePersisted` and `parentNotFound`.
- `Services[F]` in `Services.scala`: methods `upsertService`, `getService`, `getServicesByCategory`. Implementations: `Services.Dummy`, `Services.Postgres`. Postgres DDL creates `services(id, category_id, name)` with FK to `categories(id)` and root-category ownership guard.
- `Masters[F]` in `Masters.scala`: methods `upsertMaster`, `getMaster`, `getMasters`. Implementations: `Masters.Dummy`, `Masters.Postgres`. Postgres DDL creates `masters(id, name)`.
- `MasterLocations[F]` in `MasterLocations.scala`: methods `upsertMasterLocation`, `getMasterLocation`, `getMasterLocationsByMaster`. Implementations: `MasterLocations.Dummy`, `MasterLocations.Postgres`. Postgres DDL creates `master_locations(id, master_id, name, address, lat, lon)` with FK to `masters(id)`.
- `MasterServiceOffers[F]` in `MasterServiceOffers.scala`: methods `upsertMasterServiceOffer`, `getMasterServiceOffer`, `getMasterServiceOffersByMaster`, `getMasterServiceOffersByService`. Implementations: `MasterServiceOffers.Dummy`, `MasterServiceOffers.Postgres`. Postgres DDL creates `master_service_offers(id, master_id, service_id)` with FKs to `masters(id)` and `services(id)`.
- `ServiceVariantSchemas[F]` in `ServiceVariantSchemas.scala`: methods `upsertServiceVariantSchema(schema)` and `getServiceVariantSchema(serviceId)`. Implementations: `ServiceVariantSchemas.Dummy`, `ServiceVariantSchemas.Postgres`. Postgres DDL creates `service_variant_schema_items(service_id, attribute_code, required)` with FK to `services(id)`.
- `MasterServiceOfferVariants[F]` in `MasterServiceOfferVariants.scala`: methods `upsertMasterServiceOfferVariant`, `getMasterServiceOfferVariant`, `getMasterServiceOfferVariantsByOffer`, and `getMasterServiceOfferVariantsByLocation`. Implementations: `MasterServiceOfferVariants.Dummy`, `MasterServiceOfferVariants.Postgres`. Postgres DDL creates `master_service_offer_variants(id, master_service_offer_id, master_location_id, price_from, price_to, duration_min)` with FKs to `master_service_offers(id)` and `master_locations(id)`.
- `MasterServiceOfferVariantAttributesRepository.scala`: helper repository for additional variant attributes. It stores numeric attributes in `master_service_offer_variant_numeric_attributes(master_service_offer_variant_id, attribute_code, value)` with FK to `master_service_offer_variants(id)`. It encodes int, BigDecimal, boolean, and enum attributes into numeric storage and decodes them back to typed maps.

Persistence/SQL infrastructure:

- `bifunctor-tagless/src/main/scala/leaderboard/sql/SQL.scala`: wraps Doobie `Transactor`; `rg` found `transactor.trans` usage.
- `bifunctor-tagless/src/main/scala/leaderboard/sql/TransactorResource.scala`: creates transactor resource and imports `PortCheck`/`ResourceCheck`.
- `bifunctor-tagless/src/main/scala/leaderboard/config/PostgresCfg.scala`: Postgres config case class.
- `bifunctor-tagless/src/main/scala/leaderboard/config/PostgresPortCfg.scala`: Postgres port config case class.
- No standalone SQL migration files were found by `find bifunctor-tagless/src -type f -name '*.sql'`; schema is embedded in repo Postgres resources.

Visible FK/order implications:

- `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md` states DDL runs inside repo resource constructors and constructor dependencies should encode FK startup order.
- `LeaderboardPlugin.modules.repoProd` binds Postgres repo resources individually; textual binding order is not itself a sequencing guarantee per docs.
- `MasterLocations.Postgres` constructor takes parent `Masters[F]` dependency according to repository source search and docs rule.
- `MasterServiceOffers.Postgres` depends on parent master/service repositories according to source search and docs rule.
- `MasterServiceOfferVariants.Postgres` depends on `MasterServiceOffers`, `MasterLocations`, and `ServiceVariantSchemas` based on inspected method signatures/usages; these preserve FK and validation dependencies.
- `Services.Postgres` depends on `Categories[F]` based on source search and docs rule.
- `ServiceVariantSchemas.Postgres` depends on `Services[F]` based on source search and docs rule.

Seed loading and fixtures:

- `BeautyQSeedData` in `bifunctor-tagless/src/main/scala/leaderboard/seed/BeautyQSeedData.scala`: case class with `categories`, `services`, `serviceVariantSchemas`, `masters`, `masterLocations`, `masterServiceOffers`, `masterServiceOfferVariants`; decoder supports `modelRecords` wrapper and validates no skipped/blocked records.
- `BeautyQSeedLoader.ResourceLoader` in `BeautyQSeedLoader.scala`: loads classpath resource `seed/wandsbek_hamburg_beauty_services_seed_ready.json`.
- `BeautyQSeedInserter.Impl` in `BeautyQSeedInserter.scala`: inserts categories, services, masters, locations, offers, schemas, and variants sequentially under a JVM `Semaphore` lock.
- `BeautyQSeedReady.Noop`: prod-mode lifecycle resource that returns ready without loading seed.
- `BeautyQSeedReady.LoadAndInsert`: test-mode lifecycle resource that loads and inserts BeautyQ seed, logging counts.
- Seed JSON metadata records counts: 5 categories, 9 services, 9 serviceVariantSchemas, 8 masters, 8 masterLocations, 30 masterServiceOffers, and 66 masterServiceOfferVariants.
- `bifunctor-tagless/src/test/scala/leaderboard/testkit/LeaderboardSpecSupport.scala`: contains `VariantTestFixtures` helpers `enumAttributeMap`, `makeVariant`, and `makeSchema`.

## 7. Search and retrieval inventory

Search model in `bifunctor-tagless/src/main/scala/leaderboard/search/BeautySearchModels.scala`:

- Category: production/current model interfaces, but production wiring unclear.
- `UserSearchInput`: query input including text and user location fields by inspected symbol search.
- `ParsedSearchIntent`: parsed intent with remaining text/constraints/soft boosts by inferred usage.
- `BeautySearchAppliedFilter`, `BeautySearchFacetValue`, `BeautySearchFacet`: response filter/facet DTOs.
- `VariantSearchResult`: variant carousel result projection.
- `ProviderSearchResult`: provider/location carousel result projection.
- `ServiceIntentSearchResult`: service-intent carousel result projection.
- `BeautySearchResponse`: aggregate response with result carousels/facets.
- `BeautySearchBackend[F]`: backend interface with `search(input, intent)`.
- `BeautySearchService[F]`: service interface with `search(input)`.
- `BeautySearchService.Impl`: parses input and delegates to backend according to symbol search; pass 2 should inspect exact behavior and wiring.

Search document/snapshot code in `leaderboard.search.document`:

- Category: production/current pure document model and loaders; seed/repository loaders have different runtime implications.
- `BeautySearchCatalogSnapshot`: snapshot containing categories/services/schemas/masters/locations/offers/variants; `fromSeedData(seed)` exists.
- `VariantSearchDocument`: flattened read model for `MasterServiceOfferVariant`; docs say it contains identifiers, display fields, geo point, numeric fields, typed attributes, and denormalized text fields.
- `VariantSearchDocumentBuilder.build(snapshot)`: builds documents and fails broken joins with `QueryFailure` according to docs and symbol names `missingJoin`, `validateAgainstSchema`.
- `BeautySearchCatalogSnapshotLoader[F]`: trait with `load()`.
- `BeautySearchCatalogSnapshotLoader.FromSeed`: inferred from source symbol locations around `load()` and `BeautySearchCatalogSnapshot.fromSeedData`; exact class name should be rechecked in pass 2.
- `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories`: source symbol search around repository loader methods. This path should depend directly on `BeautyQSeedReady` before repository reads.
- `VariantSearchDocumentSnapshotProvider[F]`: trait with `loadSnapshot()`.
- `InMemoryVariantSearchDocumentSnapshotProvider`: fake/test/in-memory provider backed by a list.

Search DSL in `leaderboard.search.dsl`:

- Category: production/current pure spec, but backend wiring status unclear.
- `VectorDistance`: `Cosine`, `Dot`, `Euclidean`.
- `EmbeddingSpec[A]`: embedding text spec for documents.
- `VectorSearchSpec`: vector search request/spec model.
- `SearchFieldKind`: `Text`, `Keyword`, `Integer`, `Decimal`, `Boolean`, `GeoPoint`.
- `SearchGeoPoint(lat, lon)`: geo point value.
- `SearchValue`: typed values `Text`, `Keyword`, `Integer`, `Decimal`, `Boolean`, `GeoPoint`.
- `SearchFieldSemantic`: semantic tags include variant/offer/location/master/service/category ids and names, price, duration, location, text fields, and dynamic attribute semantic tags.
- `SearchField[A]`: document field metadata.
- `SearchDocumentSpec[A]`: field collection with `fieldByPath` and `fieldBySemantic` helpers.
- `SearchConstraint`: includes `ServiceAny`, `CategoryAny`, `EnumAttr`, `BoolAttr`, `IntRange`, `DecimalRange`, `PriceRange`, `DurationRange`, `NearUser`.
- `SearchSynonym`: dictionary/synonym item.
- `FacetFieldMode`, `FacetRangeBucket`, `FacetField`, `FacetSpec`: facet specification model.
- `TextOperator`: `And`, `Or`.
- `SearchRequestSpec`: backend request knobs such as hit window, text operator, aggregation size, and geo decay according to docs/source.
- `RankingSpec`: weights for ranking.
- `CarouselSpec`: carousel grouping/limits/ranking config.
- `BeautySearchSpec`: aggregate search spec.
- `BeautySearchSpecV1`: canonical BeautyQ spec. Source includes `carouselSpec`, geo fields `lat`/`lon`, dynamic fields from `AttributeDefinition.all`, and dictionary helpers `servicePhrase`, `phrase`, `enumConstraint`, `boolConstraint`.

Parser/interpreter code:

- `leaderboard.search.parser.BeautySearchIntentParser`: production/current pure parser driven by DSL synonyms; pass 2 should inspect exact matching behavior.
- `leaderboard.search.interpreter.SearchEmbeddingTextExtractor`: generic-ish support; extracts embedding text from document spec and embedding spec.
- `SearchResponseAssembler`: assembles search response; exact behavior needs pass 2.
- `SearchSpecSupport`: helper support for specs.

Elasticsearch code in `leaderboard.search.elasticsearch`:

- Category: production/current pure interpreters; runtime client/wiring unclear in this pass.
- `ElasticsearchMappingInterpreter.mapping(spec)`: builds mapping from `BeautySearchSpec` fields.
- `ElasticsearchIngestionInterpreter.bulkPayload` and `sourceJson`: builds ingestion payload/source JSON from documents/spec fields.
- `ElasticsearchSearchRequestInterpreter.request(input, intent, spec)`: builds ES request; source contains `textQuery`, `aggregations`, `facetAggregation`, `geoQuery`, `constraintClause`, `softBoostClause`, `termClause`, `termsClause`, `rangeClause`, and `boolQuery`.
- `ElasticsearchSearchResponseInterpreter.interpret`: maps ES JSON response to `BeautySearchResponse`.
- `ElasticsearchSearchResponseInterpreter.documentHits`: extracts lexical document hits; used by generic lexical seam.
- Diagnostics: search terms included `matched_queries`; pass 1 did not find a prominent production symbol in the truncated output, so pass 2 should explicitly inspect ES response/request for matched-query handling.

In-memory backend:

- Category: fake/test or pure regression backend.
- `leaderboard.search.inmemory.InMemorySearchBackend`: implements `BeautySearchBackend[F]`; methods `search` and `filterAndScore` found. Docs describe it as pure in-memory backend for regression tests.

Generic lexical/semantic retrieval seams:

- Category: generic current seams used by tests/non-production hybrid.
- `leaderboard.search.lexical.LexicalDocumentBackend`: generic lexical document backend/hit seam. Exact symbols truncated in output but test names `GenericLexicalDocumentBackendSpec` and docs mention it.
- `leaderboard.search.semantic.SemanticDocumentHit`: generic semantic hit model.
- `SemanticDocumentLookup[F, Id, Doc]`: lookup by ids.
- `SemanticCandidateAssembler[Id, Doc, Candidate]`: assembles candidates from hits and documents.
- `SemanticResponseProjector[Assembly, Response]`: projects assembly into response.
- `SemanticCandidateAssembler.assembleUnique`: unique assembly helper.
- `SemanticCandidateHit`: variant semantic hit.
- `SemanticDocumentBackend[F, Id]`: returns semantic document hits.
- `SemanticCandidateBackend[F]`: returns semantic candidate hits.
- `VariantSearchDocumentLookup[F]`: lookup for variant documents by `MasterServiceOfferVariantId`.
- `InMemoryVariantSearchDocumentLookup`: fake/in-memory lookup backed by documents.

Hybrid code in `leaderboard.search.hybrid`:

- Category: non-production/experimental unless otherwise proven.
- `BeautyQHybridProjectionPolicy`: domain-specific projection/merge policy. It has `lexicalFirstSemanticSupplement`; docs state lexical-first semantic-supplement ordering and no score fusion/reranking.
- `BeautyQHybridVariantProjection`: projects selected variant ids to `VariantSearchDocument` candidates.
- `BeautyQHybridProviderServiceProjection`: projects provider/service intent candidates from hybrid variant candidates.
- `BeautyQHybridResponseAdapter`: builds `BeautySearchResponse` from hybrid projections; includes `BeautyQHybridDisplayScorePolicy.LexicalThenSemantic` and carousel limits.
- `BeautyQHybridResponsePipeline.projectResponse`: combines hybrid policy/projection/adapter steps.
- `HybridDocumentRetrievalResult`: generic hybrid retrieval container with diagnostics; docs state it is not a ranking policy.
- `BeautyQNonProductionHybridExperimentActivation`: activation/config model. It has `Disabled`, `Enabled`, `buildIfEnabled`, `ManualTask`, `TestSetup`, `LocalExperiment`, and `ExplicitInvocationOnly` based on symbol search.
- `BeautyQNonProductionHybridResponseExperiment`: non-production experiment class with `search`.
- `ExperimentalBeautySearchService`: experimental service with `search(input, metadata)` and `diagnose`.
- `ExperimentalHybridRouteDecider`: route decision helper.
- `ExperimentalHybridRouteDiagnostics`: diagnostics model.
- `ExperimentalHybridSearchBackend`: experimental backend implementing `search`.

Routing code:

- Category: experimental/non-production or unclear production status.
- `SearchBackendRoute`: `ElasticsearchOnly`, `QdrantCandidateRoute`, `ElasticsearchThenQdrantFallback`.
- `SearchRoutingReason`: `ExplicitConstraints`, `LexicalIntent`, `HardNegativeOrNoiseGuard`, `BroadSemanticCandidate`, `FallbackNotEnabled`.
- `SearchRoutingSignal`: `BroadSemanticCandidate`, `HardNegativeOrNoiseGuard`.
- `SearchRoutingMetadata` and `SearchRoutingDecision`.
- `SearchBackendRouter.decide`: routing decision implementation. Residual text alone should not route to Qdrant, and hard negatives/noise should not route to Qdrant because of residual text; pass 2 should verify implementation against tests.

Qdrant/vector code in `leaderboard.search.qdrant`:

- Category: non-production/manual/local/test unless separately proven.
- `QdrantClient`: HTTP client wrapper with `QdrantSearchHit` model.
- `QdrantSearchClient` and `QdrantClientSearchAdapter`: search adapter around `QdrantClient`.
- `QdrantPointUpsertClient` and `QdrantClientPointUpsertAdapter`: point upsert adapter.
- `QdrantCollectionInfoClient` and `QdrantClientCollectionInfoAdapter`: collection info adapter.
- `QdrantJsonInterpreter`: creates collection JSON, search request JSON, and upsert point JSON.
- `QdrantPointId`: validates Qdrant-compatible point ids; supports UUID and unsigned long.
- `QdrantDocumentPointBuilder[A]`: generic point-id/payload builder.
- `QdrantSearchDocumentIndexer[A]`: generic document indexer that embeds and upserts documents.
- `QdrantVariantDocumentPointBuilder`: variant-specific point builder; docs/rules say arbitrary domain ids belong in payload.
- `QdrantVariantDocumentIndexer`: variant-specific document indexer.
- `QdrantVariantDocumentSnapshotIndexer`: indexes snapshot; has compatibility-guarded indexing path and returns `QdrantSnapshotIndexingResult`.
- `QdrantCandidateHitDecoder`: decodes Qdrant hits into candidate hits.
- `QdrantSemanticCandidateSearch`: embeds query text and searches Qdrant.
- `QdrantSemanticCandidateBackend`: semantic candidate backend over Qdrant.
- `QdrantCandidateAssembler`: builds variant/provider/service candidate groups from Qdrant hits/documents.
- `QdrantCandidateResponseProjector`: projects Qdrant candidate assembly into response.
- `QdrantCollectionIdentity`: renders collection names and compatibility expectations; mismatch types include collection name, vector name, dimension, distance, and embedding model.
- `QdrantCollectionReadinessConfig`: derives collection readiness config from input.
- `QdrantCollectionInfoDecoder`, `QdrantCollectionCompatibilityValidator`, `QdrantCollectionCompatibilityChecker`, `QdrantCollectionCompatibilityGuard`: readiness/compatibility stack.
- `QdrantNonProductionExperimentActivation`: activation/config with `Disabled`, `Enabled`, indexing triggers `ManualTask` and `TestSetup`, and metadata source `ExplicitMetadataOnly`.
- `QdrantNonProductionExperimentComposition`: non-production composition with `indexSnapshot()`.
- `QdrantNonProductionHybridExperiment`: non-production hybrid experiment with `indexSnapshot`, `search`, and `diagnose`.

Embedding code:

- Category: manual/local/non-production unless separately proven.
- `EmbeddingClient`: trait with `embed(text): IO[QueryFailure, Vector[Double]]`.
- `LlamaCppEmbeddingClient`: HTTP embedding client with `LlamaCppEmbeddingClientConfig`; local docs describe manual `llama-server` operation and env var `LLAMA_CPP_EMBEDDING_URL`.

Benchmark/eval code:

- Category: benchmark/test/manual decision support.
- `leaderboard.search.eval.BeautySearchEval`: eval suite/query/expectation/report/scorer models. Includes `BeautySearchEvalLoader.load(path)` and `BeautySearchEvalScorer.score`.
- `QdrantEmbeddingBenchmark`: candidate/run-mode/plan/expected/query-result/metrics/aggregate/candidate-report/comparison/report models and metrics helpers.
- `QdrantEmbeddingBenchmarkRunner`: validates and runs benchmark plans. Validation methods found for candidate count, distinct candidate ids, candidate results, expected query ids, distinct result query ids, and result coverage.
- `QdrantEmbeddingBenchmarkCandidateExecutor`: real-resource benchmark executor abstractions and Qdrant-backed executor. It includes collection create/delete client, composition factory, executor config, `runCandidate`, and cleanup.
- `QdrantEmbeddingBenchmarkDecisionPolicy`: produces benchmark decision verdicts such as `KeepBaseline`, `CandidateWorthFurtherEvaluation`, `CandidateWorthSwitching`, and `CandidateRejected`; benchmark output is decision support, not production automation.
- `QdrantEmbeddingBenchmarkQuerySubset`: selects explicit query subsets.
- `QdrantEmbeddingBenchmarkReportFormatter`, `QdrantEmbeddingBenchmarkReportJson`, `QdrantEmbeddingBenchmarkSavedReportComparison`: formatting, JSON, and saved report comparison.

Search tests strongly shaping contracts:

- Pure/eval: `BeautySearchPureSpec`, `BeautySearchEvalInventory`, `BeautySearchEvalTestSupport`.
- Elasticsearch: `BeautySearchElasticsearchIntegrationSpec`, `ElasticsearchSearchResponseInterpreterSpec`, `ElasticsearchTestClient`.
- Generic retrieval: `GenericLexicalDocumentBackendSpec`, `GenericSemanticDocumentBackendSpec`, `GenericSemanticCandidateAssemblerSpec`, `GenericSemanticResponseProjectorSpec`, `GenericHybridDocumentRetrievalSpec`, `HybridGenericSecondDomainProofSpec`.
- Hybrid: `BeautyQHybridProjectionPolicySpec`, `BeautyQHybridVariantProjectionSpec`, `BeautyQHybridProviderServiceProjectionSpec`, `BeautyQHybridResponseAdapterSpec`, `BeautyQHybridResponsePipelineSpec`, `BeautyQNonProductionHybridExperimentActivationSpec`, `BeautyQNonProductionHybridExperimentModuleGatingSpec`, `BeautyQNonProductionHybridResponseExperimentSpec`, `ExperimentalHybridRouteDiagnosticsSpec`.
- Qdrant: numerous specs listed in section 8, including compatibility, indexing, semantic candidate, smoke, and benchmark specs.

Status caution:

- Existing docs explicitly state production hybrid is not implemented and Qdrant/hybrid paths are non-production/manual/local/test boundaries. This inventory therefore does not classify Qdrant or hybrid as production-ready.
- `BeautySearchService` exists in code and should not be assumed replaced by hybrid or Qdrant without explicit wiring evidence.

## 8. Tests inventory

Repository/domain tests:

- `bifunctor-tagless/src/test/scala/leaderboard/catalog/CatalogRepositoriesSpec.scala`: repository tests for categories, services, masters, master locations, and master service offers; includes dummy and Postgres classes such as `MastersTestDummy`, `MastersTestPostgres`, `ServicesTestDummy`, `ServicesTestPostgres`.
- `bifunctor-tagless/src/test/scala/leaderboard/ranking/RankingSpec.scala`: ladder/profile/ranks tests with dummy/Postgres variants such as `LadderTestPostgres`, `ProfilesTestPostgres`, `RanksTestPostgres`.
- `bifunctor-tagless/src/test/scala/leaderboard/variants/MasterServiceOfferVariantsSpec.scala`: variant repository/domain tests.
- `bifunctor-tagless/src/test/scala/leaderboard/attributes/AttributeDefinitionSpec.scala`: attribute definition tests.
- `bifunctor-tagless/src/test/scala/leaderboard/attributes/CodedEnumValueSpec.scala`: enum value tests.
- `bifunctor-tagless/src/test/scala/leaderboard/attributes/VariantAttributeJsonSpec.scala`: variant attribute JSON tests.
- `bifunctor-tagless/src/test/scala/leaderboard/attributes/VariantAttributeSchemaSpec.scala`: service variant schema tests.
- `bifunctor-tagless/src/test/scala/leaderboard/attributes/VariantAttributeStorageSpec.scala`: variant attribute storage tests.
- `bifunctor-tagless/src/test/scala/leaderboard/seed/BeautyQSeedSpec.scala`: seed loading/insertion tests.

HTTP/API contract tests:

- `CategoryApiHttpContractSuite.scala`: category route contract tests.
- `ServiceApiHttpContractSuite.scala`: service route contract tests.
- `MasterApiHttpContractSuite.scala`: master route contract tests.
- `MasterLocationApiHttpContractSuite.scala`: master-location route contract tests; asserts `lat`/`lon` JSON.
- `MasterServiceOfferApiHttpContractSuite.scala`: offer route contract tests.
- `MasterServiceOfferVariantApiHttpContractSuite.scala`: variant route contract tests.
- `LadderApiHttpContractSuite.scala`: ladder route contract tests.
- `ProfileApiHttpContractSuite.scala`: profile route contract tests.
- `LegacySingleEntityGetHttpContractSuite.scala`: legacy single-entity GET contract tests.
- `TapirHttpSupportContractSuite.scala`: shared Tapir support contract tests.
- `HttpContractTestSupport.scala`: shared support.

Wiring/testkit:

- `WiringTest.scala`: `class WiringTest extends SpecWiring(GenericLauncher)`.
- `testkit/LeaderboardSpecSupport.scala`: base Distage test config, memoization roots, dummy/prod activations, and variant fixtures.
- `zioenv.scala`: ZIO environment support file.
- `Rnd.scala`: random test helper.

Search pure/eval/Elasticsearch tests:

- `BeautySearchPureSpec.scala`: pure Beauty search/eval tests. `rg` found repeated calls to `BeautySearchEvalTestSupport.assertEvalOutcome`.
- `BeautySearchEvalInventory.scala`: eval inventory/test data source.
- `BeautySearchEvalTestSupport.scala`: eval assertion support.
- `BeautySearchElasticsearchIntegrationSpec.scala`: Elasticsearch integration eval tests; `rg` found `requireEvalOutcome` calls.
- `ElasticsearchSearchResponseInterpreterSpec.scala`: ES response interpreter tests.
- `ElasticsearchTestClient.scala`: test client.

Search generic/hybrid tests:

- `GenericHybridDocumentRetrievalSpec.scala`
- `GenericLexicalDocumentBackendSpec.scala`
- `GenericSemanticCandidateAssemblerSpec.scala`
- `GenericSemanticDocumentBackendSpec.scala`
- `GenericSemanticResponseProjectorSpec.scala`
- `HybridGenericSecondDomainProofSpec.scala`: proves a second-domain hybrid example according to test name and `rg` output.
- `BeautyQHybridProjectionPolicySpec.scala`
- `BeautyQHybridVariantProjectionSpec.scala`
- `BeautyQHybridProviderServiceProjectionSpec.scala`
- `BeautyQHybridResponseAdapterSpec.scala`
- `BeautyQHybridResponsePipelineSpec.scala`
- `BeautyQNonProductionHybridExperimentActivationSpec.scala`
- `BeautyQNonProductionHybridExperimentModuleGatingSpec.scala`
- `BeautyQNonProductionHybridResponseExperimentSpec.scala`
- `ExperimentalHybridRouteDiagnosticsSpec.scala`

Qdrant/vector tests:

- `QdrantCandidateAssemblerSpec.scala`
- `QdrantCandidateResponseProjectorSpec.scala`
- `QdrantCollectionCompatibilityCheckerSpec.scala`
- `QdrantCollectionCompatibilityGuardSpec.scala`
- `QdrantCollectionCompatibilityIntegrationSpec.scala`
- `QdrantCollectionCompatibilityValidatorSpec.scala`
- `QdrantCollectionIdentitySpec.scala`
- `QdrantCollectionInfoDecoderSpec.scala`
- `QdrantCollectionReadinessConfigSpec.scala`
- `QdrantDockerSmokeSpec.scala`: smoke test, likely requires Docker/Qdrant based on name.
- `QdrantExperimentalHybridServiceIntegrationSpec.scala`
- `QdrantLlamaCppRetrievalSmokeSpec.scala`: smoke test, likely requires local llama.cpp/Qdrant based on name/docs.
- `QdrantNonProductionExperimentActivationSpec.scala`
- `QdrantNonProductionExperimentCompositionSpec.scala`
- `QdrantNonProductionHybridExperimentSpec.scala`
- `QdrantSearchDocumentIndexerSpec.scala`
- `QdrantSemanticCandidateBackendSpec.scala`
- `QdrantSemanticCandidateEvalSpec.scala`: AGENTS lists env-gated run with `LLAMA_CPP_EMBEDDING_URL` and optional `QDRANT_SEMANTIC_QUALITY_ASSERTIONS=true`.
- `QdrantSemanticCandidateSearchSpec.scala`
- `QdrantSnapshotIndexingCompatibilityIntegrationSpec.scala`
- `QdrantTestClient.scala`
- `QdrantVariantDocumentIndexerSpec.scala`
- `QdrantVariantDocumentPointBuilderSpec.scala`
- `QdrantVariantDocumentSnapshotIndexerSpec.scala`
- `VariantSearchDocumentSnapshotProviderSpec.scala`

Qdrant benchmark tests:

- `QdrantEmbeddingBenchmarkSpec.scala`
- `QdrantEmbeddingBenchmarkDecisionPolicySpec.scala`
- `QdrantEmbeddingBenchmarkExecutorSpec.scala`
- `QdrantEmbeddingBenchmarkExecutorIntegrationSpec.scala`
- `QdrantEmbeddingBenchmarkQuerySubsetSpec.scala`
- `QdrantEmbeddingBenchmarkReportJsonSpec.scala`
- `QdrantEmbeddingBenchmarkRunnerSpec.scala`
- `QdrantEmbeddingBenchmarkSavedReportComparisonSpec.scala`
- `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.scala`: manual by name.

Manual/local/ignored tests:

- `LlamaCppEmbeddingSmokeSpec.scala`: smoke by name, likely local/manual.
- `QdrantDockerSmokeSpec.scala`: smoke by name, likely Docker/manual/integration.
- `QdrantLlamaCppRetrievalSmokeSpec.scala`: smoke by name, likely local/manual.
- `QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.scala`: manual by name.
- This pass did not fully inspect ScalaTest `ignore`/tag annotations; pass 2 should classify exact disabled/ignored behavior from test bodies.

Architectural/contract tests observed by name/evidence:

- Route-level HTTP contract suites are architectural contracts for current API behavior.
- Repository specs with dummy and Postgres variants are repository/persistence contracts.
- `BeautySearchPureSpec` and `BeautySearchElasticsearchIntegrationSpec` are search-eval contracts.
- Qdrant compatibility/readiness specs are lifecycle/collection-identity contracts.
- Non-production activation/module gating specs are contracts that experiments remain gated/disabled by default.
- Benchmark runner/decision/report specs are benchmark integrity contracts.

## 9. Important symbols index

Package `leaderboard.model`:

- `Service`: `bifunctor-tagless/src/main/scala/leaderboard/model/package.scala`; category: domain; responsibility: service entity linked to category; collaborators: `Services`, `ServiceApi`, `ServiceVariantSchema`, search documents.
- `Category`: `model/package.scala`; category: domain; responsibility: category tree node with root id; collaborators: `Categories`, `Services`, search spec/documents.
- `Master`: `model/package.scala`; category: domain; responsibility: provider/master entity; collaborators: `Masters`, `MasterLocation`, `MasterServiceOffer`.
- `MasterLocation`: `model/package.scala`; category: domain; responsibility: provider location with address and lat/lon; collaborators: `MasterLocations`, search provider carousel.
- `MasterServiceOffer`: `model/package.scala`; category: domain; responsibility: master-service relationship; collaborators: `MasterServiceOffers`, `MasterServiceOfferVariant`.
- `MasterServiceOfferVariant`: `model/MasterServiceOfferVariant.scala`; category: domain; responsibility: purchasable/search-result unit with offer, location, price, duration, attributes; collaborators: `MasterServiceOfferVariants`, `VariantSearchDocument`.
- `MasterServiceOfferVariant.make`: `model/MasterServiceOfferVariant.scala`; category: domain; responsibility: invariant-enforcing factory for price/duration.
- `MasterServiceOfferVariantAttributes`: `model/MasterServiceOfferVariantAttributes.scala`; category: domain; responsibility: typed attribute maps.
- `AttributeDefinition`: `model/AttributeDefinition.scala`; category: domain; responsibility: typed catalog of allowed variant attributes; collaborators: schemas, variant JSON/storage, search spec fields.
- `CodedEnumValue`: `model/CodedEnumValue.scala`; category: domain; responsibility: stable enum coding for attributes.
- `ServiceVariantSchema`: `model/ServiceVariantSchema.scala`; category: domain; responsibility: allowed/required attributes per service.
- `QueryFailure`: `model/QueryFailure.scala`; category: utility/domain error; responsibility: typed failures used across repos/search/seed/API.
- `UserProfile`: `model/UserProfile.scala`; category: domain/read model; responsibility: profile data for profile/rank API.

Package `leaderboard.repo`:

- `Ladder`: `repo/Ladder.scala`; category: repository; responsibility: score storage.
- `Profiles`: `repo/Profiles.scala`; category: repository; responsibility: profile storage.
- `Categories`: `repo/Categories.scala`; category: repository/persistence; responsibility: category CRUD/tree children; validates root/parent.
- `Services`: `repo/Services.scala`; category: repository/persistence; responsibility: service CRUD by category; validates category/root.
- `Masters`: `repo/Masters.scala`; category: repository/persistence; responsibility: master CRUD/list.
- `MasterLocations`: `repo/MasterLocations.scala`; category: repository/persistence; responsibility: location CRUD by master; validates master existence.
- `MasterServiceOffers`: `repo/MasterServiceOffers.scala`; category: repository/persistence; responsibility: offer CRUD by master/service; validates master/service existence.
- `ServiceVariantSchemas`: `repo/ServiceVariantSchemas.scala`; category: repository/persistence; responsibility: allowed/required service attribute schemas.
- `MasterServiceOfferVariants`: `repo/MasterServiceOfferVariants.scala`; category: repository/persistence; responsibility: variant CRUD by offer/location; validates offer/location same master and schema attributes.
- `MasterServiceOfferVariantAttributesRepository`: `repo/MasterServiceOfferVariantAttributesRepository.scala`; category: persistence; responsibility: encode/decode/store variant additional attributes in numeric table.

Package `leaderboard.seed`:

- `BeautyQSeedData`: `seed/BeautyQSeedData.scala`; category: seed; responsibility: decoded seed aggregate.
- `BeautyQSeedLoader`: `seed/BeautyQSeedLoader.scala`; category: seed; responsibility: load seed resource.
- `BeautyQSeedInserter`: `seed/BeautyQSeedInserter.scala`; category: seed/persistence; responsibility: sequentially insert seed into repositories.
- `BeautyQSeedReady`: `seed/BeautyQSeedInserter.scala`; category: wiring/seed; responsibility: Distage lifecycle signal for seed readiness.

Package `leaderboard.api` and `leaderboard.http.tapir`:

- `HttpApi`: `api/HttpApi.scala`; category: API; responsibility: common HTTP API abstraction.
- `CategoryApi`, `ServiceApi`, `MasterApi`, `MasterLocationApi`, `MasterServiceOfferApi`, `MasterServiceOfferVariantApi`, `LadderApi`, `ProfileApi`: `api/*.scala`; category: API; responsibility: thin HTTP adapters over repos/services and Tapir endpoints.
- `*TapirEndpoints`: `http/tapir/*.scala`; category: API/DTO/transport; responsibility: pure endpoint contracts.
- `TapirHttpSupport`: `http/tapir/TapirHttpSupport.scala`; category: API utility; responsibility: route interpretation/support.
- `HttpApiFailure`: `http/HttpApiFailure.scala`; category: API DTO/error; responsibility: typed HTTP failures.
- `LegacyJsonResponse`: `http/tapir/LegacyJsonResponse.scala`; category: API compatibility utility; responsibility: optional raw JSON compatibility helper.

Package `leaderboard.search`:

- `UserSearchInput`: `search/BeautySearchModels.scala`; category: search; responsibility: user query/location input.
- `ParsedSearchIntent`: `search/BeautySearchModels.scala`; category: search; responsibility: parsed query intent.
- `BeautySearchResponse`: `search/BeautySearchModels.scala`; category: search/API DTO; responsibility: response aggregate with carousels/facets.
- `VariantSearchResult`: `search/BeautySearchModels.scala`; category: search/API DTO; responsibility: variant result projection.
- `ProviderSearchResult`: `search/BeautySearchModels.scala`; category: search/API DTO; responsibility: provider/location carousel projection.
- `ServiceIntentSearchResult`: `search/BeautySearchModels.scala`; category: search/API DTO; responsibility: service intent carousel projection.
- `BeautySearchBackend`: `search/BeautySearchModels.scala`; category: search; responsibility: backend interface.
- `BeautySearchService`: `search/BeautySearchModels.scala`; category: search; responsibility: service interface/parser-to-backend boundary.

Package `leaderboard.search.document`:

- `BeautySearchCatalogSnapshot`: `search/document/VariantSearchDocument.scala`; category: search; responsibility: joined catalog source snapshot.
- `VariantSearchDocument`: `search/document/VariantSearchDocument.scala`; category: search; responsibility: flattened variant search read model.
- `VariantSearchDocumentBuilder`: `search/document/VariantSearchDocument.scala`; category: search; responsibility: pure builder from snapshot to documents.
- `BeautySearchCatalogSnapshotLoader`: `search/document/VariantSearchDocument.scala`; category: search/persistence boundary; responsibility: load search catalog snapshot from seed/repos.
- `VariantSearchDocumentSnapshotProvider`: `search/document/VariantSearchDocumentSnapshotProvider.scala`; category: search; responsibility: load document list snapshot.

Package `leaderboard.search.dsl`:

- `BeautySearchSpecV1`: `search/dsl/BeautySearchSpecV1.scala`; category: search; responsibility: canonical BeautyQ search spec, fields, facets, ranking, dictionary.
- `BeautySearchSpec`, `SearchDocumentSpec`, `SearchField`, `SearchConstraint`, `SearchSynonym`, `FacetSpec`, `SearchRequestSpec`, `RankingSpec`, `CarouselSpec`: `search/dsl/SearchDsl.scala`; category: search; responsibility: immutable DSL/spec model.
- `VectorSearchSpec`, `EmbeddingSpec`, `VectorDistance`: `search/dsl/SearchDsl.scala`; category: search/vector; responsibility: vector search spec model.

Package `leaderboard.search.elasticsearch`:

- `ElasticsearchMappingInterpreter`: `search/elasticsearch/ElasticsearchMappingInterpreter.scala`; category: search/Elasticsearch; responsibility: spec-to-mapping JSON.
- `ElasticsearchIngestionInterpreter`: `search/elasticsearch/ElasticsearchIngestionInterpreter.scala`; category: search/Elasticsearch; responsibility: documents-to-bulk/source JSON.
- `ElasticsearchSearchRequestInterpreter`: `search/elasticsearch/ElasticsearchSearchRequestInterpreter.scala`; category: search/Elasticsearch; responsibility: input/intent/spec-to-search-request JSON.
- `ElasticsearchSearchResponseInterpreter`: `search/elasticsearch/ElasticsearchSearchResponseInterpreter.scala`; category: search/Elasticsearch; responsibility: ES response JSON to `BeautySearchResponse` and lexical hits.

Package `leaderboard.search.qdrant`:

- `QdrantClient`: `search/qdrant/QdrantClient.scala`; category: search/Qdrant/manual-local; responsibility: HTTP Qdrant client.
- `QdrantJsonInterpreter`: `search/qdrant/QdrantJsonInterpreter.scala`; category: search/Qdrant; responsibility: collection/search/upsert JSON.
- `QdrantPointId`: `search/qdrant/QdrantPointId.scala`; category: search/Qdrant; responsibility: Qdrant-compatible point-id validation.
- `QdrantSearchDocumentIndexer`, `QdrantVariantDocumentIndexer`, `QdrantVariantDocumentSnapshotIndexer`: `search/qdrant/*.scala`; category: search/Qdrant/non-production; responsibility: vector indexing.
- `QdrantSemanticCandidateSearch`, `QdrantSemanticCandidateBackend`: `search/qdrant/*.scala`; category: search/Qdrant/non-production; responsibility: semantic candidate retrieval.
- `QdrantCollectionIdentity`, `QdrantCollectionReadinessConfig`, `QdrantCollectionCompatibility*`: `search/qdrant/*.scala`; category: search/Qdrant lifecycle; responsibility: collection naming/readiness/compatibility checks.
- `QdrantNonProductionExperimentActivation`, `QdrantNonProductionExperimentComposition`, `QdrantNonProductionHybridExperiment`: `search/qdrant/*.scala`; category: search/non-production; responsibility: gated experiment composition.
- `QdrantEmbeddingBenchmark*`: `search/qdrant/*.scala`; category: benchmark; responsibility: embedding benchmark plans, metrics, runners, decisions, reports.

Package `leaderboard.search.hybrid`:

- `HybridDocumentRetrievalResult`: `search/hybrid/HybridDocumentRetrievalResult.scala`; category: search/hybrid; responsibility: generic retrieval container/diagnostics.
- `BeautyQHybridProjectionPolicy`: `search/hybrid/BeautyQHybridProjectionPolicy.scala`; category: search/hybrid/domain policy; responsibility: lexical-first semantic supplement policy.
- `BeautyQHybridVariantProjection`, `BeautyQHybridProviderServiceProjection`, `BeautyQHybridResponseAdapter`, `BeautyQHybridResponsePipeline`: `search/hybrid/*.scala`; category: search/hybrid; responsibility: projection and response assembly for experiments.
- `BeautyQNonProductionHybridExperimentActivation`, `BeautyQNonProductionHybridResponseExperiment`: `search/hybrid/*.scala`; category: search/non-production; responsibility: gated hybrid response experiment.
- `ExperimentalBeautySearchService`, `ExperimentalHybridSearchBackend`, `ExperimentalHybridRouteDecider`, `ExperimentalHybridRouteDiagnostics`: `search/hybrid/*.scala`; category: search/experimental; responsibility: experimental route/backend diagnostics.

Package `leaderboard.search.semantic` and `leaderboard.search.lexical`:

- `SemanticDocumentHit`, `SemanticDocumentLookup`, `SemanticCandidateAssembler`, `SemanticResponseProjector`, `SemanticDocumentBackend`, `SemanticCandidateBackend`, `VariantSearchDocumentLookup`: `search/semantic/*.scala`; category: search/generic seam; responsibility: semantic retrieval abstractions.
- `LexicalDocumentBackend`: `search/lexical/LexicalDocumentBackend.scala`; category: search/generic seam; responsibility: lexical retrieval abstraction.

Package `leaderboard.plugins`/wiring:

- `LeaderboardPlugin`: `plugins/LeaderboardPlugin.scala`; category: wiring; responsibility: roles, API, repo, seed, config bindings.
- `PostgresDockerPlugin`, `ElasticsearchDockerPlugin`, `QdrantDockerPlugin`: `plugins/*.scala`; category: wiring/test/runtime resources; responsibility: Docker/resource plugin definitions.
- `MainBase` and `Main*` launchers: `LeaderboardRole.scala`; category: wiring/runtime entrypoint; responsibility: Distage role launcher configurations.

## 10. Potential architectural boundaries observed

Supported by code/docs:

- Domain model vs transport: domain case classes live in `leaderboard.model`; HTTP route definitions live in `leaderboard.http.tapir`; adapters live in `leaderboard.api`.
- API adapter vs endpoint contracts: docs and source separate pure `*TapirEndpoints.scala` from thin `leaderboard.api.*Api` adapters.
- Repository interface vs implementation: each repo file defines a trait plus `Dummy` and `Postgres` implementations.
- Persistence schema vs migration files: no migration files found; Postgres schema is created in repo resource constructors via Doobie SQL.
- Seed decoding/loading vs insertion: `BeautyQSeedLoader` decodes resource, `BeautyQSeedInserter` writes via repositories, `BeautyQSeedReady` wires lifecycle readiness.
- Search DSL/spec vs backend interpreters: `SearchDsl.scala`/`BeautySearchSpecV1.scala` define metadata; Elasticsearch/in-memory/Qdrant interpreters consume specs.
- Search document builder vs repositories: `VariantSearchDocumentBuilder` builds flattened documents from `BeautySearchCatalogSnapshot`; loaders bridge seed/repos into snapshots.
- Elasticsearch lexical baseline vs Qdrant semantic candidate recall: docs and code packages separate ES interpreters from Qdrant candidate/indexing code.
- Generic retrieval seams vs BeautyQ projection policy: semantic/lexical/hybrid containers are generic-ish; `BeautyQHybridProjectionPolicy` and response adapters are domain-specific.
- Fake/test adapters vs real-resource adapters: `InMemorySearchBackend`, `InMemoryVariantSearchDocumentLookup`, dummy repos, and test clients are separate from Postgres/Qdrant/Elasticsearch code.
- Non-production experiment boundary: symbols named `NonProduction`, `Experimental`, activation configs, and tests document explicit gating for Qdrant/hybrid experiments.
- Benchmark/reporting vs production routing: benchmark code and docs frame decisions as decision support, not automatic production model switching.

Uncertain boundaries:

- Superseded historical pass-1 uncertainty: this pass did not find a production search API binding in `LeaderboardPlugin.modules.api`. Current source state: `POST /beauty-search` is production-exposed through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`. The exposed backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`; `seedCatalogInMemory` / `InMemorySearchBackend` remain rollback/non-default; Qdrant and hybrid remain not production-wired.
- Elasticsearch runtime boundary is partly uncertain: pure interpreters and integration tests exist, but production client/resource composition was not fully traced.
- `BeautySearchSpec` naming is domain-specific, while docs call some pieces reusable/generic; pass 2 should distinguish actual generic API from BeautyQ-named reusable code.
- Provider vs salon boundary is uncertain: code has `Master` and `MasterLocation`; requested salon concept is not first-class in inspected source.

## 11. Open questions for pass 2

- Superseded historical open question: pass 1 asked whether Beauty search had a production HTTP route or role, or was exercised only by tests/experiments. Current source state: `POST /beauty-search` is production-exposed through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`. The exposed backend is seed-resource catalog snapshot + `ElasticsearchSearchBackend`; `seedCatalogInMemory` / `InMemorySearchBackend` remain rollback/non-default; Qdrant and hybrid remain not production-wired. Evidence to inspect remains: `LeaderboardPlugin.modules.api`, all `leaderboard.api` files, and search service bindings.
- How exactly is `BeautySearchService.Impl` constructed and used? Does any production path bind Elasticsearch/in-memory/Qdrant as `BeautySearchBackend`?
- Are Elasticsearch client/index creation and ingestion wired as runtime resources, or only test/integration helpers? Inspect `ElasticsearchDockerPlugin`, integration specs, and any client classes not captured by symbol grep.
- What is the exact status of `MasterServiceOfferVariantApi` typed GET migration? Reconcile `docs/http-master-service-offer-variant-typed-get-plan.md`, `docs/http-legacy-json-contracts.md`, Tapir endpoint source, and route-level tests.
- Is `Salon` intentionally represented as `MasterLocation`, `Master`, both, or absent? Search for UI/client terminology and seed data labels.
- Is availability/scheduling absent, deferred, or represented under different names? Search for appointment/calendar/schedule/time-slot terms.
- Which Qdrant/Llama/benchmark tests are ignored, tagged, env-gated, or manually run only? Inspect ScalaTest annotations and environment checks.
- Does any production wiring construct Qdrant or heavy semantic dependencies when experiments are disabled? Inspect `BeautyQNonProductionHybridExperimentModuleGatingSpec` and module bindings.
- Does `BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories` have a direct `BeautyQSeedReady` dependency before repository reads? Inspect constructor signature and tests.
- Are `matched_queries` diagnostics implemented, tested, or only planned? Search exact ES request/response fields and test assertions.
- What transaction boundaries exist around multi-table variant writes and attribute replacement? Inspect Doobie `ConnectionIO` composition in `MasterServiceOfferVariants.Postgres` and attribute repository.
- Are repository FK constructor dependencies complete and intentional in all Postgres repositories? Compare source constructors against `docs/LOCAL_LLM_DISTAGE_APP_MODEL.md` graph.
- Do docs claiming eval coverage numbers still match current tests and seed inventory? Rerun focused pure eval in a later pass if accepted.
