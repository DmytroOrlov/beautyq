package leaderboard.search.document

import leaderboard.model.*
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOffers, Masters, RepoSnapshotProjection, Services, ServiceVariantSchemas}
import leaderboard.search.dsl.SearchGeoPoint

/** The BeautyQ variant document projection engine: projects a repo-backed
  * catalog snapshot (as plain lists, not the seed-coupled
  * `BeautySearchCatalogSnapshot`) into [[VariantSearchDocument]] values.
  *
  * This owns the actual materialization logic - node handles built directly
  * from repository entity metadata, joins, schema validation, and text
  * normalization/token building. It needs the BeautyQ repositories'
  * companions (for entity node metadata) and [[ServiceVariantSchemas]] and
  * therefore cannot live in the pure contract module.
  */
object BeautyQVariantSearchDocumentMaterialization {
  private val BuildOperationName = "build-variant-search-documents"

  // Projection owns these node handles only as row metadata for
  // indexing/error messages (entity model name + id field/column). They are
  // not catalog declaration/evidence API - the catalog declaration derives
  // its own entity evidence automatically at materialization time
  // (`CatalogEntity.derivedFromId`) and never references these.
  private val categoryNode           = Categories.entity.node(_.id)
  private val serviceNode            = Services.entity.node(_.id)
  private val masterNode             = Masters.entity.node(_.id)
  private val masterLocationNode     = MasterLocations.entity.node(_.id)
  private val masterServiceOfferNode = MasterServiceOffers.entity.node(_.id)

  /** Projects a materialization-owned [[BeautyQSearchCatalogSnapshot]]
    * directly. Delegates to the seven-list `project` overload below, kept
    * for source compatibility with existing callers that already have the
    * lists unpacked.
    */
  def project(
    snapshot: BeautyQSearchCatalogSnapshot
  ): Either[QueryFailure, List[VariantSearchDocument]] =
    project(
      categories                 = snapshot.categories,
      services                   = snapshot.services,
      serviceVariantSchemas      = snapshot.serviceVariantSchemas,
      masters                    = snapshot.masters,
      masterLocations            = snapshot.masterLocations,
      masterServiceOffers        = snapshot.masterServiceOffers,
      masterServiceOfferVariants = snapshot.masterServiceOfferVariants,
    )

  def project(
    categories: List[Category],
    services: List[Service],
    serviceVariantSchemas: List[ServiceVariantSchema],
    masters: List[Master],
    masterLocations: List[MasterLocation],
    masterServiceOffers: List[MasterServiceOffer],
    masterServiceOfferVariants: List[MasterServiceOfferVariant],
  ): Either[QueryFailure, List[VariantSearchDocument]] = {
    val categoriesById = RepoSnapshotProjection.indexByKeyPreservingFirst(
      RepoSnapshotProjection.source(categoryNode, categories)
    )
    val servicesById = RepoSnapshotProjection.indexByKeyPreservingFirst(
      RepoSnapshotProjection.source(serviceNode, services)
    )
    val schemasByServiceId = RepoSnapshotProjection.indexByKeyPreservingFirst(
      RepoSnapshotProjection.valueSource(ServiceVariantSchemas.valueSource, serviceVariantSchemas)
    )
    val mastersById = RepoSnapshotProjection.indexByKeyPreservingFirst(
      RepoSnapshotProjection.source(masterNode, masters)
    )
    val locationsById = RepoSnapshotProjection.indexByKeyPreservingFirst(
      RepoSnapshotProjection.source(masterLocationNode, masterLocations)
    )
    val offersById = RepoSnapshotProjection.indexByKeyPreservingFirst(
      RepoSnapshotProjection.source(masterServiceOfferNode, masterServiceOffers)
    )

    val offerByVariant = RepoSnapshotProjection.requiredJoin(
      operationName = BuildOperationName,
      joined = offersById,
      key = (variant: MasterServiceOfferVariant) => variant.masterServiceOfferId,
      rootId = (variant: MasterServiceOfferVariant) => variant.id,
    )
    val locationByVariant = RepoSnapshotProjection.requiredJoin(
      operationName = BuildOperationName,
      joined = locationsById,
      key = (variant: MasterServiceOfferVariant) => variant.masterLocationId,
      rootId = (variant: MasterServiceOfferVariant) => variant.id,
    )

    RepoSnapshotProjection.projectRoots(masterServiceOfferVariants) {
      variant =>
        for {
          offer <- offerByVariant(variant)
          service <- RepoSnapshotProjection.requiredByKey(
            operationName = BuildOperationName,
            joined = servicesById,
            key = offer.serviceId,
            rootId = variant.id,
          )
          category <- RepoSnapshotProjection.requiredByKey(
            operationName = BuildOperationName,
            joined = categoriesById,
            key = service.categoryId,
            rootId = variant.id,
          )
          master <- RepoSnapshotProjection.requiredByKey(
            operationName = BuildOperationName,
            joined = mastersById,
            key = offer.masterId,
            rootId = variant.id,
          )
          location <- locationByVariant(variant)
          _ <- RepoSnapshotProjection.checkInvariant(
            location.masterId == offer.masterId,
            QueryFailure.domain(
              s"$BuildOperationName: variant ${variant.id} joins offer ${offer.id} and location ${location.id} from different masters (${offer.masterId} != ${location.masterId})"
            ),
          )
          _ <- validateAgainstSchema(variant, service.id, RepoSnapshotProjection.optionalLookup(schemasByServiceId, service.id))
        } yield buildDocument(
          variant = variant,
          service = service,
          category = category,
          master = master,
          location = location,
        )
    }
  }

  private def buildDocument(
    variant: MasterServiceOfferVariant,
    service: Service,
    category: Category,
    master: Master,
    location: MasterLocation,
  ): VariantSearchDocument = {
    val enumAttributes = variant.enumAttributes.iterator.collect {
      case (definition: EnumAttributeDefinition[?], value) => definition.code -> value.stringCode
    }.toMap
    val booleanAttributes = variant.booleanAttributes.iterator.map {
      case (definition, value) => definition.code -> value
    }.toMap
    val intAttributes = variant.intAttributes.iterator.map {
      case (definition, value) => definition.code -> value
    }.toMap
    val bigDecimalAttributes = variant.bigDecimalAttributes.iterator.map {
      case (definition, value) => definition.code -> value
    }.toMap
    val attributeTokens = makeAttributeTokens(enumAttributes, booleanAttributes, intAttributes, bigDecimalAttributes)
    val serviceText     = normalizeText(List(service.name, category.name))
    val attributeText   = normalizeText(attributeTokens)
    val providerText    = normalizeText(List(master.name, location.name))
    val locationText    = normalizeText(List(location.name, location.address, category.name))
    val allText         = normalizeText(List(serviceText, attributeText, providerText, locationText))

    VariantSearchDocument(
      variantId = variant.id,
      masterServiceOfferId = variant.masterServiceOfferId,
      masterLocationId = variant.masterLocationId,
      masterId = master.id,
      serviceId = service.id,
      categoryId = category.id,
      serviceName = service.name,
      categoryName = category.name,
      masterName = master.name,
      locationName = location.name,
      address = location.address,
      location = SearchGeoPoint(location.lat, location.lon),
      lat = location.lat,
      lon = location.lon,
      priceFrom = variant.priceFrom,
      priceTo = variant.priceTo,
      durationMin = variant.durationMin,
      enumAttributes = enumAttributes,
      booleanAttributes = booleanAttributes,
      intAttributes = intAttributes,
      bigDecimalAttributes = bigDecimalAttributes,
      allText = allText,
      serviceText = serviceText,
      attributeText = attributeText,
      providerText = providerText,
      locationText = locationText,
    )
  }

  private def validateAgainstSchema(
    variant: MasterServiceOfferVariant,
    serviceId: ServiceId,
    schema: Option[ServiceVariantSchema],
  ): Either[QueryFailure, Unit] =
    schema match {
      case Some(value) =>
        value.validate(variant.attributes).left.map {
          error =>
            QueryFailure.domain(s"$BuildOperationName: variant ${variant.id} violates schema for service $serviceId: $error")
        }
      case None =>
        Right(())
    }

  private def makeAttributeTokens(
    enumAttributes: Map[String, String],
    booleanAttributes: Map[String, Boolean],
    intAttributes: Map[String, Int],
    bigDecimalAttributes: Map[String, BigDecimal],
  ): List[String] = {
    val enumTokens = enumAttributes.toList.flatMap {
      case (code, value) =>
        List(code, value, humanize(code), humanize(value))
    }
    val booleanTokens = booleanAttributes.toList.flatMap {
      case (code, value) =>
        List(code, humanize(code), value.toString)
    }
    val intTokens = intAttributes.toList.flatMap {
      case (code, value) =>
        List(code, humanize(code), value.toString)
    }
    val decimalTokens = bigDecimalAttributes.toList.flatMap {
      case (code, value) =>
        List(code, humanize(code), value.toString())
    }

    enumTokens ++ booleanTokens ++ intTokens ++ decimalTokens
  }

  private def humanize(value: String): String =
    value.replace('_', ' ')

  private def normalizeText(parts: Iterable[String]): String =
    parts.iterator.map(_.trim).filter(_.nonEmpty).mkString(" ")
}
