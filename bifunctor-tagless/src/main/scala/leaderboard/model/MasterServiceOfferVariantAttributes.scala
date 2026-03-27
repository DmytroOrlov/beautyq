package leaderboard.model

import leaderboard.model.AttributeValueType.{BigDecimalValue, IntValue}
import leaderboard.model.MasterServiceOfferVariantAttributeValue.{BigDecimalAttributeValue, IntAttributeValue}
import leaderboard.model.MasterServiceOfferVariantValidationError.DuplicateAdditionalAttributeCode
import scala.annotation.nowarn

sealed trait MasterServiceOfferVariantAttributeValue extends Product with Serializable {
  def valueType: AttributeValueType
}

object MasterServiceOfferVariantAttributeValue {
  final case class IntAttributeValue(value: Int) extends MasterServiceOfferVariantAttributeValue {
    override val valueType: AttributeValueType = IntValue
  }

  final case class BigDecimalAttributeValue(value: BigDecimal) extends MasterServiceOfferVariantAttributeValue {
    override val valueType: AttributeValueType = BigDecimalValue
  }
}

final case class MasterServiceOfferVariantAttributes private (
  values: Map[MasterServiceOfferVariantAttributeDefinition, MasterServiceOfferVariantAttributeValue]
) {
  @nowarn("cat=unused")
  private def copy(
    values: Map[MasterServiceOfferVariantAttributeDefinition, MasterServiceOfferVariantAttributeValue] = this.values
  ): MasterServiceOfferVariantAttributes =
    new MasterServiceOfferVariantAttributes(values)

  def valuesByType(
    valueType: AttributeValueType
  ): Map[MasterServiceOfferVariantAttributeDefinition, MasterServiceOfferVariantAttributeValue] =
    values.iterator.collect {
      case (attributeDefinition, attributeValue) if attributeValue.valueType == valueType =>
        attributeDefinition -> attributeValue
    }.toMap

  def intValues: Map[MasterServiceOfferVariantAttributeDefinition, Int] =
    values.iterator.collect {
      case (attributeDefinition, IntAttributeValue(value)) =>
        attributeDefinition -> value
    }.toMap

  def bigDecimalValues: Map[MasterServiceOfferVariantAttributeDefinition, BigDecimal] =
    values.iterator.collect {
      case (attributeDefinition, BigDecimalAttributeValue(value)) =>
        attributeDefinition -> value
    }.toMap
}

object MasterServiceOfferVariantAttributes {
  val empty: MasterServiceOfferVariantAttributes =
    MasterServiceOfferVariantAttributes(Map.empty)

  private def apply(
    values: Map[MasterServiceOfferVariantAttributeDefinition, MasterServiceOfferVariantAttributeValue]
  ): MasterServiceOfferVariantAttributes =
    new MasterServiceOfferVariantAttributes(values)

  def make(
    intAttributes: Map[MasterServiceOfferVariantAttributeDefinition, Int] = Map.empty,
    bigDecimalAttributes: Map[MasterServiceOfferVariantAttributeDefinition, BigDecimal] = Map.empty,
  ): Either[MasterServiceOfferVariantValidationError, MasterServiceOfferVariantAttributes] =
    intAttributes.keySet.intersect(bigDecimalAttributes.keySet).headOption match {
      case Some(attributeDefinition) =>
        Left(DuplicateAdditionalAttributeCode(attributeDefinition))
      case None =>
        Right(
          MasterServiceOfferVariantAttributes(
            intAttributes.iterator.map {
              case (attributeDefinition, value) =>
                attributeDefinition -> IntAttributeValue(value)
            }.toMap ++ bigDecimalAttributes.iterator.map {
              case (attributeDefinition, value) =>
                attributeDefinition -> BigDecimalAttributeValue(value)
            }.toMap
          )
        )
    }
}
