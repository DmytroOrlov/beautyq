package leaderboard.search.qdrant

import leaderboard.model.QueryFailure

final case class QdrantExplicitOptInRoutePrerequisites(
  readinessReport: QdrantProductionCandidateReadinessReport,
  activationReport: QdrantProductionCandidateActivationReport,
  configApprovalReport: QdrantProductionCandidateActivationConfigApprovalReport,
  planningDecision: QdrantProductionCandidateActivationPlanningDecision,
)

object QdrantExplicitOptInRoutePrerequisites {
  import QdrantProductionCandidateActivationApprovalStatus.Approved
  import QdrantProductionCandidateActivationConfigApprovalStatus.ReadyForPlanning
  import QdrantProductionCandidateActivationConfigGate.Enabled
  import QdrantProductionCandidateActivationDecisionStatus.Ready
  import QdrantProductionCandidateActivationPlanningStatus.ReadyForSeparateImplementationDecision
  import QdrantProductionCandidateActivationRequirementStatus.Satisfied
  import QdrantProductionCandidateActivationScope.FutureExplicitOptInRouteOnly
  import QdrantProductionCandidateActivationTargetScope.ExplicitOptInRoute

  def fromReports(
    readinessReport: QdrantProductionCandidateReadinessReport,
    activationReport: QdrantProductionCandidateActivationReport,
    configApprovalReport: QdrantProductionCandidateActivationConfigApprovalReport,
  ): Either[QueryFailure, QdrantExplicitOptInRoutePrerequisites] = {
    val planningPrerequisites =
      QdrantProductionCandidateActivationConfigApproval.applyToPlanningPrerequisites(
        configApprovalReport,
        QdrantProductionCandidateActivationPrerequisites(
          m6ReadinessReport = Some(readinessReport),
          activationPolicyReport = Some(activationReport),
          configGate = Satisfied,
          noRegressionEvidence = Satisfied,
          observabilityStatus = activationReport.policy.observability,
          rollbackDisableControl = activationReport.policy.rollbackDisableControls,
          servingApproval = activationReport.policy.routeServingApproval,
        ),
      )
    val planningDecision =
      QdrantProductionCandidateActivationPlanning.evaluate(
        ExplicitOptInRoute,
        planningPrerequisites,
      )

    validate(readinessReport, activationReport, configApprovalReport, planningDecision).map { _ =>
      QdrantExplicitOptInRoutePrerequisites(
        readinessReport = readinessReport,
        activationReport = activationReport,
        configApprovalReport = configApprovalReport,
        planningDecision = planningDecision,
      )
    }
  }

  private def validate(
    readinessReport: QdrantProductionCandidateReadinessReport,
    activationReport: QdrantProductionCandidateActivationReport,
    configApprovalReport: QdrantProductionCandidateActivationConfigApprovalReport,
    planningDecision: QdrantProductionCandidateActivationPlanningDecision,
  ): Either[QueryFailure, Unit] = {
    val blockingReasons = List(
      Option.when(!readinessReport.productionCandidateReady)("M6 production-candidate readiness report is not ready"),
      Option.when(activationReport.decision.status != Ready)("Activation policy decision is not Ready"),
      Option.when(!activationReport.policy.explicitlyApproved)("Activation is not explicitly approved"),
      Option.when(activationReport.policy.scope != FutureExplicitOptInRouteOnly)(
        "Activation scope is not limited to the explicit opt-in route"
      ),
      Option.when(activationReport.policy.routeServingApproval != Approved)(
        "Separate route/serving approval is not approved"
      ),
      Option.when(activationReport.policy.observability != Satisfied)("Observability controls are not satisfied"),
      Option.when(activationReport.policy.rollbackDisableControls != Satisfied)("Rollback/disable controls are not satisfied"),
      Option.when(activationReport.policy.noRegressionEvidence != Satisfied)("No-regression evidence is not satisfied"),
      Option.when(configApprovalReport.config.configGate != Enabled)("Qdrant activation config gate is disabled"),
      Option.when(configApprovalReport.decision.status != ReadyForPlanning)("Qdrant activation config approval is not ready"),
      Option.when(configApprovalReport.planningConfigGate != Satisfied)("Config gate is missing"),
      Option.when(configApprovalReport.planningNoRegressionEvidence != Satisfied)(
        "No-regression evidence is not satisfied by config approval"
      ),
      Option.when(planningDecision.targetScope != ExplicitOptInRoute)("Planning target is not the explicit opt-in route"),
      Option.when(planningDecision.status != ReadyForSeparateImplementationDecision)(
        "Explicit opt-in route planning decision is not ready"
      ),
    ).flatten ++ planningDecision.blockingReasons

    if (blockingReasons.isEmpty) {
      Right(())
    } else {
      Left(QueryFailure.domain(s"Qdrant explicit opt-in route prerequisites are not satisfied: ${blockingReasons.mkString("; ")}"))
    }
  }
}
