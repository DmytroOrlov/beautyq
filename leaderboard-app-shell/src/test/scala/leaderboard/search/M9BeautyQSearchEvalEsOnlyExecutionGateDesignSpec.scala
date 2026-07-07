package leaderboard.search

import leaderboard.search.eval.{
  M9BeautyQSearchEvalEsOnlyExecutionGateDecision,
  M9BeautyQSearchEvalEsOnlyExecutionGateDesign,
  M9BeautyQSearchEvalEsOnlyExecutionGateReason,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceKind,
  M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalEsOnlyExecutionGateDesignSpec extends AnyWordSpec {

  private val default = M9BeautyQSearchEvalEsOnlyExecutionGateDesign.DefaultResult
  private val esOnly = M9BeautyQSearchEvalEsOnlyExecutionGateDesign.gateForEsOnlyConfig
  private val qdrantOnly = M9BeautyQSearchEvalEsOnlyExecutionGateDesign.gateForQdrantOnlyConfig
  private val combined = M9BeautyQSearchEvalEsOnlyExecutionGateDesign.gateForCombinedConfig

  "M9BeautyQSearchEvalEsOnlyExecutionGateDesign" should {

    "block the default / no-config state" in {
      assert(default.decision == M9BeautyQSearchEvalEsOnlyExecutionGateDecision.BlockedEsOnlyPrerequisitesIncomplete)
      assert(default.blocked)
      assert(!default.pendingExplicitExecutionTask)
      assert(!default.esOnlyPrerequisitesComplete)
      assert(default.reasons.contains(M9BeautyQSearchEvalEsOnlyExecutionGateReason.EsResourceConfigMissing))
      assert(default.decision.render == "blocked_es_only_prerequisites_incomplete")
    }

    "block the Qdrant-only path" in {
      assert(qdrantOnly.blocked)
      assert(!qdrantOnly.pendingExplicitExecutionTask)
      assert(qdrantOnly.reasons.contains(
        M9BeautyQSearchEvalEsOnlyExecutionGateReason.CheckpointTargetsQdrantOnlyNotEsOnly))
    }

    "block the combined-only path even though ES-only prerequisites are complete" in {
      assert(combined.decision == M9BeautyQSearchEvalEsOnlyExecutionGateDecision.BlockedNotEsOnlyTarget)
      assert(combined.blocked)
      assert(!combined.pendingExplicitExecutionTask)
      assert(combined.esOnlyPrerequisitesComplete)
      assert(!combined.esOnlyPlanned)
      assert(combined.reasons.contains(
        M9BeautyQSearchEvalEsOnlyExecutionGateReason.CheckpointTargetsCombinedComparisonNotEsOnly))
      assert(combined.decision.render == "blocked_not_es_only_target")
    }

    "allow only the ES-only prerequisites-complete state to become pending_explicit_execution_task" in {
      assert(esOnly.decision == M9BeautyQSearchEvalEsOnlyExecutionGateDecision.PendingExplicitExecutionTask)
      assert(esOnly.pendingExplicitExecutionTask)
      assert(esOnly.esOnlyPrerequisitesComplete)
      assert(esOnly.esOnlyPlanned)
      assert(esOnly.decision.render == "pending_explicit_execution_task")
      assert(esOnly.reasons.contains(M9BeautyQSearchEvalEsOnlyExecutionGateReason.EsOnlyPrerequisitesComplete))

      // It is the unique pending state across the design's enumerated config states.
      val all = List(default, esOnly, qdrantOnly, combined)
      assert(all.count(_.pendingExplicitExecutionTask) == 1)
    }

    "keep the decision planning/design only with no real backend call/client/route/plugin/DI/http" in {
      List(default, esOnly, qdrantOnly, combined).foreach { r =>
        assert(r.designOnly)
        assert(!r.realBackendCallImplemented)
        assert(!r.realBackendCallRequired)
        assert(!r.elasticsearchRun)
        assert(!r.esClientCreated)
        assert(!r.productionBeautySearchCalled)
        assert(!r.routePluginDiHttpInvolved)
      }
    }

    "keep production activation false and Qdrant production activation not approved" in {
      List(default, esOnly, qdrantOnly, combined).foreach { r =>
        assert(!r.productionActivationApproved)
        assert(!r.qdrantProductionActivationApproved)
        assert(r.defaultBeautySearchEsBacked)
        assert(!r.qualityGreenClaimed)
        assert(!r.productionReadinessClaimed)
        assert(!r.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented)
        assert(r.reasons.contains(M9BeautyQSearchEvalEsOnlyExecutionGateReason.ProductionActivationNotApproved))
        assert(r.reasons.contains(M9BeautyQSearchEvalEsOnlyExecutionGateReason.QdrantProductionActivationNotApproved))
        assert(r.reasons.contains(M9BeautyQSearchEvalEsOnlyExecutionGateReason.DefaultBeautySearchRemainsEsBacked))
      }
    }

    "require a separate explicit operator-approved task and saved evidence artifact capture even when pending" in {
      assert(esOnly.requiresSeparateExplicitOperatorApprovedTask)
      assert(esOnly.requiresSavedEvidenceArtifactCapture)
      assert(esOnly.reasons.contains(
        M9BeautyQSearchEvalEsOnlyExecutionGateReason.RequiresSeparateExplicitOperatorApprovedTask))
      assert(esOnly.reasons.contains(
        M9BeautyQSearchEvalEsOnlyExecutionGateReason.RequiresSavedEvidenceArtifactCapture))
    }

    "name the saved evidence schema and deterministic renderer artifact as the required capture path" in {
      assert(esOnly.requiredEvidenceSchemaKind == M9BeautyQSearchEvalRealResourceSmokeEvidenceKind.EsOnlySmoke)
      assert(
        esOnly.requiredEvidenceCaptureArtifact ==
          M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer.DefaultExampleFilename
      )
    }
  }
}
