package leaderboard.search.document

import leaderboard.model.*
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.dsl.{SearchDocumentSpec, SearchField, SearchFieldKind, SearchFieldSemantic, SearchGeoPoint, SearchValue}

object BeautyQVariantSearchDocumentSchema {
  private val BuildOperationName = "build-variant-search-documents"

  private val categoryNode                  = Categories.entity.node(_.id)
  private val serviceNode                   = Services.entity.node(_.id)
  private val masterNode                    = Masters.entity.node(_.id)
  private val masterLocationNode            = MasterLocations.entity.node(_.id)
  private val masterServiceOfferNode        = MasterServiceOffers.entity.node(_.id)

  lazy val documentSpec: SearchDocumentSpec[VariantSearchDocument] =
    SearchDocumentSpec(
      indexName = "beautyq_variant_v1",
      id = _.variantId.toString,
      fields = baseFields ++ dynamicAttributeFields,
    )

  lazy val qdrantPayloadSpec: SearchDocumentPayloadSpec[VariantSearchDocument] =
    SearchDocumentPayloadSpec(
      documentSpec = documentSpec,
      fieldPaths = List(
        "variantId",
        "masterLocationId",
        "serviceId",
        "serviceName",
      ),
    )

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

  private val baseFields: List[SearchField[VariantSearchDocument]] =
    List(
      SearchField(
        path = "variantId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.variantId.toString)),
        semantic = Some(SearchFieldSemantic.VariantId),
        filterable = true,
        sortable = true,
      ),
      SearchField(
        path = "masterServiceOfferId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterServiceOfferId.toString)),
        semantic = Some(SearchFieldSemantic.MasterServiceOfferId),
        filterable = true,
      ),
      SearchField(
        path = "masterLocationId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterLocationId.toString)),
        semantic = Some(SearchFieldSemantic.MasterLocationId),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "masterId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterId.toString)),
        semantic = Some(SearchFieldSemantic.MasterId),
        filterable = true,
      ),
      SearchField(
        path = "serviceId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.serviceId.toString)),
        semantic = Some(SearchFieldSemantic.ServiceId),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "serviceName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.serviceName)),
        semantic = Some(SearchFieldSemantic.ServiceName),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "categoryId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.categoryId.toString)),
        semantic = Some(SearchFieldSemantic.CategoryId),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "categoryName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.categoryName)),
        semantic = Some(SearchFieldSemantic.CategoryName),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "masterName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterName)),
      ),
      SearchField(
        path = "locationName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.locationName)),
      ),
      SearchField(
        path = "address",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.address)),
      ),
      SearchField(
        path = "lat",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.lat)),
      ),
      SearchField(
        path = "lon",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.lon)),
      ),
      SearchField(
        path = "priceFrom",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.priceFrom)),
        semantic = Some(SearchFieldSemantic.PriceFrom),
        filterable = true,
        facetable = true,
        sortable = true,
      ),
      SearchField(
        path = "priceTo",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.priceTo)),
        semantic = Some(SearchFieldSemantic.PriceTo),
        filterable = true,
        sortable = true,
      ),
      SearchField(
        path = "durationMin",
        kind = SearchFieldKind.Integer,
        extract = document => Some(SearchValue.Integer(document.durationMin)),
        semantic = Some(SearchFieldSemantic.DurationMin),
        filterable = true,
        facetable = true,
        sortable = true,
      ),
      SearchField(
        path = "location",
        kind = SearchFieldKind.GeoPoint,
        extract = document => Some(SearchValue.GeoPoint(document.location)),
        semantic = Some(SearchFieldSemantic.Location),
        sortable = true,
      ),
      SearchField(
        path = "allText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.allText)),
        semantic = Some(SearchFieldSemantic.AllText),
        searchable = true,
        boost = 4.0,
      ),
      SearchField(
        path = "serviceText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.serviceText)),
        semantic = Some(SearchFieldSemantic.ServiceText),
        searchable = true,
        boost = 5.0,
      ),
      SearchField(
        path = "attributeText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.attributeText)),
        semantic = Some(SearchFieldSemantic.AttributeText),
        searchable = true,
        boost = 4.0,
      ),
      SearchField(
        path = "providerText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.providerText)),
        semantic = Some(SearchFieldSemantic.ProviderText),
        searchable = true,
        boost = 2.0,
      ),
      SearchField(
        path = "locationText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.locationText)),
        semantic = Some(SearchFieldSemantic.LocationText),
        searchable = true,
        boost = 2.5,
      ),
    )

  private val dynamicAttributeFields: List[SearchField[VariantSearchDocument]] =
    AttributeDefinition.all.flatMap {
      case definition: EnumAttributeDefinition[?] =>
        List(
          SearchField(
            path = s"enumAttributes.${definition.code}",
            kind = SearchFieldKind.Keyword,
            extract = document => document.enumAttributes.get(definition.code).map(SearchValue.Keyword.apply),
            semantic = Some(SearchFieldSemantic.EnumAttribute(definition.code)),
            filterable = true,
            facetable = true,
          )
        )
      case definition: BooleanAttributeDefinition =>
        List(
          SearchField(
            path = s"booleanAttributes.${definition.code}",
            kind = SearchFieldKind.Boolean,
            extract = document => document.booleanAttributes.get(definition.code).map(SearchValue.Boolean.apply),
            semantic = Some(SearchFieldSemantic.BooleanAttribute(definition.code)),
            filterable = true,
            facetable = true,
          )
        )
      case definition: IntAttributeDefinition =>
        List(
          SearchField(
            path = s"intAttributes.${definition.code}",
            kind = SearchFieldKind.Integer,
            extract = document => document.intAttributes.get(definition.code).map(SearchValue.Integer.apply),
            semantic = Some(SearchFieldSemantic.IntAttribute(definition.code)),
            filterable = true,
            facetable = true,
            sortable = true,
          )
        )
      case definition: BigDecimalAttributeDefinition =>
        List(
          SearchField(
            path = s"bigDecimalAttributes.${definition.code}",
            kind = SearchFieldKind.Decimal,
            extract = document => document.bigDecimalAttributes.get(definition.code).map(SearchValue.Decimal.apply),
            semantic = Some(SearchFieldSemantic.DecimalAttribute(definition.code)),
            filterable = true,
            facetable = true,
            sortable = true,
          )
        )
    }
}
