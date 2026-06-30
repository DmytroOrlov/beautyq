package leaderboard.search

import leaderboard.search.dsl.{BeautyQSearchPresentation, BeautySearchSpecV1}

object BeautySearchRequestContract {
  final case class SemanticError(code: String, message: String)

  val MinLimit: Int = 1
  val MaxLimit: Int =
    BeautyQSearchPresentation.variantLimit(BeautySearchSpecV1.spec.carouselSpec).fold(
      error => throw new IllegalStateException(error.message),
      identity,
    )

  val MinLatitude: BigDecimal  = BigDecimal(-90)
  val MaxLatitude: BigDecimal  = BigDecimal(90)
  val MinLongitude: BigDecimal = BigDecimal(-180)
  val MaxLongitude: BigDecimal = BigDecimal(180)

  val InvalidQuery: SemanticError =
    SemanticError(
      code = "invalid_query",
      message = "query must not be blank",
    )

  val InvalidLimit: SemanticError =
    SemanticError(
      code = "invalid_limit",
      message = s"limit must be between $MinLimit and $MaxLimit",
    )

  val InvalidLatitude: SemanticError =
    SemanticError(
      code = "invalid_latitude",
      message = s"userLat must be between $MinLatitude and $MaxLatitude",
    )

  val InvalidLongitude: SemanticError =
    SemanticError(
      code = "invalid_longitude",
      message = s"userLon must be between $MinLongitude and $MaxLongitude",
    )
}
