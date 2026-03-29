package leaderboard.model

sealed trait MasterServiceOfferVariantValidationError extends Product with Serializable {
  def message: String
}

object MasterServiceOfferVariantValidationError {
  case class NegativePriceFrom(priceFrom: BigDecimal) extends MasterServiceOfferVariantValidationError {
    val message: String = s"priceFrom must be >= 0, got $priceFrom"
  }

  case class PriceToLessThanPriceFrom(
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
  ) extends MasterServiceOfferVariantValidationError {
    val message: String = s"priceTo must be >= priceFrom, got priceFrom=$priceFrom, priceTo=$priceTo"
  }

  case class NonPositiveDurationMin(durationMin: Int) extends MasterServiceOfferVariantValidationError {
    val message: String = s"durationMin must be > 0, got $durationMin"
  }
}
