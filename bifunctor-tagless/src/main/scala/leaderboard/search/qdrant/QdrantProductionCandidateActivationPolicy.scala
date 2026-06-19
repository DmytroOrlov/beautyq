package leaderboard.search.qdrant

sealed trait QdrantProductionCandidateActivationScope extends Product with Serializable
object QdrantProductionCandidateActivationScope {
  case object NoActivation extends QdrantProductionCandidateActivationScope
  case object CandidateReadinessOnly extends QdrantProductionCandidateActivationScope
  case object FutureExplicitOptInRouteOnly extends QdrantProductionCandidateActivationScope
  case object FutureProductionRouteNotApprovedHere extends QdrantProductionCandidateActivationScope
}

sealed trait QdrantProductionCandidateActivationApprovalStatus extends Product with Serializable
object QdrantProductionCandidateActivationApprovalStatus {
  case object NotRequired extends QdrantProductionCandidateActivationApprovalStatus
  case object Approved extends QdrantProductionCandidateActivationApprovalStatus
  case object NotApproved extends QdrantProductionCandidateActivationApprovalStatus
}

sealed trait QdrantProductionCandidateActivationRequirementStatus extends Product with Serializable
object QdrantProductionCandidateActivationRequirementStatus {
  case object Satisfied extends QdrantProductionCandidateActivationRequirementStatus
  case object Missing extends QdrantProductionCandidateActivationRequirementStatus
  case object Unknown extends QdrantProductionCandidateActivationRequirementStatus
}

final case class QdrantProductionCandidateActivationPolicy(
  explicitlyApproved: Boolean,
  scope: QdrantProductionCandidateActivationScope,
  routeServingApproval: QdrantProductionCandidateActivationApprovalStatus,
  rollbackDisableControls: QdrantProductionCandidateActivationRequirementStatus,
  noRegressionEvidence: QdrantProductionCandidateActivationRequirementStatus,
  observability: QdrantProductionCandidateActivationRequirementStatus,
)

sealed trait QdrantProductionCandidateActivationDecisionStatus extends Product with Serializable
object QdrantProductionCandidateActivationDecisionStatus {
  case object Ready extends QdrantProductionCandidateActivationDecisionStatus
  case object NotApproved extends QdrantProductionCandidateActivationDecisionStatus
  case object NotReady extends QdrantProductionCandidateActivationDecisionStatus
}

final case class QdrantProductionCandidateActivationDecision(
  status: QdrantProductionCandidateActivationDecisionStatus,
  blockingReasons: List[String],
)

final case class QdrantProductionCandidateActivationReport(
  policy: QdrantProductionCandidateActivationPolicy,
  decision: QdrantProductionCandidateActivationDecision,
)

object QdrantProductionCandidateActivationPolicy {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationDecisionStatus.*
  import QdrantProductionCandidateActivationRequirementStatus.*
  import QdrantProductionCandidateActivationScope.*

  val conservativeDefault: QdrantProductionCandidateActivationPolicy =
    QdrantProductionCandidateActivationPolicy(
      explicitlyApproved = false,
      scope = NoActivation,
      routeServingApproval = QdrantProductionCandidateActivationApprovalStatus.NotApproved,
      rollbackDisableControls = Missing,
      noRegressionEvidence = Unknown,
      observability = Missing,
    )

  def evaluate(policy: QdrantProductionCandidateActivationPolicy): QdrantProductionCandidateActivationReport = {
    val approvalBlockingReasons = List(
      Option.when(!policy.explicitlyApproved)("Activation is not explicitly approved"),
      policy.scope match {
        case NoActivation =>
          Some("Activation scope is NoActivation")
        case FutureProductionRouteNotApprovedHere =>
          Some("Production route activation requires separate approval outside this policy")
        case CandidateReadinessOnly =>
          None
        case FutureExplicitOptInRouteOnly if policy.routeServingApproval != Approved =>
          Some("Future explicit opt-in route requires separate route/serving approval")
        case FutureExplicitOptInRouteOnly =>
          None
      },
    ).flatten

    val controlBlockingReasons = List(
      requirementReason(
        "Rollback/disable controls",
        policy.rollbackDisableControls,
      ),
      requirementReason(
        "No-regression evidence requirements",
        policy.noRegressionEvidence,
      ),
      requirementReason(
        "Observability controls",
        policy.observability,
      ),
    ).flatten

    val decision =
      if (approvalBlockingReasons.nonEmpty) {
        QdrantProductionCandidateActivationDecision(
          QdrantProductionCandidateActivationDecisionStatus.NotApproved,
          approvalBlockingReasons ++ controlBlockingReasons,
        )
      } else if (controlBlockingReasons.nonEmpty) {
        QdrantProductionCandidateActivationDecision(NotReady, controlBlockingReasons)
      } else {
        QdrantProductionCandidateActivationDecision(Ready, Nil)
      }

    QdrantProductionCandidateActivationReport(policy, decision)
  }

  def readinessStatus(
    policy: Option[QdrantProductionCandidateActivationPolicy]
  ): QdrantProductionCandidateReadinessStatus =
    policy match {
      case None =>
        QdrantProductionCandidateReadinessStatus.NotApproved
      case Some(value) =>
        evaluate(value).decision match {
          case QdrantProductionCandidateActivationDecision(Ready, _) =>
            QdrantProductionCandidateReadinessStatus.Ready
          case QdrantProductionCandidateActivationDecision(QdrantProductionCandidateActivationDecisionStatus.NotApproved, _) =>
            QdrantProductionCandidateReadinessStatus.NotApproved
          case QdrantProductionCandidateActivationDecision(NotReady, reasons) =>
            QdrantProductionCandidateReadinessStatus.NotReady(reasons)
        }
    }

  private def requirementReason(
    label: String,
    status: QdrantProductionCandidateActivationRequirementStatus,
  ): Option[String] =
    status match {
      case Satisfied => None
      case Missing   => Some(s"$label are missing")
      case Unknown   => Some(s"$label status is unknown")
    }
}
