package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.search.qdrant.{
  QdrantEmbeddingBenchmarkAggregate,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkCandidateReport,
  QdrantEmbeddingBenchmarkComparison,
  QdrantEmbeddingBenchmarkPlan,
  QdrantEmbeddingBenchmarkQueryMetrics,
  QdrantEmbeddingBenchmarkQueryResult,
  QdrantEmbeddingBenchmarkReport,
  QdrantEmbeddingBenchmarkReportFormatter,
  QdrantEmbeddingBenchmarkReportJson,
  QdrantEmbeddingBenchmarkRunMode,
}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantEmbeddingBenchmarkReportJsonSpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmarkReportJson" should {
    "round-trip a SingleEndpointManualRestart report" in {
      val candidate = benchmarkCandidate(
        candidateId = "qwen3-0_6b",
        modelName = "Qwen3-Embedding-0.6B",
        endpointLabel = "http://localhost:8081",
        vectorDimension = 1024,
        notes = Some("saved after manual restart"),
      )
      val report = QdrantEmbeddingBenchmarkReport(
        plan = QdrantEmbeddingBenchmarkPlan(
          runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
          candidates = List(candidate),
          k = 5,
        ),
        candidateReports = List(
          QdrantEmbeddingBenchmarkCandidateReport(
            candidate = candidate,
            queryMetrics = List(
              QdrantEmbeddingBenchmarkQueryMetrics(
                queryId = "q-single-1",
                candidateId = candidate.candidateId,
                variantHitAtK = true,
                variantHitRank = Some(1),
                reciprocalRank = 1.0,
                providerHitAtK = true,
                serviceHitAtK = false,
              )
            ),
            aggregate = QdrantEmbeddingBenchmarkAggregate(
              candidateId = candidate.candidateId,
              queryCount = 1,
              variantRecallAtK = 1.0,
              meanReciprocalRankAtK = 1.0,
              providerHitRateAtK = 1.0,
              serviceHitRateAtK = 0.0,
              meanQueryLatencyMs = Some(12.5),
            ),
          )
        ),
        comparisons = Nil,
      )

      val decoded = decode(report)

      assert(decoded == report)
      assert(decoded.plan.runMode == QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart)
      assert(decoded.plan.k == 5)
      assert(decoded.plan.candidates == List(candidate))
    }

    "round-trip a DualEndpointParallel report with comparison" in {
      val left = benchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val right = benchmarkCandidate("qwen3-4b", "Qwen3-Embedding-4B", "http://localhost:8082", 2560)
      val report = QdrantEmbeddingBenchmarkReport(
        plan = QdrantEmbeddingBenchmarkPlan(
          runMode = QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel,
          candidates = List(left, right),
          k = 10,
        ),
        candidateReports = List(
          candidateReport(left, List(queryMetrics("q-1", left.candidateId, variantHitRank = None, reciprocalRank = 0.0))),
          candidateReport(right, List(queryMetrics("q-1", right.candidateId, variantHitRank = Some(2), reciprocalRank = 0.5))),
        ),
        comparisons = List(
          QdrantEmbeddingBenchmarkComparison(
            left = QdrantEmbeddingBenchmarkAggregate(left.candidateId, 1, 0.0, 0.0, 0.0, 0.0, Some(40.0)),
            right = QdrantEmbeddingBenchmarkAggregate(right.candidateId, 1, 1.0, 0.5, 1.0, 1.0, Some(25.0)),
            variantRecallAtKDelta = 1.0,
            meanReciprocalRankAtKDelta = 0.5,
            providerHitRateAtKDelta = 1.0,
            serviceHitRateAtKDelta = 1.0,
            meanQueryLatencyMsDelta = Some(-15.0),
          )
        ),
      )

      val decoded = decode(report)

      assert(decoded == report)
      assert(decoded.plan.runMode == QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel)
      assert(decoded.comparisons.size == 1)
      assert(decoded.comparisons.head.meanQueryLatencyMsDelta.contains(-15.0))
    }

    "preserve UUID-backed domain ids exactly via related benchmark report json codecs" in {
      val queryResult = QdrantEmbeddingBenchmarkQueryResult(
        candidateId = "qwen3-4b",
        queryId = "q-uuid",
        queryText = "uuid-check",
        topVariantIds = List(variantId(1), variantId(2)),
        topProviderIds = List(providerId(3)),
        topServiceIds = List(serviceId(4)),
        scores = List(0.91, 0.77),
        queryLatencyMs = Some(19L),
      )

      val decoded = queryResult
        .asJson(QdrantEmbeddingBenchmarkReportJson.queryResultEncoder)
        .as[QdrantEmbeddingBenchmarkQueryResult](QdrantEmbeddingBenchmarkReportJson.queryResultDecoder)

      assert(decoded.contains(queryResult))
      assert(decoded.exists(_.topVariantIds == List(variantId(1), variantId(2))))
      assert(decoded.exists(_.topProviderIds == List(providerId(3))))
      assert(decoded.exists(_.topServiceIds == List(serviceId(4))))
    }

    "preserve optional latency None and candidate order across round-trip" in {
      val first = benchmarkCandidate("first", "First", "http://localhost:8081", 1024)
      val second = benchmarkCandidate("second", "Second", "http://localhost:8081", 2560)
      val report = QdrantEmbeddingBenchmarkReport(
        plan = QdrantEmbeddingBenchmarkPlan(
          runMode = QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel,
          candidates = List(first, second),
          k = 3,
        ),
        candidateReports = List(
          candidateReport(first, List(queryMetrics("q-1", first.candidateId, variantHitRank = Some(1), reciprocalRank = 1.0)), meanQueryLatencyMs = None),
          candidateReport(second, List(queryMetrics("q-1", second.candidateId, variantHitRank = Some(2), reciprocalRank = 0.5)), meanQueryLatencyMs = Some(22.0)),
        ),
        comparisons = Nil,
      )

      val decoded = decode(report)

      assert(decoded.candidateReports.map(_.candidate.candidateId) == List("first", "second"))
      assert(decoded.candidateReports.head.aggregate.meanQueryLatencyMs.isEmpty)
      assert(decoded.candidateReports(1).aggregate.meanQueryLatencyMs.contains(22.0))
    }

    "preserve candidate notes across round-trip" in {
      val report = QdrantEmbeddingBenchmarkReport(
        plan = QdrantEmbeddingBenchmarkPlan(
          runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
          candidates = List(benchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024, notes = Some("notes stay stable"))),
          k = 1,
        ),
        candidateReports = Nil,
        comparisons = Nil,
      )

      val decoded = decode(report)

      assert(decoded.plan.candidates.head.notes.contains("notes stay stable"))
    }

    "fail clearly on invalid json decode" in {
      val failure = QdrantEmbeddingBenchmarkReportJson
        .decodeReport(Json.obj("plan" -> Json.obj("runMode" -> Json.fromString("WrongMode"))))
        .swap
        .toOption
        .getOrElse(fail("expected decode failure"))

      failure match {
        case QueryFailure.OperationFailure(operationName, message) =>
          assert(operationName == "qdrant-embedding-benchmark-report-json")
          assert(message.contains("Unsupported Qdrant embedding benchmark run mode: WrongMode"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }

    "decode from json string and keep the report formattable" in {
      val candidate = benchmarkCandidate("qwen3-0_6b", "Qwen3-Embedding-0.6B", "http://localhost:8081", 1024)
      val report = QdrantEmbeddingBenchmarkReport(
        plan = QdrantEmbeddingBenchmarkPlan(
          runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
          candidates = List(candidate),
          k = 2,
        ),
        candidateReports = List(
          candidateReport(candidate, List(queryMetrics("q-format", candidate.candidateId, variantHitRank = Some(1), reciprocalRank = 1.0)), meanQueryLatencyMs = Some(10.0))
        ),
        comparisons = Nil,
      )

      val decoded = QdrantEmbeddingBenchmarkReportJson.decodeReportString(QdrantEmbeddingBenchmarkReportJson.encodeReportString(report)).fold(
        failure => fail(s"unexpected decode failure: $failure"),
        identity,
      )
      val formatted = QdrantEmbeddingBenchmarkReportFormatter.format(decoded)

      assert(formatted.contains("runMode: SingleEndpointManualRestart"))
      assert(formatted.contains("candidate: qwen3-0_6b"))
      assert(formatted.contains("meanQueryLatencyMs: 10.0000"))
    }
  }

  private def decode(report: QdrantEmbeddingBenchmarkReport): QdrantEmbeddingBenchmarkReport =
    QdrantEmbeddingBenchmarkReportJson.decodeReportString(QdrantEmbeddingBenchmarkReportJson.encodeReportString(report)).fold(
      failure => fail(s"unexpected round-trip failure: $failure"),
      identity,
    )

  private def benchmarkCandidate(
    candidateId: String,
    modelName: String,
    endpointLabel: String,
    vectorDimension: Int,
    notes: Option[String] = None,
  ): QdrantEmbeddingBenchmarkCandidate =
    QdrantEmbeddingBenchmarkCandidate(
      candidateId = candidateId,
      modelName = modelName,
      endpointLabel = endpointLabel,
      vectorDimension = vectorDimension,
      notes = notes,
    )

  private def candidateReport(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    queryMetrics: List[QdrantEmbeddingBenchmarkQueryMetrics],
    meanQueryLatencyMs: Option[Double] = Some(15.0),
  ): QdrantEmbeddingBenchmarkCandidateReport =
    QdrantEmbeddingBenchmarkCandidateReport(
      candidate = candidate,
      queryMetrics = queryMetrics,
      aggregate = QdrantEmbeddingBenchmarkAggregate(
        candidateId = candidate.candidateId,
        queryCount = queryMetrics.size,
        variantRecallAtK = if (queryMetrics.exists(_.variantHitAtK)) 1.0 else 0.0,
        meanReciprocalRankAtK = queryMetrics.map(_.reciprocalRank).headOption.getOrElse(0.0),
        providerHitRateAtK = if (queryMetrics.exists(_.providerHitAtK)) 1.0 else 0.0,
        serviceHitRateAtK = if (queryMetrics.exists(_.serviceHitAtK)) 1.0 else 0.0,
        meanQueryLatencyMs = meanQueryLatencyMs,
      ),
    )

  private def queryMetrics(
    queryId: String,
    candidateId: String,
    variantHitRank: Option[Int],
    reciprocalRank: Double,
  ): QdrantEmbeddingBenchmarkQueryMetrics =
    QdrantEmbeddingBenchmarkQueryMetrics(
      queryId = queryId,
      candidateId = candidateId,
      variantHitAtK = variantHitRank.nonEmpty,
      variantHitRank = variantHitRank,
      reciprocalRank = reciprocalRank,
      providerHitAtK = variantHitRank.nonEmpty,
      serviceHitAtK = variantHitRank.nonEmpty,
    )

  private def variantId(value: Int): MasterServiceOfferVariantId = id(value)

  private def providerId(value: Int): MasterLocationId = id(value + 100)

  private def serviceId(value: Int): ServiceId = id(value + 200)

  private def id(value: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
