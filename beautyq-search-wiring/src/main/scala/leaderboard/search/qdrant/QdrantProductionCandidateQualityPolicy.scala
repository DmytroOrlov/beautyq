package leaderboard.search.qdrant

final case class QdrantProductionCandidateQualityRule(
  minimumEvaluatedQueryCount: Int,
  maximumRecallDeficit: Int,
  maximumQdrantNoiseCount: Int,
)

final case class QdrantProductionCandidateParityReport(
  baselineLabel: String,
  candidateLabel: String,
  evaluatedQueryCount: Int,
  baselineRecallCount: Int,
  candidateRecallCount: Int,
  qdrantNoiseCount: Int,
)

sealed trait QdrantProductionCandidateQualityDecisionStatus extends Product with Serializable
object QdrantProductionCandidateQualityDecisionStatus {
  case object Passed extends QdrantProductionCandidateQualityDecisionStatus
  case object Failed extends QdrantProductionCandidateQualityDecisionStatus
  case object Unevaluated extends QdrantProductionCandidateQualityDecisionStatus
  case object Incomplete extends QdrantProductionCandidateQualityDecisionStatus
}

final case class QdrantProductionCandidateQualityDecision(
  status: QdrantProductionCandidateQualityDecisionStatus,
  reasons: List[String],
)

sealed trait QdrantProductionCandidateParityOutcome extends Product with Serializable
object QdrantProductionCandidateParityOutcome {
  case object Passed extends QdrantProductionCandidateParityOutcome
  case object Failed extends QdrantProductionCandidateParityOutcome
  case object Unevaluated extends QdrantProductionCandidateParityOutcome
  case object Incomplete extends QdrantProductionCandidateParityOutcome
}

final case class QdrantProductionCandidateQualityReport(
  parity: QdrantProductionCandidateParityReport,
  rule: QdrantProductionCandidateQualityRule,
  parityOutcome: QdrantProductionCandidateParityOutcome,
  decision: QdrantProductionCandidateQualityDecision,
)

object QdrantProductionCandidateQualityPolicy {
  import QdrantProductionCandidateQualityDecisionStatus.*

  def evaluate(
    parity: QdrantProductionCandidateParityReport,
    rule: QdrantProductionCandidateQualityRule,
  ): QdrantProductionCandidateQualityReport = {
    val incompleteReasons = List(
      Option.when(parity.baselineLabel.trim.isEmpty)("Baseline label must be non-blank"),
      Option.when(parity.candidateLabel.trim.isEmpty)("Candidate label must be non-blank"),
      Option.when(parity.evaluatedQueryCount < 0)(s"Evaluated query count must be non-negative: ${parity.evaluatedQueryCount}"),
      Option.when(parity.baselineRecallCount < 0)(s"Baseline recall count must be non-negative: ${parity.baselineRecallCount}"),
      Option.when(parity.candidateRecallCount < 0)(s"Candidate recall count must be non-negative: ${parity.candidateRecallCount}"),
      Option.when(parity.qdrantNoiseCount < 0)(s"Qdrant noise count must be non-negative: ${parity.qdrantNoiseCount}"),
      Option.when(rule.minimumEvaluatedQueryCount <= 0)(s"Minimum evaluated query count must be positive: ${rule.minimumEvaluatedQueryCount}"),
      Option.when(rule.maximumRecallDeficit < 0)(s"Maximum recall deficit must be non-negative: ${rule.maximumRecallDeficit}"),
      Option.when(rule.maximumQdrantNoiseCount < 0)(s"Maximum Qdrant noise count must be non-negative: ${rule.maximumQdrantNoiseCount}"),
    ).flatten

    val decision =
      if (incompleteReasons.nonEmpty) {
        QdrantProductionCandidateQualityDecision(Incomplete, incompleteReasons)
      } else {
        val minimumCandidateRecall = parity.baselineRecallCount - rule.maximumRecallDeficit
        val failureReasons = List(
          Option.when(parity.evaluatedQueryCount < rule.minimumEvaluatedQueryCount)(
            s"Evaluated query count ${parity.evaluatedQueryCount} is below minimum ${rule.minimumEvaluatedQueryCount}"
          ),
          Option.when(parity.candidateRecallCount < minimumCandidateRecall)(
            s"Candidate ${parity.candidateLabel} recall ${parity.candidateRecallCount} is below baseline ${parity.baselineLabel} recall ${parity.baselineRecallCount} minus allowed deficit ${rule.maximumRecallDeficit}"
          ),
          Option.when(parity.qdrantNoiseCount > rule.maximumQdrantNoiseCount)(
            s"Candidate ${parity.candidateLabel} noise count ${parity.qdrantNoiseCount} exceeds maximum ${rule.maximumQdrantNoiseCount}"
          ),
        ).flatten

        if (failureReasons.isEmpty) QdrantProductionCandidateQualityDecision(Passed, Nil)
        else QdrantProductionCandidateQualityDecision(Failed, failureReasons)
      }

    QdrantProductionCandidateQualityReport(
      parity = parity,
      rule = rule,
      parityOutcome = parityOutcome(decision.status),
      decision = decision,
    )
  }

  def readinessStatus(
    report: Option[QdrantProductionCandidateQualityReport]
  ): QdrantProductionCandidateReadinessStatus =
    report match {
      case None =>
        QdrantProductionCandidateReadinessStatus.NotEvaluated
      case Some(value) =>
        value.decision.status match {
          case Passed =>
            QdrantProductionCandidateReadinessStatus.Ready
          case Failed =>
            QdrantProductionCandidateReadinessStatus.NotReady(value.decision.reasons)
          case Unevaluated =>
            QdrantProductionCandidateReadinessStatus.NotEvaluated
          case Incomplete =>
            QdrantProductionCandidateReadinessStatus.Unknown
        }
    }

  private def parityOutcome(
    status: QdrantProductionCandidateQualityDecisionStatus
  ): QdrantProductionCandidateParityOutcome =
    status match {
      case Passed      => QdrantProductionCandidateParityOutcome.Passed
      case Failed      => QdrantProductionCandidateParityOutcome.Failed
      case Unevaluated => QdrantProductionCandidateParityOutcome.Unevaluated
      case Incomplete  => QdrantProductionCandidateParityOutcome.Incomplete
    }
}
