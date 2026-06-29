package leaderboard.search.document

import leaderboard.model.*
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.dsl.*

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
      fields = Fields.all,
    )

  lazy val qdrantPayloadSpec: SearchDocumentPayloadSpec[VariantSearchDocument] =
    SearchDocumentPayloadSpec(
      documentSpec = documentSpec,
      fields = List(
        Fields.variantId,
        Fields.masterLocationId,
        Fields.serviceId,
        Fields.serviceName,
      ),
    )

  lazy val querySchema: SearchQuerySchema[VariantSearchDocument] =
    SearchQuerySchema(
      serviceName = Fields.serviceName,
      categoryName = Fields.categoryName,
      priceFrom = Fields.priceFrom,
      durationMin = Fields.durationMin,
      location = Fields.location,
      enumAttribute = code => fieldByCode(Fields.enumAttributesByCode, "enum", code),
      booleanAttribute = code => fieldByCode(Fields.booleanAttributesByCode, "boolean", code),
      intAttribute = code => fieldByCode(Fields.intAttributesByCode, "int", code),
      decimalAttribute = code => fieldByCode(Fields.decimalAttributesByCode, "decimal", code),
    )

  private def fieldByCode(
    fields: Map[String, SearchField[VariantSearchDocument]],
    kind: String,
    code: String,
  ): Either[QueryFailure, SearchField[VariantSearchDocument]] =
    fields.get(code).toRight(QueryFailure.domain(s"Search $kind attribute '$code' is not defined for index '${documentSpec.indexName}'"))

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

  object Fields {
    val variantId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.variantId,
        _.toString,
        semantic = Some(SearchFieldSemantic.VariantId),
        filterable = true,
        sortable = true,
      )
    val masterServiceOfferId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.masterServiceOfferId,
        _.toString,
        semantic = Some(SearchFieldSemantic.MasterServiceOfferId),
        filterable = true,
      )
    val masterLocationId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.masterLocationId,
        _.toString,
        semantic = Some(SearchFieldSemantic.MasterLocationId),
        filterable = true,
        facetable = true,
      )
    val masterId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.masterId,
        _.toString,
        semantic = Some(SearchFieldSemantic.MasterId),
        filterable = true,
      )
    val serviceId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.serviceId,
        _.toString,
        semantic = Some(SearchFieldSemantic.ServiceId),
        filterable = true,
        facetable = true,
      )
    val serviceName: SearchField[VariantSearchDocument] =
      SearchField.keyword(
        _.serviceName,
        semantic = Some(SearchFieldSemantic.ServiceName),
        filterable = true,
        facetable = true,
      )
    val categoryId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.categoryId,
        _.toString,
        semantic = Some(SearchFieldSemantic.CategoryId),
        filterable = true,
        facetable = true,
      )
    val categoryName: SearchField[VariantSearchDocument] =
      SearchField.keyword(
        _.categoryName,
        semantic = Some(SearchFieldSemantic.CategoryName),
        filterable = true,
        facetable = true,
      )
    val masterName: SearchField[VariantSearchDocument] =
      SearchField.keyword(_.masterName)
    val locationName: SearchField[VariantSearchDocument] =
      SearchField.keyword(_.locationName)
    val address: SearchField[VariantSearchDocument] =
      SearchField.keyword(_.address)
    val lat: SearchField[VariantSearchDocument] =
      SearchField.decimal(_.lat)
    val lon: SearchField[VariantSearchDocument] =
      SearchField.decimal(_.lon)
    val priceFrom: SearchField[VariantSearchDocument] =
      SearchField.decimal(
        _.priceFrom,
        semantic = Some(SearchFieldSemantic.PriceFrom),
        filterable = true,
        facetable = true,
        sortable = true,
      )
    val priceTo: SearchField[VariantSearchDocument] =
      SearchField.decimal(
        _.priceTo,
        semantic = Some(SearchFieldSemantic.PriceTo),
        filterable = true,
        sortable = true,
      )
    val durationMin: SearchField[VariantSearchDocument] =
      SearchField.integer(
        _.durationMin,
        semantic = Some(SearchFieldSemantic.DurationMin),
        filterable = true,
        facetable = true,
        sortable = true,
      )
    val location: SearchField[VariantSearchDocument] =
      SearchField.geoPoint(
        _.location,
        semantic = Some(SearchFieldSemantic.Location),
        sortable = true,
      )
    val allText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.allText,
        semantic = Some(SearchFieldSemantic.AllText),
        searchable = true,
        boost = 4.0,
      )
    val serviceText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.serviceText,
        semantic = Some(SearchFieldSemantic.ServiceText),
        searchable = true,
        boost = 5.0,
      )
    val attributeText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.attributeText,
        semantic = Some(SearchFieldSemantic.AttributeText),
        searchable = true,
        boost = 4.0,
      )
    val providerText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.providerText,
        semantic = Some(SearchFieldSemantic.ProviderText),
        searchable = true,
        boost = 2.0,
      )
    val locationText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.locationText,
        semantic = Some(SearchFieldSemantic.LocationText),
        searchable = true,
        boost = 2.5,
      )

    val enumAttributesByCode: Map[String, SearchField[VariantSearchDocument]] =
      AttributeDefinition.enumDefinitions.map {
        definition =>
          definition.code -> SearchField[VariantSearchDocument](
            path = s"enumAttributes.${definition.code}",
            kind = SearchFieldKind.Keyword,
            extract = document => document.enumAttributes.get(definition.code).map(SearchValue.Keyword.apply),
            semantic = Some(SearchFieldSemantic.EnumAttribute(definition.code)),
            filterable = true,
            facetable = true,
          )
      }.toMap

    val booleanAttributesByCode: Map[String, SearchField[VariantSearchDocument]] =
      AttributeDefinition.booleanDefinitions.map {
        definition =>
          definition.code -> SearchField[VariantSearchDocument](
            path = s"booleanAttributes.${definition.code}",
            kind = SearchFieldKind.Boolean,
            extract = document => document.booleanAttributes.get(definition.code).map(SearchValue.Boolean.apply),
            semantic = Some(SearchFieldSemantic.BooleanAttribute(definition.code)),
            filterable = true,
            facetable = true,
          )
      }.toMap

    val intAttributesByCode: Map[String, SearchField[VariantSearchDocument]] =
      AttributeDefinition.intDefinitions.map {
        definition =>
          definition.code -> SearchField[VariantSearchDocument](
            path = s"intAttributes.${definition.code}",
            kind = SearchFieldKind.Integer,
            extract = document => document.intAttributes.get(definition.code).map(SearchValue.Integer.apply),
            semantic = Some(SearchFieldSemantic.IntAttribute(definition.code)),
            filterable = true,
            facetable = true,
            sortable = true,
          )
      }.toMap

    val decimalAttributesByCode: Map[String, SearchField[VariantSearchDocument]] =
      AttributeDefinition.bigDecimalDefinitions.map {
        definition =>
          definition.code -> SearchField[VariantSearchDocument](
            path = s"bigDecimalAttributes.${definition.code}",
            kind = SearchFieldKind.Decimal,
            extract = document => document.bigDecimalAttributes.get(definition.code).map(SearchValue.Decimal.apply),
            semantic = Some(SearchFieldSemantic.DecimalAttribute(definition.code)),
            filterable = true,
            facetable = true,
            sortable = true,
          )
      }.toMap

    val staticFields: List[SearchField[VariantSearchDocument]] =
      List(
        variantId,
        masterServiceOfferId,
        masterLocationId,
        masterId,
        serviceId,
        serviceName,
        categoryId,
        categoryName,
        masterName,
        locationName,
        address,
        lat,
        lon,
        priceFrom,
        priceTo,
        durationMin,
        location,
        allText,
        serviceText,
        attributeText,
        providerText,
        locationText,
      )

    val dynamicAttributeFields: List[SearchField[VariantSearchDocument]] =
      AttributeDefinition.all.flatMap {
        case definition: EnumAttributeDefinition[?]       => enumAttributesByCode.get(definition.code).toList
        case definition: BooleanAttributeDefinition       => booleanAttributesByCode.get(definition.code).toList
        case definition: IntAttributeDefinition           => intAttributesByCode.get(definition.code).toList
        case definition: BigDecimalAttributeDefinition    => decimalAttributesByCode.get(definition.code).toList
      }

    val all: List[SearchField[VariantSearchDocument]] =
      staticFields ++ dynamicAttributeFields
  }
}
