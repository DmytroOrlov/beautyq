package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan,
  M9BeautyQSearchEvalQdrantOnlySmokePlanReason,
  M9BeautyQSearchEvalQdrantOnlySmokePlanStatus,
  M9BeautyQSearchEvalRealCallCheckpoint,
  M9BeautyQSearchEvalRealCallCheckpointDecision,
  M9BeautyQSearchEvalRealCallCheckpointInput,
  M9BeautyQSearchEvalRealCallCheckpointResult,
  M9OfflineEvalBackendExecutionMode,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlanSpec extends AnyWordSpec {

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

  "M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan" should {

    "not plan execution for the default/blocked checkpoint with no resource config" in {
      val result = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.plan(
        M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult
      )

      assert(result.status == M9BeautyQSearchEvalQdrantOnlySmokePlanStatus.NotPlanned)
      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleNoExplicitResourceConfig)
      assert(result.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.CheckpointNotQdrantOnlyEligible))
      assert(result.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.CheckpointNotEligibleNoExplicitResourceConfig))
    }

    "not plan execution when the static scorecard is not ready" in {
      val notReady = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          scorecard = M9BeautyQSearchEvalRealCallCheckpointInput.Default.scorecard.copy(
            verdict = "dataset_static_rows_not_ready"
          ),
          qdrantResourceConfigPresent = true,
        )
      )
      val result = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.plan(notReady)

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.CheckpointBlockedStaticScorecardNotReady))
    }

    "produce only an offline Qdrant-only plan for a Qdrant-only eligible checkpoint" in {
      val result = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.plan(checkpoint(qdrantResourceConfigPresent = true))

      assert(result.status == M9BeautyQSearchEvalQdrantOnlySmokePlanStatus.Planned)
      assert(result.planned)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForQdrantOnlyResourceGatedSmoke)

      result.plan match {
        case Some(plan) =>
          assert(plan.requiredCheckpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForQdrantOnlyResourceGatedSmoke)
          assert(plan.offlineResourceGatedSmokeOnly)
          assert(plan.artifactShape.executionMode == M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
          assert(plan.artifactShape.candidateSource == CandidateSource.Qdrant)
          assert(plan.artifactShape.servingMode == ServingMode.QdrantOnly)
          assert(plan.selectedQueries.nonEmpty)
          assert(plan.selectedQueries.map(_.queryId) == M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.SelectedQueries.map(_.queryId))
          assert(plan.prerequisites.nonEmpty)
          assert(plan.skipReasons.nonEmpty)
          assert(plan.validationBoundaries.nonEmpty)
          assert(!plan.realBackendCallImplemented)
          assert(!plan.realBackendCallRequired)
          assert(!plan.qdrantClientCreated)
          assert(!plan.productionBeautySearchCalled)
          assert(!plan.routePluginDiHttpInvolved)
          assert(!plan.productionActivationApproved)
          assert(!plan.qdrantProductionActivationApproved)
          assert(plan.defaultBeautySearchEsBacked)
          assert(!plan.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented)
        case None =>
          fail("expected a Qdrant-only offline plan for a Qdrant-only eligible checkpoint")
      }
    }

    "not produce a Qdrant-only execution for an ES-only eligible checkpoint" in {
      val result = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.plan(checkpoint(esResourceConfigPresent = true))

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsOnlyResourceGatedSmoke)
      assert(result.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.CheckpointEligibleForEsOnlyNotQdrantOnly))
    }

    "not produce a Qdrant-only execution for a both-config comparison checkpoint" in {
      val result = M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.plan(
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true, operatorApprovalGranted = true)
      )

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsQdrantResourceGatedComparison)
      assert(result.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.CheckpointEligibleForEsQdrantComparisonNotQdrantOnly))
    }

    "preserve offline/boundary reasons for every checkpoint decision and require no real backend call" in {
      val inputs = List(
        checkpoint(),
        checkpoint(esResourceConfigPresent = true),
        checkpoint(qdrantResourceConfigPresent = true),
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true),
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true, operatorApprovalGranted = true),
      )

      val results = inputs.map(M9BeautyQSearchEvalQdrantOnlySmokeExecutionPlan.plan)

      assert(results.forall(!_.realBackendCallImplemented))
      assert(results.forall(!_.realBackendCallRequired))
      assert(results.forall(!_.productionActivationApproved))
      assert(results.forall(!_.qdrantProductionActivationApproved))
      assert(results.forall(_.defaultBeautySearchEsBacked))
      assert(results.forall(!_.routePluginDiHttpInvolved))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.ProductionActivationNotApproved)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.QdrantProductionActivationNotApproved)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.DefaultBeautySearchEsBacked)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.NoProductionRouteChange)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.RealBackendCallNotImplemented)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalQdrantOnlySmokePlanReason.NoHybridFallbackFusionRerankingTelemetryRouteSwitch)))
    }

    "never render forbidden production/hybrid claims in plan vocabulary" in {
      val forbidden = List("hybrid", "fallback", "fusion", "rerank", "route_switch", "production_telemetry")
      val tokens =
        M9BeautyQSearchEvalQdrantOnlySmokePlanReason.values.toList.map(_.render) ++
          M9BeautyQSearchEvalQdrantOnlySmokePlanStatus.values.toList.map(_.render)

      assert(tokens.forall(token => forbidden.forall { claim =>
        // "no_hybrid_..._route_switch" intentionally negates these terms; they must not appear as positive claims.
        !token.contains(claim) || token.startsWith("no_")
      }))
    }
  }
}
