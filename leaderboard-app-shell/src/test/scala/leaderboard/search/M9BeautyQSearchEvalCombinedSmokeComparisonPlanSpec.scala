package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9BeautyQSearchEvalCombinedComparisonDimension,
  M9BeautyQSearchEvalCombinedComparisonPlanReason,
  M9BeautyQSearchEvalCombinedComparisonPlanStatus,
  M9BeautyQSearchEvalCombinedSmokeComparisonPlan,
  M9BeautyQSearchEvalRealCallCheckpoint,
  M9BeautyQSearchEvalRealCallCheckpointDecision,
  M9BeautyQSearchEvalRealCallCheckpointInput,
  M9BeautyQSearchEvalRealCallCheckpointResult,
  M9OfflineEvalBackendExecutionMode,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9BeautyQSearchEvalCombinedSmokeComparisonPlanSpec extends AnyWordSpec {

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

  "M9BeautyQSearchEvalCombinedSmokeComparisonPlan" should {

    "not plan a comparison for the default/blocked checkpoint with no resource config" in {
      val result = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan(
        M9BeautyQSearchEvalRealCallCheckpoint.DefaultResult
      )

      assert(result.status == M9BeautyQSearchEvalCombinedComparisonPlanStatus.NotPlanned)
      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleNoExplicitResourceConfig)
      assert(result.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.CheckpointNotEsQdrantComparisonEligible))
      assert(result.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.CheckpointNotEligibleNoExplicitResourceConfig))
    }

    "not plan a comparison when the static scorecard is not ready" in {
      val notReady = M9BeautyQSearchEvalRealCallCheckpoint.decide(
        M9BeautyQSearchEvalRealCallCheckpointInput.Default.copy(
          scorecard = M9BeautyQSearchEvalRealCallCheckpointInput.Default.scorecard.copy(
            verdict = "dataset_static_rows_not_ready"
          ),
          esResourceConfigPresent = true,
          qdrantResourceConfigPresent = true,
          operatorApprovalGranted = true,
        )
      )
      val result = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan(notReady)

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.CheckpointBlockedStaticScorecardNotReady))
    }

    "not plan a comparison for an ES-only eligible checkpoint" in {
      val result = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan(checkpoint(esResourceConfigPresent = true))

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsOnlyResourceGatedSmoke)
      assert(result.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.CheckpointEligibleForEsOnlyNotComparison))
    }

    "not plan a comparison for a Qdrant-only eligible checkpoint" in {
      val result = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan(checkpoint(qdrantResourceConfigPresent = true))

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForQdrantOnlyResourceGatedSmoke)
      assert(result.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.CheckpointEligibleForQdrantOnlyNotComparison))
    }

    "not plan a comparison for a both-config checkpoint without operator approval" in {
      val result = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan(
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true)
      )

      assert(!result.planned)
      assert(result.plan.isEmpty)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.NotEligibleOperatorApprovalRequired)
      assert(result.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.CheckpointNotEligibleOperatorApprovalRequired))
    }

    "produce only an offline comparison plan for the both-config operator-approved checkpoint" in {
      val result = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan(
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true, operatorApprovalGranted = true)
      )

      assert(result.status == M9BeautyQSearchEvalCombinedComparisonPlanStatus.Planned)
      assert(result.planned)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsQdrantResourceGatedComparison)

      result.plan match {
        case Some(plan) =>
          assert(plan.requiredCheckpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsQdrantResourceGatedComparison)
          assert(plan.offlineResourceGatedComparisonOnly)
          assert(plan.artifactShape.esExecutionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
          assert(plan.artifactShape.qdrantExecutionMode == M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
          assert(plan.artifactShape.candidateSources == List(CandidateSource.Es, CandidateSource.Qdrant))
          assert(plan.selectedQueries.nonEmpty)
          assert(plan.selectedQueries.map(_.queryId) == M9BeautyQSearchEvalCombinedSmokeComparisonPlan.SelectedQueries.map(_.queryId))
          assert(plan.prerequisites.nonEmpty)
          assert(plan.comparisonDimensions == M9BeautyQSearchEvalCombinedComparisonDimension.values.toList)
          assert(plan.skipReasons.nonEmpty)
          assert(plan.validationBoundaries.nonEmpty)
          assert(!plan.realBackendCallImplemented)
          assert(!plan.realBackendCallRequired)
          assert(!plan.esClientCreated)
          assert(!plan.qdrantClientCreated)
          assert(!plan.productionBeautySearchCalled)
          assert(!plan.routePluginDiHttpInvolved)
          assert(!plan.productionActivationApproved)
          assert(!plan.qdrantProductionActivationApproved)
          assert(plan.defaultBeautySearchEsBacked)
          assert(!plan.qualityGreenClaimed)
          assert(!plan.productionReadinessClaimed)
          assert(!plan.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented)
        case None =>
          fail("expected a combined comparison plan for the both-config operator-approved checkpoint")
      }
    }

    "expose the same plan via the convenience accessor" in {
      val result = M9BeautyQSearchEvalCombinedSmokeComparisonPlan.planForEsQdrantComparisonEligibleCheckpoint
      assert(result.planned)
      assert(result.checkpointDecision == M9BeautyQSearchEvalRealCallCheckpointDecision.EligibleForEsQdrantResourceGatedComparison)
    }

    "preserve offline/boundary reasons for every checkpoint decision and require no real backend call" in {
      val inputs = List(
        checkpoint(),
        checkpoint(esResourceConfigPresent = true),
        checkpoint(qdrantResourceConfigPresent = true),
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true),
        checkpoint(esResourceConfigPresent = true, qdrantResourceConfigPresent = true, operatorApprovalGranted = true),
      )

      val results = inputs.map(M9BeautyQSearchEvalCombinedSmokeComparisonPlan.plan)

      assert(results.forall(!_.realBackendCallImplemented))
      assert(results.forall(!_.realBackendCallRequired))
      assert(results.forall(!_.productionActivationApproved))
      assert(results.forall(!_.qdrantProductionActivationApproved))
      assert(results.forall(_.defaultBeautySearchEsBacked))
      assert(results.forall(!_.qualityGreenClaimed))
      assert(results.forall(!_.productionReadinessClaimed))
      assert(results.forall(!_.routePluginDiHttpInvolved))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.ProductionActivationNotApproved)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.QdrantProductionActivationNotApproved)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.DefaultBeautySearchEsBacked)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.NoProductionRouteChange)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.RealBackendCallNotImplemented)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.NoQualityGreenOrProductionReadinessClaim)))
      assert(results.forall(_.reasons.contains(M9BeautyQSearchEvalCombinedComparisonPlanReason.NoHybridFallbackFusionRerankingTelemetryRouteSwitch)))

      // Exactly one of the five decisions can produce a planned comparison.
      assert(results.count(_.planned) == 1)
    }

    "never render forbidden production/hybrid claims in plan vocabulary" in {
      val forbidden = List("hybrid", "fallback", "fusion", "rerank", "route_switch", "production_telemetry")
      val tokens =
        M9BeautyQSearchEvalCombinedComparisonPlanReason.values.toList.map(_.render) ++
          M9BeautyQSearchEvalCombinedComparisonPlanStatus.values.toList.map(_.render) ++
          M9BeautyQSearchEvalCombinedComparisonDimension.values.toList.map(_.render)

      assert(tokens.forall(token => forbidden.forall { claim =>
        // "no_hybrid_..._route_switch" intentionally negates these terms; they must not appear as positive claims.
        !token.contains(claim) || token.startsWith("no_")
      }))
    }
  }
}
