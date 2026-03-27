package leaderboard.model

import leaderboard.model.MasterServiceOfferVariantAttributeDefinition.AnyAttributeDefinition

sealed trait ServiceVariantSchemaValidationError extends Product with Serializable {
  def attribute: AnyAttributeDefinition
}

object ServiceVariantSchemaValidationError {
  final case class DisallowedAttribute(attribute: AnyAttributeDefinition) extends ServiceVariantSchemaValidationError

  final case class MissingRequiredAttribute(attribute: AnyAttributeDefinition) extends ServiceVariantSchemaValidationError
}
