package leaderboard.search.qdrant

sealed trait QdrantProductionCandidateActivationConfigGate extends Product with Serializable
object QdrantProductionCandidateActivationConfigGate {
  case object Disabled extends QdrantProductionCandidateActivationConfigGate
  case object Enabled extends QdrantProductionCandidateActivationConfigGate
}

final case class QdrantProductionCandidateNoRegressionApproval(
  evidence: QdrantProductionCandidateActivationRequirementStatus,
  approval: QdrantProductionCandidateActivationApprovalStatus,
)

final case class QdrantProductionCandidateActivationConfigApproval(
  configGate: QdrantProductionCandidateActivationConfigGate,
  noRegression: QdrantProductionCandidateNoRegressionApproval,
)

sealed trait QdrantProductionCandidateActivationConfigApprovalStatus extends Product with Serializable
object QdrantProductionCandidateActivationConfigApprovalStatus {
  case object ReadyForPlanning extends QdrantProductionCandidateActivationConfigApprovalStatus
  case object Blocked extends QdrantProductionCandidateActivationConfigApprovalStatus
}

final case class QdrantProductionCandidateActivationConfigApprovalDecision(
  status: QdrantProductionCandidateActivationConfigApprovalStatus,
  blockingReasons: List[String],
)

final case class QdrantProductionCandidateActivationConfigApprovalReport(
  config: QdrantProductionCandidateActivationConfigApproval,
  planningConfigGate: QdrantProductionCandidateActivationRequirementStatus,
  planningNoRegressionEvidence: QdrantProductionCandidateActivationRequirementStatus,
  decision: QdrantProductionCandidateActivationConfigApprovalDecision,
)

object QdrantProductionCandidateActivationConfigApproval {
  import QdrantProductionCandidateActivationApprovalStatus.*
  import QdrantProductionCandidateActivationConfigApprovalStatus.*
  import QdrantProductionCandidateActivationConfigGate.*
  import QdrantProductionCandidateActivationRequirementStatus.*
  import QdrantProductionCandidateQualityDecisionStatus.*

  val conservativeDefault: QdrantProductionCandidateActivationConfigApproval =
    QdrantProductionCandidateActivationConfigApproval(
      configGate = Disabled,
      noRegression = QdrantProductionCandidateNoRegressionApproval(
        evidence = Unknown,
        approval = NotApproved,
      ),
    )

  def evaluate(
    config: QdrantProductionCandidateActivationConfigApproval
  ): QdrantProductionCandidateActivationConfigApprovalReport = {
    val planningConfigGate =
      config.configGate match {
        case Disabled => Missing
        case Enabled  => Satisfied
      }

    val planningNoRegressionEvidence =
      config.noRegression.evidence match {
        case Unknown =>
          Unknown
        case Missing =>
          Missing
        case Satisfied if config.noRegression.approval == Approved =>
          Satisfied
        case Satisfied =>
          Missing
      }

    val blockingReasons = List(
      Option.when(config.configGate == Disabled)("Qdrant activation config gate is disabled"),
      requirementReason(config.noRegression.evidence),
      approvalReason(config.noRegression.approval),
    ).flatten

    QdrantProductionCandidateActivationConfigApprovalReport(
      config = config,
      planningConfigGate = planningConfigGate,
      planningNoRegressionEvidence = planningNoRegressionEvidence,
      decision = QdrantProductionCandidateActivationConfigApprovalDecision(
        status = if (blockingReasons.isEmpty) ReadyForPlanning else Blocked,
        blockingReasons = blockingReasons,
      ),
    )
  }

  def noRegressionEvidenceFromQuality(
    report: Option[QdrantProductionCandidateQualityReport]
  ): QdrantProductionCandidateActivationRequirementStatus =
    report match {
      case None =>
        Unknown
      case Some(value) =>
        value.decision.status match {
          case Passed      => Satisfied
          case Failed      => Missing
          case Unevaluated => Unknown
          case Incomplete  => Unknown
        }
    }

  def applyToPlanningPrerequisites(
    report: QdrantProductionCandidateActivationConfigApprovalReport,
    prerequisites: QdrantProductionCandidateActivationPrerequisites,
  ): QdrantProductionCandidateActivationPrerequisites =
    prerequisites.copy(
      configGate = report.planningConfigGate,
      noRegressionEvidence = report.planningNoRegressionEvidence,
    )

  private def requirementReason(
    status: QdrantProductionCandidateActivationRequirementStatus
  ): Option[String] =
    status match {
      case Satisfied => None
      case Missing   => Some("No-regression evidence is missing")
      case Unknown   => Some("No-regression evidence status is unknown")
    }

  private def approvalReason(
    status: QdrantProductionCandidateActivationApprovalStatus
  ): Option[String] =
    status match {
      case Approved    => None
      case NotApproved => Some("No-regression evidence is not approved")
      case NotRequired => Some("No-regression evidence approval is required")
    }
}
