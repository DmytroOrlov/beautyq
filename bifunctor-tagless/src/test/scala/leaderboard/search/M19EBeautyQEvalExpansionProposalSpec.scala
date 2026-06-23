package leaderboard.search

import io.circe.parser.parse
import leaderboard.search.eval.*
import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.{
  BeautyResponseComponentId,
  BeautyQResponseComponentTaxonomy,
}
import leaderboard.search.eval.M19DBeautyQCoverageGapModel.BeautyQCoverageGapModel
import leaderboard.search.eval.M19EBeautyQEvalDatasetExpansionProposal.{
  BeautyQEvalExpansionProposal,
  EvalExpansionProposalItem,
  MissingEvidenceDimension,
  ProposalImpact,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M19EBeautyQEvalExpansionProposalSpec extends AnyWordSpec {

  "M19EBeautyQEvalDatasetExpansionProposal.fromCoverageGapModel" should {

    "report the offline/eval-only boundary and never claim serving approval" in {
      val proposal = buildProposal(threeMixedQueries)

      assert(proposal.offlineEvalOnly)
      assert(proposal.notServingPolicy)
      assert(proposal.doesNotApproveHybrid)
      assert(proposal.qdrantDoesNotOwnFacets)
      assert(proposal.qdrantDoesNotOwnInferredFilters)
    }

    "generate a proposal item for every M19D undercovered component" in {
      val model    = buildModel(threeMixedQueries)
      val proposal = BeautyQEvalExpansionProposal.fromCoverageGapModel(model)

      val proposedComponents = proposal.items.map(_.componentId).toSet
      model.undercoveredComponents.foreach { componentId =>
        assert(proposedComponents.contains(componentId), s"missing proposal item for ${componentId.render}")
      }

      // Every undercovered component is a BlocksPolicy item.
      model.undercoveredComponents.foreach { componentId =>
        val item = itemFor(proposal, componentId)
        assert(item.impact == ProposalImpact.BlocksPolicy, s"${componentId.render} should block policy")
      }
    }

    "require grouping/projection evidence for provider and service, not candidate ids alone" in {
      val proposal = buildProposal(threeMixedQueries)

      val provider = itemFor(proposal, BeautyResponseComponentId.ProviderCarousel)
      val service  = itemFor(proposal, BeautyResponseComponentId.ServiceIntentCarousel)

      assert(provider.missingEvidenceDimension == MissingEvidenceDimension.ProviderGrouping)
      assert(service.missingEvidenceDimension == MissingEvidenceDimension.ServiceGrouping)

      assert(provider.whyInsufficient.contains("candidate ids alone"))
      assert(service.whyInsufficient.contains("candidate ids alone"))
      assert(provider.whyInsufficient.contains("masterLocationId"))
      assert(service.whyInsufficient.contains("serviceId"))

      // The grouping field is source-confirmed and proposed as a NEW field, not invented values.
      assert(provider.proposedJsonFields.exists(f => f.jsonPath.contains("expectedGroupingField") && !f.alreadyPresentInSchema))
      assert(service.proposedJsonFields.exists(f => f.jsonPath.contains("expectedGroupingField") && !f.alreadyPresentInSchema))
    }

    "drive facets and inferred-filters proposals from absent JSON fields, not invented expectations" in {
      val proposal = buildProposal(threeMixedQueries)

      val facets   = itemFor(proposal, BeautyResponseComponentId.Facets)
      val inferred = itemFor(proposal, BeautyResponseComponentId.InferredFilters)

      assert(facets.missingEvidenceDimension == MissingEvidenceDimension.Facets)
      assert(inferred.missingEvidenceDimension == MissingEvidenceDimension.InferredFilters)

      // The proposed fields are absent from the schema today.
      assert(facets.proposedJsonFields.exists(f => f.jsonPath == "expected.facets" && !f.alreadyPresentInSchema))
      assert(inferred.proposedJsonFields.exists(f => f.jsonPath == "expected.inferredFilters" && !f.alreadyPresentInSchema))

      // The why-insufficient is driven by the absent field, and Qdrant is not allowed to own them.
      assert(facets.whyInsufficient.contains("no facets expectations"))
      assert(inferred.whyInsufficient.contains("no inferredFilters expectations"))
      assert(facets.whyInsufficient.contains("Qdrant does not own facets"))
      assert(inferred.whyInsufficient.contains("Qdrant does not own inferred filters"))

      // The expectation shapes are source-confirmed but carry no invented values.
      assert(facets.proposedExpectationShape.exists(_.contains("BeautySearchFacet")))
      assert(inferred.proposedExpectationShape.exists(_.contains("BeautySearchAppliedFilter")))
      assert(facets.proposedExpectationShape.exists(_.contains("not invented")))
      assert(inferred.proposedExpectationShape.exists(_.contains("not invented")))
    }

    "not treat the variant carousel as policy-blocked when M19D reports it covered" in {
      val model    = buildModel(threeMixedQueries)
      val proposal = BeautyQEvalExpansionProposal.fromCoverageGapModel(model)

      assert(!model.undercoveredComponents.contains(BeautyResponseComponentId.VariantCarousel))

      val variant = itemFor(proposal, BeautyResponseComponentId.VariantCarousel)
      assert(variant.impact == ProposalImpact.ImprovesConfidenceOnly)
      assert(variant.missingEvidenceDimension == MissingEvidenceDimension.VariantRecall)
      assert(variant.needsMoreEvidence.isEmpty)
      assert(!proposal.blockingComponents.contains(BeautyResponseComponentId.VariantCarousel))
    }

    "attach a NeedsMoreEvidence reason to every blocking proposal item" in {
      val proposal = buildProposal(threeMixedQueries)

      val blocking = proposal.items.filter(_.impact == ProposalImpact.BlocksPolicy)
      assert(blocking.nonEmpty)
      blocking.foreach { item =>
        item.needsMoreEvidence match {
          case Some(nme) =>
            assert(nme.decisionId.nonEmpty, s"${item.componentId.render} needs a decisionId")
            assert(nme.reason.nonEmpty, s"${item.componentId.render} needs a reason")
          case None =>
            fail(s"blocking proposal for ${item.componentId.render} is missing a NeedsMoreEvidence reason")
        }
      }
    }

    "never attach a NeedsMoreEvidence reason to a confidence-only item" in {
      val proposal = buildProposal(threeMixedQueries)

      proposal.items
        .filter(_.impact == ProposalImpact.ImprovesConfidenceOnly)
        .foreach(item => assert(item.needsMoreEvidence.isEmpty, s"${item.componentId.render} should not block policy"))
    }
  }

  "M19EBeautyQEvalDatasetExpansionProposal over the listed eval-query JSON" should {

    "cover provider, service, facets, inferred filters as blocking and variant as confidence-only" in {
      val proposal = buildProposal(loadEvalQueries())

      assert(proposal.blockingComponents.contains(BeautyResponseComponentId.ProviderCarousel))
      assert(proposal.blockingComponents.contains(BeautyResponseComponentId.ServiceIntentCarousel))
      assert(proposal.blockingComponents.contains(BeautyResponseComponentId.Facets))
      assert(proposal.blockingComponents.contains(BeautyResponseComponentId.InferredFilters))
      assert(!proposal.blockingComponents.contains(BeautyResponseComponentId.VariantCarousel))

      val variant = itemFor(proposal, BeautyResponseComponentId.VariantCarousel)
      assert(variant.impact == ProposalImpact.ImprovesConfidenceOnly)
    }

    "propose at least one source-confirmed query class for every item" in {
      val proposal = buildProposal(loadEvalQueries())

      proposal.items.foreach { item =>
        assert(item.proposedQueryClasses.nonEmpty, s"${item.componentId.render} should propose query classes")
        assert(item.sourceCitations.nonEmpty, s"${item.componentId.render} should cite source files")
      }
    }
  }

  private def buildModel(queries: List[BeautyQEvalQueryJson]): BeautyQCoverageGapModel = {
    val taxonomy: BeautyQResponseComponentTaxonomy = BeautyQResponseComponentTaxonomy.fromEvalQueries(queries)
    BeautyQCoverageGapModel.fromTaxonomy(taxonomy)
  }

  private def buildProposal(queries: List[BeautyQEvalQueryJson]): BeautyQEvalExpansionProposal =
    BeautyQEvalExpansionProposal.fromCoverageGapModel(buildModel(queries))

  private def itemFor(
    proposal: BeautyQEvalExpansionProposal,
    componentId: BeautyResponseComponentId,
  ): EvalExpansionProposalItem =
    proposal.items.find(_.componentId == componentId) match {
      case Some(item) => item
      case None       => fail(s"missing proposal item for ${componentId.render}")
    }

  private val threeMixedQueries: List[BeautyQEvalQueryJson] =
    List(
      sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true),
      sampleQuery("q_demo_002", hasVariant = true, hasProvider = false, hasService = true),
      sampleQuery("q_demo_003", hasVariant = false, hasProvider = false, hasService = false),
    )

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
