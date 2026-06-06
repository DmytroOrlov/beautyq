package leaderboard.search

import leaderboard.search.qdrant.QdrantEmbeddingBenchmarkSavedReportComparison
import org.scalatest.wordspec.AnyWordSpec

final class QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmarkSavedReportComparison manual comparison" should {
    "compare two saved JSON reports from environment variables" in {
      if (!envFlag(QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvCompareSavedReports)) {
        cancel(
          s"Set ${QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvCompareSavedReports}=true, " +
            s"${QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvLeftJson}=<json>, and " +
            s"${QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvRightJson}=<json> to run the saved-report comparison manual spec"
        )
      } else {
        (
          sys.env.get(QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvLeftJson),
          sys.env.get(QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvRightJson),
        ) match {
          case (Some(leftJson), Some(rightJson)) =>
            QdrantEmbeddingBenchmarkSavedReportComparison.compareReportJsonStrings(leftJson, rightJson) match {
              case Left(failure) =>
                fail(s"unexpected saved-report comparison failure: $failure")
              case Right(comparison) =>
                val formatted = QdrantEmbeddingBenchmarkSavedReportComparison.formatComparison(comparison)
                println("BEGIN_QDRANT_EMBEDDING_BENCHMARK_COMPARISON")
                println(formatted)
                println("END_QDRANT_EMBEDDING_BENCHMARK_COMPARISON")
            }
          case _ =>
            cancel(
              s"Set ${QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvCompareSavedReports}=true, " +
                s"${QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvLeftJson}=<json>, and " +
                s"${QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec.EnvRightJson}=<json> to run the saved-report comparison manual spec"
            )
        }
      }
    }
  }

  private def envFlag(name: String): Boolean =
    sys.env.get(name).exists { value =>
      val normalized = value.trim.toLowerCase
      normalized == "1" || normalized == "true" || normalized == "yes"
    }
}

object QdrantEmbeddingBenchmarkSavedReportComparisonManualSpec {
  val EnvCompareSavedReports = "QDRANT_EMBEDDING_BENCHMARK_COMPARE_SAVED_REPORTS"
  val EnvLeftJson = "QDRANT_EMBEDDING_BENCHMARK_LEFT_JSON"
  val EnvRightJson = "QDRANT_EMBEDDING_BENCHMARK_RIGHT_JSON"
}
