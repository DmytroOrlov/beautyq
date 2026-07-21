package leaderboard.http.tapir

import io.circe.Json
import leaderboard.http.HttpApiFailure
import sttp.tapir.*
import sttp.tapir.json.circe.*

/** Independent Gen2 endpoint. It is deliberately not part of the V1 route
  * and is mounted only by an explicit Gen2 composition. */
trait BeautySearchGen2TapirEndpoints {
  def searchBeautyGen2: PublicEndpoint[Json, HttpApiFailure, Json, Any]

  final def all: List[AnyEndpoint] = List(searchBeautyGen2)
}

object BeautySearchGen2TapirEndpoints extends BeautySearchGen2TapirEndpoints {
  given Schema[Json] = Schema.any[Json]

  val searchBeautyGen2: PublicEndpoint[Json, HttpApiFailure, Json, Any] =
    HttpApiFailureTapirSupport.endpointBase.in("beauty-search-gen2").post
      .in(jsonBody[Json])
      .out(jsonBody[Json])
}
