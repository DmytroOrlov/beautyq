package leaderboard.model

import leaderboard.model.MasterServiceOfferVariantAttributeDefinition.AnyAttributeDefinition

final case class MasterServiceOfferVariantAttributes(
  intValues: Map[IntAttributeDefinition, Int],
  bigDecimalValues: Map[BigDecimalAttributeDefinition, BigDecimal],
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
    MasterServiceOfferVariantAttributes(Map.empty, Map.empty)
}
