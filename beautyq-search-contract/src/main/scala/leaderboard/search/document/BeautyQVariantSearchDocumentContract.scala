package leaderboard.search.document

import leaderboard.model.*
import leaderboard.search.dsl.*

/** The pure BeautyQ document/query contract for [[VariantSearchDocument]]:
  * field declarations, the document/Qdrant-payload/query schemas, and the
  * constraint/facet resolution helpers `querySchema` needs. This is a
  * contract declaration only - no repository, materialization, seed, ES/Qdrant
  * client, or projection code lives here. `leaderboard.repo.BeautyQCatalogGraph`
  * still owns repo-backed catalog graph/materialization wiring, and
  * `leaderboard.search.document.BeautyQVariantSearchDocumentMaterialization`
  * in `beautyq-search-materialization` owns projection from
  * `BeautyQSearchCatalogSnapshot` into `VariantSearchDocument` values,
  * built on the contract-shaped members (`Fields`, `documentSpec`,
  * `qdrantPayloadSpec`, `querySchema`) declared here.
  */
object BeautyQVariantSearchDocumentContract {
  import BeautyQSearchFieldSemantics.*
  import BeautyQSearchPresentation.BoostRoles

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
    searchQuery[VariantSearchDocument, SearchConstraint]
      .field("serviceName", Fields.serviceName)
      .field("categoryName", Fields.categoryName)
      .field("priceFrom", Fields.priceFrom)
      .field("durationMin", Fields.durationMin)
      .field("location", Fields.location)
      .geoScoring(Fields.location)
      .resolve(beautyQResolveConstraint)
      .facetConstraint(beautyQFacetConstraint)

  private def fieldByCode(
    fields: Map[String, SearchField[VariantSearchDocument]],
    kind: String,
    code: String,
  ): Either[QueryFailure, SearchField[VariantSearchDocument]] =
    fields.get(code).toRight(QueryFailure.domain(s"Search $kind attribute '$code' is not defined for index '${documentSpec.indexName}'"))

  private def beautyQResolveConstraint(
    constraint: SearchConstraint
  ): Either[QueryFailure, ResolvedSearchConstraint[VariantSearchDocument]] =
    constraint match {
      case SearchConstraint.ServiceAny(names) =>
        Right(ResolvedSearchConstraint.Terms(Fields.serviceName, names, BoostRoles.Service))
      case SearchConstraint.CategoryAny(names) =>
        Right(ResolvedSearchConstraint.Terms(Fields.categoryName, names, BoostRoles.Service))
      case SearchConstraint.EnumAttr(attributeCode, values) =>
        fieldByCode(Fields.enumAttributesByCode, "enum", attributeCode).map(ResolvedSearchConstraint.Terms(_, values, BoostRoles.Attribute))
      case SearchConstraint.BoolAttr(attributeCode, value) =>
        fieldByCode(Fields.booleanAttributesByCode, "boolean", attributeCode).map(ResolvedSearchConstraint.BooleanTerm(_, value, BoostRoles.Attribute))
      case SearchConstraint.IntRange(attributeCode, min, max) =>
        fieldByCode(Fields.intAttributesByCode, "int", attributeCode).map(ResolvedSearchConstraint.Range(_, min.map(BigDecimal(_)), max.map(BigDecimal(_)), BoostRoles.Attribute))
      case SearchConstraint.DecimalRange(attributeCode, min, max) =>
        fieldByCode(Fields.decimalAttributesByCode, "decimal", attributeCode).map(ResolvedSearchConstraint.Range(_, min, max, BoostRoles.Attribute))
      case SearchConstraint.PriceRange(min, max) =>
        Right(ResolvedSearchConstraint.Range(Fields.priceFrom, min, max, BoostRoles.Attribute))
      case SearchConstraint.DurationRange(min, max) =>
        Right(ResolvedSearchConstraint.Range(Fields.durationMin, min.map(BigDecimal(_)), max.map(BigDecimal(_)), BoostRoles.Attribute))
      case SearchConstraint.NearUser =>
        Right(ResolvedSearchConstraint.GeoDistance(Fields.location, BoostRoles.ProviderDistance))
    }

  private def beautyQFacetConstraint(
    facetField: FacetField[VariantSearchDocument],
    value: String,
  ): Either[QueryFailure, SearchConstraint] =
    facetField.field.semantic match {
      case Some(ServiceName) =>
        Right(SearchConstraint.ServiceAny(Set(value)))
      case Some(CategoryName) =>
        Right(SearchConstraint.CategoryAny(Set(value)))
      case Some(semantic) if semantic.value.startsWith("enumAttributes.") =>
        Right(SearchConstraint.EnumAttr(semantic.value.stripPrefix("enumAttributes."), Set(value)))
      case Some(semantic) if semantic.value.startsWith("booleanAttributes.") =>
        value.toBooleanOption match {
          case Some(boolValue) => Right(SearchConstraint.BoolAttr(semantic.value.stripPrefix("booleanAttributes."), boolValue))
          case None => Left(QueryFailure.domain(s"Facet value '$value' is not a boolean for ${facetField.path}"))
        }
      case Some(PriceFrom) =>
        rangeConstraint(facetField, value, SearchConstraint.PriceRange.apply)
      case Some(DurationMin) =>
        rangeConstraint(facetField, value, (min, max) => SearchConstraint.DurationRange(min.map(_.toInt), max.map(_.toInt)))
      case Some(semantic) if semantic.value.startsWith("intAttributes.") =>
        rangeConstraint(facetField, value, (min, max) => SearchConstraint.IntRange(semantic.value.stripPrefix("intAttributes."), min.map(_.toInt), max.map(_.toInt)))
      case Some(semantic) if semantic.value.startsWith("bigDecimalAttributes.") =>
        rangeConstraint(facetField, value, (min, max) => SearchConstraint.DecimalRange(semantic.value.stripPrefix("bigDecimalAttributes."), min, max))
      case other =>
        Left(QueryFailure.domain(s"Facet field '${facetField.path}' with semantic $other cannot be converted into a search constraint"))
    }

  private def rangeConstraint(
    facetField: FacetField[VariantSearchDocument],
    value: String,
    build: (Option[BigDecimal], Option[BigDecimal]) => SearchConstraint,
  ): Either[QueryFailure, SearchConstraint] =
    facetField.mode match {
      case FacetFieldMode.Ranges(buckets) =>
        buckets.find(_.key == value) match {
          case Some(bucket) => Right(build(bucket.min, bucket.max))
          case None => Left(QueryFailure.domain(s"Range bucket '$value' is not defined for facet '${facetField.path}'"))
        }
      case _ =>
        Left(QueryFailure.domain(s"Facet '${facetField.path}' is not range-based"))
    }

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
