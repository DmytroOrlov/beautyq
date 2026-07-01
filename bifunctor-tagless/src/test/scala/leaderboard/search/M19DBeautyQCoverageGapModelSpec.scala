package leaderboard.search

import io.circe.parser.parse
import leaderboard.search.eval.*
import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.{
  BeautyResponseComponentId,
  BeautyQResponseComponentTaxonomy,
}
import leaderboard.search.eval.M19DBeautyQCoverageGapModel.{
  BeautyQCoverageGapModel,
  ComponentCoverageGap,
  ComponentCoverageState,
  PolicyEvidenceState,
  QueryCoverageRow,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M19DBeautyQCoverageGapModelSpec extends AnyWordSpec {

  "M19DBeautyQCoverageGapModel.BeautyQCoverageGapModel.fromTaxonomy" should {

    "report the offline/eval-only boundary and never claim serving-policy status" in {
      val model = buildModel(Nil)

      assert(model.offlineEvalOnly)
      assert(model.notServingPolicy)
      assert(model.doesNotApproveHybrid)
      assert(model.qdrantDoesNotOwnFacets)
      assert(model.qdrantDoesNotOwnInferredFilters)
    }

    "aggregate to zero per-component coverage when the source has no eval queries" in {
      val model = buildModel(Nil)

      model.componentGaps.foreach { gap =>
        assert(gap.totalQueryCount == 0, s"${gap.componentId.render} should report zero queries")
        assert(gap.sourceConfirmedQueryCount == 0)
        assert(gap.notInformedByQueryCount == 0)
        assert(gap.fieldAbsentInSourceCount == 0)
      }
    }

    "report all queries as `FieldAbsentInSource` for facets and inferred filters" in {
      val model = buildModel(List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true)))

      val facets  = gapFor(model, BeautyResponseComponentId.Facets)
      val inferred = gapFor(model, BeautyResponseComponentId.InferredFilters)

      assert(facets.fieldAbsentInSourceCount == 1)
      assert(facets.notInformedByQueryCount == 0)
      assert(facets.sourceConfirmedQueryCount == 0)
      assert(facets.totalQueryCount == 1)
      assert(inferred.fieldAbsentInSourceCount == 1)
      assert(inferred.notInformedByQueryCount == 0)
      assert(inferred.sourceConfirmedQueryCount == 0)
      assert(inferred.totalQueryCount == 1)
    }

    "preserve per-query state as either SourceConfirmed or NotInformedByQuery for the three carousels" in {
      val model = buildModel(
        List(
          sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true),
          sampleQuery("q_demo_002", hasVariant = true, hasProvider = false, hasService = true),
          sampleQuery("q_demo_003", hasVariant = false, hasProvider = false, hasService = false),
        )
      )

      val variant  = gapFor(model, BeautyResponseComponentId.VariantCarousel)
      val provider = gapFor(model, BeautyResponseComponentId.ProviderCarousel)
      val service  = gapFor(model, BeautyResponseComponentId.ServiceIntentCarousel)

      assert(variant.sourceConfirmedQueryCount == 2)
      assert(variant.notInformedByQueryCount == 1)
      assert(variant.fieldAbsentInSourceCount == 0)
      assert(variant.totalQueryCount == 3)

      assert(provider.sourceConfirmedQueryCount == 1)
      assert(provider.notInformedByQueryCount == 2)
      assert(provider.fieldAbsentInSourceCount == 0)

      assert(service.sourceConfirmedQueryCount == 2)
      assert(service.notInformedByQueryCount == 1)
      assert(service.fieldAbsentInSourceCount == 0)
    }

    "mark every component with facets/inferred-filters evidence as policy-blocked" in {
      val model = buildModel(
        List(
          sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true),
          sampleQuery("q_demo_002", hasVariant = true, hasProvider = true, hasService = true),
        )
      )

      val blocked = model.componentGaps.collect { case gap if gap.policyBlocked => gap.componentId }
      assert(blocked.contains(BeautyResponseComponentId.Facets))
      assert(blocked.contains(BeautyResponseComponentId.InferredFilters))
      assert(blocked.contains(BeautyResponseComponentId.ProviderCarousel))
      assert(blocked.contains(BeautyResponseComponentId.ServiceIntentCarousel))
    }

    "emit NeedsMoreEvidence for every undercovered component policy decision" in {
      val model = buildModel(
        List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true))
      )

      assert(model.policyBlockedDecisions.nonEmpty)
      model.policyBlockedDecisions.foreach { decision =>
        assert(decision.state == PolicyEvidenceState.NeedsMoreEvidence, s"${decision.decisionId} should be NeedsMoreEvidence")
        assert(decision.reason.nonEmpty)
      }
    }

    "list facets and inferred-filters ownership decisions as NeedsMoreEvidence" in {
      val model = buildModel(
        List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true))
      )

      val decisionIds = model.policyBlockedDecisions.map(_.decisionId).toSet
      assert(decisionIds.contains("facets_ownership"))
      assert(decisionIds.contains("inferred_filter_ownership"))
      assert(decisionIds.contains("facets_facet_field_coverage"))
      assert(decisionIds.contains("inferred_filter_threshold_evidence"))
      assert(decisionIds.contains("provider_carousel_projection_ownership"))
      assert(decisionIds.contains("service_intent_carousel_projection_ownership"))
    }

    "not authorize provider/service/facet/inferred-filter policy from candidate evidence alone" in {
      val model = buildModel(
        List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true))
      )

      val providerGap = gapFor(model, BeautyResponseComponentId.ProviderCarousel)
      val serviceGap  = gapFor(model, BeautyResponseComponentId.ServiceIntentCarousel)
      val facetsGap   = gapFor(model, BeautyResponseComponentId.Facets)
      val inferredGap = gapFor(model, BeautyResponseComponentId.InferredFilters)

      // The provider/service carousels have variant-candidate evidence, but that does
      // not authorize a provider/service policy: they remain policy-blocked with
      // NeedsMoreEvidence reasons.
      assert(providerGap.policyBlocked)
      assert(serviceGap.policyBlocked)
      assert(providerGap.policyBlockingReasons.exists(_.contains("NeedsMoreEvidence")))
      assert(serviceGap.policyBlockingReasons.exists(_.contains("NeedsMoreEvidence")))

      // Facets and inferred filters are blocked because their source fields are
      // absent from the listed JSON and Qdrant is not an owner.
      assert(facetsGap.policyBlocked)
      assert(inferredGap.policyBlocked)
      assert(facetsGap.policyBlockingReasons.exists(reason => reason.contains("Qdrant")))
      assert(inferredGap.policyBlockingReasons.exists(reason => reason.contains("Qdrant")))
    }

    "never emit a NeedsMoreEvidence decision for the variant carousel" in {
      val model = buildModel(
        List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true))
      )

      val variantDecisions = model.policyBlockedDecisions.filter(_.componentId == BeautyResponseComponentId.VariantCarousel)
      assert(variantDecisions.isEmpty)
      val variantGap = gapFor(model, BeautyResponseComponentId.VariantCarousel)
      assert(!variantGap.policyBlocked)
      assert(variantGap.policyBlockingReasons.isEmpty)
    }

    "make aggregate per-component counts sum to total query count" in {
      val model = buildModel(
        List(
          sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true),
          sampleQuery("q_demo_002", hasVariant = true, hasProvider = false, hasService = true),
          sampleQuery("q_demo_003", hasVariant = false, hasProvider = false, hasService = false),
          sampleQuery("q_demo_004", hasVariant = true, hasProvider = true, hasService = true),
        )
      )

      model.componentGaps.foreach { gap =>
        val sum = gap.sourceConfirmedQueryCount + gap.notInformedByQueryCount + gap.fieldAbsentInSourceCount
        assert(sum == gap.totalQueryCount, s"${gap.componentId.render} coverage sum $sum != total ${gap.totalQueryCount}")
      }
    }

    "expose undercovered components in the stable component order" in {
      val model = buildModel(
        List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true))
      )

      // Provider, ServiceIntent, Facets, InferredFilters - in stable component order. Variant is not undercovered.
      assert(model.undercoveredComponents == List(
        BeautyResponseComponentId.ProviderCarousel,
        BeautyResponseComponentId.ServiceIntentCarousel,
        BeautyResponseComponentId.Facets,
        BeautyResponseComponentId.InferredFilters,
      ))
    }
  }

  "M19DBeautyQCoverageGapModel.QueryCoverageRow.fromEvalCoverage" should {

    "preserve available fields and mark absent JSON fields as FieldAbsentInSource" in {
      val taxonomy = BeautyQResponseComponentTaxonomy.fromEvalQueries(
        List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true))
      )
      val row = onlyRow(BeautyQCoverageGapModel.fromTaxonomy(taxonomy))

      assert(row.queryId == "q_demo_001")
      assert(row.queryText.contains("manicure"))
      assert(row.informVariantRecall == ComponentCoverageState.SourceConfirmed)
      assert(row.informProviderCarousel == ComponentCoverageState.SourceConfirmed)
      assert(row.informServiceIntentCarousel == ComponentCoverageState.SourceConfirmed)
      assert(row.informFacets == ComponentCoverageState.FieldAbsentInSource)
      assert(row.informInferredFilters == ComponentCoverageState.FieldAbsentInSource)
      assert(row.candidateLevelOnly)
    }

    "mark absent per-query expectations as NotInformedByQuery without inventing facets/inferred-filters fields" in {
      val taxonomy = BeautyQResponseComponentTaxonomy.fromEvalQueries(
        List(sampleQuery("q_demo_002", hasVariant = true, hasProvider = false, hasService = false))
      )
      val row = onlyRow(BeautyQCoverageGapModel.fromTaxonomy(taxonomy))

      assert(row.informVariantRecall == ComponentCoverageState.SourceConfirmed)
      assert(row.informProviderCarousel == ComponentCoverageState.NotInformedByQuery)
      assert(row.informServiceIntentCarousel == ComponentCoverageState.NotInformedByQuery)
      assert(row.informFacets == ComponentCoverageState.FieldAbsentInSource)
      assert(row.informInferredFilters == ComponentCoverageState.FieldAbsentInSource)
    }
  }

  "M19DBeautyQCoverageGapModel over the listed eval-query JSON" should {

    "agree on the known query count and never invent facets or inferred-filters expectations" in {
      val queries = loadEvalQueries()
      val model   = buildModel(queries)

      assert(queries.size == 89)
      assert(model.totalQueryCount == 89)
      assert(model.queryRows.size == 89)
    }

    "report zero source-confirmed facets or inferred-filters coverage over the full dataset" in {
      val queries = loadEvalQueries()
      val model   = buildModel(queries)

      val facets  = gapFor(model, BeautyResponseComponentId.Facets)
      val inferred = gapFor(model, BeautyResponseComponentId.InferredFilters)

      assert(facets.sourceConfirmedQueryCount == 0)
      assert(facets.fieldAbsentInSourceCount == 89)
      assert(facets.policyBlocked)
      assert(inferred.sourceConfirmedQueryCount == 0)
      assert(inferred.fieldAbsentInSourceCount == 89)
      assert(inferred.policyBlocked)
    }

    "report the full 89-query set as source-confirmed for the three carousels" in {
      val queries = loadEvalQueries()
      val model   = buildModel(queries)

      List(
        BeautyResponseComponentId.VariantCarousel,
        BeautyResponseComponentId.ProviderCarousel,
        BeautyResponseComponentId.ServiceIntentCarousel,
      ).foreach { componentId =>
        val gap = gapFor(model, componentId)
        assert(gap.sourceConfirmedQueryCount == 89, s"${componentId.render} should be SourceConfirmed for all 89 queries")
        assert(gap.notInformedByQueryCount == 0)
        assert(gap.fieldAbsentInSourceCount == 0)
      }
    }

    "preserve query-class diversity from the listed query type tags" in {
      val queries = loadEvalQueries()
      val model   = buildModel(queries)

      val present = model.queryClassCounts.collect {
        case (Some(cls), count) if count > 0 => cls
      }
      assert(present.nonEmpty)
      assert(present.size >= 3)
    }

    "emit NeedsMoreEvidence reasons for every undercovered component over the full dataset" in {
      val queries = loadEvalQueries()
      val model   = buildModel(queries)

      assert(model.undercoveredComponents.contains(BeautyResponseComponentId.Facets))
      assert(model.undercoveredComponents.contains(BeautyResponseComponentId.InferredFilters))
      assert(model.undercoveredComponents.contains(BeautyResponseComponentId.ProviderCarousel))
      assert(model.undercoveredComponents.contains(BeautyResponseComponentId.ServiceIntentCarousel))
      assert(!model.undercoveredComponents.contains(BeautyResponseComponentId.VariantCarousel))

      model.policyBlockedDecisions.foreach { decision =>
        assert(decision.state == PolicyEvidenceState.NeedsMoreEvidence)
        assert(decision.reason.nonEmpty)
      }
    }
  }

  private def buildModel(queries: List[BeautyQEvalQueryJson]): BeautyQCoverageGapModel = {
    val taxonomy = BeautyQResponseComponentTaxonomy.fromEvalQueries(queries)
    BeautyQCoverageGapModel.fromTaxonomy(taxonomy)
  }

  private def gapFor(model: BeautyQCoverageGapModel, componentId: BeautyResponseComponentId): ComponentCoverageGap =
    model.componentGaps match {
      case _ =>
        model.componentGaps.find(_.componentId == componentId) match {
          case Some(gap) => gap
          case None      => fail(s"missing component gap for ${componentId.render}")
        }
    }

  private def onlyRow(model: BeautyQCoverageGapModel): QueryCoverageRow =
    model.queryRows match {
      case row :: Nil => row
      case other      => fail(s"expected exactly one query row, got: $other")
    }

  private def sampleQuery(
    queryId: String,
    hasVariant: Boolean,
    hasProvider: Boolean,
    hasService: Boolean,
    queryTypes: List[String] = List("direct"),
  ): BeautyQEvalQueryJson =
    BeautyQEvalQueryJson(
      queryId = queryId,
      queryText = Some("manicure"),
      language = Some("ru"),
      queryTypes = queryTypes,
      notes = Some("sample note"),
      expected = Some(
        BeautyQEvalQueryExpectedJson(
          variantCarousel = if (hasVariant) Some(BeautyQEvalCarouselJson(Some(2), None, None, Some(io.circe.Json.obj()))) else None,
          providerCarousel = if (hasProvider) Some(BeautyQEvalCarouselJson(None, Some(1), None, Some(io.circe.Json.obj()))) else None,
          serviceIntentCarousel = if (hasService) Some(BeautyQEvalCarouselJson(None, None, Some(1), Some(io.circe.Json.obj()))) else None,
          facets = None,
          inferredFilters = None,
        )
      ),
      scoring = Some(BeautyQEvalQueryScoringJson(hasVariant, hasProvider, hasService)),
    )

  private def loadEvalQueries(): List[BeautyQEvalQueryJson] = {
    val stream = Option(getClass.getResourceAsStream(M9BeautyQSearchEvalQueryDataset.ResourcePath))

    stream match {
      case None =>
        fail(s"missing checked-in dataset resource ${M9BeautyQSearchEvalQueryDataset.ResourcePath}")
      case Some(value) =>
        val text = Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
        val json = parse(text).getOrElse(fail("invalid eval-query JSON"))
        val queries = json.hcursor.downField("queries")
        if (queries.succeeded) {
          queries.values
            .getOrElse(fail("queries array is not a JSON array"))
            .map(BeautyQEvalQueryJson.decoder.decodeJson)
            .toList
            .map {
              case Right(value)  => value
              case Left(failure) => fail(s"failed to decode query: ${failure.message}")
            }
        } else {
          fail("queries field missing from the listed dataset")
        }
    }
  }
}
