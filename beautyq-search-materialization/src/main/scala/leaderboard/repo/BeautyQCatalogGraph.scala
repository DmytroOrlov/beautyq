package leaderboard.repo

import leaderboard.model.{ServiceId, ServiceVariantSchema, ServiceVariantSchemaItem}
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
      CatalogValue.from(ServiceVariantSchemas.valueSource)

    // No explicit CatalogValueEdge.Aux given for Service -> ServiceVariantSchema
    // here (Phase D2A): `ServiceVariantSchemas[F].getServiceVariantSchema` is
    // the repositories bundle's only method shaped `ServiceId => F[QueryFailure,
    // ServiceVariantSchema]`, so `CatalogValueEdge.derivedFromRepositories`
    // (repo-core) resolves it automatically at materialization time.
    // The old `ServiceVariantSchemas.byService` companion wrapper was removed
    // in the wrapper cleanup. The value edge now resolves through
    // `CatalogValueEdge.derivedFromRepositories`, and seed-scoped value
    // loading uses `GraphLoading.seedValuesByKey`.

    // No explicit CatalogMany.Aux given for Master -> MasterServiceOffer or
    // MasterServiceOffer -> MasterServiceOfferVariant here (nominal BeautyQ ID
    // migration): `MasterId`/`ServiceId`/`MasterLocationId`/`MasterServiceOfferId`/
    // `MasterServiceOfferVariantId` are now distinct opaque types (not
    // transparent aliases of the same `UUID`), so each repositories field has
    // exactly one method whose parameter key type matches the requested `K`
    // by type/signature - `CatalogMany.derivedFromRepositories` (repo-core)
    // resolves both edges automatically at materialization time. The old
    // `MasterServiceOffers.byMaster`/`MasterServiceOfferVariants.byOffer`
    // companion wrappers (kept explicit only for the prior ambiguous-UUID
    // era) were removed in the nominal id migration.
  }

}
