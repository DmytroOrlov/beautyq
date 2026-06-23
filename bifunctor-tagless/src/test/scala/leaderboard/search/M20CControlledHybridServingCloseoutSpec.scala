package leaderboard.search

import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.ComponentCombinationPolicy
import leaderboard.search.eval.M20ControlledHybridServingSkeleton.M20HybridServingControl
import leaderboard.search.eval.M20BControlledHybridServingOperationalControl.{
  KillSwitchState,
  M20BOperationalControl,
  ModuleProofScope,
  ReadinessInputs,
}
import leaderboard.search.eval.M20CControlledHybridServingCloseout.{CloseoutEvidence, CloseoutStatus}
import org.scalatest.wordspec.AnyWordSpec

final class M20CControlledHybridServingCloseoutSpec extends AnyWordSpec {

  // Offline/eval-only policy evidence carrying the M19I honesty flags. M20C reads this strictly as
  // evidence (through M20B -> M20A); it never treats it as an execution approval.
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

  private def defaultControl: M20BOperationalControl =
    M20BOperationalControl.disabledByDefault(skeleton)

  private def closeout: CloseoutEvidence =
    CloseoutEvidence.of(defaultControl)

  "M20C controlled hybrid serving closeout (pure evidence model)" should {

    "close M20 only as a disabled controlled surface" in {
      assert(closeout.closeoutStatus == CloseoutStatus.ClosedAsDisabledControlledSurface)
      assert(closeout.closeoutStatus.closed)
      assert(closeout.closeoutStatus.render == "closed_as_disabled_controlled_surface")
      assert(closeout.allSafetyEvidenceHolds)
      assert(closeout.missingEvidence.isEmpty)
    }

    "keep serving approval false and Qdrant production activation false" in {
      assert(!closeout.servingApproved)
      assert(!closeout.qdrantProductionActivationApproved)
      assert(!closeout.report.servingApproved)
      assert(!closeout.report.qdrantProductionActivationApproved)
    }

    "tie M20A and M20B together as evidence: skeleton disabled, control not serving" in {
      assert(closeout.skeletonDisabledByDefault)
      assert(closeout.operationalControlNotServing)
    }

    "represent readiness, kill-switch, rollback, and operator visibility safety evidence" in {
      assert(closeout.readinessCannotEnableServing)
      assert(closeout.killSwitchDisablesServing)
      assert(closeout.rollbackTargetSourceConfirmed)
      assert(closeout.operatorStatusExposesBlockersAndSafetyFlags)
    }

    "present default-route-unchanged evidence" in {
      assert(closeout.defaultBeautySearchRouteUnchanged)
      assert(closeout.report.defaultBeautySearchRouteUnchanged)
    }

    "enable no fallback, fusion, reranking, automatic Qdrant supplement, or route switch" in {
      assert(!closeout.fallbackEnabled)
      assert(!closeout.scoreFusionEnabled)
      assert(!closeout.rerankingEnabled)
      assert(!closeout.automaticQdrantSupplementEnabled)
      assert(!closeout.routeSwitchEnabled)
      assert(closeout.claimsNoProductionActivation)
    }

    "consume the M19I/M20A/M20B policy and control data as evidence only" in {
      assert(closeout.m19IPolicyConsumedAsEvidenceOnly)
      assert(closeout.report.m19IPolicyConsumedAsEvidenceOnly)
      assert(!closeout.servingApproved)
    }

    "record an honest in-memory-only proof scope, never overstated to full production graph" in {
      assert(closeout.moduleProofScope == ModuleProofScope.InMemoryOnly)
      assert(closeout.moduleProofScopeInMemoryOnly)
      assert(!closeout.moduleProofScopeOverstated)
      assert(closeout.report.moduleProofScope == "in_memory_only")
      assert(!closeout.report.moduleProofScopeOverstated)
    }

    "refuse to close and flag the overstatement if the proof scope is renamed to full production graph" in {
      val overstated =
        CloseoutEvidence.of(defaultControl.copy(moduleProofScope = ModuleProofScope.FullProductionGraph))
      assert(overstated.moduleProofScopeOverstated)
      assert(!overstated.allSafetyEvidenceHolds)
      assert(overstated.closeoutStatus == CloseoutStatus.IncompleteNeedsMoreEvidence)
      assert(overstated.missingEvidence.contains("module_proof_scope_overstated"))
    }

    "remain incomplete if any M19I honesty flag is stripped from the evidence policy" in {
      val nonEvidenceSkeleton =
        M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy.copy(offlineEvalOnly = false))
      val nonEvidenceCloseout =
        CloseoutEvidence.of(M20BOperationalControl.disabledByDefault(nonEvidenceSkeleton))
      assert(!nonEvidenceCloseout.m19IPolicyConsumedAsEvidenceOnly)
      assert(nonEvidenceCloseout.closeoutStatus == CloseoutStatus.IncompleteNeedsMoreEvidence)
      assert(nonEvidenceCloseout.missingEvidence.contains("m19i_policy_not_evidence_only"))
    }

    "prove the kill switch still forces serving disabled even with full readiness" in {
      // The closeout's kill-switch evidence reads a kill-switched, fully-ready copy of the control.
      val killed =
        defaultControl.copy(
          readiness = ReadinessInputs.allPositive,
          killSwitch = KillSwitchState.Engaged,
        )
      assert(killed.effectiveServingDisabled)
      assert(killed.blockers.contains("kill_switch_engaged"))
      assert(closeout.killSwitchDisablesServing)
    }

    "render closeout text that says M20 is not production activation and the default route is unchanged" in {
      val rendered = closeout.render
      assert(rendered.contains("m20_closeout=closed_as_disabled_controlled_surface"))
      assert(rendered.contains("m20_is_production_activation=false"))
      assert(rendered.contains("default_beauty_search_route=unchanged"))
      assert(rendered.contains("module_proof_scope=in_memory_only"))
      assert(rendered.contains("serving_approved=false"))
      assert(rendered.contains("qdrant_production_activation_approved=false"))
    }
  }
}
