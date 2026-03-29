package leaderboard.model

import leaderboard.model.MasterServiceOfferVariantAttributeDefinition.AnyAttributeDefinition

sealed trait ServiceVariantSchemaValidationError extends Product with Serializable {
  def attribute: AnyAttributeDefinition
}

object ServiceVariantSchemaValidationError {
  case class DisallowedAttribute(attribute: AnyAttributeDefinition) extends ServiceVariantSchemaValidationError

  case class MissingRequiredAttribute(attribute: AnyAttributeDefinition) extends ServiceVariantSchemaValidationError
}
