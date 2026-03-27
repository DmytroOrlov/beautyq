package leaderboard.model

sealed trait MasterServiceOfferLocationValidationError extends Product with Serializable {
  def message: String

  final def asThrowable: Throwable = new Exception(message)
}

object MasterServiceOfferLocationValidationError {
  final case class NegativePriceFrom(priceFrom: BigDecimal) extends MasterServiceOfferLocationValidationError {
    override val message: String = s"priceFrom must be >= 0, got $priceFrom"
  }

  final case class PriceToLessThanPriceFrom(
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
  ) extends MasterServiceOfferLocationValidationError {
    override val message: String = s"priceTo must be >= priceFrom, got priceFrom=$priceFrom, priceTo=$priceTo"
  }
}
