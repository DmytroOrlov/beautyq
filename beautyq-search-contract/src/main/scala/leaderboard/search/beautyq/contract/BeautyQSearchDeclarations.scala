package leaderboard.search.beautyq.contract

import leaderboard.model.{Category, Master, MasterLocation, MasterServiceOffer, MasterServiceOfferVariant, QueryFailure, Service, ServiceVariantSchema}
import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, VariantSearchDocument}
import leaderboard.search.dsl.*
import leaderboard.search.dsl.BeautyQSearchFieldSemantics.*
import leaderboard.search.dsl.BeautyQSearchPresentation.BoostRoles

/** Business-facing BeautyQ declarations expressed with the reusable catalog
  * and query-schema DSLs. This is intentionally a small, pure front door: it
  * declares catalog topology and the public query surface, but never loads
  * data, constructs clients, or selects a runtime backend.
  *
  * Document fields remain owned by [[BeautyQVariantSearchDocumentContract]];
  * the query names and their constraint/facet policy remain explicit here
  * because they are BeautyQ business contract, not derived storage metadata.
  */
object BeautyQSearchDeclarations {

  lazy val catalog =
    leaderboard.repo.catalog("beautyq")
      .branch[Category]
      .rootTree(_.parentId, root = Category.rootCategoryId)
      .child[Service](_.categoryId)
      .branch[Service]
      .value[ServiceVariantSchema](_.serviceId)
      .branch[Master]
      .rootAll
      .child[MasterLocation](_.masterId)
      .child[MasterServiceOffer](_.masterId)
      .branch[MasterServiceOffer]
      .child[MasterServiceOfferVariant](_.masterServiceOfferId)

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

  private val Fields = BeautyQVariantSearchDocumentContract.Fields

  private def fieldByCode(
    fields: Map[String, SearchField[VariantSearchDocument]],
    kind: String,
    code: String,
  ): Either[QueryFailure, SearchField[VariantSearchDocument]] =
    fields.get(code).toRight(QueryFailure.domain(s"Search $kind attribute '$code' is not defined for index '${BeautyQVariantSearchDocumentContract.documentSpec.indexName}'"))

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
}
