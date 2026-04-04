package leaderboard.model

sealed trait AttributeDefinition[+A] extends Product with Serializable {
  def code: String
  def valueType: String
}

sealed trait IntAttributeDefinition extends AttributeDefinition[Int] {
  final val valueType = "Int"
}

sealed trait BigDecimalAttributeDefinition extends AttributeDefinition[BigDecimal] {
  final val valueType = "BigDecimal"
}

object AttributeDefinition {
  type AnyAttributeDefinition = AttributeDefinition[Any]

  case object SessionCount extends IntAttributeDefinition {
    val code = "session_count"
  }

  case object IncludedCorrectionsCount extends IntAttributeDefinition {
    val code = "included_corrections_count"
  }

  case object MaxClients extends IntAttributeDefinition {
    val code = "max_clients"
  }

  case object DepositAmount extends BigDecimalAttributeDefinition {
    val code = "deposit_amount"
  }

  case object HomeVisitSurcharge extends BigDecimalAttributeDefinition {
    val code = "home_visit_surcharge"
  }

  case object MaterialsSurcharge extends BigDecimalAttributeDefinition {
    val code = "materials_surcharge"
  }

  case object FixedDiscountAmount extends BigDecimalAttributeDefinition {
    val code = "fixed_discount_amount"
  }

  val intDefinitions: List[IntAttributeDefinition] =
    List(
      SessionCount,
      IncludedCorrectionsCount,
      MaxClients,
    )

  val bigDecimalDefinitions: List[BigDecimalAttributeDefinition] =
    List(
      DepositAmount,
      HomeVisitSurcharge,
      MaterialsSurcharge,
      FixedDiscountAmount,
    )

  val all: List[AnyAttributeDefinition] =
    intDefinitions ++ bigDecimalDefinitions

  val byCode: Map[String, AnyAttributeDefinition] =
    all.iterator.map(definition => definition.code -> definition).toMap

  def fromCode(code: String): Option[AnyAttributeDefinition] =
    byCode.get(code)

  def fromCodeAsInt(code: String): Option[IntAttributeDefinition] =
    fromCode(code).collect {
      case definition: IntAttributeDefinition =>
        definition
    }

  def fromCodeAsBigDecimal(code: String): Option[BigDecimalAttributeDefinition] =
    fromCode(code).collect {
      case definition: BigDecimalAttributeDefinition =>
        definition
    }
}
