package leaderboard.search.qdrant

import leaderboard.search.eval.EngineEvalAggregateReport

object QdrantProductionCandidateQualityGate {

  def evaluate(
    parity: QdrantProductionCandidateParityReport,
    rule: QdrantProductionCandidateQualityRule,
  ): QdrantProductionCandidateQualityReport =
    QdrantProductionCandidateQualityPolicy.evaluate(parity, rule)

  def fromEngineEval(
    report: EngineEvalAggregateReport,
    rule: QdrantProductionCandidateQualityRule,
    baselineLabel: String = "Elasticsearch",
    candidateLabel: String = "Qdrant",
  ): QdrantProductionCandidateQualityReport =
    evaluate(
      parity = QdrantProductionCandidateParityReport(
        baselineLabel = baselineLabel,
        candidateLabel = candidateLabel,
        evaluatedQueryCount = report.aggregate.queryCount,
        baselineRecallCount = report.aggregate.esRecallCount,
        candidateRecallCount = report.aggregate.qdrantRecallCount,
        qdrantNoiseCount = report.aggregate.qdrantNoiseCount,
      ),
      rule = rule,
    )

  def readinessStatus(
    report: Option[QdrantProductionCandidateQualityReport]
  ): QdrantProductionCandidateReadinessStatus =
    QdrantProductionCandidateQualityPolicy.readinessStatus(report)
}
