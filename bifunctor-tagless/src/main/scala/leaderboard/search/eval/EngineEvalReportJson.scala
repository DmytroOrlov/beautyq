package leaderboard.search.eval

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import leaderboard.model.QueryFailure

object EngineEvalReportJson {
  private val OperationName = "engine-eval-report-json"

  implicit val engineEncoder: Encoder[EngineEvalEngine] = Encoder.encodeString.contramap {
    case EngineEvalEngine.Elasticsearch   => "Elasticsearch"
    case EngineEvalEngine.Qdrant          => "Qdrant"
    case EngineEvalEngine.SimulatedHybrid => "SimulatedHybrid"
  }

  implicit val engineDecoder: Decoder[EngineEvalEngine] = Decoder.decodeString.emap {
    case "Elasticsearch"   => Right(EngineEvalEngine.Elasticsearch)
    case "Qdrant"          => Right(EngineEvalEngine.Qdrant)
    case "SimulatedHybrid" => Right(EngineEvalEngine.SimulatedHybrid)
    case other             => Left(s"Unsupported EngineEval engine: $other")
  }

  implicit val expectedRoleEncoder: Encoder[EngineExpectedRole] = Encoder.encodeString.contramap {
    case EngineExpectedRole.EsShouldHandle         => "EsShouldHandle"
    case EngineExpectedRole.QdrantMayComplement    => "QdrantMayComplement"
    case EngineExpectedRole.QdrantShouldStaySilent => "QdrantShouldStaySilent"
    case EngineExpectedRole.HybridMayImprove       => "HybridMayImprove"
  }

  implicit val expectedRoleDecoder: Decoder[EngineExpectedRole] = Decoder.decodeString.emap {
    case "EsShouldHandle"         => Right(EngineExpectedRole.EsShouldHandle)
    case "QdrantMayComplement"    => Right(EngineExpectedRole.QdrantMayComplement)
    case "QdrantShouldStaySilent" => Right(EngineExpectedRole.QdrantShouldStaySilent)
    case "HybridMayImprove"       => Right(EngineExpectedRole.HybridMayImprove)
    case other                    => Left(s"Unsupported EngineEval expected role: $other")
  }

  implicit val resultEncoder: Encoder.AsObject[EngineEvalResult] = deriveEncoder
  implicit val resultDecoder: Decoder[EngineEvalResult] = deriveDecoder

  implicit val comparisonMetricsEncoder: Encoder.AsObject[EngineEvalComparisonMetrics] = deriveEncoder
  implicit val comparisonMetricsDecoder: Decoder[EngineEvalComparisonMetrics] = deriveDecoder

  implicit val queryReportEncoder: Encoder.AsObject[EngineEvalQueryReport] = deriveEncoder
  implicit val queryReportDecoder: Decoder[EngineEvalQueryReport] = deriveDecoder

  implicit val aggregateMetricsEncoder: Encoder.AsObject[EngineEvalAggregateMetrics] = deriveEncoder
  implicit val aggregateMetricsDecoder: Decoder[EngineEvalAggregateMetrics] = deriveDecoder

  implicit val aggregateReportEncoder: Encoder.AsObject[EngineEvalAggregateReport] = deriveEncoder
  implicit val aggregateReportDecoder: Decoder[EngineEvalAggregateReport] = deriveDecoder

  def encodeReport(report: EngineEvalAggregateReport): Json =
    report.asJson

  def encodeReportString(report: EngineEvalAggregateReport): String =
    encodeReport(report).spaces2

  def decodeReport(json: Json): Either[QueryFailure, EngineEvalAggregateReport] =
    json.as[EngineEvalAggregateReport].left.map(error => invalidJson(error.getMessage))

  def decodeReportString(value: String): Either[QueryFailure, EngineEvalAggregateReport] =
    parse(value).left.map(error => invalidJson(error.message)).flatMap(decodeReport)

  def decodeReportEither(json: Json): Either[io.circe.Error, EngineEvalAggregateReport] =
    json.as[EngineEvalAggregateReport]

  def decodeReportStringEither(value: String): Either[io.circe.Error, EngineEvalAggregateReport] =
    parse(value).flatMap(decodeReportEither)

  private def invalidJson(message: String): QueryFailure =
    QueryFailure.operation(OperationName, s"Invalid EngineEval report JSON: $message")
}
