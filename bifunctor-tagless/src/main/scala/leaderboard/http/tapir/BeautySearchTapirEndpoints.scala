package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.search.{BeautySearchResponse, UserSearchInput}
import leaderboard.search.dsl.BeautySearchSpecV1
import sttp.tapir.*
import sttp.tapir.json.circe.*

trait BeautySearchTapirEndpoints {
  def searchBeauty: PublicEndpoint[UserSearchInput, HttpApiFailure, BeautySearchResponse, Any]

  final def all: List[AnyEndpoint] = List(searchBeauty)
}

object BeautySearchTapirEndpoints extends BeautySearchTapirEndpoints {
  given Schema[UserSearchInput]       = Schema.any[UserSearchInput]
  given Schema[BeautySearchResponse]  = Schema.any[BeautySearchResponse]

  private val maxLimit = BeautySearchSpecV1.spec.carouselSpec.variantSize

  private val validateUserSearchInput: Validator[UserSearchInput] =
    Validator.custom[UserSearchInput](
      input =>
        if (
          input.query.trim.nonEmpty &&
          input.limit > 0 &&
          input.limit <= maxLimit &&
          input.userLat.forall(isValidLatitude) &&
          input.userLon.forall(isValidLongitude)
        ) ValidationResult.Valid
        else ValidationResult.Invalid("Invalid Beauty search request fields")
    )

  private def isValidLatitude(latitude: BigDecimal): Boolean =
    latitude >= BigDecimal(-90) && latitude <= BigDecimal(90)

  private def isValidLongitude(longitude: BigDecimal): Boolean =
    longitude >= BigDecimal(-180) && longitude <= BigDecimal(180)

  val searchBeauty: PublicEndpoint[UserSearchInput, HttpApiFailure, BeautySearchResponse, Any] =
    HttpApiFailureTapirSupport.endpointBase.in("beauty-search").post
      .in(jsonBody[UserSearchInput].validate(validateUserSearchInput))
      .out(jsonBody[BeautySearchResponse])
}
