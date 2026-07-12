package leaderboard.search.beautyq.gen2.contract

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.search.gen2.contract.*

/** The executable BeautyQ Gen2 root: the real initial `catalog`/`variants` declaration, not a
  * placeholder rendering of the eventual full tree. See docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md
  * for the section-by-section delivery order this root grows into, and
  * docs/search/NEW_DOMAIN_ONBOARDING.md for the low-boilerplate `searchFields[Document]` authoring DSL
  * `variants.Fields` is built from - every field below declares only its selector, kind (or lets it be
  * inferred), and capabilities; the generic registry derives the rest.
  */
object BeautyQSearchDomainGen2 {

  object catalog {
    val topology =
      leaderboard.repo
        .catalog("beautyq")
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
  }

  object variants {

    object Fields {
      private val declarations = searchFields[VariantSearchDocumentGen2]("variants")

      val variantId =
        declarations
          .inferred(_.variantId)
          .sortable(SortMode.Value)
          .payloadEligible
          .declare

      val masterServiceOfferId =
        declarations
          .inferred(_.masterServiceOfferId)
          .declare

      val masterLocationId =
        declarations
          .inferred(_.masterLocationId)
          .groupable(GroupMode.Terms)
          .declare

      val masterId =
        declarations
          .inferred(_.masterId)
          .declare

      val serviceId =
        declarations
          .inferred(_.serviceId)
          .declare

      val serviceCode =
        declarations
          .inferred(_.serviceCode)
          .filterable(FilterOperator.Equal, FilterOperator.In)
          .facetable(FacetMode.Terms)
          .groupable(GroupMode.Terms)
          .payloadEligible
          .declare

      val serviceName =
        declarations
          .keyword(_.serviceName)
          .declare

      val categoryId =
        declarations
          .inferred(_.categoryId)
          .declare

      val categoryCode =
        declarations
          .inferred(_.categoryCode)
          .filterable(FilterOperator.Equal, FilterOperator.In)
          .facetable(FacetMode.Terms)
          .payloadEligible
          .declare

      val categoryName =
        declarations
          .keyword(_.categoryName)
          .declare

      val masterName =
        declarations
          .keyword(_.masterName)
          .declare

      val locationName =
        declarations
          .keyword(_.locationName)
          .declare

      val address =
        declarations
          .keyword(_.address)
          .declare

      val lat =
        declarations
          .inferred(_.lat)
          .declare

      val lon =
        declarations
          .inferred(_.lon)
          .declare

      val priceFrom =
        declarations
          .inferred(_.priceFrom)
          .filterable(FilterOperator.Range)
          .facetable(FacetMode.Range)
          .sortable(SortMode.Value)
          .payloadEligible
          .declare

      val priceTo =
        declarations
          .inferred(_.priceTo)
          .filterable(FilterOperator.Range)
          .facetable(FacetMode.Range)
          .payloadEligible
          .declare

      val durationMin =
        declarations
          .inferred(_.durationMin)
          .filterable(FilterOperator.Range)
          .facetable(FacetMode.Range)
          .sortable(SortMode.Value)
          .payloadEligible
          .declare

      val location =
        declarations
          .inferred(_.location)
          .filterable(FilterOperator.GeoDistance)
          .sortable(SortMode.Distance)
          .payloadEligible
          .declare

      val allText =
        declarations
          .text(_.allText)
          .searchable
          .declare

      val serviceText =
        declarations
          .text(_.serviceText)
          .searchable
          .declare

      val attributeText =
        declarations
          .text(_.attributeText)
          .searchable
          .declare

      val providerText =
        declarations
          .text(_.providerText)
          .searchable
          .declare

      val locationText =
        declarations
          .text(_.locationText)
          .searchable
          .declare

      val intAttributes =
        declarations
          .dynamicMap(
            _.intAttributes,
            AttributeDefinition.intDefinitions,
          )(_.code)
          .inferred
          .filterable(FilterOperator.Range)
          .facetable(FacetMode.Range)
          .sortable(SortMode.Value)
          .payloadEligible
          .declare

      val decimalAttributes =
        declarations
          .dynamicMap(
            _.bigDecimalAttributes,
            AttributeDefinition.bigDecimalDefinitions,
          )(_.code)
          .inferred
          .filterable(FilterOperator.Range)
          .facetable(FacetMode.Range)
          .sortable(SortMode.Value)
          .payloadEligible
          .declare

      val enumAttributes =
        declarations
          .dynamicMap(
            _.enumAttributes,
            AttributeDefinition.enumDefinitions,
          )(_.code)
          .keyword
          .filterable(FilterOperator.Equal, FilterOperator.In)
          .facetable(FacetMode.Terms)
          .payloadEligible
          .declare

      val booleanAttributes =
        declarations
          .dynamicMap(
            _.booleanAttributes,
            AttributeDefinition.booleanDefinitions,
          )(_.code)
          .inferred
          .filterable(FilterOperator.Equal, FilterOperator.In)
          .facetable(FacetMode.Terms)
          .payloadEligible
          .declare

      val intAttributesByCode     = intAttributes.byCode
      val decimalAttributesByCode = decimalAttributes.byCode
      val enumAttributesByCode    = enumAttributes.byCode
      val booleanAttributesByCode = booleanAttributes.byCode

      val staticFields           = declarations.staticFields
      val dynamicAttributeFields = declarations.dynamicFields
      val all                    = declarations.allFields

      val document = declarations.completeDocument(variantId)
    }

    val identity = Fields.variantId
    val document = Fields.document
  }

  val structure: SearchStructureTree =
    SearchStructureTree(
      "BeautyQSearchDomainGen2",
      Vector(
        SearchStructureNode.branch(
          "catalog",
          Vector(
            SearchStructureNode.indexed(
              "topology",
              catalog.topology.steps.map(_.summary),
            )
          ),
        ),
        SearchStructureNode.document(
          variants.document.id.value,
          variants.document,
        ),
      ),
    )

  def renderStructure: String =
    SearchStructureRenderer.render(structure)
}
