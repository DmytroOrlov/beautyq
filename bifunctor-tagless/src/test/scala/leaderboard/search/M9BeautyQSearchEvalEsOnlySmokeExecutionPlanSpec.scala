package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9BeautyQSearchEvalEsOnlySmokeExecutionPlan,
  M9BeautyQSearchEvalEsOnlySmokePlanReason,
  M9BeautyQSearchEvalEsOnlySmokePlanStatus,
  M9BeautyQSearchEvalRealCallCheckpoint,
  M9BeautyQSearchEvalRealCallCheckpointDecision,
  M9BeautyQSearchEvalRealCallCheckpointInput,
  M9BeautyQSearchEvalRealCallCheckpointResult,
  M9OfflineEvalBackendExecutionMode,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalEsOnlySmokeExecutionPlanSpec extends AnyWordSpec {

  private def checkpoint(
    esResourceConfigPresent: Boolean = false,
    qdrantResourceConfigPresent: Boolean = false,
    operatorApprovalGranted: Boolean = false,
  ): M9BeautyQSearchEvalRealCallCheckpointResult =
    M9BeautyQSearchEvalRealCallCheckpoint.decide(
      M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
        esResourceConfigPresent = esResourceConfigPresent,
        qdrantResourceConfigPresent = qdrantResourceConfigPresent,
        operatorApprovalGranted = operatorApprovalGranted,
      )
    )

  "M9BeautyQSearchEvalEsOnlySmokeExecutionPlan" should {

    "not plan execution for the default/blocked checkpoint with no resource config" in {
      val result = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.plan(
        M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult
      )

      assert(result.status == M9BeautyQSearchEvalEsOnlySmokePlanStatus.NotPlanned)
      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleNoExplicitResourceConfig)
      assert(result.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.CheckpointNotEsOnlyEligible))
      assert(result.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.CheckpointNotEligibleNoExplicitResourceConfig))
    }

    "not plan execution when the static scorecard is not ready" in {
      val notReady = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          scorecard = M9BeautyQSearchEvalRealCallCheckpointInput.Default.scorecard.copy(
            verdict = "dataset_static_rows_not_ready"
          ),
          esResourceConfigPresent = true,
        )
      )
      val result = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.plan(notReady)

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.CheckpointBlockedStaticScorecardNotReady))
    }

    "produce only an offline ES-only plan for an ES-only eligible checkpoint" in {
      val result = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.plan(checkpoint(esResourceConfigPresent = true))

      assert(result.status == M9BeautyQSearchEvalEsOnlySmokePlanStatus.Planned)
      assert(result.planned)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsOnlyResourceGatedSmoke)

      result.plan match {
        case Some(plan) =>
          assert(plan.requiredCheckpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsOnlyResourceGatedSmoke)
          assert(plan.offlineResourceGatedSmokeOnly)
          assert(plan.artifactShape.executionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
          assert(plan.artifactShape.candidateSource == CandidateSource.Es)
          assert(plan.artifactShape.servingMode == ServingMode.EsOnly)
          assert(plan.selectedQueries.nonEmpty)
          assert(plan.selectedQueries.map(_.queryId) == M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.SelectedQueries.map(_.queryId))
          assert(plan.prerequisites.nonEmpty)
          assert(plan.skipReasons.nonEmpty)
          assert(plan.validationBoundaries.nonEmpty)
          assert(!plan.realBackendCallImplemented)
          assert(!plan.realBackendCallRequired)
          assert(!plan.esClientCreated)
          assert(!plan.productionBeautySearchCalled)
          assert(!plan.routePluginDiHttpInvolved)
          assert(!plan.productionActivationApproved)
          assert(!plan.qdrantProductionActivationApproved)
          assert(!plan.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented)
        case None =>
          fail("expected an ES-only offline plan for an ES-only eligible checkpoint")
      }
    }

    "not produce an ES-only execution for a Qdrant-only eligible checkpoint" in {
      val result = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.plan(checkpoint(qdrantResourceConfigPresent = true))

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForQdrantOnlyResourceGatedSmoke)
      assert(result.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.CheckpointEligibleForQdrantOnlyNotEsOnly))
    }

    "not produce an ES-only execution for a both-config comparison checkpoint" in {
      val result = M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.plan(
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true, operatorApprovalGranted = true)
      )

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsQdrantResourceGatedComparison)
      assert(result.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.CheckpointEligibleForEsQdrantComparisonNotEsOnly))
    }

    "preserve offline/boundary reasons for every checkpoint decision and require no real backend call" in {
      val inputs = List(
        checkpoint(),
        checkpoint(esResourceConfigPresent = true),
        checkpoint(qdrantResourceConfigPresent = true),
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true),
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true, operatorApprovalGranted = true),
      )

      val results = inputs.map(M9BeautyQSearchEvalEsOnlySmokeExecutionPlan.plan)

      assert(results.forall(!_.realBackendCallImplemented))
      assert(results.forall(!_.realBackendCallRequired))
      assert(results.forall(!_.productionActivationApproved))
      assert(results.forall(!_.qdrantProductionActivationApproved))
      assert(results.forall(!_.routePluginDiHttpInvolved))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.ProductionActivationNotApproved)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.QdrantProductionActivationNotApproved)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.NoProductionRouteChange)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.RealBackendCallNotImplemented)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalEsOnlySmokePlanReason.NoHybridFallbackFusionRerankingTelemetryRouteSwitch)))
    }

    "never render forbidden production/hybrid claims in plan vocabulary" in {
      val forbidden = List("hybrid", "fallback", "fusion", "rerank", "route_switch", "production_telemetry")
      val tokens =
        M9BeautyQSearchEvalEsOnlySmokePlanReason.values.toList.map(_.render) ++
          M9BeautyQSearchEvalEsOnlySmokePlanStatus.values.toList.map(_.render)

      assert(tokens.forall(token => forbidden.forall { claim =>
        // "no_hybrid_..._route_switch" intentionally negates these terms; they must not appear as positive claims.
        !token.contains(claim) || token.startsWith("no_")
      }))
    }
  }
}
