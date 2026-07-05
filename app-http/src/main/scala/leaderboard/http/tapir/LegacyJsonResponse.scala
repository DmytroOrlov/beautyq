package leaderboard.http.tapir

import io.circe.{Encoder, Json}
import io.circe.syntax.*

object LegacyJsonResponse {
  def optionalAsJson[A: Encoder](value: Option[A]): Json =
    value.fold(Json.Null)(_.asJson)
}
