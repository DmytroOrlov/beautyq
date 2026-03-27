package leaderboard.model

sealed trait AttributeValueType extends Product with Serializable

object AttributeValueType {
  case object IntValue extends AttributeValueType
  case object BigDecimalValue extends AttributeValueType

  val all: List[AttributeValueType] = List(IntValue, BigDecimalValue)
}
