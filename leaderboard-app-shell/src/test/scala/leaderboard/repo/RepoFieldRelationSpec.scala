package leaderboard.repo

import leaderboard.model.{Category, CategoryCode, MasterServiceOfferId, MasterServiceOfferVariant, Service, ServiceId, ServiceVariantSchema}
import leaderboard.model.Category.CategoryId
import leaderboard.repo.RepoOp.ManyByKey
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, ZIO}

import java.util.UUID

final class RepoFieldRelationSpec extends AnyWordSpec {

  private def uuid(suffix: String): UUID = UUID.fromString(s"00000000-0000-0000-0000-$suffix")

  "RepoField selector derivation" should {
    "derive Category.parentId as parentId / parent_id and select the value" in {
      val field   = Categories.entity.field(_.parentId)
      val parent  = CategoryId(uuid("0000000000a1"))
      val category = Category(CategoryId(uuid("0000000000a2")), CategoryCode.unsafeFromString("category_test_child"), parent, 1, "child")
      assert(field.label == "parentId")
      assert(field.column == "parent_id")
      assert(field.select(category) == parent)
    }

    "derive Category.code as code / code" in {
      val field = Categories.entity.field(_.code)
      assert(field.label == "code")
      assert(field.column == "code")
    }

    "derive Service.categoryId as categoryId / category_id" in {
      val field = Services.entity.field(_.categoryId)
      assert(field.label == "categoryId")
      assert(field.column == "category_id")
    }

    "derive Service.code as code / code" in {
      val field = Services.entity.field(_.code)
      assert(field.label == "code")
      assert(field.column == "code")
    }

    "derive MasterServiceOfferVariant.masterServiceOfferId as masterServiceOfferId / master_service_offer_id" in {
      val field = MasterServiceOfferVariants.entity.field(_.masterServiceOfferId)
      assert(field.label == "masterServiceOfferId")
      assert(field.column == "master_service_offer_id")
    }
  }

  "Entity nodes" should {
    "expose the key field label and column for Categories" in {
      val node = Categories.entity.node(_.id)
      assert(node.key.label == "id")
      assert(node.key.column == "id")
    }

    "expose the key field label and column for Services" in {
      val node = Services.entity.node(_.id)
      assert(node.key.label == "id")
      assert(node.key.column == "id")
    }
  }

  "Relation metadata" should {
    val category = Categories.entity.node(_.id)
    val service  = Services.entity.node(_.id)
    val offer    = MasterServiceOffers.entity.node(_.id)
    val variant  = MasterServiceOfferVariants.entity.node(_.id)

    val noCategories = ManyByKey[IO, CategoryId, Category](_ => ZIO.succeed(Nil))
    val noServices   = ManyByKey[IO, CategoryId, Service](_ => ZIO.succeed(Nil))
    val noVariants   = ManyByKey[IO, MasterServiceOfferId, MasterServiceOfferVariant](_ => ZIO.succeed(Nil))

    "store the self-tree parent field label and column" in {
      val tree = category.selfTree(parent = _.parentId, root = Category.rootCategoryId, children = noCategories)
      assert(tree.parent.label == "parentId")
      assert(tree.parent.column == "parent_id")
      assert(tree.node.entity.sourceName == "category")
    }

    "store the has-many child foreign-key field and retain both nodes" in {
      val relation = category.hasMany(service)(by = _.categoryId, load = noServices)
      assert(relation.foreignKey.label == "categoryId")
      assert(relation.foreignKey.column == "category_id")
      assert(relation.parent.entity.sourceName == "category")
      assert(relation.child.entity.sourceName == "service")
    }

    "store the offer-variants child foreign-key field" in {
      val relation = offer.hasMany(variant)(by = _.masterServiceOfferId, load = noVariants)
      assert(relation.foreignKey.label == "masterServiceOfferId")
      assert(relation.foreignKey.column == "master_service_offer_id")
      assert(relation.child.entity.sourceName == "master_service_offer_variant")
    }

    "store the has-value parent node, value source and value key field" in {
      val schemaLoad = RepoOp.ValueByKey[IO, ServiceId, ServiceVariantSchema](id => ZIO.succeed(ServiceVariantSchema.empty(id)))
      val relation   = service.hasValue(ServiceVariantSchemas.valueSource)(by = _.serviceId, load = schemaLoad)
      assert(relation.parent.entity.sourceName == "service")
      assert(relation.valueKey.label == "serviceId")
      assert(relation.valueKey.column == "service_id")
      assert(relation.value.rowSource.sourceName == "service_variant_schema_item")
    }
  }

  "CatalogEntity.derived" should {
    "derive the category node key label and column from the conventional id field" in {
      val derivedEntity = CatalogEntity.derived(Categories.entity)
      assert(derivedEntity.node.key.label == "id")
      assert(derivedEntity.node.key.column == "id")
    }

    "derive the same category node metadata as an explicit entity.node(_.id)" in {
      val derivedEntity = CatalogEntity.derived(Categories.entity)
      val explicitNode  = Categories.entity.node(_.id)
      assert(derivedEntity.node.entity == explicitNode.entity)
      assert(derivedEntity.node.key.label == explicitNode.key.label)
      assert(derivedEntity.node.key.column == explicitNode.key.column)
    }
  }

  "BeautyQCatalogGraph.graph" should {
    "declare the catalog under the name \"beautyq\"" in {
      assert(BeautyQCatalogGraph.graph[IO].name == "beautyq")
    }

    "list the declared roots/edges in domain declaration order, with real key labels" in {
      assert(
        BeautyQCatalogGraph.graph[IO].steps.map(_.summary) ==
          Vector(
            "root:category:tree:parentId",
            "many:category->service:categoryId",
            "value:service->serviceVariantSchema:serviceId",
            "root:master:all",
            "many:master->masterLocation:masterId",
            "many:master->masterServiceOffer:masterId",
            "many:masterServiceOffer->masterServiceOfferVariant:masterServiceOfferId",
          )
      )
    }

    "declare the category root as a real, tree-loaded step keyed by parentId" in {
      assert(BeautyQCatalogGraph.graph[IO].steps.head == CatalogStep.Root("category", RootLoading.Tree, Some("parentId")))
    }

    "declare the master root as a real, all-loaded step" in {
      val masterRootStep = BeautyQCatalogGraph.graph[IO].steps.collectFirst { case step @ CatalogStep.Root("master", _, _) => step }
      assert(masterRootStep.contains(CatalogStep.Root("master", RootLoading.All, None)))
    }
  }

  "ServiceVariantSchema value source" should {
    "be represented separately from the physical item row source" in {
      val source = ServiceVariantSchemas.valueSource
      assert(source.valueModelName == "ServiceVariantSchema")
      assert(source.rowSource.modelName == "ServiceVariantSchemaItem")
      assert(source.rowSource.sourceName == "service_variant_schema_item")
    }

    "key the aggregate by serviceId / service_id" in {
      val source = ServiceVariantSchemas.valueSource
      assert(source.keyField.label == "serviceId")
      assert(source.keyField.column == "service_id")
    }
  }
}
