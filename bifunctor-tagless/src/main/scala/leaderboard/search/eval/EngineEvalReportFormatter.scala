package leaderboard.search.eval

import leaderboard.model.MasterServiceOfferVariantId

object EngineEvalReportFormatter {
  def format(report: EngineEvalAggregateReport): String = {
    val builder = new StringBuilder

    line(builder, "EngineEval aggregate report")
    line(builder, s"queryCount: ${report.aggregate.queryCount}")
    line(builder, s"expectedVariantCount: ${report.aggregate.expectedVariantCount}")
    line(builder, s"esRecallCount: ${report.aggregate.esRecallCount}")
    line(builder, s"qdrantRecallCount: ${report.aggregate.qdrantRecallCount}")
    line(builder, s"qdrantComplementCount: ${report.aggregate.qdrantComplementCount}")
    line(builder, s"qdrantNoiseCount: ${report.aggregate.qdrantNoiseCount}")
    line(builder, s"overlapCount: ${report.aggregate.overlapCount}")
    line(builder, s"simulatedHybridGainCount: ${report.aggregate.simulatedHybridGainCount}")

    if (report.queryReports.nonEmpty) {
      line(builder, "")
      line(builder, "queries:")
      report.queryReports.foreach(formatQuery(builder, _))
    }

    builder.result()
  }

  private def formatQuery(builder: StringBuilder, queryReport: EngineEvalQueryReport): Unit = {
    val metrics = queryReport.metrics
    line(
      builder,
      s"  ${queryReport.queryId} | expectedRole=${queryReport.expectedRole} | expectedVariantCount=${queryReport.expectedVariantIds.size} | esRecall=${metrics.esRecallCount} | qdrantRecall=${metrics.qdrantRecallCount} | qdrantComplement=${metrics.qdrantComplementCount} | qdrantNoise=${metrics.qdrantNoiseCount} | overlap=${metrics.overlapCount} | simulatedHybridGain=${metrics.simulatedHybridGainCount}"
    )
    line(builder, s"    es: ${formatVariantIds(queryReport.es.variantIds)}")
    line(builder, s"    qdrant: ${formatVariantIds(queryReport.qdrant.variantIds)}")
    line(builder, s"    simulatedHybrid: ${formatVariantIds(queryReport.simulatedHybrid.variantIds)}")
  }

  private def formatVariantIds(ids: List[MasterServiceOfferVariantId]): String =
    ids match {
      case Nil => "-"
      case _   => ids.map(_.toString).mkString(", ")
    }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
