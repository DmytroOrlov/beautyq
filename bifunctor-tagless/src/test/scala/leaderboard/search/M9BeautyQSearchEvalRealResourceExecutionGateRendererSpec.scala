package leaderboard.search

import leaderboard.search.eval.M9BeautyQSearchEvalRealResourceExecutionGateRenderer
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9BeautyQSearchEvalRealResourceExecutionGateRendererSpec extends AnyWordSpec {

  private val defaultArtifact = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.DefaultArtifact

  private val pendingArtifact = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.PendingArtifact

  "M9BeautyQSearchEvalRealResourceExecutionGateRenderer" should {

    "render artifact identity and runbook consistency status" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)

      assert(rendered.contains(M9BeautyQSearchEvalRealResourceExecutionGateRenderer.Heading))
      assert(rendered.contains("- artifact_id: m9-beautyq-real-resource-execution-gate"))
      assert(rendered.contains("- artifact_version: v1"))
      assert(rendered.contains("## Runbook consistency"))
      assert(rendered.contains("- runbook_path: docs/local/BEAUTYQ_M9_REAL_RESOURCE_SMOKE_RUNBOOK.md"))
      assert(rendered.contains("- schema_evidence_mode_count: 3"))
      assert(rendered.contains("- mode_token_count_matches_schema: true"))
      assert(rendered.contains("- default_artifact_renders_blocked_skip_only: true"))
    }

    "render the three execution gate decisions separately" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)

      assert(rendered.contains("## ES-only execution gate"))
      assert(rendered.contains("## Qdrant-only execution gate"))
      assert(rendered.contains("## Combined ES/Qdrant execution gate"))
      assert(rendered.contains("- gate_decision: blocked_es_only_prerequisites_incomplete"))
      assert(rendered.contains("- gate_decision: blocked_qdrant_only_prerequisites_incomplete"))
      assert(rendered.contains("- gate_decision: blocked_combined_prerequisites_incomplete"))
    }

    "render checkpoint decisions, prerequisites status, schema kinds, and evidence artifact path per gate" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)

      assert(rendered.contains("- checkpoint_decision: not_eligible_no_explicit_resource_config"))
      assert(rendered.contains("- prerequisites_complete: false"))
      assert(rendered.contains("- required_evidence_schema_kind: es_only_smoke_saved_evidence"))
      assert(rendered.contains("- required_evidence_schema_kind: qdrant_only_smoke_saved_evidence"))
      assert(rendered.contains("- required_evidence_schema_kind: es_qdrant_comparison_saved_evidence"))
      assert(rendered.contains(
        "- required_evidence_capture_artifact: m9-beautyq-real-resource-smoke-evidence-default.md"))
    }

    "render blocked/skip reasons for each gate" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)

      assert(rendered.contains("- skip_or_block_reasons:"))
      assert(rendered.contains("  - es_resource_config_missing"))
      assert(rendered.contains("  - qdrant_resource_config_missing"))
      assert(rendered.contains("  - requires_separate_explicit_operator_approved_task"))
      assert(rendered.contains("  - requires_saved_evidence_artifact_capture"))
      assert(rendered.contains("  - no_hybrid_fallback_fusion_reranking_telemetry_route_switch"))
    }

    "render blocked/skip for the default/no-config artifact, never pending or success" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)

      assert(rendered.contains("- pending_explicit_execution_task: false"))
      assert(rendered.contains("- blocked: true"))
      assert(!rendered.contains("- pending_explicit_execution_task: true"))

      val lowered = rendered.toLowerCase
      assert(!lowered.contains("success"))
      assert(!lowered.contains("quality_green_claimed: true"))
      assert(!lowered.contains("production_readiness_claimed: true"))
      assert(!lowered.contains("route_activation_claimed: true"))
      assert(!lowered.contains("serving_approval_claimed: true"))
    }

    "render standing production boundaries with no activation or hybrid claim" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)

      assert(rendered.contains("## Standing production boundaries"))
      assert(rendered.contains("- default_beauty_search_es_backed: true"))
      assert(rendered.contains("- production_activation_approved: false"))
      assert(rendered.contains("- qdrant_production_activation_approved: false"))
      assert(rendered.contains("- quality_green_claimed: false"))
      assert(rendered.contains("- production_readiness_claimed: false"))
      assert(rendered.contains("- route_activation_claimed: false"))
      assert(rendered.contains("- serving_approval_claimed: false"))
      assert(rendered.contains("- es_client_created: false"))
      assert(rendered.contains("- qdrant_client_created: false"))
      assert(rendered.contains("- production_beauty_search_called: false"))
      assert(rendered.contains("- route_plugin_di_http_involved: false"))
      assert(rendered.contains("- real_backend_call_implemented: false"))
      assert(rendered.contains("- real_backend_call_required: false"))
      assert(rendered.contains(
        "- hybrid_fallback_fusion_reranking_telemetry_route_switch_implemented: false"))
    }

    "be deterministic across repeated renders" in {
      val first = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)
      val second = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)
      assert(first == second)
    }

    "match the checked-in default example artifact byte-for-byte" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(defaultArtifact)
      assert(rendered == checkedInDefaultExample)
    }

    "render pending-explicit-execution-task design states for the non-default fixture, never success" in {
      val rendered = M9BeautyQSearchEvalRealResourceExecutionGateRenderer.render(pendingArtifact)

      assert(rendered.contains("- gate_decision: pending_explicit_execution_task"))
      assert(rendered.contains("- pending_explicit_execution_task: true"))
      assert(rendered.contains("- prerequisites_complete: true"))
      // Pending only: never a success/quality-green/activation claim.
      val lowered = rendered.toLowerCase
      assert(!lowered.contains("success"))
      assert(!lowered.contains("quality_green_claimed: true"))
      assert(!lowered.contains("route_activation_claimed: true"))
      assert(!lowered.contains("serving_approval_claimed: true"))
      // The pending design state still carries the standing operator-approval/evidence boundaries.
      assert(rendered.contains("  - requires_separate_explicit_operator_approved_task"))
      assert(rendered.contains("  - requires_saved_evidence_artifact_capture"))
    }
  }

  private def checkedInDefaultExample: String =
    readCheckedInResource(
      "/leaderboard/search/eval/" +
        M9BeautyQSearchEvalRealResourceExecutionGateRenderer.DefaultExampleFilename
    )

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
