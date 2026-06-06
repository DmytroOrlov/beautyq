package leaderboard.search.qdrant

import leaderboard.model.QueryFailure

import java.util.Locale

object QdrantEmbeddingBenchmarkSavedReportComparison {
  private val OperationName = "qdrant-embedding-benchmark-saved-report-comparison"

  def compareReports(
    left: QdrantEmbeddingBenchmarkReport,
    right: QdrantEmbeddingBenchmarkReport,
  ): Either[QueryFailure, QdrantEmbeddingBenchmarkComparison] =
    for {
      leftCandidateReport <- singleCandidateReport(left, side = "left")
      rightCandidateReport <- singleCandidateReport(right, side = "right")
    } yield QdrantEmbeddingBenchmark.compare(leftCandidateReport.aggregate, rightCandidateReport.aggregate)

  def compareReportJsonStrings(
    leftJson: String,
    rightJson: String,
  ): Either[QueryFailure, QdrantEmbeddingBenchmarkComparison] =
    for {
      leftReport <- QdrantEmbeddingBenchmarkReportJson.decodeReportString(leftJson)
      rightReport <- QdrantEmbeddingBenchmarkReportJson.decodeReportString(rightJson)
      comparison <- compareReports(leftReport, rightReport)
    } yield comparison

  def formatComparison(comparison: QdrantEmbeddingBenchmarkComparison): String = {
    val builder = new StringBuilder

    line(builder, "Qdrant embedding benchmark saved-report comparison")
    line(builder, s"leftCandidateId: ${comparison.left.candidateId}")
    line(builder, s"rightCandidateId: ${comparison.right.candidateId}")
    line(builder, s"variantRecallAtKDelta: ${formatDelta(comparison.variantRecallAtKDelta)}")
    line(builder, s"meanReciprocalRankAtKDelta: ${formatDelta(comparison.meanReciprocalRankAtKDelta)}")
    line(builder, s"providerHitRateAtKDelta: ${formatDelta(comparison.providerHitRateAtKDelta)}")
    line(builder, s"serviceHitRateAtKDelta: ${formatDelta(comparison.serviceHitRateAtKDelta)}")
    line(builder, s"meanQueryLatencyMsDelta: ${comparison.meanQueryLatencyMsDelta.fold("-")(formatDelta)}")

    builder.result()
  }

  private def singleCandidateReport(
    report: QdrantEmbeddingBenchmarkReport,
    side: String,
  ): Either[QueryFailure, QdrantEmbeddingBenchmarkCandidateReport] =
    report.candidateReports match {
      case candidateReport :: Nil => Right(candidateReport)
      case other =>
        Left(
          QueryFailure.operation(
            OperationName,
            s"Expected exactly one candidate report in $side saved report, got ${other.size}",
          )
        )
    }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }

  private def formatDelta(value: Double): String =
    String.format(Locale.ROOT, "%+.4f", Double.box(value))
}
