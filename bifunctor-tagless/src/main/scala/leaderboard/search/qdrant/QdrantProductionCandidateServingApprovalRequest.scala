package leaderboard.search.qdrant

sealed trait QdrantProductionCandidateServingApprovalRequestTargetScope extends Product with Serializable
object QdrantProductionCandidateServingApprovalRequestTargetScope {
  case object FutureExplicitOptInRouteRequest extends QdrantProductionCandidateServingApprovalRequestTargetScope
  case object ProductionRouteActivationRequest extends QdrantProductionCandidateServingApprovalRequestTargetScope
  case object HybridServingRequest extends QdrantProductionCandidateServingApprovalRequestTargetScope
}

final case class QdrantProductionCandidateServingApprovalRequestEvidence(
  m7CloseoutEvidencePresent: Boolean,
  offlineEvalNoRegressionEvidencePresent: Boolean,
  postM7NoServingGuardrailPresent: Boolean,
  routeBoundaryEvidencePresent: Boolean,
  configNoRegressionGateEvidencePresent: Boolean,
  explicitServingApproval: QdrantProductionCandidateActivationApprovalStatus,
  targetScope: QdrantProductionCandidateServingApprovalRequestTargetScope,
)

sealed trait QdrantProductionCandidateServingApprovalRequestStatus extends Product with Serializable
object QdrantProductionCandidateServingApprovalRequestStatus {
  case object ReadyToRequestExplicitApproval extends QdrantProductionCandidateServingApprovalRequestStatus
  case object Blocked extends QdrantProductionCandidateServingApprovalRequestStatus
}

final case class QdrantProductionCandidateServingApprovalRequestReport(
  evidence: QdrantProductionCandidateServingApprovalRequestEvidence,
  status: QdrantProductionCandidateServingApprovalRequestStatus,
  blockingReasons: List[String],
)

object QdrantProductionCandidateServingApprovalRequest {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateServingApprovalRequestStatus.*
  import QdrantProductionCandidateServingApprovalRequestTargetScope.*

  def evaluate(
    evidence: QdrantProductionCandidateServingApprovalRequestEvidence
  ): QdrantProductionCandidateServingApprovalRequestReport = {
    val blockingReasons = List(
      Option.when(!evidence.m7CloseoutEvidencePresent)("M7 closeout evidence is missing"),
      Option.when(!evidence.offlineEvalNoRegressionEvidencePresent)(
        "Offline eval/no-regression evidence is missing"
      ),
      Option.when(!evidence.postM7NoServingGuardrailPresent)("Post-M7 no-serving guardrail is missing"),
      Option.when(!evidence.routeBoundaryEvidencePresent)("Route-boundary evidence is missing"),
      Option.when(!evidence.configNoRegressionGateEvidencePresent)(
        "Config/no-regression gate evidence is missing"
      ),
      approvalReason(evidence.explicitServingApproval),
      targetScopeReason(evidence.targetScope),
    ).flatten

    QdrantProductionCandidateServingApprovalRequestReport(
      evidence = evidence,
      status = if (blockingReasons.isEmpty) ReadyToRequestExplicitApproval else Blocked,
      blockingReasons = blockingReasons,
    )
  }

  private def approvalReason(
    approval: QdrantProductionCandidateActivationApprovalStatus
  ): Option[String] =
    approval match {
      case NotApproved =>
        None
      case NotRequired =>
        Some("Explicit serving approval is not represented as absent")
      case Approved =>
        Some("Explicit serving approval is already approved outside this request-readiness model")
    }

  private def targetScopeReason(
    targetScope: QdrantProductionCandidateServingApprovalRequestTargetScope
  ): Option[String] =
    targetScope match {
      case FutureExplicitOptInRouteRequest =>
        None
      case ProductionRouteActivationRequest =>
        Some("Target scope is not limited to a future explicit opt-in route request")
      case HybridServingRequest =>
        Some("Target scope is not limited to a future explicit opt-in route request")
    }
}
