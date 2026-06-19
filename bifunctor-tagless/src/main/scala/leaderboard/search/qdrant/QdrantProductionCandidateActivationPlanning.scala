package leaderboard.search.qdrant

sealed trait QdrantProductionCandidateActivationTargetScope extends Product with Serializable
object QdrantProductionCandidateActivationTargetScope {
  case object CandidateReadinessOnly extends QdrantProductionCandidateActivationTargetScope
  case object ExplicitOptInRoute extends QdrantProductionCandidateActivationTargetScope
  case object ProductionRouteActivation extends QdrantProductionCandidateActivationTargetScope
  case object HybridServing extends QdrantProductionCandidateActivationTargetScope
}

final case class QdrantProductionCandidateActivationPrerequisites(
  m6ReadinessReport: Option[QdrantProductionCandidateReadinessReport],
  activationPolicyReport: Option[QdrantProductionCandidateActivationReport],
  configGate: QdrantProductionCandidateActivationRequirementStatus,
  noRegressionEvidence: QdrantProductionCandidateActivationRequirementStatus,
  observabilityStatus: QdrantProductionCandidateActivationRequirementStatus,
  rollbackDisableControl: QdrantProductionCandidateActivationRequirementStatus,
  servingApproval: QdrantProductionCandidateActivationApprovalStatus,
)

sealed trait QdrantProductionCandidateActivationPlanningStatus extends Product with Serializable
object QdrantProductionCandidateActivationPlanningStatus {
  case object ReadyForSeparateImplementationDecision extends QdrantProductionCandidateActivationPlanningStatus
  case object Blocked extends QdrantProductionCandidateActivationPlanningStatus
}

final case class QdrantProductionCandidateActivationPlanningDecision(
  targetScope: QdrantProductionCandidateActivationTargetScope,
  status: QdrantProductionCandidateActivationPlanningStatus,
  blockingReasons: List[String],
)

object QdrantProductionCandidateActivationPlanning {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationDecisionStatus.Ready
  import QdrantProductionCandidateActivationPlanningStatus.*
  import QdrantProductionCandidateActivationRequirementStatus.*
  import QdrantProductionCandidateActivationTargetScope.*

  def evaluate(
    targetScope: QdrantProductionCandidateActivationTargetScope,
    prerequisites: QdrantProductionCandidateActivationPrerequisites,
  ): QdrantProductionCandidateActivationPlanningDecision = {
    val readinessReasons = List(
      Option.when(!prerequisites.m6ReadinessReport.exists(_.productionCandidateReady))(
        "M6 production-candidate readiness report is not ready"
      ),
      Option.when(!prerequisites.activationPolicyReport.exists(_.decision.status == Ready))(
        "Activation policy decision is not Ready"
      ),
    ).flatten

    val servingReasons =
      targetScope match {
        case CandidateReadinessOnly =>
          Nil
        case ExplicitOptInRoute | ProductionRouteActivation | HybridServing =>
          List(
            requirementReason("Config gate", prerequisites.configGate),
            requirementReason("No-regression evidence", prerequisites.noRegressionEvidence),
            requirementReason("Observability/status evidence", prerequisites.observabilityStatus),
            requirementReason("Rollback/disable control", prerequisites.rollbackDisableControl),
            approvalReason(prerequisites.servingApproval),
          ).flatten
      }

    val blockingReasons = readinessReasons ++ servingReasons

    QdrantProductionCandidateActivationPlanningDecision(
      targetScope = targetScope,
      status = if (blockingReasons.isEmpty) ReadyForSeparateImplementationDecision else Blocked,
      blockingReasons = blockingReasons,
    )
  }

  private def requirementReason(
    label: String,
    status: QdrantProductionCandidateActivationRequirementStatus,
  ): Option[String] =
    status match {
      case Satisfied => None
      case Missing   => Some(s"$label is missing")
      case Unknown   => Some(s"$label status is unknown")
    }

  private def approvalReason(
    status: QdrantProductionCandidateActivationApprovalStatus
  ): Option[String] =
    status match {
      case Approved    => None
      case NotApproved => Some("Separate route/serving approval is not approved")
      case NotRequired => Some("Separate route/serving approval is required")
    }
}
