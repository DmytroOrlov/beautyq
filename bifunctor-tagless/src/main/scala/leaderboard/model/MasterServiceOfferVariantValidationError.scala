package leaderboard.model

sealed trait MasterServiceOfferVariantValidationError extends Product with Serializable {
  def message: String

  final def asThrowable: Throwable = new Exception(message)
}

object MasterServiceOfferVariantValidationError {
  final case class NegativePriceFrom(priceFrom: BigDecimal) extends MasterServiceOfferVariantValidationError {
    override val message: String = s"priceFrom must be >= 0, got $priceFrom"
  }

  final case class PriceToLessThanPriceFrom(
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
  ) extends MasterServiceOfferVariantValidationError {
    override val message: String = s"priceTo must be >= priceFrom, got priceFrom=$priceFrom, priceTo=$priceTo"
  }

  final case class NonPositiveDurationMin(durationMin: Int) extends MasterServiceOfferVariantValidationError {
    override val message: String = s"durationMin must be > 0, got $durationMin"
  }
}
