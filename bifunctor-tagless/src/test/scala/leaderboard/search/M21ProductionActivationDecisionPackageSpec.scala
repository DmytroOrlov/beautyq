package leaderboard.search

import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.ComponentCombinationPolicy
import leaderboard.search.eval.M20ControlledHybridServingSkeleton.M20HybridServingControl
import leaderboard.search.eval.M20BControlledHybridServingOperationalControl.{M20BOperationalControl, ModuleProofScope}
import leaderboard.search.eval.M20CControlledHybridServingCloseout.CloseoutEvidence
import leaderboard.search.eval.M21ProductionActivationDecisionPackage.{
  ActivationDecision,
  ActivationDecisionEvidence,
  ActivationGate,
}
import org.scalatest.wordspec.AnyWordSpec

final class M21ProductionActivationDecisionPackageSpec extends AnyWordSpec {

  // Offline/eval-only policy evidence carrying the M19I honesty flags. M21 reads this strictly as
  // evidence (through M20C -> M20B -> M20A); it never treats it as an activation approval.
  private val evidenceOnlyPolicy: ComponentCombinationPolicy =
    ComponentCombinationPolicy(
      rows = Nil,
      offlineEvalOnly = true,
      notServingPolicy = true,
      doesNotApproveHybrid = true,
      qdrantDoesNotOwnFacets = true,
      qdrantDoesNotOwnInferredFilters = true,
    )

  private val skeleton: M20HybridServingControl =
    M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy)

  private def closeout: CloseoutEvidence =
    CloseoutEvidence.of(M20BOperationalControl.disabledByDefault(skeleton))

  private def decision: ActivationDecisionEvidence =
    ActivationDecisionEvidence.currentDeferral(closeout)

  "M21 production activation decision package (pure decision model)" should {

    "defer production activation as the current verdict" in {
      assert(decision.decision == ActivationDecision.DeferProductionActivation)
      assert(decision.decision.deferred)
      assert(decision.decision.render == "defer_production_activation")
      assert(decision.report.productionActivationDeferred)
    }

    "make approval impossible from the current M20 closeout evidence" in {
      assert(!decision.approvalAllowed)
      assert(!decision.decision.approved)
      assert(!decision.report.productionActivationApproved)

      // Even with every recorded activation gate satisfied, approval stays impossible while the
      // wrapped closeout fixes serving approval and Qdrant production activation to false.
      val gateAllSatisfied = ActivationGate(
        fullProductionRouteOrAcceptedReplacementProof = true,
        runtimeHybridExecutionProof = true,
        operatorRolloutRollbackProofSufficientForActivation = true,
        componentPolicyGapsResolved = true,
        productionLatencyFailureModeEvidence = true,
      )
      val withGates = decision.copy(gate = gateAllSatisfied)
      assert(!withGates.approvalAllowed)
      assert(withGates.decision == ActivationDecision.DeferProductionActivation)
    }

    "not reject activation: this is a defer decision, not a permanent rejection" in {
      assert(!decision.decision.rejected)
      assert(decision.decision != ActivationDecision.RejectProductionActivation)
      assert(!decision.report.productionActivationRejected)
    }

    "keep serving approval false and Qdrant production activation false" in {
      assert(!decision.servingApproved)
      assert(!decision.qdrantProductionActivationApproved)
      assert(!decision.report.servingApproved)
      assert(!decision.report.qdrantProductionActivationApproved)
    }

    "consume the M20 closeout as disabled-controlled-surface evidence, not activation proof" in {
      assert(decision.m20ClosedAsDisabledControlledSurface)
      assert(decision.report.m20ClosedAsDisabledControlledSurface)
      // Closed-as-disabled-controlled-surface is evidence, not approval: it does not enable activation.
      assert(!decision.approvalAllowed)
      assert(decision.decision.deferred)
    }

    "record the accepted-state evidence facts from M18 and M19" in {
      assert(decision.m18RealEsAndQdrantEvidenceExists)
      assert(decision.m19PolicyRemainsOfflineEvalOnly)
      assert(decision.onlyVariantCarouselQdrantPositiveOfflineSupplement)
      assert(decision.providerServiceRemainEvidenceLimited)
      assert(decision.facetsInferredFiltersCurrentOwnerOnly)
      assert(decision.report.m18RealEsAndQdrantEvidenceExists)
      assert(decision.report.m19PolicyRemainsOfflineEvalOnly)
    }

    "keep the route proof scope in-memory-only and let it block approval" in {
      assert(decision.routeProofScope == ModuleProofScope.InMemoryOnly)
      assert(decision.routeProofScopeInMemoryOnly)
      assert(decision.report.routeProofScope == "in_memory_only")
      assert(decision.report.routeProofScopeInMemoryOnly)
      assert(
        decision.blockers.contains("full_production_route_module_proof_or_accepted_replacement_absent")
      )
    }

    "let component-level policy gaps block activation" in {
      assert(!decision.gate.componentPolicyGapsResolved)
      assert(
        decision.blockers.contains("component_policy_gaps_remain_provider_service_facets_inferred_filters")
      )
    }

    "list every explicit blocker required before reconsidering activation" in {
      val blockers = decision.blockers
      assert(blockers.contains("full_production_route_module_proof_or_accepted_replacement_absent"))
      assert(blockers.contains("serving_approval_still_absent"))
      assert(blockers.contains("qdrant_production_activation_approval_still_absent"))
      assert(blockers.contains("runtime_hybrid_execution_proof_absent"))
      assert(blockers.contains("operator_rollout_rollback_proof_insufficient_for_activation"))
      assert(blockers.contains("component_policy_gaps_remain_provider_service_facets_inferred_filters"))
      assert(blockers.contains("production_latency_failure_mode_evidence_absent"))
      assert(decision.report.blockers == blockers)
    }

    "keep all dangerous runtime flags false" in {
      assert(!decision.fallbackEnabled)
      assert(!decision.scoreFusionEnabled)
      assert(!decision.rerankingEnabled)
      assert(!decision.automaticQdrantSupplementEnabled)
      assert(!decision.shadowTrafficEnabled)
      assert(!decision.mirroringEnabled)
      assert(!decision.routeSwitchEnabled)
      assert(decision.allDangerousRuntimeFlagsOff)
    }

    "render decision text: activation deferred, not approved, default route unchanged" in {
      val rendered = decision.render
      assert(rendered.contains("m21_production_activation=defer_production_activation"))
      assert(rendered.contains("production_activation_approved=false"))
      assert(rendered.contains("default_beauty_search_route=unchanged"))
      assert(rendered.contains("m20_closeout=closed_as_disabled_controlled_surface"))
      assert(rendered.contains("serving_approved=false"))
      assert(rendered.contains("qdrant_production_activation_approved=false"))
      assert(rendered.contains("route_proof_scope=in_memory_only"))
      assert(!rendered.contains("approve_production_activation"))
    }
  }
}
