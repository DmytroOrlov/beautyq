package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateActivationConfigApproval,
  QdrantProductionCandidateActivationConfigApprovalReport,
  QdrantProductionCandidateActivationConfigApprovalStatus,
  QdrantProductionCandidateActivationConfigGate,
  QdrantProductionCandidateActivationDecisionStatus,
  QdrantProductionCandidateActivationPlanning,
  QdrantProductionCandidateActivationPlanningDecision,
  QdrantProductionCandidateActivationPlanningStatus,
  QdrantProductionCandidateActivationPolicy,
  QdrantProductionCandidateActivationPrerequisites,
  QdrantProductionCandidateActivationReport,
  QdrantProductionCandidateActivationRequirementStatus,
  QdrantProductionCandidateActivationScope,
  QdrantProductionCandidateActivationTargetScope,
  QdrantProductionCandidateNoRegressionApproval,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessReport,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateM7CloseoutSpec extends AnyWordSpec {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationConfigApprovalStatus.{
    Blocked => ConfigBlocked,
    ReadyForPlanning,
  }
  import QdrantProductionCandidateActivationConfigGate.*
  import QdrantProductionCandidateActivationPlanningStatus.{
    Blocked => PlanningBlocked,
    ReadyForSeparateImplementationDecision,
  }
  import QdrantProductionCandidateActivationRequirementStatus.*
  import QdrantProductionCandidateActivationTargetScope.*
  import QdrantProductionCandidateReadinessStatus.Ready

  "M7 Qdrant activation planning and source-confirmation foundation" should {
    "aggregate the accepted pure prerequisites without approving serving" in {
      val evidence = closeoutEvidence

      assert(evidence.m6ReadinessReport.productionCandidateReady)
      assert(evidence.activationPolicyReport.decision.status == QdrantProductionCandidateActivationDecisionStatus.Ready)

      assert(evidence.conservativeConfigReport.config.configGate == Disabled)
      assert(evidence.conservativeConfigReport.planningConfigGate == Missing)
      assert(evidence.conservativeConfigReport.planningNoRegressionEvidence == Unknown)
      assert(evidence.conservativeConfigReport.decision.status == ConfigBlocked)

      assert(evidence.approvedConfigReport.config.configGate == Enabled)
      assert(evidence.approvedConfigReport.planningConfigGate == Satisfied)
      assert(evidence.approvedConfigReport.planningNoRegressionEvidence == Satisfied)
      assert(evidence.approvedConfigReport.decision.status == ReadyForPlanning)

      assert(evidence.explicitOptInDecision.targetScope == ExplicitOptInRoute)
      assert(evidence.explicitOptInDecision.status == ReadyForSeparateImplementationDecision)
      assert(evidence.explicitOptInDecision.blockingReasons.isEmpty)
    }

    "keep production activation unapproved and hybrid serving future-only" in {
      val evidence = closeoutEvidence

      assert(evidence.productionRouteDecision.targetScope == ProductionRouteActivation)
      assert(evidence.productionRouteDecision.status == PlanningBlocked)
      assert(evidence.productionRouteDecision.blockingReasons == List(
        "Separate route/serving approval is not approved"
      ))

      assert(evidence.hybridServingDecision.targetScope == HybridServing)
      assert(evidence.hybridServingDecision.status == PlanningBlocked)
      assert(evidence.hybridServingDecision.blockingReasons == List(
        "Separate route/serving approval is required"
      ))
    }

    "retain the pending opt-in and active ES route contracts as separate source evidence" in {
      assert(closeoutEvidence.routeBoundarySpecs == Set(
        "BeautySearchProductionRouteExposureSpec",
        "BeautySearchElasticsearchRouteModuleSpec",
        "BeautySearchOptInRouteModuleSpec",
        "BeautySearchOptInHttpApiModuleSpec",
      ))
    }
  }

  private final case class M7CloseoutEvidence(
    m6ReadinessReport: QdrantProductionCandidateReadinessReport,
    activationPolicyReport: QdrantProductionCandidateActivationReport,
    conservativeConfigReport: QdrantProductionCandidateActivationConfigApprovalReport,
    approvedConfigReport: QdrantProductionCandidateActivationConfigApprovalReport,
    explicitOptInDecision: QdrantProductionCandidateActivationPlanningDecision,
    productionRouteDecision: QdrantProductionCandidateActivationPlanningDecision,
    hybridServingDecision: QdrantProductionCandidateActivationPlanningDecision,
    routeBoundarySpecs: Set[String],
  )

  private lazy val closeoutEvidence: M7CloseoutEvidence = {
    val m6ReadinessReport = QdrantProductionCandidateReadiness.evaluate(allReadyState)
    val activationPolicyReport = QdrantProductionCandidateActivationPolicy.evaluate(explicitOptInPolicy)
    val conservativeConfigReport =
      QdrantProductionCandidateActivationConfigApproval.evaluate(
        QdrantProductionCandidateActivationConfigApproval.conservativeDefault
      )
    val approvedConfigReport =
      QdrantProductionCandidateActivationConfigApproval.evaluate(approvedConfig)
    val approvedOptInPrerequisites =
      QdrantProductionCandidateActivationConfigApproval.applyToPlanningPrerequisites(
        approvedConfigReport,
        servingPrerequisites(m6ReadinessReport, activationPolicyReport, Approved),
      )

    M7CloseoutEvidence(
      m6ReadinessReport = m6ReadinessReport,
      activationPolicyReport = activationPolicyReport,
      conservativeConfigReport = conservativeConfigReport,
      approvedConfigReport = approvedConfigReport,
      explicitOptInDecision =
        QdrantProductionCandidateActivationPlanning.evaluate(
          ExplicitOptInRoute,
          approvedOptInPrerequisites,
        ),
      productionRouteDecision =
        QdrantProductionCandidateActivationPlanning.evaluate(
          ProductionRouteActivation,
          approvedOptInPrerequisites.copy(servingApproval = NotApproved),
        ),
      hybridServingDecision =
        QdrantProductionCandidateActivationPlanning.evaluate(
          HybridServing,
          approvedOptInPrerequisites.copy(servingApproval = NotRequired),
        ),
      routeBoundarySpecs = Set(
        classOf[BeautySearchProductionRouteExposureSpec].getSimpleName,
        classOf[BeautySearchElasticsearchRouteModuleSpec].getSimpleName,
        classOf[BeautySearchOptInRouteModuleSpec].getSimpleName,
        classOf[BeautySearchOptInHttpApiModuleSpec].getSimpleName,
      ),
    )
  }

  private val allReadyState =
    QdrantProductionCandidateReadinessState(
      qdrantActive = true,
      collectionIdentity = Ready,
      contractParity = Ready,
      indexing = Ready,
      search = Ready,
      qualityEval = Ready,
      observability = Ready,
      rollbackDisable = Ready,
      activationPolicy = Ready,
    )

  private val explicitOptInPolicy =
    QdrantProductionCandidateActivationPolicy(
      explicitlyApproved = true,
      scope = QdrantProductionCandidateActivationScope.FutureExplicitOptInRouteOnly,
      routeServingApproval = Approved,
      rollbackDisableControls = Satisfied,
      noRegressionEvidence = Satisfied,
      observability = Satisfied,
    )

  private val approvedConfig =
    QdrantProductionCandidateActivationConfigApproval(
      configGate = Enabled,
      noRegression = QdrantProductionCandidateNoRegressionApproval(
        evidence = Satisfied,
        approval = Approved,
      ),
    )

  private def servingPrerequisites(
    m6ReadinessReport: QdrantProductionCandidateReadinessReport,
    activationPolicyReport: QdrantProductionCandidateActivationReport,
    servingApproval: QdrantProductionCandidateActivationApprovalStatus,
  ): QdrantProductionCandidateActivationPrerequisites =
    QdrantProductionCandidateActivationPrerequisites(
      m6ReadinessReport = Some(m6ReadinessReport),
      activationPolicyReport = Some(activationPolicyReport),
      configGate = Missing,
      noRegressionEvidence = Unknown,
      observabilityStatus = Satisfied,
      rollbackDisableControl = Satisfied,
      servingApproval = servingApproval,
    )
}
