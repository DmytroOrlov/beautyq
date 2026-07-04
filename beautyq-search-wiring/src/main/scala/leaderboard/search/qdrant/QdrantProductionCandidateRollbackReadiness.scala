package leaderboard.search.qdrant

final case class QdrantProductionCandidateRollbackEvidence(
  disableControlDocumentedOrConfigured: Boolean,
  rollbackPathDocumentedOrConfigured: Boolean,
  noRegressionEvidenceAvailable: Boolean,
)

sealed trait QdrantProductionCandidateRollbackDecisionStatus extends Product with Serializable
object QdrantProductionCandidateRollbackDecisionStatus {
  case object Ready extends QdrantProductionCandidateRollbackDecisionStatus
  case object NotReady extends QdrantProductionCandidateRollbackDecisionStatus
}

final case class QdrantProductionCandidateRollbackDecision(
  status: QdrantProductionCandidateRollbackDecisionStatus,
  blockingReasons: List[String],
  productionRouteActivationApproved: Boolean,
)

final case class QdrantProductionCandidateRollbackReport(
  evidence: QdrantProductionCandidateRollbackEvidence,
  decision: QdrantProductionCandidateRollbackDecision,
)

object QdrantProductionCandidateRollbackReadiness {
  import QdrantProductionCandidateRollbackDecisionStatus.*

  def evaluate(
    evidence: QdrantProductionCandidateRollbackEvidence
  ): QdrantProductionCandidateRollbackReport = {
    val blockingReasons = List(
      Option.when(!evidence.disableControlDocumentedOrConfigured)("Disable control is missing"),
      Option.when(!evidence.rollbackPathDocumentedOrConfigured)("Rollback path is missing"),
      Option.when(!evidence.noRegressionEvidenceAvailable)("No-regression evidence is missing"),
    ).flatten

    QdrantProductionCandidateRollbackReport(
      evidence = evidence,
      decision =
        if (blockingReasons.isEmpty) {
          QdrantProductionCandidateRollbackDecision(
            status = Ready,
            blockingReasons = Nil,
            productionRouteActivationApproved = false,
          )
        } else {
          QdrantProductionCandidateRollbackDecision(
            status = NotReady,
            blockingReasons = blockingReasons,
            productionRouteActivationApproved = false,
          )
        },
    )
  }

  def readinessStatus(
    report: Option[QdrantProductionCandidateRollbackReport]
  ): QdrantProductionCandidateReadinessStatus =
    report match {
      case None =>
        QdrantProductionCandidateReadinessStatus.NotConfigured
      case Some(value) =>
        value.decision.status match {
          case Ready =>
            QdrantProductionCandidateReadinessStatus.Ready
          case NotReady =>
            QdrantProductionCandidateReadinessStatus.NotReady(value.decision.blockingReasons)
        }
    }
}
