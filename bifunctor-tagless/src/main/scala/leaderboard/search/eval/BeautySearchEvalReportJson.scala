package leaderboard.search.eval

import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser.parse
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, QueryFailure, ServiceId}

object BeautySearchEvalReportJson {
  private val OperationName = "beauty-search-eval-report-json"

  implicit val reportEncoder: Encoder.AsObject[BeautySearchEvalReport] = deriveEncoder
  implicit val reportDecoder: Decoder[BeautySearchEvalReport] = Decoder.instance { c =>
    for {
      queryId <- c.get[String]("queryId")
      query <- c.get[String]("query")
      score <- c.get[Int]("score")
      failedAssertions <- c.getOrElse[List[String]]("failedAssertions")(Nil)
      topVariantIds <- c.getOrElse[List[MasterServiceOfferVariantId]]("topVariantIds")(Nil)
      topProviderLocationIds <- c.getOrElse[List[MasterLocationId]]("topProviderLocationIds")(Nil)
      topServiceIds <- c.getOrElse[List[ServiceId]]("topServiceIds")(Nil)
    } yield BeautySearchEvalReport(queryId, query, score, failedAssertions, topVariantIds, topProviderLocationIds, topServiceIds)
  }

  def encodeReports(reports: List[BeautySearchEvalReport]): Json =
    reports.asJson

  def encodeReportsString(reports: List[BeautySearchEvalReport]): String =
    encodeReports(reports).spaces2

  def decodeReports(json: Json): Either[QueryFailure, List[BeautySearchEvalReport]] =
    json.as[List[BeautySearchEvalReport]].left.map(error => invalidJson(error.getMessage))

  def decodeReportsString(value: String): Either[QueryFailure, List[BeautySearchEvalReport]] =
    parse(value).left.map(error => invalidJson(error.message)).flatMap(decodeReports)

  private def invalidJson(message: String): QueryFailure =
    QueryFailure.operation(OperationName, s"Invalid BeautySearch eval report JSON: $message")
}
