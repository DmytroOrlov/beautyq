package leaderboard.repo

import leaderboard.model.Category.CategoryId
import leaderboard.model.{Category, Master, MasterId, MasterLocation, MasterLocationId, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId, Service, ServiceId, ServiceVariantSchema, ServiceVariantSchemaItem}
import leaderboard.repo.RepoOp.{OptionalByKey, ValueByKey}

/** Model-first declaration of the BeautyQ domain graph.
  *
  * This module is the one place where BeautyQ-specific entity metadata and
  * relations are declared. The generic foundation ([[RepoEntity]],
  * [[RepoOp]], [[EntityNode]], [[Relation]], [[GraphLoading]]) knows nothing
  * about BeautyQ and never imports search classes.
  *
  * Entity/source names and columns are derived from the Scala model; no table
  * or column strings are written here.
  */
final class BeautyQRepoGraph[F[_, _]](
  categories: Categories[F],
  services: Services[F],
  serviceVariantSchemas: ServiceVariantSchemas[F],
  masters: Masters[F],
  masterLocations: MasterLocations[F],
  masterServiceOffers: MasterServiceOffers[F],
  masterServiceOfferVariants: MasterServiceOfferVariants[F],
) {

  // --- Entity / source metadata (model-derived) ---

  val categoryEntity: RepoEntity[Category]                                   = Categories.entity
  val serviceEntity: RepoEntity[Service]                                     = Services.entity
  val serviceVariantSchemaItemEntity: RepoEntity[ServiceVariantSchemaItem]   = ServiceVariantSchemas.itemEntity
  val masterEntity: RepoEntity[Master]                                       = Masters.entity
  val masterLocationEntity: RepoEntity[MasterLocation]                       = MasterLocations.entity
  val masterServiceOfferEntity: RepoEntity[MasterServiceOffer]               = MasterServiceOffers.entity
  val masterServiceOfferVariantEntity: RepoEntity[MasterServiceOfferVariant] = MasterServiceOfferVariants.entity

  /** Typed value source for the [[ServiceVariantSchema]] aggregate, keyed by
    * `serviceId` and physically sourced from the schema item rows.
    */
  val serviceVariantSchemaSource: RepoValueSource[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] =
    ServiceVariantSchemas.valueSource

  // --- Graph nodes (entity + selector-derived key field) ---

  val category: EntityNode[Category, CategoryId]                                                      = Categories.entity.node(_.id)
  val service: EntityNode[Service, ServiceId]                                                         = Services.entity.node(_.id)
  val serviceVariantSchema: RepoValueSource[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] = serviceVariantSchemaSource
  val master: EntityNode[Master, MasterId]                                                            = Masters.entity.node(_.id)
  val masterLocation: EntityNode[MasterLocation, MasterLocationId]                                    = MasterLocations.entity.node(_.id)
  val masterServiceOffer: EntityNode[MasterServiceOffer, MasterServiceOfferId]                        = MasterServiceOffers.entity.node(_.id)
  val masterServiceOfferVariant: EntityNode[MasterServiceOfferVariant, MasterServiceOfferVariantId]   = MasterServiceOfferVariants.entity.node(_.id)

  // --- Relations (the BeautyQ graph, model-first) ---

  val categoryTree: Relation.SelfTree[F, Category, CategoryId] =
    category.selfTree(parent = _.parentId, children = Categories.childrenByParent(categories))

  val categoryServices: Relation.HasMany[F, Category, CategoryId, Service, ServiceId] =
    category.hasMany(service)(by = _.categoryId, load = Services.byCategory(services))

  val serviceSchemas: Relation.HasValue[F, Service, ServiceId, ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] =
    service.hasValue(serviceVariantSchema)(by = _.serviceId, load = ServiceVariantSchemas.byService(serviceVariantSchemas))

  val allMasters: Relation.All[F, Master, MasterId] =
    master.all(Masters.all(masters))

  val masterLocationsByMaster: Relation.HasMany[F, Master, MasterId, MasterLocation, MasterLocationId] =
    master.hasMany(masterLocation)(by = _.masterId, load = MasterLocations.byMaster(masterLocations))

  val masterOffersByMaster: Relation.HasMany[F, Master, MasterId, MasterServiceOffer, MasterServiceOfferId] =
    master.hasMany(masterServiceOffer)(by = _.masterId, load = MasterServiceOffers.byMaster(masterServiceOffers))

  val offerVariants: Relation.HasMany[F, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId] =
    masterServiceOffer.hasMany(masterServiceOfferVariant)(by = _.masterServiceOfferId, load = MasterServiceOfferVariants.byOffer(masterServiceOfferVariants))

  // --- Seed-scoped optional/value operations (same op layer) ---

  val categoryById: OptionalByKey[F, CategoryId, Category]                                 = Categories.byId(categories)
  val serviceById: OptionalByKey[F, ServiceId, Service]                                   = Services.byId(services)
  val masterById: OptionalByKey[F, MasterId, Master]                                      = Masters.byId(masters)
  val masterLocationById: OptionalByKey[F, MasterLocationId, MasterLocation]              = MasterLocations.byId(masterLocations)
  val masterServiceOfferById: OptionalByKey[F, MasterServiceOfferId, MasterServiceOffer]  = MasterServiceOffers.byId(masterServiceOffers)
  val masterServiceOfferVariantById: OptionalByKey[F, MasterServiceOfferVariantId, MasterServiceOfferVariant] = MasterServiceOfferVariants.byId(masterServiceOfferVariants)
  val schemaByService: ValueByKey[F, ServiceId, ServiceVariantSchema]                     = ServiceVariantSchemas.byService(serviceVariantSchemas)
}
