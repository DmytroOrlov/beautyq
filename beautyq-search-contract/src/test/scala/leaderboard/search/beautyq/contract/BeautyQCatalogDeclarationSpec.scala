package leaderboard.search.beautyq.contract

import org.scalatest.wordspec.AnyWordSpec

final class BeautyQCatalogDeclarationSpec extends AnyWordSpec {

  "BeautyQCatalogDeclaration" should {
    "be constructible from beautyq-search-contract alone, using BeautyQ model types from beautyq-model" in {
      val declaration = BeautyQCatalogDeclaration.declaration
      assert(declaration.name == "beautyq")
    }

    "declare the category root as a self tree, joined through parentId" in {
      val steps = BeautyQCatalogDeclaration.declaration.steps
      assert(steps.head.summary == "root:category:tree:parentId")
    }

    "declare category -> service as a has-many edge keyed by categoryId" in {
      val steps = BeautyQCatalogDeclaration.declaration.steps
      assert(steps.contains(leaderboard.repo.CatalogStep.Edge("category", "service", leaderboard.repo.EdgeKind.Many, "categoryId")))
    }

    "declare service -> serviceVariantSchema as a value edge keyed by serviceId" in {
      val steps = BeautyQCatalogDeclaration.declaration.steps
      assert(steps.contains(leaderboard.repo.CatalogStep.Edge("service", "serviceVariantSchema", leaderboard.repo.EdgeKind.Value, "serviceId")))
    }

    "declare the master root as a flat all-loaded collection" in {
      val steps = BeautyQCatalogDeclaration.declaration.steps
      assert(steps.contains(leaderboard.repo.CatalogStep.Root("master", leaderboard.repo.RootLoading.All, None)))
    }

    "declare master -> masterLocation and master -> masterServiceOffer as has-many edges keyed by masterId" in {
      val steps = BeautyQCatalogDeclaration.declaration.steps
      assert(steps.contains(leaderboard.repo.CatalogStep.Edge("master", "masterLocation", leaderboard.repo.EdgeKind.Many, "masterId")))
      assert(steps.contains(leaderboard.repo.CatalogStep.Edge("master", "masterServiceOffer", leaderboard.repo.EdgeKind.Many, "masterId")))
    }

    "declare masterServiceOffer -> masterServiceOfferVariant as a has-many edge keyed by masterServiceOfferId" in {
      val steps = BeautyQCatalogDeclaration.declaration.steps
      assert(steps.contains(leaderboard.repo.CatalogStep.Edge("masterServiceOffer", "masterServiceOfferVariant", leaderboard.repo.EdgeKind.Many, "masterServiceOfferId")))
    }

    "declare exactly the full set of roots/edges in declaration order, and nothing more" in {
      val steps = BeautyQCatalogDeclaration.declaration.steps
      assert(
        steps.map(_.summary) ==
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

    "not require any repository loader or materialization to be declared" in {
      // The declaration is a pure CatalogBranch: only .name/.steps are read here,
      // no F effect type, no repositories instance, no .materialize call.
      val declaration = BeautyQCatalogDeclaration.declaration
      assert(declaration.entityName == "masterServiceOffer")
    }
  }

  "BeautyQCatalogSection" should {
    "label itself explicitly as the catalog topology section, not the complete search contract" in {
      assert(BeautyQCatalogSection.label == "catalog topology section, not complete search contract")
    }

    "wrap the same pure declaration in the generic CatalogSection ADT" in {
      assert(BeautyQCatalogSection.catalogTopology.descriptor eq BeautyQCatalogDeclaration.declaration)
      assert(BeautyQCatalogSection.section.descriptor.steps == BeautyQCatalogDeclaration.declaration.steps)
    }
  }
}
