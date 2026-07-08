package leaderboard.repo

import leaderboard.model.Category.CategoryId
import leaderboard.model.{Category, Master, MasterId, MasterLocation, MasterLocationId, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId, Service, ServiceId, ServiceVariantSchema, ServiceVariantSchemaItem}
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
  def graph[F[_, _]]: Graph[F] = {
    import Evidence.given

    BeautyQCatalogDeclaration.declaration
      .materialize[F, Repositories[F]](Graph.fromDeclaration)
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

  /** The fully-materialized BeautyQ catalog graph: a name, the inspectable
    * [[CatalogStep]]s in declaration order (see
    * [[BeautyQCatalogDeclaration.declaration]]), and one
    * repositories-to-relation factory per declared root/edge. Every factory
    * is still backed by the plain [[Relation]] case classes; nothing here
    * re-derives loading semantics. The typed fields below are BeautyQ's own
    * choice of names - the reusable DSL that builds the declaration knows
    * nothing about them.
    */
  final case class Graph[F[_, _]](
    name: String,
    steps: Vector[CatalogStep],
    categoryTree: Repositories[F] => Relation.SelfTree[F, Category, CategoryId],
    categoryServices: Repositories[F] => Relation.HasMany[F, Category, CategoryId, Service, ServiceId],
    serviceSchemas: Repositories[F] => Relation.HasValue[F, Service, ServiceId, ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem],
    allMasters: Repositories[F] => Relation.All[F, Master, MasterId],
    masterLocationsByMaster: Repositories[F] => Relation.HasMany[F, Master, MasterId, MasterLocation, MasterLocationId],
    masterOffersByMaster: Repositories[F] => Relation.HasMany[F, Master, MasterId, MasterServiceOffer, MasterServiceOfferId],
    offerVariants: Repositories[F] => Relation.HasMany[F, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId],
  )

  object Graph {

    /** Converts the materialized declaration into BeautyQ's own named, typed
      * [[Graph]]. This is the one place BeautyQ knowledge (field names)
      * meets the generic DSL: each field is picked out of the materialized
      * relation tuple by its exact type via [[TupleSelect]]
      * (`declaration.relation`/`relationAs`), so there is no manually
      * repeated tuple type and no reverse-order destructuring to keep in
      * sync with [[BeautyQCatalogDeclaration.declaration]] by hand.
      *
      * `inline` is required here, not just style: `Rels` only becomes a
      * concrete tuple type at this method's own call site (inside [[graph]]);
      * `relation`/`relationAs` defer their `TupleSelect` lookup to their
      * inline-expansion point via `summonInline`, so `fromDeclaration` itself
      * must stay inline for that lookup to see a concrete `Rels` rather than
      * an abstract type parameter.
      */
    inline def fromDeclaration[F[_, _], Rels <: Tuple](
      declaration: MaterializedDeclaration[F, Repositories[F], Rels]
    ): Graph[F] =
      Graph(
        name                    = declaration.name,
        steps                   = declaration.steps,
        categoryTree            = declaration.relationAs[Repositories[F] => Relation.SelfTree[F, Category, CategoryId]],
        categoryServices        = declaration.relationAs[Repositories[F] => Relation.HasMany[F, Category, CategoryId, Service, ServiceId]],
        serviceSchemas          = declaration.relationAs[Repositories[F] => Relation.HasValue[F, Service, ServiceId, ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem]],
        allMasters              = declaration.relationAs[Repositories[F] => Relation.All[F, Master, MasterId]],
        masterLocationsByMaster = declaration.relationAs[Repositories[F] => Relation.HasMany[F, Master, MasterId, MasterLocation, MasterLocationId]],
        masterOffersByMaster    = declaration.relationAs[Repositories[F] => Relation.HasMany[F, Master, MasterId, MasterServiceOffer, MasterServiceOfferId]],
        offerVariants           = declaration.relationAs[Repositories[F] => Relation.HasMany[F, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId]],
      )
  }

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

    given [F[_, _]]: CatalogRootTree.Aux[F, Repositories[F], Category, CategoryId] =
      CatalogRootTree.fromRepo[F, Repositories[F], Categories[F], Category, CategoryId](_.categories)(Categories.childrenByParent)

    given [F[_, _]]: CatalogMany.Aux[F, Repositories[F], Category, Service, CategoryId] =
      CatalogMany.fromRepo[F, Repositories[F], Services[F], Category, Service, CategoryId](_.services)(Services.byCategory)

    given [F[_, _]]: CatalogValueEdge.Aux[F, Repositories[F], Service, ServiceVariantSchema, ServiceId] =
      CatalogValueEdge.fromRepo[F, Repositories[F], ServiceVariantSchemas[F], Service, ServiceVariantSchema, ServiceId](_.serviceVariantSchemas)(ServiceVariantSchemas.byService)

    given [F[_, _]]: CatalogRootAll[F, Repositories[F], Master] =
      CatalogRootAll.fromRepo[F, Repositories[F], Masters[F], Master](_.masters)(Masters.all)

    given [F[_, _]]: CatalogMany.Aux[F, Repositories[F], Master, MasterLocation, MasterId] =
      CatalogMany.fromRepo[F, Repositories[F], MasterLocations[F], Master, MasterLocation, MasterId](_.masterLocations)(MasterLocations.byMaster)

    given [F[_, _]]: CatalogMany.Aux[F, Repositories[F], Master, MasterServiceOffer, MasterId] =
      CatalogMany.fromRepo[F, Repositories[F], MasterServiceOffers[F], Master, MasterServiceOffer, MasterId](_.masterServiceOffers)(MasterServiceOffers.byMaster)

    given [F[_, _]]: CatalogMany.Aux[F, Repositories[F], MasterServiceOffer, MasterServiceOfferVariant, MasterServiceOfferId] =
      CatalogMany.fromRepo[F, Repositories[F], MasterServiceOfferVariants[F], MasterServiceOffer, MasterServiceOfferVariant, MasterServiceOfferId](_.masterServiceOfferVariants)(MasterServiceOfferVariants.byOffer)
  }

  /** The BeautyQ catalog relations, materialized from [[graph]] against a
    * concrete [[Repositories]].
    */
  final class Relations[F[_, _]](repositories: Repositories[F]) {
    private val declaration: Graph[F] = graph[F]

    val categoryTree: Relation.SelfTree[F, Category, CategoryId] =
      declaration.categoryTree(repositories)

    val categoryServices: Relation.HasMany[F, Category, CategoryId, Service, ServiceId] =
      declaration.categoryServices(repositories)

    val serviceSchemas: Relation.HasValue[F, Service, ServiceId, ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] =
      declaration.serviceSchemas(repositories)

    val allMasters: Relation.All[F, Master, MasterId] =
      declaration.allMasters(repositories)

    val masterLocationsByMaster: Relation.HasMany[F, Master, MasterId, MasterLocation, MasterLocationId] =
      declaration.masterLocationsByMaster(repositories)

    val masterOffersByMaster: Relation.HasMany[F, Master, MasterId, MasterServiceOffer, MasterServiceOfferId] =
      declaration.masterOffersByMaster(repositories)

    val offerVariants: Relation.HasMany[F, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId] =
      declaration.offerVariants(repositories)
  }
}
