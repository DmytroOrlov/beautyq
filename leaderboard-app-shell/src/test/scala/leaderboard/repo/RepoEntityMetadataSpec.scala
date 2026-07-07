package leaderboard.repo

import leaderboard.model.{Category, MasterServiceOfferVariant, Service, ServiceVariantSchemaItem}
import org.scalatest.wordspec.AnyWordSpec

final class RepoEntityMetadataSpec extends AnyWordSpec {

  "SnakeCase naming strategy" should {
    "derive model/type names" in {
      assert(RepoNamingStrategy.SnakeCase.table("Category") == "category")
      assert(RepoNamingStrategy.SnakeCase.table("MasterServiceOfferVariant") == "master_service_offer_variant")
    }

    "derive field names" in {
      assert(RepoNamingStrategy.SnakeCase.column("parentId") == "parent_id")
      assert(RepoNamingStrategy.SnakeCase.column("categoryId") == "category_id")
      assert(RepoNamingStrategy.SnakeCase.column("serviceId") == "service_id")
      assert(RepoNamingStrategy.SnakeCase.column("masterServiceOfferId") == "master_service_offer_id")
    }
  }

  "Model-derived entity metadata" should {
    "derive Category source, id column and data columns" in {
      val entity = RepoEntity.derived[Category]
      assert(entity.sourceName == "category")
      assert(entity.idColumn.contains("id"))
      assert(entity.dataColumns == List("parent_id", "depth", "name"))
    }

    "derive Service source, id column and data columns" in {
      val entity = RepoEntity.derived[Service]
      assert(entity.sourceName == "service")
      assert(entity.idColumn.contains("id"))
      assert(entity.dataColumns == List("category_id", "name"))
    }

    "derive the schema item row source" in {
      val entity = RepoEntity.derived[ServiceVariantSchemaItem]
      assert(entity.sourceName == "service_variant_schema_item")
      assert(entity.idColumn.isEmpty)
    }

    "derive the variant source from a private-constructor case class" in {
      val entity = RepoEntity.derived[MasterServiceOfferVariant]
      assert(entity.sourceName == "master_service_offer_variant")
      assert(entity.idColumn.contains("id"))
      assert(entity.columns.contains("master_service_offer_id"))
      assert(entity.columns.contains("master_location_id"))
    }
  }
}
