package leaderboard.search.document

import leaderboard.model.*
import leaderboard.repo.BeautyQCatalogGraph
import leaderboard.repo.ServiceVariantSchemas
import leaderboard.search.dsl.*

/** Materialization/projection compatibility surface for the BeautyQ variant
  * document. The contract-shaped declarations (`Fields`, `documentSpec`,
  * `qdrantPayloadSpec`, `querySchema`) are owned by
  * [[BeautyQVariantSearchDocumentContract]] in `beautyq-search-contract` and
  * only delegated to here for source compatibility; this object's own
  * responsibility is projecting a repo-backed [[BeautySearchCatalogSnapshot]]
  * into [[VariantSearchDocument]] values (`projection`/`project`/
  * `buildDocument`), which needs [[BeautyQCatalogGraph]] node handles and
  * [[ServiceVariantSchemas]] and therefore cannot live in the contract
  * module.
  */
object BeautyQVariantSearchDocumentSchema {
  private val BuildOperationName = "build-variant-search-documents"

  private val categoryNode                  = BeautyQCatalogGraph.Nodes.category
  private val serviceNode                   = BeautyQCatalogGraph.Nodes.service
  private val masterNode                    = BeautyQCatalogGraph.Nodes.master
  private val masterLocationNode            = BeautyQCatalogGraph.Nodes.masterLocation
  private val masterServiceOfferNode        = BeautyQCatalogGraph.Nodes.masterServiceOffer

  val Fields = BeautyQVariantSearchDocumentContract.Fields

  lazy val documentSpec: SearchDocumentSpec[VariantSearchDocument] = BeautyQVariantSearchDocumentContract.documentSpec
  lazy val qdrantPayloadSpec: SearchDocumentPayloadSpec[VariantSearchDocument] = BeautyQVariantSearchDocumentContract.qdrantPayloadSpec
  lazy val querySchema: SearchQuerySchema[VariantSearchDocument, SearchConstraint] = BeautyQVariantSearchDocumentContract.querySchema

  lazy val projection: SearchDocumentProjection[BeautySearchCatalogSnapshot, VariantSearchDocument] =
    SearchDocumentProjection(
      documentSpec = documentSpec,
      project = project,
    )

  def project(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] = {
    val categoriesById = SearchDocumentProjection.indexByKeyPreservingFirst(
      SearchDocumentProjection.source(categoryNode, snapshot.categories)
    )
    val servicesById = SearchDocumentProjection.indexByKeyPreservingFirst(
      SearchDocumentProjection.source(serviceNode, snapshot.services)
    )
    val schemasByServiceId = SearchDocumentProjection.indexByKeyPreservingFirst(
      SearchDocumentProjection.valueSource(ServiceVariantSchemas.valueSource, snapshot.serviceVariantSchemas)
    )
    val mastersById = SearchDocumentProjection.indexByKeyPreservingFirst(
      SearchDocumentProjection.source(masterNode, snapshot.masters)
    )
    val locationsById = SearchDocumentProjection.indexByKeyPreservingFirst(
      SearchDocumentProjection.source(masterLocationNode, snapshot.masterLocations)
    )
    val offersById = SearchDocumentProjection.indexByKeyPreservingFirst(
      SearchDocumentProjection.source(masterServiceOfferNode, snapshot.masterServiceOffers)
    )

    val offerByVariant = SearchDocumentProjection.requiredJoin(
      operationName = BuildOperationName,
      joined = offersById,
      key = (variant: MasterServiceOfferVariant) => variant.masterServiceOfferId,
      rootId = (variant: MasterServiceOfferVariant) => variant.id,
    )
    val locationByVariant = SearchDocumentProjection.requiredJoin(
      operationName = BuildOperationName,
      joined = locationsById,
      key = (variant: MasterServiceOfferVariant) => variant.masterLocationId,
      rootId = (variant: MasterServiceOfferVariant) => variant.id,
    )

    SearchDocumentProjection.projectRoots(snapshot.masterServiceOfferVariants) {
      variant =>
        for {
          offer <- offerByVariant(variant)
          service <- SearchDocumentProjection.requiredByKey(
            operationName = BuildOperationName,
            joined = servicesById,
            key = offer.serviceId,
            rootId = variant.id,
          )
          category <- SearchDocumentProjection.requiredByKey(
            operationName = BuildOperationName,
            joined = categoriesById,
            key = service.categoryId,
            rootId = variant.id,
          )
          master <- SearchDocumentProjection.requiredByKey(
            operationName = BuildOperationName,
            joined = mastersById,
            key = offer.masterId,
            rootId = variant.id,
          )
          location <- locationByVariant(variant)
          _ <- SearchDocumentProjection.checkInvariant(
            location.masterId == offer.masterId,
            QueryFailure.domain(
              s"$BuildOperationName: variant ${variant.id} joins offer ${offer.id} and location ${location.id} from different masters (${offer.masterId} != ${location.masterId})"
            ),
          )
          _ <- validateAgainstSchema(variant, service.id, SearchDocumentProjection.optionalLookup(schemasByServiceId, service.id))
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
