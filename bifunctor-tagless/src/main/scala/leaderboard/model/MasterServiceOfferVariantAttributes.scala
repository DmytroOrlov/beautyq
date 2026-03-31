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
    MasterServiceOfferVariantAttributes(AttributeMap(Map.empty), AttributeMap(Map.empty))
}
