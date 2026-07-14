package leaderboard.search.beautyq.gen2.contract

import leaderboard.model.*
import leaderboard.search.gen2.contract.*

/** The executable BeautyQ Gen2 business root. Read this file first when authoring or reviewing the
  * domain, in data-flow order: `catalog` defines the source topology; `variants.Fields` defines the
  * document vocabulary and backend capabilities; `variants.document` closes the document contract;
  * `variants.request` and `variants.intent` expose the public inbound policies; `variants.plan` exposes
  * the executable plan policy (constraint-source precedence, geo-origin, facets, groups, plan modes and
  * the default-browse notice) that `BeautyQSearchPlanCompiler` composes with reusable Gen2 mechanics.
  * Backend/route wiring remains the next stage after this declaration and is intentionally not hidden
  * behind a second catalog tree.
  *
  * This is the real, current `catalog`/`variants` declaration, not a placeholder rendering of a future
  * tree. See docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md
  * for the section-by-section delivery order this root grew into, and
  * docs/search/NEW_DOMAIN_ONBOARDING.md for the low-boilerplate `searchFields[Document]` authoring DSL
  * that `variants.Fields` uses. Every field below declares only its selector, kind (or lets it be
  * inferred), and capabilities; the generic registry derives the rest.
  */
object BeautyQSearchDeclarations {

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

    /** Public request and intent branches are executable inventories, not a second hand-maintained
      * rendering. Open the referenced declarations to edit policy; these values expose the same
      * registries/vocabulary consumed by inbound validation. */
    object request {
      /** See `BeautyQPublicFilterRegistry.staticSpecs/dynamicSpecs` for public filter policy. */
      val publicFilters = BeautyQPublicFilterRegistry.fields
      /** See `BeautyQPublicSortRegistry` for public sort policy. */
      val publicSorts   = BeautyQPublicSortRegistry.names
      /** Facet IDs are derived directly from the executable plan policy; no second public facet list. */
      val publicFacets  = BeautyQSearchPlanPolicy.facetRegistry.ids
    }

    object intent {
      /** See `BeautyQIntentVocabulary.sourceRules` for aliases and contextual policy. */
      val vocabulary = BeautyQIntentVocabulary.value
    }

    /** The plan branch links directly to `BeautyQSearchPlanPolicy`'s own typed policy values - not a
      * second hand-maintained rendering - so a reviewer starting here can navigate straight to every
      * actual declaration. Open `BeautyQSearchPlanPolicy` to edit constraint-source precedence,
      * geo-origin policy, facet declarations, group policy or the default-browse notice. This root
      * links business policy only; compilation itself (`BeautyQSearchPlanCompiler`) is wiring
      * implementation and is never referenced from here. */
    object plan {
      // Direct typed references: the actual policy declarations a reviewer navigates to.
      val constraintPrecedence: ConstraintPrecedence[BeautyQConstraintSource] = BeautyQSearchPlanPolicy.constraintPrecedence
      val geoOriginPolicy: BeautyQGeoOriginPolicy                             = BeautyQSearchPlanPolicy.geoOriginPolicy
      val facetRegistry: FacetPlanRegistry[VariantSearchDocumentGen2]        = BeautyQSearchPlanPolicy.facetRegistry
      val groupPolicy: Vector[GroupRequest[VariantSearchDocumentGen2, ?]]     = BeautyQSearchPlanPolicy.groups
      val defaultBrowsePolicy: PlanDiagnostic                                 = BeautyQSearchPlanPolicy.defaultBrowseNotice
      val modeClassifier                                                     = BeautyQSearchPlanPolicy.classify _

      // Derived summaries for the generated structure tree below - always computed from the typed
      // policy values above, never a second hand-maintained rendering.
      val sourcePrecedence  = constraintPrecedence.sourceOrder.map(_.toString)
      val geoOrigin         = geoOriginPolicy.sourcePath
      val facets            = facetRegistry.ids
      val groups            = groupPolicy.map(_.id.value)
      val modes             = BeautyQSearchPlanMode.values.toVector.map(_.toString)
      val defaultBrowseCode = defaultBrowsePolicy.code.value

      /** Direct typed references to the executable candidate policy (Brick 4G-A). Compilation itself
        * (`BeautyQCandidatePlanCompiler`) is wiring implementation and is never referenced from here. */
      object candidate {
        val semanticTextParts: Vector[BeautyQSemanticTextPart] = BeautyQSemanticCandidatePolicy.semanticTextParts
        val eligibilityGates: Vector[BeautyQCandidateEligibilityGate] = BeautyQSemanticCandidatePolicy.eligibilityGates
      }
    }
  }

  /** Derived view is lazy so first touching policy values can initialize `variants.Fields` without
    * re-entering this root through `variants.plan`; the reverse first-touch order is equally safe. */
  lazy val structure: SearchStructureTree =
    SearchStructureTree(
      "BeautyQSearchDeclarations",
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
        SearchStructureNode.branch(
          "variants",
          Vector(
            SearchStructureNode.document("document", variants.document),
            SearchStructureNode.branch(
              "request",
              Vector(
                SearchStructureNode.indexed("public-filters", variants.request.publicFilters.map(_.name.value)),
                SearchStructureNode.indexed("public-sorts", variants.request.publicSorts.map(_.value)),
                SearchStructureNode.indexed("public-facets", variants.request.publicFacets.map(_.value)),
              ),
            ),
            SearchStructureNode.indexed("intent-rules", variants.intent.vocabulary.rules.map(_.id.value)),
            SearchStructureNode.branch(
              "plan",
              Vector(
                SearchStructureNode.indexed("source-precedence", variants.plan.sourcePrecedence),
                SearchStructureNode.leaf(s"geo-origin: ${variants.plan.geoOrigin}"),
                SearchStructureNode.indexed("facets", variants.plan.facets.map(_.value)),
                SearchStructureNode.indexed("groups", variants.plan.groups),
                SearchStructureNode.indexed("modes", variants.plan.modes),
                SearchStructureNode.leaf(s"default-browse-code: ${variants.plan.defaultBrowseCode}"),
                SearchStructureNode.branch(
                  "candidate",
                  Vector(
                    SearchStructureNode.indexed("semanticTextParts", variants.plan.candidate.semanticTextParts.map(_.stableId)),
                    SearchStructureNode.indexed("eligibilityGates", variants.plan.candidate.eligibilityGates.map(_.stableId)),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    )

  def renderStructure: String =
    SearchStructureRenderer.render(structure)
}
