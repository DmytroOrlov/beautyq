package leaderboard.repo

import leaderboard.model.Category.CategoryId
import leaderboard.model.{Category, Master, MasterId, MasterLocation, MasterLocationId, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, Service, ServiceId, ServiceVariantSchema, ServiceVariantSchemaItem}
import leaderboard.search.beautyq.contract.BeautyQCatalogDeclaration

/** Central BeautyQ catalog graph facade.
  *
  * This is the one place a coordinator reads to see the BeautyQ domain graph
  * as a pure, business-readable branch declaration
  * ([[BeautyQCatalogDeclaration.declaration]]), built from the reusable
  * catalog declaration DSL in `RepoGraph.scala` (`catalog`, `.branch`,
  * `.rootTree`/`.rootAll`, `.child`/`.value`). That DSL knows nothing about
  * BeautyQ, `F`, or `Repositories[F]` - the declaration itself is pure. All
  * BeautyQ-specific wiring (which repo loads what) lives in [[Evidence]],
  * imported only when materializing [[graph]]. This module adds no
  * interpreter and no new relation semantics of its own: it is a thin
  * facade over [[EntityNode]]/[[Relation]] as declared by [[RepoGraph]].
  */
object BeautyQCatalogGraph {

  /** Materializes [[BeautyQCatalogDeclaration.declaration]]
   * (the pure BeautyQ catalog topology section, owned by
   * `beautyq-search-contract` - not the complete BeautyQ search contract)
   * against a concrete effect type `F`, using [[Evidence]] to supply the
   * BeautyQ-specific repositories and loaders the declaration itself never
   * mentions.
   */
  transparent inline def graph[F[_, _]] = {
    import Evidence.given

    BeautyQCatalogDeclaration.declaration
      .materialize[F, Repositories[F]](identity)
  }

  /** Typed graph nodes for the BeautyQ catalog entities. Used only by
    * [[Evidence]] (to build [[CatalogEntity]]/[[CatalogValue]] instances) and
    * by external callers such as
    * `leaderboard.search.document.BeautyQVariantSearchDocumentMaterialization`.
    * The pure [[BeautyQCatalogDeclaration.declaration]] never references
    * these.
    */
  object Nodes {
    val category: EntityNode[Category, CategoryId] =
      Categories.entity.node(_.id)

    val service: EntityNode[Service, ServiceId] =
      Services.entity.node(_.id)

    /** Not an id-keyed node: an aggregate value source keyed by `serviceId`
      * and physically sourced from schema item rows. Stays explicit.
      */
    val serviceVariantSchema: RepoValueSource[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] =
      ServiceVariantSchemas.valueSource

    val master: EntityNode[Master, MasterId] =
      Masters.entity.node(_.id)

    val masterLocation: EntityNode[MasterLocation, MasterLocationId] =
      MasterLocations.entity.node(_.id)

    val masterServiceOffer: EntityNode[MasterServiceOffer, MasterServiceOfferId] =
      MasterServiceOffers.entity.node(_.id)
  }

  /** The repositories needed to wire the BeautyQ catalog relations for a
    * concrete effect type `F`. A materialization dependency bundle only -
    * never referenced by the pure [[BeautyQCatalogDeclaration.declaration]]
    * itself.
    */
  final case class Repositories[F[_, _]](
    categories: Categories[F],
    services: Services[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    masters: Masters[F],
    masterLocations: MasterLocations[F],
    masterServiceOffers: MasterServiceOffers[F],
    masterServiceOfferVariants: MasterServiceOfferVariants[F],
  )

  /** All BeautyQ-specific materialization wiring: what each declared type
    * *is* ([[CatalogEntity]]/[[CatalogValue]]), and how to *load* each
    * declared root/edge for a concrete `F`/[[Repositories]]
    * ([[CatalogRootTree]]/[[CatalogRootAll]]/[[CatalogMany]]/[[CatalogValueEdge]]).
    * The pure [[BeautyQCatalogDeclaration.declaration]] never imports this;
    * only [[graph]] does, at materialization time.
    */
  object Evidence {
    // No normal entity evidence (`CatalogEntity.Aux[...]`) is declared here at
    // all, for any of the six BeautyQ entities (Category/Service/Master/
    // MasterLocation/MasterServiceOffer/MasterServiceOfferVariant): Phase C3
    // made `CatalogBranch.rootAll`/`CatalogChildStart.apply` pin the output
    // key type of every declared root/child directly from the entity's own
    // conventional `id` field at declaration time (`RootAllSpec[A, K]`/
    // `ManyEdgeSpec[P, C, K, CK]` in `BeautyQCatalogDeclaration.declaration`
    // now carry that key type explicitly, the same way `RootTreeSpec[A, K]`
    // already did). With the key type always already concrete in the spec
    // tuple, `CatalogEntity.derivedFromId` (repo-core) resolves every normal
    // entity automatically at materialization time too - the free-`K`/`CK`
    // problem Phase C.1/C.2/C.2b diagnosed no longer arises, because nothing
    // ever asks for `CatalogEntity.Aux[A, K]` with a free `K`.
    given CatalogValue.Aux[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] =
      CatalogValue.from(Nodes.serviceVariantSchema)

    given [F[_, _]]: CatalogValueEdge.Aux[F, Repositories[F], Service, ServiceVariantSchema, ServiceId] =
      CatalogValueEdge.fromRepo[F, Repositories[F], ServiceVariantSchemas[F], Service, ServiceVariantSchema, ServiceId](_.serviceVariantSchemas)(ServiceVariantSchemas.byService)

    given [F[_, _]]: CatalogMany.Aux[F, Repositories[F], Master, MasterServiceOffer, MasterId] =
      CatalogMany.fromRepo[F, Repositories[F], MasterServiceOffers[F], Master, MasterServiceOffer, MasterId](_.masterServiceOffers)(MasterServiceOffers.byMaster)

    given [F[_, _]]: CatalogMany.Aux[F, Repositories[F], MasterServiceOffer, MasterServiceOfferVariant, MasterServiceOfferId] =
      CatalogMany.fromRepo[F, Repositories[F], MasterServiceOfferVariants[F], MasterServiceOffer, MasterServiceOfferVariant, MasterServiceOfferId](_.masterServiceOfferVariants)(MasterServiceOfferVariants.byOffer)
  }

}
