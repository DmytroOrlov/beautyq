package leaderboard.search.eval

import leaderboard.model.QueryFailure

final case class EngineEvalAggregateComparison(
  left: EngineEvalAggregateMetrics,
  right: EngineEvalAggregateMetrics,
  queryCountDelta: Int,
  expectedVariantCountDelta: Int,
  esRecallCountDelta: Int,
  qdrantRecallCountDelta: Int,
  qdrantComplementCountDelta: Int,
  qdrantNoiseCountDelta: Int,
  overlapCountDelta: Int,
  simulatedHybridGainCountDelta: Int,
)

object EngineEvalSavedReportComparison {
  def compareReports(
    left: EngineEvalAggregateReport,
    right: EngineEvalAggregateReport,
  ): EngineEvalAggregateComparison =
    compareAggregates(left.aggregate, right.aggregate)

  def compareReportJsonStrings(
    leftJson: String,
    rightJson: String,
  ): Either[QueryFailure, EngineEvalAggregateComparison] =
    for {
      leftReport <- EngineEvalReportJson.decodeReportString(leftJson)
      rightReport <- EngineEvalReportJson.decodeReportString(rightJson)
    } yield compareReports(leftReport, rightReport)

  def formatComparison(comparison: EngineEvalAggregateComparison): String = {
    val builder = new StringBuilder

    line(builder, "EngineEval saved-report comparison")
    line(builder, s"queryCountDelta: ${formatDelta(comparison.queryCountDelta)}")
    line(builder, s"expectedVariantCountDelta: ${formatDelta(comparison.expectedVariantCountDelta)}")
    line(builder, s"esRecallCountDelta: ${formatDelta(comparison.esRecallCountDelta)}")
    line(builder, s"qdrantRecallCountDelta: ${formatDelta(comparison.qdrantRecallCountDelta)}")
    line(builder, s"qdrantComplementCountDelta: ${formatDelta(comparison.qdrantComplementCountDelta)}")
    line(builder, s"qdrantNoiseCountDelta: ${formatDelta(comparison.qdrantNoiseCountDelta)}")
    line(builder, s"overlapCountDelta: ${formatDelta(comparison.overlapCountDelta)}")
    line(builder, s"simulatedHybridGainCountDelta: ${formatDelta(comparison.simulatedHybridGainCountDelta)}")

    builder.result()
  }

  private def compareAggregates(
    left: EngineEvalAggregateMetrics,
    right: EngineEvalAggregateMetrics,
  ): EngineEvalAggregateComparison =
    EngineEvalAggregateComparison(
      left = left,
      right = right,
      queryCountDelta = right.queryCount - left.queryCount,
      expectedVariantCountDelta = right.expectedVariantCount - left.expectedVariantCount,
      esRecallCountDelta = right.esRecallCount - left.esRecallCount,
      qdrantRecallCountDelta = right.qdrantRecallCount - left.qdrantRecallCount,
      qdrantComplementCountDelta = right.qdrantComplementCount - left.qdrantComplementCount,
      qdrantNoiseCountDelta = right.qdrantNoiseCount - left.qdrantNoiseCount,
      overlapCountDelta = right.overlapCount - left.overlapCount,
      simulatedHybridGainCountDelta = right.simulatedHybridGainCount - left.simulatedHybridGainCount,
    )

  private def formatDelta(value: Int): String =
    if (value >= 0) s"+$value" else value.toString

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
