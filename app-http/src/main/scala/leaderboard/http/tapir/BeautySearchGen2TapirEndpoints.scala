package leaderboard.http.tapir

import io.circe.Json
import leaderboard.http.HttpApiFailure
import sttp.tapir.*
import sttp.tapir.json.circe.*

trait BeautySearchGen2TapirEndpoints {
  def searchBeautyGen2: PublicEndpoint[String, HttpApiFailure, Json, Any]
  def statusBeautyGen2: PublicEndpoint[Unit, HttpApiFailure, Json, Any]

  final def all: List[AnyEndpoint] = List(searchBeautyGen2, statusBeautyGen2)
}

object BeautySearchGen2TapirEndpoints extends BeautySearchGen2TapirEndpoints {
  given Schema[Json] = Schema.any[Json]

  val searchBeautyGen2: PublicEndpoint[String, HttpApiFailure, Json, Any] =
    HttpApiFailureTapirSupport.endpointBase.in("beauty-search").post
      .in(stringBodyUtf8AnyFormat(Codec.string))
      .out(jsonBody[Json])

  val statusBeautyGen2: PublicEndpoint[Unit, HttpApiFailure, Json, Any] =
    HttpApiFailureTapirSupport.endpointBase.in("beauty-search" / "status").get
      .out(jsonBody[Json])
}
