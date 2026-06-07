package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.search.{BeautySearchResponse, UserSearchInput}
import sttp.tapir.*
import sttp.tapir.json.circe.*

trait BeautySearchTapirEndpoints {
  def searchBeauty: PublicEndpoint[UserSearchInput, HttpApiFailure, BeautySearchResponse, Any]

  final def all: List[AnyEndpoint] = List(searchBeauty)
}

object BeautySearchTapirEndpoints extends BeautySearchTapirEndpoints {
  given Schema[UserSearchInput]       = Schema.any[UserSearchInput]
  given Schema[BeautySearchResponse]  = Schema.any[BeautySearchResponse]

  val searchBeauty: PublicEndpoint[UserSearchInput, HttpApiFailure, BeautySearchResponse, Any] =
    HttpApiFailureTapirSupport.endpointBase.in("beauty-search").post
      .in(jsonBody[UserSearchInput])
      .out(jsonBody[BeautySearchResponse])
}
