package leaderboard.search.qdrant

final case class QdrantProductionCandidateIndexingEvidence(
  expectedDocumentCount: Int,
  preparedDocumentCount: Int,
  indexedDocumentCount: Option[Int],
  collectionIdentityReadiness: QdrantProductionCandidateReadinessStatus,
  embeddingVectorReady: Boolean,
)

sealed trait QdrantProductionCandidateIndexingDecisionStatus extends Product with Serializable
object QdrantProductionCandidateIndexingDecisionStatus {
  case object Ready extends QdrantProductionCandidateIndexingDecisionStatus
  case object NotReady extends QdrantProductionCandidateIndexingDecisionStatus
}

final case class QdrantProductionCandidateIndexingDecision(
  status: QdrantProductionCandidateIndexingDecisionStatus,
  blockingReasons: List[String],
)

final case class QdrantProductionCandidateIndexingReport(
  evidence: QdrantProductionCandidateIndexingEvidence,
  decision: QdrantProductionCandidateIndexingDecision,
)

object QdrantProductionCandidateIndexingReadiness {
  import QdrantProductionCandidateReadinessStatus.*

  def fromSnapshotIndexingResult(
    expectedDocumentCount: Int,
    result: QdrantSnapshotIndexingResult,
    collectionIdentityReadiness: QdrantProductionCandidateReadinessStatus,
    embeddingVectorReady: Boolean,
  ): QdrantProductionCandidateIndexingReport =
    evaluate(QdrantProductionCandidateIndexingEvidence(
      expectedDocumentCount = expectedDocumentCount,
      preparedDocumentCount = result.totalDocumentsLoaded,
      indexedDocumentCount = Some(result.totalDocumentsIndexed),
      collectionIdentityReadiness = collectionIdentityReadiness,
      embeddingVectorReady = embeddingVectorReady,
    ))

  def evaluate(
    evidence: QdrantProductionCandidateIndexingEvidence
  ): QdrantProductionCandidateIndexingReport = {
    val blockingReasons = List(
      Option.when(evidence.expectedDocumentCount <= 0)(
        s"Expected document count must be positive: ${evidence.expectedDocumentCount}"
      ),
      Option.when(evidence.preparedDocumentCount <= 0)(
        s"Prepared document count must be positive: ${evidence.preparedDocumentCount}"
      ),
      Option.when(
        evidence.expectedDocumentCount > 0 &&
          evidence.preparedDocumentCount > 0 &&
          evidence.preparedDocumentCount != evidence.expectedDocumentCount
      )(
        s"Prepared document count ${evidence.preparedDocumentCount} does not match expected ${evidence.expectedDocumentCount}"
      ),
    ).flatten ++ indexedDocumentReasons(evidence) ++
      collectionIdentityReasons(evidence.collectionIdentityReadiness) ++
      Option.when(!evidence.embeddingVectorReady)("Embedding/vector readiness evidence is missing").toList

    QdrantProductionCandidateIndexingReport(
      evidence = evidence,
      decision =
        if (blockingReasons.isEmpty) {
          QdrantProductionCandidateIndexingDecision(QdrantProductionCandidateIndexingDecisionStatus.Ready, Nil)
        } else {
          QdrantProductionCandidateIndexingDecision(
            QdrantProductionCandidateIndexingDecisionStatus.NotReady,
            blockingReasons,
          )
        },
    )
  }

  def readinessStatus(
    report: Option[QdrantProductionCandidateIndexingReport]
  ): QdrantProductionCandidateReadinessStatus =
    report match {
      case None =>
        Unknown
      case Some(value) =>
        value.decision.status match {
          case QdrantProductionCandidateIndexingDecisionStatus.Ready =>
            QdrantProductionCandidateReadinessStatus.Ready
          case QdrantProductionCandidateIndexingDecisionStatus.NotReady =>
            QdrantProductionCandidateReadinessStatus.NotReady(value.decision.blockingReasons)
        }
    }

  private def indexedDocumentReasons(
    evidence: QdrantProductionCandidateIndexingEvidence
  ): List[String] =
    evidence.indexedDocumentCount match {
      case None =>
        List("Indexed document count evidence is missing")
      case Some(indexedDocumentCount) if indexedDocumentCount < 0 =>
        List(s"Indexed document count must be non-negative: $indexedDocumentCount")
      case Some(indexedDocumentCount)
          if evidence.expectedDocumentCount > 0 && indexedDocumentCount != evidence.expectedDocumentCount =>
        List(s"Indexed document count $indexedDocumentCount does not match expected ${evidence.expectedDocumentCount}")
      case Some(_) =>
        Nil
    }

  private def collectionIdentityReasons(
    status: QdrantProductionCandidateReadinessStatus
  ): List[String] =
    status match {
      case Ready =>
        Nil
      case NotReady(reasons) if reasons.nonEmpty =>
        reasons.map(reason => s"Collection identity is not ready: $reason")
      case NotReady(_) =>
        List("Collection identity is not ready")
      case NotEvaluated =>
        List("Collection identity readiness has not been evaluated")
      case NotConfigured =>
        List("Collection identity readiness is not configured")
      case NotApproved =>
        List("Collection identity readiness is not approved")
      case Unknown =>
        List("Collection identity readiness is unknown")
    }
}
