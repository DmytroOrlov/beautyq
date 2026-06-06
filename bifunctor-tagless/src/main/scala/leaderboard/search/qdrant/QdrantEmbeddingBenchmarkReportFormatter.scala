package leaderboard.search.qdrant

import java.util.Locale

object QdrantEmbeddingBenchmarkReportFormatter {
  def format(report: QdrantEmbeddingBenchmarkReport): String = {
    val builder = new StringBuilder

    line(builder, "Qdrant embedding benchmark report")
    line(builder, s"runMode: ${runModeLabel(report.plan.runMode)}")
    line(builder, s"k: ${report.plan.k}")
    line(builder, s"candidates: ${report.plan.candidates.map(_.candidateId).mkString(", ")}")

    report.candidateReports.foreach { candidateReport =>
      line(builder, "")
      line(builder, s"candidate: ${candidateReport.candidate.candidateId}")
      line(builder, s"  modelName: ${candidateReport.candidate.modelName}")
      line(builder, s"  endpointLabel: ${candidateReport.candidate.endpointLabel}")
      line(builder, s"  vectorDimension: ${candidateReport.candidate.vectorDimension}")
      line(builder, s"  queryCount: ${candidateReport.aggregate.queryCount}")
      line(builder, s"  variantRecallAtK: ${formatDouble(candidateReport.aggregate.variantRecallAtK)}")
      line(builder, s"  meanReciprocalRankAtK: ${formatDouble(candidateReport.aggregate.meanReciprocalRankAtK)}")
      line(builder, s"  providerHitRateAtK: ${formatDouble(candidateReport.aggregate.providerHitRateAtK)}")
      line(builder, s"  serviceHitRateAtK: ${formatDouble(candidateReport.aggregate.serviceHitRateAtK)}")
      line(builder, s"  meanQueryLatencyMs: ${formatLatency(candidateReport.aggregate.meanQueryLatencyMs)}")

      if (candidateReport.queryMetrics.nonEmpty) {
        line(builder, "  queries:")
        candidateReport.queryMetrics.foreach { queryMetrics =>
          line(
            builder,
            s"    ${queryMetrics.queryId} | candidate=${queryMetrics.candidateId} | variantHit=${yesNo(queryMetrics.variantHitAtK)} | rank=${formatRank(queryMetrics.variantHitRank)} | reciprocalRank=${formatDouble(queryMetrics.reciprocalRank)}"
          )
        }
      }
    }

    if (report.comparisons.nonEmpty) {
      line(builder, "")
      line(builder, "comparisons:")
      report.comparisons.foreach { comparison =>
        line(builder, s"  ${comparison.left.candidateId} -> ${comparison.right.candidateId}")
        line(builder, s"    variantRecallAtKDelta: ${formatDelta(comparison.variantRecallAtKDelta)}")
        line(builder, s"    meanReciprocalRankAtKDelta: ${formatDelta(comparison.meanReciprocalRankAtKDelta)}")
        line(builder, s"    providerHitRateAtKDelta: ${formatDelta(comparison.providerHitRateAtKDelta)}")
        line(builder, s"    serviceHitRateAtKDelta: ${formatDelta(comparison.serviceHitRateAtKDelta)}")
        comparison.meanQueryLatencyMsDelta.foreach { latencyDelta =>
          line(builder, s"    meanQueryLatencyMsDelta: ${formatDelta(latencyDelta)}")
        }
      }
    }

    builder.result()
  }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }

  private def runModeLabel(runMode: QdrantEmbeddingBenchmarkRunMode): String =
    runMode match {
      case QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart => "SingleEndpointManualRestart"
      case QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel        => "DualEndpointParallel"
    }

  private def yesNo(value: Boolean): String =
    if (value) "yes" else "no"

  private def formatRank(rank: Option[Int]): String =
    rank.fold("-")(_.toString)

  private def formatLatency(latency: Option[Double]): String =
    latency.fold("-")(formatDouble)

  private def formatDouble(value: Double): String =
    String.format(Locale.ROOT, "%.4f", Double.box(value))

  private def formatDelta(value: Double): String =
    String.format(Locale.ROOT, "%+.4f", Double.box(value))
}
