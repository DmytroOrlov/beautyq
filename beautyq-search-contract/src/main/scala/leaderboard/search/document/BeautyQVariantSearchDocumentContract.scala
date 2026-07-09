package leaderboard.search.document

import leaderboard.model.*
import leaderboard.search.beautyq.contract.BeautyQSearchDeclarations
import leaderboard.search.dsl.*

/** The pure BeautyQ document contract for [[VariantSearchDocument]]: field
  * declarations plus document/Qdrant-payload schemas. The public query
  * declaration is owned by [[BeautyQSearchDeclarations]]; [[querySchema]] is
  * retained as a compatibility alias for existing document-contract callers.
  * This is a contract declaration only - no repository, materialization,
  * seed, ES/Qdrant client, or projection code lives here.
  */
object BeautyQVariantSearchDocumentContract {
  import BeautyQSearchFieldSemantics.*

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

  lazy val querySchema: SearchQuerySchema[VariantSearchDocument, SearchConstraint] =
    BeautyQSearchDeclarations.querySchema

  object Fields {
    val variantId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.variantId,
        _.toString,
        semantic = Some(VariantId),
        filterable = true,
        sortable = true,
      )
    val masterServiceOfferId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.masterServiceOfferId,
        _.toString,
        semantic = Some(MasterServiceOfferId),
        filterable = true,
      )
    val masterLocationId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.masterLocationId,
        _.toString,
        semantic = Some(MasterLocationId),
        filterable = true,
        facetable = true,
      )
    val masterId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.masterId,
        _.toString,
        semantic = Some(MasterId),
        filterable = true,
      )
    val serviceId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.serviceId,
        _.toString,
        semantic = Some(ServiceId),
        filterable = true,
        facetable = true,
      )
    val serviceName: SearchField[VariantSearchDocument] =
      SearchField.keyword(
        _.serviceName,
        semantic = Some(ServiceName),
        filterable = true,
        facetable = true,
      )
    val categoryId: SearchField[VariantSearchDocument] =
      SearchField.keywordRendered(
        _.categoryId,
        _.toString,
        semantic = Some(CategoryId),
        filterable = true,
        facetable = true,
      )
    val categoryName: SearchField[VariantSearchDocument] =
      SearchField.keyword(
        _.categoryName,
        semantic = Some(CategoryName),
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
        semantic = Some(PriceFrom),
        filterable = true,
        facetable = true,
        sortable = true,
      )
    val priceTo: SearchField[VariantSearchDocument] =
      SearchField.decimal(
        _.priceTo,
        semantic = Some(PriceTo),
        filterable = true,
        sortable = true,
      )
    val durationMin: SearchField[VariantSearchDocument] =
      SearchField.integer(
        _.durationMin,
        semantic = Some(DurationMin),
        filterable = true,
        facetable = true,
        sortable = true,
      )
    val location: SearchField[VariantSearchDocument] =
      SearchField.geoPoint(
        _.location,
        semantic = Some(Location),
        sortable = true,
      )
    val allText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.allText,
        semantic = Some(AllText),
        searchable = true,
        boost = 4.0,
      )
    val serviceText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.serviceText,
        semantic = Some(ServiceText),
        searchable = true,
        boost = 5.0,
      )
    val attributeText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.attributeText,
        semantic = Some(AttributeText),
        searchable = true,
        boost = 4.0,
      )
    val providerText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.providerText,
        semantic = Some(ProviderText),
        searchable = true,
        boost = 2.0,
      )
    val locationText: SearchField[VariantSearchDocument] =
      SearchField.text(
        _.locationText,
        semantic = Some(LocationText),
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
            semantic = Some(EnumAttribute(definition.code)),
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
            semantic = Some(BooleanAttribute(definition.code)),
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
            semantic = Some(IntAttribute(definition.code)),
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
            semantic = Some(DecimalAttribute(definition.code)),
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
