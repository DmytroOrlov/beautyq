package leaderboard.search

import leaderboard.search.eval.{
  M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision,
  M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign,
  M9BeautyQSearchEvalQdrantOnlyExecutionGateReason,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceKind,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalQdrantOnlyExecutionGateDesignSpec extends AnyWordSpec {

  private val default = M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign.DefaultResult
  private val qdrantOnly = M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign.gateForQdrantOnlyConfig
  private val esOnly = M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign.gateForEsOnlyConfig
  private val combined = M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign.gateForCombinedConfig

  "M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign" should {

    "block the default / no-config state" in {
      assert(default.decision == M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision.BlockedQdrantOnlyPrerequisitesIncomplete)
      assert(default.blocked)
      assert(!default.pendingExplicitExecutionTask)
      assert(!default.qdrantOnlyPrerequisitesComplete)
      assert(default.reasons.contains(M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.QdrantResourceConfigMissing))
      assert(default.decision.render == "blocked_qdrant_only_prerequisites_incomplete")
    }

    "block the ES-only path" in {
      assert(esOnly.blocked)
      assert(!esOnly.pendingExplicitExecutionTask)
      assert(esOnly.reasons.contains(
        M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.CheckpointTargetsEsOnlyNotQdrantOnly))
    }

    "block the combined-only path even though Qdrant-only prerequisites are complete" in {
      assert(combined.decision == M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision.BlockedNotQdrantOnlyTarget)
      assert(combined.blocked)
      assert(!combined.pendingExplicitExecutionTask)
      assert(combined.qdrantOnlyPrerequisitesComplete)
      assert(!combined.qdrantOnlyPlanned)
      assert(combined.reasons.contains(
        M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.CheckpointTargetsCombinedComparisonNotQdrantOnly))
      assert(combined.decision.render == "blocked_not_qdrant_only_target")
    }

    "allow only the Qdrant-only prerequisites-complete state to become pending_explicit_execution_task" in {
      assert(qdrantOnly.decision == M9BeautyQSearchEvalQdrantOnlyExecutionGateDecision.PendingExplicitExecutionTask)
      assert(qdrantOnly.pendingExplicitExecutionTask)
      assert(qdrantOnly.qdrantOnlyPrerequisitesComplete)
      assert(qdrantOnly.qdrantOnlyPlanned)
      assert(qdrantOnly.decision.render == "pending_explicit_execution_task")
      assert(qdrantOnly.reasons.contains(M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.QdrantOnlyPrerequisitesComplete))

      val all = List(default, esOnly, qdrantOnly, combined)
      assert(all.count(_.pendingExplicitExecutionTask) == 1)
    }

    "keep the decision planning/design only with no real backend call/client/route/plugin/DI/http" in {
      List(default, esOnly, qdrantOnly, combined).foreach { r =>
        assert(r.designOnly)
        assert(!r.realBackendCallImplemented)
        assert(!r.realBackendCallRequired)
        assert(!r.qdrantRun)
        assert(!r.qdrantClientCreated)
        assert(!r.productionBeautySearchCalled)
        assert(!r.routePluginDiHttpInvolved)
      }
    }

    "preserve Qdrant production activation not approved and default /beauty-search ES-backed" in {
      List(default, esOnly, qdrantOnly, combined).foreach { r =>
        assert(!r.productionActivationApproved)
        assert(!r.qdrantProductionActivationApproved)
        assert(r.defaultBeautySearchEsBacked)
        assert(!r.qualityGreenClaimed)
        assert(!r.productionReadinessClaimed)
        assert(!r.routeActivationClaimed)
        assert(!r.servingApprovalClaimed)
        assert(!r.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented)
        assert(r.reasons.contains(M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.QdrantProductionActivationNotApproved))
        assert(r.reasons.contains(M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.DefaultBeautySearchRemainsEsBacked))
      }
    }

    "require a separate explicit operator-approved task and saved evidence artifact capture even when pending" in {
      assert(qdrantOnly.requiresSeparateExplicitOperatorApprovedTask)
      assert(qdrantOnly.requiresSavedEvidenceArtifactCapture)
      assert(qdrantOnly.reasons.contains(
        M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.RequiresSeparateExplicitOperatorApprovedTask))
      assert(qdrantOnly.reasons.contains(
        M9BeautyQSearchEvalQdrantOnlyExecutionGateReason.RequiresSavedEvidenceArtifactCapture))
    }

    "name the saved evidence schema and deterministic renderer artifact as the required capture path" in {
      assert(qdrantOnly.requiredEvidenceSchemaKind == M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.QdrantOnlySmoke)
      assert(
        qdrantOnly.requiredEvidenceCaptureArtifact ==
          M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.DefaultExampleFilename
      )
    }
  }
}
