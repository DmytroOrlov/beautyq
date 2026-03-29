package leaderboard.model

import leaderboard.model.MasterServiceOfferVariantAttributeDefinition.AnyAttributeDefinition
import leaderboard.model.ServiceVariantSchemaValidationError.{DisallowedAttribute, MissingRequiredAttribute}

case class ServiceVariantSchemaItem(
  attribute: AnyAttributeDefinition,
  required: Boolean,
)

case class ServiceVariantSchema(
  serviceId: ServiceId,
  private val itemsByAttribute: Map[AnyAttributeDefinition, Boolean],
) {
  def validate(attributes: MasterServiceOfferVariantAttributes): Either[ServiceVariantSchemaValidationError, Unit] = {
    val presentAttributes   = attributes.presentDefinitions
    val disallowedAttribute = presentAttributes.diff(itemsByAttribute.keySet).headOption
    val missingAttribute    = itemsByAttribute.iterator.collectFirst {
      case (attribute, true) if !presentAttributes.contains(attribute) =>
        attribute
    }

    disallowedAttribute match {
      case Some(attribute) =>
        Left(DisallowedAttribute(attribute))
      case None =>
        missingAttribute match {
          case Some(attribute) =>
            Left(MissingRequiredAttribute(attribute))
          case None =>
            Right(())
        }
    }
  }

  private[leaderboard] def items: Iterator[ServiceVariantSchemaItem] =
    itemsByAttribute.iterator.map {
      case (attribute, required) =>
        ServiceVariantSchemaItem(attribute, required)
    }
}

object ServiceVariantSchema {
  def empty(serviceId: ServiceId): ServiceVariantSchema =
    fromItems(serviceId, Iterable.empty)

  def fromItems(serviceId: ServiceId, items: Iterable[ServiceVariantSchemaItem]): ServiceVariantSchema =
    ServiceVariantSchema(
      serviceId,
      items.iterator.map {
        item =>
          item.attribute -> item.required
      }.toMap,
    )
}
