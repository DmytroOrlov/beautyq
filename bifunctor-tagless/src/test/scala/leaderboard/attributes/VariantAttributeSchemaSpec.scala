package leaderboard

import leaderboard.model.*
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import doobie.implicits.*
import doobie.postgres.implicits.*
import leaderboard.repo.{Categories, ServiceVariantSchemas, Services}
import leaderboard.sql.SQL
import zio.{IO, ZIO}

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md

abstract class VariantAttributeSchemaSpec extends LeaderboardTest with VariantTestFixtures {
  "VariantAttributeSchema" should {
    "schema validate rejects disallowed attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("12.5000"))),
            booleanValues    = AttributeMap.Impl(Map(AttributeDefinition.WithRemoval -> true)),
          )
          _ <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.DisallowedAttribute(
                AttributeDefinition.DepositAmount
              )
            )
          )
        } yield ()
    }

    "schema validate rejects missing required attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true))
          attributes = MasterServiceOfferVariantAttributes.empty
          _         <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.MissingRequiredAttribute(
                AttributeDefinition.SessionCount
              )
            )
          )
        } yield ()
    }

    "schema validate supports allowed enum attributes" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues       = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring
            ),
          )
          _ <- assertIO(schema.validate(attributes) == Right(()))
        } yield ()
    }

    "schema validate supports allowed new enum attributes" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.NailServiceTypeAttribute, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues       = enumAttributeMap(
              AttributeDefinition.NailServiceTypeAttribute -> NailServiceType.Manicure
            ),
          )
          _ <- assertIO(schema.validate(attributes) == Right(()))
        } yield ()
    }

    "schema validate supports allowed boolean attributes" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues       = AttributeMap.empty,
            booleanValues    = AttributeMap.Impl(Map(AttributeDefinition.WithRemoval -> true)),
          )
          _ <- assertIO(schema.validate(attributes) == Right(()))
        } yield ()
    }

    "schema validate rejects disallowed enum attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues       = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring
            ),
          )
          _ <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.DisallowedAttribute(
                AttributeDefinition.HairRemovalMethodAttribute
              )
            )
          )
        } yield ()
    }

    "schema validate rejects disallowed new enum attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues       = enumAttributeMap(
              AttributeDefinition.NailServiceTypeAttribute -> NailServiceType.Manicure
            ),
          )
          _ <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.DisallowedAttribute(
                AttributeDefinition.NailServiceTypeAttribute
              )
            )
          )
        } yield ()
    }

    "schema validate rejects missing required enum attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, true))
          attributes = MasterServiceOfferVariantAttributes.empty
          _         <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.MissingRequiredAttribute(
                AttributeDefinition.HairRemovalMethodAttribute
              )
            )
          )
        } yield ()
    }

  }
}

abstract class ServiceVariantSchemasTest extends LeaderboardTest {
  private def makeSchema(serviceId: ServiceId, items: ServiceVariantSchemaItem*): ServiceVariantSchema =
    ServiceVariantSchema.fromItems(serviceId, items)

  "ServiceVariantSchemas" should {

    "upsert & get" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO], serviceVariantSchemas: ServiceVariantSchemas[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, rootCategoryId, 0, s"schema-category-$categoryId")
          service     = Service(serviceId, categoryId, s"schema-service-$serviceId")
          schema      = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
          )
          _   <- categories.upsertCategory(category)
          _   <- services.upsertService(service)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          res <- serviceVariantSchemas.getServiceVariantSchema(serviceId)
          _   <- assertIO(
            res == ServiceVariantSchema.fromItems(
              serviceId,
              List(
                ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
                ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
              ),
            )
          )
        } yield ()
    }

    "reject schema for missing service" in {
      (rnd: Rnd[IO], serviceVariantSchemas: ServiceVariantSchemas[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          result    <- serviceVariantSchemas
            .upsertServiceVariantSchema(
              makeSchema(
                serviceId,
                ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
              )
            )
            .either
          _ <- assertIO(result.isLeft)
        } yield ()
    }

  }

}

abstract class ServiceVariantSchemasStorageValidationTest extends LeaderboardTest {
  "ServiceVariantSchemas" should {
    "reject unknown attribute code from storage" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO], serviceVariantSchemas: ServiceVariantSchemas[IO], db: SQL[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, rootCategoryId, 0, s"schema-storage-category-$categoryId")
          service     = Service(serviceId, categoryId, s"schema-storage-service-$serviceId")
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(service)
          _          <- db.execute("insert-invalid-service-variant-schema-item") {
            sql"""insert into service_variant_schema_items (
                 |  service_id,
                 |  attribute_code,
                 |  required
                 |)
                 |values (
                 |  $serviceId,
                 |  ${"unknown_attribute_code"},
                 |  false
                 |)
                 |""".stripMargin.update.run
          }
          result <- serviceVariantSchemas.getServiceVariantSchema(serviceId).either
          _      <- assertIO(result.isLeft)
        } yield ()
    }
  }
}

class VariantAttributeSchemaSpecDummy extends VariantAttributeSchemaSpec with DummyTest
class VariantAttributeSchemaSpecPostgres extends VariantAttributeSchemaSpec with ProdTest
class ServiceVariantSchemasTestDummy extends ServiceVariantSchemasTest with DummyTest
class ServiceVariantSchemasTestPostgres extends ServiceVariantSchemasTest with ProdTest
class ServiceVariantSchemasStorageValidationTestPostgres extends ServiceVariantSchemasStorageValidationTest with ProdTest
