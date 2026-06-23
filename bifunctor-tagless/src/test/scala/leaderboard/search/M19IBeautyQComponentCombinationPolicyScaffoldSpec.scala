package leaderboard.search

import io.circe.parser.parse
import leaderboard.search.eval.{BeautyQEvalQueryJson, M9BeautyQSearchEvalQueryDataset}
import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.{
  BeautyResponseComponentId,
  BeautyQResponseComponentTaxonomy,
  ResponseComponentFacts,
}
import leaderboard.search.eval.M19DBeautyQCoverageGapModel.BeautyQCoverageGapModel
import leaderboard.search.eval.M19FBeautyQEvalComponentExpectationSchema.{
  EvalQueryComponentExpectations,
  evalQueryComponentExpectationsDecoder,
}
import leaderboard.search.eval.M19HBeautyQComponentCoverageEvidenceReport.ComponentCoverageEvidence
import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold
import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.{
  ComponentCombinationPolicy,
  PolicyState,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M19IBeautyQComponentCombinationPolicyScaffoldSpec extends AnyWordSpec {

  "M19I component combination policy scaffold over the checked-in 63-query dataset" should {

    "represent every BeautyQ response component exactly once" in {
      val policy = buildPolicy()

      val ids = policy.rows.map(_.componentId)
      assert(ids == BeautyResponseComponentId.stableOrder)
      assert(ids.distinct.size == ids.size)
      assert(ids.toSet == BeautyResponseComponentId.values.toSet)
    }

    "make the variant carousel the only Qdrant-positive offline supplement policy" in {
      val policy = buildPolicy()

      assert(policy.qdrantPositiveComponents == List(BeautyResponseComponentId.VariantCarousel))

      val variant = rowOf(policy, BeautyResponseComponentId.VariantCarousel)
      assert(variant.state == PolicyState.EsPrimaryWithQdrantSemanticSupplement)
      assert(variant.state.permitsQdrantContribution)
      assert(variant.qdrantContributionAllowed)
      assert(variant.candidateEvidenceAvailable)

      // No other component permits a Qdrant contribution.
      policy.rows.filterNot(_.componentId == BeautyResponseComponentId.VariantCarousel).foreach { row =>
        assert(!row.qdrantContributionAllowed, s"${row.componentId.render} must not permit Qdrant contribution")
      }
    }

    "keep provider and service intent carousels evidence-limited despite confirmed grouping metadata" in {
      val policy = buildPolicy()

      List(BeautyResponseComponentId.ProviderCarousel, BeautyResponseComponentId.ServiceIntentCarousel).foreach {
        componentId =>
          val row = rowOf(policy, componentId)
          assert(row.state == PolicyState.NeedsMoreEvidence)
          assert(!row.qdrantContributionAllowed)
          // Grouping metadata coverage is present, but it does not approve a hybrid policy.
          assert(row.expectationCoverageAvailable)
      }
    }

    "keep facets and inferred filters current-owner/ES/parser-only with no Qdrant contribution" in {
      val policy = buildPolicy()

      List(BeautyResponseComponentId.Facets, BeautyResponseComponentId.InferredFilters).foreach { componentId =>
        val row = rowOf(policy, componentId)
        assert(row.state == PolicyState.EsOrCurrentOwnerOnly)
        assert(!row.state.permitsQdrantContribution)
        assert(!row.qdrantContributionAllowed)
        assert(row.currentOwnerLayer.isDefined)
      }

      assert(policy.qdrantDoesNotOwnFacets)
      assert(policy.qdrantDoesNotOwnInferredFilters)
    }

    "give every policy row a reason" in {
      val policy = buildPolicy()
      assert(policy.rows.forall(_.reason.nonEmpty))
    }

    "approve serving for no component" in {
      val policy = buildPolicy()

      assert(policy.rows.forall(!_.servingApproved))
      assert(!policy.rows.exists(_.servingApproved))
      assert(policy.notServingPolicy)
      assert(policy.offlineEvalOnly)
      assert(policy.doesNotApproveHybrid)
    }

    "render a markdown report that says offline/eval-only and not production activation" in {
      val report = M19IBeautyQComponentCombinationPolicyScaffold.renderMarkdown(buildPolicy())

      assert(report.contains("Offline/eval-only"))
      assert(report.contains("NOT production activation"))
      assert(report.contains("serving_approved: false"))
      assert(report.contains("serving_approved_anywhere: false"))
      assert(report.contains("qdrant_positive_components: variantCarousel"))
      assert(report.contains("policy_state: EsPrimaryWithQdrantSemanticSupplement"))
    }
  }

  private def buildPolicy(): ComponentCombinationPolicy = {
    val json             = loadCheckedInDataset()
    val componentQueries = decodeComponentQueries(json)
    val narrowQueries    = decodeNarrowQueries(json)
    val taxonomy         = BeautyQResponseComponentTaxonomy.fromEvalQueries(narrowQueries)
    val gapModel         = BeautyQCoverageGapModel.fromTaxonomy(taxonomy)
    val evidence         = ComponentCoverageEvidence.build(componentQueries, gapModel)
    ComponentCombinationPolicy.build(evidence, ResponseComponentFacts)
  }

  private def rowOf(policy: ComponentCombinationPolicy, componentId: BeautyResponseComponentId) =
    policy.rowFor(componentId).getOrElse(fail(s"missing policy row for ${componentId.render}"))

  private def decodeComponentQueries(json: io.circe.Json): List[EvalQueryComponentExpectations] =
    decodeQueries(json)(evalQueryComponentExpectationsDecoder.decodeJson)

  private def decodeNarrowQueries(json: io.circe.Json): List[BeautyQEvalQueryJson] =
    decodeQueries(json)(BeautyQEvalQueryJson.decoder.decodeJson)

  private def loadCheckedInDataset(): io.circe.Json =
    Option(getClass.getResourceAsStream(M9BeautyQSearchEvalQueryDataset.ResourcePath)) match {
      case None =>
        fail(s"missing resource ${M9BeautyQSearchEvalQueryDataset.ResourcePath}")
      case Some(value) =>
        val text = Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
        parse(text).fold(err => fail(s"invalid JSON: ${err.message}"), identity)
    }

  private def decodeQueries[A](json: io.circe.Json)(decode: io.circe.Json => io.circe.Decoder.Result[A]): List[A] = {
    val queries = json.hcursor.downField("queries")
    if (queries.succeeded) {
      queries.values
        .getOrElse(fail("queries array is not a JSON array"))
        .map(decode)
        .toList
        .map {
          case Right(value)  => value
          case Left(failure) => fail(s"failed to decode query: ${failure.message}")
        }
    } else {
      fail("queries field missing from the dataset")
    }
  }
}
