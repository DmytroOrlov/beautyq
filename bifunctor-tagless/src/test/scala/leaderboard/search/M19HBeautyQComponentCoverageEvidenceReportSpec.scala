package leaderboard.search

import io.circe.parser.parse
import leaderboard.search.eval.{BeautyQEvalQueryJson, M9BeautyQSearchEvalQueryDataset}
import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.{
  BeautyResponseComponentId,
  BeautyQResponseComponentTaxonomy,
}
import leaderboard.search.eval.M19DBeautyQCoverageGapModel.BeautyQCoverageGapModel
import leaderboard.search.eval.M19FBeautyQEvalComponentExpectationSchema.{
  EvalQueryComponentExpectations,
  evalQueryComponentExpectationsDecoder,
}
import leaderboard.search.eval.M19HBeautyQComponentCoverageEvidenceReport
import leaderboard.search.eval.M19HBeautyQComponentCoverageEvidenceReport.{
  ComponentCoverageEvidence,
  ComponentDatasetAvailability,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M19HBeautyQComponentCoverageEvidenceReportSpec extends AnyWordSpec {

  "M19H component coverage evidence over the checked-in 63-query dataset" should {

    "expose provider grouping field coverage as source-confirmed masterLocationId" in {
      val evidence = buildEvidence()
      val provider = evidence.provider

      assert(provider.componentId == BeautyResponseComponentId.ProviderCarousel)
      assert(provider.schemaSupported)
      assert(provider.datasetHasExpectations)
      assert(provider.availability == ComponentDatasetAvailability.SourceConfirmedInDataset)
      assert(provider.expectedGroupingField.contains("masterLocationId"))
      assert(provider.groupingFieldConsistent)
      assert(provider.expectationQueryCount == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount)
      assert(provider.totalQueryCount == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount)
    }

    "expose service intent grouping field coverage as source-confirmed serviceId" in {
      val evidence = buildEvidence()
      val service  = evidence.serviceIntent

      assert(service.componentId == BeautyResponseComponentId.ServiceIntentCarousel)
      assert(service.schemaSupported)
      assert(service.datasetHasExpectations)
      assert(service.availability == ComponentDatasetAvailability.SourceConfirmedInDataset)
      assert(service.expectedGroupingField.contains("serviceId"))
      assert(service.groupingFieldConsistent)
      assert(service.expectationQueryCount == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount)
    }

    "keep the variant carousel covered by existing candidate/variant expectations" in {
      val evidence = buildEvidence()

      assert(evidence.variant.componentId == BeautyResponseComponentId.VariantCarousel)
      assert(evidence.variant.coveredByExistingCandidateVariantExpectations)
      assert(!evidence.variant.policyBlocked)
    }

    "keep facets and inferred filters as explicit gaps with no expected values" in {
      val evidence = buildEvidence()

      List(evidence.facets, evidence.inferredFilters).foreach { absent =>
        assert(absent.schemaSupported)
        assert(!absent.datasetHasExpectations)
        assert(absent.availability == ComponentDatasetAvailability.SupportedButAbsentFromDataset)
        assert(!absent.expectedValuesPresent)
      }
    }

    "distinguish source-confirmed metadata from policy approval" in {
      val evidence = buildEvidence()

      // Grouping metadata is source-confirmed...
      assert(evidence.metadataSourceConfirmed)
      // ...but no component-level policy is approved, and the honesty flags hold.
      assert(!evidence.componentPolicyApproved)
      assert(evidence.offlineEvalOnly)
      assert(evidence.notServingPolicy)
      assert(evidence.doesNotApproveHybrid)
      assert(evidence.qdrantDoesNotOwnFacets)
      assert(evidence.qdrantDoesNotOwnInferredFilters)
    }

    "keep provider/service/facets/inferred component policy blocked where evidence is insufficient" in {
      val evidence = buildEvidence()
      val blockedComponents = evidence.remainingPolicyBlockers.map(_.componentId).toSet

      assert(blockedComponents.contains(BeautyResponseComponentId.ProviderCarousel))
      assert(blockedComponents.contains(BeautyResponseComponentId.ServiceIntentCarousel))
      assert(blockedComponents.contains(BeautyResponseComponentId.Facets))
      assert(blockedComponents.contains(BeautyResponseComponentId.InferredFilters))
      assert(!blockedComponents.contains(BeautyResponseComponentId.VariantCarousel))
      assert(evidence.remainingPolicyBlockers.forall(_.reason.nonEmpty))
    }

    "render a markdown report that surfaces grouping fields and keeps policy unapproved" in {
      val report = M19HBeautyQComponentCoverageEvidenceReport.renderMarkdown(buildEvidence())

      assert(report.contains("expected_grouping_field=masterLocationId"))
      assert(report.contains("expected_grouping_field=serviceId"))
      assert(report.contains("grouping_metadata_source_confirmed: true"))
      assert(report.contains("component_policy_approved: false"))
      assert(report.contains("Remaining component-policy blockers"))
      // Facets / inferred filters render as gaps, never with expected values.
      assert(report.contains("expected_values_present=false"))
    }
  }

  private def buildEvidence(): ComponentCoverageEvidence = {
    val json             = loadCheckedInDataset()
    val componentQueries = decodeComponentQueries(json)
    val narrowQueries    = decodeNarrowQueries(json)
    val taxonomy         = BeautyQResponseComponentTaxonomy.fromEvalQueries(narrowQueries)
    val gapModel         = BeautyQCoverageGapModel.fromTaxonomy(taxonomy)
    ComponentCoverageEvidence.build(componentQueries, gapModel)
  }

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
