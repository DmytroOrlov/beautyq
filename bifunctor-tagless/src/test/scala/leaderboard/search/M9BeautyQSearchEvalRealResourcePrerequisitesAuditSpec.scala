package leaderboard.search

import leaderboard.search.eval.{
  M9BeautyQSearchEvalCombinedSmokeComparisonPlan,
  M9BeautyQSearchEvalEsOnlySmokeExecutionPlan,
  M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan,
  M9BeautyQSearchEvalRealCallCheckpoint,
  M9BeautyQSearchEvalRealCallCheckpointDecision,
  M9BeautyQSearchEvalRealCallCheckpointInput,
  M9BeautyQSearchEvalRealCallCheckpointResult,
  M9BeautyQSearchEvalRealResourcePrerequisiteReason,
  M9BeautyQSearchEvalRealResourcePrerequisiteStatus,
  M9BeautyQSearchEvalRealResourcePrerequisiteTarget,
  M9BeautyQSearchEvalRealResourcePrerequisitesAudit,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalRealResourcePrerequisitesAuditSpec extends AnyWordSpec {

  private def checkpoint(
    esResourceConfigPresent: Boolean,
    qdrantResourceConfigPresent: Boolean,
    operatorApprovalGranted: Boolean,
    verdict: String,
  ): M9BeautyQSearchEvalRealCallCheckpointResult =
    M9BeautyQSearchEvalRealCallCheckpoint.decide(
      M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
        scorecard = M9BeautyQSearchEvalRealCallCheckpointInput.Default.scorecard.copy(verdict = verdict),
        esResourceConfigPresent = esResourceConfigPresent,
        qdrantResourceConfigPresent = qdrantResourceConfigPresent,
        operatorApprovalGranted = operatorApprovalGranted,
      )
    )

  "M9BeautyQSearchEvalRealResourcePrerequisitesAudit" should {

    "block all future executions for the default/no-config audit" in {
      val result = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.DefaultResult

      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleNoExplicitResourceConfig)
      assert(result.entries.forall(!_.prerequisitesComplete))
      assert(!result.esOnly.prerequisitesComplete)
      assert(!result.qdrantOnly.prerequisitesComplete)
      assert(!result.esQdrantComparison.prerequisitesComplete)
      assert(result.esOnly.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.EsResourceConfigMissing))
      assert(result.qdrantOnly.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.QdrantResourceConfigMissing))
      assert(result.esQdrantComparison.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.EsResourceConfigMissing))
      assert(result.esQdrantComparison.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.QdrantResourceConfigMissing))
      assert(result.esQdrantComparison.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.OperatorApprovalMissing))
    }

    "complete only ES-only prerequisites when only ES config is present" in {
      val result = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsOnlyConfig

      assert(result.esOnly.prerequisitesComplete)
      assert(result.esOnly.status == M9BeautyQSearchEvalRealResourcePrerequisiteStatus.PrerequisitesComplete)
      assert(result.esOnly.blockReasons.isEmpty)
      assert(!result.qdrantOnly.prerequisitesComplete)
      assert(result.qdrantOnly.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.QdrantResourceConfigMissing))
      assert(!result.esQdrantComparison.prerequisitesComplete)
      assert(result.esQdrantComparison.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.QdrantResourceConfigMissing))
      assert(result.esQdrantComparison.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.OperatorApprovalMissing))
    }

    "complete only Qdrant-only prerequisites when only Qdrant config is present" in {
      val result = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForQdrantOnlyConfig

      assert(result.qdrantOnly.prerequisitesComplete)
      assert(result.qdrantOnly.blockReasons.isEmpty)
      assert(!result.esOnly.prerequisitesComplete)
      assert(result.esOnly.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.EsResourceConfigMissing))
      assert(!result.esQdrantComparison.prerequisitesComplete)
      assert(result.esQdrantComparison.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.EsResourceConfigMissing))
      assert(result.esQdrantComparison.blockReasons.contains(M9BeautyQSearchEvalRealResourcePrerequisiteReason.OperatorApprovalMissing))
    }

    "not complete combined comparison prerequisites with both configs but no operator approval" in {
      val result = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.audit(
        checkpoint(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = false,
          verdict = M9BeautyQSearchEvalRealCallCheckpointInput.Default.scorecard.verdict,
        )
      )

      assert(result.esOnly.prerequisitesComplete)
      assert(result.qdrantOnly.prerequisitesComplete)
      assert(!result.esQdrantComparison.prerequisitesComplete)
      assert(result.esQdrantComparison.blockReasons == List(M9BeautyQSearchEvalRealResourcePrerequisiteReason.OperatorApprovalMissing))
    }

    "complete combined comparison prerequisites with both configs and operator approval" in {
      val result = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsQdrantComparison

      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsQdrantResourceGatedComparison)
      assert(result.esOnly.prerequisitesComplete)
      assert(result.qdrantOnly.prerequisitesComplete)
      assert(result.esQdrantComparison.prerequisitesComplete)
      assert(result.esQdrantComparison.blockReasons.isEmpty)
      assert(result.esQdrantComparison.requiredCheckpointDecision == M9BeautyQSearchEvalCombinedSmokeComparisonPlan.RequiredCheckpointDecision)
    }

    "block every future execution when the static scorecard/checkpoint is not ready" in {
      val result = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.audit(
        checkpoint(
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
          verdict = "dataset_static_rows_not_ready",
        )
      )

      assert(!result.staticScorecardReady)
      assert(result.entries.forall(!_.prerequisitesComplete))
      assert(result.entries.forall(_.blockReasons.contains(
        M9BeautyQSearchEvalRealResourcePrerequisiteReason.StaticScorecardOrCheckpointNotReady
      )))
    }

    "reference the accepted plan contracts for required checkpoint decisions and evidence artifacts" in {
      val result = M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsQdrantComparison

      assert(result.esOnly.requiredCheckpointDecision == M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.RequiredCheckpointDecision)
      assert(result.qdrantOnly.requiredCheckpointDecision == M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.RequiredCheckpointDecision)
      assert(result.esQdrantComparison.requiredCheckpointDecision == M9BeautyQSearchEvalCombinedSmokeComparisonPlan.RequiredCheckpointDecision)
      assert(result.esOnly.expectedEvidenceArtifacts.contains(M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.MarkdownFilename))
      assert(result.qdrantOnly.expectedEvidenceArtifacts.contains(M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.MarkdownFilename))
      assert(result.esQdrantComparison.expectedEvidenceArtifacts.contains(M9BeautyQSearchEvalCombinedSmokeComparisonPlan.MarkdownFilename))
      assert(result.entries.forall(_.minimumValidationEvidenceFields.nonEmpty))
    }

    "preserve standing boundaries and require no real backend call across every audit decision" in {
      val results = List(
        M9BeautyQSearchEvalRealResourcePrerequisitesAudit.DefaultResult,
        M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsOnlyConfig,
        M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForQdrantOnlyConfig,
        M9BeautyQSearchEvalRealResourcePrerequisitesAudit.audit(
          checkpoint(
            esResourceConfigPresent = true,
            qdrantResourceConfigPresent = true,
            operatorApprovalGranted = false,
            verdict = M9BeautyQSearchEvalRealCallCheckpointInput.Default.scorecard.verdict,
          )
        ),
        M9BeautyQSearchEvalRealResourcePrerequisitesAudit.auditForEsQdrantComparison,
      )

      assert(results.forall(_.planningOnly))
      assert(results.forall(!_.realBackendCallImplemented))
      assert(results.forall(!_.realBackendCallRequired))
      assert(results.forall(!_.esClientCreated))
      assert(results.forall(!_.qdrantClientCreated))
      assert(results.forall(!_.productionBeautySearchCalled))
      assert(results.forall(!_.routePluginDiHttpInvolved))
      assert(results.forall(!_.productionActivationApproved))
      assert(results.forall(!_.qdrantProductionActivationApproved))
      assert(results.forall(_.defaultBeautySearchEsBacked))
      assert(results.forall(!_.qualityGreenClaimed))
      assert(results.forall(!_.productionReadinessClaimed))
      assert(results.forall(!_.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented))
      assert(results.forall(_.entries.forall(_.standingBoundaries.contains(
        M9BeautyQSearchEvalRealResourcePrerequisiteReason.RealBackendGateDisabledByDefault
      ))))
      assert(results.forall(_.entries.forall(_.standingBoundaries.contains(
        M9BeautyQSearchEvalRealResourcePrerequisiteReason.ProductionActivationBoundaryNotApproved
      ))))
      assert(results.forall(_.entries.forall(entry => !entry.qualityGreenClaimed && !entry.productionReadinessClaimed)))
    }

    "never expose forbidden production/hybrid/quality vocabulary in audit tokens" in {
      val forbidden = List("hybrid", "fallback", "fusion", "rerank", "route_switch", "production_telemetry", "quality_green", "readiness")
      val tokens =
        M9BeautyQSearchEvalRealResourcePrerequisiteTarget.values.toList.map(_.render) ++
          M9BeautyQSearchEvalRealResourcePrerequisiteStatus.values.toList.map(_.render) ++
          M9BeautyQSearchEvalRealResourcePrerequisiteReason.values.toList.map(_.render)

      assert(tokens.forall(token => forbidden.forall(claim => !token.contains(claim))))
    }
  }
}
