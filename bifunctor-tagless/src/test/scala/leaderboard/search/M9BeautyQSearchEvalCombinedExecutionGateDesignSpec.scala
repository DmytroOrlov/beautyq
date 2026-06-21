package leaderboard.search

import leaderboard.search.eval.{
  M9BeautyQSearchEvalCombinedExecutionGateDecision,
  M9BeautyQSearchEvalCombinedExecutionGateDesign,
  M9BeautyQSearchEvalCombinedExecutionGateReason,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceKind,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalCombinedExecutionGateDesignSpec extends AnyWordSpec {

  private val default = M9BeautyQSearchEvalCombinedExecutionGateDesign.DefaultResult
  private val esOnly = M9BeautyQSearchEvalCombinedExecutionGateDesign.gateForEsOnlyConfig
  private val qdrantOnly = M9BeautyQSearchEvalCombinedExecutionGateDesign.gateForQdrantOnlyConfig
  private val bothNoOperator = M9BeautyQSearchEvalCombinedExecutionGateDesign.gateForBothConfigNoOperatorApproval
  private val combined = M9BeautyQSearchEvalCombinedExecutionGateDesign.gateForCombinedConfig

  private val blockedStates = List(default, esOnly, qdrantOnly, bothNoOperator)
  private val allStates = combined :: blockedStates

  "M9BeautyQSearchEvalCombinedExecutionGateDesign" should {

    "block the default / no-config state" in {
      assert(default.decision == M9BeautyQSearchEvalCombinedExecutionGateDecision.BlockedCombinedPrerequisitesIncomplete)
      assert(default.blocked)
      assert(!default.combinedPrerequisitesComplete)
      assert(default.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.EsResourceConfigMissing))
      assert(default.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.QdrantResourceConfigMissing))
      assert(default.decision.render == "blocked_combined_prerequisites_incomplete")
    }

    "block the ES-only state" in {
      assert(esOnly.blocked)
      assert(!esOnly.pendingExplicitExecutionTask)
      assert(esOnly.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.CheckpointTargetsEsOnlyNotCombined))
      assert(esOnly.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.QdrantResourceConfigMissing))
    }

    "block the Qdrant-only state" in {
      assert(qdrantOnly.blocked)
      assert(!qdrantOnly.pendingExplicitExecutionTask)
      assert(qdrantOnly.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.CheckpointTargetsQdrantOnlyNotCombined))
      assert(qdrantOnly.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.EsResourceConfigMissing))
    }

    "block the both-config-without-operator-approval state" in {
      assert(bothNoOperator.blocked)
      assert(!bothNoOperator.pendingExplicitExecutionTask)
      assert(!bothNoOperator.combinedPrerequisitesComplete)
      assert(bothNoOperator.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.OperatorApprovalMissing))
    }

    "allow only operator-approved combined prerequisites to become pending_explicit_execution_task" in {
      assert(combined.decision == M9BeautyQSearchEvalCombinedExecutionGateDecision.PendingExplicitExecutionTask)
      assert(combined.pendingExplicitExecutionTask)
      assert(combined.combinedPrerequisitesComplete)
      assert(combined.combinedPlanned)
      assert(combined.decision.render == "pending_explicit_execution_task")
      assert(combined.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.CombinedPrerequisitesComplete))

      assert(allStates.count(_.pendingExplicitExecutionTask) == 1)
      assert(blockedStates.forall(_.blocked))
    }

    "keep the decision planning/design only with no real backend call/client/route/plugin/DI/http" in {
      allStates.foreach { r =>
        assert(r.designOnly)
        assert(!r.realBackendCallImplemented)
        assert(!r.realBackendCallRequired)
        assert(!r.elasticsearchRun)
        assert(!r.qdrantRun)
        assert(!r.esClientCreated)
        assert(!r.qdrantClientCreated)
        assert(!r.productionBeautySearchCalled)
        assert(!r.routePluginDiHttpInvolved)
      }
    }

    "preserve Qdrant production activation not approved and default /beauty-search ES-backed with no forbidden claim" in {
      allStates.foreach { r =>
        assert(!r.productionActivationApproved)
        assert(!r.qdrantProductionActivationApproved)
        assert(r.defaultBeautySearchEsBacked)
        assert(!r.qualityGreenClaimed)
        assert(!r.productionReadinessClaimed)
        assert(!r.routeActivationClaimed)
        assert(!r.servingApprovalClaimed)
        assert(!r.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented)
        assert(r.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.QdrantProductionActivationNotApproved))
        assert(r.reasons.contains(M9BeautyQSearchEvalCombinedExecutionGateReason.DefaultBeautySearchRemainsEsBacked))
        assert(r.reasons.contains(
          M9BeautyQSearchEvalCombinedExecutionGateReason.NoHybridFallbackFusionRerankingTelemetryRouteSwitch))
      }
    }

    "require a separate explicit operator-approved task and saved evidence artifact capture even when pending" in {
      assert(combined.requiresSeparateExplicitOperatorApprovedTask)
      assert(combined.requiresSavedEvidenceArtifactCapture)
      assert(combined.reasons.contains(
        M9BeautyQSearchEvalCombinedExecutionGateReason.RequiresSeparateExplicitOperatorApprovedTask))
      assert(combined.reasons.contains(
        M9BeautyQSearchEvalCombinedExecutionGateReason.RequiresSavedEvidenceArtifactCapture))
    }

    "name the saved evidence schema and deterministic renderer artifact as the required capture path" in {
      assert(combined.requiredEvidenceSchemaKind == M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsQdrantComparison)
      assert(
        combined.requiredEvidenceCaptureArtifact ==
          M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.DefaultExampleFilename
      )
    }
  }
}
