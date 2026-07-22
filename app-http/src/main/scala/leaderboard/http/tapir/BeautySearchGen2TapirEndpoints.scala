package leaderboard.http.tapir

import io.circe.Json
import leaderboard.http.HttpApiFailure
import sttp.tapir.*
import sttp.tapir.json.circe.*

/** The canonical `/beauty-search` endpoint. Native Gen2 request/response ownership. */
trait BeautySearchGen2TapirEndpoints {
  def searchBeautyGen2: PublicEndpoint[Json, HttpApiFailure, Json, Any]

  final def all: List[AnyEndpoint] = List(searchBeautyGen2)
}

object BeautySearchGen2TapirEndpoints extends BeautySearchGen2TapirEndpoints {
  given Schema[Json] = Schema.any[Json]

  val searchBeautyGen2: PublicEndpoint[Json, HttpApiFailure, Json, Any] =
    HttpApiFailureTapirSupport.endpointBase.in("beauty-search").post
      .in(jsonBody[Json])
      .out(jsonBody[Json])
}
