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

final case class EngineEvalQueryComparison(
  queryId: String,
  left: EngineEvalQueryComparisonMetrics,
  right: EngineEvalQueryComparisonMetrics,
  expectedVariantCountDelta: Int,
  esRecallCountDelta: Int,
  qdrantRecallCountDelta: Int,
  qdrantComplementCountDelta: Int,
  qdrantNoiseCountDelta: Int,
  overlapCountDelta: Int,
  simulatedHybridGainCountDelta: Int,
)

final case class EngineEvalQueryComparisonMetrics(
  expectedVariantCount: Int,
  esRecallCount: Int,
  qdrantRecallCount: Int,
  qdrantComplementCount: Int,
  qdrantNoiseCount: Int,
  overlapCount: Int,
  simulatedHybridGainCount: Int,
)

final case class EngineEvalQueryClassComparison(
  queryClass: EngineEvalQueryClass,
  left: EngineEvalQueryClassAggregateMetrics,
  right: EngineEvalQueryClassAggregateMetrics,
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
  classComparisons: List[EngineEvalQueryClassComparison],
  roleComparisons: List[EngineEvalRoleComparison],
  queryComparisons: List[EngineEvalQueryComparison],
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
    val queryComparisons = compareQueries(left.queryReports, right.queryReports)
    aggregateComparison.copy(roleComparisons = roleComparisons, queryComparisons = queryComparisons)
  }

  def compareReportsWithQueryClasses(
    left: EngineEvalAggregateReport,
    right: EngineEvalAggregateReport,
    leftClassesByQueryId: Map[String, List[EngineEvalQueryClass]],
    rightClassesByQueryId: Map[String, List[EngineEvalQueryClass]],
  ): Either[QueryFailure, EngineEvalAggregateComparison] =
    for {
      leftClassBreakdown <- EngineEvalQueryClassBreakdown.from(left.queryReports, leftClassesByQueryId)
      rightClassBreakdown <- EngineEvalQueryClassBreakdown.from(right.queryReports, rightClassesByQueryId)
    } yield {
      val comparison = compareReports(left, right)
      comparison.copy(classComparisons = compareClasses(leftClassBreakdown, rightClassBreakdown))
    }

  def compareReportJsonStrings(
    leftJson: String,
    rightJson: String,
  ): Either[QueryFailure, EngineEvalAggregateComparison] =
    for {
      leftReport <- EngineEvalReportJson.decodeReportString(leftJson)
      rightReport <- EngineEvalReportJson.decodeReportString(rightJson)
    } yield compareReports(leftReport, rightReport)

  def compareReportJsonStringsWithQueryClasses(
    leftJson: String,
    rightJson: String,
    leftClassesByQueryId: Map[String, List[EngineEvalQueryClass]],
    rightClassesByQueryId: Map[String, List[EngineEvalQueryClass]],
  ): Either[QueryFailure, EngineEvalAggregateComparison] =
    for {
      leftReport <- EngineEvalReportJson.decodeReportString(leftJson)
      rightReport <- EngineEvalReportJson.decodeReportString(rightJson)
      comparison <- compareReportsWithQueryClasses(
        left = leftReport,
        right = rightReport,
        leftClassesByQueryId = leftClassesByQueryId,
        rightClassesByQueryId = rightClassesByQueryId,
      )
    } yield comparison

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

    val presentClassDeltas = comparison.classComparisons.filterNot(c => isZeroClass(c))
    if (presentClassDeltas.nonEmpty) {
      line(builder, "")
      line(builder, "classDeltas:")
      presentClassDeltas.foreach(formatClassDelta(builder, _))
    }

    val presentQueryDeltas = comparison.queryComparisons.filterNot(q => isZeroQuery(q))
    if (presentQueryDeltas.nonEmpty) {
      line(builder, "")
      line(builder, "queryDeltas:")
      presentQueryDeltas.foreach(formatQueryDelta(builder, _))
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
      classComparisons = Nil,
      roleComparisons = Nil,
      queryComparisons = Nil,
    )

  private def compareClasses(
    leftBreakdown: EngineEvalQueryClassBreakdown,
    rightBreakdown: EngineEvalQueryClassBreakdown,
  ): List[EngineEvalQueryClassComparison] = {
    val leftMap = leftBreakdown.byClass.map(c => c.queryClass -> c).toMap
    val rightMap = rightBreakdown.byClass.map(c => c.queryClass -> c).toMap
    val allClasses = EngineEvalQueryClass.stableOrder.filter(c => leftMap.contains(c) || rightMap.contains(c))
    allClasses.map { queryClass =>
      val left = leftMap.getOrElse(queryClass, zeroClassMetrics(queryClass))
      val right = rightMap.getOrElse(queryClass, zeroClassMetrics(queryClass))
      EngineEvalQueryClassComparison(
        queryClass = queryClass,
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

  private def compareQueries(
    leftReports: List[EngineEvalQueryReport],
    rightReports: List[EngineEvalQueryReport],
  ): List[EngineEvalQueryComparison] = {
    val leftMap = leftReports.map(r => r.queryId -> queryMetrics(r)).toMap
    val rightMap = rightReports.map(r => r.queryId -> queryMetrics(r)).toMap
    val leftIds = leftReports.map(_.queryId).distinct
    val rightOnlyIds = rightReports.map(_.queryId).distinct.filterNot(id => leftMap.contains(id))
    (leftIds ++ rightOnlyIds).map { queryId =>
      val left = leftMap.getOrElse(queryId, zeroQueryMetrics)
      val right = rightMap.getOrElse(queryId, zeroQueryMetrics)
      EngineEvalQueryComparison(
        queryId = queryId,
        left = left,
        right = right,
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

  private def queryMetrics(report: EngineEvalQueryReport): EngineEvalQueryComparisonMetrics =
    EngineEvalQueryComparisonMetrics(
      expectedVariantCount = report.expectedVariantIds.size,
      esRecallCount = report.metrics.esRecallCount,
      qdrantRecallCount = report.metrics.qdrantRecallCount,
      qdrantComplementCount = report.metrics.qdrantComplementCount,
      qdrantNoiseCount = report.metrics.qdrantNoiseCount,
      overlapCount = report.metrics.overlapCount,
      simulatedHybridGainCount = report.metrics.simulatedHybridGainCount,
    )

  private val zeroQueryMetrics: EngineEvalQueryComparisonMetrics =
    EngineEvalQueryComparisonMetrics(
      expectedVariantCount = 0,
      esRecallCount = 0,
      qdrantRecallCount = 0,
      qdrantComplementCount = 0,
      qdrantNoiseCount = 0,
      overlapCount = 0,
      simulatedHybridGainCount = 0,
    )

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

  private def zeroClassMetrics(queryClass: EngineEvalQueryClass): EngineEvalQueryClassAggregateMetrics =
    EngineEvalQueryClassAggregateMetrics(
      queryClass = queryClass,
      queryCount = 0,
      expectedVariantCount = 0,
      esRecallCount = 0,
      qdrantRecallCount = 0,
      qdrantComplementCount = 0,
      qdrantNoiseCount = 0,
      overlapCount = 0,
      simulatedHybridGainCount = 0,
    )

  private def isZeroClass(comparison: EngineEvalQueryClassComparison): Boolean =
    comparison.queryCountDelta == 0 &&
    comparison.expectedVariantCountDelta == 0 &&
    comparison.esRecallCountDelta == 0 &&
    comparison.qdrantRecallCountDelta == 0 &&
    comparison.qdrantComplementCountDelta == 0 &&
    comparison.qdrantNoiseCountDelta == 0 &&
    comparison.overlapCountDelta == 0 &&
    comparison.simulatedHybridGainCountDelta == 0

  private def isZeroRole(comparison: EngineEvalRoleComparison): Boolean =
    comparison.queryCountDelta == 0 &&
    comparison.expectedVariantCountDelta == 0 &&
    comparison.esRecallCountDelta == 0 &&
    comparison.qdrantRecallCountDelta == 0 &&
    comparison.qdrantComplementCountDelta == 0 &&
    comparison.qdrantNoiseCountDelta == 0 &&
    comparison.overlapCountDelta == 0 &&
    comparison.simulatedHybridGainCountDelta == 0

  private def isZeroQuery(comparison: EngineEvalQueryComparison): Boolean =
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

  private def formatClassDelta(builder: StringBuilder, classComparison: EngineEvalQueryClassComparison): Unit = {
    line(
      builder,
      s"  ${classComparison.queryClass} | queryCountDelta=${formatDelta(classComparison.queryCountDelta)} | expectedVariantCountDelta=${formatDelta(classComparison.expectedVariantCountDelta)} | esRecallDelta=${formatDelta(classComparison.esRecallCountDelta)} | qdrantRecallDelta=${formatDelta(classComparison.qdrantRecallCountDelta)} | qdrantComplementDelta=${formatDelta(classComparison.qdrantComplementCountDelta)} | qdrantNoiseDelta=${formatDelta(classComparison.qdrantNoiseCountDelta)} | overlapDelta=${formatDelta(classComparison.overlapCountDelta)} | simulatedHybridGainDelta=${formatDelta(classComparison.simulatedHybridGainCountDelta)}"
    )
  }

  private def formatQueryDelta(builder: StringBuilder, queryComparison: EngineEvalQueryComparison): Unit = {
    line(
      builder,
      s"  ${queryComparison.queryId} | expectedVariantCountDelta=${formatDelta(queryComparison.expectedVariantCountDelta)} | esRecallDelta=${formatDelta(queryComparison.esRecallCountDelta)} | qdrantRecallDelta=${formatDelta(queryComparison.qdrantRecallCountDelta)} | qdrantComplementDelta=${formatDelta(queryComparison.qdrantComplementCountDelta)} | qdrantNoiseDelta=${formatDelta(queryComparison.qdrantNoiseCountDelta)} | overlapDelta=${formatDelta(queryComparison.overlapCountDelta)} | simulatedHybridGainDelta=${formatDelta(queryComparison.simulatedHybridGainCountDelta)}"
    )
  }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
