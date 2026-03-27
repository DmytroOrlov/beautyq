package leaderboard.model

import leaderboard.model.AttributeValueType.{BigDecimalValue, IntValue}

sealed trait MasterServiceOfferVariantAttributeDefinition extends Product with Serializable {
  def code: String
  def valueType: AttributeValueType
}

object MasterServiceOfferVariantAttributeDefinition {
  case object SessionCount extends MasterServiceOfferVariantAttributeDefinition {
    val code = "session_count"
    val valueType: AttributeValueType = IntValue
  }

  case object IncludedCorrectionsCount extends MasterServiceOfferVariantAttributeDefinition {
    val code = "included_corrections_count"
    val valueType: AttributeValueType = IntValue
  }

  case object MaxClients extends MasterServiceOfferVariantAttributeDefinition {
    val code = "max_clients"
    val valueType: AttributeValueType = IntValue
  }

  case object DepositAmount extends MasterServiceOfferVariantAttributeDefinition {
    val code = "deposit_amount"
    val valueType: AttributeValueType = BigDecimalValue
  }

  case object HomeVisitSurcharge extends MasterServiceOfferVariantAttributeDefinition {
    val code = "home_visit_surcharge"
    val valueType: AttributeValueType = BigDecimalValue
  }

  case object MaterialsSurcharge extends MasterServiceOfferVariantAttributeDefinition {
    val code = "materials_surcharge"
    val valueType: AttributeValueType = BigDecimalValue
  }

  case object FixedDiscountAmount extends MasterServiceOfferVariantAttributeDefinition {
    val code = "fixed_discount_amount"
    val valueType: AttributeValueType = BigDecimalValue
  }

  val all: List[MasterServiceOfferVariantAttributeDefinition] =
    List(
      SessionCount,
      IncludedCorrectionsCount,
      MaxClients,
      DepositAmount,
      HomeVisitSurcharge,
      MaterialsSurcharge,
      FixedDiscountAmount,
    )

  val byCode: Map[String, MasterServiceOfferVariantAttributeDefinition] =
    all.iterator.map(definition => definition.code -> definition).toMap

  def fromCode(code: String): Option[MasterServiceOfferVariantAttributeDefinition] =
    byCode.get(code)
}
