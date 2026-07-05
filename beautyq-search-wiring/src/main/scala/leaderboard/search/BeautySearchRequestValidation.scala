package leaderboard.search

object BeautySearchRequestValidation {
  def validate(input: UserSearchInput): Either[BeautySearchRequestContract.SemanticError, UserSearchInput] =
    if (input.query.trim.isEmpty) {
      Left(BeautySearchRequestContract.InvalidQuery)
    } else if (input.limit < BeautySearchRequestContract.MinLimit || input.limit > BeautySearchRequestContract.MaxLimit) {
      Left(BeautySearchRequestContract.InvalidLimit)
    } else if (input.userLat.exists(latitude =>
        latitude < BeautySearchRequestContract.MinLatitude || latitude > BeautySearchRequestContract.MaxLatitude
      )) {
      Left(BeautySearchRequestContract.InvalidLatitude)
    } else if (input.userLon.exists(longitude =>
        longitude < BeautySearchRequestContract.MinLongitude || longitude > BeautySearchRequestContract.MaxLongitude
      )) {
      Left(BeautySearchRequestContract.InvalidLongitude)
    } else {
      Right(input)
    }
}
