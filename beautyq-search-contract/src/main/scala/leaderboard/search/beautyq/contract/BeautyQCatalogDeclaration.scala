package leaderboard.search.beautyq.contract

import leaderboard.model.{Category, Master, MasterLocation, MasterServiceOffer, MasterServiceOfferVariant, Service, ServiceVariantSchema}
import leaderboard.repo.catalog

/** The BeautyQ catalog topology section - NOT the complete BeautyQ search
  * contract. This is a pure, business-readable branch declaration built from
  * the reusable catalog declaration DSL in `repo-core` (`catalog`, `.branch`,
  * `.rootTree`/`.rootAll`, `.child`/`.value`), which knows nothing about
  * BeautyQ, `F`, or any repositories type. It declares only roots and edges -
  * which BeautyQ types exist and how they join - never how to load them.
  *
  * A future full BeautyQ search contract would additionally need document,
  * intent, runtime, response, and evaluation sections; none of those exist
  * yet, and this declaration must not be described as if it were the whole
  * contract. All repository/materialization wiring for this catalog topology
  * lives in `leaderboard.repo.BeautyQCatalogGraph` (in
  * `beautyq-search-materialization`), which materializes this pure
  * declaration - it is never imported here.
  */
object BeautyQCatalogDeclaration {

  val declaration =
    catalog("beautyq")
      .branch[Category]
      .rootTree(_.parentId, root = Category.rootCategoryId)
      .child[Service](_.categoryId)
      .branch[Service]
      .value[ServiceVariantSchema](_.serviceId)
      .branch[Master]
      .rootAll
      .child[MasterLocation](_.masterId)
      .child[MasterServiceOffer](_.masterId)
      .branch[MasterServiceOffer]
      .child[MasterServiceOfferVariant](_.masterServiceOfferId)
}
