package leaderboard.search.qdrant

final case class QdrantProductionCandidateSearchEvidence(
  semanticCandidateBackendContractPresent: Boolean,
  semanticCandidateSearchContractPresent: Boolean,
  candidateAssemblyReady: Boolean,
  responseProjectionReady: Boolean,
  beautySearchContractParityReady: Boolean,
)

sealed trait QdrantProductionCandidateSearchDecisionStatus extends Product with Serializable
object QdrantProductionCandidateSearchDecisionStatus {
  case object Ready extends QdrantProductionCandidateSearchDecisionStatus
  case object NotReady extends QdrantProductionCandidateSearchDecisionStatus
}

final case class QdrantProductionCandidateSearchDecision(
  status: QdrantProductionCandidateSearchDecisionStatus,
  blockingReasons: List[String],
)

final case class QdrantProductionCandidateSearchReport(
  evidence: QdrantProductionCandidateSearchEvidence,
  decision: QdrantProductionCandidateSearchDecision,
)

object QdrantProductionCandidateSearchReadiness {
  import QdrantProductionCandidateSearchDecisionStatus.*

  def fromExistingSourceContracts(
    beautySearchContractParityReady: Boolean
  ): QdrantProductionCandidateSearchReport =
    evaluate(QdrantProductionCandidateSearchEvidence(
      semanticCandidateBackendContractPresent = true,
      semanticCandidateSearchContractPresent = true,
      candidateAssemblyReady = true,
      responseProjectionReady = true,
      beautySearchContractParityReady = beautySearchContractParityReady,
    ))

  def evaluate(
    evidence: QdrantProductionCandidateSearchEvidence
  ): QdrantProductionCandidateSearchReport = {
    val blockingReasons = List(
      Option.when(!evidence.semanticCandidateBackendContractPresent)("Semantic candidate backend contract is missing"),
      Option.when(!evidence.semanticCandidateSearchContractPresent)("Semantic candidate search contract is missing"),
      Option.when(!evidence.candidateAssemblyReady)("Candidate assembly readiness is missing"),
      Option.when(!evidence.responseProjectionReady)("Response projection readiness is missing"),
      Option.when(!evidence.beautySearchContractParityReady)("BeautySearch contract parity evidence is missing"),
    ).flatten

    QdrantProductionCandidateSearchReport(
      evidence = evidence,
      decision =
        if (blockingReasons.isEmpty) QdrantProductionCandidateSearchDecision(Ready, Nil)
        else QdrantProductionCandidateSearchDecision(NotReady, blockingReasons),
    )
  }

  def readinessStatus(
    report: Option[QdrantProductionCandidateSearchReport]
  ): QdrantProductionCandidateReadinessStatus =
    report match {
      case None =>
        QdrantProductionCandidateReadinessStatus.Unknown
      case Some(value) =>
        value.decision.status match {
          case Ready =>
            QdrantProductionCandidateReadinessStatus.Ready
          case NotReady =>
            QdrantProductionCandidateReadinessStatus.NotReady(value.decision.blockingReasons)
        }
    }
}
