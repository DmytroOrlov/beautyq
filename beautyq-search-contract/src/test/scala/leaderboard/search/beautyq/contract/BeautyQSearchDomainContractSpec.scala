package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{SearchBackendId, SearchBackendKind, SearchFieldKind}
import leaderboard.search.document.BeautyQVariantSearchDocumentContract
import leaderboard.search.dsl.{BeautyQSearchIntentVocabulary, BeautyQSearchPresentation, BeautySearchSpecV1}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchDomainContractSpec extends AnyWordSpec {

  "BeautyQSearchDomainContract" should {
    "declare its domain id as beautyq" in {
      assert(BeautyQSearchDomainContract.domainId == "beautyq")
    }

    "label itself explicitly as not a complete SearchDomainSpec" in {
      assert(BeautyQSearchDomainContract.label.contains("not complete SearchDomainSpec"))
    }

    "reference the same catalog declaration as BeautyQCatalogDeclaration, unmodified" in {
      assert(BeautyQSearchDomainContract.catalog.descriptor eq BeautyQCatalogDeclaration.declaration)
    }

    "expose the exact catalog topology summaries in declaration order" in {
      assert(
        BeautyQSearchDomainContract.catalog.descriptor.steps.map(_.summary) ==
          Vector(
            "root:category:tree:parentId",
            "many:category->service:categoryId",
            "value:service->serviceVariantSchema:serviceId",
            "root:master:all",
            "many:master->masterLocation:masterId",
            "many:master->masterServiceOffer:masterId",
            "many:masterServiceOffer->masterServiceOfferVariant:masterServiceOfferId",
          )
      )
    }

    "reference the same document spec as BeautyQVariantSearchDocumentContract" in {
      assert(BeautyQSearchDomainContract.document eq BeautyQVariantSearchDocumentContract.documentSpec)
    }

    "reference the same Qdrant payload spec as BeautyQVariantSearchDocumentContract" in {
      assert(BeautyQSearchDomainContract.qdrantPayload eq BeautyQVariantSearchDocumentContract.qdrantPayloadSpec)
    }

    "reference the same query schema as BeautyQVariantSearchDocumentContract" in {
      assert(BeautyQSearchDomainContract.query eq BeautyQVariantSearchDocumentContract.querySchema)
    }

    "reference the same intent vocabulary as BeautyQSearchIntentVocabulary" in {
      assert(BeautyQSearchDomainContract.intent eq BeautyQSearchIntentVocabulary.vocabulary)
    }

    "reference the same search spec as BeautySearchSpecV1" in {
      assert(BeautyQSearchDomainContract.searchSpec eq BeautySearchSpecV1.spec)
    }

    "reference the same runtime spec as BeautySearchSpecV1" in {
      assert(BeautyQSearchDomainContract.runtime eq BeautySearchSpecV1.runtimeSpec)
    }

    "reference the same carousel spec as BeautySearchSpecV1.spec" in {
      assert(BeautyQSearchDomainContract.carousel eq BeautySearchSpecV1.spec.carouselSpec)
    }

    "reference the same facet spec as BeautySearchSpecV1.spec" in {
      assert(BeautyQSearchDomainContract.facets eq BeautySearchSpecV1.spec.facetSpec)
    }

    "reference the same evaluation section as BeautyQSearchEvaluationContract" in {
      assert(BeautyQSearchDomainContract.evaluation eq BeautyQSearchEvaluationContract.section)
    }

    "declare that an evaluation section now exists" in {
      assert(BeautyQSearchDomainContract.evaluationDeclared == true)
    }

    "declare no full SearchDomainSpec value yet" in {
      assert(BeautyQSearchDomainContract.fullSearchDomainSpecDeclared == false)
    }

    "reference the same result unit as BeautyQSearchResultUnitContract" in {
      assert(BeautyQSearchDomainContract.resultUnit eq BeautyQSearchResultUnitContract.variant)
    }

    "declare the exact BeautyQ variant result unit values" in {
      assert(BeautyQSearchDomainContract.resultUnit.id == "variant")
      assert(BeautyQSearchDomainContract.resultUnit.label == "BeautyQ variant result")
      assert(BeautyQSearchDomainContract.resultUnit.documentIndexName == "beautyq_variant_v1")
      assert(BeautyQSearchDomainContract.resultUnit.carouselLimitName == "variantSize")
    }

    "match the result unit descriptor against the existing contract owners" in {
      assert(BeautyQSearchDomainContract.resultUnit.documentIndexName == BeautyQVariantSearchDocumentContract.documentSpec.indexName)
      assert(BeautyQSearchDomainContract.resultUnit.carouselLimitName == BeautyQSearchPresentation.CarouselLimits.Variant)
    }

    "reference the same supported languages as BeautyQSearchLanguageContract" in {
      assert(BeautyQSearchDomainContract.languages eq BeautyQSearchLanguageContract.supported)
    }

    "declare the exact supported language codes" in {
      assert(BeautyQSearchDomainContract.languages.map(_.code) == List("de", "en", "ru"))
    }

    "not declare a mixed language" in {
      assert(!BeautyQSearchDomainContract.languages.map(_.code).contains("mixed"))
    }

    "reference the same generic fields as BeautyQSearchDocumentFieldContract" in {
      assert(BeautyQSearchDomainContract.fields eq BeautyQSearchDocumentFieldContract.fields)
    }

    "declare a generic field for every BeautyQ document field name" in {
      val genericNames = BeautyQSearchDomainContract.fields.map(_.name.value)
      assert(
        List(
          "variantId",
          "serviceName",
          "categoryName",
          "priceFrom",
          "durationMin",
          "location",
          "allText",
          "serviceText",
          "attributeText",
          "providerText",
          "locationText",
        ).forall(genericNames.contains)
      )
    }

    "declare exactly as many generic fields as the BeautyQ document spec" in {
      assert(BeautyQSearchDomainContract.fields.size == BeautyQVariantSearchDocumentContract.documentSpec.fields.size)
    }

    "not declare any generic field as SemanticText" in {
      assert(!BeautyQSearchDomainContract.fields.exists(_.kind == SearchFieldKind.SemanticText))
    }

    "map representative generic field kinds correctly" in {
      val kindByName = BeautyQSearchDomainContract.fields.map(field => field.name.value -> field.kind).toMap
      assert(kindByName("allText") == SearchFieldKind.Text)
      assert(kindByName("serviceText") == SearchFieldKind.Text)
      assert(kindByName("serviceName") == SearchFieldKind.Facet)
      assert(kindByName("categoryName") == SearchFieldKind.Facet)
      assert(kindByName("variantId") == SearchFieldKind.Keyword)
      assert(kindByName("priceFrom") == SearchFieldKind.Range)
      assert(kindByName("priceTo") == SearchFieldKind.Numeric)
      assert(kindByName("durationMin") == SearchFieldKind.Range)
      assert(kindByName("location") == SearchFieldKind.Geo)
    }

    "map any dynamic enum field to Facet when present" in {
      val kindByName = BeautyQSearchDomainContract.fields.map(field => field.name.value -> field.kind).toMap
      kindByName.foreach {
        case (name, kind) if name.startsWith("enumAttributes.") => assert(kind == SearchFieldKind.Facet)
        case _ => ()
      }
    }

    "map any dynamic boolean field to Facet when present" in {
      val kindByName = BeautyQSearchDomainContract.fields.map(field => field.name.value -> field.kind).toMap
      kindByName.foreach {
        case (name, kind) if name.startsWith("booleanAttributes.") => assert(kind == SearchFieldKind.Facet)
        case _ => ()
      }
    }

    "map any dynamic int field to Range when present" in {
      val kindByName = BeautyQSearchDomainContract.fields.map(field => field.name.value -> field.kind).toMap
      kindByName.foreach {
        case (name, kind) if name.startsWith("intAttributes.") => assert(kind == SearchFieldKind.Range)
        case _ => ()
      }
    }

    "map any dynamic decimal field to Range when present" in {
      val kindByName = BeautyQSearchDomainContract.fields.map(field => field.name.value -> field.kind).toMap
      kindByName.foreach {
        case (name, kind) if name.startsWith("bigDecimalAttributes.") => assert(kind == SearchFieldKind.Range)
        case _ => ()
      }
    }

    "reference the same runtime section as BeautyQSearchRuntimeContract" in {
      assert(BeautyQSearchDomainContract.runtimeSection eq BeautyQSearchRuntimeContract.section)
    }

    "declare the exact runtime backend ids and kinds" in {
      assert(BeautyQSearchDomainContract.runtimeSection.declarations.map(_.backendId) == List(SearchBackendId("elasticsearch"), SearchBackendId("qdrant")))
      assert(BeautyQSearchDomainContract.runtimeSection.declarations.map(_.kind) == List(SearchBackendKind.Elasticsearch, SearchBackendKind.Qdrant))
    }

    "declare Elasticsearch as full-text/facet/geo capable and not semantic-vector capable" in {
      val capabilities = BeautyQSearchRuntimeContract.elasticsearch.capabilities
      assert(capabilities.supportsFullText == true)
      assert(capabilities.supportsFacets == true)
      assert(capabilities.supportsGeo == true)
      assert(capabilities.supportsSemanticVector == false)
    }

    "declare Qdrant as semantic-vector capable and not full-text/facet/geo capable" in {
      val capabilities = BeautyQSearchRuntimeContract.qdrant.capabilities
      assert(capabilities.supportsFullText == false)
      assert(capabilities.supportsFacets == false)
      assert(capabilities.supportsGeo == false)
      assert(capabilities.supportsSemanticVector == true)
    }

    "reference the same response section as BeautyQSearchResponsePolicyContract" in {
      assert(BeautyQSearchDomainContract.response eq BeautyQSearchResponsePolicyContract.section)
    }

    "declare a generic response section with no single grouping policy" in {
      assert(BeautyQSearchDomainContract.response.grouping.isEmpty)
    }

    "declare a generic carousel policy using the variant result-unit limit" in {
      assert(BeautyQSearchDomainContract.response.carousel.exists(_.enabled))
      assert(BeautyQSearchDomainContract.response.carousel.flatMap(_.maxItems) == Some(10))
    }

    "declare generic facets including representative BeautyQ facet fields" in {
      assert(BeautyQSearchDomainContract.response.facets.exists(_.fields.nonEmpty))
      val facetFieldNames = BeautyQSearchDomainContract.response.facets.toList.flatMap(_.fields.map(_.value))
      assert(facetFieldNames.contains("serviceName"))
      assert(facetFieldNames.contains("categoryName"))
      assert(facetFieldNames.contains("priceFrom"))
      assert(facetFieldNames.contains("durationMin"))
    }

    "declare enabled inferred filters describing the FacetSpec threshold and min count" in {
      assert(BeautyQSearchDomainContract.response.inferredFilters.exists(_.enabled))
      val description = BeautyQSearchDomainContract.response.inferredFilters.map(_.description).getOrElse("")
      assert(description.contains("0.70"))
      assert(description.contains("2"))
    }

    "declare empty presentation labels and disabled debug flags" in {
      assert(BeautyQSearchDomainContract.response.presentation.labels == Map.empty)
      assert(BeautyQSearchDomainContract.response.debug.includeExplanation == false)
      assert(BeautyQSearchDomainContract.response.debug.includeScoreBreakdown == false)
    }

    "preserve BeautyQ-specific carousel limit names and values in response details" in {
      val details = BeautyQSearchResponsePolicyContract.details
      assert(details.variantCarouselLimitName == "variantSize")
      assert(details.providerCarouselLimitName == "providerSize")
      assert(details.serviceIntentCarouselLimitName == "serviceIntentSize")
      assert(details.variantCarouselMaxItems == 10)
      assert(details.providerCarouselMaxItems == 10)
      assert(details.serviceIntentCarouselMaxItems == 10)
    }

    "preserve BeautyQ-specific provider and service-intent group field names in response details" in {
      val details = BeautyQSearchResponsePolicyContract.details
      assert(details.providerGroupFieldName == "masterLocationId")
      assert(details.serviceIntentGroupFieldName == "serviceId")
    }

    "record catalog, evaluation, document-result-unit, intent-languages, document-field-kind-mapping, runtime-capabilities, and response-policy as the ready readiness sections" in {
      assert(
        BeautyQSearchDomainContract.searchDomainSpecReadiness.readySections ==
          List(
            "catalog",
            "evaluation",
            "document-result-unit",
            "intent-languages",
            "document-field-kind-mapping",
            "runtime-capabilities",
            "response-policy",
          )
      )
    }

    "record the exact pending decision ids" in {
      assert(
        BeautyQSearchDomainContract.searchDomainSpecReadiness.pendingDecisions.map(_.id) ==
          List("intent-section-mapping")
      )
    }

    "record the exact pending decision sections" in {
      assert(
        BeautyQSearchDomainContract.searchDomainSpecReadiness.pendingDecisions.map(_.section) ==
          List("intent")
      )
    }

    "explain why full SearchDomainSpec is still not declared" in {
      val reason = BeautyQSearchDomainContract.searchDomainSpecReadiness.pendingDecisions.head.reason
      assert(reason.contains("IntentSection"))
      assert(reason.contains("SearchIntentVocabulary"))
    }

    "derive fullSearchDomainSpecDeclared from the readiness value" in {
      assert(
        BeautyQSearchDomainContract.fullSearchDomainSpecDeclared ==
          BeautyQSearchDomainContract.searchDomainSpecReadiness.fullSearchDomainSpecDeclared
      )
    }

    "keep fullSearchDomainSpecDeclared false via non-empty pending decisions" in {
      assert(BeautyQSearchDomainContract.fullSearchDomainSpecDeclared == false)
    }

    "be fully usable from values available in beautyq-search-contract alone, with no repository/materialization/client construction" in {
      // Every assertion above reads a plain value already owned by this module;
      // none of them require constructing a repository, materializer, ES/Qdrant
      // client, route, or runtime service.
      assert(BeautyQSearchDomainContract.document.indexName == "beautyq_variant_v1")
    }
  }
}
