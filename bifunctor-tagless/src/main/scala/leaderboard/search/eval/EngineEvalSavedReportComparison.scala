package leaderboard.search.eval

import leaderboard.model.QueryFailure

final case class EngineEvalRoleComparison(
  role: EngineExpectedRole,
  left: EngineEvalRoleAggregateMetrics,
  right: EngineEvalRoleAggregateMetrics,
  queryCountDelta: Int,
  expectedVariantCountDelta: Int,
  esRecallCountDelta: Int,
  qdrantRecallCountDelta: Int,
  qdrantComplementCountDelta: Int,
  qdrantNoiseCountDelta: Int,
  overlapCountDelta: Int,
  simulatedHybridGainCountDelta: Int,
)

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
  roleComparisons: List[EngineEvalRoleComparison],
)

object EngineEvalSavedReportComparison {
  def compareReports(
    left: EngineEvalAggregateReport,
    right: EngineEvalAggregateReport,
  ): EngineEvalAggregateComparison = {
    val aggregateComparison = compareAggregates(left.aggregate, right.aggregate)
    val roleComparisons = compareRoles(
      EngineEvalRoleBreakdown.from(left.queryReports),
      EngineEvalRoleBreakdown.from(right.queryReports),
    )
    aggregateComparison.copy(roleComparisons = roleComparisons)
  }

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

    val presentRoleDeltas = comparison.roleComparisons.filterNot(r => isZeroRole(r))
    if (presentRoleDeltas.nonEmpty) {
      line(builder, "")
      line(builder, "roleDeltas:")
      presentRoleDeltas.foreach(formatRoleDelta(builder, _))
    }

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
      roleComparisons = Nil,
    )

  private def compareRoles(
    leftBreakdown: EngineEvalRoleBreakdown,
    rightBreakdown: EngineEvalRoleBreakdown,
  ): List[EngineEvalRoleComparison] = {
    val leftMap = leftBreakdown.byRole.map(r => r.role -> r).toMap
    val rightMap = rightBreakdown.byRole.map(r => r.role -> r).toMap
    val allRoles = EngineExpectedRole.stableOrder.filter(r => leftMap.contains(r) || rightMap.contains(r))
    allRoles.map { role =>
      val left = leftMap.getOrElse(role, zeroRoleMetrics(role))
      val right = rightMap.getOrElse(role, zeroRoleMetrics(role))
      EngineEvalRoleComparison(
        role = role,
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
    }
  }

  private def zeroRoleMetrics(role: EngineExpectedRole): EngineEvalRoleAggregateMetrics =
    EngineEvalRoleAggregateMetrics(
      role = role,
      queryCount = 0,
      expectedVariantCount = 0,
      esRecallCount = 0,
      qdrantRecallCount = 0,
      qdrantComplementCount = 0,
      qdrantNoiseCount = 0,
      overlapCount = 0,
      simulatedHybridGainCount = 0,
    )

  private def isZeroRole(comparison: EngineEvalRoleComparison): Boolean =
    comparison.queryCountDelta == 0 &&
    comparison.expectedVariantCountDelta == 0 &&
    comparison.esRecallCountDelta == 0 &&
    comparison.qdrantRecallCountDelta == 0 &&
    comparison.qdrantComplementCountDelta == 0 &&
    comparison.qdrantNoiseCountDelta == 0 &&
    comparison.overlapCountDelta == 0 &&
    comparison.simulatedHybridGainCountDelta == 0

  private def formatDelta(value: Int): String =
    if (value >= 0) s"+$value" else value.toString

  private def formatRoleDelta(builder: StringBuilder, roleComparison: EngineEvalRoleComparison): Unit = {
    line(
      builder,
      s"  ${roleComparison.role} | queryCountDelta=${formatDelta(roleComparison.queryCountDelta)} | expectedVariantCountDelta=${formatDelta(roleComparison.expectedVariantCountDelta)} | esRecallDelta=${formatDelta(roleComparison.esRecallCountDelta)} | qdrantRecallDelta=${formatDelta(roleComparison.qdrantRecallCountDelta)} | qdrantComplementDelta=${formatDelta(roleComparison.qdrantComplementCountDelta)} | qdrantNoiseDelta=${formatDelta(roleComparison.qdrantNoiseCountDelta)} | overlapDelta=${formatDelta(roleComparison.overlapCountDelta)} | simulatedHybridGainDelta=${formatDelta(roleComparison.simulatedHybridGainCountDelta)}"
    )
  }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
