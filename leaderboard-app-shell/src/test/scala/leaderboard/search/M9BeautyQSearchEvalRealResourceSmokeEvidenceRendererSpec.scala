package leaderboard.search

import leaderboard.search.eval.{
  M9BeautyQSearchEvalRealResourcePrerequisitesAudit,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9BeautyQSearchEvalRealResourceSmokeEvidenceRendererSpec extends AnyWordSpec {

  private val defaultArtifact = M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.DefaultArtifact

  private val comparisonReadyArtifact =
    M9BeautyQSearchEvalRealResourceSmokeEvidenceSchema.fromAudit(
      M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsQdrantComparison
    )

  "M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer" should {

    "render artifact identity, checkpoint decision, and static scorecard readiness" in {
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)

      assert(rendered.contains(M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.Heading))
      assert(rendered.contains("- artifact_id: m9-beautyq-real-resource-smoke-evidence"))
      assert(rendered.contains("- artifact_version: v1"))
      assert(rendered.contains("## Checkpoint decision"))
      assert(rendered.contains("- required_checkpoint_decision: not_eligible_no_explicit_resource_config"))
      assert(rendered.contains("## Static scorecard readiness"))
      assert(rendered.contains("- static_scorecard_ready: true"))
      assert(rendered.contains("- static_scorecard_verdict: dataset_static_rows_ready"))
    }

    "render the three per-backend evidence sections" in {
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)

      assert(rendered.contains("## ES-only evidence"))
      assert(rendered.contains("## Qdrant-only evidence"))
      assert(rendered.contains("## Combined ES/Qdrant comparison evidence"))
      assert(rendered.contains("- evidence_kind: es_only_smoke_saved_evidence"))
      assert(rendered.contains("- evidence_kind: qdrant_only_smoke_saved_evidence"))
      assert(rendered.contains("- evidence_kind: es_qdrant_comparison_saved_evidence"))
    }

    "render prerequisites status, anchors, expected files, skip reasons, and validation summary" in {
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)

      assert(rendered.contains("- prerequisites_status: real_resource_prerequisites_blocked"))
      assert(rendered.contains("- selected_query_anchors:"))
      assert(rendered.contains("  - q_nails_001: ingredient_attribute"))
      assert(rendered.contains("- expected_evidence_files_or_sections:"))
      assert(rendered.contains("  - m9-es-only-resource-gated-smoke-execution-plan-report.md"))
      assert(rendered.contains("- skip_or_block_reasons:"))
      assert(rendered.contains("  - es_resource_config_missing_block"))
      assert(rendered.contains("  - qdrant_resource_config_missing_block"))
      assert(rendered.contains("  - operator_approval_missing_block"))
      assert(rendered.contains("- validation_summary:"))
    }

    "render every combined comparison dimension" in {
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)

      assert(rendered.contains("- comparison_dimensions:"))
      assert(rendered.contains("  - es_candidate_ids:"))
      assert(rendered.contains("  - qdrant_candidate_ids:"))
      assert(rendered.contains("  - overlap_candidate_ids:"))
      assert(rendered.contains("  - miss_candidate_ids:"))
      assert(rendered.contains("  - unexpected_candidate_ids:"))
      assert(rendered.contains("  - missing_lookup_rate: -"))
      assert(rendered.contains("  - comparison_notes:"))
      assert(rendered.contains("- es_candidate_source: es"))
      assert(rendered.contains("- qdrant_candidate_source: qdrant"))
    }

    "render blocked/skip evidence for the default artifact, never success evidence" in {
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)

      assert(rendered.contains("- evidence_state: prerequisites_blocked_skip_evidence"))
      assert(rendered.contains("- blocked_or_skipped: true"))
      assert(!rendered.contains("prerequisites_complete_pending_execution_evidence"))

      val lowered = rendered.toLowerCase
      // No success/quality-green evidence state is ever rendered; boundary fields only ever
      // appear in their negative (`: false`) form.
      assert(!lowered.contains("success_evidence"))
      assert(!lowered.contains("quality_green_claimed: true"))
      assert(!lowered.contains("production_readiness_claimed: true"))
      assert(!lowered.contains("route_activation_claimed: true"))
      assert(!lowered.contains("serving_approval_claimed: true"))
    }

    "render standing production boundaries with no activation or hybrid claim" in {
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)

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
      assert(rendered.contains(
        "- hybrid_fallback_fusion_reranking_telemetry_route_switch_implemented: false"))
    }

    "be deterministic across repeated renders" in {
      val first = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)
      val second = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)
      assert(first == second)
    }

    "match the checked-in default example artifact byte-for-byte" in {
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(defaultArtifact)
      assert(rendered == checkedInDefaultExample)
    }

    "render pending-execution evidence when prerequisites are complete" in {
      // Sanity check the renderer reflects the complete state without claiming success.
      val rendered = M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.render(comparisonReadyArtifact)
      assert(rendered.contains("- evidence_state: prerequisites_complete_pending_execution_evidence"))
      assert(rendered.contains("- prerequisites_complete: true"))
      assert(!rendered.toLowerCase.contains("success_evidence"))
    }
  }

  private def checkedInDefaultExample: String =
    readCheckedInResource(
      "/leaderboard/search/eval/" +
        M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.DefaultExampleFilename
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
