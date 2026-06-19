package leaderboard.search.qdrant

final case class QdrantProductionCandidateObservabilityEvidence(
  readinessStatusReportAvailable: Boolean,
  qualityEvalReportAvailable: Boolean,
  activationDecisionReportAvailable: Boolean,
)

sealed trait QdrantProductionCandidateObservabilityDecisionStatus extends Product with Serializable
object QdrantProductionCandidateObservabilityDecisionStatus {
  case object Ready extends QdrantProductionCandidateObservabilityDecisionStatus
  case object NotReady extends QdrantProductionCandidateObservabilityDecisionStatus
}

final case class QdrantProductionCandidateObservabilityDecision(
  status: QdrantProductionCandidateObservabilityDecisionStatus,
  blockingReasons: List[String],
)

final case class QdrantProductionCandidateObservabilityReport(
  evidence: QdrantProductionCandidateObservabilityEvidence,
  decision: QdrantProductionCandidateObservabilityDecision,
)

object QdrantProductionCandidateObservabilityReadiness {
  import QdrantProductionCandidateObservabilityDecisionStatus.*

  def evaluate(
    evidence: QdrantProductionCandidateObservabilityEvidence
  ): QdrantProductionCandidateObservabilityReport = {
    val blockingReasons = List(
      Option.when(!evidence.readinessStatusReportAvailable)("Readiness/status report is missing"),
      Option.when(!evidence.qualityEvalReportAvailable)("Quality/eval report is missing"),
      Option.when(!evidence.activationDecisionReportAvailable)("Activation decision report is missing"),
    ).flatten

    QdrantProductionCandidateObservabilityReport(
      evidence = evidence,
      decision =
        if (blockingReasons.isEmpty) QdrantProductionCandidateObservabilityDecision(Ready, Nil)
        else QdrantProductionCandidateObservabilityDecision(NotReady, blockingReasons),
    )
  }

  def readinessStatus(
    report: Option[QdrantProductionCandidateObservabilityReport]
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
