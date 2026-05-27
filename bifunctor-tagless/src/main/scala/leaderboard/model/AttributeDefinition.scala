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

sealed trait EnumAttributeDefinition[E <: CodedEnumValue] extends AttributeDefinition[E] {
  final val valueType = "Enum"
  def values: List[E]
  def fromIntCode(code: Int): Option[E]
  def fromStringCode(code: String): Option[E]
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

  case object HairRemovalMethodAttribute extends EnumAttributeDefinition[HairRemovalMethod] {
    val code = "hair_removal_method"

    def values: List[HairRemovalMethod] =
      HairRemovalMethod.values.toList

    def fromIntCode(code: Int): Option[HairRemovalMethod] =
      HairRemovalMethod.fromIntCode(code)

    def fromStringCode(code: String): Option[HairRemovalMethod] =
      HairRemovalMethod.fromStringCode(code)
  }

  case object NailCoatingTypeAttribute extends EnumAttributeDefinition[NailCoatingType] {
    val code = "nail_coating_type"

    def values: List[NailCoatingType] =
      NailCoatingType.values.toList

    def fromIntCode(code: Int): Option[NailCoatingType] =
      NailCoatingType.fromIntCode(code)

    def fromStringCode(code: String): Option[NailCoatingType] =
      NailCoatingType.fromStringCode(code)
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

  val enumDefinitions: List[EnumAttributeDefinition[?]] =
    List(
      HairRemovalMethodAttribute,
      NailCoatingTypeAttribute,
    )

  val all: List[AnyAttributeDefinition] =
    intDefinitions ++ bigDecimalDefinitions ++ enumDefinitions

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

  def fromCodeAsEnum(code: String): Option[EnumAttributeDefinition[?]] =
    fromCode(code).collect {
      case definition: EnumAttributeDefinition[?] =>
        definition
    }
}
