package leaderboard.model

import leaderboard.model.AttributeDefinition.AnyAttributeDefinition

case class MasterServiceOfferVariantAttributes(
  intValues: AttributeMap[Int],
  bigDecimalValues: AttributeMap[BigDecimal],
) {
  def get(attributeDefinition: IntAttributeDefinition): Option[Int] =
    intValues.get(attributeDefinition)

  def get(attributeDefinition: BigDecimalAttributeDefinition): Option[BigDecimal] =
    bigDecimalValues.get(attributeDefinition)

  private[model] def presentDefinitions: Set[AnyAttributeDefinition] =
    intValues.keysIterator.map(identity[AnyAttributeDefinition]).toSet ++
    bigDecimalValues.keysIterator.map(identity[AnyAttributeDefinition]).toSet
}

object MasterServiceOfferVariantAttributes {
  val empty: MasterServiceOfferVariantAttributes =
    MasterServiceOfferVariantAttributes(AttributeMap.empty, AttributeMap.empty)
}

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
