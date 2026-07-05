package leaderboard.search.qdrant

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import leaderboard.model.QueryFailure

object QdrantEmbeddingBenchmarkReportJson {
  private val OperationName = "qdrant-embedding-benchmark-report-json"

  implicit val runModeEncoder: Encoder[QdrantEmbeddingBenchmarkRunMode] = Encoder.encodeString.contramap {
    case QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart => "SingleEndpointManualRestart"
    case QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel        => "DualEndpointParallel"
  }

  implicit val runModeDecoder: Decoder[QdrantEmbeddingBenchmarkRunMode] = Decoder.decodeString.emap {
    case "SingleEndpointManualRestart" => Right(QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart)
    case "DualEndpointParallel"        => Right(QdrantEmbeddingBenchmarkRunMode.DualEndpointParallel)
    case other =>
      Left(s"Unsupported Qdrant embedding benchmark run mode: $other")
  }

  implicit val candidateEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkCandidate] = deriveEncoder
  implicit val candidateDecoder: Decoder[QdrantEmbeddingBenchmarkCandidate] = deriveDecoder

  implicit val planEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkPlan] = deriveEncoder
  implicit val planDecoder: Decoder[QdrantEmbeddingBenchmarkPlan] = deriveDecoder

  implicit val expectedEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkExpected] = deriveEncoder
  implicit val expectedDecoder: Decoder[QdrantEmbeddingBenchmarkExpected] = deriveDecoder

  implicit val queryResultEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkQueryResult] = deriveEncoder
  implicit val queryResultDecoder: Decoder[QdrantEmbeddingBenchmarkQueryResult] = deriveDecoder

  implicit val queryMetricsEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkQueryMetrics] = deriveEncoder
  implicit val queryMetricsDecoder: Decoder[QdrantEmbeddingBenchmarkQueryMetrics] = deriveDecoder

  implicit val aggregateEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkAggregate] = deriveEncoder
  implicit val aggregateDecoder: Decoder[QdrantEmbeddingBenchmarkAggregate] = deriveDecoder

  implicit val candidateReportEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkCandidateReport] = deriveEncoder
  implicit val candidateReportDecoder: Decoder[QdrantEmbeddingBenchmarkCandidateReport] = deriveDecoder

  implicit val comparisonEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkComparison] = deriveEncoder
  implicit val comparisonDecoder: Decoder[QdrantEmbeddingBenchmarkComparison] = deriveDecoder

  implicit val reportEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkReport] = deriveEncoder
  implicit val reportDecoder: Decoder[QdrantEmbeddingBenchmarkReport] = deriveDecoder

  implicit val runOutputEncoder: Encoder.AsObject[QdrantEmbeddingBenchmarkRunOutput] = deriveEncoder
  implicit val runOutputDecoder: Decoder[QdrantEmbeddingBenchmarkRunOutput] = deriveDecoder

  def encodeReport(report: QdrantEmbeddingBenchmarkReport): Json =
    report.asJson

  def encodeReportString(report: QdrantEmbeddingBenchmarkReport): String =
    encodeReport(report).spaces2

  def decodeReport(json: Json): Either[QueryFailure, QdrantEmbeddingBenchmarkReport] =
    json.as[QdrantEmbeddingBenchmarkReport].left.map(error => invalidJson(error.getMessage))

  def decodeReportString(value: String): Either[QueryFailure, QdrantEmbeddingBenchmarkReport] =
    parse(value).left.map(error => invalidJson(error.message)).flatMap(decodeReport)

  def decodeReportEither(json: Json): Either[io.circe.Error, QdrantEmbeddingBenchmarkReport] =
    json.as[QdrantEmbeddingBenchmarkReport]

  def decodeReportStringEither(value: String): Either[io.circe.Error, QdrantEmbeddingBenchmarkReport] =
    parse(value).flatMap(decodeReportEither)

  def encodeRunOutput(output: QdrantEmbeddingBenchmarkRunOutput): Json =
    output.asJson

  def encodeRunOutputString(output: QdrantEmbeddingBenchmarkRunOutput): String =
    encodeRunOutput(output).spaces2

  def decodeRunOutput(json: Json): Either[QueryFailure, QdrantEmbeddingBenchmarkRunOutput] =
    json.as[QdrantEmbeddingBenchmarkRunOutput].left.map(error => invalidJson(error.getMessage))

  def decodeRunOutputString(value: String): Either[QueryFailure, QdrantEmbeddingBenchmarkRunOutput] =
    parse(value).left.map(error => invalidJson(error.message)).flatMap(decodeRunOutput)

  def decodeRunOutputEither(json: Json): Either[io.circe.Error, QdrantEmbeddingBenchmarkRunOutput] =
    json.as[QdrantEmbeddingBenchmarkRunOutput]

  def decodeRunOutputStringEither(value: String): Either[io.circe.Error, QdrantEmbeddingBenchmarkRunOutput] =
    parse(value).flatMap(decodeRunOutputEither)

  private def invalidJson(message: String): QueryFailure =
    QueryFailure.operation(OperationName, s"Invalid Qdrant embedding benchmark report JSON: $message")
}
