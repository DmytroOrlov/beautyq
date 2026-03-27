package leaderboard.model

final case class ServiceVariantSchemaItem(
  attribute: MasterServiceOfferVariantAttributeDefinition,
  required: Boolean,
)

final case class ServiceVariantSchema(
  serviceId: ServiceId,
  items: Map[MasterServiceOfferVariantAttributeDefinition, Boolean],
) {
  def allowedAttributes: Set[MasterServiceOfferVariantAttributeDefinition] = items.keySet

  def requiredAttributes: Set[MasterServiceOfferVariantAttributeDefinition] =
    items.iterator.collect {
      case (attribute, true) =>
        attribute
    }.toSet
}

object ServiceVariantSchema {
  def empty(serviceId: ServiceId): ServiceVariantSchema =
    ServiceVariantSchema(serviceId, Map.empty)

  def fromItems(serviceId: ServiceId, items: Iterable[ServiceVariantSchemaItem]): ServiceVariantSchema =
    ServiceVariantSchema(
      serviceId,
      items.iterator.map {
        item =>
          item.attribute -> item.required
      }.toMap,
    )
}
