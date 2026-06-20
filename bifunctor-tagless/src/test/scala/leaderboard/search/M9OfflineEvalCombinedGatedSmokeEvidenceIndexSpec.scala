package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9OfflineEvalBackendExecutionMode,
  M9OfflineEvalCombinedGatedSmokeEvidenceIndex,
  M9OfflineEvalCombinedGatedSmokeEvidenceInput,
  M9OfflineEvalCombinedGatedSmokeEvidenceRenderer,
  M9OfflineEvalEsOnlyGatedSmokeEvidence,
  M9OfflineEvalEsOnlyGatedSmokeInput,
  M9OfflineEvalQdrantOnlyGatedSmokeEvidence,
  M9OfflineEvalQdrantOnlyGatedSmokeInput,
  M9OfflineEvalRealBackendResourceGate,
  M9OfflineEvalRealBackendResourceGateStatus,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9OfflineEvalCombinedGatedSmokeEvidenceIndexSpec extends AnyWordSpec {

  "M9OfflineEvalCombinedGatedSmokeEvidenceIndex" should {

    "render ES and Qdrant entries from not-configured inputs" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      val rendered = index.markdownArtifact.contents
      assert(rendered.contains("## ES-only gated smoke evidence"))
      assert(rendered.contains("## Qdrant-only gated smoke evidence"))
      assert(rendered.contains(M9OfflineEvalEsOnlyGatedSmokeEvidence.NotConfiguredFilename))
      assert(rendered.contains(M9OfflineEvalQdrantOnlyGatedSmokeEvidence.NotConfiguredFilename))
    }

    "include both checked-in artifact filenames" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      val rendered = index.markdownArtifact.contents
      assert(rendered.contains(M9OfflineEvalEsOnlyGatedSmokeEvidence.NotConfiguredFilename))
      assert(rendered.contains(M9OfflineEvalQdrantOnlyGatedSmokeEvidence.NotConfiguredFilename))
    }

    "preserve ES source and serving mode attribution" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      assert(index.summary.esSource == CandidateSource.Es)
      assert(index.summary.esServingMode == ServingMode.EsOnly)
      val esArtifact = index.markdownArtifact.contents
      assert(esArtifact.contains("es_source: es"))
      assert(esArtifact.contains("es_serving_mode: es_only"))
      assert(esArtifact.contains("es_execution_mode: es_only_offline"))
    }

    "preserve Qdrant source and serving mode attribution" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      assert(index.summary.qdrantSource == CandidateSource.Qdrant)
      assert(index.summary.qdrantServingMode == ServingMode.QdrantOnly)
      val qdrantArtifact = index.markdownArtifact.contents
      assert(qdrantArtifact.contains("qdrant_source: qdrant"))
      assert(qdrantArtifact.contains("qdrant_serving_mode: qdrant_only"))
      assert(qdrantArtifact.contains("qdrant_execution_mode: qdrant_only_offline"))
    }

    "record both ES and Qdrant resource configs as absent/not-configured" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      assert(!index.summary.esResourceConfigPresent)
      assert(!index.summary.qdrantResourceConfigPresent)
      assert(index.summary.esGateDecision.status ==
        M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig)
      assert(index.summary.qdrantGateDecision.status ==
        M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig)
      assert(index.markdownArtifact.contents.contains("es_resource_config_present: false"))
      assert(index.markdownArtifact.contents.contains("qdrant_resource_config_present: false"))
      assert(index.markdownArtifact.contents.contains("gate_status: allowed_without_resource_config"))
    }

    "record production non-approval" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      assert(index.summary.productionActivationNotApprovedConfirmed)
      assert(index.summary.notActivationApproval)
      assert(index.summary.notProductionTelemetry)
      assert(index.markdownArtifact.contents.contains("production_activation_not_approved_confirmed: true"))
      assert(index.markdownArtifact.contents.contains(
        M9OfflineEvalCombinedGatedSmokeEvidenceIndex.ArtifactBoundaryNote))
    }

    "record no real backend call" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      assert(!index.summary.realBackendCallImplemented)
      assert(index.markdownArtifact.contents.contains("real_backend_call_implemented: false"))
    }

    "represent warnings and failures as data" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      assert(!index.summary.esResourceConfigPresent)
      assert(!index.summary.qdrantResourceConfigPresent)
      val rendered = index.markdownArtifact.contents
      assert(rendered.contains("es_warnings:"))
      assert(rendered.contains("qdrant_warnings:"))
      assert(rendered.contains("not configured"))
      assert(rendered.contains(M9OfflineEvalQdrantOnlyGatedSmokeEvidence.EmbeddingVectorPrerequisiteWarning))
    }

    "not fake successful backend execution" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      val rendered = index.markdownArtifact.contents.toLowerCase
      assert(!rendered.contains("es backend success"))
      assert(!rendered.contains("qdrant backend success"))
      assert(!rendered.contains("elasticsearch query executed"))
      assert(!rendered.contains("qdrant query executed"))
    }

    "not claim hybrid execution, fallback used, fusion implemented, or reranking implemented" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      val rendered = index.markdownArtifact.contents.toLowerCase
      assert(!rendered.contains("hybrid execution"))
      assert(!rendered.contains("hybrid served"))
      assert(!rendered.contains("hybrid enabled"))
      assert(!rendered.contains("fallback used"))
      assert(!rendered.contains("fallback applied"))
      assert(!rendered.contains("fusion implemented"))
      assert(!rendered.contains("reranking implemented"))
      assert(!rendered.contains("rerank applied"))
      assert(rendered.contains("no_hybrid_or_fallback_or_fusion_or_reranking"))
    }

    "produce a byte-for-byte stable artifact for not-configured inputs" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      val rendered = index.markdownArtifact.contents
      val renderedAgain = M9OfflineEvalCombinedGatedSmokeEvidenceRenderer.render(input)
      assert(rendered == renderedAgain)
      assert(rendered == checkedInCombinedIndex)
    }

    "not involve route/plugin/DI/http source" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      val rendered = index.markdownArtifact.contents.toLowerCase
      assert(rendered.contains("no_route_or_plugin_or_di_or_http_source_change"))
    }

    "build a default-disabled combined index" in {
      val input = combinedDefaultDisabledInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyDefaultDisabledIndexFilename,
      )

      assert(index.summary.esGateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Disabled)
      assert(index.summary.qdrantGateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Disabled)
      assert(!index.summary.realBackendCallImplemented)
    }

    "use stable execution modes preserved from per-backend inputs" in {
      val input = combinedNotConfiguredInput
      val index = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.build(
        input = input,
        markdownFilename = M9OfflineEvalCombinedGatedSmokeEvidenceIndex.EsOnlyQdrantOnlyNotConfiguredIndexFilename,
      )

      assert(input.esResult.esExecutionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
      assert(input.qdrantResult.qdrantExecutionMode == M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
      assert(index.markdownArtifact.contents.contains("es_execution_mode: es_only_offline"))
      assert(index.markdownArtifact.contents.contains("qdrant_execution_mode: qdrant_only_offline"))
    }
  }

  private def combinedNotConfiguredInput: M9OfflineEvalCombinedGatedSmokeEvidenceInput = {
    val esGate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true)
    val esInput = M9OfflineEvalEsOnlyGatedSmokeInput(resourceConfig = None, gate = esGate)
    val qdrantInput = M9OfflineEvalQdrantOnlyGatedSmokeInput(resourceConfig = None, gate = esGate)

    val es = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureNotConfigured(esInput) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected not-configured ES smoke evidence, got $error")
    }
    val qdrant = M9OfflineEvalQdrantOnlyGatedSmokeEvidence.captureNotConfigured(qdrantInput) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected not-configured Qdrant smoke evidence, got $error")
    }
    M9OfflineEvalCombinedGatedSmokeEvidenceInput(esResult = es, qdrantResult = qdrant)
  }

  private def combinedDefaultDisabledInput: M9OfflineEvalCombinedGatedSmokeEvidenceInput = {
    val gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = false)
    val esInput = M9OfflineEvalEsOnlyGatedSmokeInput(resourceConfig = None, gate = gate)
    val qdrantInput = M9OfflineEvalQdrantOnlyGatedSmokeInput(resourceConfig = None, gate = gate)

    val es = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureDefaultDisabled(esInput) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected default-disabled ES smoke evidence, got $error")
    }
    val qdrant = M9OfflineEvalQdrantOnlyGatedSmokeEvidence.captureDefaultDisabled(qdrantInput) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected default-disabled Qdrant smoke evidence, got $error")
    }
    M9OfflineEvalCombinedGatedSmokeEvidenceInput(esResult = es, qdrantResult = qdrant)
  }

  private def checkedInCombinedIndex: String =
    readCheckedInResource("/leaderboard/search/eval/m9-combined-gated-smoke-evidence-index.md")

  private def readCheckedInResource(path: String): String = {
    val stream = Option(getClass.getResourceAsStream(path))

    stream match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource artifact $path")
    }
  }
}
